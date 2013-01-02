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
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lookup.JoinManager;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is terminated, all leases
 * managed on behalf of the associated test service are also cancelled.
 * 
 */
public class TerminateLeases extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) whose member groups are finite
     *          and unique relative to the member groups of all other lookup
     *          services running within the same multicast radius of the new
     *          lookup services
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a set of attributes (either null or non-null)
     *          with which to register the service, and a non-null instance
     *          of a lookup discovery manager configured to discover the
     *          lookup services started in the previous step
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Discover & join lookups just started */
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,serviceAttrs,serviceID,
                                        getLookupDiscoveryManager(),null,
					sysConfig.getConfiguration());
        /* Note: no need to add joinMgrSrvcID to the joinMgrList for
         *       termination during tearDown because it will be terminated
         *       in the run method
         */
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> verifies that the test service is registered (it's leases
     *          are being managed) with each lookup service started during
     *          construct
     *     <li> terminates the join manager
     *     <li> verifies that the test service is no longer registered (the
     *          leases are no longer being managed) with any of the lookup
     *          service(s) with which it was previously registered
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Verify that the lookups were discovered */
        logger.log(Level.FINE, "verifying the lookup "
                                        +"service(s) are discovered ...");
        mainListener.setLookupsToDiscover(getLookupsStarted(),
                                          toGroupsArray(getLookupsStarted()));
        waitForDiscovery(mainListener);
        /* Verify join is successful */
        logger.log(Level.FINE, "verifying test service is "
                          +"registered with all lookup service(s) ...");
        verifyJoin();
        /* Terminate the join manager */
        logger.log(Level.FINE, "terminating the join manager ...");

        DiscoveryManagement discMgr = joinMgrSrvcID.getDiscoveryManager();
        joinMgrSrvcID.terminate();
        discMgr.terminate();//do this or it will bleed into following tests
        /* Verify test service is no longer registered with any lookup */
        logger.log(Level.FINE, "verifying test service is NO "
                          +"LONGER registered with any of the "
                          +"lookup service(s) ...");
	try {
	    verifyJoin();
	    throw new TestException("test service is still registered "
				    +"with at least 1 lookup service");
	} catch (TestException e) {
	    // expected exception
	}
    }//end run

}//end class TerminateLeases


