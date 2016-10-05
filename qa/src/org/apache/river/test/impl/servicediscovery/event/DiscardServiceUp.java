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

import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceItem;
import net.jini.lookup.LookupCache;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.DiscoveryServiceUtil;
import org.apache.river.test.spec.servicediscovery.AbstractBaseTest;

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
 * This class simulates the situation where an entity discards from a
 * lookup cache, a service that never actually goes down. This class then
 * verifies that the service discovery manager identifies the situation
 * and "re-discovers" the previously discarded service.
 * 
 * Related bug ids: 4355024
 */
public class DiscardServiceUp extends AbstractBaseTest {

    /* MAX_N_TASKS is the maximum number of service discard timer tasks the
     * the LookuCache's service discard timer task manager will allow. This
     * means this test makes assumptions about the implementation of the
     * service discovery manager.
     */
    protected final static int MAX_N_TASKS      = 10;
    protected final static int MIN_DISCARD_WAIT = (30*1000);
    protected final static int MAX_DISCARD_WAIT = (6*60*1000);

    protected long removedWait    =  1*1000; //for serviceRemoved events
    protected LookupCache cache;
    protected int testServiceType;

    protected int nAddedExpected   = 0;
    protected int nRemovedExpected = 0;
    protected int nChangedExpected = 0;

    protected boolean simulateDownService = false;

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
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();
        testDesc = ""+nLookupServices+" lookup service(s), "+nServices
                       +" service(s) -- discard still-up service and wait for "
                       +"re-discovery";
        nAddedExpected      = 2*nServices;
        nRemovedExpected    = 1*nServices;
        simulateDownService = false;
        testServiceType     = AbstractBaseTest.TEST_SERVICE;
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
     *  4. Discards the service returned by the call to lookup() to simulate
     *     a service that appears down to the entity.
     *  5. Verifies that a serviceRemoved event occurs as a result of the
     *     call to discard()
     *  6. If simulating a down service, remove each service from the lease
     *     renewal manager, and shorten the renewal duration to hasten
     *     the expiration.
     *  7. Waits an appropriate amount of time for a serviceAdded event to
     *     occur (whether it actually arrives or not is dependent on whether
     *     the service remained up or simulates a down service).
     *  8. Verifies that expected number of serviceAdded and serviceRemoved
     *     events arrived.
     */
    protected void applyTestDef() throws Exception {
        long newLeaseDur = MIN_DISCARD_WAIT/2; //expire relatively quickly
        if(newLeaseDur == 0) newLeaseDur = 1*1000;
        long addedWait = getAddedWait();
        regServicesAndCreateCache();
	/* Query the cache for the desired registered service. */
	logger.log(Level.FINE, "querying the cache for the "
		   +"service reference(s)");
	ServiceItem srvcItem[] = cache.lookup(secondStageFilter,
					      Integer.MAX_VALUE);
	/* Verify the results of the cache query. */
	verifyQueryResults(srvcItem,"first query");
	logger.log(Level.FINE, "# serviceAdded events = "
		   +srvcListener.getNAdded());
	/* Shorten the leases for quick expiration if appropriate. */
	if(simulateDownService) shortenLeases(newLeaseDur);
	/* Discard each service (simulates an unavailable service). */
	doServiceDiscard(srvcItem);
	/* Wait for the serviceAdded events to arrive. */
	waitForServiceEvents(addedWait);
	/* Re-query the cache for the desired registered service. */
	logger.log(Level.FINE, "re-querying the cache for the "
		   +"service reference");
	srvcItem = cache.lookup(secondStageFilter,Integer.MAX_VALUE);
	/* Verify the results of the cache query. */
	if(simulateDownService) {
	    verifyServicesRemoved(srvcItem,"second query");
	} else {
	    verifyQueryResults(srvcItem,"second query");
	}
    }//end applyTestDef

