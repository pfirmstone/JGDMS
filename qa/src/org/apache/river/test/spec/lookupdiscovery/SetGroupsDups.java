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

import java.util.logging.Level;

import java.util.ArrayList;

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that when the parameter input to the <code>setGroups</code> method
 * contains at least one element that is a duplicate of another element in
 * the input set, the <code>LookupDiscovery</code> utility operates
 * as if the <code>setGroups</code> method was invoked with the duplicates
 * removed from the input set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite
 *        set of member groups, and each started during construct, before the
 *        test begins execution
 *   <li> one instance of the lookup discovery utility configured to discover
 *        the set of groups whose elements are the member groups of the
 *        initial lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 *   <li> after discovery, setGroups is invoked using an array in which
 *        at least one element is a duplicate of at least one other element
 *        in the set, so as to replace some (but not all) of the groups with
 *        which the lookup discovery utility was originally configured to
 *        discover; so that the new groups the lookup discovery utility
 *        is configured to discover include only some of the of the groups
 *        with which it was originally configured
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then after
 * invoking the <code>setGroups</code> method with a set of groups
 * containing duplicate elements, the listener will receive the expected
 * discovery events, with the expected contents.
 */
public class SetGroupsDups extends SetGroupsReplaceSome {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	int len1 = newGroupsToDiscover.length;
	int len2 = 2*len1;
	ArrayList dupGroupsList = new ArrayList(len2);
	for(int i=0;i<len1;i++) {
	    dupGroupsList.add( i,new String(newGroupsToDiscover[i]) );
	}//end loop
	for(int i=len1;i<len2;i++) {
	    dupGroupsList.add( i,new String(newGroupsToDiscover[i-len1]) );
	}//end loop
	newGroupsToDiscover = (String[])(dupGroupsList).toArray
	    (new String[dupGroupsList.size()]);
        return this;
    }//end construct

}//end class SetGroupsDups

