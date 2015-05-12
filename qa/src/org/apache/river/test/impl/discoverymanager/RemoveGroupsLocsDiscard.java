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

package org.apache.river.test.impl.discoverymanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.discoverymanager.AbstractBaseTest;
import org.apache.river.test.share.GroupsUtil;
import org.apache.river.test.share.LocatorsUtil;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.core.discovery.LookupLocator;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.List;

/**
 * This class acts as a regression test for bug ID 4510435. This class verifies
 * that the previous faulty behavior in the <code>LookupDiscoveryManager</code>
 * utility, as described in that bug, has been correted. That faulty can
 * be described as follows:
 *
 * Suppose a lookup discovery manager discovers a lookup service by both group
 * and locator discovery. If, after that lookup service has been discovered,
 * the client of the lookup discovery manager then invokes either 
 * <code>setGroups</code> or <code>removeGroups</code> with input parameters
 * such that the resulting set of desired groups no longer intersects the
 * member groups of the previously discovered lookup service, the method 
 * <code>discarded</code> method of the lookup discovery manager discards
 * the lookup service, even though the client is still interested in that
 * lookup service through locator discovery. Since the discarded lookup
 * service was also discovered by the lookup discovery manager throught
 * locator discovery, that lookup service should not have been discarded
 * after the call to <code>setGroups</code> or <code>removeGroups</code>.
 *
 * A similar unintended discard occurs if the discovery mechanism roles are
 * reversed. That is, if after discovery, <code>setLocators</code> or
 * <code>removeLocators</code> is called so that interest in the lookup
 * service through locator discovery is removed, then a discard also occurs;
 * which is faulty behavior.
 * 
 * This test verifies that the current <code>LookupDiscoveryManager</code>
 * implementation has been modified to correct the above described faulty
 * behavior.
 *
 * Refer to the description for bug ID 4510435.
 *
 */
public class RemoveGroupsLocsDiscard extends AbstractBaseTest implements Test {

