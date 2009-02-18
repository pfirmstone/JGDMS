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
 * With respect to the <code>removeGroups</code> method, this class
 * verifies that the <code>LookupDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking the <code>removeGroups</code> method to
 * remove ALL of the member groups with which the lookup discovery utility
 * was previously configured to discover, that utility will send discarded
 * events referencing the previously discovered lookup services whose member
 * groups equal none of the groups with which the lookup discovery utility
 * is configured to discover after the call to <code>removeGroups</code>
 * (that is, discarded events will be sent for all of the previously
 * discovered lookup services).
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite
 *        set of member groups, and each started during setup, before the
 *        test begins execution
 *   <li> one instance of the lookup discovery utility configured to discover
 *        the set of groups whose elements are the member groups of the
 *        initial lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 *   <li> after discovery, removeGroups is invoked to remove all of the
 *        groups with which the lookup discovery utility was originally
 *        configured to discover
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the listener
 * will receive the expected discarded events, having the expected contents.
 */
public class RemoveGroupsRemoveAll extends RemoveGroupsRemoveSome {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        changeAll = true;
        super.setup(sysConfig);
    }//end setup

}//end class RemoveGroupsRemoveAll
