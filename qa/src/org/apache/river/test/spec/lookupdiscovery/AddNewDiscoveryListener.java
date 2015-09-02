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
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.lookupdiscovery.AbstractBaseTest;

/**
 * With respect to the <code>addDiscoveryListener</code> method, this class
 * verifies that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies that
 * upon adding a new instance of <code>DiscoveryListener</code>, the
 * <code>LookupDiscovery</code> utility will, for each lookup service that
 * was previously discovered, send a discovered event containing the set of
 * member groups with which the lookup service was configured.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each with a finite set of member groups
 *   <li> one instance of LookupDiscovery configured to discover the lookup
 *        services that were started
 *   <li> one DiscoveryListener registered with that LookupDiscovery
 *   <li> after discovery, a new instance of DiscoveryListener is added to
 *        the LookupDiscovery utility through the invocation of the
 *        addDiscoveryListener method
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discovered event
 * will be sent to the new listener for each lookup service that was 
 * previously discovered; and that event will accurately reflect the set
 * of member groups with which the lookup service was configured.
 */
public class AddNewDiscoveryListener extends Discovered {

    protected LookupListener newListener = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        newListener = new AbstractBaseTest.LookupListener();
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup discovery utility to use group
     *         discovery to discover the set of lookup services started during
     *         construct
     *    <li> starts the multicast discovery process by adding a listener to
     *         the lookup discovery utility
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> invokes the addDiscoveryListener method on the lookup discovery
     *         utility to add a new listener for the original set of lookups
     *         to discover
     *    <li> verifies that the lookup discovery utility under test sends the
     *         expected discovered events, with the expected contents
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        logger.log(Level.FINE, "adding a new listener to "
                                        +"LookupDiscovery ... ");
        newListener.setLookupsToDiscover(getInitLookupsToStart());
        lookupDiscovery.addDiscoveryListener(newListener);
        waitForDiscovery(newListener);
    }//end run

}//end class AddNewDiscoveryListener


