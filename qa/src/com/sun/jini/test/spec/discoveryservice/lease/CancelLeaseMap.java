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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.UnknownLeaseException;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * This class determines if the lookup discovery service can, when requested
 * by a registered client, successfully cancel through a single method
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
public class CancelLeaseMap extends AbstractBaseTest {
    private LookupDiscoveryRegistration reg[] = null;
    private Lease lease[] = null;
    private LeaseMap leaseMap = null;

    /** Constructs and returns the set of groups with which to register
     *  with the lookup discovery srvice; that is, the set of groups
     *  that service should attempt to discover for the client's 
     *  registration (can be overridden by sub-classes)
     */
    String[] getGroupsToDiscover() {
        return new String[] { "g0", "g1", "g2", "g3" };
    }//end getGroupsToDiscover

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service.
     *  Requests a number of registrations with the service, requesting that
     *  a particular set of groups be discovered.
     *  Through each registration, retrieves the set of groups that the service
     *  will attempt to discover on behalf of the client (this is toverify
     *  that each registration is valid)
     *  Constructs a LeaseMap that maps each lease to its current duration
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        /* Create the registrations */
        reg = new LookupDiscoveryRegistration[5];
        lease = new Lease[reg.length];
        long  durs[]  = new long[reg.length];
        String[] expectedGroups = getGroupsToDiscover();
        for(int i=0;i<reg.length;i++) {
            reg[i] = DiscoveryServiceUtil.getRegistration
                               (discoverySrvc,
                                new DiscoveryServiceUtil.BasicEventListener(),
                                expectedGroups);
            String[] currentGroups = reg[i].getGroups();
            if( !GroupsUtil.compareGroupSets(currentGroups,expectedGroups,Level.OFF) ) {
                throw new TestException("-- failed to retrieve the expected "
                                          +"groups to discover from "
                                          +"registration["+i+"]");
            }
            lease[i] = getPreparedLease(reg[i]);
            durs[i]  = DiscoveryServiceUtil.expirationToDuration
                                                 (lease[i].getExpiration(),
                                                  System.currentTimeMillis());
            logger.log(Level.FINE, "initial lease duration["
                                            +i+"] = "+durs[i]+" mSecs");
        }//endloop

        /* Create the LeaseMap */
        leaseMap = lease[0].createLeaseMap(durs[0]);
        for(int i=1;i<durs.length;i++) {
            if(lease[0].canBatch(lease[i])) {
                leaseMap.put(lease[i],new Long(durs[i]));
                logger.log(Level.FINE, "can batch lease["+i+"] with lease[0]");
            } else {
                logger.log(Level.FINE, "can not batch lease["+i+"] with lease[0]");
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
     *  1. Requests the cancellation of all leases referenced in the map
     *     constructed during construct
     *  2. For each lease granted during construct, verifies the lease is no
     *     longer valid by doing the following:
     *     a. attempts to retrieve the set of groups that the service
     *        will discover on behalf of the client; if the lease was
     *        successfully cancelled, then a java.rmi.NoSuchObjectException
     *        should be received
     *     b. attempts to renew the lease; if the lease was successfully
     *        cancelled, then a net.jini.core.lease.UnknownLeaseException
     *        should be received
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException(
                                 "could not successfully start the service "
                                 +serviceName);
        }
        /* Renew each lease in the map */
            leaseMap.cancelAll();
        /* Try to retrieve the groups through each registration */
        for(int i=0;i<reg.length;i++) {
            try {
                reg[i].getGroups();
                throw new TestException(
                                   " -- groups successfully retrieved; "
                                   +"registration["+i+"] must still be valid");
            } catch (NoSuchObjectException e) {
                // expected exception; continue processing
            }
        }//endloop

        /* Try to renew each lease */
        for(int i=0;i<lease.length;i++) {
            try {
                lease[i].renew(DiscoveryServiceUtil.defaultDuration);
                throw new TestException(
                                    " -- lease["+i+"] successfully renewed; "
                                   +"registration["+i+"] must still be valid");
            } catch (UnknownLeaseException e) {
                // expected exception
            }
        }//endloop
    }//end run

} //end class CancelLeaseMap


