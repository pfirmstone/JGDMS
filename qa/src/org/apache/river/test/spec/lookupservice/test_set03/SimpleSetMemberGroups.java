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
 *  setMemberGroups(). This class repeatedly sets (replaces) the current
 *  set of group member strings with a different set of group members and
 *  then retrieves the new set to verfiy that the original set of group 
 *  members was indeed modified in the Registrar.
 *
 *  @see net.jini.lookup.DiscoveryAdmin
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class SimpleSetMemberGroups extends QATestRegistrar {

    private static final String SET_GROUPS[][]
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
     *  as expected. Then for each element of the set of member groups,
     *  replaces (sets) the current set of member groups with the new
     *  set of member groups. After each set replacement, retrieves the 
     *  current set of member groups from the Registrar and verifies that 
     *  the set contains the expected member group strings.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	String[] groups;
	/* retrieve the current member groups; verify they are as expected */
	ArrayList expectedGroups = new ArrayList();

	groups = adminProxy.getMemberGroups();
	if ( !(QATestUtils.groupsAreEqual(groups,expectedGroups)) ) {
	    throw new TestException("OLD Member Group (size "+groups.length+
				    ") != Expected OLD MemberGroup (empty)");
	}
	/* loop through the set of member groups to add to the Registrar's
	 * set of member groups; add each element
	 */
	for(int i=0; i<SET_GROUPS.length;i++) {
	    /* accumulate each element added to the set */
	    expectedGroups = new ArrayList();
	    for(int j=0;j<SET_GROUPS[i].length;j++) {
	        expectedGroups.add(SET_GROUPS[i][j]);
	    }
	    /* replace the current member groups with the new set */
	    adminProxy.setMemberGroups(SET_GROUPS[i]);
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
