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

package org.apache.river.qa.harness;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Detects timeouts and calls a timeout handler.
 */
class Timeout implements Runnable {

    /** An interface defining the timeout behavior */
    static interface TimeoutHandler {

	/**
	 * Perform an action required for a timeout.
	 */
	public void handleTimeout();
    }

    /** the timeout handler to call after the timer expires */
    private TimeoutHandler timeoutHandler;

    /** the thread which implements the timeout interval */
    private Thread sleepThread;

    /** flag set when a timeout is detected */
    private boolean timedOut;

    /** the timeout interval */
    private long interval;

    /**
     * Construct a timeout object to wait the given <code>interval</code>
     * and interrupts the given <code>targetThread</code> if the
     * interval passes before the <code>cancel</code> method is called.
     *
     * @param timeoutHandler the handler to call if a timeout is detected
     * @param interval the timeout interval in milliseconds
     */
    Timeout(TimeoutHandler timeoutHandler, int interval) {
	this.timeoutHandler = timeoutHandler;
	this.interval = interval;
    }

    /**
     * Starts the timeout clock
     */
    void start() {
	sleepThread = new Thread(this);
	sleepThread.start();
    }

    /**
     * Sleep for the timeout interval unless interrupted. If the
     * sleep completes without interruption, the <code>timedOut</code>
     * flag is set and the <code>TimeoutHandler.handleTimeout</code> method
     * is called. Early returns from <code>Thread.sleep</code> will
     * not cause premature indications of timeout.
     */
    public void run() {
	timedOut = false;
	long startTime = new Date().getTime();
	long delta = 0;
	while (delta < interval) {
	    try {
		Thread.sleep(interval - delta);
	    } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
		return;
	    }
	    delta = new Date().getTime() - startTime;
	}
	timedOut = true;
	timeoutHandler.handleTimeout();
	return;
    }

    /**
     * Cancel this timeout. An interrupt for this thread is fired.
     */
    void cancel() {
	sleepThread.interrupt();
    }

    /**
     * Return the state of the <code>timedOut</code> flag.
     *
     * @return <code>true</code> if the most recent call to <code>run</code>
     *         resulted in a timeout.
     */
    boolean timedOut() {
	return timedOut;
    }

    /**
     * Handler for thread timeouts. The handled thread is interrupted.
     */
    static class ThreadTimeoutHandler implements TimeoutHandler {

	/** the thread to interrupt */
	Thread thread;

	/**
	 * Construct the <code>TimeoutHandler</code>. The given 
	 * <code>Thread</code> is interrupted.
	 */
	public ThreadTimeoutHandler(Thread thread) {
	    this.thread = thread;
	}

	/**
	 * Interrupt the thread.
	 */
	public void handleTimeout() {
	    thread.interrupt();
	}
    }

    /**
     * Handler for socket timeouts. The handled socket is closed.
     */
    static class ServerSocketTimeoutHandler implements TimeoutHandler {

	/** the socket to close */
	ServerSocket socket;

	/**
	 * Construct the <code>TimeoutHandler</code>.
	 */
	public ServerSocketTimeoutHandler(ServerSocket socket) {
	    this.socket = socket;
	}

	/**
	 * close the socket.
	 */
	public void handleTimeout() {
	    try {
	        socket.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * Handler for InputStream timeouts. The handled stream is closed.
     */
    static class InputStreamTimeoutHandler implements TimeoutHandler {

	/** the socket to close */
	InputStream stream;

	/**
	 * Construct the <code>TimeoutHandler</code>.
	 */
	public InputStreamTimeoutHandler(InputStream stream) {
	    this.stream = stream;
	}

	/**
	 * close the stream.
	 */
	public void handleTimeout() {
	    try {
	        stream.close();
	    } catch (IOException e) {
	    }
	}
    } 
}
