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

/**
 * With respect to the <code>removeDiscoveryListener</code> method, this class
 * verifies that the <code>LookupLocatorDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking <code>removeDiscoveryListener</code> with a
 * listener that does not exist in the set of listeners maintained by the
 * <code>LookupLocatorDiscovery</code> utility, no action is taken by
 * <code>removeDiscoveryListener</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, the method removeDiscoveryListener is invoked in
 *        an attempt to remove from the set of listeners maintained by the
 *        LookupLocatorDiscovery utility, an instance of DiscoveryListener
 *        that is known to not be an element of that set of listeners
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discovered event
 * (accurately reflecting the expected contents) will be sent to the initial
 * listener for each lookup service that was started; and, upon the invocation
 * of the <code>removeDiscoveryListener</code> method with an instance of
 * <code>DiscoveryListener</code> known to not be an element of the set of
 * managed listeners, no further events are received or actions are taken.
 *
 */
public class RemoveDiscoveryListenerDNE extends Discovered {

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the set of locators whose elements are the locators of each
     *          lookup service that was started during construct
     *     <li> starts the unicast discovery process by adding a listener to
     *          the lookup locator discovery utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> invokes the removeDiscoveryListener method on the lookup
     *          locator discovery utility with a listener that is not an
     *          element of that utility's managed set of listeners
     *     <li> verifies that no further events are received or actions are
     *          taken
     *   </ul>
     */
    public void run() throws Exception {
        super.run();

        LookupListener dneListener = new AbstractBaseTest.LookupListener();
        logger.log(Level.FINE, 
		   "attempt to remove a listener that "
		   +"DOES NOT EXIST in the managed set "
		   +"of listeners ... ");
	locatorDiscovery.removeDiscoveryListener(dneListener);
	dneListener.clearAllEventInfo();
	logger.log(Level.FINE, "verifying non-existant "
		   +"listener receives NO events ...");
	waitForDiscovery(dneListener);
    }//end run

}//end class RemoveDiscoveryListenerDNE

