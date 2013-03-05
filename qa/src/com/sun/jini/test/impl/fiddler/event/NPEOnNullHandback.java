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

package com.sun.jini.test.impl.fiddler.event;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import com.sun.jini.qa.harness.TestException;

import net.jini.core.discovery.LookupLocator;

import java.rmi.RemoteException;

/**
 * This class verifies that when the fiddler implementation of the lookup
 * discovery service is configured for event debugging, and a registration
 * is requested with fiddler in which <code>null</code> is input to the
 * the <code>handback</code> parameter, the debug mechanism employed by
 * fiddler can handle the <code>null</code> handback that arrives in events
 * sent to the registration's listener.
 *
 * This test was written to test the fiddler implementation of the lookup
 * discovery service when it is used in the following way:
 * <p><ul>
 *   <li> a lookup service is started belonging to some group, say 'g0'
 *   <li> a registration, with a null handback, is made with the lookup
 *        discovery service, requesting that group 'g0' be discovered
 *   <li> the listener for the registration should receive the expected
 *        remote discovery event for group 'g0' which references the null
 *        handback
 *   <li> upon receiving the event from the lookup discovery service, the
 *        debug mechanism should successful process the null handback without
 *        throwing a NullPointerException
 * </ul><p>
 *
 * If the fiddler implementation of the lookup discovery service functions as
 * intended, then upon receiving an event from fiddler, the debug mechanism
 * should successfully process a <code>null</code> handback in the event
 * without throwing a <code>NullPointerException</code>.
 * 
 * Regression test for Bug ID 4427873
 *
 */
public class NPEOnNullHandback extends AbstractBaseTest {

    /** Constructs an instance of this class. Initializes this classname */
    public NPEOnNullHandback() {
        subCategories = new String[] {"discoveryserviceevent"};
    }//end constructor

    /** Executes the current test by doing the following:
     * <p><ul>
     * <li> requests one registration with the fiddler implementation of the
     *      lookup discovery service, requesting a null handback
     * <li> verifies that the registration's listener receives the expected
     *      event, and successfully processes the null handback without
     *      throwing a NullPointerException
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINER, "run()");
        String[] groups = getGroupsToDiscover(getUseOnlyGroupDiscovery());
        LookupLocator[] noLocs = new LookupLocator[0];
	logger.log(Level.FINER, "lookup discovery service "
		   +"registration_-1 with NULL handback --");
	doRegistration(groups,noLocs,-1,leaseDuration);
	logger.log(Level.FINER, "wait for discovery event debug info");
	waitForDiscovery();
	logger.log(Level.FINER, "wait period complete");
	/* If a TestException is thrown before reaching this point, then it
	 * is likely that the expected discovery event did not arrive in
	 * time because fiddler did not send it due to a
	 * NullPointerException while attempting to write debug event info
	 * to the activation system output. Check that output for 
	 * the expected contents.
	 */
    }//end run
} //end class NPEOnNullHandback
