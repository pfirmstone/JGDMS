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
 * With respect to the <code>addDiscoveryListener</code> method, this class
 * verifies that the <code>LookupLocatorDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking <code>addDiscoveryListener</code> with a
 * <code>null</code> parameter, a <code>NullPointerException</code> is
 * thrown".
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, a null DiscoveryListener is added to the
 *        LookupLocatorDiscovery utility through the invocation of the
 *        addDiscoveryListener method
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discovered event
 * (accurately reflecting the expected contents) will be sent to the initial
 * listener for each lookup service that was started; and, upon the invocation
 * of the <code>addDiscoveryListener</code> method with a <code>null</code>
 * parameter, a <code>NullPointerException</code> will occur.
 *
 */
public class AddDiscoveryListenerNull extends Discovered {

    protected LookupListener newListener = null;

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the set of locators whose elements are the locators of each
     *          lookup service that was started during setup
     *     <li> starts the unicast discovery process by adding a listener to
     *          the lookup locator discovery utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> invokes the addDiscoveryListener method on the lookup locator
     *          discovery utility to attempt to add null to set of listeners
     *          waiting for discovered and discarded events
     *     <li> verifies that a NullPointerException is thrown when the
     *          attempt to add null to the listeners is made
     *   </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, 
		   "adding a NULL listener to "
		   +"LookupLocatorDiscovery ... ");
        try {
            locatorDiscovery.addDiscoveryListener(newListener);
            throw new TestException("no NullPointerException");
        } catch(NullPointerException e) {
            logger.log(Level.FINE, 
		       "NullPointerException occurred as expected");
        }
    }//end run

}//end class AddDiscoveryListenerNull

