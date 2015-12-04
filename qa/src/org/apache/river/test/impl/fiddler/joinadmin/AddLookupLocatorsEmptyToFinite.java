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

import net.jini.core.discovery.LookupLocator;
import java.net.MalformedURLException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully add a new set of locators to the set of locators with 
 * which it has been configured to join.
 *
 * This test attempts to add an empty set of locators to the 
 * non-<code>null</code> set of locators with which the service is
 * currently configured.
 *
 * In addition to verifying the capabilities of the service with respect to
 * locator addition, this test also verifies that the <code>addLocators</code>
 * method of the <code>net.jini.discovery.DiscoveryLocatorManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "If the empty array is input, the managed set of locators will not change".
 *
 * Note that this test class is a sub-class of the test class
 * <code>AddLookupLocators</code>. That parent class performs almost
 * all of the processing for this test. This is because the only
 * difference between this test and the test being performed by the
 * parent class is in the contents of the set of locators with which
 * the lookup discovery service under test is configured. Rather than
 * running one test multiple times, editing a single shared configuration
 * file for each run, running separate tests that perform identical
 * functions but which are associated with different configuration
 * files allows for efficient batching of the test runs. Thus, providing
 * one parent test class from which all other related tests are sub-classed
 * provides for efficient code re-use.
 * 
 * @see <code>org.apache.river.test.impl.fiddler.joinadmin.AddLookupLocators</code> 
 */
public class AddLookupLocatorsEmptyToFinite extends AddLookupLocators {

    /** Constructs and returns the set of locators to add (overrides the
     *  parent class' version of this method)
     */
    LookupLocator[] getTestLocatorSet() throws MalformedURLException {
        return new LookupLocator[0];
    }
}


