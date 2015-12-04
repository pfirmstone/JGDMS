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
package org.apache.river.test.impl.norm;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import java.lang.reflect.Constructor;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.UnknownEventException;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.ExpirationWarningEvent;
import net.jini.lease.RenewalFailureEvent;

import org.apache.river.test.share.TestBase;


/** 
 * Test creates a set and registers for both warning and renewail failure
 * events.  One listener will throw an exception, the other will succed.
 * We the take action that will cause both to listers to recive an event.
 * If both recive an event we optionally shutdown the server, and then
 * take the action again.  At this point we pass if only one of the 
 * events are recived (the one with the listener that did not throw
 * an exception) and fail otherwise
 */
public class RemoveExactlyOneTest extends TestBase implements Test {
    /** Total time we are willing to wait for events to happen */
    private long eventWaitFor;

    /** When we want renewal failures to happen */
    private long whenFailure;

    /** When we want warning event to happen */
    private long whenWarning;

    /** 
     * True if the failure listener should fail, false if warning should
     * fail
     */
    private boolean failureFails;

    /** 
     * How the failing listener should fail
     */
    private Throwable throwThis;

    /** Should we try shuting down the service under test? */
    private boolean tryShutdown;

    /** 
     * The warning listener
     */
    private BaseListener warningListener;

    /** 
     * The failure listener
     */
    private BaseListener failureListener;

