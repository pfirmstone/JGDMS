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
import java.util.ArrayList;

import java.util.logging.Level;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

import java.util.List;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the lookup discovery utility can successfully
 * employ the multicast discovery protocol on behalf of a client to
 * discover a number of pre-determined lookup services and then, for each
 * discovered lookup service, send to the client's listener, the appropriate
 * discovery event containing the set of member groups with which the
 * discovered lookup service was configured.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one client with one instance of the lookup discovery utility
 *   <li> the lookup discovery utility is configured to discover some of
 *        the lookup services started by the test through group discovery,
 *        some through locator discovery, and some through either group or
 *        locator discovery
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery manager
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the client's
 * listener will receive the expected discovery events, with the expected
 * contents.
 */
public class Discovered extends AbstractBaseTest {

    protected volatile List       locGroupsList  = new ArrayList(0);
    protected volatile LookupDiscovery ldToUse        = null;
    protected volatile LookupListener  listenerToUse  = null;
    protected volatile String[] groupsToDiscover  = DiscoveryGroupManagement.NO_GROUPS;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	locGroupsList    = getInitLookupsToStart();
	ldToUse          = lookupDiscovery;
	listenerToUse    = mainListener;
	groupsToDiscover = toGroupsArray(locGroupsList);
        return this;
    }

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup discovery utility to use group
     *         discovery to discover the set of lookup services started during
     *         construct
     *    <li> starts the multicast discovery process by adding a listener to
     *         the lookup discovery utility
     *    <li> verifies that the lookup discovery utility under test
     *         sends the expected discovered events, with the expected
     *         contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        doDiscovery(locGroupsList,ldToUse,listenerToUse,groupsToDiscover);
    }//end run

}//end class Discovered

