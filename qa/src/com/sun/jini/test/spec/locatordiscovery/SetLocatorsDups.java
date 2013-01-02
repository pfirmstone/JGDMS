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
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.core.discovery.LookupLocator;

import java.util.ArrayList;

/**
 * With respect to the <code>setLocators</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that when the parameter input to the <code>setLocators</code> method
 * contains at least one element that is a duplicate of another element in
 * the input set, the <code>LookupLocatorDiscovery</code> utility operates
 * as if <code>setLocators</code> was invoked with the duplicates removed
 * from the input set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more "initial" lookup services, each started during construct,
 *        before the test begins execution
 *   <li> one or more "additional" lookup services, each started after the
 *        test has begun execution
 *   <li> one instance of the lookup locator discovery utility
 *   <li> the lookup locator discovery utility is initially configured to
 *        discover the set of locators whose elements are the locators of
 *        the initial lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then after
 * invoking the <code>setLocators</code> method with an input set containing
 * duplicate elements, the listener will receive the expected discovery
 * events, with the expected contents.
 *
 */
public class SetLocatorsDups extends AbstractBaseTest {

    protected LookupLocator[] dupLocs = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Create a set of locators to discover that contain duplicates */
        ArrayList newLookups = new ArrayList(11);
        int len1 = getAddLookupsToStart().size();
        int len2 = 2*len1;
        for(int i=0;i<len1;i++) {
            LocatorGroupsPair pair
                                = (LocatorGroupsPair)getAddLookupsToStart().get(i);
            newLookups.add(i,pair);
        }//end loop
        for(int i=len1;i<len2;i++) {
            LocatorGroupsPair pair
                          = (LocatorGroupsPair)getAddLookupsToStart().get(i-len1);
            newLookups.add(i,pair);
        }//end loop
        dupLocs = toLocatorArray(newLookups);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> start the additional lookup services
     *     <li> verifies that the lookup locator discovery utility under test
     *          discovers the initial lookup services that were started 
     *          during construct
     *     <li> re-configures the listener's expected event state to expect
     *          the discovery of the new, replacement set of lookup services
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the new, replacement set of locators containing the duplicate
     *          elements
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends the expected discovered events, having the expected 
     *          contents related to the additional lookups that were started
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Start the additional lookup services */
        startAddLookups();
        /* Verify discovery of the initial lookups */
        doDiscovery(getInitLookupsToStart(),mainListener);
        /* Configure the listener's expected event state for the additional
         * lookup services
         */
        mainListener.clearAllEventInfo();
        mainListener.setLookupsToDiscover(getAddLookupsToStart());
        /* Configure the lookup locator discovery utility to discover the
         * additional lookups
         */
        locatorDiscovery.setLocators(dupLocs);
        logger.log(Level.FINE, "replaced current locators to "
                        +"discover with new set containing DUPLICATES --");
        LocatorsUtil.displayLocatorSet(dupLocs,"locator",
                                       Level.FINE);
        /* Verify discovery of the added lookups */
        waitForDiscovery(mainListener);
    }//end run

}//end class SetLocatorsDups

