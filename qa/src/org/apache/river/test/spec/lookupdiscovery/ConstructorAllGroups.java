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

package org.apache.river.test.spec.lookupdiscovery;
import org.apache.river.qa.harness.QAConfig;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the parameter input to the constructor
 * is <code>null</code> (ALL_GROUPS), then attempts will be made to discover
 * all lookup services located within the current multicast radius, regardless
 * of group membership.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services started during construct
 *   <li> an instance of the lookup discovery utility constructed with
 *        a null parameter
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the client's
 * listener will receive the expected discovery events, with the expected
 * contents.
 */
public class ConstructorAllGroups extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> constructs a lookup discovery utility with a null parameter
     *    <li> starts the multicast discovery process for the lookup discovery
     *         utility just constructed by adding a discovery listener
     *    <li> verifies that the lookup discovery utility under test sends the
     *         expected discovered events, with the expected contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Create LookupDiscovery instance configured for ALL_GROUPS */
        logger.log(Level.FINE,
                          "constructing a new LookupDiscovery "
                          +"to discover -- ");
        logger.log(Level.FINE, "   ALL_GROUPS");
        LookupDiscovery newLD = new LookupDiscovery
                                    (DiscoveryGroupManagement.ALL_GROUPS,
				     getConfig().getConfiguration());
        lookupDiscoveryList.add(newLD);
        /* Verify discovery - set the expected groups to discover */
        mainListener.setLookupsToDiscover(getAllLookupsToStart());
        /* Add the listener to the LookupDiscovery utility */
        newLD.addDiscoveryListener(mainListener);
        /* Wait for the discovery of the expected lookup service(s) */
        waitForDiscovery(mainListener);
    }//end run

}//end class ConstructorAllGroups

