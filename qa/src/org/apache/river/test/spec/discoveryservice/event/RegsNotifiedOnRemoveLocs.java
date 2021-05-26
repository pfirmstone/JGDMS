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

import java.rmi.RemoteException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class verifies that when the <code>removeLocators</code> method is used
 * to remove already-discovered locators from a number of registrations at 
 * different times, the lookup discovery service sends the appropriate remote
 * discard events to the listener of each such registration.
 *
 * This test was written to test the lookup discovery service when it is
 * used in the following way:
 * <p><ul>
 *   <li> a lookup service is started having locator, say 'l0'
 *   <li> a second lookup service is started having locator, say 'l1'
 *   <li> two separate sets of registrations are made with the lookup
 *        discovery service, requesting that both locator 'l0' and locator 'l1'
 *        be discovered
 *   <li> the listeners for the registrations in each set should receive the
 *        expected remote discovery events for locators 'l0' and 'l1'
 *   <li> removeLocators is invoked on each registration in the first set of
 *        registrations, requesting that locator 'l0' be removed from the
 *        the set of locators to discover for each such registration
 *   <li> the listeners for the registrations in the first set should then
 *        receive the expected remote discard events for locator 'l0'
 *   <li> removeLocators is again invoked on each registration in the first set
 *        of registrations, this time requesting that locator 'l1' be removed
 *   <li> the listeners for the registrations in the first set should then
 *        receive the expected remote discard events for locator 'l1'
 *   <li> removeLocators is then invoked on each registration in the second set
 *        of registrations, requesting that locator 'l0' be removed from the
 *        the set of locators to discover for each such registration
 *   <li> the listeners for the registrations in the second set should then
 *        receive the expected remote discard events for locator 'l0'
 *   <li> removeLocators is again invoked on each registration in the second
 *        set of registrations, this time requesting that locator 'l1' be
 *        removed
 *   <li> the listeners for the registrations in the first set should then
 *        receive the expected remote discard events for locator 'l1'
 * </ul><p>
 *
 * If the lookup discovery service functions as specified, then upon invoking
 * <code>removeLocators</code> for each registration, the listener of each
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * indicating that a discard has occurred, and which accurately reflects the
 * correct set of discarded lookup services.
 * 
 * Specification test for LD3.2 and LD4.1.1 (Locator Mutator Methods)
 */
