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

import com.sun.jini.logging.Levels;
import com.sun.jini.qa.harness.Test;

import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryManager;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * This test attempts to simulate the following race condition that
 * can occur between an instance of UnmapProxyTask (created and queued
 * in LookupTask) and instances of NewOldServiceTask that are created
 * and queued by NotifyEventTask:
 *
 * - 1 LUS {L0}
 * - N (~250) services {s0, s1, ..., sN-1}, to be registered in L0
 * - M (~24) SDMs, each with 1 cache with template matching all si's
 *   {SDM_0/C0, SDM_1/C1, ... SDM_M-1/CM-1}
 *
 * Through the shear number of service registrations, caches, and events,
 * this test attempts to produce the conditions that cause the regular
 * occurrence of the race between an instance of UnmapProxyTask and
 * instances of NewOldServiceTask produced by NotifyEventTask when a
 * service event is received from L0.
 *
 * This test starts lookup L0 during construct. Then, when the test begins
 * running, half the services are registered with L0, followed by the
 * creation of half the SDMs and corresponding caches; which causes the
 * tasks being tested to be queued, and event generation to ultimately
 * begin. After registering the first half of the services and creating
 * the first half of the SDMs, the remaining services are registered and
 * the remaining SDMs and caches are created. As events are generated,
 * the number of serviceAdded and serviceRemoved events are tallied.
 *
 * When an SDM_i/cach_i pair is created, an instance of RegisterListenerTask
 * is queued and executed. RegisterListenerTask registers a remote event
 * listener with L0's event mechanism. When the services are registered with
 * L0, that listener receives service events; which causes NotifyEventTask
 * to be queued and executed. After RegisterListerTask registers for events
 * with L0, but before RegisterListenerTask exits, an instance of LookupTask
 * is queued and executed. LookupTask retrieves from L0 a "snapshot" of its
 * state. Thus, while events begin to arrive informing each cache of the
 * services that are registering with L0, LookupTask is querying L0 for
 * its current state.
 *                   
 * Upon receipt of a service event, NotifyEventTask queues a NewOldServiceTask
 * to determine if the service corresponding to the event represents a new
 * service that has been added, a change to a previously-registered service,
 * or the removal of a service from L0. If the event corresponds to a newly
 * registered service, the service is added to the cache's serviceIdMap and
 * a serviceAdded event is sent to any listeners registered with the cache.
 * That is,
 *
 * Service event received
 *
 *   NotifyEventTask {
 *     if (service removed) {
 *       remove service from serviceIdMap
 *       send serviceRemoved
 *     } else {
 *       NewOldServiceTask
 *         if (service changed) {
 *           send serviceChanged
 *         } else if (service is new) {
 *           add service to serviceIdMap
 *           send serviceAdded
 *         }
 *     }
 *   }
 *
 * While events are being received and processed by NotifyEventTask and
 * NewOldServiceTask, LookupTask is asynchronously requesting a snapshot
 * of L0's state and attempting to process that snapshot to populate
 * the same serviceIdMap that is being populated by instances of 
 * NewOldServiceTask that are initiated by NotifyEventTask. LookupTask
 * first examines serviceIdMap, looking for services that are NOT in the
 * snapshot; that is, services that are not currently registered with L0.
 * Such a service is referred to as an, "orphan". For each orphan service
 * that LookupTask finds, an instance of UnmapProxyTask is queued. That task
 * removes the service from the serviceIdMap and sends a serviceRemoved
 * event to any listeners registered with the cache. After processing
 * any orphans that it finds, LookupTask then queues an instance of
 * NewOldServiceTask for each service in the snapshot previously retrieved.
 * That is,
 *
 * LookupTask - retrieve snapshot {
 *
 *   for each service in serviceIdMap {
 *     if (service is not in snapshot) { //orphan
 *       UnmapProxyTask {
 *         remove service from serviceIdMap
 *         send serviceRemoved
 *       }
 *     }
 *   }
 *   for each service in snapshot {
 *       NewOldServiceTask
 *         if (service changed) {
 *           send serviceChanged
 *         } else if (service is new) {
 *           add service to serviceIdMap
 *           send serviceAdded
 *         }
 *   }
 * }
 *
 * The race can occur because the NewOldServiceTasks that are queued by the
 * NotifyEventTasks can add services to the serviceIdMap between the time 
 * LookupTask retrieves the snapshot and the time it analyzes the serviceIdMap
 * for orphans. That is, 
 * 
 *                        o SDM_i/cache_i created
 * RegisterListenerTask 
 * --------------------     
 *  register for events
 *  LookupTask
 *  ---------- 
 *   retrieve snapshot {s0,s1,s2}
 *                        o s3 registered with L0
 *                        o L0 sends NO_MATCH_MATCH
 *                                                   NotifyEventTask
 *                                                   ---------------
 *                                                     NewOldServiceTask
 *                                                     -----------------
 *                                                     add s3 to serviceIdMap
 *                                                     send serviceAdded event
 *   ORPHAN: s3 in serviceIdMap, not snapshot
 *   UnmapProxyTask
 *   --------------
 *     remove s3 from serviceIdMap
 *     send serviceRemoved event
 *
 * This test returns a pass when no race is detected between UnmapProxyTask
 * and any NewOldServiceTask initiated by a NotifyEventTask. This is 
 * determined by examining the serviceAdded and serviceRemoved event
 * tallies collected during test execution. If, for each SDM/cache 
 * combination, the number of serviceAdded events received equals the
 * number of services registered with L0, and no serviceRemoved events
 * are received, then there is no race, and the test passes; otherwise,
 * the test fails (in particular, if at least one serviceRemoved event
 * is sent by at least one SDM/cache).
 *
 * No special modifications to the SDM are required to cause the race
 * condition to occur consistently. When running this test individually
 * on Solaris, out of "the vob", under a JERI or JRMP configuration, and
 * with 24 SDMs/caches and 250 services, the race condition was consistently
 * observed (until a fix was integrated). Thus, it appears that the greater
 * the number of SDMs/caches/services, the greater the probability the
 * conditions for the race will be encountered. 
 *
 * Related bug ids: 6291851
 */
