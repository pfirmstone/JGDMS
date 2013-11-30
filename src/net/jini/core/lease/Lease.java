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

package net.jini.core.lease;

import java.rmi.RemoteException;

/**
 * The Lease interface defines a type of object that is returned to the
 * lease holder and issued by the lease grantor.  Particular instances of
 * the Lease type will be created by the grantors of a lease, and returned
 * to the holder of the lease as part of the return value from a call that
 * allocates a leased resource.  The call that requests a leased resource
 * will typically include a requested duration for the lease.  If the request
 * is for a particular duration, the lease grantor is required to grant a
 * lease of no more than the requested period of time. A lease may be granted
 * for a period of time shorter than that requested.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public interface Lease {

    /** 
     * Used to request a lease that never expires. 
     */
    final long FOREVER = Long.MAX_VALUE;

    /**
     * Used by the requestor to indicate that there is no particular lease
     * time desired, and that the grantor of the lease should supply a time
     * that is most convenient for the grantor.
     */
    final long ANY = -1;

    /**
     * The serialized form of the lease will convert the time of lease
     * expiration into a duration (in milliseconds) from the time of
     * serialization.  This form is best used when transmitting a Lease
     * object from one address space to another (via an RMI call) where
     * it cannot be assumed that the address spaces have synchronized clocks.
     */
    final int DURATION = 1;

    /**
     * The serialized form of the lease will contain the time of expiration
     * stored as an absolute time, represented in terms of milliseconds since
     * the beginning of the epoch.
     */
    final int ABSOLUTE = 2;

    /**
     * Returns a <code>long</code> that indicates the time that the
     * lease will expire. This time is represented as
     * milliseconds from the beginning of the epoch, relative to the local
     * clock.
     * 
     * @return a <code>long</code> that indicates the time that the
     *         lease will expire
     */
    long getExpiration();

    /**
     * Used by the lease holder to indicate that it is no longer interested
     * in the resource or information held by the lease.  If the leased
     * information or resource could cause a callback to the lease holder
     * (or some other object on behalf of the lease holder), the lease
     * grantor should not issue such a callback after the lease has been
     * cancelled.  The overall effect of a cancel call is the same as
     * lease expiration, but instead of happening at the end of a pre-agreed
     * duration it happens immediately.
     *
     * @throws UnknownLeaseException the lease being cancelled is unknown
     *         to the lease grantor
     * @throws RemoteException
     */
    void cancel() throws UnknownLeaseException, RemoteException;

    /**
     * Used to renew a lease for an additional period of time, specified in
     * milliseconds.  This duration is not added to the original lease, but
     * is used to determine a new expiration time for the existing lease.
     * If the renewal is granted this is reflected in value returned by
     * getExpiration.  If the renewal fails, the lease is left intact for
     * the same duration that was in force prior to the call to renew.
     *
     * @param duration the requested duration in milliseconds
     *
     * @throws LeaseDeniedException the lease grantor is unable or
     *         unwilling to renew the lease
     * @throws UnknownLeaseException the lease being renewed is unknown
     *         to the lease grantor
     * @throws RemoteException
     */
    void renew(long duration)
	throws LeaseDeniedException, UnknownLeaseException, RemoteException;

    /**
     * Sets the format to use when serializing the lease.
     *
     * @param format DURATION or ABSOLUTE
     * @see #getSerialFormat
     */
    void setSerialFormat(int format);

    /**
     * Returns the format that will be used to serialize the lease.
     *
     * @return an <tt>int</tt> representing the serial format value
     * @see #setSerialFormat
     */
    int getSerialFormat();

    /**
     * Creates a Map object that can contain leases whose renewal or
     * cancellation can be batched, and adds the current lease to that map.
     * The current lease is put in the map with the duration value given
     * by the parameter.
     *
     * @param duration the duration to put into a Long and use as the
     * value for the current lease in the created LeaseMap
     *
     * @return the created <tt>LeaseMap</tt> object
     */
    LeaseMap<? extends Lease, Long> createLeaseMap(long duration);

    /**
     * Returns a boolean indicating whether or not the lease given as a
     * parameter can be batched (placed in the same LeaseMap) with the
     * current lease.  Whether or not two Lease objects can be batched
     * is an implementation detail determined by the objects.
     * 
     * @param lease the <tt>Lease</tt> to be evaluated
     * @return a boolean indicating whether or not the lease given as a
     *         parameter can be batched (placed in the same LeaseMap) with 
     *         the current lease
     */
    boolean canBatch(Lease lease);
}
