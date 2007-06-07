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
package com.sun.jini.thread;

/**
 * This class provides a blocking mechanism that will not proceed while
 * some operation bounded by this object is in progress.  The thread
 * performing the operation invokes <code>start</code> and
 * <code>stop</code> on this object when it starts and stops the
 * operation in progress.  The thread that wants to block itself while
 * the operation is in progress invokes <code>waitWhileStarted</code>,
 * which will block if <code>start</code> has been invoked without a
 * matching <code>stop</code>.  The block will continue until a
 * matching <code>stop</code> is invoked.  Each <code>start</code> adds
 * one to the number of operations; each <code>stop</code> subtracts
 * one.  When the number of operations is zero, the operation bounded
 * by this object is not in progress, and <code>waitWhileStarted</code>
 * will return.
 * <p>
 * The operation can also be blocked by invoking <code>block</code> and
 * <code>unblock</code>, which nest like <code>start</code> and
 * <code>stop</code>.  When a <code>block</code> is invoked on this
 * object, no <code>start</code> will proceed until the matching
 * <code>unblock</code> call is made.  The reverse is also true: when a
 * <code>stop</code> call has been made on this object, no
 * <code>block</code> call will proceed until the matching
 * <code>stop</code> call is made.  A <code>waitUntilUnblocked</code>
 * method allows selective waiting for the unblocked state without
 * invoking <code>start</code>.
 * <p>
 * A <code>waitUntilQuiet</code> call waits until the object is neither
 * blocked nor stopped.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class InProgress {
    /** The state. */
    private int		count = 0;
    private String	name;
    private boolean	debug;

    public InProgress(String name) {
	this.name = name;
    }

    /** Signal the start of the operation this object bounds. */
    public synchronized void start() throws InterruptedException {
	while (count < 0)
	    wait();
	count++;
	if (debug)
	    show("start");
    }

    /** Signal the stop of the operation this object bounds. */
    public synchronized void stop() {
	if (--count == 0)
	    notifyAll();
	if (count < 0) {
	    count = 0;
	    throw new IllegalMonitorStateException("Too many stop invocations");
	    // set to a sane state in case the exception is caught and this
	    // object gets re-used
	}
	if (debug)
	    show("stop");
    }

    /**
     * Return <code>true</code> if at least one <code>start</code> has been
     * invoked without its corresponding <code>stop</code>.
     */
    public boolean inProgress() {
	return (count > 0);
    }

    /**
     * Wait if the operation bounded by this object is in progress, i.e.,
     * between a <code>start</code> and a <code>stop</code>.
     */
    public synchronized void waitWhileStarted() throws InterruptedException {
	if (debug)
	    show("waitWhileStarted");
	while (count > 0) {
	    wait();
	    if (debug)
		show("waitWhileStarted");
	}
    }

    /** Signal the blocking of the operation this object bounds. */
    public synchronized void block() throws InterruptedException {
	while (count > 0)
	    wait();
	count--;
	if (debug)
	    show("block");
    }

    /** Signal the unblocking of the operation this object bounds. */
    public synchronized void unblock() {
	if (++count == 0)
	    notifyAll();
	if (count > 0) {
	    count = 0;
	    throw new
		IllegalMonitorStateException("Too many unblock invocations");
	    // set to a sane state in case the exception is caught and this
	    // object gets re-used
	}
	if (debug)
	    show("unblock");
    }

    /**
     * Return <code>true</code> if at least one <code>block</code> has been
     * invoked without its corresponding <code>unblock</code>.
     */
    public boolean blocked() {
	return (count < 0);
    }

    /**
     * Wait if the operation bounded by this object has been blocked, i.e.,
     * between a <code>block</code> and a <code>unblock</code>.
     */
    public synchronized void waitWhileBlocked() throws InterruptedException {
	if (debug)
	    show("waitWhileBlocked");
	while (count < 0) {
	    wait();
	    if (debug)
		show("waitWhileBlocked");
	}
    }

    /**
     * Wait if the operation bounded by this object is either started
     * or blocked.
     */
    public synchronized void waitUntilQuiet() throws InterruptedException {
	if (debug)
	    show("waitUntilQuiet");
	while (count != 0) {
	    wait();
	    if (debug)
		show("waitUntilQuiet");
	}
    }

    /**
     * Set the debug mode on or off.
     */
    public void debug(boolean debugOn) {
	debug = debugOn;
    }

    /**
     * Show the current state of this object (used in debug mode).
     */
    private void show(String label) {
	System.out.print(name);
	System.out.print(":");
	System.out.print(label);
	System.out.print(": count = ");
	System.out.println(count);
    }
}
