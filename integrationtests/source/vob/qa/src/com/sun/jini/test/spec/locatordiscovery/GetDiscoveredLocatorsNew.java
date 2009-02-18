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
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular, this
 * class verifies that "the method <code>getDiscoveredLocators</code> returns
 * a new array upon each invocation."
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
 * If the lookup locator discovery utility functions as specified, then on
 * each separate invocation of the <code>getDiscoveredLocators</code> method,
 * a new array containing the discovered <code>LookupLocator</code> instances
 * will be returned.
 *
 */
public class GetDiscoveredLocatorsNew extends GetDiscoveredLocators {

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> verifies that getDiscoveredLocators returns the locators of
     *          the lookup services that have already been discovered
     *     <li> invokes getDiscoveredLocators two more times and verifies 
     *          that each invocation returns different arrays having the
     *          same contents
     * </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "1st call to "
                                        +"getDiscoveredLocators ...");
        LookupLocator[] lldLocs0 = locatorDiscovery.getDiscoveredLocators();
        logger.log(Level.FINE, "2nd call to "
                                        +"getDiscoveredLocators ...");
        LookupLocator[] lldLocs1 = locatorDiscovery.getDiscoveredLocators();
        logger.log(Level.FINE, "verifying arrays from calls "
                          +"to getDiscoveredLocators() are different arrays "
                          +"with equal content ...");
        if(lldLocs0 == lldLocs1) {
            throw new TestException(
                                 "same array returned on different calls");
        }//endif
	if (!LocatorsUtil.compareLocatorSets(lldLocs0, lldLocs1,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
    }//end run

}//end class GetDiscoveredLocatorsNew

