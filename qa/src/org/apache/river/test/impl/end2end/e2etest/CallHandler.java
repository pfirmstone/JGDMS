/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.test.impl.end2end.e2etest;

import java.lang.reflect.InvocationTargetException;

/**
 * An abstract class which provides a uniform way to perform a remote method
 * call and validate the correctness of the return. Extensions of this
 * class provide the call specific invocation and validation mechanisms.
 */
abstract class CallHandler implements Constants {

    /** the <code>UserInterface</code> bound to this test thread */
    private UserInterface ui;

    /** the <code>TestClient</code> executing in this test thread */
    private TestClient client;

    /**  handler to detect and report hung remote calls */
    private HangDetector hangDetector;

    /** the TestCoordinator for this test run */
    private TestCoordinator coordinator;

    /**
     * Construct the CallHandler for the given <code>TestClient</code>
     * and <code>UserInterface</code>.
     *
     * @param client the <code>TestClient</code>
     * @param ui the <code>UserInterface</code>
     * @param coordinator the <code>TestCoordinator</code>
     */
    CallHandler(TestClient client,
		UserInterface ui,
		TestCoordinator coordinator)
    {
	this.client = client;
        this.ui = ui;
	this.coordinator = coordinator;
    }

    /**
     * implemented by subclasses to perform the remote call. This
     * method must not perform any Exception handling; exception
     * handling is the responsiblity of the <code>validateException</code>
     * method.
     *
     * @param method the <code>TestMethod</code> to call. This argument may
     *        be ignored by implementations which support a single method call
     * @throws Exception as no exception handling should be provided by this
     *         method
     */
    abstract protected void doCall(TestMethod method) throws Exception;

    /**
     * implemented by subclasses to verify that successful (no exception)
     * return from a <code>Remote</code> call is appropriate. In general
     * if a successful return is inappropriate, the client's
     * <code>logFailure</code> method should be called. If success is
     * always expected, this method should do nothing.
     *
     * @param method The <code>Remote</code> method to validate
     */
    abstract protected void validateSuccess(TestMethod method);

    /**
     * implemented by subclasses to verify that an exception
     * return from a <code>Remote</code> call is appropriate.
     * if an exception return is inappropriate, the client's
     * <code>logFailure</code> method should be called. If an exception is
     * expected, this method should should verify that the
     * exception is of the expected type and log a failure if not.
     *
     * @param method The <code>Remote</code> method to validate
     * @param e The exception resulting from the <code>Remote</code> call.
     */
    abstract protected void validateException(TestMethod method, Exception e);

    /**
     * Perform the remote call, validating successful return or exception
     * return of the call. This method also performs updates of the
     * test <code>UserInterface</code> if one exists, as well as logging
     * of the call and return status.
     *
     * @param method The <code>TestMethod</code> to invoke
     */
    void handleCall(TestMethod method) {
	preCallOps(method);
        Exception ex = null;
	try {
	    doCall(method);
	    validateSuccess(method);
	} catch (Exception e) {
            ex = e;
	    validateException(method, e);
	}
	postCallOps(method, ex);
    }

    /**
     * performs any operations which should be made in preparation for the
     * remote call, such as updating of the <code>UserInterface</code> and
     * updating the log.
     *
     * @param method the <code>TestMethod</code> being called
     */
    private void preCallOps(TestMethod method) {
	long memory;
	memory = Runtime.getRuntime().freeMemory();
	ui.setTestCount(client.getTestNumber(), client.getTestTotal());
	ui.setPreCallFreeMemory(memory);
	ui.setServerConstraints(method.parseConstraints());
	ui.setTestName(method.getName());
	ui.setCallInProgress();
	ui.setCallStatus("Call In Progress");
	client.getLogger().log(CALLS, "calling "
                         + method.getSignature());
        hangDetector = new HangDetector();
        hangDetector.start();
    }

    /**
     * performs any operations which should be made after completion of the
     * remote call, such as updating of the <code>UserInterface</code> and
     * updating the log.
     *
     * @param method the <code>TestMethod</code> that was called
     * @param e the exception that was generated, or <code>null</code> if
     *          the call returned successfully
     */
    private void postCallOps(TestMethod method, Exception e) {
        hangDetector.stop();
        Throwable t = e;
	if (e instanceof InvocationTargetException) {
	    t = ((InvocationTargetException) e).getTargetException();
	}
	ui.setCallComplete();
	ui.setPostCallFreeMemory(Runtime.getRuntime().freeMemory());
	ui.setCallStatus(e == null ? "Normal Return" : t.toString());
	if (ui.forceFailure()) client.logFailure("Forced Failure", t);
	String retMessage =
		((e == null) ? "normal"
                             : "exception")
                             + " return from " + method.getSignature();
	client.getLogger().log(CALLS, retMessage);
    }

    /**
     * A class which detects and handles remote call hangs.
     */
    private class HangDetector implements Runnable {

	/** the timeout Thread */
	private Thread timeoutThread;

	/** the timeout duration */
	private int timeoutDuration = 60 * 60 * 1000; // one hour

	/** constructs the HangDetector */
	HangDetector() {
	    timeoutThread = new Thread(this);
	}

	/** starts the <code>HangDetector</code> */
	void start() {
	    timeoutThread.start();
	}

	/** stops the <code>HangDetector</code> */
	void stop() {
	    timeoutThread.interrupt();
	}

	/**
	 * executed by the timeout thread. If a hang is detected,
	 * a failure is logged and the test is aborted. This
	 * is done because no mechanism exists for forcing a hung
	 * remote call to complete
	 */
	public void run() {
	    boolean timedOut = true;
	    try {
		Thread.sleep(timeoutDuration);
	    } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
		timedOut = false;
	    }
	    if (timedOut) {
		client.logFailure("Remote call timed out", null);
		coordinator.abortRun(TestCoordinator.TIMEOUT);
	    }
	}
    }
}
