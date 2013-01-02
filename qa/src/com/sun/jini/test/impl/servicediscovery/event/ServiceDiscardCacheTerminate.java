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
import com.sun.jini.qa.harness.Test;
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
 * This class verifies that the tasks involved in the service discard process
 * are terminated correctly when the cache itself is terminated.
 * 
 * Related bug ids: 4355024
 */
public class ServiceDiscardCacheTerminate extends DiscardServiceUp {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        testDesc = ""+getnLookupServices()+" lookup service(s), "+getnServices()
                       +" service(s) -- service discard timer task "
                       +"termination when cache is terminated";
        nAddedExpected   = 1*getnServices();
        nRemovedExpected = 1*getnServices();
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Register the service(s) with each lookup service started during
     *     construct.
     *  2. Create a cache that discovers the service(s), and register
     *     for service discovery events from that cache.
     *  3. Verifies the service was discovered by the cache by invoking
     *     lookup() on the cache.
     *  4. Discards the service returned by the call to lookup() to cause
     *     creation of tasks that handle the discard problem
     *  5. Terminates the cache
     *  6. Cancels all service leases to generate service events.
     *  7. Wait for serviceRemoved events.
     *  8. Verifies that expected number of serviceAdded and serviceRemoved
     *     events arrived.
     */
    protected void applyTestDef() throws Exception {
        long addedWait = getAddedWait();
	regServicesAndCreateCache();
	/* Query the cache for the desired registered service. */
	logger.log(Level.FINE, ""+": querying the cache for the "
		   +"service reference(s)");
	ServiceItem srvcItem[] = cache.lookup(secondStageFilter,
					      Integer.MAX_VALUE);
	/* Verify the results of the cache query. */
	verifyQueryResults(srvcItem,"first query");
	logger.log(Level.FINE, ""+": # serviceAdded events = "
		   +srvcListener.getNAdded());
	/* Discard all the services to cause the creation of timer tasks */
	doServiceDiscard(srvcItem);
	/* Terminate the cache */
	logger.log(Level.FINE, ""+": terminating the lookup cache");
	cache.terminate();
	/* Cancel all service leases to generate service events */
	logger.log(Level.FINE, ""+": cancelling all service leases "
		   +"to generate MATCH_NOMATCH remote events");
	unregisterServices();
	/* Wait for serviceRemoved events */
	waitForServiceEvents(addedWait);
    }//end applyTestDef

}//end class ServiceDiscardCacheTerminate


