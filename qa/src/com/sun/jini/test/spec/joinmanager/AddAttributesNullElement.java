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
 * this class verifies that when the parameter input to any version of
 * <code>addAttributes</code> contains at least 1 <code>null</code> element,
 * a <code>NullPointerException</code> is thrown.
 * 
 */
public class AddAttributesNullElement extends GetAttributes {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step (if any)
     *     <li> constructs the set of attributes -- containing at least 1
     *          null element -- to input to the addAttributes method
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        if(newServiceAttrs == null) {
            newServiceAttrs = new Entry[3];
            for(int i=0;i<getnAttributes();i++) {
                newServiceAttrs[i] = new TestServiceIntAttr
                                       (SERVICE_BASE_VALUE + getnAttributes() + i);
            }//end loop
        }//endif
        if(newServiceAttrs.length > 1) {
            newServiceAttrs[1] = null;
        } else {
            newServiceAttrs[0] = null;
        }//endif
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that a <code>NullPointerException</code> is thrown when
     *   any version of the <code>addAttributes</code> method is invoked
     *   with a set of attributes that is either <code>null</code> itself,
     *   or which contains at least 1 <code>null</code> element.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "adding attribute set that is "
                       +"either null or contains at least 1 null element ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        try {
            /* First version of addAttributes (no checkSC parameter) */
            joinMgrSrvcID.addAttributes(newServiceAttrs);
        } catch(NullPointerException e) {
            logger.log(Level.FINE, "NullPointerException occurred "
                                            +"on first attempt as expected");
            /* Version of addAttributes with checkSC parameter */
            try {
                joinMgrSrvcID.addAttributes(newServiceAttrs,true);
            } catch(NullPointerException e1) {
                logger.log(Level.FINE, "NullPointerException "
                                                +"occurred on second attempt "
                                                +"as expected");
                return;
            }
        }
        String errStr = new String("no NullPointerException");
        logger.log(Level.FINE, errStr);
        throw new TestException(errStr);
    }//end run

} //end class AddAttributesNullElement


