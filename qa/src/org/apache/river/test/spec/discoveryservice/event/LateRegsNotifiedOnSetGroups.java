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

package org.apache.river.test.spec.discoveryservice.event;

import java.util.logging.Level;

import org.apache.river.test.spec.discoveryservice.AbstractBaseTest;
import org.apache.river.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

import net.jini.core.discovery.LookupLocator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class verifies that when the <code>setGroups</code> method, rather
 * than the <code>register</code> method, is used to configure a "late-joiner"
 * registration with its desired groups, the lookup discovery service sends
 * the appropriate remote discovery events to that registration's listener.
 *
 * This test was written to investigate a posting on the user's list. In
 * that posting, a user observed that when the lookup discovery service
 * was used in the following way, unexpected results occurred:
 * <p><ul>
 *   <li> a lookup service was started belonging to some group, say 'g0'
 *   <li> an initial service registered with the lookup discovery service,
 *        requesting NO_GROUPS and no locators be discovered
 *   <li> setGroups was then invoked to request that group 'g0' be discovered
 *   <li> the listener for that initial registration then received the
 *        expected remote discovery event
 *   <li> a second service was then registered with the lookup discovery
 *        service, again requesting that NO_GROUPS and no locators be
 *        discovered
 *   <li> setGroups was again invoked to request that group 'g0' be discovered
 *        for the second registration, but no remote discovery event was
 *        ever sent to the registration's listener
 * </ul><p>
 *
 * Upon investigation, it was found that the lookup discovery service was
 * not sending events for lookup services that were previously discovered for 
 * other registrations (the place where this should have occurred in the
 * contributed implementation of the lookup discovery service was in the
 * <code>SetGroupsTask</code>). This bug was filed as bug 4434000.
 *
 * To duplicate the user's scenario within the context of the current test
 * harness, it is not necessary to create two services; it is only necessary to
 * create two registrations, each initially registering for NO_GROUPS and no
 * locators, and then calling <code>setGroups</code> to request that the
 * lookup discovery service discover a given set of groups for each
 * registration.
 * 
 * If the lookup discovery service functions as specified, then upon invoking
 * <code>setGroups</code> for each registration, the listener of each
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * which accurately reflects the correct set of member groups.
 * 
 * Specification test for LD3.2 and LD4.1.1 (Group Mutator Methods)
 * Regression test for Bug ID 4434000
 */
public class LateRegsNotifiedOnSetGroups extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> create a first set of registration(s) with the lookup discovery
     *      service, requesting that NO_GROUPS and no locators be discovered
     * <li> calls setGroups, requesting the discovery of the member groups of
     *      the lookup service(s) started in construct
     * <li> verifies that the discovery process is working for the first
     *      registration by waiting for the expected discovery events
     * <li> creates a second set of registration(s) with the lookup discovery
     *      service, again requesting that NO_GROUPS and no locators be
     *      discovered
     * <li> again calls setGroups, requesting the discovery of the same groups
     *      with which the first registration is configured
     * <li> verifies that the discovery process is working for the second
     *      registration by waiting for the expected discovery events
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        String[] groupsToDiscover = getGroupsToDiscover(getUseOnlyGroupDiscovery());
        LookupLocator[] noLocs = getLocatorsToDiscover(getUseOnlyGroupDiscovery());
        /* create the 1st set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(DiscoveryGroupManagement.NO_GROUPS,noLocs,
                           i,leaseDuration);
        }//end loop
        /* set the groups to discover for the 1st set of registrations */
        logger.log(Level.FINE, "set groups on initial "
                          +"registration(s)");
        setGroupsAllRegs(groupsToDiscover);
        logger.log(Level.FINE, "wait for discovery on "
                          +" initial registration(s)");
        waitForDiscovery();
        /* Reset all discovery event info in preparation for the next
         * set of registraions
         */
        resetAllEventInfoAllRegs();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... request "+nAddRegistrations
                          +" additional registration(s)");

        Map regMap0 = new HashMap(getRegistrationMap());

        /* create 2nd set of registrations */
        int totalRegs = nRegistrations+nAddRegistrations;
        for(int i=nRegistrations;i<totalRegs;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(DiscoveryGroupManagement.NO_GROUPS,noLocs,
                           i,leaseDuration);
        }//end loop
        /* set the groups to discover for the 2nd set of registrations */
        logger.log(Level.FINE, "set groups on additional "
                          +"registration(s)");
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Skip registrations from the initial set of registrations */
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            if( regMap0.containsKey(regListenerPair.getKey()) ) {
                /* Must reset discovery event info for these registrations
                 * since their 'discoveryComplete' flag was set to true the
                 * last time waitForDiscovery() was invoked.
                 */
                resetAllEventInfoOneReg(regListenerPair);
                continue;
            }//endif
            setGroupsOneReg( groupsToDiscover, regListenerPair );
            if( groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"groups set to ALL_GROUPS");
            } else if( groupsToDiscover.length <= 0 ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"groups set to NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet( groupsToDiscover,
                                           "   additional reg -- group",
                                           Level.FINE);
            }//endif
        }//end loop
        logger.log(Level.FINE, "wait for discovery on "
                         +"additional registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");
    }//end run

} //end class LateRegsNotifiedOnSetGroups

