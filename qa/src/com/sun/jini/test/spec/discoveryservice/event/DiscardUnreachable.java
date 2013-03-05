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
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.share.DiscoveryProtocolSimulator;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
 * instance indicating a discarded event will be sent to the listener of
 * each registration for each un-reachable lookup service.
 */
public class DiscardUnreachable extends AbstractBaseTest {


    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     *
     *  Retrieves additional configuration values. 
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        discardType = ACTIVE_DISCARDED;
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> registers with the lookup discovery service, requesting
     *          the discovery of the the desired lookup services using the
     *          desired discovery protocol
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> stops the multicast announcements being sent from each
     *          lookup service, and destroys the corresponding lookup service
     *     <li> attempts to interact with each lookup service, invoking
     *          the discard() method on each registration upon finding that
     *          the lookup service is un-reachable
     *     <li> verifies that the lookup discovery service utility under test
     *          sends the expected number of events - containing the expected
     *          set of member groups
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, "lookup discovery service registration_"+i+" --");
            doRegistration(getGroupsToDiscoverByIndex(i),
                           getLocatorsToDiscoverByIndex(i),
                           i, leaseDuration);
        }//end loop
        waitForDiscovery();
        ServiceRegistrar[] proxies = getLookupProxies();
        logger.log(Level.FINE, "# of proxies is " + proxies.length);
        terminateAllLookups();
        long t = 10000;
        logger.log(Level.FINE, "waiting "+(t/1000)+" seconds "
                          +"for shutdown completion ...");
        DiscoveryServiceUtil.delayMS(10000);//wait for shutdown completion
        pingAndDiscard(proxies,getRegistrationMap());
        waitForDiscard(discardType);
    }//end run

    /** Retrieves the proxy to each lookup service started */
    ServiceRegistrar[] getLookupProxies() {
        ServiceRegistrar[] proxies = new ServiceRegistrar[getGenMap().size()];
	Iterator iter = getGenMap().keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            proxies[i] =
                    ((DiscoveryProtocolSimulator)iter.next()).getLookupProxy();
        }//end loop
        return proxies;
    }//end getLookupProxies

    /** Stops the generation of multicast announcements and destroys the
     *  corresponding simulated lookup service
     *  
     *  @throws com.sun.jini.qa.harness.TestException
     */
    void terminateAllLookups() throws TestException, IOException {
        logger.log(Level.FINE, "destroying each lookup service ...");
        Iterator iter = getGenMap().keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object curObj = iter.next();
            ServiceRegistrar regProxy = null;
            if(curObj instanceof DiscoveryProtocolSimulator) {
                DiscoveryProtocolSimulator curGen
                                         = (DiscoveryProtocolSimulator)curObj;
                regProxy = curGen.getLookupProxy();
                curGen.stopAnnouncements();
            } else {
                regProxy = (ServiceRegistrar)curObj;
            }//endif
            /* destroy lookup service i */
            getManager().destroyService(regProxy);
        }//end loop
        announcementsStopped = true;
    }//end terminateAllLookups

    /** For each lookup service proxy input, this method attempts to retrieve
     *  the associated locator and, if that registrar is found to be 
     *  unreachable, discards the lookup service through each of the give
     *  regInfo's.
     */
    void pingAndDiscard(ServiceRegistrar[] proxies, Map regInfoMap) throws Exception {
        int nDiscarded = 0;
        for(int i=0;i<proxies.length;i++) {
            try {
                LookupLocator loc = QAConfig.getConstrainedLocator(proxies[i].getLocator());
                logger.log(Level.FINE, "");
                logger.log(Level.FINE, "warning -- lookup service "
                                               +i+" still reachable");
            } catch(RemoteException e) {
                Set eSet = regInfoMap.entrySet();
                Iterator iter = eSet.iterator();
                for(int j=0;iter.hasNext();j++) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    LookupDiscoveryRegistration ldsReg =
                                    (LookupDiscoveryRegistration)pair.getKey();

                    LDSEventListener regListener =
                                             (LDSEventListener)pair.getValue();
                    RegistrationInfo regInfo = regListener.getRegInfo();
                    int rID = regInfo.getHandback();
		    logger.log(Level.FINE, "  registration_"+rID
			       +" -- discarding lookup service "+i);
		    ldsReg.discard(proxies[i]);
                    logger.log(Level.FINE, " registration_"+rID
                               +" -- discarded lookup service "+i);
                }//end loop(j)
            } catch(Exception exc) { // just for logging purposes
              logger.log(Level.FINE, " ******* exception during pingAndDiscard ********");
              StackTraceElement[] stackTrace = exc.getStackTrace();
              for(int k=0;k<stackTrace.length;k++) {
                  logger.log(Level.FINE, stackTrace[k].toString());
              }
              logger.log(Level.FINE, " ******* end of exception during pingAndDiscard ********");
              throw exc;
            }
        }//end loop(i)
    }//end pingAndDiscard

} //end class DiscardUnreachable

