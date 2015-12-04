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

import java.util.ArrayList;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>modifyAttributes</code> is invoked
 * to delete a single element of the initial set of attributes, the
 * the join manager is re-configured with the appropriate set of attributes.
 */
public class ModifyAttributesDeleteOne extends ModifyAttributesOne {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attribute templates to use to indicate
     *          that only one of the current attributes should be selected
     *          for deletion
     *     <li> constructs the set of attributes to expect after deleting
     *          one of the elements of the current attribute set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Construct the attribute set that indicates deletion is requested */
        newServiceAttrs = new Entry[1];
        newServiceAttrs[0] = null;
        /* Construct attributes to expect after deletion is complete */
        ArrayList tmpList = new ArrayList(1);
        for(int i=0;i<expectedAttrs.length;i++) {
            if(i == chngIndx) continue;//remove the element to be deleted
            tmpList.add(expectedAttrs[i]);
        }//end loop
        expectedAttrs = (Entry[])(tmpList).toArray(new Entry[tmpList.size()]);
        return this;
    }//end construct

} //end class ModifyAttributesDeleteOne


