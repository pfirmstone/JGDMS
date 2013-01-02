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
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

import net.jini.core.discovery.LookupLocator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class verifies that when the <code>setLocators</code> method, rather
 * than the <code>register</code> method, is used to configure a "late-joiner"
 * registration with its desired locators, the lookup discovery service sends
 * the appropriate remote discovery events to that registration's listener.
 *
 * This test was written to test the lookup discovery service when it is
 * used in the following way:
 * <p><ul>
 *   <li> a lookup service having locator 'l0' is started (group membership is
 *        irrelevant)
 *   <li> an initial registration is made with the lookup discovery,
 *        requesting NO_GROUPS and no locators be discovered
 *   <li> setLocators is invoked on that registration to request that locator
 *        'l0' be discovered
 *   <li> receipt of the expected remote discovery event by the listener for
 *        that initial registration is verified
 *   <li> a second registration is made with the lookup discovery service,
 *        again requesting that NO_GROUPS and no locators be discovered
 *   <li> another attempt is made to verify the receipt of the expected remote
 *        discovery event by the listener for the second registration
 * </ul><p>
 * 
 * If the lookup discovery service functions as specified, then upon invoking
 * <code>setLocators</code> for each registration, the listener of each
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * which accurately reflects the expected lookup services.
 * 
 * Specification test for LD3.2 and LD4.1.1 (Locator Mutator Methods)
 * Regression test for Bug ID 4434883
 */
public class LateRegsNotifiedOnSetLocs extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> create a first set of registration(s) with the lookup discovery
     *      service, requesting that NO_GROUPS and no locators be discovered
     * <li> calls setLocators, requesting the discovery of the locators of
     *      the lookup service(s) started in construct
     * <li> verifies that the discovery process is working for the first
     *      registration by waiting for the expected discovery events
     * <li> creates a second set of registration(s) with the lookup discovery
     *      service, again requesting that NO_GROUPS and no locators be
     *      discovered
     * <li> again calls setLocators, requesting the discovery of the same
     *      locators with which the first registration is configured
     * <li> verifies that the discovery process is working for the second
     *      registration by waiting for the expected discovery events
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        String[]        noGroups = DiscoveryGroupManagement.NO_GROUPS;
        LookupLocator[] noLocs   = new LookupLocator[0];
        LookupLocator[] locsToDiscover = getLocatorsToDiscover
                                                         (useOnlyLocDiscovery);
        /* create the 1st set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,noLocs,i,leaseDuration);
        }//end loop
        /* set the locators to discover for the 1st set of registrations */
        logger.log(Level.FINE, "set loators on initial "
                          +"registration(s)");
        setLocatorsAllRegs(locsToDiscover);
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

        HashMap regMap0 = (HashMap)registrationMap.clone();

        /* create 2nd set of registrations */
        int totalRegs = nRegistrations+nAddRegistrations;
        for(int i=nRegistrations;i<totalRegs;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,noLocs,i,leaseDuration);
        }//end loop
        /* set the locators to discover for the 2nd set of registrations */
        logger.log(Level.FINE, "set locators on additional "
                          +"registration(s)");
        Set eSet = registrationMap.entrySet();
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
            setLocatorsOneReg( locsToDiscover, regListenerPair );
            if( locsToDiscover.length == 0 ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"locators set to NO_LOCATORS");
            } else {
                LocatorsUtil.displayLocatorSet( locsToDiscover,
                                           "   additional reg -- locator",
                                           Level.FINE);
            }//endif
        }//end loop
        logger.log(Level.FINE, "wait for discovery on "
                         +"additional registration(s)");
        waitForDiscovery();
        logger.log(Level.FINE, "discovery wait period "
                          +"complete");
    }//end run

} //end class LateRegsNotifiedOnSetLocs

