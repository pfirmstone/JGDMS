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

import net.jini.core.discovery.LookupLocator;

import java.util.HashMap;
import java.util.Map;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that, upon registration, so-called "late-joiner"
 * registrations that request discovery of groups that have already been
 * discovered for other registration(s) will receive notification of all
 * of those previously discovered lookup services. That is, this class
 * verifies that when one or more registrations -- configured for discovery
 * of a finite set of groups -- are added to a lookup discovery service
 * after an initial set of registration(s) -- similarly configured for
 * group discovery -- have been made with that lookup discovery service, the
 * second set of registrations are notified of the discovery of all lookup
 * services belonging to the groups with which the registraions were
 * configured. 
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of the lookup discovery service
 *   <li> a set of one or more initial registrations with the lookup
 *        discovery service, each configured for discovery of the
 *        group(s) in which the lookup service(s) are members
 *   <li> each initial registration with the lookup discovery service should
 *        receive remote discovery events through an instance of 
 *        RemoteEventListener
 *   <li> after verification that the initial registrations have received
 *        the appropriate notifications, an additional set of one or more
 *        registrations with the lookup discovery service, each configured
 *        for discovery of the same group(s) as the initial set
 *   <li> each additional registration should also receive remote discovery
 *        events through an instance of RemoteEventListener
 * </ul><p>
 * 
 * If the lookup discovery service functions as specified, then upon
 * requesting the additional registrations, the listener of each such
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * which accurately reflects the correct set of member groups.
 */
public class LateRegsNotifiedGroups extends AbstractBaseTest {

    protected String[] groupsToDiscover;
    protected LookupLocator[] locsToDiscover;

    /** Performs actions necessary to prepare for execution of the 
     *  current test. Populates the sets of group and/or locators to use.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        logger.log(Level.FINE, "setup()");
        groupsToDiscover = getGroupsToDiscover(useOnlyGroupDiscovery);
        locsToDiscover = getLocatorsToDiscover(useOnlyGroupDiscovery);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> registers with the lookup discovery service, requesting
     *      the discovery of the the desired lookup services using the
     *      desired discovery protocol
     * <li> verifies that the discovery process is working by waiting
     *      for the expected discovery events
     * <li> verifies that the lookup discovery service utility under test
     *      sends the expected number of events - containing the expected
     *      set of member groups
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* create the first set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(groupsToDiscover,locsToDiscover,
                           i,leaseDuration);
        }//end loop
        logger.log(Level.FINE, ""+nRegistrations+" initial "
                          +"registration(s) complete ... wait for "
                          +"discovery");
        waitForDiscovery();
        /* Reset all discovery event info in preparation for the next
         * set of registraions
         */
        resetAllEventInfoAllRegs();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... request "+nAddRegistrations
                          +" additional registration(s)");
        /* create the second set of registrations */
        for(int i=nRegistrations;i<(nRegistrations+nAddRegistrations);i++){
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(groupsToDiscover,locsToDiscover,
                           i,leaseDuration);
        }//end loop
        logger.log(Level.FINE, ""+nAddRegistrations
                          +" additional registration(s) complete ... "
                          +"wait for discovery");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");
    }//end run

} //end class LateRegsNotifiedGroups

