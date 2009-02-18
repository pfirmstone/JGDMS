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
 * using a set of templates whose elements are each a duplicate of only
 * one of the elements in the initial set of attributes, and using a set of
 * modification attributes whose elements are all duplicates of each other,
 * the join manager is re-configured with the appropriate set of attributes.
 */
public class ModifyAttributesDup extends ModifyAttributesOne {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attribute templates containing more 
     *          than 1 element, in which each element is a duplicate of the
     *          element in the initial set that is to be modified
     *     <li> constructs the set of attributes to use to cause the
     *          intended change to the one element in the current set
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* Construct template&attribute set duplicating 1 current attribute */
        Entry tmpEntry0 = attrTmpls[0];
        Entry tmpEntry1 = newServiceAttrs[0];
        attrTmpls = new Entry[serviceAttrs.length];
        newServiceAttrs = new Entry[serviceAttrs.length];
        for(int i=0;i<serviceAttrs.length;i++) {
            attrTmpls[i] = new TestServiceIntAttr
                         (( ((TestServiceIntAttr)tmpEntry0).val).intValue());
            newServiceAttrs[i] = new TestServiceIntAttr
                         (( ((TestServiceIntAttr)tmpEntry1).val).intValue());
        }//end loop
    }//end setup

} //end class ModifyAttributesDup


