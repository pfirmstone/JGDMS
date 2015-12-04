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

import net.jini.core.entry.Entry;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>modifyAttributes</code> is invoked
 * to delete all elements of the initial set of attributes, the join manager
 * is re-configured with the appropriate set of attributes.
 */
public class ModifyAttributesDeleteAll extends ModifyAttributesAllToOne {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attribute templates to use to indicate
     *          that all of the current attributes should be selceted for
     *          deletion
     *     <li> constructs the set of attributes to expect after deleting
     *          all of the elements of the current attribute set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Construct the attribute set that indicates deletion is requested */
        newServiceAttrs = new Entry[1];
        newServiceAttrs[0] = null;
        /* Construct attributes to expect after deletion is complete */
        expectedAttrs = new Entry[0];
        return this;
    }//end construct

} //end class ModifyAttributesDeleteAll


