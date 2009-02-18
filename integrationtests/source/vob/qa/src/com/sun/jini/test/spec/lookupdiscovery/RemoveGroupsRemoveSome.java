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

package com.sun.jini.test.spec.lookupdiscovery;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;

import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

import java.util.ArrayList;

/**
 * With respect to the <code>removeGroups</code> method, this class
 * verifies that the <code>LookupDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking the <code>removeGroups</code> method to
 * remove some (but not all) of the member groups with which the lookup
 * discovery utility was previously configured to discover, that utility will
 * send discarded events referencing the previously discovered lookup
 * services whose member groups equal none of the groups with which
 * the lookup discovery utility is configured to discover after the
 * call to <code>removeGroups</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite
 *        set of member groups, and each started during setup, before the
 *        test begins execution
 *   <li> one instance of the lookup discovery utility configured to discover
 *        the set of groups whose elements are the member groups of the
 *        initial lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 *   <li> after discovery, removeGroups is invoked to remove some (but not
 *        all) of the groups with which the lookup discovery utility
 *        was originally configured to discover; so that the remaining
 *        groups the lookup discovery utility is configured to discover
 *        include none of the member groups of at least one of the lookup
 *        services that were started
 * </ul><p>
 */
public class RemoveGroupsRemoveSome extends Discovered {

    protected ArrayList curLookupsToDiscover = initLookupsToStart;
    protected ArrayList newLookupsToDiscover = new ArrayList(11);
    protected ArrayList lookupsToRemoveList = new ArrayList(11);
    protected String[]  groupsToRemove = DiscoveryGroupManagement.NO_GROUPS;

    protected boolean changeAll = false;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
	/* Remove the groups for the lookup services at an even index.
	 * Remove groups at an odd index as well if changeAll is true.
	 */
	for(int i=0;i<curLookupsToDiscover.size();i++) {
	    LocatorGroupsPair curPair =
		(LocatorGroupsPair)curLookupsToDiscover.get(i);
	    if( ((i%2) == 0) || changeAll ) {//index is even or changeAll
		String[] curGroups = curPair.groups;
		if(    (curGroups != DiscoveryGroupManagement.ALL_GROUPS)
		       && (curGroups.length != 0) )
                    {
                        for(int j=0;j<curGroups.length;j++) {
                            lookupsToRemoveList.add(curGroups[j]);
                        }//end loop
                    }//endif
	    } else {
		newLookupsToDiscover.add(curPair);
	    }//endif
	}//end loop
	groupsToRemove =(String[])(lookupsToRemoveList).toArray
	    (new String[lookupsToRemoveList.size()]);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup discovery utility to use group 
     *         discovery to discover the lookup services started during setup
     *    <li> starts the multicast discovery process by adding a discovery
     *         listener to the lookup discovery utility
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> invokes the removeGroups method on the lookup discovery utility
     *         to remove the indicated groups
     *    <li> verifies that the lookup discovery utility under test sends the
     *         expected discarded events
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        logger.log(Level.FINE,
                     "remove groups from LookupDiscovery -- ");
        GroupsUtil.displayGroupSet(groupsToRemove,"  groupsToRemove",
                                   Level.FINE);
        /* Reset the listener's expected discard state */
        listenerToUse.setLookupsToDiscover(newLookupsToDiscover);
        ldToUse.removeGroups(groupsToRemove);
        waitForDiscard(listenerToUse);
    }//end run

}//end class RemoveGroupsRemoveSome

