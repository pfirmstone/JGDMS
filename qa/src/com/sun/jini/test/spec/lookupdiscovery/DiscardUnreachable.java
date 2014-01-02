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

package com.sun.jini.test.spec.lookupdiscovery;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.core.lookup.ServiceRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * With respect to the <code>discard</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon actively discarding one or more un-reachable lookup services,
 * the <code>LookupDiscovery</code> utility sends a discarded event for
 * each discarded lookup service.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each with a finite set of member groups
 *   <li> one instance of LookupDiscovery configured to discover the union
 *        of the member groups to which each lookup belongs 
 *   <li> one DiscoveryListener registered with that LookupDiscovery
 *   <li> after discovery, the generation of multicast announcements is 
 *        stopped and each lookup service is destroyed, rendering it
 *        unreachable
 *   <li> each un-reachable lookup service is queried for its locator
 *        to verify that it is indeed, un-reachable
 *   <li> if the lookup service is found to be un-reachable, the lookup
 *        service is discarded through the invocation of the discard()
 *        method of the LookupDiscovery utility
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified, then
 * a <code>DiscoveryEvent</code> instance indicating a discarded event
 * will be sent for each un-reachable lookup service; and that event will
 * accurately reflect the set of member groups with which the discarded
 * lookup service was configured.
 */
public class DiscardUnreachable extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> re-configures the LookupDiscovery utility to discover the
     *          union of the member groups to which each lookup service belongs
     *     <li> starts the discovery process by adding to the LookupDiscovery
     *          utility, a listener that listens for only discovered and
     *          discarded events
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> retrieves the proxy to each lookup service 
     *     <li> stops the multicast announcements being sent from each
     *          lookup service, and destroys the corresponding lookup service
     *     <li> attempts to interact with each lookup service, invoking
     *          the discard() method of the LookupDiscovery utility upon
     *          finding that the lookup service is un-reachable
     *     <li> verifies that the LookupDiscovery utility under test sends
     *          the expected number of discard events - with the expected
     *          contents
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
        LookupListener mainListener = this.mainListener;
        mainListener.lock.lock();
        try {
            logger.log(Level.FINE,
                              "terminating each lookup service ...");
            /* Stop announcements & destroy all lookups started in construct */
            terminateAllLookups();
            DiscoveryServiceUtil.delayMS(7000);//wait for shutdown complete
            logger.log(Level.FINE,
                         "discarding un-reachable lookup services ...");
            /* This will cause discarded events to be sent */
            List locGroupsNotDiscarded = pingAndDiscard
                                                         (proxies,
                                                          lookupDiscovery,
                                                          mainListener);
            /* Set the expected discard event info (don't expect an event
             * for anything that we failed to discard)
             */
            mainListener.setLookupsToDiscover(locGroupsNotDiscarded);
        } finally {
            mainListener.lock.unlock();
        }
        waitForDiscard(mainListener);
    }//end run

}//end class DiscardUnreachable

