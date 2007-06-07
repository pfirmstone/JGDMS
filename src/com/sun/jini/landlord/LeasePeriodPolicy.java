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
package com.sun.jini.landlord;

import net.jini.core.lease.LeaseDeniedException;

/**
 * Interface for objects that define what policy to use
 * when calculating lease grants and renewals.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface LeasePeriodPolicy {
    /**
     * Simple class that holds a pair of times, the
     * new expiration and duration for a lease.
     */
    public class Result {
	/** The expiration time. */
	final public long expiration;

	/** The duration. */
	final public long duration;

	/**
	 * Simple constructor to create a new <code>Result</code>
	 * object.  
	 * @param expiration the value for the <code>expiration</code> field.
	 * @param duration the value for the <code>duration</code> field.
	 */
	public Result(long expiration, long duration) {
	    this.expiration = expiration;
	    this.duration = duration;
	}
    }

    /**
     * Calculate the initial expiration and duration for
     * a new lease.
     * @param resource the server side representation of the new lease that
     *        needs an initial expiration and duration
     * @param requestedDuration the initial duration the 
     *        requester of the resources would like. May be
     *        <code>Lease.ANY</code>
     * @return A <code>Result</code> that holds
     *         both the expiration and duration.
     * @throws LeaseDeniedException if the grant request 
     *         is denied.
     * @throws IllegalArgumentException if the 
     *         requested duration is not <code>Lease.ANY</code> 
     *         and is negative.
     */
    public Result grant(LeasedResource resource, long requestedDuration)
	throws LeaseDeniedException;

    /**
     * Calculate the expiration and duration for an
     * existing lease that is being renewed.
     * @param resource the server side representation of the lease that is
     *                 being renewed
     * @param requestedDuration the duration the client is requesting. May be
     *        <code>Lease.ANY</code>.
     * @return A <code>Result</code> that holds
     *         both the expiration and duration.
     * @throws LeaseDeniedException if the renewal request 
     *         is denied.
     * @throws IllegalArgumentException if the 
     *         requested duration is not <code>Lease.ANY</code> 
     *         and is negative.
     */
    public Result renew(LeasedResource resource, long requestedDuration)
        throws LeaseDeniedException;
}
