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
import com.sun.jini.qa.harness.Test;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that if an empty set (NO_GROUPS) is passed to the
 * constructor, discovery will not be started until the <code>setGroups</code>
 * method is called with either <code>null</code> (ALL_GROUPS), or a
 * non-empty set.
 * <p>
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services, each belonging to a finite
 *        set of member groups, started during construct
 *   <li> an instance of the lookup discovery utility created by passing
 *        and empty String array (DiscoveryGroupManagement.NO_GROUPS) to
 *        the constructor
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 *   <li> after creating the lookup discovery utility, <code>setGroups</code>
 *        is invoked with a non-null, non-empty array of group names
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the
 * listener will receive no events until the <code>setGroups</code> method
 * is called to re-configure the lookup discovery utility to discover
 * the lookup services started during construct.
 */
public class DiscoveryBeginsOnSetGroupsAfterEmpty
                                  extends DiscoveryBeginsOnAddGroupsAfterEmpty
{
   /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        addGroups = false;
        return this;
    }//end construct

}//end class DiscoveryBeginsOnSetGroupsAfterEmpty

