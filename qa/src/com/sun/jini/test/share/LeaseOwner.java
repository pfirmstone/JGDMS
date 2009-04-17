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
package com.sun.jini.test.share;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

/**
 * Abstract base class for objects that track the condition of a particular
 * lease that has been given to a Lease Renewal Service for testing
 */
public abstract class LeaseOwner {
    /**
     * The renewal service is renewing the given lease. 
     * <p>
     * @param extension The requested extension for the lease.
     * @return Ether a <code>Long</code> representing the new duration
     * for the lease it can simulate a  <code>RemoteException</code>,
     * <code>Error</code>, or <code>RuntimeException</code> by
     * returning an object of one of these types
     * @throws LeaseDeniedException if it wants to
     * @throws UnknownLeaseException if it wants to
     */
    public abstract Object renew(long extension) 
        throws LeaseDeniedException, UnknownLeaseException;

    /**
     * The renewal service is canceling the given lease 
     * @return If the method want to simulate a
     * <code>RemoteException</code>, <code>Error</code>, or
     * <code>RuntimeException</code> it can do by returning an object
     * of one of these types.
     * @throws UnknownLeaseException if it wants to     
     */
    public abstract Throwable cancel() throws UnknownLeaseException;

    /**
     * The renewal service is renewing the lease through a lease map.
     * @param extension The requested extension for the lease.
     * @return The new duration for the lease.  Note owner can not
     * directly simulate a runtime/remote exception and/or error using
     * this method.
     */
    public abstract long batchRenew(long extension) 
	throws LeaseDeniedException, UnknownLeaseException;

    /**
     * The renewal service is canceling the lease through a lease map.
     * Note owner can not directly simulate a runtime/remote exception
     * and/or error using this method.
     */
    public abstract void batchCancel() throws UnknownLeaseException;
}
