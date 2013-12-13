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

package com.sun.jini.test.spec.discoverymanager;

import java.util.logging.Level;

import com.sun.jini.test.share.DiscoveryServiceUtil;
import net.jini.core.lookup.ServiceRegistrar;
import java.util.ArrayList;
import java.util.logging.Level;
import com.sun.jini.qa.harness.QAConfig;
import java.util.List;

/**
 * With respect to the <code>discard</code> method, this class verifies
 * that the <code>LookupDiscoveryManager</code> utility operates in a manner
 * consistent with the specification.
 * <p>
 * In particular, this class verifies that upon actively discarding one or
 * more un-reachable lookup services, the lookup discovery manager sends
 * a discarded event, with the appropriate contents, for each discarded
 * lookup service.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover some of the
 *         lookups by only group discovery, some by only locator discovery,
 *         and some by both group and locator discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, each lookup service is destroyed, rendering it
 *         unreachable
 *    <li> each un-reachable lookup service is queried for its locator and,
 *         if it is found to be un-reachable, is discarded from the lookup
 *         discovery manager
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded events, with the
 * expected contents.
 */
public class DiscardUnreachable extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> reconfigures the lookup discovery manager to discover some of
     *         the lookups by only group discovery, some by only locator
     *         discovery, and some by both group and locator discovery
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> destroys each lookup, rendering it un-reachable
     *    <li> discards each lookup that is determined to be un-reachable
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        /* Save the proxies for reachability tests after termination.
	 * The returned proxies are already prepared using the 
	 * default reggie preparer
	 */
        ServiceRegistrar[] proxies = getLookupProxies();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and the contents of those maps
         * are examined; so those maps shouldn't be allowed to change
         * they have been examined.
         */
        synchronized(mainListener) {
            logger.log(Level.FINE, "terminating each lookup service ...");
            /* Stop announcements & destroy all lookups started in construct */
            terminateAllLookups();
            DiscoveryServiceUtil.delayMS(7000);//wait for shutdown complete
            logger.log(Level.FINE, "discarding un-reachable lookup services ...");

            /* This will cause discarded events to be sent */
            List locGroupsNotDiscarded = pingAndDiscard(proxies,
                                                             discoveryMgr,
                                                             mainListener);
            /* Set the expected discard event info */
            mainListener.setLookupsToDiscover(locGroupsNotDiscarded);
        }//end sync(mainListener)
        waitForDiscard(mainListener);
    }
}

