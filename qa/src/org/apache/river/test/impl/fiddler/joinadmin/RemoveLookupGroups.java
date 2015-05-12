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

package org.apache.river.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import org.apache.river.test.spec.discoveryservice.AbstractBaseTest;

import org.apache.river.test.share.GroupsUtil;
import org.apache.river.test.share.JoinAdminUtil;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryService;

import java.rmi.RemoteException;
import java.util.ArrayList;
import org.apache.river.qa.harness.AbstractServiceAdmin;
import org.apache.river.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully remove a set of groups from the set of groups with which
 * it has been configured to join.
 * 
 * This test attempts to remove a finite set of groups from a finite set of 
 * groups, where <i>finite</i> means:
 * <p>
 * 'not <code>net.jini.discovery.DiscoveryGroupManagement.ALL_GROUPS</code>'
 * 'not <code>net.jini.discovery.DiscoveryGroupManagement.NO_GROUPS</code>'.
 * <p>
 * In addition to verifying the capabilities of the service with respect to
 * group addition, this test also verifies that the <code>removeGroups</code>
 * method of the <code>net.jini.discovery.DiscoveryGroupManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "The <code>removeGroups</code> method deletes a set of group names from
 *  the managed set of groups."
 *
 *
 * @see <code>net.jini.discovery.DiscoveryGroupManagement</code> 
 */
public class RemoveLookupGroups extends AbstractBaseTest {

    String[] removeGroupSet = null;
    private String[] expectedGroups = null;

    /** Constructs and returns the set of groups to remove (can be
     *  overridden by sub-classes)
     */
    String[] getTestGroupSet() {	
        AbstractServiceAdmin admin =
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        return GroupsUtil.getSubset(admin.getGroups());
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the set
     *  of groups that should be expected after removing a set of groups.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        removeGroupSet = getTestGroupSet();
        AbstractServiceAdmin admin =
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        if (admin == null) {
            return this;
        }
        String[] configGroups = admin.getGroups();

        /* Construct the set of groups expected after removal by selecting
         * each element from the set of groups with which the service is
         * currently configured that were not selected for removal.
         */
        if(configGroups == DiscoveryGroupManagement.ALL_GROUPS){
            logger.log(Level.FINE, 
		       "expectedGroups = UnsupportedOperationException");
        } else {//configGroups != DiscoveryGroupManagement.ALL_GROUPS
            if(removeGroupSet == DiscoveryGroupManagement.ALL_GROUPS) {
                logger.log(Level.FINE, "expectedGroups = NullPointerException");
            } else {//removeGroupSet & configGroups != ALL_GROUPS
                ArrayList eList = new ArrayList();
                iLoop: 
                for(int i=0;i<configGroups.length;i++) {
                    for(int j=0;j<removeGroupSet.length;j++) {
                        if(configGroups[i].equals(removeGroupSet[j])) {
                            continue iLoop;
                        }
                    }
                    eList.add(configGroups[i]);
                }
                expectedGroups =
                        (String[])(eList).toArray(new String[eList.size()]);
                GroupsUtil.displayGroupSet(expectedGroups,
                                           "expectedGroups",
					   Level.FINE);
            }
        }
        return this;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, removes from the service's current set of groups
     *     the indicated set of groups to join 
     *  3. Through the admin, retrieves the set of groups that the service
     *     is now configured to join.
     *  4. Determines if the set of groups retrieved through the admin is
     *     equivalent to the expected set of groups
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
	GroupsUtil.displayGroupSet(removeGroupSet, "removeGroups", Level.FINE);
	joinAdmin.removeLookupGroups(removeGroupSet);
	String[] newGroups = joinAdmin.getLookupGroups();
	GroupsUtil.displayGroupSet(newGroups, "newGroups", Level.FINE);
	if (!GroupsUtil.compareGroupSets(expectedGroups,newGroups,Level.FINE)) {
	    throw new TestException("Group sets are not equivalent");
	}
    }
}


