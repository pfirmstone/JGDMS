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

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;

/**
 * Simple implementation of <code>LeasePeriodPolicy</code> that grants
 * lease times based on a fixed default and maximum lease. Will grant
 * renewals longer than the maximum if the current lease and the
 * request are both longer than the maximum.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class FixedLeasePeriodPolicy implements LeasePeriodPolicy {
    /** Under normal circumstances, the maximum lease or renewal to grant */
    final private long maximum;

    /** The default lease length */
    final private long defaultLength;

    /**
     * Create a new <code>FixedLeasePeriodPolicy</code> with 
     * the specified values for the maxium and default lease
     * lengths.
     * @param maximum the length in milliseconds of the longest lease
     *                this object should normally grant
     * @param defaultLength the length in milliseconds of the
     *                default lease grants
     * @throws IllegalArgumentException if either argument
     *         is not positive.
     */
    public FixedLeasePeriodPolicy(long maximum, long defaultLength) {
	if (maximum <= 0)
	    throw new IllegalArgumentException("FixedLeasePeriodPolicy:" +
	        "maximum lease time must be larger than 0, " +
		 "passed:" + maximum);

	if (defaultLength <= 0)
	    throw new IllegalArgumentException("FixedLeasePeriodPolicy:" +
	        "default lease time must be larger than 0, " + 
		 "passed:" + defaultLength);

	this.maximum = maximum;
	this.defaultLength = defaultLength;
    }


    /**
     * Returns the duration this policy is willing to grant for the
     * passed resource at this time.  The duration actually granted
     * will be shorter if the duration extends pass the end of the
     * epoch.  Must return a positive number.
     * <p>
     * Note the duration returned by this method will be shorter than
     * the final duration granted if the requested duration extends
     * past the current expiration, and duration return by this 
     * method is before the current expiration.
     *     
     * @param resource          the resource having a lease granted
     *                          or renewed
     * @param requestedDuration the duration the client wants 
     * @exception IllegalArgumentException thrown if requestedDuration
     *            is less than 0 and not equal to
     *            <code>Lease.ANYLENGTH</code> or
     *            <code>Lease.FOREVER</code>.  
     */
    protected long calculateDuration(LeasedResource resource, 
        long requestedDuration) 
    {
	if (requestedDuration == Lease.FOREVER)
	    requestedDuration = Long.MAX_VALUE;
	else if (requestedDuration == Lease.ANY)
	    requestedDuration = defaultLength;
	else if (requestedDuration < 0)
	    throw new 
		IllegalArgumentException("Negative lease duration " +
		    "requested");

	return Math.min(requestedDuration, maximum);
    }

    /**
     * Method that provides some notion of the current time in milliseconds
     * since the beginning of the epoch. Default implementation
     * calls System.currentTimeMillis()
     */
    protected long currentTime() {
	return System.currentTimeMillis();
    }

    /**
     * Calculates an expiration based on the passed time and a requested
     * duration. Will clip the expiration to the end of the epoch
     * @param preferredDuration the duration the policy wants to grant
     * @param now               the current time in milliseconds since
     *                          the beginning of the epoch
     * @return the new expiration time for the lease in milliseconds since 
     *         the beginning of the epoch
     */
    private long calcExpiration(long preferredDuration, long now) {
	long expiration = now + preferredDuration;
	// Any addition of two positive longs is guaranteed to be
	// negative if it overflowed
	if (expiration < 0) {
	    // Not enough time for the duration we want, set to end of
	    // epoch 
	    return Long.MAX_VALUE;
	}

	return expiration;
    }


    public Result grant(LeasedResource resource, long requestedDuration) 
        throws LeaseDeniedException
    {
	final long now = currentTime();
	final long expiration =
	    calcExpiration(calculateDuration(resource, requestedDuration), 
			   now);
	final long duration = expiration - now;
	return new Result(expiration, duration);
    }

    public Result renew(LeasedResource resource, long requestedDuration)
        throws LeaseDeniedException
    {
	final long now = currentTime();

	// The new expiration we would grant independent of the
	// current expiration time 
	long newExpiration =
	    calcExpiration(calculateDuration(resource, requestedDuration), 
			   now);	

	// Do not change the expiration time to be sooner that it is
	// now unless the client explicitly asked for it.  If they did
	// ask for a sooner expiration give them exactly what they
	// asked for.

	// The current expiration of the lease
	final long oldExpiration = resource.getExpiration();

	if (oldExpiration > newExpiration) {
	    // need to make sure they asked for it

	    if (requestedDuration == Lease.ANY) {
		// they did not explicitly ask for a sooner
		// expiration, give them at least what they had
		newExpiration = oldExpiration; 
	    } else {
		// The new expiration time they are requesting
		// (possibly clipped to the end of the epoch) this is
		// not affected by what we are willing to grant.
		// [Note we know at this point that requestedDuration
		// is >= 0]
		final long requestedExpiration = calcExpiration(
		    requestedDuration, now);
		
		if (requestedExpiration > oldExpiration) {
		    // they made a request for something longer, give
		    // them at least what they had
		    newExpiration = oldExpiration; 
		} else {
		    // They made a request for something shorter,
		    // give them what they at least what they want
		    newExpiration = requestedExpiration;
		}
	    }
	}

	// Now that we finally have decided on a new expiration,
	// get the real duration and return the result.
	return new Result(newExpiration, newExpiration - now);
    }
}
