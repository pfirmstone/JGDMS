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
import org.apache.river.qa.harness.TestException;

import net.jini.discovery.LookupLocatorDiscovery;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that "if <code>null</code> is passed to the constructor,
 * discovery will not be started until the <code<addLocators</code> method
 * is called with a non-<code>null</code>, non-empty set".
 * <p>
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services started during construct
 *   <li> an instance of the lookup locator discovery utility created by
 *        passing null to the constructor
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive no events until the <code>addLocators</code> method
 * is called to re-configure the lookup locator discovery utility to discover
 * the lookup services started during construct.
 *
 */
public class DiscoveryBeginsOnAddLocsAfterNull extends AbstractBaseTest {

    protected LookupLocator[] locsToDiscover = null;
    protected String constStr  = "NULL";
    protected String methodStr = "addLocators";
    protected boolean addLocs  = true;

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> creates a lookup locator discovery utility using null in the
     *         constructor
     *    <li> adds a listener to the lookup locator discovery utility just
     *         created, and verifies the listener receives no events
     *    <li> depending on the value of <code>addLocs</code>, invokes either
     *         addLocators or setLocators to re-configure the lookup locator
     *         discovery utility to discover the lookup services started in
     *         construct
     *    <li> verifies that the locator discovery utility utility under test
     *         sends the expected discovered events, having the expected
     *         contents related to the lookups started in construct
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(!addLocs) {
            constStr  = new String("EMPTY SET");
            methodStr = new String("setLocators");
        }//endif
        /* Creating a LookupLocatorDiscovery instance using null as the
         * input to the constructor.
         */
        logger.log(Level.FINE, "creating a LookupLocatorDiscovery with input = "
                          +constStr);
        LookupLocatorDiscovery lld 
                    = new LookupLocatorDiscovery(locsToDiscover,
		     getConfig().getConfiguration());
        locatorDiscoveryList.add(lld);
        /* Add a listener to the lookup locator discovery utility created
         * above, and verify the listener receives no events.
         */
        mainListener.clearAllEventInfo();//listener expects no events
        lld.addDiscoveryListener(mainListener);
        waitForDiscovery(mainListener);
        /* Re-configure the listener to expect events for the lookups
         * started during construct.
         */
        locsToDiscover = toLocatorArray(getInitLookupsToStart());
        logger.log(Level.FINE, "calling "+methodStr
                          +" to change the locators to discover to -- ");
        for(int i=0;i<locsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+locsToDiscover[i]);
        }//end loop
        mainListener.setLookupsToDiscover(getInitLookupsToStart());
        /* Using either addLocators ore setLocators, re-configure the 
         * lookup locator discovery utility to discover the lookup
         * services started in construct
         */
        if(addLocs) {
            lld.addLocators(locsToDiscover);
        } else {
            lld.setLocators(locsToDiscover);
        }//endif
        /* Verify that the listener receives the expected events */
        waitForDiscovery(mainListener);
    }//end run

}//end class DiscoveryBeginsOnAddLocsAfterNull

