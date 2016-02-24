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

import java.io.Serializable;
import net.jini.io.MarshalledInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.apache.river.api.security.CombinerSecurityManager;

/**
 * The entry point for the end-to-end test. Sets up the server
 * and runs the clients in separate threads. The system
 * property <code>threadCount</code> contains the number of
 * client threads to run. If this property is undefined, is
 * not an integer string, or has a value less than 1, a value of 1 is used.
 */

public class End2EndTest implements Constants, TestCoordinator {

     /** the default instance to use when the wrapper is disabled */
    private static TestClient defaultInstance;

    /** The test instance, to prevent the server from being GC'ed */
    private static End2EndTest end2endTest;

    /** the set of test names specified on the command line */
    private Set tests = new HashSet();

    /** The server instance, to prevent it from being GC'ed */
    private SecureServer server;

    /** the set of test client instances */
    private ArrayList clients = new ArrayList();

    /** the number of threads to run */
    private int threadCount;

    /**
     * The test entry point. Constructs the server and all of the
     * clients required by the <code>threadCount</code> property,
     * builds the table of tests to run, and starts all of the
     * clients. The test log results are
     * buffered (per-test) unless the <code>end2end.logUnbuffered</code>
     * property is set. It is preferable to use buffering, not only
     * to provide clean logs when running multiple threads, but also
     * because BOUNDARY records must be written unconditionally when
     * unbuffered, but can be supressed if a test generates no output
     * and the log is buffered. The provider wrapper must be enabled when
     * multiple client threads are requested because the wrapper is required
     * for coupling the calling client thread with its associated server
     * side dispatch thread.
     *
     * @param args the command line arguments, which are expected to be
     * the names or numbers of tests to run.
     */

    public static void main(String[] args) throws Exception{
        ProviderManager.initialize();
        SecureClient.initialize();
        end2endTest = new End2EndTest(args);
    }

    /**
     * Construct the <code>TestCoordinator</code> for the test
     *
     * @param testNames the names or numbers of tests to run
     */
    End2EndTest(String[] testNames) {
	final MarshalledInstance iface;
	threadCount = Integer.getInteger("threadCount",1).intValue();
	if (threadCount < 1) {
            threadCount = 1;
        }

        /*
         * Note that ProviderManager includes a static initializer which
         * will install the providers and the wrapper
         */
	if ((!ProviderManager.isWrapped()) && threadCount != 1) {
	    System.out.println("The wrapper must be enabled when "
			     + "threadCount is greater than 1");
	    abortRun(1);
	}
	boolean displayGUI =
		System.getProperty("end2end.displayGUI") != null;
	boolean exitOnCompletion =
		System.getProperty("end2end.exit") != null;
	Logger.setBufferedLog(
		(System.getProperty("end2end.logUnbuffered") == null)
		&& ProviderManager.isWrapped());
        System.out.println("Starting test " + new Date());
	Thread[] threadArray = new Thread[threadCount];
	int totalFailures = 0;
	for (int i=testNames.length; --i>=0; ) {
	    tests.add(testNames[i]);
	}
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new CombinerSecurityManager());
        }
	server = new SecureServer(this);
	MarshalledInstance pickeledStub = server.getProxy();
	for (int i=threadCount; --i >= 0; ) {
	    TestClient client = new SecureClient(this,
						 pickeledStub);
	    clients.add(client);
            defaultInstance = client;
	}

	/*
	 * construct the GUI/NullGUI, depending on the state of the
	 * displayGUI flag. The user interface is responsible
	 * for registering with the tests, so it is not necessary to
	 * retain a reference here. The test clients must have a GUI
	 * registered before being started.
	 */
	if (displayGUI) {
	    GUI gui = new GUI(this);
	    gui.pack();
	    gui.setVisible(true);
	} else {
	    new NullGUI(this);
	}
	for (int i=0; i<threadCount; i++) {
	    Runnable client = (Runnable) clients.get(i);
	    Thread runnerThread = new Thread(client, "Secure Client " + i);
	    threadArray[i] = runnerThread;
	    runnerThread.start();
	}
	for (int i=threadArray.length; --i >= 0; ) {
	    try {
		threadArray[i].join();
	    } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
	    }
	    TestClient client = (TestClient) clients.get(i);
	    totalFailures += client.getFailureCount();
	}
        if (exitOnCompletion) {
            int failureCode = ((totalFailures == 0) ? TestCoordinator.SUCCESS
                                                    : TestCoordinator.FAILURE);
            abortRun(failureCode);
        }
    }

    /**
     * This method just exits without performing any cleanup,
     * so that any hung threads or calls in progress won't prevent a return
     * to the system.
     *
     * @param failureCode a return code passed to the underlying OS via
     * <code>System.exit</code>.
     */
    public void abortRun(int failureCode) {
        System.exit(failureCode);
    }

    /*
     * returns a copy of the test Set.
     *
     * @return a Set containing the tests specified on the command
     *         line. An empty set is returned if no commands line
     *         arguments were present.
     */
    public Set getTests() {
	return (Set) ((HashSet) tests).clone();
    }

    /**
     * return the collection of test clients to run. A reference to
     * the internal collection is returned, so the caller must
     * not modify the collection.
     *
     * @return the collection of tests
     */
     public Collection getTestClients() {
	return clients;
    }

    /**
     * return the number of threads to run. This is somewhat redundant, since
     * this method should always return the same value as
     * <code>getTestClients().size()</code>.
     *
     * @return the number of threads
     */
    public int getThreadCount() {
	return threadCount;
    }

    /**
     * Return an <code>InstanceCarrier</code> to be used when the wrapper
     * is disabled.
     *
     * @return an <code>InstanceCarrier</code> which may be used to obtain
     * a reference to one of the SecureClient objects created by this
     * <code>TestCoordinator</code>.
     */
     public InstanceCarrier getDefaultInstanceCarrier() {
	return new DefaultCarrier();
    }

    private static class DefaultCarrier implements InstanceCarrier,
                                                   Serializable {

	/**
	 * Return an instance of a SecureClient. The <code>TestClient</code>
	 * returned by this method should not be used unless the run is
	 * single threaded.
	 *
	 * @return a SecureClient instance created by this
	 * <code>TestCoordinator.</code>
	 */
	 public TestClient getInstance() {
	    return End2EndTest.defaultInstance;
	}
    }
}
