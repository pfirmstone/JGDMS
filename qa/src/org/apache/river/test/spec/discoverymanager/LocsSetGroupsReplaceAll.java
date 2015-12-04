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
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscoveryManager</code> utility operates in a manner
 * consistent with the specification.
 * <p>
 * In particular, this class verifies that when the lookup discovery manager
 * is configured to discover a set of lookup services through only locator
 * discovery, and the <code>setGroups</code> method is invoked to reconfigure
 * the lookup discovery manager to use group discovery to discover groups
 * in which NONE of the lookups are members, the lookup discovery manager will
 * send to the client's listener, the appropriate events with the appropriate
 * contents.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover all of the
 *         lookups by only locator discovery
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery manager
 *    <li> after discovery, setGroups is invoked on the lookup discovery
 *         manager to reconfigure the lookup discovery manager to employ
 *         group discovery to discover member groups in which NONE of the
 *         lookups are members
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of discarded events, with the
 * expected contents.
 */
public class LocsSetGroupsReplaceAll extends SetGroupsReplaceAll {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        groupsToDiscover = toGroupsToDiscover(getInitLookupsToStart(),
                                              AbstractBaseTest.ALL_BY_LOC);
        locatorsToDiscover = toLocatorsToDiscover(getInitLookupsToStart(),
                                                  AbstractBaseTest.ALL_BY_LOC);
        return this;
    }//end construct

}//end class LocsSetGroupsReplaceAll
