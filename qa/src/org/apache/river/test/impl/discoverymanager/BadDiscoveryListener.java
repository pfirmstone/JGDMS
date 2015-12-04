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

package org.apache.river.test.impl.discoverymanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.BadTestListener;
import org.apache.river.test.spec.discoverymanager.Discovered;

/**
 * With respect to the <code>addDiscoveryListener</code> method, this class
 * verifies that the <code>LookupDiscoveryManager</code> utility operates in a
 * manner consistent with the specification.
 * <p>
 * In particular, this class verifies that upon adding a new instance of
 * <code>DiscoveryListener</code> to a lookup discovery manager configured to
 * discover a set of lookup services through a MIX of group discovery and
 * locator discovery, the lookup discovery manager will send a discovered event
 * - with the appropriate contents - to that listener for each lookup service
 * that satisfies the discovery criteria.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p>
 * <ul>
 * <li>N lookup services having locator L0i, and belonging to groups
 * {G0i,G1i,G2i}, where i = 0 ... N
 * <li>one lookup discovery manager configured to discover some of the lookups
 * by only group discovery, some by only locator discovery, and some by both
 * group and locator discovery
 * <li>one instance of DiscoveryListener registered with the lookup discovery
 * manager
 * <li>after discovery, a new instance of DiscoveryListener is added to the
 * lookup discovery manager utility through the invocation of the
 * addDiscoveryListener method
 * </ul>
 * <p>
 * 
 * If the lookup discovery manager functions as specified, then each of the
 * client's listeners will receive the expected number of discovery events, with
 * the expected contents.
 */
public class BadDiscoveryListener extends Discovered {

    protected final LookupListener newListener = new LookupListener();;

    /**
     * Executes the current test by doing the following:
     * <p>
     * <ul>
     * <li>creates N lookup services having locator L0i, and belonging to groups
     * {G0i,G1i,G2i}, where i = 0 ... N
     * <li>creates a lookup discovery manager (LDM) initially configured to
     * discover NO_GROUPS and NO_LOCATORS
     * <li>reconfigures the LDM to discover some of the lookups by only group
     * discovery, some by only locator discovery, and some by both group and
     * locator discovery
     * <li>verifies that the discovery process is working by waiting for the
     * expected discovery events
     * <li>verifies that the lookup discovery manager utility under test sends
     * the expected events, with the expected contents to the first listener
     * <li>invokes the addDiscoveryListener method on the lookup discovery
     * manager to add a new listener for the original set of lookups to discover
     * <li>verifies that the lookup discovery manager utility under test sends
     * the expected events, with the expected contents to the first listener
     * </ul>
     */
    public void run() throws Exception {
        discoveryMgr.addDiscoveryListener(new BadTestListener(logger));
        super.run();
        logger.log(Level.FINE, "Adding bad listener");
        boolean exceptionHappened = false;
        try {
            discoveryMgr.addDiscoveryListener(new BadTestListener(logger));
        } catch (BadTestListener.BadListenerException e) {
            logger.log(Level.FINEST, "Expected exception happened");
            exceptionHappened = true;
        }
        if (!exceptionHappened) {
            throw new TestException(
                    "Initial discovery exception not thrown back to test program");
        }
        logger.log(Level.FINE, "adding a new listener to the "
                + "lookup discovery manager ... ");
        newListener.setLookupsToDiscover(getLookupServices().getInitLookupsToStart(),
                locatorsToDiscover, groupsToDiscover);
        discoveryMgr.addDiscoveryListener(newListener);
        waitForDiscovery(newListener);
    }
}
