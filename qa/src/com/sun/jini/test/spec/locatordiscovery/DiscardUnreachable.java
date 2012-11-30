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
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.core.lookup.ServiceRegistrar;

import java.util.ArrayList;

/**
 * With respect to the <code>discard</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon actively discarding one or more un-reachable lookup services,
 * the <code>LookupLocatorDiscovery</code> utility sends a discarded event
 * for each discarded lookup service.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, each lookup service that was started is terminated
 *        in such a way that it is rendered unreachable
 *   <li> each un-reachable lookup service is discarded through the invocation
 *        of the discard() method of the LookupLocatorDiscovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive the expected number of discarded events, each with
 * the expected contents.
 *
 */
public class DiscardUnreachable extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup locator discovery utility to discover
     *         the set of locators whose elements are the locators of each
     *         lookup service that was started during setup
     *    <li> starts the unicast discovery process by adding to the
     *         lookup locator discovery utility, a listener that listens
     *         for only discovered and discarded events
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> retrieves the proxy to each lookup service 
     *    <li> renders un-reachable, each lookup service started in setup
     *    <li> discards from the lookup locator discovery utility, each
     *         un-reachable lookup service
     *    <li> verifies that the lookup locator discovery utility under test
     *         sends the expected discarded events, with the expected
     *         contents
     * </ul>
     */
    public void run() throws Exception {
        super.run();

        /* Save the proxies for reachability tests after termination */
        ServiceRegistrar[] proxies = getLookupProxies();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive and setLookupsToDiscover examines
         * the contents of those maps. So we don't want those maps to
         * change until setLookupsToDiscover returns.
         */
        synchronized(mainListener) {
            logger.log(Level.FINE, "terminating each lookup service ...");
            /* Stop announcements & destroy all lookups started in setup */
            terminateAllLookups();
            DiscoveryServiceUtil.delayMS(7000);//wait for shutdown complete
            logger.log(Level.FINE, "discarding un-reachable lookup services ...");
            /* This will cause discarded events to be sent */
            ArrayList locGroupsNotDiscarded = pingAndDiscard
                                                         (proxies,
                                                          locatorDiscovery,
                                                          mainListener);
            /* Set the expected discard event info (don't expect an event
             * for anything that we failed to discard)
             */
            mainListener.setLookupsToDiscover(locGroupsNotDiscarded);
        }//end sync(mainListener)
        waitForDiscard(mainListener);
    }//end run

}//end class DiscardUnreachable

