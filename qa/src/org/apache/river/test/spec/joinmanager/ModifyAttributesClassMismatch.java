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
 * with a set of attribute templates and a set of modification attributes
 * in which at least one element in the set of modification attributes is
 * neither the same class as, nor a superclass of, the corresponding
 * element in the set of templates, an <code>IllegalArgumentException</code>
 * is thrown.
 */
public class ModifyAttributesClassMismatch 
                                       extends ModifyAttributesLengthMismatch
{
    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of modification attribute such that it
     *          contains at least one element which is neither the same
     *          class as, nor a subclass of, the corresponding element in
     *          the set of attribute templates
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Construct new attributes with different length than templates */
        int chngIndx = newServiceAttrs.length/2;//don't just pick first element
        newAttrs = new Entry[newServiceAttrs.length];
        for(int i=0;i<newAttrs.length;i++) {
            if(i == chngIndx) {
                newAttrs[i] = new TestServiceStringAttr("Bad Attribute");
            } else {
                newAttrs[i] = new TestServiceIntAttr
                 (( ((TestServiceIntAttr)newServiceAttrs[i]).val).intValue());
            }//endif
        }//end loop
        return this;
    }//end construct

} //end class ModifyAttributesClassMismatch


