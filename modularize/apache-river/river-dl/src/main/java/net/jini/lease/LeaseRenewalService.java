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
package net.jini.lease;

import java.rmi.RemoteException;

/**
 * Interface to the lease renewal service. The interface is not a remote
 * interface; each implementation of the renewal service exports proxy
 * objects that implement the <code>LeaseRenewalService</code> interface
 * that use an implementation-specific protocol to communicate with the
 * actual remote server. All of the proxy methods obey normal RMI remote
 * interface semantics. Two proxy objects are equal if they are proxies
 * for the same renewal service. Every method invocation (on both a
 * <code>LeaseRenewalService</code> and any <code>LeaseRenewalSet</code>
 * it has created) is atomic with respect to other invocations.
 * 
 * @author Sun Microsystems, Inc.
 * @see LeaseRenewalSet 
 */

public interface LeaseRenewalService {
    /**
     * Create a new <code>LeaseRenewalSet</code> that the client can
     * populate with leases to be renewed. The initial duration of the
     * lease granted on this set will be less than or equal to
     * <code>leaseDuration</code>.
     * <p>
     * Two calls to this method should never return objects that are
     * equal.
     *
     * @param leaseDuration requested lease duration in milliseconds
     * @return a new <code>LeaseRenewalSet</code> in the renewal service
     * @throws IllegalArgumentException if <code>leaseDuration</code> is
     *	       not positive, <code>Lease.ANY</code>, or
     *	       <code>Lease.FOREVER</code>
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public LeaseRenewalSet createLeaseRenewalSet(long leaseDuration) 
	throws RemoteException;
}
