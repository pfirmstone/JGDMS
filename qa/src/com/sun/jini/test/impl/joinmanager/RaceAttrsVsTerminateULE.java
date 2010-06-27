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

package com.sun.jini.test.impl.joinmanager;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.test.spec.joinmanager.AbstractBaseTest;

import net.jini.lookup.JoinManager;

import java.util.logging.Level;
import net.jini.discovery.DiscoveryListenerManagement;

/** Regression test for bug #4953710 (ULE = UnknownLeaseException).
 *
 *  This test should be run manually against an instrumented JoinManager
 *  to test for regression. When run in automatic mode against an
 *  uninstrumented JoinManager, this should always pass. See the run()
 *  method documentation below for information on how to instrument
 *  JoinManager for this test.
 *
 *  It is not necessary to run this under all configurations.
 * 
 *  See bug ID 4953710: JoinManager - race when terminate is called after
 *                      attribute mutator methods (ex. setAttributes).
 */
public class RaceAttrsVsTerminateULE extends AbstractBaseTest {

    private JoinManager jm;

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
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        newServiceAttrs =
               removeDups( addAttrsDup1DupAll(serviceAttrs,newServiceAttrs) );
        /* Discover & join lookups just started */
        jm = new JoinManager(testService,serviceAttrs,serviceID,
                             (DiscoveryListenerManagement) getLookupDiscoveryManager(),
                             leaseMgr,
                             sysConfig.getConfiguration());
    }//end setup

    /** Call one of the attribute mutator methods and then immediately
     *  terminate the JoinManager to test for regression. Regression occurs
     *  if an UnknownLeaseException is encountered and logged; thus, 
     *  this test should be manually run and the output should be 
     *  visually inspected for a stack trace containing UnknownLeaseException.
     *
     *  Note that in order to create the conditions that can result in
     *  an UnknownLeaseException, the JoinManager must be instrumented,
     *  and the test must be run, in the following way:
     * 
     * 1. Place a 5 second delay in JoinManager.setAttributes, just prior
     *    to the call to srvcRegistration.setAttributes(attSet)
     * 2. Place a 3 second delay in JoinManager.terminate, just prior to
     *    the call to terminateTaskMgr()
     * 3. Use the instrumented JoinManager to register a service with a
     *    lookup service and then call setAttributes() (or addAttributes()
     *    or modifyAttributes()) immediately followed by terminate().
     * 
     * The delay before terminateTaskMgr() allows time for the
     * SetAttributesTask to be queued before the task manager is 
     * terminated. And the difference in the values of the delays 
     * (the delay before setAttributes must be larger than the delay 
     * before terminateTaskMgr) allows the leases to be cancelled before 
     * the remote call (srvcRegistration.setAttributes) is made; which 
     * then results in an UnknownLeaseException from the lookup service.
     */
    public void run() throws Exception {
        /* Verify all lookups are discovered */
        mainListener.setLookupsToDiscover(lookupsStarted,
                                          toGroupsArray(lookupsStarted));
        waitForDiscovery(mainListener);
        logger.log(Level.INFO, "discovery verified: discovered "
                              +curLookupListSize("RaceAttrsVsTerminateULE.run")
                              +" lookup service(s)");
        /* Verify service has joined all lookups */
        verifyJoin();
        logger.log(Level.INFO, "join verified: joined "
                              +curLookupListSize("RaceAttrsVsTerminateULE.run")
                              +" lookup service(s)");
        /* Verify initial attributes are registered with all lookups */
        verifyPropagation(serviceAttrs);
        logger.log(Level.INFO, "initial attributes verified: registered with "
                               +curLookupListSize("RegisterAttributes.run")
                               +" lookup service(s)");
        /* Call setAttributes() immediately followed by terminate() */
        logger.log(Level.INFO,  "JoinManager.setAttributes");
        logger.log(Level.INFO,  "JoinManager.terminate");
        jm.setAttributes(newServiceAttrs);
        jm.terminate();
    }//end run

}//end class RaceAttrsVsTerminateULE
