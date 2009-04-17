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

import java.util.logging.Level;

import net.jini.discovery.DiscoveryGroupManagement;

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that when the parameter input to the <code>setGroups</code> method is
 * <code>null</code> (<code>ALL_GROUPS</code>), attempts will be made to
 * discover all lookup services located within the current multicast radius,
 * regardless of group membership.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite
 *        set of member groups, and each started during setup, before the
 *        test begins execution
 *   <li> one instance of the lookup discovery utility initially configured
 *        to discover NO_GROUPS
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 *   <li> after the lookup discovery utility is constructed, setGroups is
 *        invoked to re-configure the lookup discovery utility to discover
 *        lookup services belonging to any group (even NO_GROUPS) that are
 *        within range
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the client's
 * listener will receive the expected discovered events, with the expected
 * contents.
 */
public class SetGroupsAllGroups extends Discovered {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        groupsToDiscover = DiscoveryGroupManagement.ALL_GROUPS;
    }//end setup

}//end class SetGroupsAllGroups

