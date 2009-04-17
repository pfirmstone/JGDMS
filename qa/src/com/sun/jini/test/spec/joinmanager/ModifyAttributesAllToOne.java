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

import net.jini.core.entry.Entry;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>modifyAttributes</code> is invoked
 * to change all elements of the initial set of attributes to a single
 * attribute with a new value, the join manager is re-configured with the
 * appropriate set of attributes.
 */
public class ModifyAttributesAllToOne extends ModifyAttributesOne {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attribute templates to use to indicate
     *          that all of the current attributes should be selected for
     *          modification
     *     <li> constructs the set of attributes to expect after changing
     *          all of the elements of the current attribute set to a single
     *          attribute
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* Construct a template that will match all the current attributes */
        attrTmpls = new Entry[1];
        attrTmpls[0] = new TestServiceIntAttr();//null field matches all
        /* Construct attributes to expect after changing current attributes */
        expectedAttrs = new Entry[1];
        expectedAttrs[0] = new TestServiceIntAttr
                  (( ((TestServiceIntAttr)newServiceAttrs[0]).val).intValue());
    }//end setup

} //end class ModifyAttributesAllToOne


