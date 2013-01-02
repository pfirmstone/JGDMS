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
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.core.discovery.LookupLocator;

import java.util.Iterator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the lookup locator discovery utility can
 * successfully employ the unicast discovery protocol on behalf of a client
 * to discover both lookup services that already exist when the utility
 * is constructed, and lookup services that are started after the utility is
 * created.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more "initial" lookup services, each started during construct,
 *        before the test begins execution
 *   <li> one or more "additional" lookup services, each started after the
 *        test has begun execution, and after the initial lookup services
 *        started during construct have been discovered
 *   <li> one instance of the lookup locator discovery utility
 *   <li> the lookup locator discovery utility is configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that is to be started
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive the expected discovery events, with the expected
 * contents.
 *
 */
public class DiscoveredDelay extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> configures the lookup locator discovery utility to discover
     *         the set of locators whose elements are the locators of each
     *         lookup service that was started during construct
     *    <li> starts the unicast discovery process by adding a discovery
     *         listener to the lookup locator discovery utility
     *    <li> verifies that the lookup locator discovery utility under test
     *         sends the expected discovered events, having the expected 
     *         contents related to the initial lookups that were started
     *    <li> starts an additional set of lookups to discover
     *    <li> resets the listener's discovery and discard related state for
     *         for discovery of the addtional lookups just started
     *    <li> verifies that the lookup locator discovery utility under test
     *         sends the expected discovered events, having the expected 
     *         contents related to the additional lookups that were started
     * </ul>
     */
    public void run() throws Exception {
        /* Verify discovery of the initial lookups */
        super.run();

        /* Configure the listener's expected lookups for all the lookups */
        mainListener.setLookupsToDiscover(getAllLookupsToStart());
        /* Configure the lookup locator discovery utility to discover both
         * the initial and the additional lookups
         */
        LookupLocator[] addLocs = toLocatorArray(getAddLookupsToStart());
        locatorDiscovery.addLocators(addLocs);
        logger.log(Level.FINE, "added additional locators to "
                          +"lookup locator discovery --");
        LocatorsUtil.displayLocatorSet(addLocs,"locator",Level.FINE);
        /* Start the additional lookup services */
        startAddLookups();
        /* Verify discovery of the delayed lookups */
        waitForDiscovery(mainListener);
    }//end run

}//end class DiscoveredDelay

