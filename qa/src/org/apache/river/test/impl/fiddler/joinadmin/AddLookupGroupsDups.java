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

import org.apache.river.test.share.GroupsUtil;
import org.apache.river.qa.harness.AbstractServiceAdmin;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully add a new set of groups to the set of groups with which it
 * has been configured to join.
 * 
 * This test attempts to add a finite set of groups containing elements
 * that duplicate at least one element of the initial set groups with
 * which the service is configured.
 *
 * In addition to verifying the capabilities of the service with respect
 * to group addition, this test also verifies that the <code>addGroups</code>
 * method of the <code>net.jini.discovery.DiscoveryGroupManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "Elements in the input set that duplicate elements already in the managed
 *  set will be ignored."
 * <p>
 * Note that this test class is a sub-class of the <code>AddLookupGroups</code>
 * class. That parent class performs almost all of the processing for this
 * test. This is because the only difference between this test and the test
 * being performed by the parent class is in the contents of the set of
 * groups with which the lookup discovery service under test is configured.
 * Rather than running one test multiple times, editing a single shared
 * configuration file for each run, running separate tests that perform
 * identical functions but which are associated with different configuration
 * files allows for efficient batching of the test runs. Thus, providing
 * one parent test class from which all other related tests are sub-classed
 * provides for efficient code re-use.
 * 
 * @see <code>org.apache.river.test.impl.fiddler.joinadmin.AddLookupGroups</code> 
 *
 * @see <code>net.jini.discovery.DiscoveryGroupManagement</code> 
 */
public class AddLookupGroupsDups extends AddLookupGroups {

    /** Constructs and returns the set of groups to add (overrides the
     *  parent class' version of this method)
     */
    String[] getTestGroupSet() {
	AbstractServiceAdmin admin = 
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        return GroupsUtil.getGroupsWithDups(admin.getGroups());
    }
}


