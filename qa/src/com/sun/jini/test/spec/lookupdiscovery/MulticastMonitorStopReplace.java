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

import java.util.ArrayList;
import java.util.List;

/** This class verifies that the <code>LookupDiscovery</code> utility
 *  monitors the multicast announcements sent from lookup services
 *  in a manner consistent with the specification. In particular, this
 *  class verifies that when the lookup discovery utility stops receiving
 *  multicast announcements from a lookup service, even if that lookup
 *  service is still running and reachable, if it no longer belongs to
 *  any of the groups the lookup discovery utility is currently interested
 *  in discovering, the lookup discovery utility will discard that lookup
 *  service.
 *
 *  Note that this class verifies only one aspect of the multicast monitoring
 *  mechanism provided by the lookup discovery utility: upon determining 
 *  that multicast announcements have ceased being received, the lookup
 *  discovery utility will test the associated lookup service for
 *  reachability; and will discard that lookup service only if that
 *  lookup service is unreachable, or no longer belongs to any of the
 *  current groups of interest.  Full verification of the multicast
 *  announcement monitoring mechanism is acheived through the combination
 *  of the lookup discovery utility test classes whose names are prefixed
 *  with 'MulticastMonitor'.
 *
 *  The environment in which this class expects to operate is as follows:
 *  <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of LookupDiscovery configured to discover the union
 *        of the member groups to which each lookup service belongs 
 *   <li> one DiscoveryListener registered with the lookup discovery utility
 *   <li> after discovery, the generation of multicast announcements is 
 *        stopped and the lookup services associated with those announcements
 *        remain reachable, but the member groups of those lookup services
 *        are replaced with a set of groups containing NONE of the groups
 *        the lookup discovery utility is currently interested in discovering
 *  </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then even though the lookup discovery utility no longer receives
 * mulitcast announcements from the previously discovered lookup services,
 * and even though those lookup services are determined to still be reachable,
 * because those lookup services no longer belong to any of the groups of
 * interest, the lookup discovery utility will discard each of those lookup
 * services.
 *
 * Related bug ids: 4292957
 * 
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorChange
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplace
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplaceNone
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorStop
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorTerminate
 */
public class MulticastMonitorStopReplace extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies the lookup discovery mechanism is functional by
     *         using group discovery to discover the lookup services
     *         started during construct
     *    <li> stops the generation of multicast announcements by each
     *         lookup service, but allows the lookup services to remain
     *         running and reachable
     *    <li> verifies that the lookup discovery utility discards the
     *         lookup services whose member groups were changed
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        verifyAnnouncementsSent();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and setLookupsToDiscover
         * examines the contents of those maps. So we don't want those
         * maps to change until setLookupsToDiscover returns.
         */
        synchronized(mainListener) {
            stopAnnouncements();
            /* Replace current groups with new groups to cause discards */
            List locGroupsPairList = replaceMemberGroups();
            /* Set the expected discard event info */
            mainListener.setLookupsToDiscover
                                     (locGroupsPairList,
                                      toGroupsArray(getInitLookupsToStart()));
        }//end sync(mainListener)
        waitForDiscard(mainListener);
    }//end run

}//end class MulticastMonitorStopReplace
