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
import org.apache.river.test.share.AttributesUtil;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>modifyAttributes</code> is invoked
 * with a set of attribute templates and a set of modification attributes
 * whose lengths do not match, an <code>IllegalArgumentException</code>
 * is thrown.
 * 
 */
public class ModifyAttributesLengthMismatch extends ModifyAttributes {

    protected Entry[] newAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of modification attribute such that it
     *          contains a different number of elements than the set of 
     *          attribute templates
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Construct new attributes with different length than templates */
        newAttrs = new Entry[newServiceAttrs.length-1];
        for(int i=0;i<newAttrs.length;i++) {
            newAttrs[i] = new TestServiceIntAttr
                 (( ((TestServiceIntAttr)newServiceAttrs[i]).val).intValue());
        }//end loop
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that when <code>modifyAttributes</code> is invoked with a
     *   set of attribute templates and a set of modification attributes
     *   whose lengths do not match, an <code>IllegalArgumentException</code>
     *   is thrown.
     */
    public void run() throws Exception {
        super.run();

        /* Change the length of newServiceAttrs */
        newServiceAttrs = newAttrs;
        logger.log(Level.FINE, "changing current attributes "
                                        +"through join manager ...");
        AttributesUtil.displayAttributeSet(attrTmpls,
                                           "atttribute templates",
                                           Level.FINE);
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        try {
            /* First version of modifyAttributes (no checkSC parameter) */
            joinMgrSrvcID.modifyAttributes(attrTmpls,newServiceAttrs);
        } catch(IllegalArgumentException e) {
            logger.log(Level.FINE, "IllegalArgumentException "
                              +"occurred on first attempt as expected");
            /* Version of addAttributes with checkSC parameter */
            try {
                joinMgrSrvcID.modifyAttributes(attrTmpls,newServiceAttrs,true);
            } catch(IllegalArgumentException e1) {
                logger.log(Level.FINE, "IllegalArgumentException "
                                                +"occurred on second attempt "
                                                +"as expected");
                return;
            }
        }
        String errStr = new String("no IllegalArgumentException");
        logger.log(Level.FINE, errStr);
        throw new TestException(errStr);
    }//end run

} //end class ModifyAttributesLengthMismatch


