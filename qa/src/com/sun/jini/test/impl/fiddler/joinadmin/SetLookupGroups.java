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

package com.sun.jini.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.JoinAdminUtil;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryService;

import java.rmi.RemoteException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully replace the set of groups with which it has been configured
 * to join with a new set of groups.
 * 
 * This test attempts to replace a finite set of groups with a finite set of 
 * groups, where <i>finite</i> means:
 * <p>
 * 'not <code>net.jini.discovery.DiscoveryGroupManagement.ALL_GROUPS</code>'
 * 'not <code>net.jini.discovery.DiscoveryGroupManagement.NO_GROUPS</code>'.
 * <p>
 * In addition to verifying the capabilities of the service with respect to
 * group replacement, this test also verifies that the <code>setGroups</code>
 * method of the <code>net.jini.discovery.DiscoveryGroupManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "The <code>setGroups</code> method replaces all of the group names in the
 *  managed set with names from a new set".
 *
 *
 * @see <code>net.jini.discovery.DiscoveryGroupManagement</code> 
 */
public class SetLookupGroups extends AbstractBaseTest {
    String[] newGroupSet = null;
    private String[] expectedGroups = null;

    /** Constructs and returns the set of groups with which to replace
     *  the service's current set of groups (can be overridden by 
     *  sub-classes)
     */
    String[] getTestGroupSet() {
        return new String[]{"newGroup0","newGroup1"};
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the set
     *  of groups that should be expected after replacing the service's
     *  current group set with the new set of groups.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        newGroupSet = getTestGroupSet();
        expectedGroups = newGroupSet;
        if(expectedGroups != DiscoveryGroupManagement.ALL_GROUPS){
            if(expectedGroups.length == 0){
                logger.log(Level.FINE, "expectedGroups = NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet(expectedGroups,
                                           "expectedGroups",
					   Level.FINE);
            }
        } else {
            logger.log(Level.FINE, "expectedGroups = ALL_GROUPS");
        }
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, replaces the service's current set of groups
     *     with a new set of groups to join 
     *  3. Through the admin, retrieves the set of groups that the service
     *     is now configured to join.
     *  4. Determines if the set of groups retrieved through the admin is
     *     equivalent to the sum of the service's original set of groups 
     *     and the new set of groups
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				  + serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);
	String[] oldGroups = joinAdmin.getLookupGroups();
	GroupsUtil.displayGroupSet(oldGroups, "oldGroups", Level.FINE);
	GroupsUtil.displayGroupSet(newGroupSet, "setGroups", Level.FINE);
	joinAdmin.setLookupGroups(newGroupSet);
	String[] newGroups = joinAdmin.getLookupGroups();
	GroupsUtil.displayGroupSet(newGroups, "newGroups", Level.FINE);
	if (!GroupsUtil.compareGroupSets(expectedGroups,newGroups,Level.FINE)) {
	    throw new TestException("Group sets are not equivalent");
	}
    }
}


