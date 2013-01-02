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

import net.jini.discovery.LookupDiscovery;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that discovery processing begins when an instance
 * of <code>LookupDiscovery</code> is created, and ends when that instance's
 * <code>terminate</code> method is invoked.
 * <p>
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more initial lookup services, each belonging to a finite
 *         set of member groups, started during construct
 *    <li> one or more additional lookup services, each belonging to a finite
 *         set of member groups, to be started after termination of the lookup
 *         discovery utility
 *    <li> an instance of the lookup discovery utility configured to discover
 *         the set of member groups whose elements are the member groups
 *         associated with both the initial and the addtional lookup services
 *    <li> one instance of DiscoveryListener registered with the lookup
 *         discovery utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the client's
 * listener will receive the expected discovered events, with the expected
 * contents prior to termination, and will receive no more discovered events
 * after termination of the lookup discovery utility.
 */
public class DiscoveryEndsOnTerminate extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     *   <li> creates a lookup discovery utility configured to discover the
     *        set of groups whose elements are the member groups of both the
     *        initial and the addtional lookups to be started
     *   <li> starts the multicast discovery process by adding to the lookup
     *        discovery utility, a listener that listens for discovered and
     *        discarded events related to the lookups to be started
     *   <li> verifies that the lookup discovery utility under test sends the
     *        expected discovered events related to the initial lookups started
     *   <li> terminates the lookup discovery utility
     *   <li> starts the addtional lookup services the lookup discovery
     *        utility was configured to discover
     *   <li> verifies the listener receives no more discovered events for
     *        any lookup service
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Start the discovery prcess by creating a LookupDiscovery
         * instance configured to discover BOTH the initial and additional
         * lookup services to be started.
         */
        String[] groupsToDiscover = toGroupsArray(getAllLookupsToStart());
        logger.log(Level.FINE,
                          "starting discovery by creating a "
                          +"LookupDiscovery to discover -- ");
        for(int i=0;i<groupsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+groupsToDiscover[i]);
        }//end loop
        LookupDiscovery ld = new LookupDiscovery(groupsToDiscover,
                                          getConfig().getConfiguration());
        lookupDiscoveryList.add(ld);

        /* Verify that the lookup discovery utility created above is
         * operational by verifying that the INITIIAL lookups are
         * discovered.
         */
        mainListener.setLookupsToDiscover(getInitLookupsToStart());
        ld.addDiscoveryListener(mainListener);
        waitForDiscovery(mainListener);

        /* Terminate the lookup discovery utility */
        ld.terminate();
        logger.log(Level.FINE, "terminated lookup discovery");


        /* Since the lookup discovery utility was terminated, the listener
         * should receive no more events when new lookups are started that
         * belong to groups the utility is configured to discover. Thus,
         * reset the listener to expect no more events.
         */
        mainListener.clearAllEventInfo();
        /* Verify that the lookup discovery utility created above is no
         * longer operational by starting the additional lookups, and
         * verifying that the listener receives no more discovered events.
         */
        logger.log(Level.FINE,
                          "starting additional lookup services ...");
        startAddLookups();
        /* Wait a nominal amount of time to allow any un-expected events
         * to arrive.
         */
        waitForDiscovery(mainListener);
    }//end run

}//end class DiscoveryEndsOnTerminate

