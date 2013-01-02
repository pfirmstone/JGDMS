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

package com.sun.jini.test.impl.fiddler.joinadmin;

import com.sun.jini.test.share.LocatorsUtil;
import net.jini.core.discovery.LookupLocator;
import java.net.MalformedURLException;
import com.sun.jini.qa.harness.AbstractServiceAdmin;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully remove a set of locators from the set of locators with 
 * which it has been configured to join.
 *
 * This test attempts to remove a non-empty, non-unique set of locators
 * from the non-empty set of locators with which the service is currently
 * configured.
 * 
 * @see <code>net.jini.discovery.DiscoveryLocatorManagement</code> 
 */
public class RemoveLookupLocatorsDups extends RemoveLookupLocators {

    /** Constructs and returns the set of locators to remove  (overrides
     *  the parent class' version of this method)
     */
    LookupLocator[] getTestLocatorSet() throws MalformedURLException {
        /* First retrieve a sub-set of the initial locators */
	AbstractServiceAdmin admin = 
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        LookupLocator[] subsetCurLocators = LocatorsUtil.getSubset(admin.getLocators());
        /* Next, use the above sub-set to construct a set with duplicates */
        return (LocatorsUtil.getLocatorsWithDups(subsetCurLocators));
    }
}


