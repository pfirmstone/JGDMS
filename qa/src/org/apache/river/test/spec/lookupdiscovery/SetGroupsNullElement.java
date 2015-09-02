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

/**
 * With respect to the <code>setGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that invoking the <code>setGroups</code> method with an input array that
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
 * the <code>setGroups</code> method with an input array that contains
 * at least at least one <code>null</code> element will result in a
 * <code>NullPointerException</code>.
 */
public class SetGroupsNullElement extends AddGroupsNullElement {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	NPEStr = new String("NullPointerException on group replacement "
			    +"as expected");
	doStr = new String
	    ("setting groups in lookup discovery to --");
	doFlag = DO_SET;
        return this;
    }//end construct

}//end class SetGroupsNullElement

