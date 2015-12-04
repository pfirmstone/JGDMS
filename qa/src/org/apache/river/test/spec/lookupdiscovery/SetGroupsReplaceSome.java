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

import java.util.ArrayList;
import java.util.List;

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>setGroups</code> method to re-configure
 * the <code>LookupDiscovery</code> utility to discover a new set of
 * member groups which replaces the current set of member groups to discover,
 * and which contains some (but not all) of the member groups with which it was
 * previously configured, that utility will send discarded events referencing
 * the previously discovered lookup services that do not belong to
 * any of the new member groups.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of LookupDiscovery configured to discover the union
 *        of the member groups to which each lookup belongs 
 *   <li> one DiscoveryListener registered with that LookupDiscovery
 *   <li> after discovery, the LookupDiscovery utility is re-configured
 *        to discover a new set of member groups; a set that contains only
 *        some of the groups with which it was originally configured
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the listener
 * will receive the expected discarded events, having the expected contents.
 */
public class SetGroupsReplaceSome extends Discovered {

    protected List oldLookupsToDiscover = null;
    protected String[] newGroupsToDiscover = new String[] {"SetGroups_newSet"};

    protected boolean changeAll = false;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
	oldLookupsToDiscover = locGroupsList;
	ArrayList newGroupsList = new ArrayList(11);
	/* Change the groups to discover for the lookup services at an
	 * even index. Change groups to discover at an odd index as well
	 * if changeAll is true.
             */
	for(int i=0;i<oldLookupsToDiscover.size();i++) {
	    LocatorGroupsPair oldPair =
		(LocatorGroupsPair)oldLookupsToDiscover.get(i);
	    String[] oldGroups = oldPair.getGroups();
	    String[] newGroups = oldGroups;
	    if( ((i%2) == 0) || changeAll ) {//index is even or changeAll
		if(oldGroups.length == 0) {
		    newGroups = new String[] {"NewGroup_LookupService_"+i};
		} else {
		    newGroups = new String[oldGroups.length];
		    for(int j=0;j<oldGroups.length;j++) {
			newGroups[j] = new String(oldGroups[j]+"_new");
		    }//end loop
		}//endif
	    }//endif
	    for(int j=0;j<newGroups.length;j++) {
		newGroupsList.add(newGroups[j]);
	    }//end loop
	}//end loop
	newGroupsToDiscover =(String[])(newGroupsList).toArray
	    (new String[newGroupsList.size()]);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup discovery utility to use group 
     *         discovery to discover the lookup services started during construct
     *    <li> starts the multicast discovery process by adding a discovery
     *         listener to the lookup discovery utility
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> invokes the setGroups method on the lookup discovery utility to
     *         re-configure that utility with a new set of groups to discover
     *    <li> verifies that the lookup discovery utility under test sends
     *         the expected discarded events
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        logger.log(Level.FINE,
                     "re-configure LookupDiscovery to discover -- ");
        for(int i=0;i<newGroupsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+newGroupsToDiscover[i]);
        }//end loop
        /* Reset the listener's expected discard state */
        mainListener.setLookupsToDiscover(oldLookupsToDiscover,
                                          newGroupsToDiscover);
        lookupDiscovery.setGroups(newGroupsToDiscover);
        waitForDiscard(mainListener);
    }//end run

}//end class SetGroupsReplaceSome

