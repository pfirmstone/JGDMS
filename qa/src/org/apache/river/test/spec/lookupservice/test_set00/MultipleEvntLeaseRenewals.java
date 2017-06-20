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
package org.apache.river.test.spec.lookupservice.test_set00;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceEvent;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.event.*;
import net.jini.core.lease.*;
import net.jini.lookup.SafeServiceRegistrar;
import java.rmi.RemoteException;

/** This class is used to test that event lease renewal works as expected for
 *  N (currently N = 5) successive lease renewal attempts.
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class MultipleEvntLeaseRenewals extends QATestRegistrar {
    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;
    /* lease duration to 1 minute */
    private final static long DEFAULT_LEASEDURATION
                                                = (4*QATestUtils.N_MS_PER_MIN);
    private final static int DEFAULT_LOOP_COUNT = 5;
    private final static int  loopCount= DEFAULT_LOOP_COUNT;   
    private final static long leaseDuration = DEFAULT_LEASEDURATION;
    private final static long leaseWaitTime  = DEFAULT_LEASEDURATION *3/4;
    private final static long halfDurationTime = DEFAULT_LEASEDURATION /2;
    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[] srvcIDTmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;
    private long leaseStartTime;

    private ServiceEvent[] notificationEvnt;
    private int numEvnt = 0;
    private Lease[] evntLeases ;
    private EventRegistration[] evntRegs;

    /** Class which handles all events sent by the lookup service */
    private class Listener extends BasicListener implements RemoteEventListener, 
							    java.io.Serializable
    {
        public Listener() throws RemoteException {
            super();
	}
        /** Method called remotely by lookup to handle the generated event. */
	public void notify(RemoteEvent theEvent)
	                throws UnknownEventException, java.rmi.RemoteException 
	{
            synchronized (MultipleEvntLeaseRenewals.this){
                notificationEvnt[numEvnt++] = (ServiceEvent) theEvent;
            }
	}
    }

    /** The event handler for the services registered by this class */
    private BasicListener listener;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates a single event handler to handle all events generated
     *  by any of the registered service items. Creates the lookup
     *  service. Loads and instantiates all service classes. Registers 
     *  each service class instance with the maximum service lease duration. 
     *  Retrieves the proxy to the lookup Registrar. Creates an array of 
     *  ServiceTemplates in which each element contains the service ID of 
     *  one of the registered service items. Associates with each service
     *  item, the corresponding service ID returned by the registration 
     *  process; to enable reuse of the service IDs during the re-registration 
     *  of the services. Establishes an approximate event lease start time 
     *  for each event lease by retrieving the current system time. For
     *  each registered service, registers an event notification request,
     *  with a specified lease duration, based on the contents of the 
     *  corresponding template created previously and the appropriate 
     *  transition mask. Creates an array of Leases in which each element 
     *  contains one of the event leases created during the event 
     *  notification registration process.
     */
    public synchronized Test construct(QAConfig sysConfig) throws Exception {

        QATestUtils.setLeaseDuration(sysConfig, 1000L * 60 * 60);
	super.construct(sysConfig);
	listener = new Listener();
        listener.export();
	logger.log(Level.FINE, "in setup() method.");
        nInstances = super.getNInstances();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = registerAll();
	proxy = super.getProxy();
	srvcIDTmpls = new ServiceTemplate[nInstances];
	for(int i=0; i<srvcIDTmpls.length; i++) {
	    srvcIDTmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                                 null,null);
	}
	reuseServiceID();
	evntRegs = new EventRegistration[nInstances];
	evntLeases = new Lease[nInstances];
	notificationEvnt = new ServiceEvent[nInstances];
	leaseStartTime = QATestUtils.getCurTime();
	registerAllEvents();
        return this;
    }

    /** Executes the current QA test.
     *
     *  Repeats the following steps N times:
     *     Waits for three-fourths of the current lease duration time.
     *     Sets the new (approximate) lease start time to the current time.
     *     Renews all event leases; requesting the new lease duration.
     *     Verifies that the lease duration returned is the duration requested.
     *     Waits for one-half of the current lease duration time.
     *     If the loop index is even, cancels each service lease (to generate 
     *     an event).
     *     If the loop index is odd, re-registers each instance of each service
     *     class that was previously registered (also to generate an event).
     *     Waits for a specified amount of time to allow time for any events
     *     to be generated, sent and collected.
     *     Performs a simple lookup of each registered service item.
     *     Verifies that the set of events sent by the lookup service is
     *     the set of events expected. 
     *     Verifies that each event corresponds to the expected service ID 
     *     and that the transition is the expected transition (MATCH_NOMATCH
     *     if the service lease was cancelled, NOMATCH_MATCH if the service
     *     was re-registered).
     */

    /*  The time-line diagram below shows the steps of this test:
     *
     *                                                     Renewal
     *                                                    NR4 |---------------|
     *                                        Renewal         :       ^  ^    .
     *                                        NR3 |---------------|   :  :    .
     *                            Renewal         :      ^  ^     .   :  :    .
     *                            NR2 |---------------|  :  :     .   :  :    .
     *                Renewal         :     ^  ^      .  :  :     .   :  :    .
     *                NR1 |---------------| :  :      .  :  :     .   :  :    .
     *    Renewal         :      ^  ^     . :  :      .  :  :     .   :  :    .
     *    NR0 |---------------|  :  :     . :  :      .  :  :     .   :  :    .
     *        :      ^  ^     .  :  :     . :  :      .  :  :     .   :  :    .
  |---------------|  :  :     .  :  :     . :  :      .  :  :     .   :  :    .
  0      0.5      1  :  :    1.5 :  :     2 :  :     2.5 :  :     3   :  :  3.5
     *               :  :        :  :       :  :         :  :         :  :
     *               :  :        :  :       :  :         :  :         :  :
     *               :  A0       :  A1      :  A2        :  A3        :  A4
     *               :           :          :            :            :
     *               :           :          :            :            :
     *              P0          P1         P2           P3           P4
     */
    public synchronized void run() throws Exception {
	logger.log(Level.FINE, "MultipleSrvcLeaseRenewals : in run() method.");
	logger.log(Level.FINE, "# of trials = " + loopCount);

	for(int i =0; i<loopCount; i++) {
	    logger.log(Level.FINE, "\n**** Start trial #" + i + "****");
	    logger.log(Level.FINE, "Waiting 3/4 of lease duration time.");
	    numEvnt = 0;
	    QATestUtils.computeDurAndWait(leaseStartTime, leaseWaitTime, this);
	    logger.log(Level.FINE, "Renewing leases ...");
	    leaseStartTime = QATestUtils.getCurTime();
	    QATestUtils.doRenewLease(evntLeases, leaseDuration);
	    logger.log(Level.FINE, "Verifying leases against minimum " +
			      "expiration time.");
	    QATestUtils.verifyLeases(evntLeases,
				     leaseStartTime + leaseDuration);
	    logger.log(Level.FINE, "Waiting 1/2 of the lease duration time.");
	    QATestUtils.computeDurAndWait(leaseStartTime, 
					  halfDurationTime, this);

	    String transitionText = null;
	    int transition;
	    if( QATestUtils.isEven(i) ) {
		logger.log(Level.FINE, "Canceling all leases ...");
		cancelAllLeases(srvcRegs);
		transition = ServiceRegistrar.TRANSITION_MATCH_NOMATCH;
		transitionText = "TRANSITION_MATCH_NOMATCH";
	    } else {
		logger.log(Level.FINE, "Re-registering all services ...");
		srvcRegs = registerAll();
		transition = ServiceRegistrar.TRANSITION_NOMATCH_MATCH;
		transitionText = "TRANSITION_NOMATCH_MATCH";
	    }
	    try {
		logger.log(Level.FINE, "Waiting " + deltaTListener + 
				  " milliseconds for all " +
				  transitionText + " events to arrive.");
                QATestUtils.waitDeltaT( deltaTListener, this);
		//wait(deltaTListener);
	    } catch (InterruptedException e) {
	    }
	    logger.log(Level.FINE, "Verifying event set for correctness ...");
	    verifyNotification(transition);
	}
    }

    /** Performs cleanup actions necessary to achieve a graceful exit of 
     *  the current QA test.
     *
     *  Unexports the listener and then performs any remaining standard
     *  cleanup duties.
     */
    public synchronized void tearDown() {
	logger.log(Level.FINE, "in tearDown() method.");
	try {
	    unexportListener(listener, true);
	} finally {
	    super.tearDown();
	}
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
	    evntRegs[i] = ((SafeServiceRegistrar)proxy).notiFy(srvcIDTmpls[i],
				       ServiceRegistrar.TRANSITION_NOMATCH_MATCH  |
				       ServiceRegistrar.TRANSITION_MATCH_NOMATCH |
				       ServiceRegistrar.TRANSITION_MATCH_MATCH,
				       listener,
				       null,
				       leaseDuration);
	    evntRegs[i] = prepareEventRegistration(evntRegs[i]);
	    evntLeases[i] = getEventLease(evntRegs[i]);
	}
    }

    /* Cancels the service lease of each registered service item. */
    private void cancelAllLeases(ServiceRegistration[] regs)
                                                         throws Exception
    {
        for(int i=0; i<regs.length; i++) {
	    getRegistrationLease(regs[i]).cancel();
	}
    }

    /* Associates with each registered service item, the corresponding 
     * service ID returned by the registration process; so as to enable 
     * reuse of the service IDs during the re-registration process.
     */
    private void reuseServiceID() {
        for(int i=0; i< srvcItems.length; i++) {
	    srvcItems[i].serviceID = srvcRegs[i].getServiceID();
	}
    }
  
    /* Verifies that the number of events received equals the number of
     * events expected. For each registered service item, verifies that
     * service's ID equals the service ID associated with the corresponding
     * event; and that the transition associated with the event equals
     * the expected transition.
     */
    private void verifyNotification(int transition) throws TestException {
	if(srvcItems.length != numEvnt )
            throw new TestException("# of Events Received ("+
				    numEvnt+
				    ") != # of Events Expected ("+
				    srvcItems.length+")");

	for(int i=0; i <srvcItems.length; i++) {
	    if(!verifyServiceItemTransition(srvcItems[i],transition)) {
                throw new TestException("transition mismatch (index "+i+")");
	    }
	}	
    }

    /* For each event associated with the given service item, verifies that
     * verifies that service's ID equals the service ID associated with the 
     * event; and that the transition associated with the event equals
     * the expected transition.
     */
    private boolean verifyServiceItemTransition(ServiceItem serviceItem,
                                                int transition)
    {
	for(int i=0; i<numEvnt; i++ ) {
	    if(    (notificationEvnt[i].getServiceID().equals
                                                       (serviceItem.serviceID))
                && (notificationEvnt[i].getTransition() == transition) )
            {
		return true;
	    }
	}
	//System.out.println("verifyServiceItemTransition failed");
	return false;
    }
}