public class RegsNotifiedOnRemoveLocs extends LateRegsNotifiedOn2SetLocs {

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> creates two separate sets of registrations with the lookup
     *      discovery service, requesting the discovery of both sets of
     *      locators which should, respectively, equal the locators of the
     *      lookup services started in construct
     * <li> verifies that the listeners for the registrations in either set
     *      receive the appropriate remote discovery event for the both sets
     *      of locators
     * <li> on each registration in the first set of registrations, calls
     *      removeLocators, requesting the removal of the first set of locators
     *      from the set of locators to discover for those registrations
     * <li> verifies that the listener for each registration in the first set
     *      receives the appropriate remote discard event for the locators that
     *      were removed
     * <li> on each registration in the first set of registrations, calls
     *      removeLocators, requesting the removal of the second set of
     *      locators from the set of locators to discover for those
     *      registrations
     * <li> verifies that the listener for each registration in the first set
     *      receives the appropriate remote discard event for the locators that
     *      were removed
     * <li> on each registration in the second set of registrations, calls
     *      removeLocators, requesting the removal of the first set of locators
     *      from the set of locators to discover for those registrations
     * <li> verifies that the listener for each registration in the second set
     *      receives the appropriate remote discard event for the locators that
     *      were removed
     * <li> on each registration in the second set of registrations, calls
     *      removeLocators, requesting the removal of the second set of
     *      locators from the set of locators to discover for those
     *      registrations
     * <li> verifies that the listener for each registration in the second set
     *      receives the appropriate remote discard event for the locators that
     *      were removed
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        String[]        noGroups = DiscoveryGroupManagement.NO_GROUPS;
        LookupLocator[] totalLocators = getLocatorsToDiscover
                                                        (getUseOnlyLocDiscovery());
        /* create first set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, 
                      "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,totalLocators,i,leaseDuration);
        }//end loop

        Map regMap0 = new HashMap(getRegistrationMap());

        /* create 2nd set of registrations */
        int totalRegs = nRegistrations+nAddRegistrations;
        for(int i=nRegistrations;i<totalRegs;i++) {
            logger.log(Level.FINE, 
                      "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,totalLocators,i,leaseDuration);
        }//end loop
        nRegistrations = getRegistrationMap().size();
        /* Construct a map containing the second set of registrations */
        HashMap regMap1 = new HashMap(1);
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Skip registrations from the first set of registrations */
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            if( regMap0.containsKey(regListenerPair.getKey()) ) continue;
            regMap1.put( regListenerPair.getKey(),
                         regListenerPair.getValue() );
       }//end loop
        /* verify discovery of each locator for each registration */
        logger.log(Level.FINE, "wait for discovery -- each "
                          +"locator, each registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "wait period complete");
        /* Must clear discard event info for the second set of
         * registrations. This is because locators are removed only from
         * the first set of registrations; thus, only the first set of 
         * registrations will be expecting discard events.
         */
        resetDiscardEventInfoRegMap(regMap1);


        /* remove 1st set of locators from the 1st set of registrations */
        logger.log(Level.FINE, "remove first locator set from "
                          +"the first registration(s)");
        removeLocatorsRegMap(regMap0,locs0);
        /* verify discard of 1st locator set from 1st registrations */
        logger.log(Level.FINE, "wait for discard -- first "
                          +"locator set, first registration(s)");
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait period complete");
        /* Must clear discard event info for all registrations before
         * removing locators from the first set of registrations and
         * calling waitForDiscard(). This is because when the discard
         * events arrived for the first set of registrations, the 
         * 'discardedMap' associated with each of those registrations
         * was populated with the new discard events. Before again removing
         * locators from the first set of registrations and then calling
         * waitForDiscard(), the 'discardMap' for each registration must
         * be cleared so the next call to waitForDiscard() is not 
         * affected by old data in the 'discardMap'.
         *
         * Additionally, the previous call to waitForDiscard() set the
         * 'discardComplete' flag on each of the registrations from the
         * second set of registrations. Thus, those 'discardComplete'
         * flags must be cleared; otherwise, waitForDiscard() will
         * process registrations that shouldn't be expecting any
         * discard events as if they are expecting discard events.
         */
        resetDiscardEventInfoAllRegs();
        /* remove 2nd set of locators from the 1st set of registrations */
        logger.log(Level.FINE, 
                          "remove second locator set from "
                          +"the first registration(s)");
        removeLocatorsRegMap(regMap0,locs1);
        /* verify discard of 2nd locator set from 1st registrations */
        logger.log(Level.FINE, "wait for discard -- second "
                          +"locator set, first registration(s)");
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait period complete");
        /* Clear all discardMap's and all 'discardComplete' flags */
       resetDiscardEventInfoAllRegs();


        /* remove 1st set of locators from the 2nd set of registrations */
        logger.log(Level.FINE, "remove first locator set from "
                          +"the second registration(s)");
        removeLocatorsRegMap(regMap1,locs0);
        /* verify discard of 1st locator set from 2nd registrations */
        logger.log(Level.FINE, "wait for discard -- first "
                          +"locator set, second registration(s)");
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait period complete");
        /* Clear all discardMap's and all 'discardComplete' flags */
        resetDiscardEventInfoAllRegs();


        /* remove 2nd set of locators from the 2nd set of registrations */
        logger.log(Level.FINE, 
                          "remove second locator set from "
                          +"the second registration(s)");
        removeLocatorsRegMap(regMap1,locs1);
        /* verify discard of 2nd locator set from 2nd registrations */
        logger.log(Level.FINE, "wait for discard -- second "
                          +"locator set, 2nd registration(s)");
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait period complete");
    }//end run

} //end class RegsNotifiedOnRemoveLocs

