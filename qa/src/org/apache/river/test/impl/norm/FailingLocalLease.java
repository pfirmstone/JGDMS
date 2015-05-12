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
package org.apache.river.test.impl.norm;

import java.rmi.RemoteException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lease.LeaseDeniedException;

/**
 * Subclass of LocalLease that after a preset number of deserilizations
 * sets the expiration to 0
 */
class FailingLocalLease extends LocalLease {
    /**
     * How many deseriailzations to allow setting expiration to 0
     */
    private long untilFailure;

    /**
     * Create a destructing local lease with the specified initial
     * expiration time
     * @param initExp    Initial expiration time
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     * @param id         Uniuque ID for this lease
     *                   value can be batched together
     * @param count      Number of renewals to accept befor throwing
     *                   a fit
     */
    FailingLocalLease(long initExp, long renewLimit, long bundle, long id,
		      long count) 
    {
	super(initExp, renewLimit, bundle, id);
	untilFailure = count;
    }
	    
    /**
     * Override that fails after the specified number of calls
     */
    protected synchronized void renewWork(long duration) 
	throws RemoteException, LeaseDeniedException, UnknownLeaseException
    {

	if (untilFailure == 0) {
	    throw new UnknownLeaseException();
	} else {
	    untilFailure--;
	    super.renewWork(duration);
	}
    }
}
