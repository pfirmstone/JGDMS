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

package org.apache.river.test.spec.lookupdiscovery;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * monitors the multicast announcements sent from the lookup service
 * in a manner consistent with the specification. In particular, this
 * class verifies that when a lookup discovery utility is configured to
 * notify an instance of <code>DiscoveryChangeListener</code> of group
 * membership changes occurring in the lookup services previously discovered,
 * through the contents of the multicast announcements received, the lookup
 * discovery utility can determine when the member groups of those lookup
 * services have changed from the original finite set of member groups
 * to a new set containing only a SUBSET of the original member groups, and
 * will send to the listener, changed events referencing the correct changed
 * event information corresponding to each such lookup service.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services, each belonging to a finite set of
 *         member groups
 *    <li> one instance of LookupDiscovery configured to discover the union
 *         of the member groups to which each lookup service belongs 
 *    <li> one DiscoveryChangeListener registered with the lookup discovery
 *         utility
 *    <li> after discovery, the set of member groups of each the lookup
 *         services started by this test is changed to a set in which SOME
 *         of the elements equals one of the original member groups used
 *         when starting the lookup services, and some of the elements 
 *         equal none of the original member groups of that lookup service
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then because the previously discovered lookup services have had their
 * group memberships changed, the lookup discovery utility will send to
 * the listener, changed events referencing the correct changed event
 * information corresponding to each lookup service whose group membership
 * has changed.
 *
 * Related bug ids: 4292957
 * 
 * @see org.apache.river.test.spec.lookupdiscovery.MulticastMonitorReplace
 */
public class MulticastMonitorChange extends Discovered {

    protected volatile String[] replacementGroups = null;//null ==> generate new groups
    protected volatile boolean alternateReplacements = true;//some replaced, some not

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        listenerToUse = new AbstractBaseTest.GroupChangeListener();
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies the lookup discovery mechanism is functional by
     *         using group discovery to discover the lookup services
     *         started during construct
     *    <li> for alternating elements, replaces with a new group name,
     *         the member group elements of each of the discovered lookup
     *         services
     *    <li> verifies that the multicast announcements are correctly 
     *         monitored and handled by verifying that the lookup discovery
     *         utility sends to the listener, changed events referencing the
     *         correct changed event information corresponding to each lookup
     *         service whose member groups were changed
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and setLookupsToDiscover
         * examines the contents of those maps. So we don't want those
         * maps to change until setLookupsToDiscover returns.
         */
        LookupListener listenerToUse = this.listenerToUse;
        listenerToUse.lock.lock();
        try {
            List locGroupsPairList = null;
            /* Replace groups with new groups to cause changed events */
            locGroupsPairList = replaceMemberGroups(alternateReplacements);
            /* Set the expected changed event info */
            listenerToUse.setLookupsToDiscover
                                     (locGroupsPairList,
                                      groupsToDiscover);
        } finally {
            listenerToUse.lock.unlock();
        }
        waitForChange((GroupChangeListener)listenerToUse);
    }//end run

}//end class MulticastMonitorReplaceAll

