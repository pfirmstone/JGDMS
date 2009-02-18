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
package com.sun.jini.test.share.reggie;

import net.jini.core.lease.*;

/**
 * The base class for lease proxies.
 *
 * @author Sun Microsystems, Inc.
 *
 */
abstract class RegistrarLease extends com.sun.jini.lease.AbstractLease {

    private static final long serialVersionUID = 1286538697644640310L;

    /**
     * The registrar.
     *
     * @serial
     */
    protected final Registrar server;
    /**
     * The internal lease id.
     *
     * @serial
     */
    protected final long leaseID;

    /** Simple constructor. */
    protected RegistrarLease(Registrar server, long leaseID, long expiration) {
	super(expiration);
	this.server = server;
	this.leaseID = leaseID;
    }

    /** Create a lease map. */
    public LeaseMap createLeaseMap(long duration) {
	return new RegistrarLeaseMap(this, duration);
    }

    /**
     * Two leases can be batched if they are both RegistrarLeases and
     * have the same server.
     */
    public boolean canBatch(Lease lease) {
	return (lease instanceof RegistrarLease &&
		server.equals(((RegistrarLease)lease).server));
    }

    /** Return the registrar. */
    Registrar getRegistrar() {
	return server;
    }

    /** Returns the lease ID */
    long getLeaseID() {
	return leaseID;
    }

    /** Returns the service ID, or the event ID as a Long */
    abstract Object getRegID();

    /** Set the expiration. */
    void setExpiration(long expiration) {
	this.expiration = expiration;
    }
}
