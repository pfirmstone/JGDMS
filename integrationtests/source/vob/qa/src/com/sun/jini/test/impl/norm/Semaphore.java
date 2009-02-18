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
package com.sun.jini.test.impl.norm;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple Semaphore
 */
class Semaphore {

    private Logger logger = Logger.getLogger("com.sun.jini.qa.harness");

    /** Current Number of holders */
    private int holders;

    Semaphore(int holders) {
	this.holders = holders;
    }

    
    /**
     * Increment the holders
     */
    synchronized void inc() {
	holders++;
	logger.log(Level.FINER, "incremented holders to " + holders);
	notifyAll();
    }

    /**
     * Decrement the holders
     */
    synchronized void dec() {
	holders--;
	logger.log(Level.FINER, "decremented holders to " + holders);
	notifyAll();
    }

    /**
     * Set holders to zero 
     */
    synchronized void zero() {
	holders = 0;
	logger.log(Level.FINER, "holders set to zero");
	notifyAll();
    }

    /**
     * Wait until number of holders goes to zero
     * @param waitFor How long to wait for
     * @return Number of holders
     * @throws InterruptedException if this thread is interupted.
     */
    int waitOnZero(long waitFor) throws InterruptedException {
	long now = System.currentTimeMillis();
	long until = now + waitFor;
	if (until < 0) // Overflow
	    until = Long.MAX_VALUE;

	synchronized (this) {
	    while (holders != 0) {		
		wait(until - now);
		now = System.currentTimeMillis();
		if (now >= until)
		    return holders;
	    } 
	    return holders;
	}
    }

    /**
     * Wait until number of holders goes to zero
     * @throws InterruptedException if this thread is interupted.
     */
    synchronized void waitOnZero() throws InterruptedException {
	while (holders != 0) 
	    wait();
    }

    /**
     * Retun the number holders 
     */
    synchronized int get() {
	return holders;
    }
}
