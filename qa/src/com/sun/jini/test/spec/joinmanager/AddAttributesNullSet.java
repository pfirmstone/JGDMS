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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>null</code> is input to any
 * version of <code>addAttributes</code>, a <code>NullPointerException</code>
 * is thrown.
 */
public class AddAttributesNullSet extends AddAttributesNullElement {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step (if any)
     *     <li> sets to <code>null</code> the set attributes to input to
     *          the addAttributes method
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        newServiceAttrs = null;
        return this;
    }//end construct

} //end class AddAttributesNullSet


