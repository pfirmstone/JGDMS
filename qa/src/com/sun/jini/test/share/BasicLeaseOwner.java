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

import com.sun.jini.test.share.LeaseOwner;

/**
 * This class is provides only essential and basic behavior to the
 * lease that it owns.
 *
 * 
 */
public class BasicLeaseOwner extends LeaseOwner {

    /**
     * Set to a non-<code>null</code> if the Lease Renewal Service
     * screws up
     */
    private String rslt = null;

    /**
     * The max time for renewing leases
     */
    long renewGrant = 0;

    /**
     * Constructor requiring a maximum lease grant duration.
     * 
     * @param maxGrant  the maximum duration for which a lease will be renewed.
     * 
     */
    public BasicLeaseOwner(long maxGrant) {
	renewGrant = maxGrant;
    }

    /**
     * Set rslt string if it is not already set
     */
    private synchronized void setRsltIfNeeded(String newResult) {
	if (rslt == null) {
	    rslt = newResult;
	}
    }

    public synchronized long batchRenew(long extension) {

	long grant;
	if (extension == Lease.ANY) {
	    grant = renewGrant;
	} else {
	    grant = Math.min(renewGrant, extension);
	}

	return grant;
    }

    /**
     * Return <code>null</code> if the lease assocated with this 
     * owner had all the right things and an error message otherwise.
     */
    public synchronized String didPass() {
	return rslt;
    }

    // Inherit java doc from super type
    public Object renew(long extension) {
	return new Long(batchRenew(extension));
    }

	
    // Inherit java doc from super type
    public void batchCancel() {
	setRsltIfNeeded("Renewal Service canceld lease!");
    }
	
    // Inherit java doc from super type
    public Throwable cancel() {
	batchCancel();
	return null;
    }	

} // BasicLeaseOwner


