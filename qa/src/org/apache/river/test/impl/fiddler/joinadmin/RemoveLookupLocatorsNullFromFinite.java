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
import net.jini.core.discovery.LookupLocator;
import java.net.MalformedURLException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully remove a set of locators from the set of locators with 
 * which it has been configured to join.
 *
 * This test attempts to remove a null locator set from the non-empty set
 * of locators with which the service is currently configured.
 *
 * In addition to verifying the capabilities of the service with respect
 * to locator removal, this test also verifies that the 
 * <code>removeLocators</code> method of the interface
 * <code>net.jini.discovery.DiscoveryLocatorManagement</code> functions
 * as specified. That is, 
 * <p>
 * "If <code>null</code> is input to <code>removeLocators</code>, a
 *  <code>NullPointerException</code> will be thrown."
 * 
 * @see <code>net.jini.discovery.DiscoveryLocatorManagement</code> 
 */
public class RemoveLookupLocatorsNullFromFinite extends RemoveLookupLocators {

    /** Constructs and returns the set of locators to remove  (overrides
     *  the parent class' version of this method)
     */
    LookupLocator[] getTestLocatorSet() throws MalformedURLException {
        return null;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, attempts to remove a <code>null</code> set
     *     of locators from the service's current set of locators
     *  3. Because <code>null</code> is being removed, a 
     *     <code>NullPointerException</code> is expected (refer to the
     *     DiscoveryLocatorManagement specification). Verify that the
     *     expected exception is thrown.
     */
    public void run() throws Exception {
        try {
	    super.run();
	    throw new TestException("Expected NullPointerException not thrown");
	} catch (NullPointerException e) {
	    logger.log(Level.FINE, "NullPointerException thrown as expected");
        }
    }
}


