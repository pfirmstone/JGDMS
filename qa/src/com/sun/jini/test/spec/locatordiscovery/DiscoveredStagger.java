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
import com.sun.jini.test.share.LookupServices;
import java.util.List;
import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the lookup locator discovery utility can
 * successfully employ the unicast discovery protocol on behalf of a client
 * to discover lookup services that are started at various times before and
 * during the operation of the lookup locator discovery utility.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services, each started at various "staggered"
 *         times throughout the test, each having a known unicast port
 *    <li> one instance of the lookup locator discovery utility
 *    <li> the lookup locator discovery utility is configured to discover the
 *         set of locators whose elements are the locators of each lookup
 *         service that is to be started
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive the expected discovery events, with the expected
 * contents.
 *
 */
public class DiscoveredStagger extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        delayLookupStart = true;
        super.construct(sysConfig);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> starts one lookup service (so it's up before discovery actually
     *         starts -- so that the lookup locator discovery utility is
     *         fully exercised)
     *    <li> configures the lookup locator discovery utility to discover
     *         the set of locators whose elements are the locators of each
     *         lookup service that was/will be started
     *    <li> starts the unicast discovery process by adding a discovery 
     *         listener to the lookup locator discovery utility
     *    <li> asynchronously starts additional lookup services at various
     *         staggered times while the lookup locator discovery utility
     *         is operational
     *    <li> verifies that the lookup locator discovery utility under test
     *         sends the expected discovered events, having the expected 
     *         contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* The events arrive over a long (staggered) period of time. Thus,
         * this test cannot use the fast timeout feature for quick testing.
         */
        boolean oldUseFastTimeout = useFastTimeout;
        useFastTimeout = false;
        LookupServices lookups = getLookupServices();
        Thread lookupsThread = null;
                               // new StaggeredStartThread(1, getAllLookupsToStart());
        try {
            /* Start 1st lookup service (so it's up before discovery starts) */
//            LocatorGroupsPair pair
//                                 = (LocatorGroupsPair)getAllLookupsToStart().get(0);
//	    LookupLocator l = pair.getLocator();
//            int port = l.getPort();
//            if(portInUse(port)) port = 0;//use randomly chosen port
//            startLookup(0, port, l.getHost());
            int next = lookups.startNextLookup(null);
            lookupsThread = lookups.staggeredStartThread(next);
            /* Re-configure LookupLocatorDiscovery to discover given locators*/
            logger.log(Level.FINE, "change LookupLocatorDiscovery to discover -- ");
            List<LocatorGroupsPair> allLookupsToStart = getAllLookupsToStart();
            LookupLocator[] locatorsToDiscover
                                          = toLocatorArray(allLookupsToStart);
            for(int i=0;i<locatorsToDiscover.length;i++) {
                logger.log(Level.FINE, "    "+locatorsToDiscover[i]);
            }//end loop
            locatorDiscovery.setLocators(locatorsToDiscover);
            /* Add the given listener to the LookupLocatorDiscovery utility */
            mainListener.setLookupsToDiscover(allLookupsToStart);
            locatorDiscovery.addDiscoveryListener(mainListener);
            /* Start remaining lookup services in a time-staggered fashion */
            lookupsThread.start();
            /* Wait for discovery of all lookup service(s) started above */
            waitForDiscovery(mainListener);
        } finally {
            /* If an exception occurred before the thread finished starting
             * all lookups, then we need to tell the thread to stop.
             *
             * If waitForDiscovery() somehow completed successfully, but the
             * thread is still running - creating lookups - then we still need
             * to tell the thread to stop so that it doesn't continue running
             * into the next test.
             */
            if (lookupsThread != null) lookupsThread.interrupt();
            useFastTimeout = oldUseFastTimeout;
        }
    }//end run

}//end class DiscoveredStagger

