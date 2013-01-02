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

import java.util.logging.Level;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * With respect to the <code>removeLocators</code> method, this class
 * verifies that the <code>LookupLocatorDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking the <code>removeLocators</code> method to
 * remove ALL of the locators with which the lookup locator discovery utility
 * was previously configured to discover, that utility will send discarded
 * events referencing the previously discovered lookup services whose locators
 * correspond to the locators that were removed (that is, discarded events
 * will be sent for all of the previously discovered lookup services).
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, the LookupLocatorDiscovery utility is re-configured
 *        to discover a new set of locators; a set that contains none of
 *        the locators with which it was originally configured
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive the expected discarded events, having the expected
 * contents.
 */
/**
 * With respect to the <code>removeLocators</code> method, this class
 * verifies that the <code>LookupLocatorDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking the <code>removeLocators</code> method to
 * remove ALL of the locators with which it was previoulsy configured to
 * discover, that utility will send discarded events referencing the
 * previously discovered lookup services whose locators correspond to the
 * locators that were removed.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, removeLocators is invoked to remove ALL of the
 *        locators with which the lookup locator discovery utility was
 *        originally configured to discover
 * </ul><p>
  *
 */

public class RemoveLocatorsAll extends RemoveLocatorsSome {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        changeAll = true;
        super.construct(sysConfig);
        return this;
    }//end construct

}//end class RemoveLocatorsAll

