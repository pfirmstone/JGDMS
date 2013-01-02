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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * This class determines if the lookup discovery service can, when requested
 * by a registered client, successfully cancel the lease the service granted
 * on the registration object through which the client requests the
 * cancellation.
 *
 * This class verifies the following behaviour specified by
 * <i>The LookupDiscoveryService</i> specification:
 * "... the resources granted by this service are leased, and implementations
 *  of this service must adhere to the distributed leasing model for 
 *  Jini(TM) technology as defined in the <i>Jini(tm) Technology Core
 *  Platform Specification</i>, "Distributed Leasing"
 */
public class CancelLease extends AbstractBaseTest {

    private LookupDiscoveryRegistration reg = null;
    private Lease lease = null;

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
     *  Requests a registration with the service, requesting that a particular
     *  set of groups be discovered.
     *  Through the registration, retrieves the set of groups that the service
     *  will attempt to discover on behalf of the client (this is toverify
     *  that the registration is valid)
     *  Retrieves and stores the lease granted on the registration
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        String[] expectedGroups = getGroupsToDiscover();
        reg = DiscoveryServiceUtil.getRegistration
                                (discoverySrvc,
                                 new DiscoveryServiceUtil.BasicEventListener(),
                                 expectedGroups);
        String[] currentGroups = reg.getGroups();
        if( !GroupsUtil.compareGroupSets(currentGroups,expectedGroups,Level.OFF) ) {
            throw new TestException("-- failed to retrieve the expected "
                                      +"groups to discover from the "
                                      +"registration");
        }
        lease = getPreparedLease(reg);
        long duration = DiscoveryServiceUtil.expirationToDuration
                                          (lease.getExpiration(),
                                           System.currentTimeMillis());
        logger.log(Level.FINE, "initial lease duration = "+duration);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Requests the cancellation of the lease on the registration granted
     *     during construct
     *  2. Verifies the lease is no longer valid by doing the following:
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
            throw new TestException("could not successfully start the service "
				    +serviceName);
        }
        lease.cancel();
        /* Try to retrieve the groups through the registration */
        try {
            reg.getGroups();
            throw new TestException(
                                 " -- groups successfully retrieved; "
                                 +"registration must still be valid");
        } catch (NoSuchObjectException e) {
            // expected exception; continue processing
        }
        /* Try to renew the lease */
        try {
            lease.renew(DiscoveryServiceUtil.defaultDuration);
            throw new TestException(
                                 " -- lease successfully renewed; "
                                 +"registration must still be valid");
        } catch (UnknownLeaseException e) {
            // expected exception
        }
    }//end run

} //end class CancelLease


