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

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>setGroups</code> method to re-configure
 * the <code>LookupDiscovery</code> utility to discover a new set of
 * member groups which replaces the current set of member groups to discover,
 * and which contains NONE of the groups with which it was previously
 * configured, that utility will send discarded events referencing the
 * previously discovered lookup services that do not belong to any of the
 * new member groups (that is, discarded events will be sent for ALL of
 * the previously discovered lookup services).
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of LookupDiscovery configured to discover the union
 *        of the member groups to which each lookup belongs 
 *   <li> one DiscoveryListener registered with that LookupDiscovery
 *   <li> after discovery, the LookupDiscovery utility is re-configured
 *        to discover a new set of member groups; a set that contains none
 *        of the groups with which it was originally configured
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the listener
 * will receive the expected discarded events, having the expected contents.
 */
public class SetGroupsReplaceAll extends SetGroupsReplaceSome {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        changeAll = true;//replace ALL the groups to discover with new groups
        super.setup(sysConfig);
    }//end setup

}//end class SetGroupsReplaceAll


