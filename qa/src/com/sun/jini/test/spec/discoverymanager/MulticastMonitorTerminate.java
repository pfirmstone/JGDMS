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

import java.util.ArrayList;
import com.sun.jini.qa.harness.QAConfig;
import java.util.List;

/**
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * handles, in a manner consistent with the specification, the "passive" 
 * discarded events that occur as a result of the termination of discovered
 * lookup services and the multicast announcements those lookups broadcast.
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
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded events, with the
 * expected contents.
 *
 * Related bug ids: 4292957
 */
public class MulticastMonitorTerminate extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> reconfigures the lookup discovery manager to discover some of
     *         the lookups by only group discovery, some by only locator
     *         discovery, and some by both group and locator discovery
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> stops the generation of multicast announcements by destroying
     *         the lookups
     *    <li> verifies that the lookup discovery manager under test
     *         sends the expected number of discarded events with the
     *         expected contents
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        verifyAnnouncementsSent();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and the contents of those maps
         * are examined; so those maps shouldn't be allowed to change
         * they have been examined.
         */
        synchronized(mainListener) {
            terminateAllLookups();//cause discard events to be sent
            /* Since passive discards will NOT occur for those lookups
             * that were discovered by only locator discovery, the 
             * lookups that were discovered by only group discovery or
             * both group and locator discovery are retrieved, and the
             * expected discard info is set for those lookups.
             */
            List discoveredByGroupsList =
                                    filterListByGroups(initLookupsToStart,
                                                       groupsToDiscover);
            mainListener.setDiscardEventInfo(discoveredByGroupsList);
        }//end sync(mainListener)
        waitForDiscard(mainListener);
    }//end run

}//end class MulticastMonitorTerminate

