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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * This class verifies that when the <code>setLocators</code> method is called
 * more than once to configure/re-configure a "late-joiner" registration
 * for various locators of interest, the lookup discovery service sends to
 * that registration's listener, the appropriate remote discovery and discard
 * events.
 *
 * If the lookup discovery service functions as specified, then for
 * each invocation of <code>setLocators</code> on each registration,
 * the listener of each such registration will receive an instance of
 * <code>RemoteDiscoveryEvent</code> corresponding to each new discovery
 * or discard which occurs; and which accurately reflects the expected
 * lookup services.
 *
 * Note that discard events will occur only if the lookup services started for
 * this test, as well as the locators of interest for each registration, are
 * configured in such a way that when <code>setLocators</code> is called, a
 * "no interest" discard would normally occur.
 */
public class LateRegsNotifiedOn2SetLocs extends AbstractBaseTest {

    protected LookupLocator[] locs0;
    protected LookupLocator[] locs1;

    /** Performs actions necessary to prepare for execution of the 
     *  current test. Populates the sets of locators that are passed in
     *  to the <code>setLocators</code> method. The intent is to guarantee
     *  that those two sets of locators do not share any elements so that
     *  for properly configured lookup services, discard events, as well as
     *  discovery events will be generated.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        logger.log(Level.FINE, "setup()");
        LookupLocator[] allLocsToDiscover = getLocatorsToDiscover
                                                       (useOnlyLocDiscovery);
        int len = allLocsToDiscover.length;
        int len0 = ( (len > 1) ? (len/2) : len);
        int len1 = ( (len > 1) ? (len-len0) : len);
        locs0 = new LookupLocator[len0];
        locs1 = new LookupLocator[len1];
        for(int i=0;i<len0;i++) {
            locs0[i] = allLocsToDiscover[i];
        }//end loop
        if(len1 == len) {
            locs1[0] = allLocsToDiscover[0];
        } else {
            for(int i=len0;i<len;i++) {
                locs1[i-len0] = allLocsToDiscover[i];
            }//end loop
        }//endif
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> create a first set of registration(s) with the lookup discovery
     *      service, requesting that NO_GROUPS and no locators be discovered
     * <li> calls setLocators, requesting the discovery of the locators of
     *      the lookup service(s) started in construct
     * <li> verifies that the discovery/discard process is working for the
     *      initial registration(s) by waiting for the expected discovery
     *      and discard events
     * <li> calls setLocators, requesting the discovery of the 2nd set of
     *      locators which should equal the locators of the 2nd lookup service
     *      started in construct
     * <li> verifies that the discovery/discard process is working for the
     *      initial registration(s) by waiting for the expected discovery and
     *      discard events
     * <li> creates a second set of registration(s) with the lookup discovery
     *      service, again requesting that NO_GROUPS and no locators be
     *      discovered
     * <li> calls setLocators on the second set of registration(s), requesting
     *      the discovery of the 1st set of locators
     * <li> verifies that the discovery/discard process is working for the
     *      second set of registration(s) by waiting for the expected
     *      discovery and discard events
     * <li> calls setLocators on the second set of registration(s), this time
     *      requesting the discovery of the 2nd set of locators
     * <li> verifies that the discovery/discard process is working for the
     *      second set of registration(s) by waiting for the expected
     *      discovery and discard events
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        String[]        noGroups = DiscoveryGroupManagement.NO_GROUPS;
        LookupLocator[] noLocs   = new LookupLocator[0];
        /* create the first set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,noLocs,i,leaseDuration);
        }//end loop
        /* for 1st reg, set 1st set of locators to discover */
        logger.log(Level.FINE, "set 1st locator set on "
                          +"initial registration(s)");
        setLocatorsAllRegs(locs0);
        logger.log(Level.FINE, "wait for discovery of the 1st "
                          +"locator set of initial registration(s)");
        waitForDiscovery();
        /* for 1st reg, set 2nd set of locators to discover */
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... set 2nd locator set on "
                          +"initial registration(s)");
        setLocatorsAllRegs(locs1);
        logger.log(Level.FINE, "wait for expected discovery "
                          +"and discard events generated by the 2nd "
                          +"locator set of initial registration(s)");
        waitForDiscovery();
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait periods "
                          +"complete ... request "+nAddRegistrations
                          +" additional registration(s)");

        HashMap regMap0 = (HashMap)registrationMap.clone();

        /* create second set of registrations */
        int totalRegs = nRegistrations+nAddRegistrations;
        for(int i=nRegistrations;i<totalRegs;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(noGroups,noLocs,i,leaseDuration);
        }//end loop
        /* for 2nd reg, set 1st set of locators to discover */
        logger.log(Level.FINE, "set 1st locator set on "
                          +"additional registration(s)");
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Skip registrations from the initial set of registrations */
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            if( regMap0.containsKey(regListenerPair.getKey()) ) {
                /* Must reset discovery & discard event info for these
                 * registrations since their 'discoveryComplete' and
                 * 'discardComplete' flags were set to true the last
                 * time waitForDiscovery() and waitForDiscard() were
                 * invoked.
                 */
                resetAllEventInfoOneReg(regListenerPair);
                continue;
            }//endif
            setLocatorsOneReg( locs0, regListenerPair );

            if( locs0.length == 0 ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"locators set to NO_LOCATORS");
            } else {
                LocatorsUtil.displayLocatorSet( locs0,
                                            "   additional reg -- locator",
                                            Level.FINE);
            }//endif
        }//end loop
        logger.log(Level.FINE, "wait for discovery of the 1st "
                         +"locator set of the additional registration(s)");
        waitForDiscovery();
        /* for 2nd reg, set 2nd set of locators to discover */
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... set 2nd locator set on "
                          +"additional registration(s)");
        iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Skip registrations from the initial set of registrations */
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            if( regMap0.containsKey(regListenerPair.getKey()) ) {
                /* Must reset discovery event info for these
                 * registrations since their 'discoveryComplete' flag
                 * was set to true the last time waitForDiscovery()
                 * was invoked.
                 */
                resetAllEventInfoOneReg(regListenerPair);
                continue;
            }//endif
            setLocatorsOneReg( locs1, regListenerPair );
            if( locs1.length <= 0 ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"locators set to NO_LOCATORS");
            } else {
                LocatorsUtil.displayLocatorSet( locs1,
                                            "   additional reg -- locator",
                                            Level.FINE);
            }//endif
        }//end loop
        logger.log(Level.FINE, "wait for expected discovery "
                          +"and discard events generated by the 2nd "
                         +"locator set of the additional registration(s)");
        waitForDiscovery();
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait periods complete");
    }//end run

} //end class LateRegsNotifiedOn2SetLocs