    /** Based on the configuration of the test, this method computes and
     *  returns the amount of time to wait for all events to arrive and,
     *  if appropriate, adjusts the value of org.apache.river.sdm.discardWait.
     *  
     *  The amount time to wait for all events to arrive is dependent on
     *  the following items: the number of services, whether or not those
     *  services will simulate services that have gone down, and the
     *  maximum number of tasks allowed by the manager for the service
     *  discard timer task (which cannot be retrieved from the service
     *  discovery manager). 
     *
     *  For example, if there are 7 services being discarded that do NOT
     *  simulate down services, and if the task manager allows only 5
     *  timer tasks at one time, then 2 service discard tasks will have to
     *  wait for 2 of the initial tasks to complete before those 2
     *  additional can run. Since a service discard timer task exits 
     *  early only if it determines that its corresponding discarded
     *  service is actually down, and since for this example none of the
     *  services go down, each task will run for its full allotted time.
     *  Thus, the amount of time to wait for all events to arrive would
     *  need to be 2 times the maximum amount of time a service discard
     *  task is allowed to run, plus some epsilon [(2*discardWait)+epsion].
     *
     *  For the case where the services do simulate down services, since
     *  the tasks will exit early, the amount of time to wait for all
     *  events to arrive needs to be only (discardWait+epsilon). But
     *  for this case, depending on the number of services and the number
     *  lookup services, it's possible that not all of the expected number
     *  of TRANSITION_MATCH_NOMATCH events (signalling lease expiration)
     *  will arrive before each service discard timer task completes its
     *  wait cycle (that is, the more lookups and services, the longer the
     *  possible delay in events from the lookup services). Thus, for this
     *  case, the value of discardWait itself is adjusted to account for
     *  this situation.
     */
    protected long getAddedWait() {
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();       
        long deltaWait = nServices*5*1000;
        long addedWait = (2*discardWait) + deltaWait;//1st assume not down
        if(simulateDownService) {//services down, adjust discardWait, addedWait
            /* Adjust the value of discardWait */
            if(discardWait < MIN_DISCARD_WAIT) discardWait = MIN_DISCARD_WAIT;
            discardWait = nLookupServices*nServices*discardWait;
            if(discardWait > MAX_DISCARD_WAIT) discardWait = MAX_DISCARD_WAIT;
            System.setProperty("org.apache.river.sdm.discardWait",
                               (new Long(discardWait)).toString());
            addedWait = discardWait + deltaWait;
        } else {//services not down, adjust addedWait
            long waitFactor = 1;
            int tmpNServices = nServices;
            while(tmpNServices > MAX_N_TASKS) {
                waitFactor = waitFactor+1;
                tmpNServices = tmpNServices - MAX_N_TASKS;
            }//end loop
            addedWait = (waitFactor*discardWait) + deltaWait;
        }//endif
        logger.log(Level.FINE, "seconds each timer task waits for "
                          +"MATCH_NOMATCH events  -- "+(discardWait/1000));
        logger.log(Level.FINE, "seconds to wait for all events "
                          +"to arrive after discard  -- "+(addedWait/1000));
        return addedWait;
    }//end getAddedWait

