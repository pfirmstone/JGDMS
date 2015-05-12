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

package org.apache.river.test.spec.joinmanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import net.jini.lookup.JoinManager;

import java.io.IOException;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the constructor that takes a 
 * <code>ServiceIDListener</code> in its argument list is used to
 * register a service with N lookup services, the join manager will
 * send to the listener one and only one notification referencing the
 * <code>ServiceID</code> for the service -- as generated according to
 * the algorithm documented in the specification of the lookup service.
 * 
 */
public class ServiceIDNotify extends AbstractBaseTest {

    protected int expectedNEvents = 1;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) whose member groups are finite
     *          and unique relative to the member groups of all other lookup
     *          services running within the same multicast radius of the new
     *          lookup services
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes ServiceIDListener (callback),
     *          inputting an instance of a test service and a non-null
     *          instance of a lookup discovery manager configured to discover
     *          the member groups of the lookup services started in the
     *          previous step
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        logger.log(Level.FINE, "creating a callback join manager ...");
        callback = new SrvcIDListener(testService);
        joinMgrCallback = new JoinManager(testService,serviceAttrs,callback,
                                          getLookupDiscoveryManager(),
                                          leaseMgr,
					  sysConfig.getConfiguration());
        joinMgrList.add(joinMgrCallback);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the ServiceID listener input to the constructor of the
     *   join manager utility class receives one and only one ServiceID
     *   notification from the join manager.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Verify that the lookups were discovered */
        logger.log(Level.FINE, "verifying the lookup "
                                        +"service(s) are discovered ...");
        mainListener.setLookupsToDiscover(getLookupsStarted(),
                                          toGroupsArray(getLookupsStarted()));
        waitForDiscovery(mainListener);
        verifyJoin(expectedNEvents);
    }//end run

}//end class ServiceIDNotify


