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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the method <code>addAttributes</code>
 * is invoked with a set of attributes that either duplicate elements 
 * with which the join manager is already configured, or that duplicate
 * elements in the set itself (or both), the join manager is re-configured
 * with the appropriate set of attributes (duplicates are removed).
 */
public class AddAttributesDup extends AddAttributes {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) (where N may be 0) whose member
     *          groups are finite and unique relative to the member groups
     *          of all other lookup services running within the same multicast
     *          radius of the new lookup services
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step (if any)
     *     <li> constructs the set of attributes to expect after adding
     *          a new set
     *     <li> constructs the set of attributes -- containing the appropriate
     *          duplicates -- to input to the <code>addAttributes</code> 
     *          method
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* Create an array that contains elements from the set of attributes
         * with which the join manager is currently configured, the new
         * set of attributes to be added to the initial set, and duplicates
         * of the elements from the set to be added.
         */
        newServiceAttrs = addAttrsWithDups(serviceAttrs,newServiceAttrs);
    }//end setup

} //end class AddAttributesDup