    /* Registers the appropriate number of services with the lookup services
     * and creates a LookupCache
     */
    protected void regServicesAndCreateCache() throws Exception {
        int nServices = getLookupServices().getnServices();
        int nAttributes = getLookupServices().getnAttributes();
        int nSecsServiceDiscovery = getLookupServices().getnSecsServiceDiscovery();
        /* Register new proxies */
        registerServices(0,nServices,nAttributes,testServiceType);
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
                              +"creation", e);
        }
        /* Wait for the cache to populate. */
	logger.log(Level.FINE, "wait "
		   +nSecsServiceDiscovery+" seconds to allow the "
		   +"cache to be populated ... ");
        DiscoveryServiceUtil.delayMS(nSecsServiceDiscovery*1000);
    }//end regServicesAndCreateCache

    /* Discards each of the services in the given ServiceItem arrya (simulates
     * an unavailable service), and waits for the expected serviceRemoved
     * events.
     */
    protected void doServiceDiscard(ServiceItem srvcItem[]) throws Exception {
        int nServices = getLookupServices().getnServices();
        /* Discard each service. */
        for(int i=0;i<srvcItem.length;i++) {
            logger.log(Level.FINE, "discarding service["+i+"] -- ID = "
                              +srvcItem[i].serviceID+" ...");
            cache.discard(srvcItem[i].service);
        }//endloop
        /* Wait for the serviceRemoved event to arrive. */
        long removedWait = nSecsServiceEvent;
        if(nServices > removedWait) removedWait = nServices;//wait at least N
        logger.log(Level.FINE, "waiting "+removedWait+" seconds to "
                   +"allow serviceRemoved event(s) to arrive after "
                   +"service(s) discarded ... ");
        DiscoveryServiceUtil.delayMS(removedWait*1000);
        logger.log(Level.FINE, "# serviceRemoved events = "
                          +srvcListener.getNRemoved());
        if(srvcListener.getNRemoved() != nRemovedExpected) {
            logger.log(Level.FINE, "failure -- "
                              +"# serviceRemoved events expected = "
                              +nRemovedExpected
                              +"# serviceRemoved events received = "
                              +srvcListener.getNRemoved());
            throw new TestException(" -- failure -- "
                              +"# serviceRemoved events expected = "
                              +nRemovedExpected
                              +"# serviceRemoved events received = "
                              +srvcListener.getNRemoved());
        }//endif
    }//end doServiceDiscard

    /* This method removes each lease from the lease renewal manager so the
     * leases are no longer renewed, and shortens each lease to cause a
     * relatively fast expiration so the service discovery manager will
     * commit any service discards performed earlier. In this way, the
     * services can simulate a services that is truly down.
     */
    protected void shortenLeases(long newLeaseDur) throws Exception {
        leaseRenewalMgr.clear();//remove the leases from the lease renewal mgr
        /* Shorten each service lease duration for quick expiration */
        for(int i=0;i<leaseList.size();i++) {
            Lease srvcLease = (Lease)(leaseList.get(i));
            logger.log(Level.FINE, "service lease["+i+"] changed to expire "
                              +"within "+(newLeaseDur/1000)+" second(s) -- "
                              +"simulates a down service");
            srvcLease.renew(newLeaseDur);
        }//end loop
        /* Remove all leases so we don't try to cancel them during shutdown. */
        leaseList.clear();
    }//end shortenLeases

    /* This method waits the specified amount of time for the expected or
     * un-expected events to arrive. If the system is configured to simulate
     * down services, then if an unexpected number of events arrive before
     * the wait time is up, the method can be prematurely exited. On the
     * other hand, if the services do not simulate down services, we have
     * to wait the whole wait period.
     */
    protected void waitForServiceEvents(long waitTime) throws Exception {
        logger.log(Level.FINE, "waiting "+(waitTime/1000)
                          +" seconds for expected/un-expected service "
                          +"events to arrive ... ");
        if(simulateDownService) {
            /* If events received > expected, then failure */
            for(int i=0;i<(waitTime/1000);i++) {
                DiscoveryServiceUtil.delayMS(1*1000);
                if(     (srvcListener.getNAdded()   > nAddedExpected) 
                     || (srvcListener.getNRemoved() > nRemovedExpected) )
                {
                    logger.log(Level.FINE, "failure -- unexpected extra "
                                      +"event(s) arrived after "+i
                                      +" second(s)");
                    logger.log(Level.FINE, "# serviceAdded events expected = "
                                      +nAddedExpected
                                      +", # serviceAdded events received = "
                                      +srvcListener.getNAdded());
                    logger.log(Level.FINE, "# serviceRemoved events expected = "
                                      +nRemovedExpected
                                      +", # serviceRemoved events received = "
                                      +srvcListener.getNRemoved());
                    throw new TestException("# added expected = "+nAddedExpected
                                      +", # added received = "
                                      +srvcListener.getNAdded()
                                      +", # removed expected = "
                                      +nRemovedExpected
                                      +", # removed received = "
                                      +srvcListener.getNRemoved());
                }//endif
            }//end loop
        } else {
            /* If services not down, need to wait the whole time */
            DiscoveryServiceUtil.delayMS(waitTime);
        }//endif
        if(     (srvcListener.getNAdded()   != nAddedExpected) 
             || (srvcListener.getNRemoved() != nRemovedExpected) )
        {
            logger.log(Level.FINE, "# serviceAdded events expected = "
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
        }//endif
        logger.log(Level.FINE, "# serviceAdded events = "
                          +srvcListener.getNAdded());
        logger.log(Level.FINE, "# serviceRemoved events = "
                          +srvcListener.getNRemoved());
    }//end waitForServiceEvents

    /* Verifies that the service was found in the cache. */
    protected void verifyQueryResults(ServiceItem srvcItem[],String qStr) 
	throws Exception
    {
        int nServices = getLookupServices().getnServices();
        List expectedServiceList = getExpectedServiceList();
        if(srvcItem.length == 0) {
            logger.log(Level.FINE, ""+qStr+" -- no services in cache");
            throw new TestException(" -- "+qStr+" -- no services in cache");
        }//endif
        if(srvcItem.length != nServices) {
            logger.log(Level.FINE, ""+qStr+" -- # services in cache ("
                              +srvcItem.length+") != # services started "
                              +"("+nServices+")");
            throw new TestException(" -- "+qStr+" -- # services in cache ("
                              +srvcItem.length+") != # services started "
                              +"("+nServices+")");
        }//endif
        for(int i=0,n=srvcItem.length;i<n;i++) {
            if(srvcItem[i].service == null) {
                logger.log(Level.FINE, ""+qStr+" -- service component of "
                                  +"returned service["+i+"] is null");
                throw new TestException(" -- "+qStr+" -- service component "
                                  +"of returned service["+i+"] is null");
            }//endif
            boolean srvcFound = expectedServiceList.contains(srvcItem[i].service);
	    if (srvcFound) {
		logger.log(Level.FINE, "{0} -- expected service found in cache", qStr);
	    }
//            for(int j=0,l=expectedServiceList.size();i<l;j++) {
//                if((srvcItem[i].service).equals(expectedServiceList.get(j))) {
//                    logger.log(Level.FINE, ""+qStr+" -- expected "
//                                      +"service["+j+"] found in cache");
//                    srvcFound = true;
//                    break;// service exists in cache
//                }//endif
//            }//end loop (j)
            if(!srvcFound) {
                logger.log(Level.FINE, ""+qStr+" -- returned service["
                                  +i+"] is not equivalent to any of the "
                                  +"service(s) registered with lookup");
                throw new TestException(""+qStr+" -- returned service["
                                  +i+"] is not equivalent to any of the "
                                  +"service(s) registered with lookup");
            }//endif
        }//end loop (i)
    }//end verifyQueryResults

    /* Verifies that the service was not found in the cache */
    protected void verifyServicesRemoved(ServiceItem[] srvcItem,String qStr)
	throws Exception
    {
        if(srvcItem.length == 0) {
            logger.log(Level.FINE, ""+qStr+" -- no services in cache, as expected");
            return;
        }//endif
        List expectedServiceList = getExpectedServiceList();
        for(int i=0,l=srvcItem.length;i<l;i++) {
            if(srvcItem[i].service == null) continue;
	    if (expectedServiceList.contains(srvcItem[i].service)){
		int j = expectedServiceList.indexOf(srvcItem[i].service);
		logger.log(Level.FINE, ""+qStr+" -- service["+j+"] found in "
                                      +"cache");
                    throw new TestException(" "+qStr+" -- service["+j+"] found "
					    +"in cache");
	    }
//            for(int j=0;i<expectedServiceList.size();j++) {
//                if((srvcItem[i].service).equals(expectedServiceList.get(j))) {
//                    logger.log(Level.FINE, ""+qStr+" -- service["+j+"] found in "
//                                      +"cache");
//                    throw new TestException(" "+qStr+" -- service["+j+"] found "
//					    +"in cache");
//                 }//endif
//            }//end loop (j)
        }//end loop (i)
        logger.log(Level.FINE, ""+qStr+" -- no services in cache, as expected");
    }//end verifyServicesRemoved

}//end class DiscardServiceUp


