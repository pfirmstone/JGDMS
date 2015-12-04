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
 * to change each element of the initial set of attributes to an 
 * attribute with a new value, the join manager is re-configured with the
 * appropriate set of attributes.
 * 
 */
public class ModifyAttributes extends GetAttributes {

    protected Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attributes to expect after changing
     *          the current attributes to a new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        expectedAttrs = newServiceAttrs;
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the join manager is re-configured correctly after
     *   the <code>modifyAttributes</code> method is invoked.
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
        Entry[] newJoinAttrs = joinMgrSrvcID.getAttributes();
        logger.log(Level.FINE, "comparing the changed attribute "
                                        +"set from the join manager to the "
                                        +"expected set of attributes ...");
        AttributesUtil.displayAttributeSet(expectedAttrs,
                                           "expectedAttrs",
                                           Level.FINE);
        AttributesUtil.displayAttributeSet(newJoinAttrs,
                                           "newJoinAttrs",
                                           Level.FINE);
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

} //end class ModifyAttributes


