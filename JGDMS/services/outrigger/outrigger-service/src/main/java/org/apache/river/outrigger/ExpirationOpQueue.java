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
package org.apache.river.outrigger;

import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.id.Uuid;

/**
 * Logs expiration of leases and asynchronously persists them to disk.
 */
class ExpirationOpQueue extends Thread {
    /** The queue of expirations to log */
    private final LinkedList queue = new LinkedList();

    /** The server we are working for */
    private final OutriggerServerImpl server;

    /** Logger for logging exceptions */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.leaseLoggerName);

    /**
     * Create a new <code>ExpirationOpQueue</code> that
     * will handle lease expiration logging for the 
     * specified server.
     * @param server the <code>OutriggerServerImpl</code> to 
     *        log for.
     */
    ExpirationOpQueue(OutriggerServerImpl server) {
	super("Expiration Op Queue");
	this.server = server;
    }

    /**
     * Enqueue the logging of the expiration of the specified lease.
     * @param cookie The cookie of the lease that has expired.
     */
    synchronized void enqueue(Uuid cookie) {
	queue.add(cookie);
	notifyAll();
    }

    /**
     * Stop the queue
     */
    void terminate() {
        interrupt();
        synchronized (this){
	notifyAll();
    }
    }

    public void run() {
	while (!Thread.currentThread().isInterrupted()) { // ok not to lock since it starts false
	    try {
		final Uuid cookie;
		synchronized (this) { 
		    while (queue.isEmpty()) {
			wait();
		    }
		    cookie = (Uuid)queue.removeFirst();
		}
		
		server.cancelOp(cookie, true);
	    } catch (InterruptedException e){
                Thread.currentThread().interrupt(); // restore
                return;
	    } catch (Throwable t) {
		try {
		    logger.log(Level.INFO,
			       "ExpirationOpQueue.run encountered " +
			           t.getClass().getName() + ", continuing", 
			       t);
		} catch (Throwable tt) {
		    // don't let a problem in logging kill the thread
		}
	    }
	}
    }
}


