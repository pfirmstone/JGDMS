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
package org.apache.river.test.spec.lookupservice.test_set03;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;
import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.test.spec.lookupservice.QATestUtils;
import java.rmi.RemoteException;
import java.io.IOException;
import java.util.ArrayList;
import net.jini.lookup.DiscoveryAdmin;

/** This class is used to perform a simple verification of the following
 *  methods from the DiscoveryAdmin interface: getMemberGroups() and
 *  addMemberGroups() and removeMemberGroups(). This class adds a number 
 *  of group member strings to the current set of group members maintained 
 *  by the Registrar and then retrieves the new list to verfiy that the 
 *  original set of group members was indeed modified in the Registrar.
 *  This class then removes each set of member groups; and as each set is
 *  removed, it retrieves the new list to verify that it contains the
 *  expected set of member groups.
 *
 *  @see net.jini.lookup.DiscoveryAdmin
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class SimpleRemoveMemberGroups extends QATestRegistrar {

    private static final String REMOVE_GROUPS[][]
                   = { 
                       {"g00e00","g00e01","g00e02"},
                       {"g01e00","g01e01","g01e02","g01e03","g01e01","g01e03"},
                       {"g02e00","g02e01","g02e02","g02e03","g02e04"},
                       {"g03e00"}
                     };
    private DiscoveryAdmin adminProxy;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the DiscoveryAdmin interface.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	adminProxy = (DiscoveryAdmin) super.getAdminProxy();
        return this;
    }

    /** Executes the current QA test.
     *
     *  Retrieves the current member groups and then verifies that they are
     *  as expected. Adds each (even repeats) new member group to the
     *  Registrar's set of member groups. After all new member groups have
     *  been added, for each member group added, removes one of those new
     *  groups, retrieves the new set of member groups and verifies that 
     *  the new set contains the expected member group strings.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	String[] groups;
	ArrayList addedGroups = new ArrayList();

	/* retrieve the current member groups; verify they are as expected */
	groups = adminProxy.getMemberGroups();
	if ( !(QATestUtils.groupsAreEqual(groups,addedGroups)) ) {
	    throw new TestException("OLD Member Group (size "+groups.length+
				    ") != Expected OLD MemberGroup (empty)");
	}
	/* add each new member group to the Registrar's set of member groups */
	for(int i=0; i<REMOVE_GROUPS.length;i++) {
	    /* accumulate each element added to the set */
	    for(int j=0;j<REMOVE_GROUPS[i].length;j++) {
	        addedGroups.add(REMOVE_GROUPS[i][j]);
	    }
	    /* add the current member group to the Registrar's set */
	    adminProxy.addMemberGroups(REMOVE_GROUPS[i]);
	    /* retrieve the current member groups; verify they are as
	     * expected
	     */
	    groups = adminProxy.getMemberGroups();
	    if ( !(QATestUtils.groupsAreEqual(groups,addedGroups)) ) {
		throw new TestException("NEW Member Groups != "
					+ "Expected OLD MemberGroups");
	    }
	}
	ArrayList expectedGroups = addedGroups;
	/* remove each new member group from the Registrar's set of groups */
	for(int i=0;i<REMOVE_GROUPS.length;i++) {
	    expectedGroups = QATestUtils.removeListFromArray(expectedGroups,
	                                                     REMOVE_GROUPS[i]);
	    /* remove the current member group from the Registrar's set */
	    adminProxy.removeMemberGroups(REMOVE_GROUPS[i]);
	    /* retrieve the current member groups; verify they are as
	     * expected
	     */
	    groups = adminProxy.getMemberGroups();
	    if ( !(QATestUtils.groupsAreEqual(groups,expectedGroups)) ) {
		throw new TestException("NEW Member Groups != "
					+ "Expected OLD MemberGroups");
	    }
	}
    }
}