    /** Constructs an instance of this class. Initializes this classname */
    public RemoveGroupsLocsDiscard() {
        useFastTimeout=true;
        fastTimeout = 30;
    }//end constructor

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> creates 1 lookup service having locator L0, and belonging to
     *         to groups {G0,G1,G2}
     *    <li> creates a lookup discovery manager (LDM) initially configured
     *         to discover NO_GROUPS and NO_LOCATORS
     *    <li> reconfigures the LDM to discover groups {G0,G1,G2}
     *    <li> verifies that the LDM sends 1 appropriate discovered event
     *    <li> reconfigures the LDM to discover locator L0
     *    <li> verifies that the LDM sends no discovered events
     *    <li> reconfigures the LDM to no longer discover groups {G0,G1,G2}
     *    <li> verifies that the LDM sends no discarded events
     *    <li> reconfigures the LDM to no longer discover locator L0
     *    <li> verifies that the LDM sends 1 appropriate discarded event
     *    <li> repeats the above with the order of group and locator discovery
     *         swapped
     *    </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        List initLookupsToStart = getLookupServices().getInitLookupsToStart();
        /* From the lookups that have been started, retrieve the groups
         * and locators to discover. We want a "mix" where some of the
         * lookups started should be discovered by both group and locator,
         * some should be disocvered by only group discovery, and some
         * should be discovered by only loator discovery.
         */
        String[] groupsToDiscover = toGroupsToDiscover
                                                   (initLookupsToStart,
                                                    AbstractBaseTest.MIX);
        LookupLocator[] locsToDiscover = toLocatorsToDiscover
                                                   (initLookupsToStart,
                                                    AbstractBaseTest.MIX);
        /* Configure the expectedDiscoveredMap in the listener registered
         * with the LDM so that when group and/or locator discovery is
         * turned on in the LDM, the discovered events that arrive are
         * consistent with the discovery events that are expected.
         */
        mainListener.setLookupsToDiscover(initLookupsToStart,
                                          locsToDiscover,
                                          groupsToDiscover);
        /* Add the configured listener to the LDM so that we can receive
         * and process discovered and discarded events.
         */
        discoveryMgr.addDiscoveryListener(mainListener);
        /* 1. Turn on group discovery in the LDM by setting the groups
         *    to discover. Then verify that the appropriate discovered
         *    events arrive.
         */
        logger.log(Level.FINE, "1. SET groups to discover -- ");
        GroupsUtil.displayGroupSet(groupsToDiscover, "   group", Level.FINE);
        discoveryMgr.setGroups(groupsToDiscover);
        waitForDiscovery(mainListener);
        /* 2. Turn on locator discovery in the LDM by setting the locators
         *    to discover. Then verify that NO DISCOVERED EVENTS ARRIVE.
         *    Since the LDM has discovered the lookups by group discovery
         *    already, it should send no more discovered events. To verify
         *    that no discovered events are sent by the LDM, the
         *    expectedDiscoveredMap in the listener should be cleared.
         */
        mainListener.clearDiscoveryEventInfo();
        logger.log(Level.FINE, "2. SET locators to discover -- ");
        LocatorsUtil.displayLocatorSet(locsToDiscover, 
				       "   locator", Level.FINE);
        discoveryMgr.setLocators(locsToDiscover);
        waitForDiscovery(mainListener);
        /* 3. Turn off group discovery in the LDM by removing all of
         *    the groups to discover from the LDM. Then verify that NO
         *    DISCARDED EVENTS ARRIVE. The failure mode being tested for
         *    here, which is now corrected in the LDM (and which this
         *    regression test verifies has indeed been corrected),
         *    manifests itself by sending a discarded event when the 
         *    groups of interest are removed. Because of this, and because
         *    the listener's discoveredMap was cleared for the
         *    verification above, below the discoveredMap is re-populated
         *    with the previously discovered lookups. If this is not done,
         *    the waitForDiscard() method will ignore any discarded events
         *    received that reference those lookups; and thus, a failure
         *    that occurs here will not be recognized and flagged.
         */
        logger.log(Level.FINE, "3. REMOVE groups to discover -- ");
        GroupsUtil.displayGroupSet(groupsToDiscover, "   group", Level.FINE);
        mainListener.setDiscoveredMap(initLookupsToStart);
        discoveryMgr.removeGroups(groupsToDiscover);
        waitForDiscard(mainListener);
        /* 4. Turn off locator discovery in the LDM by the removing all of
         *    the locators to discover from the LDM. Then verify that
         *    discarded events actually do arrive now. Note that the
         *    expected discarded event info must be explicitly set since
         *    the data structures of the listener were cleared above.
         */
        mainListener.clearDiscoveryEventInfo();
        mainListener.setDiscardEventInfo(initLookupsToStart);
        logger.log(Level.FINE, "4. REMOVE locators to discover -- ");
        LocatorsUtil.displayLocatorSet( locsToDiscover,
				       "   locator", Level.FINE);
        discoveryMgr.removeLocators(locsToDiscover);
        waitForDiscard(mainListener);
        /* REVERSE: do the same as above, but reverse the order of locator
         * and group discovery. So first start over by clearing the
         * expected event info data structures in the listener.
         */
        logger.log(Level.FINE, "");
        logger.log(Level.FINE, "*** REVERSE THE DISCOVERY MECHANISM ORDER ***");
        logger.log(Level.FINE, "");
        mainListener.clearAllEventInfo();
        mainListener.setLookupsToDiscover(initLookupsToStart,
                                          locsToDiscover,
                                          groupsToDiscover);
        /* 5. Turn on locator discovery in the LDM by setting the locators
         *    to discover. Then verify that the appropriate discovered
         *    events arrive.
         */
        logger.log(Level.FINE, "5. SET locators to discover -- ");
        LocatorsUtil.displayLocatorSet(locsToDiscover,
				       "   locator", Level.FINE);
        discoveryMgr.setLocators(locsToDiscover);
        waitForDiscovery(mainListener);
        /* 6. Turn on group discovery in the LDM by setting the groups
         *    to discover. Then verify that NO DISCOVERED EVENTS ARRIVE.
         *    Since the LDM has discovered the lookups by locator discovery
         *    already, it should send no more discovered events. To verify
         *    that no discovered events are sent by the LDM, the
         *    expectedDiscoveredMap in the listener should be cleared.
         */
        mainListener.clearDiscoveryEventInfo();
        logger.log(Level.FINE, "6. SET groups to discover -- ");
        GroupsUtil.displayGroupSet(groupsToDiscover, "   group", Level.FINE);
        discoveryMgr.setGroups(groupsToDiscover);
        waitForDiscovery(mainListener);
        /* 7. Turn off locator discovery in the LDM by removing all of
         *    the locators to discover from the LDM. Then verify that NO
         *    DISCOVERED EVENTS ARRIVE. The failure mode being tested for
         *    here, which is now corrected in the LDM (and which this
         *    regression test verifies has indeed been corrected),
         *    manifests itself by sending a discarded event when the 
         *    locator is removed, with the discarded event eventually
         *    being followed by a discovered event. A discovered event
         *    follows the discarded event because the removal of the
         *    locator resulted in an unintended discard of the lookup
         *    service (due to the bug in the LDM), but because the LDM is
         *    configured to also discover that lookup service by its
         *    groups, the LDM eventually re-discovers the discarded lookup
         *    service and sends a discovered event.
         *
         *    Thus, when working correctly, the LDM should send no
         *    discarded events, and no discovered events. When not working
         *    correctly, the LDM will send a discarded event, followed by
         *    a discovered event. Because of the way the listener tracks
         *    the events, adding and removing elements from the
         *    discardedMap as discarded events and discovered events are
         *    received from the LDM, a call to waitForDiscard() below will
         *    not catch any unintended discarded event if the LDM fails
         *    here. This is because the discarded event and the discovered
         *    event arrive so quickly that the discovered event will cause
         *    the listener to remove the discarded information from the
         *    discardedMap before waitForDiscard() has a chance to 
         *    realize that the lookup has been discarded. This means that
         *    at this point, only waitForDiscovery() can be used to flag a
         *    failure or verify that the LDM is working correctly. Thus,
         *    upon failure, this section of the test will flag an
         *    unexpected discovered event, which implies there was also a
         *    corresponding unexpected discarded event that preceded the
         *    discovered event.
         */
        logger.log(Level.FINE, "7. REMOVE locators to discover -- ");
        LocatorsUtil.displayLocatorSet(locsToDiscover,
				       "   locator", Level.FINE);
        mainListener.clearDiscoveryEventInfo();
        discoveryMgr.removeLocators(locsToDiscover);
        waitForDiscovery(mainListener);
        /* 8. Turn off group discovery in the LDM by the removing all of
         *    the groups to discover from the LDM. Then verify that
         *    discarded events actually do arrive now. Note that the
         *    expected discarded event info must be explicitly set since
         *    the data structures of the listener were cleared above.
         */
        mainListener.setDiscardEventInfo(initLookupsToStart);
        logger.log(Level.FINE, "8. REMOVE groups to discover -- ");
        GroupsUtil.displayGroupSet(groupsToDiscover, "   group", Level.FINE);
        discoveryMgr.removeGroups(groupsToDiscover);
        waitForDiscard(mainListener);

        return;
    }//end run

}//end class RemoveGroupsLocsDiscard