public class LookupTaskServiceIdMapRace extends AbstractBaseTest {

    private static final int N_SDM      = 2*12; //2*12; //always even
    private static final int N_SERVICES = 2*125; //2*125;//always even
    private static final int MAX_N_SECS = 2*240;

    private static boolean inShutdown   = false;

    protected int testServiceType  = AbstractBaseTest.TEST_SERVICE;
    protected int nAddedExpected   = N_SERVICES;
    protected int nRemovedExpected = 0;

    protected QAConfig testConfig;
    protected LookupDiscoveryManager ldm;

    public static class SDMListener extends AbstractBaseTest.SrvcListener {
        String sdmName;
        public SDMListener(QAConfig config, String sdmName) {
            super(config,"");
            this.sdmName = sdmName;
        }//end constructor

	public void serviceAdded(ServiceDiscoveryEvent event) {
            super.serviceAdded(event);
            if(!inShutdown) {
                logger.log(Level.FINER, "added event -- "
                           +event.getPostEventServiceItem().serviceID+" -- "
                           +sdmName+" (nAdded = "+this.getNAdded()+")");
            }//endif
	}//end serviceAdded

	public void serviceRemoved(ServiceDiscoveryEvent event) {
            super.serviceRemoved(event);
            if(!inShutdown) {
                logger.log(Level.FINER, "removed event -- "
                          +event.getPreEventServiceItem().serviceID+" -- "
                          +sdmName+" (nRemoved = "+this.getNRemoved()+")");
            }//endif
	}//end serviceAdded
    }//end class SDMListener

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Start 1 (transient) lookup service
     */
    public Test construct(QAConfig config) throws Exception {
        int nLookupServices = 0;
        int nServices = 0;
        int nAddServices = 0;
        testDesc = ""+nLookupServices+" lookup service(s), "
                       +(nServices+nAddServices)+" service(s), should receive "
                       +nAddedExpected+" serviceAdded and "+nRemovedExpected
                       +" serviceRemoved event(s)";
        testConfig = config;

        createSDMduringConstruction = false;
        nLookupServices    = 1;
        System.setProperty("net.jini.lookup.nLookupServices", Integer.toString(nLookupServices));
//        nAddLookupServices = 0;
//        nServices          = 0;
//        nAddServices       = 0;
//        nAttributes        = 0;
        
        super.construct(config);
        if ( getLookupServices().getnLookupServices() != nLookupServices) 
            throw new TestException("nLookupServices not set");
        ldm = getLookupDiscoveryManager();
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     * 1. Register half the services/
     * 2. Create half the SDM's and caches
     * 3. Register the remaining services
     * 4. Create the remaining SDM's and caches
     * 5. Allow serviceAdded and serviceRemoved event to arrive
     * 6. Analyze the number of serviceAdded events received
     * 7. Analyze the number of serviceRemoved events received
     * 8. Determine if/how failure has occurred
     */
    protected void applyTestDef() throws Exception {
        ArrayList caches       = new ArrayList(N_SDM);
        ArrayList sdmListeners = new ArrayList(N_SDM);

        /* 1. Register half the services */
        int startVal = 0;
        int nSrvcs   = N_SERVICES/2;
        int nAttrs   = 0;
        registerServices(startVal, nSrvcs, nAttrs, testServiceType);

        /* 2. Create half the SDM's and caches */
        for (int i=0; i<(N_SDM/2); i++) {
            ServiceDiscoveryManager sdm = new ServiceDiscoveryManager
                                      (ldm,null,testConfig.getConfiguration());
            sdmList.add(sdm);
            SDMListener l = new SDMListener(testConfig, "SDM_"+i);
            sdmListeners.add(l);
            caches.add( sdm.createLookupCache(template, null, l) );
        }//end loop

        /* 3. Register the remaining services */
        startVal = nSrvcs;
        registerServices(startVal, nSrvcs, nAttrs, testServiceType);

        /* 4. Create the remaining SDM's and caches */
        for (int i=(N_SDM/2); i<N_SDM; i++) {
            ServiceDiscoveryManager sdm = new ServiceDiscoveryManager
                                      (ldm,null,testConfig.getConfiguration());
            sdmList.add(sdm);
            SDMListener l = new SDMListener(testConfig, "SDM_"+i);
            sdmListeners.add(l);
            caches.add( sdm.createLookupCache(template, null, l) );
        }//end loop

        /* 5. Allow serviceAdded and serviceRemoved event to arrive */
        int nSecsTotal = 0;
        int nSecsWait  = 5;
        boolean done = false;
        while( (nSecsTotal < MAX_N_SECS) && !done ) {
            done = true;
            for(int i=0;i<sdmListeners.size();i++) {
                SDMListener l = (SDMListener)sdmListeners.get(i);
                if( l.getNAdded() != nAddedExpected ) {
                    done = false;
                    break;
                }//endif
            }//end loop
            if( !done ) {
                DiscoveryServiceUtil.delayMS(nSecsWait*1000);
                nSecsTotal = nSecsTotal + nSecsWait;
            }//endif
        }//end loop

        /* 6. Analyze the number of serviceAdded events received */
        int nWithWrongAdded = 0;
        for(int i=0;i<sdmListeners.size();i++) {
            SDMListener l = (SDMListener)sdmListeners.get(i);
            int nAddedThisSDM = l.getNAdded();
            logger.log(Levels.HANDLED,
                       ""+nAddedThisSDM+" added events -- "
                       +N_SERVICES+" services -- SDM_"+i);
            if(nAddedThisSDM != nAddedExpected) nWithWrongAdded++;
        }//end loop

        /* 7. Analyze the number of serviceRemoved events received */
        int nWithWrongRemoved = 0;
        for(int i=0;i<sdmListeners.size();i++) {
            SDMListener l = (SDMListener)sdmListeners.get(i);
            int nRemovedThisSDM = l.getNRemoved();
            logger.log(Levels.HANDLED,
                       ""+nRemovedThisSDM+" removed events -- "
                       +"SDM_"+i);
            if(nRemovedThisSDM > 0) nWithWrongRemoved++;
        }//end loop

        inShutdown = true;

        /* 8. Determine if/how failure has occurred */
        String failStr = null;
        if( (nWithWrongAdded > 0) && (nWithWrongRemoved > 0) ) {
            failStr = new String(" -- failure -- "+nWithWrongAdded
                                 +" SDMs with wrong number of added "
                                 +"events, "+nWithWrongRemoved+" SDMs with "
                                 +"wrong number of removed events");
        } else if(nWithWrongAdded > 0) {
            failStr = new String(" -- failure -- "+nWithWrongAdded
                                 +" SDMs with wrong number of added "
                                 +"events, (removed events OKAY) ");
        } else if(nWithWrongRemoved > 0) {
            failStr = new String(" -- failure -- "+nWithWrongRemoved
                                 +" SDMs with wrong number of removed "
                                 +"event, (added events OKAY) ");
        }//endif

        /* Turn off events from the lookup service before exiting */
        for(int i=0; i<caches.size(); i++) {
            try {
                ((LookupCache)caches.get(i)).terminate();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }//end loop

        if(failStr != null) throw new TestException(failStr);
    }//end applyTestDef

}//end class LookupTaskServiceIdMapRace
