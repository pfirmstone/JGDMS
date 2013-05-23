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
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class determines if the lookup discovery service can, when requested
 * by a registered client, successfully renew through a single method
 * invocation a set of "batchable" leases the service granted on a set
 * of registrations held by the client.
 *
 * This class verifies the following behaviour specified by
 * <i>The LookupDiscoveryService</i> specification:
 * "... the resources granted by this service are leased, and implementations
 *  of this service must adhere to the distributed leasing model for 
 * Jini(TM) as defined in the <i>Jini(TM) Technology Core Platform
 * Specification</i>, "Distributed Leasing"
 */
public class RenewLeaseMap extends AbstractBaseTest {
    private long[] expectedDurations = null;
    private LookupDiscoveryRegistration reg[] = null;
    private Lease lease[] = null;
    private LeaseMap leaseMap = null;

    /** Constructs and returns the duration values (in milliseconds) to 
     *  request on each renewal attempt (can be overridden by sub-classes)
     */
    long[] getRenewalDurations() {
        return new long[] { 45*1000, 15*1000, 27*1000, 11*1000, 23*1000 };
    }//end getRenewalDurations

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service.
     *  Requests a number of registrations with the service (number of 
     *  registrations requested equals the number of new lease durations
     *  that will be requested)
     *  Retrieves and stores the lease corresponding to each registration
     *  Constructs a LeaseMap that maps each lease to the new duration
     *  that will be used in a renewal request.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        /* Create the registrations */
        expectedDurations = getRenewalDurations();
        reg = new LookupDiscoveryRegistration[expectedDurations.length];
        lease = new Lease[expectedDurations.length];
        for(int i=0;i<reg.length;i++) {
            DiscoveryServiceUtil.BasicEventListener listener = new DiscoveryServiceUtil.BasicEventListener();
            listener.export();
            reg[i] = DiscoveryServiceUtil.getRegistration
                               (discoverySrvc,
                                listener);
            lease[i] = getPreparedLease(reg[i]);
            long duration = DiscoveryServiceUtil.expirationToDuration
                                                 (lease[i].getExpiration(),
                                                  System.currentTimeMillis());
            logger.log(Level.FINE, "initial lease duration["
                                            +i+"] = "+duration+" mSecs");
        }//endloop

        /* Create the LeaseMap */
        leaseMap = lease[0].createLeaseMap(expectedDurations[0]);
        for(int i=1;i<expectedDurations.length;i++) {
            if(lease[0].canBatch(lease[i])) {
                leaseMap.put(lease[i],new Long(expectedDurations[i]));
                logger.log(Level.FINE, 
			   "can batch lease["+i+"] with lease[0]");
            } else {
                logger.log(Level.FINE, 
			   "can not batch lease["+i+"] with lease[0]");
            }//endif
        }//end loop

        /* Display the (lease,duration) pairs in the map */
        Set expectedSet = leaseMap.entrySet();
        for( Iterator itr=expectedSet.iterator(); itr.hasNext();) {
            Map.Entry mappedPair = (Map.Entry)itr.next();
            long  mappedDuration = ((Long)mappedPair.getValue()).longValue();
            Lease mappedLease = (Lease)mappedPair.getKey();
            for(int i=0;i<lease.length;i++) {
                if(mappedLease.equals(lease[i])) {;
                    logger.log(Level.FINE, "lease["+i +"] mapped to duration = "
                                            +mappedDuration);
                    break;
                }
            }
        }//end loop
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Requests the renewal of all leases referenced in the map
     *     constructed during construct
     *  2. For each lease granted during construct,
     *     a. retrieves the current expiration on the renewed lease
     *     b. converts the expiration to a duration, rounding to a whole value
     *     c. compares the value expected to the value returned
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException(
                                 "could not successfully start the service "
                                 +serviceName);
        }
        /* Renew each lease in the map */
        leaseMap.renewAll();

        /* Retrieve the new lease durations after renewal */
        long[] newDurations = new long[lease.length];
        for(int i=0;i<newDurations.length;i++) {
            newDurations[i] = DiscoveryServiceUtil.expirationToDuration
                                              (lease[i].getExpiration(),
                                               System.currentTimeMillis());
            logger.log(Level.FINE, "\n"+"lease["+i
                                            +"] expected new duration = "
                                            +expectedDurations[i]);
            logger.log(Level.FINE, "lease["+i
                                            +"] new duration          = "
                                            +newDurations[i]);
        }//endloop

        /* Compare the new lease durations to the expected durations */
        for(int i=0;i<newDurations.length;i++) {
            if(!(newDurations[i] == expectedDurations[i])) {
                throw new TestException(
                                     " -- newDurations["+i+"] ("
                                     +newDurations[i]
                                     +") != expectedDurations["+i+"] ("
                                     +expectedDurations[i]+")");
            }
        }//endloop
    }//end run

} //end class RenewLeaseMap


