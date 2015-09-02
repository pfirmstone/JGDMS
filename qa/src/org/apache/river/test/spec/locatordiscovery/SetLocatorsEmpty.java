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
import org.apache.river.test.share.LocatorsUtil;

import net.jini.discovery.LookupLocatorDiscovery;

import net.jini.core.discovery.LookupLocator;

/**
 * With respect to the <code>setLocators</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>setLocators</code> method with the empty
 * set, locator discovery will cease.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services started during construct
 *   <li> one or more additional lookup services to be started after
 *        setLocators is invoked with the empty set
 *   <li> an instance of the lookup locator discovery utility configured to
 *        discover the set of locators whose elements are the locators of
 *        both the initial and the addtional lookup services
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then
 * after invoking the <code>setLocators</code> method with the empty
 * set, discovery will cease.
 *
 */
public class SetLocatorsEmpty extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     *   <li> creates a lookup locator discovery utility configured to 
     *        to discover the set of locators whose elements are the
     *        locators of both the initial and the addtional lookups to
     *        be started
     *   <li> starts the unicast discovery process by adding to the
     *        lookup locator discovery utility, a listener that listens
     *        for discovered and discarded events related to the 
     *        lookups to be started
     *   <li> verifies that the lookup locator discovery utility under test
     *        sends the expected discovered events related to the initial
     *        lookups started
     *   <li> replaces with the empty set, the current managed set of
     *        locators with which the the lookup locator discovery utility
     *        is currently configured
     *   <li> starts the addtional lookup services the lookup locator discovery
     *        utility was configured to discover
     *   <li> verifies that discovery has ceased by verifying that the
     *        listener receives no more discovered events for any lookup
     *        service
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Start the discovery prcess by creating a LookupLocatorDiscovery
         * instance configured to discover BOTH the initial and additional
         * lookup services to be started.
         */
        LookupLocator[] locsToDiscover
                                    = toLocatorArray(getAllLookupsToStart());
        logger.log(Level.FINE, "starting discovery by creating a "
                          +"LookupLocatorDiscovery to discover -- ");
        for(int i=0;i<locsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+locsToDiscover[i]);
        }//end loop
        LookupLocatorDiscovery lld
                    = new LookupLocatorDiscovery(locsToDiscover,
			     getConfig().getConfiguration());
        locatorDiscoveryList.add(lld);

        /* Verify that the lookup locator discovery utility created
         * above is operational by verifying that the INITIIAL lookups
         * are discovered.
         */
        mainListener.setLookupsToDiscover(getInitLookupsToStart());
        lld.addDiscoveryListener(mainListener);
        waitForDiscovery(mainListener);

        /* Replace the current locators to discover with the empty set */
        LookupLocator[] locsToRemove = new LookupLocator[0];
        logger.log(Level.FINE, "replace the current set of "
                          +"locators to discover with the EMPTY SET --");
        LocatorsUtil.displayLocatorSet(locsToRemove,"locator",
                                       Level.FINE);

        LookupLocator[] locsBefore = lld.getLocators();
        lld.setLocators(locsToRemove);
        LookupLocator[] locsAfter = lld.getLocators();

        logger.log(Level.FINE, "comparing locators BEFORE "
                          +"and AFTER replacing current set with the"
                          +"EMPTY SET ...");
        // log comparison info
	LocatorsUtil.compareLocatorSets(locsBefore, locsAfter, Level.FINE); 

        /* Verify that discovery has been "turned off" (now configured
         * to discover NO_LOCATORS) by starting the additional lookups,
         * and verifying that the listener receives no more discovery
         * events.
         */
        logger.log(Level.FINE, "starting additional lookup services ...");
        startAddLookups();
        /* Since the lookup locator discovery utility was re-configured
         * to discover NO_LOCATORS, the listener should receive no more
         * events; even though lookups have just been started that have
         * locators that utility was originally configured to discover.
         * Thus, reset the listener to expect no more events.
         */
        mainListener.clearAllEventInfo();
        /* Wait a nominal amount of time to allow any un-expected events
         * to arrive.
         */
        waitForDiscovery(mainListener);
    }//end run

}//end class SetLocatorsEmpty

