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
 * With respect to the <code>addGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that invoking the <code>addGroups</code> method with a <code>null</code>
 * (<code>ALL_GROUPS</code>) parameter will result in a
 * <code>NullPointerException</code>.
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
 * invoking the <code>addGroups</code> method with a <code>null</code>
 * parameter (<code>ALL_GROUPS</code>), a <code>NullPointerException</code>
 * is thrown.
 */
public class AddGroupsAllGroups extends Discovered {

    protected LookupDiscovery newLD = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        delayLookupStart = true;//don't start lookups, just want config info
	super.construct(sysConfig);
	newLD = new LookupDiscovery(groupsToDiscover,
				    sysConfig.getConfiguration());
	lookupDiscoveryList.add(newLD);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> invokes addGroups, inputting null (ALL_GROUPS)
     *    <li> verifies that a <code>NullPointerException</code> is thrown
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        try {
            String[] curGroups = newLD.getGroups();
            logger.log(Level.FINE, "current groups lookup "
                              +"discovery is configured to discover --");
            GroupsUtil.displayGroupSet(curGroups,"  curGroup",
                                       Level.FINE);
            /* Add ALL_GROUPS to the original set of groups to discover */
            logger.log(Level.FINE,
                              "invoking addGroups with ALL_GROUPS ...");
            newLD.addGroups(DiscoveryGroupManagement.ALL_GROUPS);
	    throw new TestException("no NullPointerException");
        } catch(NullPointerException e) {
            logger.log(Level.FINE, "NullPointerException on "
                              +"null input to LookupDiscovery.addGroups as "
                              +"expected");
        }
    }//end run

}//end class AddGroupsAllGroups

