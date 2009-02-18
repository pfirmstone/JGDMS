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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification.  In particular, this
 * class verifies that "the method <code>getUndiscoveredLocators</code> returns
 * an array of <code>LookupLocator</code> objects in which each element
 * corresponds to a lookup service - from the set of desired lookup
 * services - that has not yet been discovered."
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more initial lookup services with explicitly configured
 *         port numbers that are actually started during setup
 *    <li> one or more addtional lookup services with explicitly configured
 *         port numbers that are never actually started during the test
 *    <li> one client with one instance of the lookup locator discovery utility
 *    <li> the lookup locator discovery utility is configured to discover the
 *         set of locators whose elements are the locators of the initial
 *         lookup services and the addtional lookup services
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * <code>getUndiscoveredLocators</code> method will return an array of 
 * <code>LookupLocator</code> instances whose elements are equal to the
 * locators of the additional lookup services that are not started (and thus,
 * not discovered).
 *
 */
public class GetUndiscoveredLocators extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> configures the lookup locator discovery utility to discover
     *         only the initial lookup services started during setup, and
     *         verifies that the listener is notified of the discovery of
     *         those lookup services (this establishes the set of
     *         already-discovered locators in the listener)
     *    <li> re-configures the lookup locator discovery utility to discover
     *         both the initial and the addtional lookup services (this
     *         establishes in the lookup locator discovery utility, a
     *         non-empty set of un-discovered locators)
     *    <li> re-configures the listener to expect both the initial lookups
     *         and the addtional lookups
     *    <li> calls getUndiscoveredLocators on the lookup locator discovery
     *         utility used in the discovery process
     *    <li> retrieves from the listener, the expected, but undiscovered,
     *         locators
     *    <li> compares the expected-but-undiscovered locators referenced
     *         by the listener with the locators returned by
     *         getUndiscoveredLocators, and verifies that the contents
     *         of those arrays are the same
     * </ul>
     */
    public void run() throws Exception {
        /* Establish the set of already-discovered locators */
        super.run();

        /* Re-configure the LLD for both the initial and the additional
         * lookups so it will have a set of discovered locators and a set
         * of un-discovered locators.
         */
        locatorDiscovery.setLocators(toLocatorArray(allLookupsToStart));
        /* Establish the full set of locators expected to be discovered */
        mainListener.setLookupsToDiscover(allLookupsToStart);

        logger.log(Level.FINE, "retrieving un-discovered locators "
                          +"from LookupLocatorDiscovery ...");
        LookupLocator[] lldLocs
                             = locatorDiscovery.getUndiscoveredLocators();
        LocatorsUtil.displayLocatorSet(lldLocs,
                                       "From LLD --      locator",
                                       Level.FINE);
        logger.log(Level.FINE, "retrieving un-discovered locators "
                          +"expected to be discovered ...");
        LookupLocator[] listenerLocs
                                  = mainListener.getUndiscoveredLocators();
        LocatorsUtil.displayLocatorSet(listenerLocs,
                                       "From listener -- locator",
                                       Level.FINE);
        logger.log(Level.FINE, "verifying locators from "
                      +"getUndiscoveredLocators() equal un-discovered "
                      +"locators expected to be discovered ...");
	if (!LocatorsUtil.compareLocatorSets(lldLocs, listenerLocs,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
    }//end run

}//end class GetUndiscoveredLocators

