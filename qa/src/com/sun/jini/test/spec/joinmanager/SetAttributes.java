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
 * this class verifies that when <code>setAttributes</code> is invoked,
 * the join manager is re-configured with the appropriate set of attributes.
 * 
 */
public class SetAttributes extends GetAttributes {

    protected Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> verifies that the set of attributes with which to replace
     *          the current attributes is not equivalent to the current
     *          set (so that replacement can be accurately verified)
     *     <li> constructs the set of attributes to expect after replacing
     *          the current set with a new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        if(AttributesUtil.compareAttributeSets(serviceAttrs,newServiceAttrs, Level.OFF)) {
            throw new TestException("newServiceAttrs is identical to "
                                  +"current serviceAttrs ... test is invalid");
        }//endif
        expectedAttrs = removeDups(newServiceAttrs);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the join manager is re-configured correctly after
     *   the <code>setAttributes</code> method is invoked.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "replacing current attributes with "
                          +"new attributes through join manager ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        joinMgrSrvcID.setAttributes(newServiceAttrs);
        Entry[] newJoinAttrs = joinMgrSrvcID.getAttributes();
        logger.log(Level.FINE, "comparing the replaced attribute "
                                        +"set from the join manager to the "
                                        +"expected set of attributes ...");
        if (!AttributesUtil.compareAttributeSets(expectedAttrs,
                                                 newJoinAttrs,
                                                 Level.FINE))
	{
            throw new TestException("new attributes from join manager not "
                                   +"equal to service attributes");
        }
        logger.log(Level.FINE, "attributes from join manager equal "
                          +"expected set of attributes");
    }//end run

} //end class SetAttributes


