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
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * monitors, in a manner consistent with the specification, the mulitcast
 * announcements received from lookup services it has discovered.
 * <p>
 * In particular, this test verifies that when a discovered lookup service
 * stops sending multicast announcements, the lookup discovery manager 
 * can identify that those announcements have indeed stopped being received.
 * This test also verifies that the lookup discovery manager will send the
 * appropriate event (discarded, changed or no event), with the appropriate
 * contents, depending on how the lookup service was previously discovered
 * (group discovery, locator discovery, or both), and depending on the state
 * of the member groups of the lookup service when the lookup discovery
 * manager determines that the announcements have stopped.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover all of the
 *         lookups by only locator discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, the generation of multicast announcements is 
 *         stopped, but the lookup services associated with those announcements
 *         remains reachable
 *    <li> after stopping the announcements, the member groups of each lookup
 *         are changed to a set containing none of the lookup's original
 *         member groups (so that passive discarded and changed events will
 *         be generated)
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded and changed events,
 * with the expected contents.
 *
 * Related bug ids: 4292957
 * 
 * @see com.sun.jini.test.spec.discoverymanager.MulticastMonitorStop
 */
public class LocsMulticastMonitorStop extends MulticastMonitorStop {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        groupsToDiscover = toGroupsToDiscover(initLookupsToStart,
                                              AbstractBaseTest.ALL_BY_LOC);
        locatorsToDiscover = toLocatorsToDiscover(initLookupsToStart,
                                                  AbstractBaseTest.ALL_BY_LOC);
    }//end setup

}//end class LocsMulticastMonitorStop


