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

/**
 * With respect to the <code>addLocators</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>addLocators</code> method to re-configure
 * the <code>LookupLocatorDiscovery</code> utility to discover a set of
 * locators which augments the current set of locators to discover with
 * a new set of locators, that utility will send discovered events referencing
 * the lookup services whose locators belong to the additional set of locators
 * to discover.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more "initial" lookup services, each started during setup,
 *        before the test begins execution
 *   <li> one or more "additional" lookup services, each started after the
 *        test has begun execution
 *   <li> one instance of the lookup locator discovery utility
 *   <li> the lookup locator discovery utility is initially configured to
 *        discover the set of locators whose elements are the locators of
 *        the initial lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then after
 * invoking the <code>addLocators</code> method, the listener will receive the
 * expected discovery events, with the expected contents.
 *
 */
public class AddLocators extends Discovered {
    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> start the additional lookup services
     *     <li> verifies that the lookup locator discovery utility under test
     *          discovers the initial lookup services that were started 
     *          during setup
     *     <li> re-configures the listener's expected event state to expect
     *          the discovery of the addtional lookup services
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the new set of locators in addtion to the initial set of
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends the expected discovered events, having the expected 
     *          contents related to the additional lookups that were started
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Start the additional lookup services */
        startAddLookups();
        /* Verify discovery of the initial lookups */
        doDiscovery(initLookupsToStart,mainListener);
        /* Configure the listener's expected event state for the additional
         * lookup services
         */
        mainListener.clearAllEventInfo();
        mainListener.setLookupsToDiscover(addLookupsToStart);
        /* Configure the lookup locator discovery utility to discover the
         * additional lookups
         */
        LookupLocator[] locsToAdd = toLocatorArray(addLookupsToStart);
        locatorDiscovery.addLocators(locsToAdd);
        logger.log(Level.FINE, "added additional locators to "
                          +"lookup locator discovery --");
        LocatorsUtil.displayLocatorSet(locsToAdd,"locator",
                                       Level.FINE);
        /* Verify discovery of the added lookups */
        waitForDiscovery(mainListener);
    }//end run

}//end class AddLocators

