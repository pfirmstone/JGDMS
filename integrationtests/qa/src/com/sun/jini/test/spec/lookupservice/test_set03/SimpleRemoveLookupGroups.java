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
package com.sun.jini.test.spec.lookupservice.test_set03;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import java.rmi.RemoteException;
import java.io.IOException;
import java.util.ArrayList;
import net.jini.admin.JoinAdmin;

/** This class is used to perform a simple verification of the following
 *  methods from the Joindmin interface: getLookupGroups(), addLookupGroups()
 *  and removeLookupGroups(). This class first retrieves the initial set of 
 *  groups that the lookup service should join; then verifies that that 
 *  set contains the expected groups. A new set of groups to join is then
 *  added to the Registrar's set of groups; and all of its groups are
 *  again retrieved and verified against the expected set of groups. This
 *  class then removes each of the groups from the Registrar; as each set
 *  is removed it retrieves the new list from the Registrar and verifies
 *  that it contains the expected set of groups to join.
 *
 *  @see net.jini.admin.JoinAdmin
 *  @see com.sun.jini.test.spec.lookupservice.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class SimpleRemoveLookupGroups extends QATestRegistrar {

    private static final String REMOVE_GROUPS[][]
                   = { 
                       {"g00e00","g00e01","g00e02"},
                       {"g01e00","g01e01","g01e02","g01e03","g01e01","g01e03"},
                       {"g02e00","g02e01","g02e02","g02e03","g02e04"},
                       {"g03e00"}
                     };
    private JoinAdmin adminProxy;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the registrar admin proxy.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	adminProxy = (JoinAdmin) super.getAdminProxy();
    }

    /** Executes the current QA test.
     *
     *  Retrieves the current set of groups to join from the Registrar and
     *  then verifies that they are as expected. Adds each (even repeats) new
     *  group to join to the Registrar's set of groups. After each set of
     *  additions, retrieves the current set of groups from the Registrar and
     *  verifies that the set contains the expected group strings.  After all
     *  new lookup groups have been added to the Registrar, for each group
     *  added, removes one of the new groups, retrieves the resulting set of 
     *  lookup groups and verifies that the new set contains the expected
     *  set of group strings.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	String[] groups;
	ArrayList addedGroups = new ArrayList();
	/* retrieve the current lookup groups; verify they are as expected */
	groups = adminProxy.getLookupGroups();
	if ( !(QATestUtils.groupsAreEqual(groups,addedGroups)) ) {
	    throw new TestException("OLD Lookup Group (size "+groups.length+
				    ") != Expected OLD Lookup Group (empty)");
	}
	/* add each new lookup group to the Registrar's set of lookup groups */
	for(int i=0; i<REMOVE_GROUPS.length;i++) {
	    /* accumulate each element added to the set */
	    for(int j=0;j<REMOVE_GROUPS[i].length;j++) {
	        addedGroups.add(REMOVE_GROUPS[i][j]);
	    }
	    /* add the current lookup group to the Registrar's set */
	    adminProxy.addLookupGroups(REMOVE_GROUPS[i]);
	    /* retrieve the current lookup groups; verify they are as
	     * expected
	     */
	    groups = adminProxy.getLookupGroups();
	    if ( !(QATestUtils.groupsAreEqual(groups,addedGroups)) ) {
		throw new TestException("NEW Lookup Groups != "
					+ "Expected OLD Lookup Groups");
	    }
	}
	ArrayList expectedGroups = addedGroups;
	/* remove each new lookup group from the Registrar's set of groups */
	for(int i=0;i<REMOVE_GROUPS.length;i++) {
	    expectedGroups = QATestUtils.removeListFromArray(expectedGroups,
	                                                     REMOVE_GROUPS[i]);
	    /* remove the current lookup group from the Registrar's set */
	    adminProxy.removeLookupGroups(REMOVE_GROUPS[i]);
	    /* retrieve the current lookup groups; verify they are as
	     * expected
	     */
	    groups = adminProxy.getLookupGroups();
	    if ( !(QATestUtils.groupsAreEqual(groups,expectedGroups)) ) {
		throw new TestException("NEW Lookup Groups != "
					+ "Expected OLD LookupGroups");
	    }
	}
    }
}
