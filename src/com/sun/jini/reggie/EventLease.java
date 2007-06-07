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
package com.sun.jini.reggie;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;

/**
 * When a registrar (lookup service) grants a lease on an event registration
 * on behalf of some object (client), a proxy is employed to allow the client
 * to interact with the lease; this class is the implementation of that proxy.
 * Clients only see instances of this class via the Lease interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class EventLease extends RegistrarLease {

    private static final long serialVersionUID = 2L;
    /** The type of the lease used in toString() calls. */
    private static final String LEASE_TYPE = "event";

    /**
     * The eventID returned in the EventRegistration.
     *
     * @serial
     */
    final long eventID;

    /**
     * Returns EventLease or ConstrainableEventLease instance, depending on
     * whether given server implements RemoteMethodControl.
     */
    static EventLease getInstance(Registrar server,
				  ServiceID registrarID,
				  long eventID,
				  Uuid leaseID,
				  long expiration)
    {
	return (server instanceof RemoteMethodControl) ?
	    new ConstrainableEventLease(
		server, registrarID, eventID, leaseID, expiration, null) :
	    new EventLease(server, registrarID, eventID, leaseID, expiration);
    }

    /** Constructor for use by getInstance(), ConstrainableEventLease. */
    EventLease(Registrar server,
	       ServiceID registrarID,
	       long eventID,
	       Uuid leaseID,
	       long expiration)
    {
	super(server, registrarID, leaseID, expiration);
	this.eventID = eventID;
    }

    // This method's javadoc is inherited from an interface of this class
    public void cancel() throws UnknownLeaseException, RemoteException {
	server.cancelEventLease(eventID, leaseID);
    }

    /** 
     * Renews the event lease associated with an instance of this class.
     * Each instance of this class corresponds to a lease on an event
     * registration for a particular client. This method renews that 
     * lease on behalf of the client.
     *
     * @param duration the requested duration for the lease being renewed
     * @return long value representing the new duration that was granted
     *         for the renewed lease. Note that the duration returned may
     *         be less than the duration requested.
     * @exception UnknownLeaseException indicates the lease does not exist;
     *            typically because the lease has expired.
     */
    protected long doRenew(long duration)
	throws UnknownLeaseException, RemoteException
    {
	return server.renewEventLease(eventID, leaseID, duration);
    }

    // This method's javadoc is inherited from a super class of this class
    Object getRegID() {
	return new Long(eventID);
    }
    
    // inherit javadoc
    String getLeaseType() {
	return LEASE_TYPE;
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}
