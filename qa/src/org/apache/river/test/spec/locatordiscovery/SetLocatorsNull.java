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

package org.apache.river.test.spec.locatordiscovery;

import java.util.logging.Level;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code>
 * utility operates in a manner consistent with the specification.
 * In particular, this class verifies that when the parameter input to
 * the <code>setLocators</code> method is <code>null</code>, a
 * <code>NullPointerException</code> is thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> zero lookup services
 *   <li> an instance of the lookup locator discovery utility that is
 *        constructed using an array of locators consistent with the
 *        the test's configuration
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified,
 * then when the <code>setLocators</code> method is invoked with a
 * <code>null</code> parameter, a <code>NullPointerException</code>
 * will occur.
 *
 */
public class SetLocatorsNull extends SetLocatorsNullElement {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        nullLocs = null;
        return this;
    }//end construct

}//end class SetLocatorsNull

