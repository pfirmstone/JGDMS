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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

/**
 * With respect to the <code>removeGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that invoking the <code>removeGroups</code> method with a set of groups
 * containing none of the groups with which the <code>LookupDiscovery</code>
 * utility is currently configured to discover, <code>removeGroups</code>
 * takes no action.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of the lookup discovery utility
 *   <li> the lookup discovery utility is initially configured to discover
 *        a finite set of groups (not NO_GROUPS and not ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then after
 * invoking the <code>removeGroups</code> method with a set of groups
 * containing none of the groups with which the lookup discovery utility
 * is currently configured, there is no change in the set of groups that
 * utility is configured to discover.
 */
public class RemoveGroupsRemoveNone extends RemoveGroupsAllGroups {

    private String[] initGroups = DiscoveryGroupManagement.NO_GROUPS;
    private String[] groupsToRemove
                           = new String[] {"RemoveGroupsRemoveNoneTestGroup"};

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	initGroups = newLD.getGroups();
	if(    (initGroups != DiscoveryGroupManagement.ALL_GROUPS)
	       && (initGroups.length != 0) )
        {
	    groupsToRemove = new String[initGroups.length];
	    for(int i=0;i<initGroups.length;i++) {
		groupsToRemove[i] = new String(initGroups[i]+"_new");
	    }//end loop
	}//endif
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> invokes removeGroups, inputting a finite set of groups that
     *         contains none of the groups with which the lookup discovery
     *         utility is currently configured
     *    <li> verifies that the lookup discovery utility is configured to
     *         discover the same set of groups it was initially configured
     *         to discover
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        logger.log(Level.FINE, "initial groups lookup "
                          +"discovery is configured to discover --");
        GroupsUtil.displayGroupSet(initGroups,"  initGroups",
                                   Level.FINE);
        /* Remove groupsToRemove from original set of groups to discover */
        logger.log(Level.FINE, "invoking removeGroups to "
                          +"remove from the groups to discover --");
        GroupsUtil.displayGroupSet(groupsToRemove,"  groupsToRemove",
                                   Level.FINE);
        newLD.removeGroups(groupsToRemove);


        String[] newGroups = newLD.getGroups();
        logger.log(Level.FINE, "removed groups, lookup "
                          +"discovery is now configured to discover --");
        GroupsUtil.displayGroupSet(newGroups,"  newGroups",
                                   Level.FINE);
        if( GroupsUtil.compareGroupSets(initGroups,newGroups, Level.OFF) ) {
            logger.log(Level.FINE, "no change in group "
                              +"configuration -- group sets before and "
                              +"after call to removeGroups are equal");
            return;
        }//endif
        throw new TestException("old and new group sets NOT equal");
    }//end run

}//end class RemoveGroupsRemoveNone

