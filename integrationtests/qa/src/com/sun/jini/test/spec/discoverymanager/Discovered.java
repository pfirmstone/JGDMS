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

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.core.discovery.LookupLocator;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * operates in a manner consistent with the specification.
 * <p>
 * In particular, this class verifies that the lookup discovery manager can
 * successfully employ both the multicast and unicast discovery protocols on
 * behalf of a client to discover a number of pre-determined lookup services
 * when the lookup discovery manager is configured to discover those lookup
 * services through a MIX of group discovery and locator discovery; and then
 * for each discovered lookup service, send to the client's listener, the
 * appropriate discovery event with the appropriate contents.
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
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discovered events, with the
 * expected contents.
 */
public class Discovered extends AbstractBaseTest {

    protected String[] groupsToDiscover = DiscoveryGroupManagement.NO_GROUPS;
    protected LookupLocator[] locatorsToDiscover = new LookupLocator[0];

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     *
     *  Retrieves additional configuration values, and then
     *  <p><ul>
     *    <li> creates N lookup services having locator L0i, and belonging
     *         to groups {G0i,G1i,G2i}, where i = 0 ... N
     *    <li> creates a lookup discovery manager initially configured
     *         to discover NO_GROUPS and NO_LOCATORS
     *    <li> constructs the set of groups and locators with which to
     *         configure the lookup discovery manager to discover
     *  </ul>
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        groupsToDiscover = toGroupsToDiscover(initLookupsToStart,
                                              AbstractBaseTest.MIX);
        locatorsToDiscover = toLocatorsToDiscover(initLookupsToStart,
                                                  AbstractBaseTest.MIX);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> reconfigures the lookup discovery manager to discover some of
     *         the lookups by only group discovery, some by only locator
     *         discovery, and some by both group and locator discovery
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> verifies that the lookup discovery manager utility under test
     *         sends the expected events, with the expected contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        doDiscovery(initLookupsToStart,discoveryMgr,mainListener,
                    locatorsToDiscover,groupsToDiscover);
    }//end run

}//end class Discovered

