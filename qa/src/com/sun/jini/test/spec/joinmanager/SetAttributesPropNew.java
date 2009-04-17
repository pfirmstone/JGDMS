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
 * this class verifies that when the <code>setAttributes</code> method
 * is invoked, the join manager will not only propagate the new attributes
 * to each lookup service with which the test service is currently registered,
 * but will also propagate those attributes to each new lookup service
 * that may be started after the service's attributes have been replaced.
 * 
 */
public class SetAttributesPropNew extends SetAttributesProp {

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that when the <code>setAttributes</code> method is invoked,
     *   the join manager will not only propagate the new attributes
     *   to each lookup service with which the test is currently registered,
     *   but will also propagate those attributes to each new lookup service
     *   that may be started after the service's attributes have been
     *   augmented.
     */
    public void run() throws Exception {
        boolean oldUseFastTimeout = useFastTimeout;
        useFastTimeout = false;//needs max discovery time for lookup start
        /* Set attributes and verify propagation to current lookup services */
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
        /* Verify attribute propagation to new lookup services */
        logger.log(Level.FINE, "verifying all attributes were "
                          +"propagated to each new lookup service ...");
        verifyPropagation(expectedAttrs,nSecsJoin);
        logger.log(Level.FINE, "join manager successfully propagated "
                          +"all attributes to the new lookup service(s)");
    }//end run

} //end class SetAttributesPropNew

