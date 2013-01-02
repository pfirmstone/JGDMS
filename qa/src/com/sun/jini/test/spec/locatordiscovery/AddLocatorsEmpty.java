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
 * that upon invoking the <code>addLocators</code> method with the empty
 * set, the set of locators the lookup locator discovery utility is configured
 * to discover will not change.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more "initial" lookup services, each started during construct,
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
 * If the lookup locator discovery utility functions as specified, then
 * after invoking the <code>addLocators</code> method with the empty
 * set, the contents of the managed set of locators with which the lookup
 * locator discovery utility is configured prior to the invocation of 
 * <code>addLocators</code> is the same as the contents of that set 
 * after <code>addLocators</code> is invoked; and the listener will
 * receive no discovered events.
 *
 */
public class AddLocatorsEmpty extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> start the additional lookup services
     *     <li> verifies that the lookup locator discovery utility under test
     *          discovers the initial lookup services that were started 
     *          during construct
     *     <li> re-configures the listener's expected event state to expect
     *          no more discovered events
     *     <li> adds the empty set to the set of locators with which the
     *          lookup locator discovery utility is configured
     *     <li> verifies the contents of the managed set of locators with
     *          which the lookup locator discovery utility is configured
     *          prior to the invocation of addLocators is the same as the
     *          contents of that set after addLocators is invoked
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends no discovered events
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Start the additional lookup services */
        startAddLookups();
        /* Verify discovery of the initial lookups */
        doDiscovery(getInitLookupsToStart(),mainListener);
        /* Configure the listener's expected event state for the additional
         * lookup services
         */
        mainListener.clearAllEventInfo();
        /* Add the empty set */
        LookupLocator[] locsToAdd = new LookupLocator[0];
        logger.log(Level.FINE, "add the EMPTY SET to "
                          +"lookup locator discovery --");
        LocatorsUtil.displayLocatorSet(locsToAdd,"locator",
                                       Level.FINE);

        LookupLocator[] locsBefore = locatorDiscovery.getLocators();
        locatorDiscovery.addLocators(locsToAdd);
        LookupLocator[] locsAfter = locatorDiscovery.getLocators();

        logger.log(Level.FINE, "comparing locators in managed "
                         +"set BEFORE and AFTER adding the EMPTY SET ...");
	if (!LocatorsUtil.compareLocatorSets(locsBefore, locsAfter,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
        /* Verify no discovered events were sent */
        waitForDiscovery(mainListener);
    }//end run

}//end class AddLocatorsEmpty

