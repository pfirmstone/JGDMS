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

package com.sun.jini.jeri.internal.mux;

import java.io.IOException;

/**
 * An IOFuture represents an I/O operation that may or may not have
 * completed yet.
 *
 * @author	Sun Microsystems, Inc.
 * 
 */
final class IOFuture {

    private boolean done = false;
    private IOException exception = null;

    IOFuture() { }

    /**
     * Waits until the I/O operation has completed.  If this method
     * returns normally, then the I/O operation has completed
     * successfully.  If this method throws IOException, then the
     * I/O operation failed.
     *
     * REMIND: Maybe we should support a timeout here, as a paranoid
     * escape hatch; if this wait really takes a long time, something
     * has gone dreadfully wrong.  To a large extent, we're really
     * depending on someone's finally clause to make sure that pending
     * instances of this class always get notified somehow.
     *
     * @throws	IOException if the I/O operation failed
     *
     * @throws	InterruptedException if the current thread was
     * interrupted while waiting for the I/O to complete.
     */
    synchronized void waitUntilDone()
	throws IOException, InterruptedException
    {
	while (!done) {
	    wait();
	}
	if (exception != null) {
	    exception.fillInStackTrace();
	    throw exception;
	}
    }

    /**
     * Signals that this I/O operation has completed successfully.
     */
    synchronized void done() {
	assert !done;
	done = true;
	notifyAll();
    }

    /**
     * Signals that this I/O operation has failed (with details of the
     * failure in the given IOException).
     *
     * @param	e detail of the I/O operation's failure
     */
    synchronized void done(IOException e) {
	if (done) {
	    /*
	     * This shouldn't normally happen, but it's difficult to prevent
	     * in bizarre failure scenarios (like an OutOfMemoryError).
	     */
	    return;
	}
	if (e == null) {
	    throw new NullPointerException();
	}
	this.exception = e;
	done = true;
	notifyAll();
    }
}
