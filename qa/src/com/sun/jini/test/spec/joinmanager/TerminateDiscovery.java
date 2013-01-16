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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.LookupServices;

import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is terminated, if the
 * discovery manager employed by the join manager was created by the
 * join manager itself, all discovery processing being performed by that
 * manager on behalf of the associated test service will also be terminated.
 * 
 */
public class TerminateDiscovery extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> starts N lookup service(s) whose member groups are finite
     *         and unique relative to the member groups of all other lookup
     *         services running within the same multicast radius of the new
     *         lookup services
     *    <li> creates an instance of JoinManager inputting an instance of
     *         a test service, a set of attributes (either null or non-null)
     *         with which to register the service, and both a null instance
     *         of a lookup discovery manager, and a null instance of a
     *         lease renewal manager
     *    <li> retrieves and then changes the default discovery manager
     *         created by the join manager to discover the member groups
     *         to which the lookup service(s) started earlier belong
     *    <li> turns on the discovery process by adding a discovery listener
     *         to the default discovery manager
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Discover & join lookups just started */
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,serviceAttrs,serviceID,
                                        null,null,sysConfig.getConfiguration());
        /* Note: no need to add joinMgrSrvcID to the joinMgrList for
         *       termination during tearDown because it will be terminated
         *       in the run method
         */
        LookupDiscoveryManager discMgr 
              = (LookupDiscoveryManager)(joinMgrSrvcID.getDiscoveryManager());
        discMgr.setGroups(toGroupsArray(getInitLookupsToStart()));
        discMgr.addDiscoveryListener(mainListener);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> verifies that the lookup service(s) started initially were
     *         discovered by the default discovery manager created by the
     *         join manager
     *    <li> starts a new lookup service and verifies that the default
     *         discovery manager discovers any new lookup service started
     *         while the join manager's discovery process is still in
     *         progress
     *    <li> terminates the join manager
     *    <li> starts another new lookup service
     *    <li> verifies that all discovery processing in the terminated
     *         join manager was also terminated by verifying that the
     *         lookup service started after the join manager was terminated
     *         is not discovered
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Verify that the initial lookups were discovered */
        logger.log(Level.FINE, "verifying initial lookup "
		   +"service(s) are discovered ...");
        mainListener.setLookupsToDiscover(getLookupsStarted(),
                                          toGroupsArray(getLookupsStarted()));
        waitForDiscovery(mainListener);
        /* Start a new lookup service */
        logger.log(Level.FINE, "starting another lookup service "
                          +" to verify discovery in the join manager ...");
//        startLookup(curLookupListSize("TerminateDiscovery.run"),0);
        LookupServices lookups = getLookupServices();
        lookups.startNextLookup("TerminateDiscovery.run");
        /* Verify that the new lookup was discovered */
        logger.log(Level.FINE, ""+": verifying the new lookup "
                                        +"service is discovered ...");
        mainListener.setLookupsToDiscover(getLookupsStarted(),
                                          toGroupsArray(getLookupsStarted()));
        waitForDiscovery(mainListener);
        /* Terminate the join manager */
        logger.log(Level.FINE, "terminating the join manager ...");
        joinMgrSrvcID.terminate();
        /* Start new lookup services */
        logger.log(Level.FINE, "starting another lookup service "
                          +"to verify discovery terminated ...");
//        startLookup(curLookupListSize("TerminateDiscovery.run"),0);
        lookups.startNextLookup("TerminateDiscovery.run");
        /* Verify that the new lookup was NOT discovered */
        logger.log(Level.FINE, "verifying the new lookup "
                          +"service was NOT discovered ...");
        mainListener.setLookupsToDiscover(getLookupsStarted(),
                                          toGroupsArray(getLookupsStarted()));
        try {
            waitForDiscovery(mainListener);
            throw new TestException("discovery still works even though "
				    +"join manager terminated");
        } catch(Exception e) {
        }
    }//end run

}//end class TerminateDiscovery


