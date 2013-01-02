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
import com.sun.jini.qa.harness.Test;

/**
 * With respect to the <code>addDiscoveryListener</code> method, this class
 * verifies that the <code>LookupDiscoveryManager</code> utility operates in
 * a manner consistent with the specification.
 * <p>
 * In particular, this class verifies that upon adding a new instance of
 * <code>DiscoveryChangeListener</code> to a lookup discovery manager
 * configured to discover a set of lookup services through ONLY group
 * discovery, the lookup discovery manager will send a discovered
 * event - with the appropriate contents - to that listener for each lookup
 * service that satisfies the discovery criteria.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover the lookups by
 *         only group discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, a new instance of DiscoveryChangeListener is added
 *         to the lookup discovery manager utility through the invocation of
 *         the addDiscoveryListener method
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then each of the
 * client's listeners will receive the expected number of discovery events,
 * with the expected contents.
 *
 * Related bug ids: 4292957
 */
public class GroupsAddNewDiscoveryChangeListener extends
                                                AddNewDiscoveryChangeListener
{
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
        return this;
    }//end construct

}//end class GroupsAddNewDiscoveryChangeListener

