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
import org.apache.river.test.share.LocatorsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

import net.jini.core.discovery.LookupLocator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.river.qa.harness.QAConfig;

/**
 * This class verifies that when the <code>addLocators</code> method is used
 * to add already-discovered locators to a "late-joiner" registration, the
 * lookup discovery service sends the appropriate remote discovery events
 * to that registration's listener.
 *
 * This test was written to test the lookup discovery service when it is
 * used in the following way:
 * <p><ul>
 *   <li> a lookup service is started having locator, say 'l0'
 *   <li> a second lookup service is started locator, say 'l1'
 *   <li> an initial registration is made with the lookup discovery service,
 *        requesting that locator 'l0' be discovered
 *   <li> the listener for the initial registration should receive the expected
 *        remote discovery event for locator 'l0'
 *   <li> addLocators is invoked on the initial registration to add locator
 *        'l1' to the set of locators to discover for that registration
 *   <li> the listener for the initial registration should receive the expected
 *        remote discovery event for locator 'l1'
 *   <li> a second registration is made with the lookup discovery service,
 *        requesting that the previously discovered locator 'l0' be discovered
 *   <li> the listener for the second registration should receive the expected
 *        remote discovery event for locator 'l0'
 *   <li> addLocators is invoked on the second registration to add the
 *        previously discovered locator 'l1' to the set of locators to
 *        discover for that second registration
 *   <li> the listener for the initial registration should receive the expected
 *        remote discovery event for locator 'l1'
 * </ul><p>
 *
 * If the lookup discovery service functions as specified, then upon invoking
 * <code>addLocators</code> for each registration, the listener of each
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * which accurately reflects the expected lookup services.
 * 
 * Specification test for LD3.2 and LD4.1.1 (Locator Mutator Methods)
 * Regression test for Bug ID 4437560
 */
public class LateRegsNotifiedOnAddLocs extends LateRegsNotifiedOn2SetLocs {

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> creates a first set of registration(s) with the lookup discovery
     *      service, requesting the discovery of the first set of locators,
     *      which should equal the locators of the first lookup service(s)
     *      started in construct
     * <li> verifies that the listener for the first registration(s) receives
     *      the appropriate remote discovery event for the first set of
     *      locators
     * <li> on each of the initial registration(s) calls addLocators,
     *      requesting that the discovery of the second set of locators, which
     *      should equal the locators of the second lookup service(s) started
     *      in construct
     * <li> verifies that the listener for the first registration(s) receive
     *      the appropriate remote discovery event for the second set of
     *      locators
     * <li> creates a second set of registration(s) with the lookup discovery
     *      service, requesting the discovery of the first set of locators
     * <li> verifies that the listener for the second registration(s) receive
     *      the appropriate remote discovery event for the first set of
     *      locators
     * <li> on each of the second registration(s) calls addLocators, requesting
     *      the discovery of the second set of locators
     * <li> verifies that the listener for the second registration(s) receive
     *      the appropriate remote discovery event for the second set of
     *      locators
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        String[] noGroups = DiscoveryGroupManagement.NO_GROUPS;
        /* create the 1st set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,locs0,i,leaseDuration);
        }//end loop
        /* verify discovery of the 1st locs for the 1st registration */
        logger.log(Level.FINE, "wait for discovery -- initial "
                          +"locators, initial registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");

        Map regMap0 = new HashMap(getRegistrationMap());

        /* add to locators to discover for the 1st set of registrations */
        logger.log(Level.FINE, "add to locators on initial "
                          +"registration(s)");
        addLocatorsAllRegs(locs1);
        /* verify discovery of the 2nd locators for the 1st registration */
        logger.log(Level.FINE, "wait for discovery -- "
                          +"additional locators, initial registration(s)");
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
            doRegistration(noGroups,locs0,i,leaseDuration);
        }//end loop
        /* verify discovery of the 1st locators for the 2nd registration */
        logger.log(Level.FINE, "wait for discovery -- initial "
                          +"locators, additional registration(s)");
        waitForDiscovery();
        /* add to locators to discover for the 2nd set of registrations */
        logger.log(Level.FINE, "wait period complete ... "
                         +"add to locators on additional registration(s)");
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
            addLocatorsOneReg( locs1, regListenerPair );

            if(locs1 == null) {
                logger.log(Level.FINE, "    FAILURE -- tried to add NULL");
            } else if( locs1.length <= 0 ) {
                logger.log(Level.FINE, "   NO-OP -- tried to add NO_LOCATORS");
            } else {
                LocatorsUtil.displayLocatorSet( locs1,
                                           "   additional reg -- locator",
                                           Level.FINE);
            }//endif
        }//end loop
        /* verify discovery of the 2nd locators for the 2nd registration */
        logger.log(Level.FINE, "wait for discovery -- "
                       +"additional locators, additional registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");
    }//end run

} //end class LateRegsNotifiedOnAddLocs

