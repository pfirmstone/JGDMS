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

package com.sun.jini.test.spec.discoveryservice.event;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import java.util.HashMap;
import java.util.Map;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the lookup discovery service operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that the lookup discovery service can successfully employ both the
 * multicast and unicast discovery protocols on behalf of one or more clients
 * registered with that service to discover a number of pre-determined lookup
 * services and then, for each discovered lookup service, send to the 
 * appropriate registration listener, the appropriate remote event containing
 * the set of member groups with which the discovered lookup service was
 * configured.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registrations with the lookup discovery service
 *   <li> each registration with the lookup discovery service requests that
 *        some of the lookup services be discovered through only group
 *        discovery, some through only locator discovery, and some through
 *        both group and locator discovery
 *   <li> each registration with the lookup discovery service will receive
 *        remote discovery events through an instance of RemoteEventListener
 * </ul><p>
 * 
 * If the lookup discovery service utility functions as specified, then
 * for each discovered lookup service, a <code>RemoteDiscoveryEvent</code>
 * instance indicating a discovered event will be sent to the listener of
 * each registration that requested discovery of the lookup service.
 * Additionally, each event received will accurately reflect the new set
 * of member groups.
 */
public class Discovered extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> registers with the lookup discovery service, requesting
     *          the discovery of the the desired lookup services using the
     *          desired discovery protocol
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> verifies that the lookup discovery service utility under test
     *          sends the expected number of events - containing the expected
     *          set of member groups
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(getGroupsToDiscoverByIndex(i),
                           getLocatorsToDiscoverByIndex(i),
                           i, leaseDuration);
        }//end loop
        waitForDiscovery();
    }//end run

} //end class Discovered

