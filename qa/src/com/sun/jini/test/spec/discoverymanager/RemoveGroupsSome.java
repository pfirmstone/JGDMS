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

package com.sun.jini.test.spec.discoverymanager;

import java.util.logging.Level;

import com.sun.jini.test.share.GroupsUtil;
import net.jini.discovery.DiscoveryGroupManagement;
import java.util.ArrayList;
import java.util.List;
import com.sun.jini.qa.harness.QAConfig;

/**
 * With respect to the <code>removeGroups</code> method, this class verifies
 * that the <code>LookupDiscoveryManager</code> utility operates in a manner
 * consistent with the specification. 
 * <p>
 * In particular, this class verifies that when the lookup discovery manager
 * is configured to discover a set of lookup services through group and/or
 * locator discovery, and the <code>removeGroups</code> method is invoked to 
 * reconfigure the lookup discovery manager to discover only SOME of
 * the original set of groups to discover (which may be the empty set),
 * the lookup discovery manager will send to the client's listener, the
 * appropriate events with the appropriate contents.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover some of the
 *         lookups by only group discovery, some by only locator discovery,
 *         and some by both group and locator discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, the member groups of SOME of the lookups are
 *         removed from the lookup discovery manager
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded events, with the
 * expected contents.
 */
public class RemoveGroupsSome extends Discovered {

    protected String[]  groupsToRemove = DiscoveryGroupManagement.NO_GROUPS;
    protected String[]  newGroupsToDiscover 
                                        = DiscoveryGroupManagement.NO_GROUPS;
    protected boolean   alternateRemoval = true;

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> reconfigures the lookup discovery manager to discover some of
     *         the lookups by only group discovery, some by only locator
     *         discovery, and some by both group and locator discovery
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> invokes the removeGroups method on the lookup discovery 
     *         manager to remove the member groups of some of the lookups
     *    <li> verifies that the lookup discovery manager utility under test
     *         sends the expected events, with the expected contents
     */
    public void run() throws Exception {
        super.run();
        setGroupsToRemove(getInitLookupsToStart(),alternateRemoval);
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and setLookupsToDiscover
         * examines the contents of those maps. So we don't want those
         * maps to change until setLookupsToDiscover returns.
         */
        synchronized(mainListener) {
            /* Set the expected discarded event info */
            mainListener.setLookupsToDiscover(getInitLookupsToStart(),
                                              locatorsToDiscover,
                                              newGroupsToDiscover);
        }//end sync(mainListener)
        logger.log(Level.FINE, "remove groups to discover -- ");
        if((groupsToRemove != null) && (groupsToRemove.length<=0)) {
            logger.log(Level.FINE, "   NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groupsToRemove,
                                       "   removeGroup",Level.FINE);
        }//endif
        discoveryMgr.removeGroups(groupsToRemove);
        waitForDiscard(mainListener);
        logger.log(Level.FINE, 
		   "discarded events arrived as expected, "
		   +"waiting for expected changed events ...");
    }//end run

    /** Common code, shared by this class and its sub-classes, that is 
     *  invoked by the run() method. This method constructs the set of
     *  groups to remove from the lookup discovery manager.
     */
    void setGroupsToRemove(List list, boolean alternate) {
        List removeList = new ArrayList(11);
        List newDiscoverList = new ArrayList(11);
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] curGroups = pair.getGroups();
            if( (curGroups == null) || (curGroups.length == 0) ) continue;
            if( ((i%2) == 0) || !alternate ) {//index is even or removeAll
                for(int j=0;j<curGroups.length;j++) {
                    removeList.add(new String(curGroups[j]));
                }//end loop(j)
            } else {//don't remove these groups, discover them
                for(int j=0;j<curGroups.length;j++) {
                    newDiscoverList.add(new String(curGroups[j]));
                }//end loop(j)
            }//endif
        }//end loop(i)
        if(removeList.size() > 0) {
            groupsToRemove = ((String[])(removeList).toArray
                                             (new String[removeList.size()]));
        }//endif
        if(newDiscoverList.size() > 0) {
            newGroupsToDiscover = ((String[])(newDiscoverList).toArray
                                         (new String[newDiscoverList.size()]));
        }//endif
    }//end setGroupsToRemove

}//end class RemoveGroupsSome

