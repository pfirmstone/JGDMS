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

package com.sun.jini.test.spec.servicediscovery.cache;

import java.util.logging.Level;

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.ServiceDiscoveryManager;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import net.jini.discovery.DiscoveryManagement2;

/**
 * This class verifies that the <code>addListener</code> method of
 * the <code>LookupCache</code> employed by the
 * <code>ServiceDiscoveryManager</code> helper utility operates as
 * specified. That is, this class verifies the following statement
 * from the specification:
 * <p><i>
 * "If <code>null</code> is input to the <code>listener</code> parameter
 * of the <code>addListener</code> method, a <code>NullPointerException</code>
 * is thrown."
 * </i><p>
 * 
 * Regression test for Bug ID 4354525
 */
public class AddListenerNPE extends AbstractBaseTest {

    private LookupDiscoveryManager  discoveryMgr = null;
    private LookupCache             lookupCache  = null;

    /** Listener class used to verify that a <code>NullPointerException</code>
     *  is not always thrown by <code>LookupCache.addListener</code>.
     */
    private class SrvcDiscoveryListener implements ServiceDiscoveryListener{
        public void serviceAdded(ServiceDiscoveryEvent evnt) { }
        public void serviceRemoved(ServiceDiscoveryEvent evnt) { }
        public void serviceChanged(ServiceDiscoveryEvent evnt) { }
    }//end class SrvcDiscoveryListener

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Creates an instance of <code>LookupDiscoveryManager</code> that
     *     will be used by the service discovery manager, which discovers
     *     no groups and no locators.
     *  2. Creates an instance of <code>ServiceDiscoveryManager</code>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        createSDMInSetup = false;
        waitForLookupDiscovery = false;
        terminateDelay = 0;
        super.setup(sysConfig);
        testDesc = "NullPointerException should occur upon adding a null "
                   +"listener to a lookup cache";
        logger.log(Level.FINE, "constructing a service discovery manager");
        discoveryMgr = 
	    new LookupDiscoveryManager(DiscoveryGroupManagement.NO_GROUPS,
				       null,
				       null,
				       sysConfig.getConfiguration());
        srvcDiscoveryMgr = 
	    new ServiceDiscoveryManager((DiscoveryManagement2) discoveryMgr,
					null,
					sysConfig.getConfiguration());
        sdmList.add(srvcDiscoveryMgr);
    }//end setup

    /** Executes the current test by doing the following:
     *  
     *  1. Requests the creation of a <code>LookupCache</code>
     *  2. Adds a non-<code>null</code> listener to the cache to verify that
     *     a <code>NullPointerException</code> is not always thrown.
     *  3. Attempts to add a <code>null</code> listener
     *  4. Verifies that a <code>NullPointerException</code> is thrown.
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    protected void applyTestDef() throws Exception {
	logger.log(Level.FINE, "requesting a lookup cache");
	lookupCache = srvcDiscoveryMgr.createLookupCache(null,null,null);
        ServiceDiscoveryListener listener = new SrvcDiscoveryListener();
	logger.log(Level.FINE, 
		   "adding a non-null listener to the lookup cache");
	lookupCache.addListener(listener);
        try {
            logger.log(Level.FINE, "adding a null listener to "
                                            +"the lookup cache");
            lookupCache.addListener(null);
            throw new TestException(" -- no exception thrown upon adding "
				    +"a null listener to the lookup cache");
        } catch(NullPointerException e) {
            logger.log(Level.FINE, "NullPointerException occurred as expected");
        }
    }
}

