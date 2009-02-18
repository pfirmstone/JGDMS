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

package com.sun.jini.test.spec.lookupdiscovery;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the lookup discovery utility can successfully
 * employ the multicast discovery protocol on behalf of a client to discover
 * lookup services that are started at various times before and during the
 * operation of the lookup discovery utility.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services, each belonging to a finite
 *         set of member groups, and each started at various "staggered"
 *         times throughout the test (each having a known unicast port)
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is configured to discover the
 *         member groups of each lookup service that is to be started
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the listener
 * will receive the expected discovery events, with the expected contents.
 */
public class DiscoveredStagger extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        delayLookupStart = true;
        super.setup(sysConfig);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> starts one lookup service (so it's up before discovery actually
     *         starts -- so that the lookup discovery utility is fully
     *         exercised)
     *    <li> configures the lookup discovery utility to discover the
     *         member groups of each lookup service that was/will be started
     *    <li> starts the multicast discovery process by adding a discovery 
     *         listener to the lookup discovery utility
     *    <li> asynchronously starts additional lookup services at various
     *         staggered times while the lookup discovery utility is
     *         operational
     *    <li> verifies that the lookup discovery utility under test sends
     *         the expected discovered events, having the expected contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* The events arrive over a long (staggered) period of time. Thus,
         * this test cannot use the fast timeout feature for quick testing.
         */
        boolean oldUseFastTimeout = useFastTimeout;
        useFastTimeout = false;
        StaggeredStartThread lookupsThread =
                                new StaggeredStartThread(1,allLookupsToStart);
        try {
            /* Start 1st lookup service (so it's up before discovery starts) */
            LocatorGroupsPair pair
                                 = (LocatorGroupsPair)allLookupsToStart.get(0);
            int port = (pair.locator).getPort();
            if(portInUse(port)) port = 0;//use randomly chosen port
            startLookup(0, port, pair.locator.getHost());
            /* Re-configure LookupDiscovery to discover given  groups */
            logger.log(Level.FINE,
                          "change LookupDiscovery to discover -- ");
            String[] groupsToDiscover = toGroupsArray(allLookupsToStart);
            for(int i=0;i<groupsToDiscover.length;i++) {
                logger.log(Level.FINE,
                                  "   "+groupsToDiscover[i]);
            }//end loop
            lookupDiscovery.setGroups(groupsToDiscover);
            /* Add the given listener to the LookupDiscovery utility */
            mainListener.setLookupsToDiscover(allLookupsToStart);
            lookupDiscovery.addDiscoveryListener(mainListener);
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
            lookupsThread.interrupt();
            useFastTimeout = oldUseFastTimeout;
        }
    }//end run

}//end class DiscoveredStagger

