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

import net.jini.discovery.DiscoveryGroupManagement;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * monitors the multicast announcements sent from the lookup service
 * in a manner consistent with the specification. In particular, this
 * class verifies that when the lookup discovery utility, through the
 * contents of the multicast announcements it receives, determines that
 * the member groups of each of a subset of the lookup services it had
 * previously discovered have changed from a finite set of groups to
 * NO_GROUPS - indicating that that the lookup service is no longer of
 * any interest - the lookup discovery utility will discard each
 * such lookup service.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N (for N >= 1) lookup services, each belonging to a finite set of
 *         member groups
 *    <li> one instance of LookupDiscovery configured to discover the union
 *         of the member groups to which each lookup service belongs 
 *    <li> one DiscoveryListener registered with the lookup discovery utility
 *    <li> after discovery, the member groups of [1+(N-1)/2] of the previously
 *         discovered lookup services are changed to NO_GROUPS
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then because the previously discovered lookup services whose member 
 * groups have been changed to NO_GROUPS no longer belong to any of the
 * original groups of interest, the lookup discovery utility will discard
 * each of those lookup services; but will NOT discard the lookup services
 * whose member groups were not changed.
 *
 * Related bug ids: 4292957
 * 
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplace
 */
public class MulticastMonitorReplaceNone extends MulticastMonitorReplace {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        replacementGroups = DiscoveryGroupManagement.NO_GROUPS;
        int N = nLookupServices + nAddLookupServices;
        nLookupsToReplace = 1 + ( (N-1)/2 );
        if(nLookupsToReplace <= 0) nLookupsToReplace = 1;
    }//end setup

}//end class MulticastMonitorReplaceNone

