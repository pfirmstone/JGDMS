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
 * operates in a manner consistent with the specification.
 * <p>
 * In particular, this class verifies that the lookup discovery manager can
 * successfully employ the multicast discovery protocol on behalf of a client
 * to discover a number of pre-determined lookup services using ONLY group
 * discovery when the lookup discovery manager is configured to discover
 * ALL_GROUPS; and then for each discovered lookup service, send to the
 * client's listener, the appropriate discovery event with the appropriate
 * contents.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to use only group discovery
 *         to discover ALL_GROUPS
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 * </ul><p>
 * 
 * If the lookup discovery manager utility functions as specified, then
 * the client's listener will receive the expected number of discovery events,
 * with the expected contents.
 */
public class GroupsDiscoveredAll extends DiscoveredAll {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     *
     *  Retrieves additional configuration values. 
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        locatorsToDiscover = toLocatorsToDiscover
                                              (getInitLookupsToStart(),
                                               AbstractBaseTest.ALL_BY_GROUP);
        return this;
    }//end construct

}//end class GroupsDiscoveredAll

