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

package com.sun.jini.test.spec.discoveryservice.event;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.share.DiscoveryProtocolSimulator;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Iterator;

/**
 * This class verifies that the lookup discovery service operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that the lookup discovery service can successfully employ both the
 * multicast and unicast discovery protocols on behalf of one or more clients
 * registered with that service to discover a number of pre-determined lookup
 * services and then, for each discovered lookup service, send to the 
 * appropriate registration listener, the appropriate remote event containing
 * the set of member groups with which the discovered lookup service was
 * configured.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registrations with the lookup discovery service
 *   <li> each registration with the lookup discovery service requests that
 *        some of the lookup services be discovered through only group
 *        discovery, some through only locator discovery, and some through
 *        both group and locator discovery
 *   <li> each registration with the lookup discovery service will receive
 *        remote discovery events through an instance of RemoteEventListener
 * </ul><p>
 * 
 * If the lookup discovery service utility functions as specified, then
 * for each discovered lookup service, a <code>RemoteDiscoveryEvent</code>
 * instance indicating a discovered event will be sent to the listener of
 * each registration that requested discovery of the lookup service.
 * Additionally, each event received will accurately reflect the new set
 * of member groups.
 */
public class MulticastMonitorTerminate extends AbstractBaseTest {

    protected boolean terminate = true;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     *
     *  Retrieves additional configuration values. 
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        logger.log(Level.FINE, 
                        "number of announcements to wait for    -- "
                        +minNAnnouncements);
        logger.log(Level.FINE, 
                        "number of intervals to wait through    -- "
                        +nIntervalsToWait);
        if(terminate) {
            logger.log(Level.FINE, 
                        "stop announcements and destroy lookups -- "
                        +terminate);
        } else {
            logger.log(Level.FINE, 
                        "stop announcements, will not destroy lookups");
        }
        discardType = COMM_DISCARDED;
    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> registers with the lookup discovery service, requesting
     *          the discovery of the the desired lookup services using the
     *          desired discovery protocol
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> verifies that the lookup discovery service utility under test
     *          sends the expected number of events - containing the expected
     *          set of member groups
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, 
                      "lookup discovery service registration_"+i+" --");
            doRegistration(getGroupsToDiscoverByIndex(i),
                           getLocatorsToDiscoverByIndex(i),
                           i, leaseDuration);
        }//end loop
        waitForDiscovery();
        stopAnnouncements();
        waitForDiscard(discardType);
    }//end run

    /** Common code, shared by this class and its sub-classes, that is 
     *  invoked by the run() method. This method stops the generation of
     *  multicast announcements by terminating each announcement generator
     *  and, depending on the value of the terminate configuration flag,
     *  may also destroy the corresponding simulated lookup service
     *
     *  If terminate is true, then both the multicast announcement generators
     *  and their associated lookup services will be terminated; otherwise,
     *  only the generators will be terminated, leaving each corresponding
     *  lookup service still reachable. If the only the multicast announcements
     *  stopped, then the member groups of each lookup
     *  
     *  @throws com.sun.jini.qa.harness.TestException
     */
    void stopAnnouncements() throws TestException, IOException {
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            logger.log(Level.FINE, "stop multicast announcements "
                              +"from lookup service "+i+" ...");
            Object curObj = iter.next();
            ServiceRegistrar regProxy = null;
            /* Since LookupDiscovery sends a unicast announcement, at
             * startup, typically resulting in immediate discovery by
             * unicast, the generator won't send its first multicast
             * announcement until after net.jini.discovery.announce
             * number of milliseconds. Thus, before stopping multicast
             * announcements, wait until at least N multicast
             * announcements have been sent.
             */
            if(curObj instanceof DiscoveryProtocolSimulator) {//simulated LUS
                DiscoveryProtocolSimulator curGen
                                         = (DiscoveryProtocolSimulator)curObj;
                regProxy = curGen.getLookupProxy();
                logger.log(Level.FINE, "lookup "+i
                                  +" - waiting ... announcements so far -- "
                                  +curGen.getNAnnouncementsSent());
                for(int j=0; ((j<nIntervalsToWait)
                    &&(curGen.getNAnnouncementsSent()< minNAnnouncements));j++)
                {
                    DiscoveryServiceUtil.delayMS(announceInterval);
                    logger.log(Level.FINE, "lookup "+i
                                  +" - waiting ... announcements so far -- "
                                  +curGen.getNAnnouncementsSent());
                }//end loop
                logger.log(Level.FINE, 
                                  "lookup "+i
                                  +" - wait complete ... announcements  -- "
                                  +curGen.getNAnnouncementsSent());
                curGen.stopAnnouncements();
            } else {//non-simulated LUS
                logger.log(Level.FINE, "lookup "+i+" - waiting "
                                  +(nIntervalsToWait*announceInterval/1000)
                                  +" seconds for "+minNAnnouncements
                                  +" announcements ... ");
                for(int j=0;j<nIntervalsToWait;j++) {
                    DiscoveryServiceUtil.delayMS(announceInterval);
                    logger.log(Level.FINE, "lookup "+i
                                      +" - still waiting for "
                                      +minNAnnouncements+" announcements ...");
                }//end loop
                logger.log(Level.FINE, "lookup "+i+" - wait complete");
                /* cannot stop the announcements without destroying */
                regProxy = (ServiceRegistrar)curObj;
            }//endif
            if(terminate) {//destroy lookups individually
                manager.destroyService(regProxy);
            } else {//don't terminate, replace member groups to gen discard evt
                logger.log(Level.FINE, "lookup service "+i+" -- "
                                  +"replace member groups to "
                                  +"generate discard event ...");
                replaceGroups(regProxy,i,null,false,discardType);
            }//endif(terminate)
        }//end loop
        announcementsStopped = true;
    }//end stopAnnouncements

}//end class MulticastMonitorTerminate

