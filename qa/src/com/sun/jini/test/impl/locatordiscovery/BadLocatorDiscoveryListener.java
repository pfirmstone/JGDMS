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

package com.sun.jini.test.impl.locatordiscovery;

import java.util.logging.Level;

import net.jini.core.discovery.LookupLocator;

import com.sun.jini.test.share.BadTestListener;
import com.sun.jini.test.share.LocatorsUtil;
import com.sun.jini.test.spec.locatordiscovery.AbstractBaseTest;

/**
 * With respect to the current implementation of the
 * <code>LookupLocatorDiscovery</code> utility, this class verifies that if the
 * <code>addDiscoveryListener</code> method is invoked after the lookup locator
 * discovery utility has been terminated, an <code>IllegalStateException</code>
 * results.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p>
 * <ul>
 * <li>no lookup services
 * <li>one instance of LookupLocatorDiscovery
 * <li>after invoking the terminate method on the lookup locator discovery
 * utility, the addDiscoveryListener method is invoked
 * </ul>
 * <p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as intended,
 * upon invoking the <code>addDiscoveryListener</code> method after the that
 * utility has been terminated, an <code>IllegalStateException</code> will
 * occur.
 * 
 */
public class BadLocatorDiscoveryListener extends AbstractBaseTest {

    /**
     * Tests listener throwing exception by adding a bad listener. Remaining
     * code based on AddLocators.java.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Add the bad listener. */
        logger.log(Level.FINE, "Adding bad listener");
        locatorDiscovery.addDiscoveryListener(new BadTestListener(logger));
        /* Start the additional lookup services */
        startAddLookups();
        /* Verify discovery of the initial lookups */
        doDiscovery(initLookupsToStart, mainListener);
        /*
         * Configure the listener's expected event state for the additional
         * lookup services
         */
        mainListener.clearAllEventInfo();
        mainListener.setLookupsToDiscover(addLookupsToStart);
        /*
         * Configure the lookup locator discovery utility to discover the
         * additional lookups
         */
        LookupLocator[] locsToAdd = toLocatorArray(addLookupsToStart);
        locatorDiscovery.addLocators(locsToAdd);
        logger.log(Level.FINE, "added additional locators to "
                + "lookup locator discovery --");
        LocatorsUtil.displayLocatorSet(locsToAdd, "locator", Level.FINE);
        /* Verify discovery of the added lookups */
        waitForDiscovery(mainListener);
    }
}
