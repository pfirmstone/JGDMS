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

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryManager;

import java.rmi.RemoteException;
import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.LookupServices;

/**
 * This class verifies that bug 4712396 has been fixed. As stated in the
 * bug description: "When a ServiceDiscoveryListener is added to a lookup
 * cache, only that new listener should be notified of the existing services
 * of interest in that cache. But it appears that when a second (or third
 * or fourth, etc.) listener is added to a cache, all listeners currently
 * registered with the cache receive serviceAdded events for the services
 * of interest; even though the previously registered listeners have already
 * received the expected serviceAdded events when they were each originally
 * added to the cache."
 *
 * This test attempts to simulate the environment in which the bug described
 * above will manifest itself so as to duplicate the bug prior to a fix being
 * implemented, and to verify that the bug has indeed been fixed after the
 * intended fix has been implemented in the ServiceDiscoveryManager.
 *
 * Related bug ids: 4712396
 */
public class AddListenerEvent extends AbstractBaseTest {

    protected LookupCache cache;
    protected int testServiceType;

    protected int nListeners = 2;
    protected int nAddedExpected = 0;

    protected SDMListener[] sdmListener = new SDMListener[nListeners];

    public static class SDMListener extends AbstractBaseTest.SrvcListener {
        final String testName;
        final int listenerIndx;
        public SDMListener(QAConfig config, String classname, int listenerIndx) {
            super(config,classname);
            this.testName = classname;
            this.listenerIndx = listenerIndx;
        }//end constructor
	public void serviceAdded(ServiceDiscoveryEvent event) {
            super.serviceAdded(event);
            logger.log(Level.FINEST, testName+": Listener-"+listenerIndx
                       +" ---> "+this.getNAdded()+" serviceAdded event(s)");
	}//end serviceAdded
    }//end class SDMListener

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts 1 lookup service
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();
        testDesc = "" + nLookupServices + " lookup service(s), " + nServices
                 + " service(s), " + nListeners + " ServiceDiscoveryListeners";
        nAddedExpected   = nServices;
        testServiceType  = AbstractBaseTest.TEST_SERVICE;
        for(int i=0;i<sdmListener.length;i++) {
            sdmListener[i] = new SDMListener(getConfig(), "", i);
        }//endloop
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Register the service(s) with each lookup service started during
     *     construct.
     *  2. Create a cache that discovers the service(s), and register
     *     1 listner to receive service discovery events from that cache.
     *  3. Verifies that the expected number of serviceAdded events are sent
     *     by the cache to that listener.
     *  3. Register a second listner to receive service discovery events from
     *     that cache.
     *  4. Verifies that the second listener receives the expected number of
     *     serviceAdded events
     *  5. Verifies that the first receives no more serviceAdded events
     * 
     *  @throws TestException on failure
     */
    protected void applyTestDef() throws Exception {
	/* Register new proxies */
        LookupServices lookupServices = getLookupServices();
        int nServices = lookupServices.getnServices();
        int nAttributes = lookupServices.getnAttributes();
        int nSecsServiceDiscovery = lookupServices.getnSecsServiceDiscovery();
	registerServices(0,nServices,nAttributes,testServiceType);
	/* Create a cache for the service that was registered; register
             * the first listener to receive service discovery events.
             */
	logger.log(Level.FINE, "requesting a lookup cache");
	try {
	    cache = srvcDiscoveryMgr.createLookupCache(template,
						       firstStageFilter,
						       sdmListener[0]);
	    cacheList.add(cache);
	} catch(RemoteException e) {
	    throw new TestException("RemoteException during lookup cache "
				    +"creation", e);
	}
	logger.log(Level.FINE, "wait "
		   +nSecsServiceDiscovery+" seconds to allow the "
		   +"cache to be populated ... ");
        DiscoveryServiceUtil.delayMS(nSecsServiceDiscovery*1000);
        /* 1. Verify the 1st listener received the expected event(s) */
	int indx = 0;
	int nAdded = sdmListener[indx].getNAdded();
	logger.log(Level.FINE, "listener_"+indx
		   +" ---> "+nAdded+" event(s) received, "
		   +nAddedExpected+" event(s) expected");
	if(nAdded != nAddedExpected) {
	    throw new TestException(" -- failure -- "+nAdded+" event(s) "
				    + "received, "+nAddedExpected
				    + " event(s) expected");
	}//endif
	/* 2. Register the 2nd listener and verify events */
	indx = 1;
	logger.log(Level.FINE, "adding listener_"+indx+" to cache");
	cache.addListener(sdmListener[indx]);
	nAdded = sdmListener[indx].getNAdded();
	logger.log(Level.FINE, "listener_"+indx
		   +" ---> "+nAdded+" event(s) received, "
		   +nAddedExpected+" event(s) expected");
	if(nAdded != nAddedExpected) {
	    throw new TestException(" -- failure -- "+nAdded+" event(s) "
				    +"received, "+nAddedExpected+" event(s) "
				    +"expected");
	}//endif
	/* 3. Verify the 1st listener did NOT receive any more event(s) */
	indx = 0;
	nAdded = sdmListener[indx].getNAdded();
	logger.log(Level.FINE, "listener_"+indx
		   +" ---> "+nAdded+" event(s) received, "
		   +nAddedExpected+" event(s) expected");
	if(nAdded != nAddedExpected) {
	    throw new TestException(" -- failure -- "+nAdded+" event(s) "
				    +"received, "+nAddedExpected+" event(s) "
				    +"expected");
	}//endif
    }//end applyTestDef

}//end class AddListenerEvent
