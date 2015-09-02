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

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.GroupsUtil;

import net.jini.discovery.LookupDiscovery;

/**
 * With respect to the <code>getGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that the <code>getGroups</code> method returns a <code>String</code>
 * array in which each element of the array equals the name of a member
 * group containing the lookup services the lookup discovery utility is
 * currently configured to discover.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is initially configured to discover
 *         a finite set of groups (not NO_GROUPS and not ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then invoking
 * the <code>getGroups</code> method should return the member groups
 * with which the lookup discovery utility is currently configured to
 * discover.
 */
public class GetGroups extends Discovered {

    protected LookupDiscovery newLD = null;

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
     *    <li> retrieves the member groups the lookup discovery utility
     *         under test is initially configured to discover
     *    <li> verifies that the set of groups the lookup discovery utility
     *         was configured to discover initially is the same as the set
     *         of groups returned by getGroups
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        logger.log(Level.FINE, "creating a new "
                          +"LookupDiscovery initially configured to "
                          +"discover -- ");
        GroupsUtil.displayGroupSet(groupsToDiscover,"  initGroup",
                                   Level.FINE);
        newLD = new LookupDiscovery(groupsToDiscover,
				    getConfig().getConfiguration());
        lookupDiscoveryList.add(newLD);


        String[] curGroups = newLD.getGroups();
        logger.log(Level.FINE,
		   "groups returned by getGroups --");
        GroupsUtil.displayGroupSet(curGroups,"  group",
                                   Level.FINE);
        if( GroupsUtil.compareGroupSets(curGroups,
					groupsToDiscover, 
					Level.OFF) ) 
	{
            logger.log(Level.FINE, 
		       "initial and retrieved group sets are equal");
            return;
        }//endif
        throw new TestException("initial and retrieved group sets NOT equal");
    }//end run

}//end class GetGroups

