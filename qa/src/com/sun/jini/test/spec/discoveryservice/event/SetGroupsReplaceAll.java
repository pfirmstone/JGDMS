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
import com.sun.jini.qa.harness.Test;

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscoveryManager</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon re-configuring the <code>LookupDiscoveryManager</code> utility to
 * discover a new set of member groups, containing none of the groups
 * with which it was previously configured, that utility will send a
 * discarded event for each previously discovered lookup service that
 * does not belong to any of the groups from the new set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each with a finite set of member groups
 *   <li> one instance of LookupDiscoveryManager configured to discover 
 *        some of the lookup services through only group discovery, some
 *        through only locator discovery, and some through both group and
 *        locator discovery
 *   <li> one DiscoveryListener registered with that LookupDiscoveryManager
 *   <li> after discovery, the LookupDiscoveryManager utility is re-configured
 *        with a new set of groups to discover; a set that contains none
 *        of the groups with which it was originally configured
 * </ul><p>
 * 
 * If the <code>LookupDiscoveryManager</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discarded event
 * will be sent for each lookup service that does not belong to any of the
 * groups with which the <code>LookupDiscoveryManager</code> utility was 
 * re-configured. Additionally, each discarded event received will accurately
 * reflect the new set of member groups.
 */
public class SetGroupsReplaceAll extends SetGroupsReplaceSome {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        groupsMap = getPassiveCommDiscardMap(getUseOnlyGroupDiscovery());
        return this;
    }//end construct

} //end class SetGroupsReplaceAll


