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

package org.apache.river.test.spec.lookupdiscovery;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.discovery.DiscoveryGroupManagement;

/**
 * With respect to the <code>getGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that the <code>getGroups</code> method returns <code>null</code>)
 * (<code>ALL_GROUPS</code>) when the lookup discovery utility is configured
 * to discover lookup services belonging to any group (including
 * (<code>NO_GROUPS</code>).
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is initially configured to discover
 *         lookup services that belong to any group (even NO_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then invoking
 * the <code>getGroups</code> method should return <code>null</code>)
 * (<code>ALL_GROUPS</code>).
 */
public class GetGroupsNoGroups extends GetGroups {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	groupsToDiscover = DiscoveryGroupManagement.NO_GROUPS;
        return this;
    }//end construct

}//end class GetGroupsNoGroups

