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

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;

/**
 * With respect to the <code>addGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that invoking the <code>addGroups</code> method with an empty array
 * (<code>NO_GROUPS</code>) has no effect on the set of groups the
 * <code>LookupDiscovery</code> utility is currently configured to discover.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is initially configured to discover
 *         a finite set of groups (not NO_GROUPS and not ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then after
 * invoking the <code>addGroups</code> method with the empty array
 * (<code>NO_GROUPS</code>), there will be no change in the set of 
 * groups the lookup discovery utility is configured to discover.
 */
public class AddGroupsNoGroups extends AddGroupsAllGroups {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> retrieves the member groups the lookup discovery utility
     *         under test is initially configured to discover
     *    <li> invokes addGroups, inputting an empty array (NO_GROUPS)
     *    <li> verifies that the set of groups the lookup discovery utility
     *         was configured to discover prior to the call to addGroups
     *         is the same as the set of groups that utility is configured
     *         to discover after the call to addGroups
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        String[] oldGroups = newLD.getGroups();
        logger.log(Level.FINE, "current groups lookup "
                          +"discovery is configured to discover --");
        GroupsUtil.displayGroupSet(oldGroups,"  curGroup",
                                   Level.FINE);
        /* Add NO_GROUPS to the original set of groups to discover */
        logger.log(Level.FINE,
                          "invoking addGroups with NO_GROUPS ...");
        newLD.addGroups(DiscoveryGroupManagement.NO_GROUPS);
        String[] newGroups = newLD.getGroups();
        logger.log(Level.FINE, "added groups, lookup "
                          +"discovery is now configured to discover --");
        GroupsUtil.displayGroupSet(newGroups,"  newGroups",
                                   Level.FINE);
        if( GroupsUtil.compareGroupSets(oldGroups,newGroups, Level.OFF) ) {
            logger.log(Level.FINE, "old and new group sets "
                                            +"are equal");
            return;
        }//endif
        throw new TestException("old and new group sets NOT equal");
    }//end run

}//end class AddGroupsNoGroups

