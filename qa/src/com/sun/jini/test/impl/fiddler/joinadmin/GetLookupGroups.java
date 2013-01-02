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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryService;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.AbstractServiceAdmin;
import com.sun.jini.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully return the groups with which it has been configured to join.
 *
 * This test attempts to retrieve a finite set of groups, where
 * <i>finite</i> means:
 * <p>
 * 'not <code>net.jini.discovery.DiscoveryGroupManagement.ALL_GROUPS</code>'
 * 'not <code>net.jini.discovery.DiscoveryGroupManagement.NO_GROUPS</code>'.
 * <p>
 * In addition to verifying the capabilities of the service with respect
 * to group addition, this test also verifies that the <code>getGroups</code>
 * method of the <code>net.jini.discovery.DiscoveryGroupManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "The <code>getGroups</code> method returns an array consisting of the
 *  names of the groups in the managed set (of groups)."
 *
 *
 * @see <code>net.jini.discovery.DiscoveryGroupManagement</code> 
 */
public class GetLookupGroups extends AbstractBaseTest {

    private String[] expectedGroups = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then retrieves from the
     *  tests's configuration property file, the set of groups whose members
     *  are the lookup service(s) that the lookup discovery service is
     *  expected to attempt to join.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
	AbstractServiceAdmin admin = 
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        if (admin == null) {
            return this;
        }
        expectedGroups = admin.getGroups();
        if(expectedGroups != DiscoveryGroupManagement.ALL_GROUPS){
            if(expectedGroups == DiscoveryGroupManagement.NO_GROUPS){
                logger.log(Level.FINE, "expectedGroups = NO_GROUPS");
            } else if(expectedGroups.length == 0) {
                logger.log(Level.FINE, 
			   "expectedGroups.length = 0, "
			 + "but expectedGroups != NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet(expectedGroups,
                                           "expectedGroups",
					   Level.FINE);
            }
        } else {
            logger.log(Level.FINE, "expectedGroups = ALL_GROUPS");
        }
        return this;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, retrieves the set of groups that the service
     *     is currently configured to join.
     *  3. Determines if the set of groups retrieved through the admin is
     *     equivalent to the set of groups with which the service was
     *     configured when it was started.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				  + serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);
	String[] curGroups = joinAdmin.getLookupGroups();
	GroupsUtil.displayGroupSet(curGroups, "curGroups", Level.FINE);
	if (!GroupsUtil.compareGroupSets(expectedGroups,curGroups,Level.FINE)) {
	    throw new TestException("Group sets are not equivalent");
	}
    }
}
