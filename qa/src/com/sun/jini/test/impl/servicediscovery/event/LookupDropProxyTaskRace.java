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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.rmi.RemoteException;
import java.util.logging.Level;

/**
 * This test attempts to simulate the following race condition that
 * can occur between an instance of LookupTask and an instance of 
 * DropProxyRegTask:
 *
 * - 1 LUS {L0}
 * - 1 service s0 registered in L0
 * - 1 cache C0 with template matching s0
 *
 * Upon creation of the cache, LookupTask is initiated for L0. The test
 * waits a few seconds after the cache is created because if L0 is discarded
 * too quickly, L0 will be removed from the proxyRegSet before LookupTask
 * has a chance to begin any processing. After the wait period is up,
 * L0 is discarded; which initiates the DropProxyRegTask. The race occurs
 * as follows:
 *          LookupTask                       DropProxyRegTask
 *   ----------------------------   ----------------------------------------
 *   o determine s0 is "new"
 *   o sleep for n seconds          o remove L0 from proxyRegSet
 *                                  o serviceIdMap is empty, do nothing else
 *   o add new s0 to serviceIdMap
 *
 * The result is that serviceIdMap should NOT be empty when DropProxyRegTask
 * encounters it. But if LookupTask is too slow in adding s0 to the map,
 * DropProxyRegTask will have nothing to remove, and so will return without
 * modifying serviceIdMap. But when LookupTask returns, s0 will be contained
 * in serviceIdMap; even though it shouldn't.
 *
 * In order to insert the time delay, the SDM must be modified. Also, in
 * order to observe the race, println's must be inserted in the SDM to 
 * display whether the serviceIdMap is empty/non-empty when it is supposed
 * to be empty/non-empty. That is, the pass/fail status of this test cannot
 * be determined by the test itself; it must be observed by the test
 * engineer. Thus, this test will always return a pass status.
 *
 * This test is not part of the regular suite. It must be run manually, with
 * a temporarily-modified SDM.
 *
 * Related bug ids: 4675746
 *                  4707125
 */
public class LookupDropProxyTaskRace extends AbstractBaseTest {

    static final int LOOKUP_TASK_RACE                  = 0;
    static final int LOOKUP_DROP_PROXY_TASK_RACE       = 1;
    static final int DROP_PROXY_LOOKUP_TASK_RACE       = 2;
    static final int NOTIFY_EVENT_DROP_PROXY_TASK_RACE = 3;
    static final int INTERRUPT_NOTIFY                  = 4;

    private static int thisTestType = LOOKUP_DROP_PROXY_TASK_RACE;

    protected LookupDiscoveryManager ldm;
    protected ServiceRegistrar lus;

    protected LookupCache cache;
    protected int testServiceType;

    protected int nAddedExpected   = 0;
    protected int nRemovedExpected = 0;

    protected SDMListener srvcListener;

    private static final String[] NO_GROUPS = new String[0];
    private static final LookupLocator[] NO_LOCS = new LookupLocator[0];

