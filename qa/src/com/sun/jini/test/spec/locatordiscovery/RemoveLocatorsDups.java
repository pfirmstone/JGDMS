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
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import net.jini.core.discovery.LookupLocator;

import java.util.ArrayList;

/**
 * With respect to the <code>removeLocators</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that when the parameter input to the <code>removeLocators</code> method
 * contains at least one element that is a duplicate of another element in
 * the input set, the <code>LookupLocatorDiscovery</code> utility operates
 * as if <code>removeLocators</code> was invoked with the duplicates removed
 * from the input set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, removeLocators is invoked - with a set of locators
 *        containing duplicates - to remove some of the locators with which
 *        the lookup locator discovery utility was originally configured to
 *        discover
 * </ul><p>
  *
 */
public class RemoveLocatorsDups extends RemoveLocatorsSome {

    protected LookupLocator[] dupLocs = new LookupLocator[0];

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Create a set of locators to remove that contain duplicates */
        dupLocs = new LookupLocator[2*locsToRemove.length];
        int len1 = locsToRemove.length;
        int len2 = 2*len1;
        for(int i=0;i<len1;i++) {
            dupLocs[i] = locsToRemove[i];
        }//end loop
        for(int i=len1;i<len2;i++) {
            dupLocs[i] = locsToRemove[i-len1];
        }//end loop
        locsToRemove = dupLocs;
        return this;
    }//end construct

}//end class RemoveLocatorsDups

