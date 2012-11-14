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

import java.util.ArrayList;
import java.util.List;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * monitors the multicast announcements sent from the lookup service
 * in a manner consistent with the specification. In particular, this
 * class verifies that when the lookup discovery utility, through the
 * contents of the multicast announcements it receives, determines that
 * the member groups of a particular lookup service it had previously
 * discovered have changed in such a way that the lookup service is no
 * longer of any interest, the lookup discovery utility discards that
 * lookup service.
 *
 *  The environment in which this class expects to operate is as follows:
 *  <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of LookupDiscovery configured to discover the union
 *        of the member groups to which each lookup service belongs 
 *   <li> one DiscoveryListener registered with the lookup discovery utility
 *   <li> after discovery, the member groups of each previously discovered
 *        lookup service is changed to a set in which NONE of the elements
 *        is a group the lookup discovery utility is interested in discovering
 *  </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then because the previously discovered lookup services no longer belong
 * to any of the groups of interest, the lookup discovery utility will
 * discard each of those lookup services.
 *
 * Related bug ids: 4292957
 * 
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorChange
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplace
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorReplaceNone
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorStop
 * @see com.sun.jini.test.spec.lookupdiscovery.MulticastMonitorTerminate
 */
public class MulticastMonitorReplace extends Discovered {

    protected String[] replacementGroups = null;//null ==> generate new groups
    protected int nLookupsToReplace = 0;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        nLookupsToReplace = genMap.size();//replace groups on all lookups
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies the lookup discovery mechanism is functional by
     *         using group discovery to discover the lookup services
     *         started during setup
     *    <li> replaces the member groups of each of the discovered lookup
     *         services with a new set that contains none of the elements
     *         from the set of groups the lookup discovery utility is
     *         configured to discover
     *    <li> verifies that the lookup discovery utility discards the
     *         lookup services whose member groups were changed
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and setLookupsToDiscover
         * examines the contents of those maps. So we don't want those
         * maps to change until setLookupsToDiscover returns.
         */
        synchronized(listenerToUse) {
            List locGroupsPairList = null;
            /* Replace current groups with new groups to cause discards */
            if(replacementGroups == null) {//use unique generated groups
                locGroupsPairList = replaceMemberGroups();
            } else {//use groups preset in setup
                locGroupsPairList = replaceMemberGroups(nLookupsToReplace,
                                                        replacementGroups);
            }//endif
            /* Set the expected discard event info */
            listenerToUse.setLookupsToDiscover
                                     (locGroupsPairList,
                                      toGroupsArray(initLookupsToStart));
        }//end sync(listenerToUse)
        waitForDiscard(listenerToUse);
    }//end run

}//end class MulticastMonitorReplace


