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

package com.sun.jini.test.spec.discoverymanager;

import java.util.logging.Level;

import java.util.ArrayList;
import com.sun.jini.qa.harness.QAConfig;

/**
 * This class verifies that the <code>LookupDiscoveryManager</code> utility
 * handles, in a manner consistent with the specification, the changed
 * events that occur as a result of the replacement of one or more of the
 * member groups of discovered lookup services.
 * 
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> N lookup services having locator L0i, and belonging to groups
 *         {G0i,G1i,G2i}, where i = 0 ... N
 *    <li> one lookup discovery manager configured to discover some of the
 *         lookups by only group discovery, some by only locator discovery,
 *         and some by both group and locator discovery
 *    <li> one instance of DiscoveryChangeListener registered with the lookup
 *         discovery manager
 *    <li> after all of the lookup services are successfully discovered, 
 *         every other element of each lookup's set of member groups is
 *         replaced with a new element
 * </ul><p>
 * 
 * If the lookup discovery manager functions as specified, then the client's
 * listener will receive the expected number of changed events, with the
 * expected contents.
 *
 * Related bug ids: 4292957
 */
public class MulticastMonitorChange extends Discovered {

    /** Performs actions necessary to prepare for execution of the current
     *  test (refer to the description of this method in the parent class).
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        mainListener = new GroupChangeListener();
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> reconfigures the lookup discovery manager to discover some of
     *         the lookups by only group discovery, some by only locator
     *         discovery, and some by both group and locator discovery
     *    <li> verifies that the discovery process is working by waiting
     *         for the expected discovery events
     *    <li> verifies that the lookup discovery manager utility under test
     *         sends the expected discovery events, with the expected contents
     *    <li> for each lookup discovered, replaces every other element of
     *         the lookup's member groups with a new element
     *    <li> verifies that the lookup discovery manager utility under test
     *         sends the expected changed events, with the expected contents
     *   </ul>
     */
    public void run() throws Exception {
        super.run();
        /* Must sync on listener since Discovered/Discarded/Changed Map
         * will change as events arrive, and setLookupsToDiscover
         * examines the contents of those maps. So we don't want those
         * maps to change until setLookupsToDiscover returns.
         */
        synchronized(mainListener) {
            /* Replace alternate groups to cause changed events */
            ArrayList locGroupsPairList = replaceMemberGroups(true);
            /* Set the expected changed event info */
            mainListener.setLookupsToDiscover(locGroupsPairList,
                                              locatorsToDiscover,
                                              groupsToDiscover);
        }//end sync(mainListener)
        waitForChange((GroupChangeListener)mainListener);
    }//end run

}//end class MulticastMonitorChange

