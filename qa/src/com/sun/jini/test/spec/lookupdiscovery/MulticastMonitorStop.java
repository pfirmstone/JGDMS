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

/** This class verifies that the <code>LookupDiscovery</code> utility
 *  monitors the multicast announcements sent from lookup services
 *  in a manner consistent with the specification. In particular, this
 *  class verifies that even if the lookup discovery utility stops 
 *  receiving multicast announcements from a lookup service, if that
 *  lookup service is still running and reachable, and that lookup service
 *  still belongs to at least one group of interest, the lookup discovery
 *  utility will NOT discard that lookup service.
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
 *        stopped but the lookup services associated with those announcements
 *        remain reachable, and continue to belong to at least one of the
 *        groups the lookup discovery utility is currently interested in
 *        discovering
 *  </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then even though the lookup discovery utility no longer receives
 * mulitcast announcements from the previously discovered lookup services,
 * because the lookup services are still reachable and they still belong to
 * at least one group of interest, the lookup discovery utility will not
 * discard those lookup services.
 *
 * Related bug ids: 4292957
 * 
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorChange
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplace
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplaceNone
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorStopReplace
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorTerminate
 */
public class MulticastMonitorStop extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies the lookup discovery mechanism is functional by
     *         using group discovery to discover the lookup services
     *         started during construct
     *    <li> stops the generation of multicast announcements by each
     *         lookup service, but allows the lookup services to remain
     *         running and reachable
     *    <li> verifies that although the multicast announcements from the
     *         lookup services have ceased, the lookup discovery utility
     *         does not discard the lookup services because they are still
     *         reachable
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        verifyAnnouncementsSent();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and clearAllEventInfo
         * changes the contents of those maps. So we don't want those
         * maps to change due to events until clearAllEventInfo returns.
         */
        synchronized(mainListener) {
            stopAnnouncements();
            mainListener.clearAllEventInfo();//expect no discard events
        }//end sync(mainListener)
        waitForDiscard(mainListener);
    }//end run

}//end class MulticastMonitorStop
