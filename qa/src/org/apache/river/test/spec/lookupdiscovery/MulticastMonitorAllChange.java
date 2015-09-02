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

import net.jini.discovery.DiscoveryGroupManagement;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * monitors the multicast announcements sent from the lookup service
 * in a manner consistent with the specification. In particular, this
 * class verifies that when a lookup discovery utility, configured to
 * discover ALL_GROUPS, is also configured to notify an instance of
 * <code>DiscoveryChangeListener</code> of group membership changes
 * occurring in the lookup services previously discovered, through the
 * contents of the multicast announcements received, the lookup discovery
 * utility can determine when the member groups of those lookup services
 * have changed from the original finite set of member groups to a new set
 * containing NONE of the original member groups, and will send to the
 * listener, changed events referencing the correct changed event information
 * corresponding to each such lookup service.
 *
 * Note that because the member groups will be changed to contain NONE of the
 * original member groups, one might assume that the lookup discovery utility
 * will discard the lookup services. But changed events, not discarded events,
 * will be sent because no matter what the member groups are changed to, 
 * the lookup discovery utility will still be interested in those lookup 
 * services since that utility is configured to discover ALL_GROUPS.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services, each belonging to a finite set of
 *         member groups
 *    <li> one instance of LookupDiscovery configured to discover ALL_GROUPS
 *         (that is, any lookup service belonging to any group or NO_GROUPS,
 *         even those lookup servives NOT started by this test)
 *    <li> one DiscoveryChangeListener registered with the lookup discovery
 *         utility
 *    <li> after discovery, the set of member groups of each the lookup
 *         services started by this test is changed to a set in which NONE
 *         of the elements equals any of the original member groups used
 *         when starting the lookup services
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then because the previously discovered lookup services have had their
 * group memberships changed, and since the lookup discovery utility is
 * still interested in those lookup services (because the lookup discovery 
 * utility is configured to discover ALL_GROUPS), the lookup discovery
 * utility will send to the listener, changed events referencing the correct
 * changed event information corresponding to each lookup service whose group
 * membership has changed.
 *
 * Related bug ids: 4292957
 * 
 * @see org.apache.river.test.spec.lookupdiscovery.MulticastMonitorReplace
 */
public class MulticastMonitorAllChange extends MulticastMonitorChange {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        groupsToDiscover = DiscoveryGroupManagement.ALL_GROUPS;
        alternateReplacements = false;//replace all groups
        useFastTimeout = false;//needs max discovery time for lookup start
        return this;
    }//end construct

}//end class MulticastMonitorAllChange

