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

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.lookup.LookupCache;

import java.rmi.RemoteException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that when services that are to be discovered are
 * implemented with a well defined ("good") equals() method, the event
 * mechanism of the cache will operate in a predictable fashion, sending
 * a number of serviceAdded and serviceRemoved events.
 *
 * With respect to services defined with a "good" equals() method, the
 * lookup cache will always view different instances of such a service as
 * being the same service since different instances of the service will
 * have the same service ID. Based on this fact, the number of serviceAdded
 * and serviceRemoved events can be computed from the number of services and
 * the number of lookup services with which each service has registered.
 */
public class ReRegisterGoodEquals extends AbstractBaseTest {

    protected LookupCache cache;
    protected volatile int testServiceType;

    protected int nAddedExpected   = 0;
    protected int nRemovedExpected = 0;
    protected int nChangedExpected = 0;

    protected AbstractBaseTest.SrvcListener srvcListener;


    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        int nServices = getnServices();
        testDesc = ""+getnLookupServices()+" lookup service(s), " + nServices
                       +" service(s) with well-defined equals() method";
        nAddedExpected   = nServices*2;
        nRemovedExpected = nAddedExpected-nServices;
        testServiceType  = AbstractBaseTest.TEST_SERVICE;
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Register the service(s) with each lookup service started during
     *     construct.
     *  2. Create a cache that discovers the service(s), and register
     *     for service discovery events from that cache.
     *  3. After the original services have been discovered and all expected
     *     notifications have arrived, register a new version of each
     *     of the previously registered services.
     *
     *     That is, each new version should be associated with a new service
     *     value so that the equals() method returns false, but each new
     *     version should have the same service ID as its original counterpart
     *     so that the cache will interpret the different versions as the same
     *     service.
     *  4. Verifies that the expected number of serviceAdded and serviceRemoved
     *     events are sent by the cache.
     */
    protected void applyTestDef() throws Exception {
	/* Register new proxies */
	registerServices(0, getnServices(), getnAttributes(),testServiceType);
	/* Create a cache for the services that were registered. */
	try {
	    logger.log(Level.FINE, "requesting a lookup cache");
	    srvcListener = new AbstractBaseTest.SrvcListener
		(getConfig(),"");
	    cache = srvcDiscoveryMgr.createLookupCache(template,
						       firstStageFilter,
						       srvcListener);
	} catch(RemoteException e) {
	    throw new TestException(" -- RemoteException during lookup cache "
				    +"creation");
	}
	logger.log(Level.FINE, "wait {0}"+" seconds to allow the "
		   +"cache to be populated ... ", getnSecsServiceDiscovery());
        DiscoveryServiceUtil.delayMS(getnSecsServiceDiscovery()*1000);
        /* Re-register new proxies */
	reRegisterServices(0, getnServices(), getnAttributes(),testServiceType);
	logger.log(Level.FINE, "wait {0}"+" seconds to allow the "
		   +"cache to be re-populated ... ", getnSecsServiceDiscovery());
        DiscoveryServiceUtil.delayMS(getnSecsServiceDiscovery()*1000);
	int nAdded   = srvcListener.getNAdded();
	int nRemoved = srvcListener.getNRemoved();
        int nChanged = srvcListener.getNChanged();
	logger.log(Level.FINE, 
                "nAdded = {0}, nAddedExpected = {1}, nRemoved = {2}, nRemovedExpected = {3}, nChanged = {4}, nChangedExpected = {5}",
                new Object[]{nAdded, nAddedExpected, nRemoved, nRemovedExpected, nChanged, nChangedExpected});
	if((nAdded != nAddedExpected) || (nRemoved != nRemovedExpected)) {
	    throw new TestException(" -- failure -- nAdded = "+nAdded
				    +", nAddedExpected = "+nAddedExpected
				    +", nRemoved = "+nRemoved
				    +", nRemovedExpected = "+nRemovedExpected);
	}
    }//end applyTestDef

}//end class ReRegisterGoodEquals
