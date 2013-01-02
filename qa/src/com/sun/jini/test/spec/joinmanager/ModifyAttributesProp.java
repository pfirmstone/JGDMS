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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.AttributesUtil;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>modifyAttributes</code> is invoked
 * to change each element of the initial set of attributes to an 
 * attribute with a new value, the join manager propagates the appropriate
 * set of attributes to each lookup service with which the associated test
 * service is registered.
 * 
 */
public class ModifyAttributesProp extends RegisterAttributes {

    protected Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attribute templates to use for selecting
     *          the attributes to change. This set is constructed with at
     *          least one duplicate element, and such that each element in the
     *          initial set of attributes will be changed to a new value
     *     <li> constructs the set of attributes containing the changes to
     *          make to the initial set. This set is contstructed so that
     *          each element contains a unique value.
     *     <li> constructs the set of attributes to expect after changing
     *          the current attributes to the new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Construct both the template set and the new attribute set to be 1
         * element greater than the initial attribute set with the extra
         * element in the attribute set duplicating one of the other elements
         * in the set, and the extra element in the new attribute set not
         * duplicating any other element in that set.
         */
        Entry tmpTmpl = attrTmpls[0];
        attrTmpls = new Entry[serviceAttrs.length+1];
        newServiceAttrs = new Entry[serviceAttrs.length+1];
        expectedAttrs = new Entry[serviceAttrs.length];
        for(int i=0;i<serviceAttrs.length;i++) {
            attrTmpls[i] = new TestServiceIntAttr
                     (( ((TestServiceIntAttr)serviceAttrs[i]).val).intValue());
            newServiceAttrs[i] = new TestServiceIntAttr
            (getnAttributes()+( ((TestServiceIntAttr)attrTmpls[i]).val).intValue());
            expectedAttrs[i] = new TestServiceIntAttr
                  (( ((TestServiceIntAttr)newServiceAttrs[i]).val).intValue());
        }//end loop
        attrTmpls[attrTmpls.length-1] = new TestServiceIntAttr
                            (( ((TestServiceIntAttr)tmpTmpl).val).intValue());
        newServiceAttrs[newServiceAttrs.length-1] = new TestServiceIntAttr
      ( getnAttributes()+( ((TestServiceIntAttr)newServiceAttrs[0]).val).intValue());
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Changes the initial set of attributes to the new set, and verifies
     *   that the join manager propagates the appropriate attributes to 
     *   each lookup service with which the associated test service is
     *   registered.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "changing current attributes "
                                        +"through join manager ...");
        AttributesUtil.displayAttributeSet(attrTmpls,
                                           "atttribute templates",
                                           Level.FINE);
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        joinMgrSrvcID.modifyAttributes(attrTmpls,newServiceAttrs);


        logger.log(Level.FINE, "verifying changed attributes were "
                                  +"propagated to each lookup service ...");
        verifyPropagation(expectedAttrs, getnSecsJoin());
        logger.log(Level.FINE, "changed attributes successfully propagated to "
                          +"all "+curLookupListSize("ModifyAttributesProp.run")
                          +" lookup service(s)");
    }//end run

}//end class ModifyAttributesProp


