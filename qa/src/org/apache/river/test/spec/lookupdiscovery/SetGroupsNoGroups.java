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

package org.apache.river.test.spec.lookupdiscovery;
import org.apache.river.qa.harness.QAConfig;

import java.util.logging.Level;

import org.apache.river.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that when the parameter input to the <code>setGroups</code> method is
 * an empty array (<code>NO_GROUPS</code>), group discovery is halted until
 * the <code>LookupDiscovery</code> utility is once again configured - through
 * either another call to <code>setGroups</code>, or a call to 
 * <code>addGroups</code> - to discover either a non-empty set of member
 * groups, or <code>ALL_GROUPS</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more "initial" lookup services, each belonging to a finite
 *         set of member groups, and each started during construct, before the
 *         test begins execution
 *    <li> one or more "additional" lookup services, each belonging to a finite
 *         set of member groups, and each started after the test has begun
 *         execution, and after the initial lookup services started during
 *         construct have been discovered
 *    <li> one instance of the lookup discovery utility initially configured
 *         to discover the member groups of the initial lookup services
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery utility
 *    <li> after the lookup discovery utility is constructed, setGroups is
 *         invoked with NO_GROUPS to halt discovery processing; and is
 *         eventually invoked again to restart discovery processing for
 *         the addtional lookup services
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the client's
 * listener will receive the expected discovered events, with the expected
 * contents, at the expected times during the test.
 */
public class SetGroupsNoGroups extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> configures the lookup discovery utility to discover the
     *         member groups of both the initial lookup services started
     *         during construct, and the addtional lookup services started below
     *    <li> starts the multicast discovery process by adding a discovery
     *         listener to the lookup discovery utility
     *    <li> verifies that group discovery is currently operational by
     *         verifying that the lookup discovery utility under test sends
     *         the expected discovered events, having the expected contents
     *         related to the initial lookups that were started
     *    <li> invokes setGroups with NO_GROUPS to halt discovery processing
     *    <li> starts an additional set of lookups to discover
     *    <li> verifies that group discovery has been halted by verifying that
     *         the lookup discovery utility sends no discovered events 
     *    <li> invokes setGroups again, inputting all of the member groups
     *         with which the lookup discovery utility was initially configured
     *    <li> verifies that group discovery has been restarted by verifying
     *         that the lookup discovery utility under test sends the expected
     *         discovered events, having the expected contents related to the
     *         additional lookups that were started
     * </ul>
     */
    public void run() throws Exception {
        String[] initGroupsToDiscover = toGroupsArray(getInitLookupsToStart());
        String[] allGroupsToDiscover  = toGroupsArray(getAllLookupsToStart());
        /* Set the expected groups to discover to be the initial lookups */
        mainListener.setLookupsToDiscover(getInitLookupsToStart(),
                                          initGroupsToDiscover);
        logger.log(Level.FINE, "starting discovery, groups "
                                        +"to discover --");
        GroupsUtil.displayGroupSet(allGroupsToDiscover,"group",
                                   Level.FINE);
        /* Configure for discovery of both initial & additional lookups */
        lookupDiscovery.setGroups(allGroupsToDiscover);
        /* Start discovery by adding the listener to LookupDiscovery */
        lookupDiscovery.addDiscoveryListener(mainListener);
        /* Verify discovery of the initial lookups */
        waitForDiscovery(mainListener);

        /* Set expected lookups to be discarded due to discovery halting */
        mainListener.setLookupsToDiscover(getInitLookupsToStart(),
                                      DiscoveryGroupManagement.NO_GROUPS);
        logger.log(Level.FINE, "halting discovery, setting "
                          +"groups to discover to NO_GROUPS ...");
        /* Halt group discovery */
        lookupDiscovery.setGroups(DiscoveryGroupManagement.NO_GROUPS);
        /* Verify discard of previously discovered lookups */
        waitForDiscard(mainListener);

        /* After halting, expect NO MORE DISCOVERED EVENTS */
        mainListener.clearAllEventInfo();
        /* Start the additional lookup services */
        startAddLookups();
        /* Verify discovery has been halted (no events are sent) */
        waitForDiscovery(mainListener);

        /* Set expected groups after restart to both initial & additional*/
        mainListener.setLookupsToDiscover(getAllLookupsToStart(),
                                          allGroupsToDiscover);
        logger.log(Level.FINE, "re-starting discovery, groups "
                                        +"to discover --");
        GroupsUtil.displayGroupSet(allGroupsToDiscover,"group",
                                   Level.FINE);
        /* Restart group discovery */
        lookupDiscovery.setGroups(allGroupsToDiscover);
        /* Verify discovery has been restarted */
        waitForDiscovery(mainListener);
    }//end run

}//end class SetGroupsNoGroups

