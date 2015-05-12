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

package org.apache.river.test.spec.joinmanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification.  In particular,
 * this class verifies that when <code>setAttributes</code> is invoked with
 * a set of attributes that either duplicate elements with which the join
 * manager is already configured, or that duplicate elements in the set
 * itself (or both), the join manager is re-configured with the appropriate
 * set of attributes.
 * 
 */
public class SetAttributesDup extends SetAttributes {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attributes -- containing the appropriate
     *          duplicates -- to input to the <code>setAttributes</code> 
     *          method
     *     <li> constructs the set of attributes to expect after replacing
     *          the current set with a new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Create an array that contains 1 element from the set of attributes
         * with which the join manager is currently configured, the new
         * set of attributes to be added to the initial set, and duplicates
         * of the elements from the set to be added.
         */
        newServiceAttrs = addAttrsDup1DupAll(serviceAttrs,newServiceAttrs);
        expectedAttrs   = removeDups(newServiceAttrs);
        return this;
    }//end construct

} //end class SetAttributesDup


