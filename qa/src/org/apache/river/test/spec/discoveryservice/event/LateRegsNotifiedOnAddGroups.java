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
import org.apache.river.qa.harness.QAConfig;

/**
 * This class verifies that when the <code>addGroups</code> method is used
 * to add already-discovered groups to a "late-joiner" registration, the
 * lookup discovery service sends the appropriate remote discovery events
 * to that registration's listener.
 *
 * This test was written to test the lookup discovery service when it is
 * used in the following way:
 * <p><ul>
 *   <li> a lookup service is started belonging to some group, say 'g0'
 *   <li> a second lookup service is started belonging to some group, say 'g1'
 *   <li> an initial registration is made with the lookup discovery service,
 *        requesting that group 'g0' be discovered
 *   <li> the listener for the initial registration should receive the expected
 *        remote discovery event for group 'g0'
 *   <li> addGroups is invoked on the initial registration to add group 'g1'
 *        to the set of groups to discover for that registration
 *   <li> the listener for the initial registration should receive the expected
 *        remote discovery event for group 'g1'
 *   <li> a second registration is made with the lookup discovery service,
 *        requesting that the previously discovered group 'g0' be discovered
 *   <li> the listener for the second registration should receive the expected
 *        remote discovery event for group 'g0'
 *   <li> addGroups is invoked on the second registration to add the previously
 *        discovered group 'g1' to the set of groups to discover for that
 *        second registration
 *   <li> the listener for the initial registration should receive the expected
 *        remote discovery event for group 'g1'
 * </ul><p>
 *
 * If the lookup discovery service functions as specified, then upon invoking
 * <code>addGroups</code> for each registration, the listener of each
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * which accurately reflects the correct set of member groups.
 * 
 * Specification test for LD3.2 and LD4.1.1 (Group Mutator Methods)
 * Regression test for Bug ID 4437011
 */
public class LateRegsNotifiedOnAddGroups extends LateRegsNotifiedOn2SetGroups {

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> creates a first set of registration(s) with the lookup discovery
     *      service, requesting the discovery of the first set of groups,
     *      which should equal the member groups of the first lookup service(s)
     *      started in construct
     * <li> verifies that the listener for the first registration(s) receives
     *      the appropriate remote discovery event for the first set of groups
     * <li> on each of the initial registration(s) calls addGroups, requesting
     *      that the discovery of the second set of groups, which should equal
     *      the member groups of the second lookup service(s) started in construct
     * <li> verifies that the listener for the first registration(s) receive
     *      the appropriate remote discovery event for the second set of groups
     * <li> creates a second set of registration(s) with the lookup discovery
     *      service, requesting the discovery of the first set of groups
     * <li> verifies that the listener for the second registration(s) receive
     *      the appropriate remote discovery event for the first set of groups
     * <li> on each of the second registration(s) calls addGroups, requesting
     *      that the discovery of the second set of groups
     * <li> verifies that the listener for the second registration(s) receive
     *      the appropriate remote discovery event for the second set of groups
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        LookupLocator[] noLocs = getLocatorsToDiscover(getUseOnlyGroupDiscovery());
        /* create the 1st set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(groups0,noLocs,i,leaseDuration);
        }//end loop
        /* verify discovery of the 1st groups for the 1st registration */
        logger.log(Level.FINE, "wait for discovery -- initial "
                          +"groups, initial registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");

        Map regMap0 = new HashMap(getRegistrationMap());

        /* add to groups to discover for the 1st set of registrations */
        logger.log(Level.FINE, "add to groups on initial "
                          +"registration(s)");
        addGroupsAllRegs(groups1);
        /* verify discovery of the 2nd groups for the 1st registration */
        logger.log(Level.FINE, "wait for discovery -- "
                          +"additional groups, initial registration(s)");
        waitForDiscovery();
        /* Reset all discovery event info in preparation for the next
         * set of registraions
         */
        resetAllEventInfoAllRegs();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... request "+nAddRegistrations
                          +" additional registration(s)");
        /* create 2nd set of registrations */
        int totalRegs = nRegistrations+nAddRegistrations;
        for(int i=nRegistrations;i<totalRegs;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(groups0,noLocs,i,leaseDuration);
        }//end loop
        /* verify discovery of the 1st groups for the 2nd registration */
        logger.log(Level.FINE, "wait for discovery -- initial "
                          +"groups, additional registration(s)");
        waitForDiscovery();
        /* add to groups to discover for the 2nd set of registrations */
        logger.log(Level.FINE, "wait period complete ... "
                          +"add to groups on additional registration(s)");
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Reset & skip registrations from the initial set */
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            if( regMap0.containsKey(regListenerPair.getKey()) ) {
                /* Must reset discovery event info for these registrations
                 * since their 'discoveryComplete' flag was set to true the
                 * last time waitForDiscovery() was invoked.
                 */
                resetAllEventInfoOneReg(regListenerPair);
                continue;
            }//endif
            addGroupsOneReg( groups1, regListenerPair );

            if( groups1 == DiscoveryGroupManagement.ALL_GROUPS ) {
                logger.log(Level.FINE, "   FAILURE -- tried to add ALL_GROUPS");
            } else if( groups1.length <= 0 ) {
                logger.log(Level.FINE, "    NO-OP -- tried to add NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet( groups1,
                                           "   additional reg -- group",
                                           Level.FINE);
            }//endif
        }//end loop
        /* verify discovery of the 2nd groups for the 2nd registration */
        logger.log(Level.FINE, "wait for discovery -- "
                         +"additional groups, additional registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");
    }//end run

} //end class LateRegsNotifiedOnAddGroups