    public static class SDMListener extends AbstractBaseTest.SrvcListener {
        String testName;
        public SDMListener(QAConfig config, String classname) {
            super(config,classname);
            this.testName = classname;
        }//end constructor
	public void serviceAdded(ServiceDiscoveryEvent event) {
            super.serviceAdded(event);
            logger.log(Level.FINE, ""+this.getNAdded()
                                   +" -- serviceAdded event(s) received");
	}//end serviceAdded
	public void serviceRemoved(ServiceDiscoveryEvent event) {
            super.serviceRemoved(event);
            logger.log(Level.FINE, ""+this.getNRemoved()
                                   +" -- serviceRemoved event(s) received");
	}//end serviceAdded
    }//end class SDMListener

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts 1 lookup service
     *  2. Creates a service discovery manager that discovers the lookup
     *     service that is started
     *  3. Creates a template that will match the test service based on
     *     service type only
     */
    public Test construct(QAConfig config) throws Exception {
	// the next line used to be in the constructor. Don't see where
	// this property is ever used
        System.setProperty( "sdm.testType", String.valueOf(thisTestType) );
        super.construct(config);
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();
        int nAddServices = getLookupServices().getnAddServices();
        nAddedExpected   = nServices;
        nRemovedExpected = nAddedExpected;
        testDesc = ""+nLookupServices+" lookup service(s), "+nServices
                       +" service(s), should receive "+nServices+" event(s)";
        testServiceType  = AbstractBaseTest.TEST_SERVICE;
        testDesc = ""+nLookupServices+" lookup service(s), "
                       +(nServices+nAddServices)+"service(s), should receive "
                       +nAddedExpected+" serviceAdded and "+nRemovedExpected
                       +" serviceRemoved event(s)";
        ldm = (LookupDiscoveryManager)(srvcDiscoveryMgr.getDiscoveryManager());
        srvcListener = new SDMListener(config,"");
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Create a cache to initiate the RegisterListenerTask/LookupTask combo
     *  2. Set groups and locators so the lookup service won't be re-discovered
     *  3. Discard the lookup service to initiate the DropProxyRegTask
     */
    protected void applyTestDef() throws Exception {
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();
        int nAttributes = getLookupServices().getnAttributes();
	logger.log(Level.FINE, "pre-register "+nServices
		   +" service(s) with the "+nLookupServices+" lookup "
		   +"service(s)");
	registerServices(0,nServices,nAttributes,testServiceType);
	lus = (ldm.getRegistrars())[0];
	try {
	    /* kick off the RegisterListener/LookupTask combo */
	    logger.log(Level.FINE, "create cache and register for events to "
		       +"initiate "+nLookupServices+" LookupTask(s)");
	    srvcListener = new SDMListener(getConfig(),"");
	    cache = srvcDiscoveryMgr.createLookupCache(template,
						       firstStageFilter,
						       srvcListener);
	    cacheList.add(cache);
	    //delay to allow LookupTask to get started before L0
	    //is removed from proxyRegSet when it is discarded below
	    logger.log(Level.FINE, "cache created ... "
		       +"wait 5 seconds to allow LookupTask(s) "
		       +"to begin executing discarding");
	    DiscoveryServiceUtil.delayMS(5000);
	    logger.log(Level.FINE, "wait over ... "
		       +"discarding all lookup service(s)");
	    /* Turn off discovery so the lookup isn't discovered again */
	    ldm.setLocators(NO_LOCS);
	    ldm.setGroups(NO_GROUPS);
	    ldm.discard(lus);//kick off the ProxyRegDropTask
	    logger.log(Level.FINE, "lookup service(s) discarded");
	} catch(RemoteException e) {
	    throw new TestException(" -- RemoteException during lookup "
				    +"cache creation", e);
	}
        logger.log(Level.FINE, "waiting "+nSecsServiceEvent+" seconds to "
                   +"allow all event(s) to arrive after lookup "
                   +"service discard ... ");
        DiscoveryServiceUtil.delayMS(nSecsServiceEvent*1000);
	logger.log(Level.FINE, "wait over");
	int nAdded   = srvcListener.getNAdded();
	int nRemoved = srvcListener.getNRemoved();
	logger.log(Level.FINE, "nAdded   = "+nAdded
		   +", nAddedExpected   = "+nAddedExpected);
	logger.log(Level.FINE, "nRemoved = "+nRemoved
		   +", nRemovedExpected = "+nRemovedExpected);
	logger.log(Level.FINE, "done ... shutting down the test");
	if((nAdded != nAddedExpected) || (nRemoved != nRemovedExpected)) {
	    throw new TestException(" -- failure -- nAdded = "+nAdded
				    +", nAddedExpected = "+nAddedExpected
				    +"; nRemoved = "+nRemoved
				    +", nRemovedExpected = "+nRemovedExpected);
	}//endif
    }//end applyTestDef

}//end class LookupDropProxyTaskRace