    /** 
     * The id to use for the local leases that get created 
     */
    private long leaseID;
	
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	this.parse();
        return this;
    }

    /**
     * Parse our args
     * <DL>
     *
     * <DT>-eventWaitFor <var>int</var><DD> Length of time from time t,
     * willing to wait for events to arive.
     *
     * <DT>-whenFailure <var>int</var><DD> Upper bound length of time
     * from time t, renewal failure events should occure
     *
     * <DT>-whenWarning <var>int</var><DD> Lower bound from time t on
     * when warning events should occure
     *
     * <DT>-leaseID <var>int</var><DD> ID to give to the local lease
     * we create
     *
     * <DT>-throwThis <var>String</var><DD> Throwable that should be
     * thrown by failing listener.
     *
     * <DT>-failureFails<DD> If used the failure listner will fail,
     * if not warning listener will
     *
     * <DT>-tryShutdown <DD>If used the test will kill the VM the service
     * is running in after adding the client leases to the set and again
     * after all the leases should have expired.
     * </DL> 
     */
    protected void parse() throws Exception {
	super.parse();
	eventWaitFor = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.eventWaitFor", 2 * 60 * 1000);
	whenFailure = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.whenFailure", 60 * 1000);
	whenWarning = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.whenWarning", 10 * 1000);
	leaseID = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.leaseID", 0);
	failureFails = getConfig().getBooleanConfigVal("org.apache.river.test.impl.norm.failureFails", false);	
	tryShutdown = getConfig().getBooleanConfigVal("org.apache.river.test.impl.norm.tryShutdown", false);
	final String throwThisName = getConfig().getStringConfigVal("org.apache.river.test.impl.norm.throwThis",
			    "net.jini.core.event.UnknownEventException");

	try {
	    final Class exClass = Class.forName(throwThisName);
	    final Constructor ctor = 
                 exClass.getConstructor(new Class[]{String.class});
	    throwThis = (Throwable)ctor.newInstance(
			    new String[]{"Physco Killer"});
    
	} catch (Throwable e) {
	    e.printStackTrace();
	    throw new TestException(
                "Something bad happend parsing -throwThis " + throwThisName);
	} 
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	final LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet     set = 
	    lrs.createLeaseRenewalSet(eventWaitFor);
        set = prepareSet(set);
	final Lease setLease = prepareNormLease(set.getRenewalSetLease());
	addLease(setLease, false);


	// Create and register listeners
	warningListener = new WarningListener(failureFails?null:throwThis);
        warningListener.export();
	failureListener = new BaseListener(failureFails?throwThis:null);
        failureListener.export();

	MarshalledObject handback = new MarshalledObject(new Long(3));
	logger.log(Level.FINER, "setting expiration warning listener");
	EventRegistration reg =
	    set.setExpirationWarningListener(warningListener, 
		eventWaitFor - whenWarning, handback); 
	logger.log(Level.FINER, "preparing returned registration");
        reg = prepareNormEventRegistration(reg);
	logger.log(Level.FINER, "completing initialization of listener");
	warningListener.setRegInfo(reg, handback);

	handback = null;
	reg = set.setRenewalFailureListener(failureListener, handback); 
	reg = prepareNormEventRegistration(reg);
	failureListener.setRegInfo(reg, handback);

	// Create and add lease that will cause failure
	long now = System.currentTimeMillis();
	Lease l = LocalLease.getFailingLocalLease(now + whenFailure, 0, leaseID, 1, 0);
	set.renewFor(l, Lease.FOREVER);

	// Sleep 
	logger.log(Level.INFO, "Sleeping for " + eventWaitFor  + " ms");
	Thread.sleep(eventWaitFor);
	logger.log(Level.INFO, "Awake");

	// Check to see if we have failed yet
	checkListener(warningListener, "Warning", true);
	checkListener(failureListener, "Failure", true);

	if (tryShutdown)
	    shutdown(0);

	// Generate 2nd set of events

	// Create and add lease that will cause failure
	setLease.renew(eventWaitFor);
	now = System.currentTimeMillis();
	l = LocalLease.getFailingLocalLease(now + whenFailure, 0, leaseID, 1, 0);
	set.renewFor(l, Lease.FOREVER);

	// Sleep 
	logger.log(Level.INFO, "Sleeping for " + eventWaitFor  + " ms");
	Thread.sleep(eventWaitFor);
	logger.log(Level.INFO, "Awake");

	// Check to see if we have failed yet
	checkListener(warningListener, "Warning", false);
	checkListener(failureListener, "Failure", false);
    }

    void checkListener(BaseListener listener, String name, boolean firstPass) throws TestException {
	final String msg = listener.didPass();
	if (msg != null)
	    throw new TestException (msg);
	final long callCount = listener.getCallCount();
	final long shouldBe = (firstPass || listener.isThrowing())?1:2;
	
	if (callCount != shouldBe) 
	    throw new TestException (name + " listener recived " + callCount + " events should " +
		 "have recived at this point " + shouldBe);
    }

	
    private class BaseListener extends RemoteListener {
	/** Event registration we are expecting events from  */
	protected EventRegistration registation;

	/** Handback object we expect to see */
	private MarshalledObject handback;

	/** Set to a discriptive non-null value if there is an error */
	private String rslt = null;

	/** Number of times we have been called */
	private long callCount;

	/** If non-null throw this exception when notify is called */
	private Throwable throwThis;

	/*
	 * Simple constructor
	 * @param throwThis Throwable that should be thrown by notify
	 *        or null.
	 */
	private BaseListener(Throwable throwThis) 
	    throws RemoteException
	{
	    this.throwThis = throwThis;
	}

	/** 
	 * Set the registion and handback so we can do basic error checking
	 */
	private void setRegInfo(EventRegistration er, MarshalledObject hb) {
	    registation = er;
	    handback = hb;
	}

	/**
	 * Set rslt string if it is not already set
	 */
	protected void setRsltIfNeeded(String newResult) {
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

	/** 
	 * Return the number of times we were called
	 */
	private long getCallCount() {
	    return callCount;
	}

	/**
	 * Return true if this listener's notify method throws exceptions
	 */
	private boolean isThrowing() {
	    return throwThis != null;
	}

	public void notify(RemoteEvent theEvent) throws UnknownEventException {
 	    logger.log(Level.INFO, "Recived " + theEvent + " event");

	    callCount++;
	    
	    if (registation == null) {
		logger.log(Level.INFO, "OurListener not initialized");
		setRsltIfNeeded("TEST CODE ERROR:Event recived before " +
				"OurListener was fully initialized");
		return;
	    }
            	       
	    // check source
	    if (!theEvent.getSource().equals(registation.getSource())) {
		logger.log(Level.INFO, "Soruce miss-match");
		setRsltIfNeeded("Service sent event with wrong source");
		return;
	    }

	    // Check event ID
	    if (theEvent.getID() != registation.getID()) {
		logger.log(Level.INFO, "Event ID miss-match");
		setRsltIfNeeded("Service sent event with wrong event ID");
		return;
	    }
	    
	    if (throwThis == null) {
 		logger.log(Level.INFO, "Normal return");
		return;
	    }

	    if (throwThis instanceof RuntimeException) {
 		logger.log(Level.INFO, "throwing RuntimeException");
		throw (RuntimeException)throwThis;
	    } else if (throwThis instanceof Error) {
 		logger.log(Level.INFO, "throwing Error");
		throw (Error)throwThis;
	    } else if (throwThis instanceof UnknownEventException) {
 		logger.log(Level.INFO, "throwing UnknownEventException");
		throw (UnknownEventException)throwThis;
	    } else {
		setRsltIfNeeded("TEST CODE ERROR:Can't throw " + throwThis);
		return;
	    }
	}
    }

    private class WarningListener extends RemoveExactlyOneTest.BaseListener {
	/*
	 * Simple constructor
	 * @param throwThis Throwable that should be thrown by notify
	 *        or null.
	 */
	private WarningListener(Throwable throwThis) 
	    throws RemoteException
	{
	    super(throwThis);
	}

	public void notify(RemoteEvent theEvent) throws UnknownEventException {
	    // Before possably failing renew the lease

	    if (registation == null) {
		logger.log(Level.INFO, "OurListener not initialized");
		setRsltIfNeeded("TEST CODE ERROR:Event recived before " +
				"OurListener was fully initialized");
		return;
	    }
	    try {
		final ExpirationWarningEvent ewe = 
		    (ExpirationWarningEvent)theEvent;
		if (ewe == null) {
		    logger.log(Level.INFO, "notify called with null event");
		    setRsltIfNeeded(" notify called with null event");
		    return;
		}
		if (!registation.getLease().equals(prepareNormLease(ewe.getRenewalSetLease()))) {
		    logger.log(Level.INFO, "Lease miss-match");
		    setRsltIfNeeded("Lease in warning event did not " +
				    "match set lease");
		    return;
		}

		// $$$ should also check lease in event to make sure
		// expiration is up to date.

		// deal with renewing the lease
		logger.log(Level.INFO, "Renewing set lease");
		prepareNormLease(ewe.getRenewalSetLease()).renew(Lease.FOREVER);
		
	    } catch (ClassCastException e) {
		setRsltIfNeeded("Event was not of type ExpirationWarningEvent");
		return;
	    } catch (Exception e) {
		e.printStackTrace();
		setRsltIfNeeded("Recived exception durring renewal " +
				e.getMessage());
		return;
	    }

	    super.notify(theEvent);
	}
    }
}

