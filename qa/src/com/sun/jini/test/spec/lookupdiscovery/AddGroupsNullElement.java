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
 * that invoking the <code>addGroups</code> method with an input array that
 * contains at least one <code>null</code> element will result in a
 * <code>NullPointerException</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> an instance of the lookup discovery utility initially configured to
 *         discover a finite set of groups (not NO_GROUPS and not ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then invoking
 * the <code>addGroups</code> method with an input array that contains
 * at least at least one <code>null</code> element will result in a
 * <code>NullPointerException</code>.
 */
public class AddGroupsNullElement extends ConstructorNullElement {

    protected String[] configGroups = DiscoveryGroupManagement.NO_GROUPS;
    protected String doStr  = "adding groups to lookup discovery --";
    protected String NPEStr = "NullPointerException on group addition "
	                       + "as expected";
    protected static final int DO_ADD    = 0;
    protected static final int DO_REMOVE = 1;
    protected static final int DO_SET    = 2;

    protected int doFlag = DO_ADD;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	configGroups = toGroupsArray(getInitLookupsToStart());
	logger.log(Level.FINE, "creating a new "
		   +"LookupDiscovery initially configured to "
		   +"discover -- ");
	GroupsUtil.displayGroupSet(configGroups,"  configGroups",
				   Level.FINE);
	newLD = new LookupDiscovery(configGroups,
				    sysConfig.getConfiguration());
	lookupDiscoveryList.add(newLD);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> creates an instance of the lookup discovery utility configured
     *         to discover a finite set of groups (not NO_GROUPS and not
     *         ALL_GROUPS)
     *    <li> attempts to augment the set of groups the lookup discovery
     *         utility is configured to discover with a set that contains
     *         at least one null element
     *    <li> verifies that a <code>NullPointerException</code> is thrown
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* First verify newLD is initially configured with expected groups */
        String[] newLDGroups = newLD.getGroups();
	if (!GroupsUtil.compareGroupSets(newLDGroups, 
					 configGroups, 
					 Level.FINE)) 
	{
	    throw new TestException("Group sets not equivalent");
	}
        /* Attempt to add a set of groups containing a null element */
        try {
            logger.log(Level.FINE,doStr);
            GroupsUtil.displayGroupSet(nullGroups,"  group",
                                       Level.FINE);
            switch(doFlag) {
                case DO_ADD:
                    newLD.addGroups(nullGroups);
                    break;
                case DO_REMOVE:
                    newLD.removeGroups(nullGroups);
                    break;
                case DO_SET:
                    newLD.setGroups(nullGroups);
                    break;
            }//end switch(doFlag)
        } catch(NullPointerException e) {
            logger.log(Level.FINE, NPEStr);
            return;
        }
        String errStr = new String("no NullPointerException");
        logger.log(Level.FINE,errStr);
        throw new TestException(errStr);
    }//end run

}//end class AddGroupsNullElement

