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
package com.sun.jini.outrigger;

import java.rmi.RemoteException;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.id.Uuid;

/**
 * The iterator that is returned to the client.
 * <code>IteratorProxy</code> uses a <code>RemoteIter</code> reference
 * to talk to the server.  The <code>RemoteIter</code> object is its
 * partner on the server side.
 * <p>
 * Note, because there is no way to duplicate one of these objects, or
 * get a new reference to the underlying iteration one (a situation
 * that is unlikely to change since part of the iteration is local), we
 * don't need to do anything special for equality. The default
 * implementations of <code>equals</code> and <code>hashCode</code>
 * work fine.
 */
class IteratorProxy implements AdminIterator {
    /**
     * The <code>Uuid</code> of the iteration this 
     * proxy is associated with.
     */
    private final Uuid iterationUuid;

    /** 
     * Reference to the server.
     * Only assigned by this class.
     */
    OutriggerAdmin server;

    /** Last set of reps we got from the server */
    private EntryRep[] reps;

    /** 
     * Index of the next entry in rep to return.  If <code>delete()</code>
     * we will call <code>iter.delete()</code> next - 1
     */
    private int next = -1;	

    /**
     * How many entries to ask for each time we go to the server
     */
    private final int fetchSize;

    /** ID of last entry we got from server */
    private Uuid lastId = null;
    
    /**
     * Create client side iterator proxy.
     * @param iterationUuid The identity of the iteration this proxy is for.
     * @param server reference to remote server for the space.
     * @param fetchSize Number of entries to ask for when it goes to the
     *                  server
     * @throws NullPointerException if <code>server</code> or
     *         <code>iterationUuid</code> is <code>null</code>.     
     */
    IteratorProxy(Uuid iterationUuid, OutriggerAdmin server, int fetchSize) {
	if (iterationUuid == null)
	    throw new NullPointerException("iterationUuid must be non-null");

	if (server == null)
	    throw new NullPointerException("server must be non-null");
       
	if (fetchSize <= 0 && fetchSize != JavaSpaceAdmin.USE_DEFAULT)
	    throw new IllegalArgumentException
		("fetchSize must be postive or JavaSpaceAdmin.USE_DEFAULT");

	this.iterationUuid = iterationUuid;
	this.server = server;
	this.fetchSize = fetchSize;
    }

    // purposefully inherit doc comment from supertype
    public Entry next() throws UnusableEntryException, RemoteException {
	assertOpen();
	
	if (next < 0 || next >= reps.length) {
	    // Need to get more Entries
	    reps = server.nextReps(iterationUuid, fetchSize, lastId);
	    
	    if (reps == null) {
		// Finished
		close();
		return null;
	    }		
	    
	    lastId = reps[reps.length-1].id();
	    
	    next = 0;
	}
	
	// This may throw UnusableEntryException, but that's
	// the right thing
	return reps[next++].entry();
    }

    // purposefully inherit doc comment from supertype
    public void delete() throws RemoteException {
	if (next < 0) 
	    throw new IllegalStateException("AdminIterator:Can't call " +
					    "delete before calling next()");

	assertOpen();
	server.delete(iterationUuid, reps[next-1].id());
    }
    
    // purposefully inherit doc comment from supertype
    public void close() throws RemoteException {
	if (server != null) {
	    // always set to null, and then try to use the remote method,
	    // which is actually optional
	    OutriggerAdmin it = server;
	    server = null;
	    reps = null;
	    it.close(iterationUuid);
	}
    }

    /**
     * Throw <code>IllegalStateException</code> if this iterator
     * has been closed; otherwise just return.
     */
    private void assertOpen() throws IllegalStateException {
	if (server == null) {
	    throw new IllegalStateException("closed AdminIterator");
	}
    }

    public String toString() {
	return getClass().getName() + " for " + iterationUuid + 
	    " (through " + server + ")";
    }
}
