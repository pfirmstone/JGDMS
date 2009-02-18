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
import com.sun.jini.test.share.AttributesUtil;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>setAttributes</code> is invoked
 * with an attribute set containing at least 1 <code>null</code> element,
 * a <code>NullPointerException</code> is thrown.
 * 
 */
public class SetAttributesNullElement extends AddAttributesNullElement {

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that a <code>NullPointerException</code> is thrown when
     *   <code>setAttributes</code> is invoked with a set of attributes
     *   that is either <code>null</code> itself, or which contains at
     *   least 1 <code>null</code> element.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "replacing attribute set with "
                       +"a new set that is either null itself, or which "
                       +"contains at least 1 null element ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        try {
            /* First version of addAttributes (no checkSC parameter) */
            joinMgrSrvcID.setAttributes(newServiceAttrs);
	    throw new TestException("no NullPointerException thrown");
        } catch(NullPointerException e) {
            logger.log(Level.FINE, "NullPointerException occurred "
                                            +"as expected");
        }
    }//end run

} //end class SetAttributesNullElement


