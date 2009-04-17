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
 * With respect to the <code>removeLocators</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>removeLocators</code> method with the empty
 * set, the set of locators the lookup locator discovery utility is configured
 * to discover will not change.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of the lookup locator discovery utility initially
 *        configured to discover the set of locators whose elements are the
 *        locators of the initial lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then
 * after invoking the <code>removeLocators</code> method with the empty
 * set, the contents of the managed set of locators with which the lookup
 * locator discovery utility is configured prior to the invocation of 
 * <code>removeLocators</code> is the same as the contents of that set 
 * after <code>removeLocators</code> is invoked; and the listener will
 * receive no discarded events.
 *
 */
public class RemoveLocatorsEmpty extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> verifies that the lookup locator discovery utility under test
     *          discovers the lookup services that were started during setup
     *     <li> re-configures the listener's expected event state to expect
     *          no more discarded events
     *     <li> removes the empty set from the set of locators with which the
     *          lookup locator discovery utility is configured
     *     <li> verifies the contents of the managed set of locators with
     *          which the lookup locator discovery utility is configured
     *          prior to the invocation of removeLocators is the same as the
     *          contents of that set after removeLocators is invoked
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends no discarded events
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Verify discovery of the initial lookups */
        doDiscovery(initLookupsToStart,mainListener);
        mainListener.clearAllEventInfo();
        /* Remove the empty set */
        LookupLocator[] locsToRemove = new LookupLocator[0];
        logger.log(Level.FINE, "remove the EMPTY SET from "
                          +"lookup locator discovery --");
        LocatorsUtil.displayLocatorSet(locsToRemove,"locator",
                                       Level.FINE);

        LookupLocator[] locsBefore = locatorDiscovery.getLocators();
        locatorDiscovery.removeLocators(locsToRemove);
        LookupLocator[] locsAfter = locatorDiscovery.getLocators();

        logger.log(Level.FINE, "comparing locators in managed "
                      +"set BEFORE and AFTER removing the EMPTY SET ...");
	if (!LocatorsUtil.compareLocatorSets(locsBefore, locsAfter,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
        /* Verify no discarded events were sent */
        waitForDiscard(mainListener);
    }//end run

}//end class RemoveLocatorsEmpty

