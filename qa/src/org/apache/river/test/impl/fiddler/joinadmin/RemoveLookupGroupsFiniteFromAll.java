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

/**
 * This class determines whether or not the lookup discovery service can
 * successfully remove a set of groups from the set of groups with which
 * it has been configured to join.
 * 
 * This test attempts to remove a finite set of groups from the set of
 * groups with which the service is currently configured to join, where
 * the service's current set of groups is represented by 
 * <code>ALL_GROUPS</code> (<code>null</code>).
 * 
 * In addition to verifying the capabilities of the service with respect to
 * group removal, this test also verifies that the <code>removeGroups</code>
 * method of the <code>net.jini.discovery.DiscoveryGroupManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "This method throws an <code>UnsupportedOperationException</code> if
 * there is no managed set of groups from which to remove elements."
 * <p>
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
public class RemoveLookupGroupsFiniteFromAll extends RemoveLookupGroups {

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, attempts to remove from the service's current
     *     set of groups the indicated set of groups to join 
     *  3. Because there is no set of groups with which the service is
     *     currently configured (that is, the service is configured for
     *     <code>ALL_GROUPS</code>), an UnsupportedOperationException is
     *     expected (refer to the DiscoveryGroupManagement specification).
     *     Verify that the expected exception is thrown.
     * 
     *  (Note that this method overrides the version of this method in the
     *  super class.)
     */
    public void run() throws Exception {
        try {
	    super.run();
	    throw new TestException("Expected UnsupportedOperationException "
				  + "not thrown");
	} catch (UnsupportedOperationException e) {
	    logger.log(Level.FINE, 
		       "UnsupportedOperationException thrown as expected");
        }
    }
}


