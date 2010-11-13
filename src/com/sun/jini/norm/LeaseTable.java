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

import java.util.HashMap;
import java.util.Map;

import net.jini.core.lease.Lease;

/**
 * Maps client leases to the wrapper objects that we use internally.
 * <p>
 * Internally we keep two tables of client leases, one mapping client
 * leases to client lease wrappers, the other a list of client lease
 * wrappers whose leases could not be unpacked (aka "deformed wrappers")
 * when they were added (and thus could not be put in the map).  This
 * bifurcation is not exposed to the client.
 * <p>
 * Unless otherwise noted the methods of this class are not thread safe.
 *
 * @author Sun Microsystems, Inc.
 */
class LeaseTable {
    /**
     * The list we store deformed wrappers in.
     */
    final private DeformedLeaseList deformedLeases = new DeformedLeaseList();

    /**
     * The map we use to map client leases to wrappers.
     */
    final private Map leaseTable = new HashMap();

    /**
     * Find the client lease wrapper associated with the passed lease.
     *
     * @param clientLease the lease we need the wrapper for
     * @return the wrapper associated with the passed lease, or
     *         <code>null</code> if we don't know about this lease
     */
    ClientLeaseWrapper get(Lease clientLease) {
	final ClientLeaseWrapper clw = 
	    (ClientLeaseWrapper) leaseTable.get(clientLease);
	if (clw == null) {
	    /*
	     * Could it be that this was a lease that could not be
	     * recovered? Check the deformed list.
	     */
	    return deformedLeases.query(clientLease, leaseTable);
	} else {
	    return clw;
	}
    }

    /**
     * Add a mapping from lease wrapper to client lease.  Gets client
     * lease from wrapper.
     *
     * @param clw client lease wrapper, and client lease to add to
     *        table
     */
    void put(ClientLeaseWrapper clw) {
	if (clw.isDeformed()) {
	    deformedLeases.add(clw);
	} else {
	    leaseTable.put(clw.getClientLease(), clw);
	}
    }

    /**
     * Remove a lease from the table.
     *
     * @param clw client lease wrapper for the lease we want to
     *	      remove
     */
    void remove(ClientLeaseWrapper clw) {
	if (!clw.isDeformed()) {
	    /*
	     * Not deformed, we need to remove both the client lease and
	     * the wrapper from the leaseTable.
	     */
	    leaseTable.remove(clw.getClientLease());
	} else {
	    /* Deformed, we need to remove the lease from the deformed
	     * list.
	     */
	    deformedLeases.remove(clw);
	}
    }
}
