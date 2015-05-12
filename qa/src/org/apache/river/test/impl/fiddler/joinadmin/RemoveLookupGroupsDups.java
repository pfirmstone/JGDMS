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
import net.jini.discovery.DiscoveryGroupManagement;
import org.apache.river.qa.harness.AbstractServiceAdmin;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully remove a set of groups from the set of groups with which
 * it has been configured to join.
 * 
 * This test attempts to remove from the finite set of groups with which
 * the service is configured, a finite set of groups containing elements
 * that are unique
 * 
 * Note that this test class is a sub-class of <code>RemoveLookupGroups</code>.
 * That parent class performs almost all of the processing for this
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
 * @see <code>org.apache.river.test.impl.fiddler.joinadmin.RemoveLookupGroups</code> 
 *
 * @see <code>net.jini.discovery.DiscoveryGroupManagement</code> 
 */
public class RemoveLookupGroupsDups extends RemoveLookupGroups {

    /** Constructs and returns the set of groups to remove (overrides
     *  the parent class' version of this method)
     */
    String[] getTestGroupSet() {
        /* First retrieve a sub-set of the initial groups */
	AbstractServiceAdmin admin = 
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        String[] subsetCurGroups = GroupsUtil.getSubset(admin.getGroups());
        /* Next, use the above sub-set to construct a set with duplicates */
        return GroupsUtil.getGroupsWithDups(subsetCurGroups);
    }
}
