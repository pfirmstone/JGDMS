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

package com.sun.jini.test.spec.discoveryservice.lease;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * This class determines if the lookup discovery service can, when requested
 * by a registered client, successfully renew the lease the service granted
 * on the registration object through which the client requests the renewal.
 *
 * This class verifies the following behaviour specified by
 * <i>The LookupDiscoveryService</i> specification:
 * "... the resources granted by this service are leased, and implementations
 *  of this service must adhere to the distributed leasing model for 
 * Jini(TM) as defined in the <i>Jini(TM) Technology Core Platform
 * Specification</i>, "Distributed Leasing"
 */
public class RenewLease extends AbstractBaseTest {

    private LookupDiscoveryRegistration reg = null;

    /** Constructs and returns the duration values (in milliseconds) to 
     *  request on each renewal attempt (can be overridden by sub-classes)
     */
    long[] getRenewalDurations() {
        return new long[] { 45*1000, 15*1000 };
    }//end getRenewalDurations

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service.
     *  Requests a registration with the service.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        reg = DiscoveryServiceUtil.getRegistration
                               (discoverySrvc,
                                new DiscoveryServiceUtil.BasicEventListener());
        long duration = DiscoveryServiceUtil.expirationToDuration
                                          ((getPreparedLease(reg)).getExpiration(),
                                           System.currentTimeMillis());
        logger.log(Level.FINE, "initial lease duration = "+duration);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the durations with which to renew the lease
     *  2. For each duration,
     *     a. requests that the lease be renewed for that period of time
     *     b. retrieves the current expiration on the renewed lease
     *     c. converts the expiration to a duration, rounding to a whole value
     *     d. compares the value expected to the value returned
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException(
                                 "could not successfully start the service "
                                 +serviceName);
        }
	Lease lease = getPreparedLease(reg);
	long[] expectedDurations = getRenewalDurations();
	for(int i=0;i<expectedDurations.length;i++) {
            lease.renew(expectedDurations[i]);
            long newDuration = DiscoveryServiceUtil.expirationToDuration
                                              (lease.getExpiration(),
                                               System.currentTimeMillis());
            logger.log(Level.FINE, 
                              "expected lease duration["+i+"] = "
                              +expectedDurations[i]);
            logger.log(Level.FINE, 
                              "new lease duration["+i+"]      = "
                              +newDuration);
            if(!(newDuration == expectedDurations[i])) {
                throw new TestException(
                                     " -- duration granted ("
                                     +newDuration
                                     +") != expectedDuration ("
                                     +expectedDurations[i]+")");
            }
	}//endloop
    }//end run

} //end class RenewLease


