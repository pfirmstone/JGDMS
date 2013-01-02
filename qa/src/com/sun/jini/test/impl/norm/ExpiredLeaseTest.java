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
package com.sun.jini.test.impl.norm;

import java.util.logging.Level;

import com.sun.jini.qa.harness.Admin;
import com.sun.jini.qa.harness.ActivatableServiceStarterAdmin;
import com.sun.jini.qa.harness.TestException;

import java.io.IOException;
import java.io.PrintWriter;

import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

import com.sun.jini.test.share.TestBase;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/** 
 * Test that registers for renewal failure events, creates an expired
 * lease, adds the lease to the set and check to see if it gets a
 * renewal failure event.
 * <p>
 * As a special bonus if tryShutdown is enabled, tries to add a lease
 * that will expire after the sever is restarted and check to see if 
 * an event is delvered for that lease as well
 */
public class ExpiredLeaseTest extends TestBase implements Test {

    /** Should we try shuting down the service under test? */
    private boolean tryShutdown;

    /** How long should we wait before giving up on event delevery */
    private long eventWait;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	this.parse();
        return this;
    }

    /**
     * Parse our args
     * <DL>
     *
     * <DT>-tryShutdown <DD>If used the test will kill the VM the service
     * is running in after adding the client lease to the set and again
     * after removing the lease from the set.
     *
     * <DT>-eventWait int <DD>How long to wait (in milliseconds) for
     * events to be received
     * 
     * </DL> 
     */
    protected void parse() throws Exception {
	super.parse();
	tryShutdown = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.norm.tryShutdown", false);
	eventWait = getConfig().getIntConfigVal("com.sun.jini.test.impl.norm.eventWait", 20000);
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet     set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	addLease(prepareNormLease(set.getRenewalSetLease()), false);

	// Register for failure events
	final OurListener listener = new OurListener();
	final MarshalledObject handback = new MarshalledObject(new Long(347));
	EventRegistration reg = 
	    set.setRenewalFailureListener(listener, handback);
	reg = prepareNormEventRegistration(reg);

	if (!set.equals(reg.getSource())) 
	    throw new TestException( "Source object in event registration is not set");

	if (reg.getID() != LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID)
	    throw new TestException( "Event ID in registration is not correct");

	if (!reg.getLease().equals(prepareNormLease(set.getRenewalSetLease())))
	    throw new TestException( "Lease in registration is not correct");

	listener.setRegInfo(reg, handback);

	final Lease first = LocalLease.getLocalLease(0, 60000, 1, 1);
	listener.setExpectedLease(first);
	set.renewFor(first, Lease.FOREVER);

	logger.log(Level.INFO, "Sleeping for " + eventWait + " ms");
	Thread.sleep(eventWait);

	String listenerRslt = listener.didPass();
	if (listenerRslt != null) {
	    throw new TestException( listenerRslt);
	}			    	

	if (!listener.didReceiveExpected()) 
	    throw new TestException( "Did not receive an appropriate event");

	if (!tryShutdown) 
	    return;

	logger.log(Level.INFO, "First half of test passed, ");

	Admin admin = getManager().getAdmin(lrs);
	if (admin instanceof ActivatableServiceStarterAdmin) {
	    logger.log(Level.INFO, "trying second half");
	    final Lease second = LocalLease.getDestructingLocalLease(Lease.FOREVER,
							   60000, 1, 1, 2);
	    listener.setExpectedLease(second);
	    set.renewFor(second, Lease.FOREVER);

	    if (listener.didReceiveExpected()) {
		throw new TestException( 
				     "Got 2nd event before "
				   + "we killed the server");   
	    }    
	    shutdown(0);

	    logger.log(Level.INFO, "Sleeping for " + eventWait + " ms");
	    Thread.sleep(eventWait);

	    listenerRslt = listener.didPass();
	    if (listenerRslt != null) {
		throw new TestException( listenerRslt);
	    }			    	

	    if (!listener.didReceiveExpected()) {
		throw new TestException( 
				     "Did not receive an 2nd event");
	    }
	} else {
	    logger.log(Level.INFO, "service is not activable, skipping second half");
	}
    }	

    /** Listener class that does some checking and dispaches to the owner */
    private class OurListener extends RemoteListener {
	// $$$ should probably keep a map of sequence numbers and make
	// sure they get incremented like we expect.

	/** The lease we should expect in the next event */
	private Lease expected = null;

	/** True if we have recived the expected event */
	private boolean expectedReceived = true;

	/** Event registration we are expecting events from  */
	private EventRegistration registation;

	/** Handback object we expect to see */
	private MarshalledObject handback;

	/** Set to a discriptive non-null value if there is an error */
	private String rslt = null;

	/**
	 * Simple constructor
	 * @param log Log to send messages to
	 */
	private OurListener() throws RemoteException {
	}

	/** 
	 * Set the registion and handback so we can do basic error checking
	 */
	private void setRegInfo(EventRegistration er, MarshalledObject hb) {
	    registation = er;
	    handback = hb;
	}

	/**
	 * Set the lease we should expect in the next event
	 */
	private void setExpectedLease(Lease l) {
	    expected = l;
	    expectedReceived = false;
	}

	/**
	 * Return true if we have recived the expected lease
	 */
	private boolean didReceiveExpected() {
	    return expectedReceived;
	}

	/**
	 * Set rslt string if it is not already set
	 */
	private void setRsltIfNeeded(String newResult) {
	    if (rslt == null) {
		rslt = newResult;
	    }
	}
   
	/**
	 * Return null if we dected no error, and a disciptive string otherwise
	 */
	private String didPass() {
	    return rslt;
	}

	public void notify(RemoteEvent theEvent) {
	    if (registation == null || expected == null) {
		setRsltIfNeeded("TEST CODE ERROR:Event recived before " +
				"OurListener was fully initialized");
		return;
	    }
            	       
	    // check source
	    if (!theEvent.getSource().equals(registation.getSource())) {
		setRsltIfNeeded("Service sent event with wrong source");
		return;
	    }

	    // Check event ID
	    if (theEvent.getID() != registation.getID()) {
		setRsltIfNeeded("Service sent event with wrong event ID");
		return;
	    }

	    try {
		final RenewalFailureEvent rfe = (RenewalFailureEvent)theEvent;

		try {		    
		    final Throwable error = rfe.getThrowable();
		    logger.log(Level.INFO, "Received a RenewalFailureEvent with a " +
				"throwable of " + error);
		    if (error != null) {
			setRsltIfNeeded("Got non-null throwable in event");
			return;
		    }

		    final Lease l = rfe.getLease();
		    if (!l.equals(expected)) {
			setRsltIfNeeded("Did not get lease we expected");
			return;
		    }

		    expectedReceived = true;
		} catch (IOException e) {
		    logger.log(Level.INFO, "Problem logging event");
		    e.printStackTrace();
		    setRsltIfNeeded("Service set a failure event with a " +
			"lease and/or throwable that could not be unpacked");
		} catch (ClassNotFoundException e) {
		    logger.log(Level.INFO, "Problem logging event");
		    e.printStackTrace();
		    setRsltIfNeeded("Service set a failure event with a " +
			"lease and/or throwable that could not be unpacked");
		}
	    } catch (ClassCastException e) {
		logger.log(Level.INFO, "Problem logging event");
		e.printStackTrace();
		setRsltIfNeeded("Service sent an event which was not of " +
				"type RenewalFailureEvent");		
	    }
	}
    }
}
