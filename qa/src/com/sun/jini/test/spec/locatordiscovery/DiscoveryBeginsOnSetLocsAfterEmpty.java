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

package com.sun.jini.test.spec.locatordiscovery;

import net.jini.core.discovery.LookupLocator;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that "if an empty set is passed to the constructor,
 * discovery will not be started until the <code<setLocators</code> method
 * is called with a non-<code>null</code>, non-empty set".
 * <p>
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more initial lookup services started during setup
 *    <li> an instance of the lookup locator discovery utility created by
 *         passing the empty set to the constructor
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive no events until the <code>setLocators</code> method
 * is called to re-configure the lookup locator discovery utility to discover
 * the lookup services started during setup.
 */
public class DiscoveryBeginsOnSetLocsAfterEmpty
                                  extends DiscoveryBeginsOnSetLocsAfterNull
{

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        locsToDiscover = new LookupLocator[0];
    }//end setup

}//end class DiscoveryBeginsOnSetLocsAfterEmpty

