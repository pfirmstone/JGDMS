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

package com.sun.jini.test.impl.servicediscovery.event;

import java.util.logging.Level;

import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.core.lookup.ServiceItem;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>ServiceDiscoveryManager</code> handles
 * the "discard problem" in the manner described in the specification.
 *
 * The discard problem occurs when an entity discards a service from a
 * lookup cache (because the service is unavailable to the entity), but
 * the entity is not really down (the service can still communicate with
 * the lookup services with which it is registered). When this situation
 * occurs, unless the service discovery manager takes steps equivalent to
 * those described in the specification, the service may never be
 * re-discovered (because the service - since it is not actually down -
 * continues to renew its leases with the lookup services, so none of
 * those lookup services will ever re-discover the service).
 * 
 * This class simulates the situation where a service that actually goes
 * down is discarded by an entity. This class then verifies that the service 
 * discovery manager identifies the situation and "commits" the service
 * discard. Then when the service comes back on line and re-registers with
 * each lookup service, this class verifies that the service is re-discovered.
 * 
 * Related bug ids: 4355024
 */
public class DiscardDownReDiscover extends DiscardServiceDown {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        testDesc = "" + nLookupServices+" lookup service(s), "+nServices
                       +" service(s) -- discard down service and wait for "
                       +"re-discovery after re-registration";
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. After passing the initial discard test, re-registers the service
     *     verifies that the service is re-discovered. 
     */
    protected void applyTestDef() throws Exception {
        super.applyTestDef();
	/* Re-register the original service(s) */
	logger.log(Level.FINE, "re-registering the original "
		   +"services");
	registerServices(0,nServices,nAttributes,testServiceType);
	/* Wait for the cache to populate. */
	logger.log(Level.FINE, "wait "
		   +nSecsServiceDiscovery+" seconds to allow the "
		   +"cache to be populated ... ");
        DiscoveryServiceUtil.delayMS(nSecsServiceDiscovery*1000);
	logger.log(Level.FINE, "# serviceAdded events = "
		   +srvcListener.getNAdded());
	/* Verify the expected # of added & removed events have arrived */
	nAddedExpected = nAddedExpected + (1*nServices);
	if(     (srvcListener.getNAdded()   != nAddedExpected) 
		|| (srvcListener.getNRemoved() != nRemovedExpected) )
            {
                logger.log(Level.FINE, ""
			   +"# serviceAdded events expected = "
			   +nAddedExpected
			   +", # serviceAdded events received = "
			   +srvcListener.getNAdded()
			   +", # serviceRemoved events expected = "
			   +nRemovedExpected
			   +", # serviceRemoved events received = "
			   +srvcListener.getNRemoved());
                throw new TestException("# added expected = "+nAddedExpected
                                  +", # added received = "
                                  +srvcListener.getNAdded()
                                  +", # removed expected = "+nRemovedExpected
                                  +", # removed received = "
                                  +srvcListener.getNRemoved());
            }
	/* Re-query the cache for the desired registered service. */
	logger.log(Level.FINE, "re-querying the cache for the "
		   +"service references ...");
	ServiceItem[] srvcItem = cache.lookup(secondStageFilter,
					      Integer.MAX_VALUE);
	verifyQueryResults(srvcItem,"last query");
    }//end applyTestDef

}//end class DiscardDownReDiscover


