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
 * With respect to the <code>discard</code> method, this class verifies
 * that the <code>LookupDiscoveryManager</code> utility operates in a manner
 * consistent with the specification.
 * <p>
 * In particular, this class verifies that upon actively discarding one or
 * more un-reachable lookup services, the lookup discovery manager sends
 * a discarded event, with the appropriate contents, for each discarded
 * lookup service.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover all of the
 *         lookups by only group discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, each lookup service is destroyed, rendering it
 *         unreachable
 *    <li> each un-reachable lookup service is queried for its locator and,
 *         if it is found to be un-reachable, is discarded from the lookup
 *         discovery manager
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded events, with the
 * expected contents.
 * 
 * @see com.sun.jini.test.spec.discoverymanager.DiscardUnreachable
 */
public class GroupsDiscardUnreachable extends DiscardUnreachable{

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        groupsToDiscover = toGroupsToDiscover(initLookupsToStart,
                                              AbstractBaseTest.ALL_BY_GROUP);
        locatorsToDiscover = toLocatorsToDiscover
                                             (initLookupsToStart,
                                              AbstractBaseTest.ALL_BY_GROUP);
    }//end setup

}//end class GroupsDiscardUnreachable

