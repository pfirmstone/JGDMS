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
package com.sun.jini.test.spec.renewalmanager;

import java.util.logging.Level;

import java.io.PrintWriter;

import java.util.List;
import java.util.Iterator;
import java.rmi.RemoteException;

import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseListener;
import net.jini.lease.DesiredExpirationListener;
import net.jini.lease.LeaseRenewalEvent;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;

/**
 * Register a number of leases with various desired and actual expirations
 * and types of listeners and make sure that the correct events come back.
 */
public class EventTest extends QATestEnvironment implements Test {
    /** The utility under test */
    private LeaseRenewalManager lrm;

    /**
     * Sets up the testing environment.
     */
    public Test construct(QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // output the name of this test
       logger.log(Level.FINE, "Test Name = " + this.getClass().getName());
	
       // Announce where we are in the test
       logger.log(Level.FINE, "EventTest:In setup() method.");

       // capture an instance of the Properties file.
       QAConfig config = (QAConfig)getConfig();
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * Create a GoodLeaseDEL and place in the LRM and the passed list
     * @param leases     List to add lease too
     * @param initDuration Initial duration of lease
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     *                   value can be batched together
     * @param id         Uniuque ID for this lease
     * @param desiredDuration Desired duration of lease
     */
    private void addGoodLeaseDEL(List leases, long initDuration, long renewLimit,
			      long bundle, long id, long desiredDuration) 
    {
	final long now = System.currentTimeMillis();
	final long desiredExpiration 
	    = (now + desiredDuration>0)?(now+desiredDuration):Long.MAX_VALUE;
	final GoodLeaseDEL l = 
	    new GoodLeaseDEL(now + initDuration, 
			  renewLimit, bundle, id, this, desiredExpiration);
	lrm.renewUntil(l, desiredExpiration, l);
	leases.add(l);
    }

    /**
     * Create a GoodLease and place in the LRM and the passed list
     * @param leases     List to add lease too
     * @param initDuration Initial duration of lease
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     *                   value can be batched together
     * @param id         Uniuque ID for this lease
     * @param desiredDuration Desired duration of lease
     */
    private void addGoodLease(List leases, long initDuration, long renewLimit,
			      long bundle, long id, long desiredDuration) 
    {
	final long now = System.currentTimeMillis();
	final long desiredExpiration 
	    = (now + desiredDuration>0)?(now+desiredDuration):Long.MAX_VALUE;
	final GoodLease l = 
	    new GoodLease(now + initDuration, renewLimit, bundle, id, this);
	lrm.renewUntil(l, desiredExpiration, l);
	leases.add(l);
    }

    /**
     * Create a BadLease and place in the LRM and the passed list
     * @param leases     List to add lease too
     * @param initDuration Initial duration of lease
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     *                   value can be batched together
     * @param id         Uniuque ID for this lease
     * @param desiredDuration Desired duration of lease
     * @param toThrow    Exception the lease should throw when renewed
     */
    private void addBadLease(List leases, long initDuration, 
        long bundle, long id, long desiredDuration, Throwable toThrow) 
    {
	final long now = System.currentTimeMillis();
	final long desiredExpiration 
	    = (now + desiredDuration>0)?(now+desiredDuration):Long.MAX_VALUE;
	final BadLease l = 
	    new BadLease(now + initDuration, bundle, id, this, desiredExpiration,
			 toThrow);
	lrm.renewUntil(l, desiredExpiration, l);
	leases.add(l);
    }

    /**
     * Create a BadLeaseDEL and place in the LRM and the passed list
     * @param leases     List to add lease too
     * @param initDuration Initial duration of lease
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     *                   value can be batched together
     * @param id         Uniuque ID for this lease
     * @param desiredDuration Desired duration of lease
     * @param toThrow    Exception the lease should throw when renewed
     */
    private void addBadLeaseDEL(List leases, long initDuration, 
        long bundle, long id, long desiredDuration, Throwable toThrow) 
    {
	final long now = System.currentTimeMillis();
	final long desiredExpiration 
	    = (now + desiredDuration>0)?(now+desiredDuration):Long.MAX_VALUE;
	final BadLeaseDEL l = 
	    new BadLeaseDEL(now + initDuration, bundle, id, this, 
			    desiredExpiration, toThrow);
	lrm.renewUntil(l, desiredExpiration, l);
	leases.add(l);
    }

    /**
     * Iterate over the passed list return a failed status object if 
     * one of the leases was raised a failure
     */
    private TestComponent hadEarlyFailure(List leases) {
	for (Iterator i=leases.iterator(); i.hasNext(); ) {
	    final TestComponent l = (TestComponent)i.next();
	    if (l.hadEarlyFailure()) {
		return l;
	    }
	}

	return null;
    }

    public void run() throws Exception {
	// Announce where we are in the test
	logger.log(Level.FINE, "EventTest: In run() method.");

	long id = 2;
	final List leases = new java.util.LinkedList();
	// Lease that should not get any events
	addGoodLeaseDEL(leases, 60000, 60000, ++id, id, Long.MAX_VALUE);

	// Lease that should get a DER event
	addGoodLeaseDEL(leases, 60000, 60000, ++id, id, 2*60000);

	// Lease that has already reached its desired expiration
	// and should get a DER event
	addGoodLeaseDEL(leases, -5000, 60000, ++id, id, -10000);

	// Lease that has already reached its desired expiration
	// and should get a DER event
	addGoodLeaseDEL(leases, +5000, 60000, ++id, id, -10000);

	// Lease that should get no events
	addGoodLease(leases, 60000, 60000, ++id, id, Long.MAX_VALUE);

	// Lease that would  get a DER event but only registers a LeaesListener
	addGoodLease(leases, 60000, 60000, ++id, id, 2*60000);

	// Lease that has already reached its desired expiration
	// and would get a DER event but only registers a LeaesListener
	addGoodLeaseDEL(leases, -5000, 60000, ++id, id, -10000);

	// Lease that has already reached its desired expiration
	// and would get a DER event but only registers a LeaesListener
	addGoodLeaseDEL(leases, +5000, 60000, ++id, id, -10000);

	// Lease that will generate a renewal failure because of a definie
	// exception
	addBadLease(leases, 60000, ++id, id, 2 * 60000, 
		    new UnknownLeaseException());

	// Lease that will generate a renewal failure because of an indefinie
	// exception
	addBadLease(leases, 60000, ++id, id, 2 * 60000, new RemoteException());

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLease(leases, -20000, ++id, id, -10000, null);

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLease(leases, -20000, ++id, id, +60000, null);

	// Lease that will generate a renewal failure because of a definie
	// exception
	addBadLeaseDEL(leases, 60000, ++id, id, 2 * 60000, 
		    new UnknownLeaseException());

	// Lease that will generate a renewal failure because of an indefinie
	// exception
	addBadLeaseDEL(leases, 60000, ++id, id, 2 * 60000, 
		       new RemoteException());

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLeaseDEL(leases, -20000, ++id, id, -10000, null);

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLeaseDEL(leases, -20000, ++id, id, +60000, null);

	// Do it all again (twicw) but with batching 

	// Lease that should not get any events
	addGoodLeaseDEL(leases, 60000, 60000, 0, ++id, Long.MAX_VALUE);

	// Lease that should get a DER event
	addGoodLeaseDEL(leases, 60000, 60000, 0, ++id, 2*60000);

	// Lease that has already reached its desired expiration
	// and should get a DER event
	addGoodLeaseDEL(leases, -5000, 60000, 0, ++id, -10000);

	// Lease that has already reached its desired expiration
	// and should get a DER event
	addGoodLeaseDEL(leases, +5000, 60000, 0, ++id, -10000);

	// Lease that should get no events
	addGoodLease(leases, 60000, 60000, 0, ++id, Long.MAX_VALUE);

	// Lease that would  get a DER event but only registers a LeaesListener
	addGoodLease(leases, 60000, 60000, 0, ++id, 2*60000);

	// Lease that has already reached its desired expiration
	// and would get a DER event but only registers a LeaesListener
	addGoodLeaseDEL(leases, -5000, 60000, 0, ++id, -10000);

	// Lease that has already reached its desired expiration
	// and would get a DER event but only registers a LeaesListener
	addGoodLeaseDEL(leases, +5000, 60000, 0, ++id, -10000);

	// Lease that will generate a renewal failure because of a definie
	// exception
	addBadLease(leases, 60000, 0, ++id, 2 * 60000, 
		    new UnknownLeaseException());

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLease(leases, -20000, 0, ++id, -10000, null);

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLease(leases, -20000, 0, ++id, +60000, null);

	// Lease that will generate a renewal failure because of a definie
	// exception
	addBadLeaseDEL(leases, 60000, 0, ++id, 2 * 60000, 
		    new UnknownLeaseException());

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLeaseDEL(leases, -20000, 0, ++id, -10000, null);

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLeaseDEL(leases, -20000, 0, ++id, +60000, null);

	// Lease that should not get any events
	addGoodLeaseDEL(leases, 60000, 60000, 1, ++id, Long.MAX_VALUE);

	// Lease that should get a DER event
	addGoodLeaseDEL(leases, 60000, 60000, 1, ++id, 2*60000);

	// Lease that has already reached its desired expiration
	// and should get a DER event
	addGoodLeaseDEL(leases, -5000, 60000, 1, ++id, -10000);

	// Lease that has already reached its desired expiration
	// and should get a DER event
	addGoodLeaseDEL(leases, +5000, 60000, 1, ++id, -10000);

	// Lease that should get no events
	addGoodLease(leases, 60000, 60000, 1, ++id, Long.MAX_VALUE);

	// Lease that would  get a DER event but only registers a LeaesListener
	addGoodLease(leases, 60000, 60000, 1, ++id, 2*60000);

	// Lease that has already reached its desired expiration
	// and would get a DER event but only registers a LeaesListener
	addGoodLeaseDEL(leases, -5000, 60000, 1, ++id, -10000);

	// Lease that has already reached its desired expiration
	// and would get a DER event but only registers a LeaesListener
	addGoodLeaseDEL(leases, +5000, 60000, 1, ++id, -10000);

	// Lease that will generate a renewal failure because of a definie
	// exception
	addBadLease(leases, 60000, 1, ++id, 2 * 60000, 
		    new UnknownLeaseException());

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLease(leases, -20000, 1, ++id, -10000, null);

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLease(leases, -20000, 1, ++id, +60000, null);

	// Lease that will generate a renewal failure because of a definie
	// exception
	addBadLeaseDEL(leases, 60000, 1, ++id, 2 * 60000, 
		    new UnknownLeaseException());

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLeaseDEL(leases, -20000, 1, ++id, -10000, null);

	// Lease that will generate a renewal failure because it has already
	// expired and it has not reached its desired expiration
	addBadLeaseDEL(leases, -20000, 1, ++id, +60000, null);

	final long waitUntil = System.currentTimeMillis() + 3 * 60000;
	TestComponent failedComponet = null;
	synchronized (this) {
	    if (null == (failedComponet = hadEarlyFailure(leases))) {
		// let run() propogate InterruptedException
		wait(waitUntil - System.currentTimeMillis());
		failedComponet = hadEarlyFailure(leases);
	    }
	}

	if (failedComponet != null) 
	    throw new TestException("Failure detected: " +
				    failedComponet.didPass()); 

	for (Iterator i=leases.iterator(); i.hasNext(); ) {
	    final TestComponent l = (TestComponent)i.next();
	    final String rslt = l.didPass();
	    if (rslt != null) {
		throw new TestException("Failure detected:" + rslt);
	    }
	}

	return;
    }

    public void tearDown() {
	super.tearDown();
	logger.log(Level.FINE, "EventTest:In teardown() method.");
	lrm.clear();
    }

    /**
     * Interface for objects that embody certain test cases.
     */
    private interface TestComponent {
	/**
	 * Returns true if the component has detected an early failure.
	 */
	public boolean hadEarlyFailure();

	/**
	 * If the test case associated with this component passed return
	 * null, otherwise return a string describing the problem.
	 */
	public String didPass();
    }
    
    /**
     * Subclass of LocalLease that is well behaved and implements
     * DesiredExpirationListener
     */
    private class GoodLeaseDEL extends GoodLease
	implements DesiredExpirationListener
    {
	/** 
	 * When (if ever) we should expect a desired expiration
	 * event. Long.MAX_VALUE if no event is expected
	 */
	final private long whenExpected;

	/** 
	 * When we recived a desired expiration event, Long.MAX_VALUE
	 * if no event has been recived.
	 */
	private long whenReceived = Long.MAX_VALUE;

	/**
	 * Create a local lease with the specified initial expiration time 
	 * @param initExp    Initial expiration time
	 * @param renewLimit Limit on long each renewal request can be for
	 * @param bundle     Two <code>LocalLeases</code> with the same bundle
	 *                   value can be batched together
	 * @param id         Uniuque ID for this lease
	 * @param notifyOnFailure Object to notify if we detect an early failure
	 * @param whenExpected When (if ever) we should expect a 
	 *                    desired expiration event. Use Long.MAX_VALUE
	 *                    if no event is expected
	 */
	GoodLeaseDEL(long initExp, long renewLimit, long bundle, long id,
		  Object notifyOnFailure, long whenExpected) 
	{
	    super(initExp, renewLimit, bundle, id, notifyOnFailure);
	    this.whenExpected = whenExpected;
	}

	public synchronized void expirationReached(LeaseRenewalEvent e) {
	    whenReceived = System.currentTimeMillis();

            // ordered so that e.getSource doesn't need to be prepared
	    if (!lrm.equals(e.getSource())) {
		setRsltIfNeeded("Lease " + id + " received desired expiration " +
				" reached event with wrong souce");
		return;
	    }

            // ordered so that e.getLease doesn't need to be prepared
	    if (!this.equals(e.getLease())) {
		setRsltIfNeeded("Received desired expiration reached event " +
				"with wrong lease");
		return;
	    }

	    if (e.getException() != null) {
		setRsltIfNeeded("Lease " + id + " received desired expiration " +
				"reached event with non-null exception");
		return;
	    }

	    if (e.getExpiration() != whenExpected) {
		setRsltIfNeeded("Lease " + id + " received desired expiration " +
				"reached event with wrong desired expiration");
		return;
	    }


	    if (whenReceived < whenExpected) {
		setRsltIfNeeded("Lease " + id + " received desired expiration " +
				"reached event sooner than expected");
		return;
	    }
	} 

	public synchronized String didPass() {
	    final String suprslt = super.didPass();
	    if (suprslt != null) 
		return suprslt;

	    // If we were supposted to recive and event, make sure we did
	    if (whenExpected < Long.MAX_VALUE) {
		if (whenReceived == Long.MAX_VALUE) {
		    setRsltIfNeeded("Lease " + id + " did not receive desired " +
				    "expiration reached event");
		    return super.didPass();
		}
	    }

	    return null;
	}
    }

    /**
     * Subclass of LocalLease that is well behaved and implements
     * LeaseListener
     */
    private class GoodLease extends LocalLease 
	implements LeaseListener, TestComponent
    {
	/**
	 * Create a local lease with the specified initial expiration time 
	 * @param initExp    Initial expiration time
	 * @param renewLimit Limit on long each renewal request can be for
	 * @param bundle     Two <code>LocalLeases</code> with the same bundle
	 *                   value can be batched together
	 * @param id         Uniuque ID for this lease
	 * @param notifyOnFailure Object to notify if we detect an early failure
	 */
	GoodLease(long initExp, long renewLimit, long bundle, long id,
		  Object notifyOnFailure) 
	{
	    super(initExp, renewLimit, bundle, id, notifyOnFailure);
	}

	public void notify(LeaseRenewalEvent e) {
	    setRsltIfNeeded("Lease " + id + " received renewal failure event");
	}

	public synchronized boolean hadEarlyFailure() {
	    return getRslt() != null;
	}
    }

    /**
     * Subclass of LocalLease that throws a specified exception when called 
     * and implements desired expiration listener
     */
    private class BadLeaseDEL extends BadLease
	implements DesiredExpirationListener
    {
	/**
	 * Create a BadLeaseDEL lease with the specified initial
	 * expiration time
	 * @param initExp    Initial expiration time
	 * @param bundle     Two <code>LocalLeases</code> with the same bundle
	 *                   value can be batched together
	 * @param id         Uniuque ID for this lease
	 * @param notifyOnFailure 
	 *                   Object to notify if we detect an early failure 
	 * @param desiredExpiration The lease's desired expiration event.
	 * @param toThrow    Exception to throw
	 */
	BadLeaseDEL(long initExp, long bundle, long id, 
		 Object notifyOnFailure, long desiredExpiration,
		 Throwable toThrow)
	{
	    super(initExp, bundle, id, notifyOnFailure, desiredExpiration,
		  toThrow);
	}

	public synchronized void expirationReached(LeaseRenewalEvent e) {
	    setRsltIfNeeded("Lease " + id + " received desired expiration " +
			    "reached event");
	}

    }

    /**
     * Subclass of LocalLease that throws a specified exception when called.
     */
    private class BadLease extends LocalLease
	implements LeaseListener, TestComponent
    {
	/** Exception to throw when renewed */
	private final Throwable toThrow;

	/** Have we recived the expected event */
	private boolean recived = false;

	/** Our desired expiration */ 
	final private long desiredExpiration;

	/**
	 * Create a BadLease lease with the specified initial
	 * expiration time
	 * @param initExp    Initial expiration time
	 * @param bundle     Two <code>LocalLeases</code> with the same bundle
	 *                   value can be batched together
	 * @param id         Uniuque ID for this lease
	 * @param notifyOnFailure 
	 *                   Object to notify if we detect an early failure 
	 * @param desiredExpiration The lease's desired expiration event.
	 * @param toThrow    Exception to throw
	 */
	BadLease(long initExp, long bundle, long id, 
		 Object notifyOnFailure, long desiredExpiration,
		 Throwable toThrow)
	{
	    super(initExp, 60000, bundle, id, notifyOnFailure);
	    this.desiredExpiration = desiredExpiration;
	    this.toThrow = toThrow;
	}

	public void notify(LeaseRenewalEvent e) {
	    if (!lrm.equals(e.getSource())) {
		setRsltIfNeeded("Lease " + id + " received renewal failure " +
				"event with wrong souce");
		return;
	    }

	    if (!this.equals(e.getLease())) {
		setRsltIfNeeded("Lease " + id + " received renewal failure " +
				"event with wrong lease");
		return;
	    }

	    if (e.getException() != toThrow) {
		setRsltIfNeeded("Lease " + id + " received renewal failure " +
				"event with wrong exception");
		return;
	    }

	    if (e.getExpiration() != desiredExpiration) {
		setRsltIfNeeded("Lease " + id + " received renewal failure " +
				"event with wrong desired expiration");
		return;
	    }

	    recived = true;
	}

	public synchronized String didPass() {
	    final String suprslt = super.didPass();
	    if (suprslt != null) 
		return suprslt;

	    if (!recived) {
		setRsltIfNeeded("Lease " + id + " did not receive renewal " +
				"failure event");
		return super.didPass();
	    }

	    return null;
	}

	public synchronized boolean hadEarlyFailure() {
	    return getRslt() != null;
	}

	protected synchronized void renewWork(long duration) 
	    throws RemoteException, LeaseDeniedException, UnknownLeaseException
	{
	    if (toThrow instanceof RemoteException) 
		throw (RemoteException)toThrow;
	    else if (toThrow instanceof LeaseDeniedException) 
		throw (LeaseDeniedException)toThrow;
	    else if (toThrow instanceof UnknownLeaseException) 
		throw (UnknownLeaseException)toThrow;
	    else if (toThrow instanceof RuntimeException) 
		throw (RuntimeException)toThrow;
	    else if (toThrow instanceof Error) 
		throw (Error)toThrow;

	    return;
	}
    }

}
