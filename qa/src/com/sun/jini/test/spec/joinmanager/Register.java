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

import net.jini.lookup.JoinManager;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is constructed, the
 * service input to the constructor is registered with all lookup
 * services the join manager is configured to discover (through its
 * <code>DiscoveryManagement</code> instance).
 * 
 */
public class Register extends AbstractBaseTest {

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
                                        getLookupDiscoveryManager(),leaseMgr,
					sysConfig.getConfiguration());
        joinMgrList.add(joinMgrSrvcID);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the test service input to the join manager constructor
     *   is registered with all lookup services the join manager is configured
     *   to discover (through its <code>DiscoveryManagemen</code>t instance).
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");

        /* Verify that the lookups were discovered */
        logger.log(Level.FINE, "verifying the lookup "
                                        +"service(s) are discovered ...");
        mainListener.setLookupsToDiscover(getLookupsStarted(),
                                          toGroupsArray(getLookupsStarted()));
        waitForDiscovery(mainListener);
        verifyJoin();
        logger.log(Level.FINE, "join manager successfully registered "
                          +"TestService with all "
                          +curLookupListSize("Register.run")
                          +" lookup service(s)");
    }//end run

}//end class Register
