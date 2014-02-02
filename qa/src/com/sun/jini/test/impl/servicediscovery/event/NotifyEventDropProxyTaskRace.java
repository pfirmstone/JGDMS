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
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
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
 * ProxyRegDropTask:
 *
 * - 1 LUS {L0}
 * - 1 services {s0}, to be registered in L0
 * - 1 cache C0 with template matching s0
 *
 * This test attempts to simulate the race that appears to be possible
 * between the NotifyEventTask and the ProxyRegDropTask. This test 
 * starts lookup L0 and creates cache C0. It then registers s0 with L0
 * to generate a NOMATCH_MATCH event and ultimately initiate an instance
 * of NotifyEventTask. Suppose that before NotifyEventTask can modify the
 * serviceIdMap, L0 is discarded so that the ProxyRegDropTask will be
 * initiated. Without the proposed fix implemented in the SDM, it's then
 * possible that L0 will be discarded before NotifyEventTask inserts the
 * mapping { [s0,L0] } in serviceIdMap, which may result (if the timing is
 * right) in NotifyEventTask placing { [s0,L0] } in serviceIdMap after L0
 * has been discarded; which means tha contents of serviceIdMap will be
 * inconsistent, and the serviceRemoved event that should occur because
 * of the discard, will never actually occur.
 *
 * The race occurs as follows:
 *
 *                          o L0 created
 *                          o C0 created
 *                          o s0 registered with L0
 *                          o L0 sends NO_MATCH_MATCH
 *
 *          NotifyEventTask                       ProxyRegDropTask
 *   -----------------------------     ----------------------------------------
 *   o task0 determine s0 is "new"
 *   o sleep for n seconds
 *                          o L0 is discarded
 *                                     o remove L0 from proxyRegSet
 *                                     o serviceIdMap is empty, do nothing else
 *                                     o thinking map should be empty, return
 *   o add new s0 to serviceIdMap
 *   o map NOT empty now but should be
 *
 * The result is that serviceIdMap should be empty and L0 should not be 
 * in proxyRegSet. But if NotifyEventTask is too slow in processing the
 * new s0, ProxyRegDropTask will have nothing to process and so the
 * serviceIdMap will not be empty, and the serviceRemoved event that should
 * have been sent because the [s0,L0] pair was removed from the serviceIdMap
 * is never sent.
 *
 * Although this test can be run against an unmodified SDM, the situation
 * described above does not occur consistently unless the SDM is modified
 * to insert a time delay in the appropriate place.
 *
 * Related bug ids: 4675746
 *                  4707125
 */
public class NotifyEventDropProxyTaskRace extends AbstractBaseTest {

    static final int LOOKUP_TASK_RACE                  = 0;
    static final int LOOKUP_DROP_PROXY_TASK_RACE       = 1;
    static final int DROP_PROXY_LOOKUP_TASK_RACE       = 2;
    static final int NOTIFY_EVENT_DROP_PROXY_TASK_RACE = 3;
    static final int INTERRUPT_NOTIFY                  = 4;

    private static int thisTestType = NOTIFY_EVENT_DROP_PROXY_TASK_RACE;

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
        public SDMListener(QAConfig config, String classname) {
            super(config,classname);
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
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig config)
                                                            throws Exception
    {
	// from constructor, apparently never accessed
        System.setProperty( "sdm.testType", String.valueOf(thisTestType) );
        super.construct(config);
        nAddedExpected   = getnAddServices();
        nRemovedExpected = nAddedExpected;
        testServiceType  = AbstractBaseTest.TEST_SERVICE;
        testDesc = ""+getnLookupServices()+" lookup service(s), "
                       +(getnServices()+getnAddServices())+" service(s), should receive "
                       +nAddedExpected+" serviceAdded and "+nRemovedExpected
                       +" serviceRemoved event(s)";
        ldm = (LookupDiscoveryManager)(srvcDiscoveryMgr.getDiscoveryManager());
        srvcListener = new SDMListener(config,"");
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Create a cache to initiate the RegisterListenerTask/LookupTask combo
     *  2. Destroy the lookup service so it won't be re-discovered
     *  3. Discard the lookup service to initiate the ProxyRegDropTask
     */
    protected void applyTestDef() throws Exception {
	lus = (ldm.getRegistrars())[0];
	try {
	    logger.log(Level.FINE, "create cache and register for events");
	    cache = srvcDiscoveryMgr.createLookupCache(template,
						       firstStageFilter,
						       srvcListener);
	    cacheList.add(cache);
            logger.log(Level.FINE, 
		       "cache created ... wait "+nSecsRemoteCall
		       +" seconds for event registration to complete");
            DiscoveryServiceUtil.delayMS(nSecsRemoteCall*1000);
	    /* kick off the NotifyEventTask/NewOldServiceTask combo */
	    logger.log(Level.FINE, "wait over ... register "+getnAddServices()
		       +" service to cause NOMATCH_MATCH to "
		       +"initiate NotifyEventTask");
	    registerServices
		(getnServices(), getnAddServices(), getnAttributes(),testServiceType);
	    logger.log(Level.FINE, "service registration call completed");
            logger.log(Level.FINE, 
		       "wait "+getnSecsServiceDiscovery()+" seconds to allow "
		       +"serviceAdded event(s) to arrive after service "
                       +"registration");
            DiscoveryServiceUtil.delayMS(getnSecsServiceDiscovery()*1000);
	    logger.log(Level.FINE,
		       "service(s) registered ... DISCARD lookup "
		       +"service to initiate ProxyRegDropTask");
	    /* Turn off discovery so the lookup isn't discovered again */
	    ldm.setLocators(NO_LOCS);
	    ldm.setGroups(NO_GROUPS);
	    /* kick off the ProxyRegDropTask */
	    ldm.discard(lus);
	} catch(RemoteException e) {
	    throw new TestException(" -- RemoteException during lookup cache "
				    +"creation", e);
	}
        logger.log(Level.FINE, "waiting "+nSecsServiceEvent+" seconds to "
                   +"allow serviceRemoved event(s) to arrive after lookup "
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

}//end class NotifyEventDropProxyTaskRace
