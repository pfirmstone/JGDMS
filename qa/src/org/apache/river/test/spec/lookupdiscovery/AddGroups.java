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
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;

import org.apache.river.test.share.GroupsUtil;

/**
 * With respect to the <code>addGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>addGroups</code> method to re-configure
 * the <code>LookupDiscovery</code> utility to discover a set of groups
 * which includes the original set of groups to discover as well as an
 * additional set of groups, that utility will send discovered events
 * referencing any previously un-discovered lookup services whose member
 * groups belong to one or more of the additional set of groups to discover.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more "initial" lookup services, each belonging to a finite
 *         set of member groups, and each started during construct, before the
 *         test begins execution
 *    <li> one or more "additional" lookup services, each belonging to a finite
 *         set of member groups that does not include any of the member groups
 *         of the initial set of lookup services, and each started after the
 *         test has begun execution
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is initially configured to discover
 *         the set of groups whose elements are the member groups of the
 *         initial lookup services
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then after
 * invoking the <code>addGroups</code> method, the listener will receive the
 * expected discovery events, with the expected contents.
 */
public class AddGroups extends Discovered {

    protected String[] groupsToAdd = new String[0];

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	startAddLookups();//Start the additional lookup services
	groupsToAdd = toGroupsArray(getAddLookupsToStart());
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies that the lookup discovery utility under test
     *         discovers the initial lookup services that were started 
     *         during construct
     *    <li> re-configures the listener's expected event state to expect
     *         the discovery of the addtional lookup services
     *    <li> invokes addGroups to re-configure the lookup discovery utility
     *         to discover the new set of groups in addtion to the initial set
     *    <li> verifies that the lookup discovery utility under test
     *         sends the expected discovered events, having the expected 
     *         contents related to the additional lookups that were started
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Verify discovery of the initial lookups */
        doDiscovery(getInitLookupsToStart(),listenerToUse);
        /* Configure the listener's expected event state for the additional
         * lookup services
         */
        listenerToUse.clearAllEventInfo();
        listenerToUse.setLookupsToDiscover(getAddLookupsToStart());
        /* Re-configure the lookup discovery utility to discover the
         * additional lookups
         */
        ldToUse.addGroups(groupsToAdd);
        logger.log(Level.FINE, "added additional groups to "
                          +"lookup discovery --");
        GroupsUtil.displayGroupSet(groupsToAdd,"  group",
                                   Level.FINE);
        /* Verify discovery of the added lookups */
        waitForDiscovery(listenerToUse);
    }//end run

}//end class AddGroups

