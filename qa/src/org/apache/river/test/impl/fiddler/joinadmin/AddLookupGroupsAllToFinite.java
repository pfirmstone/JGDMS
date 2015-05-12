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

import org.apache.river.qa.harness.TestException;
import net.jini.discovery.DiscoveryGroupManagement;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully add a new set of groups to the set of groups with which it
 * has been configured to join.
 * 
 * This test attempts to add a set of groups represented by 
 * <code>ALL_GROUPS</code> (<code>null</code>) to a finite set of groups.
 * 
 * In addition to verifying the capabilities of the service with respect
 * to group addition, this test also verifies that the <code>addGroups</code>
 * method of the <code>net.jini.discovery.DiscoveryGroupManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "If <code>null</code> is input, this method throws a
 * <code>NullPointerException</code>."
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
public class AddLookupGroupsAllToFinite extends AddLookupGroups {

    /** Constructs and returns the set of groups to add (overrides the
     *  parent class' version of this method)
     */
    String[] getTestGroupSet() {
        return DiscoveryGroupManagement.ALL_GROUPS;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds to the service's current set of groups
     *     a new set of groups to join 
     *  3. Because ALL_GROUPS is being added, a NullPointerException is
     *     expected (refer to the DiscoveryGroupManagement specification).
     *     Verify that the expected exception is thrown.
     * 
     *  (Note that this method overrides the version of this method in the
     *  super class.)
     */
    public void run() throws Exception {
        try {
	    super.run();
	    throw new TestException("expected NullPointerException not thrown");
	} catch (NullPointerException e) {
	    logger.log(Level.FINE, "NullPointerException thrown as expected");
        }
    }
}


