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

import com.sun.jini.test.share.LocatorsUtil;

import net.jini.core.discovery.LookupLocator;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular, this
 * class verifies that "the method <code>getDiscoveredLocators</code> returns
 * an array of <code>LookupLocator</code> objects in which each element
 * corresponds to a lookup service - from the set of desired lookup
 * services - that has already been discovered."
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services
 *    <li> one client with one instance of the lookup locator discovery utility
 *    <li> the lookup locator discovery utility is configured to discover the
 *         set of locators whose elements are the locators of each lookup
 *         service that was started
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * <code>getDiscoveredLocators</code> method will return an array of 
 * <code>LookupLocator</code> instances whose elements are equal to the
 * locators of the lookup services about whose discovery the listener has
 * been notified.
 *
 */
public class GetDiscoveredLocators extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies that the listener is notified of the discovery of
     *         all of the lookup services started during setup
     *    <li> calls getDiscoveredLocators on the lookup locator discovery
     *         utility used in the discovery process
     *    <li> retrieves from the listener, the locators that were actually
     *         discovered
     *    <li> compares the locators actually discovered with the locators
     *         returned by getDiscoveredLocators, and verifies that the
     *         contents of those arrays are the same
     * </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "retrieving locators from "
                                        +"LookupLocatorDiscovery ...");
        LookupLocator[] lldLocs = locatorDiscovery.getDiscoveredLocators();
        logger.log(Level.FINE, "retrieving locators that were "
                                        +"actually discovered ...");
        LookupLocator[] listenerLocs = mainListener.getDiscoveredLocators();
        logger.log(Level.FINE, "verifying locators from "
                          +"getDiscoveredLocators() equal locators that "
                          +"were acutally discovered ...");
	if (!LocatorsUtil.compareLocatorSets(lldLocs, listenerLocs,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
    }//end run

}//end class GetDiscoveredLocators

