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

package com.sun.jini.test.spec.discoveryservice.event;

import java.util.Map;
import com.sun.jini.qa.harness.QAConfig;

/**
 * With respect to the <code>removeGroups</code> method, this class verifies
 * that the lookup discovery service operates in a manner consistent with the
 * specification. In particular, this class verifies that upon re-configuring
 * the lookup discovery service to discover a new set of member groups for
 * each of its registrations, containing none of the groups with which
 * it was previously configured, that service will send to each registration's
 * listener a discarded event for each previously discovered lookup service
 * that does not belong to any of the groups from the new set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registration with the lookup discovery service
 *   <li> each registration with the lookup discovery service requests that
 *        some of the lookup services be discovered through only group
 *        discovery, some through only locator discovery, and some through
 *        both group and locator discovery
 *   <li> after discovery, using the removeGroups method of each registration,
 *        the lookup discovery service is re-configured with a new set of
 *        groups to discover; a set that contains none of the groups
 *        with which it was originally configured
 * </ul><p>
 * 
 * If the lookup discovery service utility functions as specified, then
 * for each discarded lookup service a <code>RemoteDiscoveryEvent</code>
 * indicating a discarded event will be sent to each registration's listener.
 */
public class RemoveGroupsAll extends RemoveGroupsSome {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        groupsMap = getPassiveCommDiscardMap(useOnlyGroupDiscovery);
    }//end setup

} //end class RemoveGroupsAll


