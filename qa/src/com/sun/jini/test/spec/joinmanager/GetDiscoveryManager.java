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
import net.jini.core.discovery.LookupLocator;

import net.jini.discovery.DiscoveryManagement;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the method <code>getDiscoveryManager</code>
 * returns the instance of <code>DiscoveryManagement</code> passed in
 * to the constructor of the join manager.
 * 
 */
public class GetDiscoveryManager extends LDMNullPublicGroup {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates the instance of DiscoveryManagement that is to be
     *          passed in to the join manager's constructor
     *     <li> creates an instance of JoinManager using the version of
     *          the constructor that takes ServiceIDListener (callback),
     *          inputting an instance of a test service and the instance of
     *          DiscoveryManagement created above
     *     <li> creates an instance of JoinManager using the version of
     *          the constructor that takes a service ID, inputting 
     *          an instance of a test service and the instance of
     *          DiscoveryManagement created above
     *   </ul>
     */
    public Test construct(QAConfig config) throws Exception {
        discoveryMgr = getLookupDiscoveryManager(new String[0], null, config);
        callback = new SrvcIDListener(testService);
        super.construct(config);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   For each instance of the <code>JoinManager</code> utility class
     *   created during the construct process,
     *   <ul>
     *     <li> retrieves the instance of DiscoveryManagement being
     *          employed by the JoinManager
     *     <li> verifies that the instance of DiscoveryManagement that is
     *          retrieved is equal to the instnace of DiscoveryManagement
     *          passed in to the join manager's constructor
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Callback join manager */
        logger.log(Level.FINE, "retrieving the discovery manager from the "
                          +"callback join manager ...");
        DiscoveryManagement dmCallback = joinMgrCallback.getDiscoveryManager();
        if(dmCallback == null) {
            throw new TestException("could not successfully retrieve "
                                 +"the discovery manager");
        }//endif
        if( !dmCallback.equals(discoveryMgr) ) {
            throw new TestException(
                                 "callback join manager -- "
                                 +"discovery manager returned is NOT equal "
                                 +"to the discovery manager used to construct "
                                 +"the join manager");
        }//endif
        logger.log(Level.FINE, "discovery manager retrieved "
                          +"equals the discovery manager used to construct "
                          +"the callback join manager");
        /* Service ID join manager */
        logger.log(Level.FINE, "retrieving the discovery manager from the "
                          +"service ID join manager ...");
        DiscoveryManagement dmSrvcID = joinMgrSrvcID.getDiscoveryManager();
        if(dmSrvcID == null) {
            throw new TestException("could not successfully retrieve "
                                 +"the discovery manager");
        }//endif
        if( !dmSrvcID.equals(discoveryMgr) ) {
            throw new TestException(
                                 "service ID join manager -- "
                                 +"discovery manager returned is NOT equal "
                                 +"to the discovery manager used to construct "
                                 +"the join manager");
        }//endif
        logger.log(Level.FINE, "discovery manager retrieved "
                          +"equals the discovery manager used to construct "
                          +"the service ID join manager");
    }//end run
} //end class GetDiscoveryManager


