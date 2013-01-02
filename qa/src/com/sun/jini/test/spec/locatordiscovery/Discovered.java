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

package com.sun.jini.test.spec.locatordiscovery;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the lookup locator discovery utility can
 * successfully employ the unicast discovery protocol on behalf of a client
 * to discover the appropriate set of lookup services.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more "initial" lookup services, each started during construct,
 *        before the test begins execution
 *   <li> one client with one instance of the lookup locator discovery utility
 *   <li> the lookup locator discovery utility is configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive the expected discovery events, with the expected
 * contents.
 *
 */
public class Discovered extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> configures the lookup locator discovery utility to discover
     *         the set of locators whose elements are the locators of each
     *         lookup service that was started during construct
     *    <li> starts the unicast discovery process by adding a discovery
     *         listener to the lookup locator discovery utility
     *    <li> verifies that the lookup locator discovery utility under test
     *         sends the expected discovered events, having the expected 
     *         contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        doDiscovery(getInitLookupsToStart(),mainListener);
    }//end run

}//end class Discovered

