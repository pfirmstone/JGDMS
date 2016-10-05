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

import org.apache.river.test.spec.lookupservice.QATestUtils;
import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.qa.harness.TestException;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceEvent;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import java.rmi.RemoteException;
import java.util.Vector;

/** This class is used to test that all event notifications cease to be
 *  delivered after all event leases have expired.
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class EvntLeaseExpiration extends QATestRegistrar {

    /** Class which handles all events sent by the lookup service */
    public class Listener extends BasicListener implements RemoteEventListener
    {
        public Listener() throws RemoteException {
            super();
	}

        /** Method called remotely by lookup to handle the generated event. */
        public void notify(RemoteEvent ev) {
            ServiceEvent srvcEvnt = (ServiceEvent)ev;
            evntVec.addElement(srvcEvnt);
        }
    }

    /** The event handler for the services registered by this class */
    private static BasicListener listener;

    protected final Vector evntVec = new Vector();

    private long evntLeaseDurMS;
    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate emptyTmpl;
    private ServiceRegistrar proxy;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates a single event handler to handle all events generated
     *  by any of the registered service items. Creates the lookup
     *  service. Loads and instantiates all service classes. Registers 
     *  each service class instance with the maximum service lease duration. 
     *  Retrieves the proxy to the lookup Registrar. Creates a single
     *  "empty" ServiceTemplate; that is, a template with all null fields.
     *  Register an event notification request based on the contents of that
     *  template, a transition mask including all three transitions, and
     *  a lease duration that is less than the duration of the service
     *  leases.
     */
    public synchronized Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	logger.log(Level.FINE, "in setup() method.");
	listener = new Listener();
        listener.export();
        evntLeaseDurMS = 5*QATestUtils.N_MS_PER_SEC;
        srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();
	emptyTmpl = new ServiceTemplate(null,null,null);
	EventRegistration evntReg
	    = proxy.notiFy(emptyTmpl,
			   ServiceRegistrar.TRANSITION_NOMATCH_MATCH |
			   ServiceRegistrar.TRANSITION_MATCH_NOMATCH |
			   ServiceRegistrar.TRANSITION_MATCH_MATCH,
			   listener, null, evntLeaseDurMS);
	evntReg = prepareEventRegistration(evntReg);
        return this;
    }

    /** wait for the event lease to expire and then verify that NO events
     *  have arrived
     */
    /** Executes the current QA test.
     *
     *  Computes the amount of time to wait to guarantee that all event
     *  leases have expired. Waits that amount of time. Cancels each
     *  service lease (to generate an event for each cancellation). Waits
     *  an appropriate amount of time to allow any events that may be
     *  generated to be sent and collected. Verifies that no events were
     *  sent by the Lookup service. 
     *  @exception TestException usually indicates test failure
     */

    /*  The time-line diagram below shows the steps of this test:
     *
     *           |-----------------------------------------------------------|
     *           :         :         ^      :  
     * |-------------------|         :      :  
     * 0                 5 secs      :      : 
     *                     :         :      :
     *                     :         :      :
     *                  Expires    Cancel   :
     *                             Service  :
     *                             Lease    Analyze
     */
    public synchronized void run() throws Exception {

	logger.log(Level.FINE, "EvntLeaseExpiration : in run() method.");

	ServiceEvent evnt = null;
	/* wait for the event lease to expire */
	try {
	    long waitTime = evntLeaseDurMS + super.deltaTEvntLeaseExp;
	    logger.log(Level.FINE, "Waiting {0} milliseconds for event lease to expire.", waitTime);
            QATestUtils.waitDeltaT(waitTime, this);
//	    Thread.sleep(waitTime);
	} catch (InterruptedException e) {
	}

	logger.log(Level.FINE, "Cancelling each service lease to generate " +
			  "events.");
	/* cancel each service lease so as to generate events */
	for(int i=0; i<srvcRegs.length; i++) {
	    // test will fail for unknown lease or remote exceptions
	    getRegistrationLease(srvcRegs[i]).cancel(); 
	}

	/* give the Listener a chance to collect all events */
	logger.log(Level.FINE, "Waiting " + deltaTListener + " milliseconds" +
			  " for listener to collect events.");
	try {
            QATestUtils.waitDeltaT( deltaTListener, this);
	    //Thread.sleep(deltaTListener);
	} catch (InterruptedException e) {
	}

	/* Verify that no events have arrived. If no events have arrived
	 * the event vector should be empty. If not, declare failure
	 */
	if (evntVec.size() != 0) {
	    String message = "evntVec.size() != 0\n" +
		"Event leases expired yet " + evntVec.size() + 
		" event(s) arrived.";
	    throw new TestException(message);
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
}
