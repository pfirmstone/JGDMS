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
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * monitors, in a manner consistent with the specification, the mulitcast
 * announcements received from lookup services it has discovered.
 * <p>
 * In particular, this test verifies that when the member groups of a
 * discovered lookup service are changed to a set containing NONE of the
 * lookup service's original member groups, if the lookup discovery manager
 * is configured to discover ALL_GROUPS, then the lookup discovery manager
 * will send a changed event (rather than a discarded event) containing
 * the appropriate contents.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover ALL_GROUPS,
 *         and configured to discover none of the lookups by locator discovery
 *    <li> one instance of DiscoveryChangeListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, each set of member groups is completely replaced
 *         with a new set
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of changed events, with the
 * expected contents.
 *
 * Related bug ids: 4292957
 * 
 * @see org.apache.river.test.spec.discoverymanager.MulticastMonitorReplaceAll
 */
public class GroupsMulticastMonitorReplaceAll
                                          extends MulticastMonitorReplaceAll
{
    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        locatorsToDiscover = toLocatorsToDiscover
                                              (getInitLookupsToStart(),
                                               AbstractBaseTest.ALL_BY_GROUP);
        return this;
    }//end construct

}//end class GroupsMulticastMonitorReplaceAll

