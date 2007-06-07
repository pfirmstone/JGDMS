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
package com.sun.jini.norm;

import net.jini.core.lease.Lease;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * <code>DeformedLeaseList</code> provides a list for keeping track of
 * of client lease that could not be fully recovered during log
 * recovery.  Unless otherwise noted all the methods in the class are
 * not thread safe.
 *
 * @author Sun Microsystems, Inc.
 */
class DeformedLeaseList {
    /** 
     * A list of all the leases we are tracking.
     */
    private Set leases = null;

    /**
     * Add a wrapped client lease to the list of deformed client leases.
     * This method is not thread safe.
     *
     * @param clw a deformed client lease
     */
    void add(ClientLeaseWrapper clw) {
	if (leases == null)
	    leases =  new HashSet();
	leases.add(clw);
    }

    /**
     * Remove a lease from the list.  This method is not thread safe.
     *
     * @param clw a deformed client lease
     */
    void remove(ClientLeaseWrapper clw) {
	if (leases != null) {
	    leases.remove(clw);
	    if (leases.isEmpty()) {
		leases = null;
	    }
	}
    }

    /**
     * Query the list to see if the specified client lease is in this list
     * of deformed leases.  If it is, return the existing client lease
     * wrapper for this client lease.  During the query, any client
     * lease wrappers (including the target if found) which are
     * discovered to no longer be deformed will be inserted into the
     * passed table using their associated client lease as a key, and
     * removed from the list of deformed leases. This method is not
     * thread safe.
     *
     * @param clientLease the client lease which may be
     *        referenced by a deformed client lease wrapper
     * @param table a table mapping client leases to client lease
     *        wrappers.  Any client lease wrappers encountered during
     *        the query that are no longer deformed will be placed in
     *        this table.  It is assumed that no other thread is trying
     *        to access this table.
     * @return if it could be found, the client lease wrapper that goes
     *         with <code>cl</code>, <code>null</code> otherwise
     */
    ClientLeaseWrapper query(Lease clientLease, Map table) {
	// Recheck now that we have the lock
	if (leases == null) 
	    return null;
	
	try {
	    for (Iterator i = leases.iterator(); i.hasNext(); ) {
		ClientLeaseWrapper clw = (ClientLeaseWrapper) i.next();
		final Lease candidate = clw.getClientLease();
		
		if (candidate != null) {
		    // We've got a live one!, remove it from the
		    // deformed list and place it in the table
		    i.remove();
		    table.put(candidate, clw);

		    // Is it the one we are looking for?
		    if (candidate.equals(clientLease)) {
			return clw;
		    }
		} 
	    }
	} finally {
	    // Can we GC the leases set?
	    if (leases.isEmpty()) 
		leases = null;
	}

	return null;
    }
}
