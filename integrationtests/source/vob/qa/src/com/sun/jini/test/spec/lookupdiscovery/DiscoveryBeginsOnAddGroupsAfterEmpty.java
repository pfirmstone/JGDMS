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

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that if an empty set (NO_GROUPS) is passed to the
 * constructor, discovery will not be started until the <code>addGroups</code>
 * method is called with a non-<code>null</code>, non-empty set.
 * <p>
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services, each belonging to a finite
 *        set of member groups, started during setup
 *   <li> an instance of the lookup discovery utility created by passing
 *        and empty String array (DiscoveryGroupManagement.NO_GROUPS) to
 *        the constructor
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 *   <li> after creating the lookup discovery utility, <code>addGroups</code>
 *        is invoked with a non-null, non-empty array of group names
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the
 * listener will receive no events until the <code>addGroups</code> method
 * is called to re-configure the lookup discovery utility to discover
 * the lookup services started during setup.
 */
public class DiscoveryBeginsOnAddGroupsAfterEmpty extends AbstractBaseTest {

    protected String[] groupsToDiscover = DiscoveryGroupManagement.NO_GROUPS;
    protected String constStr  = "NO_GROUPS";
    protected String methodStr = "addGroups";
    protected boolean addGroups  = true;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        groupsToDiscover = toGroupsArray(initLookupsToStart);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> creates a lookup discovery utility using an empty String
     *         (NO_GROUPS) in the constructor
     *    <li> adds a listener to the lookup discovery utility just created,
     *         and verifies the listener receives no events
     *    <li> depending on the value of <code>addGroups</code>, invokes either
     *         addGroups or setGroups to re-configure the lookup discovery
     *         utility to discover the lookup services started in setup
     *    <li> verifies that the lookup discovery utility under test
     *         sends the expected discovered events, having the expected
     *         contents related to the lookups started in setup
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(!addGroups) {
            methodStr = new String("setGroups");
        }//endif
        /* Creating a LookupDiscovery instance using an empty String
         * (NO_GROUPS) as the input to the constructor.
         */
        logger.log(Level.FINE,
                       "creating a new LookupDiscovery with input = "
                          +constStr);
        LookupDiscovery ld = new LookupDiscovery
                                     (DiscoveryGroupManagement.NO_GROUPS,
				      getConfig().getConfiguration());
        lookupDiscoveryList.add(ld);
        /* Add a listener to the lookup discovery utility created above,
         * and verify the listener receives no events.
         */
        mainListener.clearAllEventInfo();//listener expects no events
        ld.addDiscoveryListener(mainListener);
        waitForDiscovery(mainListener);
        /* Re-configure the listener to expect events for the lookups
         * started during setup.
         */
        logger.log(Level.FINE, "calling "+methodStr
                          +" to change the groups to discover to -- ");
        if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
            logger.log(Level.FINE, "   ALL_GROUPS");
        } else {
            if(groupsToDiscover.length == 0) {
                logger.log(Level.FINE, "   NO_GROUPS");
            } else {
                for(int i=0;i<groupsToDiscover.length;i++) {
                    logger.log(Level.FINE, "   "
                                      +groupsToDiscover[i]);
                }//end loop
            }//endif
        }//endif
        mainListener.setLookupsToDiscover(initLookupsToStart);
        /* Using either addGroups ore setGroups, re-configure the lookup
         * discovery utility to discover the lookup services started in
         * setup
         */
        if(addGroups) {
            ld.addGroups(groupsToDiscover);
        } else {
            ld.setGroups(groupsToDiscover);
        }//endif
        /* Verify that the listener receives the expected events */
        waitForDiscovery(mainListener);
    }//end run

}//end class DiscoveryBeginsOnAddGroupsAfterEmpty

