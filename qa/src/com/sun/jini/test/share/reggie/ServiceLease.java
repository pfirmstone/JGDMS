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
import net.jini.core.lookup.ServiceID;
import java.rmi.RemoteException;

/**
 * A ServiceLease is a proxy for a service registration lease at a registrar.
 * Clients only see instances via the Lease interface.
 *
 * 
 *
 */
class ServiceLease extends RegistrarLease {

    private static final long serialVersionUID = 4366637533663829830L;

    /**
     * The service id assigned at registration.
     *
     * @serial
     */
    private final ServiceID serviceID;

    /** Simple constructor. */
    ServiceLease(Registrar server,
		 ServiceID serviceID,
		 long leaseID,
		 long expiration)
    {
	super(server, leaseID, expiration);
	this.serviceID = serviceID;
    }

    public void cancel() throws UnknownLeaseException, RemoteException {
	server.cancelServiceLease(serviceID, leaseID);
    }

    /** Do the actual renew. */
    protected long doRenew(long duration)
	throws UnknownLeaseException, RemoteException
    {
	return server.renewServiceLease(serviceID, leaseID, duration);
    }

    /** Returns the service ID */
    ServiceID getServiceID() {
	return serviceID;
    }

    Object getRegID() {
	return serviceID;
    }

    /** Returns a hash code based on content. */
    public int hashCode() {
	return server.hashCode() ^ serviceID.hashCode() ^ (int)leaseID;
    }

    /** Equal if for the same serviceID and leaseID at the same registrar. */
    public boolean equals(Object obj) {
	if (!(obj instanceof ServiceLease))
	    return false;
	ServiceLease ls = (ServiceLease)obj;
	return (server.equals(ls.server) &&
		serviceID.equals(ls.serviceID) &&
		leaseID == ls.leaseID);
    }
}
