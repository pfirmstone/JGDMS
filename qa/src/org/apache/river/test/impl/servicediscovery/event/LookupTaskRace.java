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

package org.apache.river.test.impl.servicediscovery.event;

import org.apache.river.test.spec.servicediscovery.AbstractBaseTest;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.DiscoveryServiceUtil;

import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;

import java.rmi.RemoteException;
import java.util.logging.Level;

/**
 * This test attempts to simulate the following race condition that
 * can occur between different instances of LookupTask:
 *
 * - 2 LUS's {L0,L1}
 * - 1 service s0 registered in L0 and L1
 * - 1 cache C0 with template matching s0
 *
 * Upon creation of the cache, 2 LookupTasks are queued; one for L0 and
 * one for L1. Both tasks modify the serviceIdMap. Suppose the first task 
 * determines that the service is "new", but before it can update the
 * serviceIdMap, the context switches to the other task. Because the first
 * has not yet updated the serviceIdMap, the second task should then also
 * view the service as new. Thus, because both tasks view the service
 * as new, each task sends a serviceAdded event; when only one is supposed
 * to be sent.
 *
 * The race occurs as follows:
 *
 *                    o L0 created
 *                    o L1 created
 *                    o s0 registered in L0 and L1
 *                    o C0 created with listener and template for s0
 *                    o new C0 initiates a RegisterListener
 *                    o RegisterListenerTask registers for events from {L0,L1}
 *                    o RegisterLisenerTask initiates a LookupTask for {L0,L1}
 *
 *          LookupTask-L0                      LookupTask-L1 
 *   -----------------------------     -----------------------------
 *   o task0 determine s0 is "new"
 *   o sleep for n seconds
 *                                     o task1 determine s0 is "new"
 *                                     o add new s0 to serviceIdMap
 *                                     o send serviceAdded event
 *   o sleep expires
 *   o add new s0 to serviceIdMap
 *   o send serviceAdded event
 *
 * Although this test can be run against an unmodified SDM, the situation
 * described above does not occur consistently unless the SDM is modified
 * to insert a time delay in the appropriate place. 
 * 
 * This class verifies that bug 4675746 has been fixed. As stated in the
 * bug description: "The operations performed in addService() appear NOT
 * to be performed atomically; which may result in a race condition." 
 *
 * This bug was reported by a user who claimed that his application was
 * receiving unexpected duplicate serviceAdded() events for a single service
 * when the service registers with multiple lookup services; indicating a
 * possible race condition in the addService() mechanism.
 *
 * This test attempts to simulate the user's described environment to
 * duplicate the bug prior to a fix being implemented, and to verify that
 * the bug has indeed been fixed after the intended fix has been implemented
 * in the ServiceDiscoveryManager.
 *
 * Related bug ids: 4675746
 *                  4707125
 */
public class LookupTaskRace extends AbstractBaseTest {

    static final int LOOKUP_TASK_RACE                  = 0;
    static final int LOOKUP_DROP_PROXY_TASK_RACE       = 1;
    static final int DROP_PROXY_LOOKUP_TASK_RACE       = 2;
    static final int NOTIFY_EVENT_DROP_PROXY_TASK_RACE = 3;
    static final int INTERRUPT_NOTIFY                  = 4;

    private static int thisTestType = LOOKUP_TASK_RACE;

    protected LookupCache cache;
    protected int testServiceType;

    protected int nAddedExpected = 0;

    protected SDMListener srvcListener;

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
    }//end class SDMListener

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts 2 lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services that are started
     *  3. Creates a template that will match the test service based on
     *     service type only
     */
    public Test construct(QAConfig config) throws Exception {
	// from constructor, apparently never accessed
        System.setProperty( "sdm.testType", String.valueOf(thisTestType) );
        super.construct(config);
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();
        int nAddServices = getLookupServices().getnAddServices();
        nAddedExpected  = nServices;
        testServiceType = AbstractBaseTest.TEST_SERVICE;
        testDesc = ": "+nLookupServices+" lookup service(s), "
                       +(nServices+nAddServices)+"service(s), should receive "
                       +nAddedExpected+" serviceAdded event(s)";
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Register the service with each lookup service started during
     *     construct.
     *  2. Create a cache that discovers the service, and register
     *     for service discovery events from that cache.
     *  3. Verifies that the expected number of serviceAdded events are sent
     *     by the cache.
     */
    protected void applyTestDef() throws Exception {
        int nServices = getLookupServices().getnServices();
        int nAttributes = getLookupServices().getnAttributes();
        int nSecsServiceDiscovery = getLookupServices().getnSecsServiceDiscovery();
	/* Register new proxies */
	registerServices(0,nServices,nAttributes,testServiceType);
	/* Create a cache for the services that were registered. */
	try {
	    logger.log(Level.FINE, 
		       "create cache and register for events "
		       +"to initiate the LookupTask");
	    srvcListener = new SDMListener(getConfig(),"");
	    cache = srvcDiscoveryMgr.createLookupCache(template,
						       firstStageFilter,
						       srvcListener);
	} catch(RemoteException e) {
	    throw new TestException(" -- RemoteException during lookup cache "
			      +"creation", e);
	}
	logger.log(Level.FINE, "wait "
		   +nSecsServiceDiscovery+" seconds to allow the "
		   +"cache to be populated ... ");
        DiscoveryServiceUtil.delayMS(nSecsServiceDiscovery*1000);
	logger.log(Level.FINE, "wait over ... analyze event(s) received");
	int nAdded   = srvcListener.getNAdded();
	logger.log(Level.FINE, "nAdded = "+nAdded
		   +", nAddedExpected = "+nAddedExpected);
	logger.log(Level.FINE, "done ... shutting down the test");
	if(nAdded != nAddedExpected) {
	    throw new TestException(" -- failure -- nAdded = "+nAdded
			      +", nAddedExpected = "+nAddedExpected);
	}
    }//end applyTestDef

}//end class LookupTaskRace