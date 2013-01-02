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
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

/**
 * With respect to the <code>removeGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that if the <code>removeGroups</code> method is invoked while the
 * <code>LookupDiscovery</code> utility is currently configured to 
 * discover lookup services belonging to any group (that is, it is configured
 * to discover <code>ALL_GROUPS</code>), then an
 * <code>UnsupportedOperationException</code> occurs.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is initially configured to discover
 *         all lookup services within the multicast radius that belong to
 *         to any group -- even NO_GROUPS (that is, the lookup discovery
 *         utility is initially configured to discover ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then upon
 * invoking the <code>removeGroups</code> method with an empty array or
 * a non-<code>null</code>, non-empty array, an
 * <code>UnsupportedOperationException</code> will occur.
 */
public class RemoveGroupsUnsupportedOperation extends Discovered {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        delayLookupStart = true;//don't start lookups, just want config info
	super.construct(sysConfig);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> creates a lookup discovery utility configured to discover
     *         ALL_GROUPS
     *    <li> invokes removeGroups, inputting a non-null, non-empty set of
     *         groups
     *    <li> verifies that an <code>UnsupportedOperationException</code>
     *         occurs
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        try {
            LookupDiscovery newLD = new LookupDiscovery
                                        (DiscoveryGroupManagement.ALL_GROUPS,
					 getConfig().getConfiguration());
            lookupDiscoveryList.add(newLD);
            String[] oldGroups = newLD.getGroups();
            logger.log(Level.FINE, "current groups lookup "
                              +"discovery is configured to discover --");
            GroupsUtil.displayGroupSet(oldGroups,"  curGroup",
                                       Level.FINE);
            /* Attempt to remove a finite set of groups to the original set */
            logger.log(Level.FINE, "invoking removeGroups to "
                              +"remove from the groups to discover --");
            GroupsUtil.displayGroupSet(groupsToDiscover,"  newGroups",
                                       Level.FINE);
            newLD.removeGroups(groupsToDiscover);
        } catch(UnsupportedOperationException e) {
            logger.log(Level.FINE, "UnsupportedOperationException "
                              +"on attempt to remove groups from ALL_GROUPS, "
                              +"as expected");
            return;
        }
        String errStr = new String("no UnsupportedOperationException");
        logger.log(Level.FINE, errStr);
        throw new TestException(errStr);
    }//end run

}//end class RemoveGroupsUnsupportedOperation

