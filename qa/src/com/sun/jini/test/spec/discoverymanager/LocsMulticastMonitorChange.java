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
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * handles, in a manner consistent with the specification, the changed
 * events that occur as a result of the replacement of one or more of the
 * member groups of discovered lookup services.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover all of the
 *         lookups by only locator discovery
 *    <li> one instance of DiscoveryChangeListener registered with the lookup
 *         discovery manager
 *    <li> after all of the lookup services are successfully discovered, 
 *         every other element of each lookup's set of member groups is
 *         replaced with a new element
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of changed events (which should
 * be zero since the lookup discovery manager is configured to discover the
 * lookups by only locator discovery), with the expected contents.
 *
 * Related bug ids: 4292957
 */
public class LocsMulticastMonitorChange extends MulticastMonitorChange {
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

}//end class LocsMulticastMonitorChange

