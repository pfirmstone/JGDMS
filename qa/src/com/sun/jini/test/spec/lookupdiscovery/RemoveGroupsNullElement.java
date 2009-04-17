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
 * With respect to the <code>removeGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that invoking the <code>removeGroups</code> method with an input array that
 * contains at least one <code>null</code> element will result in a
 * <code>NullPointerException</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> an instance of the lookup discovery utility initially configured to
 *         discover a finite set of groups (not NO_GROUPS and not ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then invoking
 * the <code>removeGroups</code> method with an input array that contains
 * at least at least one <code>null</code> element will result in a
 * <code>NullPointerException</code>.
 */
public class RemoveGroupsNullElement extends AddGroupsNullElement {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	/* Change the groups to remove to include some of the groups the
	 * lookup discovery utility is currently configured to discover
	 */
	for(int i=0;i<nullGroups.length;i++) {
	    if( (nullGroups[i] != null) && (i < configGroups.length)) {
		nullGroups[i] = new String(configGroups[i]);
	    }//endif
	}//end loop
	NPEStr = new String("NullPointerException on group removal "
			    +"as expected");
	doStr = new String("removing groups from lookup discovery --");
	doFlag = DO_REMOVE;
    }//end setup

}//end class RemoveGroupsNullElement

