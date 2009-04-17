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

import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.LookupDiscovery;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that after invoking the <code>removeDiscoveryListener</code>
 * method to remove a listener registered with a lookup discovery utility,
 * the listener that was removed will receive no more discovered events from
 * the lookup discovery utility from which that listener was removed.
 * <p>
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services, each belonging to a finite set of
 *         member groups, and each started during setup, before the test
 *         begins execution
 *    <li> one or more "initial" lookup services, each belonging to a finite
 *         set of member groups, and each started during setup, before the
 *         test begins execution
 *    <li> one or more "additional" lookup services, each belonging to a finite
 *         set of member groups, and each started after the listener has been
 *         removed
 *    <li> one instance of the lookup discovery utility configured to discover
 *         the set of groups whose elements are the member groups of all
 *         of the lookup services to be started
 *    <li> two instances of DiscoveryListener registered with the lookup
 *         discovery utility: one used to verify the continued operation of
 *         discovery processing, one to be removed from the lookup discovery
 *         utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then after removing
 * the listener from the lookup discovery utility, the removed listener will
 * receive no more discovered events.
 */
public class RemoveDiscoveryListener extends AbstractBaseTest {


    /** Executes the current test by doing the following:
     * <p><ul>
     *   <li> creates a lookup discovery utility configured to discover all
     *        of the lookup services (both initial and additonal) to be
     *        started
     *   <li> starts the multicast discovery process by adding two discovery
     *        listeners to the lookup discovery utility just created
     *   <li> verifies that the lookup discovery utility under test sends the
     *        expected discovered events related to the initial lookups
     *        started to both listeners registered with it
     *   <li> removes one of the listeners from the lookup discovery utility
     *   <li> clears the expected event state of both listeners
     *   <li> starts the addtional lookup services the lookup discovery
     *        utility is configured to discover
     *   <li> verifies that the lookup discovery utility under test sends the
     *        expected discovered events related to the addtional lookups
     *        started to only the listener that was not removed
     *   <li> verifies that the lookup discovery utility under test does NOT
     *        send any discovered events to the listener that was removed
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Start the discovery prcess by creating a LookupDiscovery
         * instance configured to discover BOTH the initial and additional
         * lookup services to be started.
         */
        String[] groupsToDiscover = toGroupsArray(allLookupsToStart);
        String[] initialGroups    = toGroupsArray(initLookupsToStart);
        String[] additionalGroups = toGroupsArray(addLookupsToStart);
        logger.log(Level.FINE,
                          "create LookupDiscovery to initially "
                          +"discover groups -- ");
        GroupsUtil.displayGroupSet(initialGroups,"  initialGroups",
                                   Level.FINE);
        logger.log(Level.FINE,
                          "LookupDiscovery just created should "
                          +"additionally discover groups -- ");
        GroupsUtil.displayGroupSet(additionalGroups,
                                   "  additionalGroups",Level.FINE);
        LookupDiscovery ld = new LookupDiscovery(groupsToDiscover,
						 getConfig().getConfiguration());
        lookupDiscoveryList.add(ld);
        LookupListener newListener = new AbstractBaseTest.LookupListener();

        /* Verify that the lookup discovery utility created above is
         * operational by verifying that the INITIIAL lookups are
         * discovered by both listeners.
         */
        logger.log(Level.FINE,
                      "verifying discovery for initial listener ...");
        mainListener.setLookupsToDiscover(initLookupsToStart);
        ld.addDiscoveryListener(mainListener);
        waitForDiscovery(mainListener);

        logger.log(Level.FINE,
                 "verifying discovery for listener to be removed ...");
        newListener.setLookupsToDiscover(initLookupsToStart);
        ld.addDiscoveryListener(newListener);
        waitForDiscovery(newListener);

        /* Remove the listener */
        ld.removeDiscoveryListener(newListener);
        logger.log(Level.FINE,
                          "removed listener from lookup discovery");

        /* Verify that the listener still registered with the lookup
         * discovery utility (mainListener) continues to receive
         * discovered events (which verifies that events are still being
         * sent), but that the listener removed from the lookup discovery
         * utility (newListener) no longer receives discovered events.
         *
         * Clear the mainListener's event state for the new lookups
         * Clear the newListener's event state for the new lookups
         * 
         * Start the additional lookups to cause events to be sent.
         *
         * Re-configure the mainListener's expected event state to
         * expect discovered events for the addtional lookups
         * Verify the mainListener receives the expected discovered events.
         *
         * Leave the newListener's expected event state cleared so that
         * it expects NO discovered events; if events are actually
         * received by this listener, then failure is declared.
         * Verify the newListener receives no more discovered events.
         */
        mainListener.clearAllEventInfo();//must clear before starting LUSs
        newListener.clearAllEventInfo(); //must clear before starting LUSs
        logger.log(Level.FINE,
                          "starting additional lookup services ...");
        startAddLookups();

        logger.log(Level.FINE,
                          "verifying events are still being sent ...");
        mainListener.setLookupsToDiscover(addLookupsToStart);
        waitForDiscovery(mainListener);

        logger.log(Level.FINE, "verifying removed listener "
                          +"receives NO MORE events ...");
        waitForDiscovery(newListener);
    }//end run

}//end class RemoveDiscoveryListener

