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
package org.apache.river.test.spec.lookupservice.test_set02;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;
import org.apache.river.qa.harness.TestException;

import java.rmi.RemoteException;
import java.io.IOException;
import java.util.Date;
import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.test.spec.lookupservice.QATestUtils;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lease.*;
import net.jini.core.event.*;

/** This class is used to test that service lease LeaseMap renewAll()
 *  works as expected for N (currently N = 5) successive lease
 *  renewal attempts.
 */
public class LeaseMapRenew extends QATestRegistrar {
    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;
    /* lease duration to 1 minute */
    private final static long DEFAULT_LEASEDURATION = 120000;
    private final static int DEFAULT_LOOP_COUNT = 5;
    private final static int  loopCount= DEFAULT_LOOP_COUNT;   
    private final static long leaseDuration = DEFAULT_LEASEDURATION;
    private final static long initialLeaseDuration = DEFAULT_LEASEDURATION * 2;
    private final static long leaseWaitTime  = DEFAULT_LEASEDURATION *3/4;
    private final static long halfDurationTime = DEFAULT_LEASEDURATION /2;
    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private Lease[] srvcLeases ;
    private ServiceTemplate[] srvcIDTmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;
    private long leaseStartTime;
    private LeaseMap leaseMap;
    private java.util.Map mapCopy;
    private Lease[] evntLeases ;
    private EventRegistration[] evntRegs;

    /** Class which handles all events sent by the lookup service */
    private class Listener extends BasicListener 
                           implements RemoteEventListener, java.io.Serializable
    {
	Listener() throws RemoteException {
	    super();
	}
        /** Method called remotely by lookup to handle the generated event. */
	public void notify(RemoteEvent theEvent)
	                throws UnknownEventException, java.rmi.RemoteException 
	{
	}
    }

    /** The event handler for the services registered by this class */
    private RemoteEventListener listener;
    
    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes. Retrieves the proxy to the lookup Registrar. 
     *  Registers each service class 
     *  instance with an initial lease duration. Establishes 
     *  an approximate service lease start time for each service item by 
     *  retrieving the current system time.  Creates an array of 
     *  Leases in which each element contains the service lease of one 
     *  of the registered services. Creates an array of ServiceTemplates 
     *  in which each element contains the service ID of one of the 
     *  registered service items. 
     *  <p>
     *  The initial lease duration is greater than the lease renewal
     *  duration to accomodate systems with excessive construct times.
     *  Some test systems were observed to require more than 15 seconds
     *  for construct, which caused the early leases to expire before the
     *  first leasemap renewal call.
     *
     *  @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public synchronized Test construct(QAConfig sysConfig) throws Exception {
        int i;
	super.construct(sysConfig);
	listener = new Listener();
        ((BasicListener) listener).export();
        nInstances = super.getNInstances();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	proxy = super.getProxy();
	srvcIDTmpls = new ServiceTemplate[nInstances];
	logger.log(Level.FINE, "beginning registrations at " + new Date());
 	srvcRegs = registerAll(initialLeaseDuration);
	leaseStartTime = QATestUtils.getCurTime();
 	srvcRegs = registerAll(leaseDuration);
        srvcLeases = new Lease[srvcRegs.length];

	for(i=0; i<srvcLeases.length; i++) {
	    srvcLeases[i] = getRegistrationLease(srvcRegs[i]);
	}
	for(i=0; i<srvcIDTmpls.length; i++) {
	    srvcIDTmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                                 null,null);
	}
 	evntRegs = new EventRegistration[nInstances];
	evntLeases = new Lease[nInstances];
	registerAllEvents();
	createLeaseMap();
	logger.log(Level.FINEST, "setup complete");
        return this;
   }

    /** Executes the current QA test.
     *
     *  Repeats the following steps N times:
     *     Waits for three-fourths of the current lease duration time.
     *     Sets the new (approximate) lease start time to the current time.
     *     Renews all service leases; requesting the new lease duration.
     *     Verifies that the lease duration returned is the duration requested.
     *     Waits for one-half of the current lease duration time.
     *     Performs a simple lookup of each registered service item.
     *     Verifies that the set of service items returned by the lookup 
     *     operation equals the expected set of service items.
     */

    /*  The time-line diagram below shows the steps of this test:
     *
     *                                                     Renewal
     *                                                     R4 |---------------|
     *                                         Renewal        :        ^      .
     *                                         R3 |---------------|    :      .
     *                             Renewal        :       ^       .    :      .
     *                             R2 |---------------|   :       .    :      .
     *                 Renewal        :       ^       .   :       .    :      .
     *                 R1 |---------------|   :       .   :       .    :      .
     *     Renewal        :       ^       .   :       .   :       .    :      .
     *     R0 |---------------|   :       .   :       .   :       .    :      .
     *        :       ^       .   :       .   :       .   :       .    :      .
  |---------------|   :       .   :       .   :       .   :       .    :      .
  0      0.5      1   :      1.5  :       2   :      2.5  :       3    :    3.5
     *                :           :           :           :            :
     *                :           :           :           :            :
     *               L0          L1          L2          L3           L4
     */
    public synchronized void run() throws Exception {
	for(int i =0; i<loopCount; i++) {
	    logger.log(Level.FINEST, "renewLeaseWait: " + i);
	    QATestUtils.computeDurAndWait(leaseStartTime, leaseWaitTime, this);
	    logger.log(Level.FINEST, "doRenewLease");
	    leaseStartTime = QATestUtils.getCurTime();
	    leaseMap.renewAll(); 
	    QATestUtils.verifyLeases(srvcLeases,
	    			 leaseStartTime + leaseDuration);
	    QATestUtils.verifyLeases(evntLeases,
	    			 leaseStartTime + leaseDuration);
	    if (!mapCopy.equals(leaseMap))
	    	throw new TestException("map contents changed");
	    logger.log(Level.FINEST, "lookupwait");
	    QATestUtils.computeDurAndWait(leaseStartTime, halfDurationTime, this);
	    logger.log(Level.FINEST, "dolookup");
	    QATestUtils.doLookup(srvcItems, srvcIDTmpls, proxy ); 
	    logger.log(Level.FINEST, "lookup successful");
	}
    }

    /** put all our leases into the leaseMap */
    private void createLeaseMap() {
	
	leaseMap = prepareRegistrationLeaseMap(srvcLeases[0].createLeaseMap(leaseDuration));
	for(int i=1; i<srvcLeases.length; i++) {
	    leaseMap.put(srvcLeases[i], new Long(leaseDuration));
 	}
       
	for(int i=0; i<evntLeases.length; i++) {
	    leaseMap.put(evntLeases[i], new Long(leaseDuration));
 	}
	mapCopy = new java.util.HashMap(leaseMap);
    }

    /* For each registered service, registers an event notification request,
     * with a specified lease duration, based on the contents of the 
     * corresponding template created during construct and corresponding to
     * the appropriate transition mask. Populates the array of Leases so
     * that each element contains one of the event leases returned by the 
     * event notification registration process.
     */
    private void registerAllEvents() throws Exception {
        for(int i=0; i<evntRegs.length; i++) {
	    EventRegistration er;
	    er = proxy.notify(srvcIDTmpls[i],
			      ServiceRegistrar.TRANSITION_NOMATCH_MATCH  |
			      ServiceRegistrar.TRANSITION_MATCH_NOMATCH |
			      ServiceRegistrar.TRANSITION_MATCH_MATCH,
			      listener,
			      null,
			      initialLeaseDuration);
	    evntRegs[i] = prepareEventRegistration(er);
	    evntLeases[i] = getEventLease(evntRegs[i]);
	}
    }  
}
