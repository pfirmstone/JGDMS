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

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.lease.LeaseRenewalService;

import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.landlord.Landlord;
import com.sun.jini.proxy.ThrowThis;
import com.sun.jini.start.ServiceProxyAccessor;

/**
 * This interface is the private wire protocol to that the various
 * proxy objects created by a Norm server (the lease renewal service
 * itself, sets, leases, admins, etc.) use to communicate back to the
 * server.
 *
 * @author Sun Microsystems, Inc.
 */
interface NormServer extends Landlord, LeaseRenewalService, 
    Administrable, JoinAdmin, DestroyAdmin, ServiceProxyAccessor, Remote 
{
    /**
     * If calling <code>setExpirationWarningListener</code> with a
     * <code>null</code> listener, this is the value that should be passed.
     */
    final static long NO_LISTENER = -1;

    /**
     * Add a lease to a set.
     *
     * @param id what set the lease should be added to
     * @param leaseToRenew the lease to be added to the set
     * @param membershipDuration how long the lease should be in the set
     * @param renewDuration how long the lease should be renewed for
     *	      each time it is renewed
     * @throws ThrowThis when another exception has to be thrown by the proxy
     * @throws RemoteException if a communication-related exception occurs
     */
    public void renewFor(Uuid id, Lease leaseToRenew,
			 long membershipDuration, long renewDuration)
	throws RemoteException, ThrowThis;

    /**
     * Remove a lease from a set.
     *
     * @param id of set being operated on
     * @param leaseToRemove the lease to be removed from the set
     * @throws ThrowThis when another exception has to be thrown by the proxy
     * @throws RemoteException if a communication-related exception occurs
     */
    public Lease remove(Uuid id, Lease leaseToRemove) 
	throws RemoteException, ThrowThis;

    /**
     * Return all the leases in the set.  Returns <code>null</code>
     * or a zero-length array if there are no leases in the set.
     *
     * @param id of set being operated on
     * @return an object containing an array of {@link MarshalledInstance}s,
     *	       one for each lease
     * @throws ThrowThis when another exception has to be thrown by the proxy
     * @throws RemoteException if a communication-related exception occurs
     */
    public GetLeasesResult getLeases(Uuid id) 
	throws RemoteException, ThrowThis;

    /**
     * Set the expiration warning listener for a set.  Also used to 
     * cancel a registration.
     *
     * @param id of set being operated on
     * @param listener listener to be notified when this event occurs.
     *        Pass <code>null</code> to clear the registration.
     * @param minWarning how long be for the lease on the set expires
     *        should the event be sent. Ignored if <code>listener</code>
     *        is <code>null</code>.
     * @param handback an object to be handed back to the listener when
     *        the warning event occurs. Ignored if <code>listener</code>
     *        is <code>null</code>.
     * @return an <code>EventRegistration</code> object for the new
     *	       registration if <code>listener</code> is
     *	       non-<code>null</code> and <code>null</code> otherwise
     * @throws ThrowThis when another exception has to be thrown by the proxy
     * @throws RemoteException if a communication-related exception occurs
     */
    public EventRegistration setExpirationWarningListener(
			         Uuid                id,
	                         RemoteEventListener listener, 
				 long                minWarning, 
				 MarshalledObject    handback)
	throws RemoteException, ThrowThis;

    /**
     * Set the renewal failure listener for a set.  Also used to 
     * cancel a registration.
     * @param id of set being operated on
     * @param listener listener to be notified when this event occurs.
     *        Pass <code>null</code> to clear the registration.
     * @param handback an object to be handed back to the listener when
     *        the failure event occurs.  Ignored if
     *        <code>listener</code> is <code>null</code>.
     * @return an <code>EventRegistration</code> object for the new
     *	       registration if <code>listener</code> is
     *	       non-<code>null</code> and <code>null</code> otherwise.
     * @throws ThrowThis when another exception has to be thrown by the proxy
     * @throws RemoteException if a communication-related exception occurs
     */
    public EventRegistration setRenewalFailureListener(
			         Uuid                id,
	                         RemoteEventListener listener, 
				 MarshalledObject    handback)
	throws RemoteException, ThrowThis;
}
