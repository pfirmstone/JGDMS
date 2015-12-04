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

package org.apache.river.test.spec.discoverymanager;

import java.util.logging.Level;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.List;

/**
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * handles, in a manner consistent with the specification, the "passive" 
 * discarded events that occur as a result of the complete replacement of
 * the member groups of previously discovered lookup services.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover all of the
 *         lookups by only group discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after all of the lookup services are successfully discovered, 
 *         each lookup's set of member groups is completely replaced
 *         with a new set
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded events, with the
 * expected contents.
 *
 * Related bug ids: 4292957
 */
public class GroupsMulticastMonitorReplace extends Discovered {

    protected String[] replacementGroups = null;//null ==> generate new groups
    protected int nLookupsToReplace = 0;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        groupsToDiscover = toGroupsToDiscover(getInitLookupsToStart(),
                                              AbstractBaseTest.ALL_BY_GROUP);
        locatorsToDiscover = toLocatorsToDiscover
                                             (getInitLookupsToStart(),
                                              AbstractBaseTest.ALL_BY_GROUP);
        nLookupsToReplace = getGenMap().size();//replace groups on all lookups
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> reconfigures the lookup discovery manager to discover all of the
     *         lookups by only group discovery
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> replaces the member groups of each discovered lookup service
     *         with a completely new set that contains none of the elements
     *         from the original set
     *    <li> verifies that the lookup discovery manager utility under test
     *         sends the expected discarded events with the expected contents
     *    </ul>
     */
    public void run() throws Exception {
        super.run();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and setLookupsToDiscover
         * examines the contents of those maps. So we don't want those
         * maps to change until setLookupsToDiscover returns.
         */
        LookupListener mainListener = this.mainListener;
        mainListener.lock.lock();
        try {
            List locGroupsPairList = null;
            /* Replace current groups with new groups to cause discards */
            if(replacementGroups == null) {//use unique generated groups
                locGroupsPairList = replaceMemberGroups();
            } else {//use groups preset in construct
                locGroupsPairList = replaceMemberGroups(nLookupsToReplace,
                                                        replacementGroups);
            }//endif
            /* Set the expected discard event info */
            mainListener.setLookupsToDiscover(locGroupsPairList,
                                               locatorsToDiscover,
                                               groupsToDiscover);
        } finally {
            mainListener.lock.unlock();
        }
        waitForDiscard(mainListener);
    }//end run

}//end class GroupsMulticastMonitorReplace

