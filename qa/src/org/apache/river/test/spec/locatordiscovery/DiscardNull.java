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

import net.jini.core.lookup.ServiceRegistrar;

import java.util.ArrayList;

/**
 * With respect to the <code>discard</code> method, this class verifies that
 * the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking <code>discard</code> with a <code>null</code> parameter,
 * no action is taken by <code>discard</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, each of the lookup service(s) that were started
 *        are destroyed and then discarded so that it can be verified that
 *        the discard mechanism is operating correctly
 *   <li> the discard method on the LookupLocatorDiscovery instance is then
 *        invoked with a null parameter
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discovered event
 * (accurately reflecting the expected contents) will be sent to the initial
 * listener for each lookup service that was started. Upon the invocation
 * of the <code>discard</code> method with each discovered lookup service,
 * the expected discarded events are sent. And upon the invocation of the
 * <code>discard</code> method with a <code>null</code> parameter, no further
 * events are sent or actions are taken.
 *
 */
public class DiscardNull extends Discovered {

    protected ServiceRegistrar proxy = null;
    protected String discardStr = "attempt to discard a NULL registrar ...";

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup locator discovery utility to discover
     *         the set of locators whose elements are the locators of each
     *         lookup service that was started during construct
     *    <li> starts the unicast discovery process by adding a listener to
     *         the lookup locator discovery utility
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> for each lookup service started during construct, destroys the
     *         lookup service and invokes the discard method on the lookup
     *         locator discovery utility
     *    <li> verifies that the discard mechanism is working correctly by
     *         verifying the expected discarded events - with the expected
     *         contents - are sent by the lookup locator discovery utility
     *         to the listener
     *    <li> again invokes the discard method on the lookup locator
     *         discovery utility, this time using a null paramete
     *    <li> verifies that no further events are received or actions are
     *         taken
     * </ul>
     */
    public void run() throws Exception {
        /* Verify that the discovery mechanism is working properly */
        super.run();

        /* Next, verify that the discard mechanism is working properly */
        ServiceRegistrar[] discoveredProxies
                                          = locatorDiscovery.getRegistrars();
        /* Update listener's state to expect appropriate discard events.
         * Input an empty ArrayList since we are discarding every discovered
         * registrar (this will cause all previously discovered registrars
         * to be moved from the expected discovered map to the expected
         * discarded map). Note that each registrar must first be destroyed
         * so that it is not immediately re-discovered after it is discarded.
         * This is intended to verify that the discard mechanism is working 
         * correctly.
         */
        mainListener.setLookupsToDiscover(new ArrayList(1));
        logger.log(Level.FINE, "destroying and then discarding EACH "
                              +"previously discovered registrar ...");
        /* Perform the actual discards to generate the discard events */
        for(int i=0;i<discoveredProxies.length;i++) {
	    discoveredProxies[i] = (ServiceRegistrar)
	    getConfig().prepare("test.reggiePreparer", 
				discoveredProxies[i]);
	    getManager().destroyService(discoveredProxies[i]);
	    locatorDiscovery.discard( discoveredProxies[i] );
	}//end loop
        waitForDiscard(mainListener);//verify the discarded events
        /* Finally, verify that inputting either null or an unknown registrar
         * to discard() has no effect.
         */
        logger.log(Level.FINE, discardStr);
        locatorDiscovery.discard(proxy);
        mainListener.clearAllEventInfo();
        logger.log(Level.FINE, "verifying NO discarded events occur ...");
        waitForDiscard(mainListener);
    }//end run

}//end class DiscardNull

