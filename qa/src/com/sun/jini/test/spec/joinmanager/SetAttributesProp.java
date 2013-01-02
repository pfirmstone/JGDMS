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
 * the join manager propagates the appropriate set of new attributes
 * (no duplicates) to to each lookup service with which the associated
 * test service is registered.
 * 
 */
public class SetAttributesProp extends RegisterAttributes {

    protected Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attributes with which to replace the
     *          join manager's current set of attributes
     *     <li> constructs the set of attributes to expect after adding
     *          the new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        newServiceAttrs = addAttrsDup1DupAll(serviceAttrs,newServiceAttrs);
        expectedAttrs   = removeDups(newServiceAttrs);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the appropriate attributes (no duplicates) are
     *   propagated to each lookup service with which the associated test
     *   service is registered.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "replacing current attributes with "
                          +"new attributes through join manager ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        joinMgrSrvcID.setAttributes(newServiceAttrs);
        logger.log(Level.FINE, "verifying new attributes were "
                                    +"propagated to each lookup service ...");
        verifyPropagation(expectedAttrs, getnSecsJoin());
        logger.log(Level.FINE, "new attributes successfully propagated to "
                          +"all "+curLookupListSize("SetAttributesProp.run")
                          +" lookup service(s)");
    }//end run

} //end class SetAttributesProp


