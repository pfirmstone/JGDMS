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
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

import net.jini.core.discovery.LookupLocator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that when the <code>setGroups</code> method is called
 * more than once to configure/re-configure a "late-joiner" registration
 * for various groups of interest, the lookup discovery service sends to
 * that registration's listener, the appropriate remote discovery and discard
 * events.
 *
 * If the lookup discovery service functions as specified, then for
 * each invocation of <code>setGroups</code> on each registration,
 * the listener of each such registration will receive an instance of
 * <code>RemoteDiscoveryEvent</code> corresponding to each new discovery
 * or discard which occurs; and which accurately reflects the correct set of
 * member groups.
 *
 * Note that discard events will occur only if the lookup services started for
 * this test, as well as the groups of interest for each registration, are
 * configured in such a way that when <code>setGroups</code> is called, a
 * "no interest" discard would normally occur.
 */
public class LateRegsNotifiedOn2SetGroups extends AbstractBaseTest {

    protected String[] groups0;
    protected String[] groups1;

    /** Performs actions necessary to prepare for execution of the 
     *  current test. Populates the sets of group names that are passed
     *  in to the <code>setGroups</code> method. The intent is to guarantee
     *  that those two sets of group names do not share any names so that
     *  for properly configured lookup services, discard events, as well as
     *  discovery events will be generated.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        logger.log(Level.FINE, "setup()");
        String[] allGroupsToDiscover = getGroupsToDiscover
                                                      (useOnlyGroupDiscovery);
        int len = allGroupsToDiscover.length;
        int len0 = ( (len > 1) ? (len/2) : len);
        int len1 = ( (len > 1) ? (len-len0) : len);
        groups0 = new String[len0];
        groups1 = new String[len1];
        for(int i=0;i<len0;i++) {
            groups0[i] = allGroupsToDiscover[i];
        }//end loop
        if(len1 == len) {
            groups1[0] = allGroupsToDiscover[0];
        } else {
            for(int i=len0;i<len;i++) {
                groups1[i-len0] = allGroupsToDiscover[i];
            }//end loop
        }//endif
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> create a first set of registration(s) with the lookup discovery
     *      service, requesting that NO_GROUPS and no locators be discovered
     * <li> calls setGroups, requesting the discovery of the 1st set of groups,
     *      which should equal the member groups of the 1st lookup service
     *      started in setup
     * <li> verifies that the discovery/discard process is working for the
     *      1st registration by waiting for the expected discovery and
     *      discard events
     * <li> calls setGroups, requesting the discovery of the 2nd set of groups,
     *      which should equal the member groups of the 2nd lookup service
     *      started in setup
     * <li> verifies that the discovery/discard process is working for the
     *      1st registration by waiting for the expected discovery and
     *      discard events
     * <li> creates a second set of registration(s) with the lookup discovery
     *      service, again requesting that NO_GROUPS and no locators be
     *      discovered
     * <li> again calls setGroups, requesting the discovery of the 1st set
     *      of groups
     * <li> verifies that the discovery/discard process is working for the
     *      2nd registration by waiting for the expected discovery and
     *      discard events
     * <li> again calls setGroups, requesting the discovery of the 2nd set
     *      of groups
     * <li> verifies that the discovery/discard process is working for the
     *      2nd registration by waiting for the expected discovery and
     *      discard events
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        LookupLocator[] noLocs = getLocatorsToDiscover(useOnlyGroupDiscovery);
        /* create the first set of registrations */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(DiscoveryGroupManagement.NO_GROUPS,noLocs,
                           i,leaseDuration);
        }//end loop
        /* for 1st reg, set 1st set of groups to discover */
        logger.log(Level.FINE, "set 1st group set on "
                          +"initial registration(s)");
        setGroupsAllRegs(groups0);

        logger.log(Level.FINE, "wait for discovery of the 1st "
                          +"group set of initial registration(s)");
        waitForDiscovery();
        /* for 1st reg, set 2nd set of groups to discover */
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... set 2nd group set on "
                          +"initial registration(s)");

        setGroupsAllRegs(groups1);
        logger.log(Level.FINE, "wait for expected discovery "
                          +"and discard events generated by the 2nd group "
                          +"set of initial registration(s)");
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
            doRegistration(DiscoveryGroupManagement.NO_GROUPS,noLocs,
                           i,leaseDuration);
        }//end loop


        /* for 2nd reg, set 1st set of groups to discover */
        logger.log(Level.FINE, "set 1st group set on "
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
            setGroupsOneReg( groups0, regListenerPair );
            if( groups0 == DiscoveryGroupManagement.ALL_GROUPS ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"groups set to ALL_GROUPS");
            } else if( groups0.length <= 0 ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"groups set to NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet( groups0,
                                           "   additional reg -- group",
                                           Level.FINE);
            }//endif
        }//end loop
        logger.log(Level.FINE, "wait for discovery of the 1st "
                          +"group set of the additional registration(s)");
        waitForDiscovery();


        /* for 2nd reg, set 2nd set of groups to discover */
        logger.log(Level.FINE, "discovery wait period "
                          +"complete ... set 2nd group set on "
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
            setGroupsOneReg( groups1, regListenerPair );
            if( groups1 == DiscoveryGroupManagement.ALL_GROUPS ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"groups set to ALL_GROUPS");
            } else if( groups1.length <= 0 ) {
                logger.log(Level.FINE, "   additional reg -- "
                                  +"groups set to NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet( groups1,
                                           "   additional reg -- group",
                                           Level.FINE);
            }//endif
        }//end loop
        logger.log(Level.FINE, "wait for expected discovery "
                          +"and discard events generated by the 2nd group "
                          +"set of the additional registration(s)");
        waitForDiscovery();
        waitForDiscard(NO_INTEREST_DISCARDED);
        logger.log(Level.FINE, "wait periods complete");
    }//end run

} //end class LateRegsNotifiedOn2SetGroups

