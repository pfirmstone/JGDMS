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

package org.apache.river.test.spec.locatordiscovery;

import java.util.logging.Level;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.LocatorsUtil;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular, this
 * class verifies that "the method <code>getUndiscoveredLocators</code> returns
 * a new array upon each invocation."
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
 * If the lookup locator discovery utility functions as specified, then on
 * each separate invocation of the <code>getUndiscoveredLocators</code> method,
 * a new array containing the desired, but not-yet-discovered
 * <code>LookupLocator</code> instances will be returned.
 *
 */
public class GetUndiscoveredLocatorsNew extends GetUndiscoveredLocators {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies that getUndiscoveredLocators returns the locators of
     *         the desired, but not-yet-discovered, lookup services for
     *         which the lookup locator discovery utility is configured
     *    <li> invokes getUndiscoveredLocators two more times and verifies 
     *         that each invocation returns different arrays having the
     *         same content
     * </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "1st call to "
                                        +"getUndiscoveredLocators ...");
        LookupLocator[] lldLocs0
                             = locatorDiscovery.getUndiscoveredLocators();
        LocatorsUtil.displayLocatorSet(lldLocs0,
                                       "From 1st call  -- locator",
                                       Level.FINE);
        logger.log(Level.FINE, "2nd call to "
                                        +"getUndiscoveredLocators ...");
        LookupLocator[] lldLocs1 
                             = locatorDiscovery.getUndiscoveredLocators();
        LocatorsUtil.displayLocatorSet(lldLocs1,
                                       "From 2nd call  -- locator",
                                       Level.FINE);
        logger.log(Level.FINE, "verifying arrays from calls "
                      +"to getUndiscoveredLocators() are different arrays "
                      +"with equal content ...");
        if(lldLocs0 == lldLocs1) {
            throw new TestException("same array returned on different calls");
        }//endif
	if (!LocatorsUtil.compareLocatorSets(lldLocs0, lldLocs1,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
    }//end run

}//end class GetUndiscoveredLocatorsNew

