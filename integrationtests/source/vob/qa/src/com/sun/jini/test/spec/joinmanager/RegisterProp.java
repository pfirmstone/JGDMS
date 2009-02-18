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

import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is constructed, the
 * service input to the constructor is registered with all currently
 * running lookup services the join manager is configured to discover
 * (through its <code>DiscoveryManagement</code> instance); and will
 * register with any such lookup service that happens to be come on line
 * later, after registration occurs with the initial set of lookup services.
 * 
 */
public class RegisterProp extends Register {

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the test service input to the join manager
     *   constructor is registered with all currently running lookup
     *   services the join manager is configured to discover (through
     *   its <code>DiscoveryManagement</code> instance). Starts at least
     *   1 new lookup service belonging to the group(s) the discovery
     *   manager is configured to discover. Finally, verifies that the
     *   service is registered with the new lookup services(s) as well.
     */
    public void run() throws Exception {
        boolean oldUseFastTimeout = useFastTimeout;
        useFastTimeout = false;//needs max discovery time for lookup start
        super.run();

        /* Stagger-start additional lookup services */
        logger.log(Level.FINE, "starting "+nAddLookupServices
                          +" additional lookup service(s) ...");
        StaggeredStartThread lookupsThread =
             new StaggeredStartThread(lookupsStarted.size(),allLookupsToStart);
        lookupsThread.start();
        try {
            mainListener.clearAllEventInfo();
            mainListener.setLookupsToDiscover(addLookupsToStart,
                                             toGroupsArray(addLookupsToStart));
            waitForDiscovery(mainListener);
        } finally {
            /* If an exception occurred before the thread finished starting
             * all lookups, then we need to tell the thread to stop.
             *
             * If waitForDiscovery() somehow completed successfully, but the
             * thread is still running - creating lookups - then we still need
             * to tell the thread to stop so that it doesn't continue running
             * into the next test.
             */
            lookupsThread.interrupt();
            useFastTimeout = oldUseFastTimeout;//reset for next test
        }
        /* Verify registrations are propagated to new lookup services */
        logger.log(Level.FINE, "verifying join manager registers "
                          +"service with each new lookup service ...");
        verifyJoin();
        logger.log(Level.FINE, "join manager successfully registered "
                          +"TestService with all "+nAddLookupServices
                          +" additional lookup service(s)");
    }//end run

} //end class RegisterProp


