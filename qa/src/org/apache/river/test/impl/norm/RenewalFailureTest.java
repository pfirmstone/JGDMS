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

import java.io.PrintWriter;
import java.io.IOException;
import java.io.InvalidClassException;

import net.jini.io.MarshalledInstance;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ServerException;
import java.rmi.NoSuchObjectException;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lease.LeaseDeniedException;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.EventRegistration;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

import org.apache.river.test.share.TestBase;


/** 
 * Test creates a lease a number of leases, places them all in the 
 * renewal set and then forces a number of failure and makes sure that
 * the appropriate events get generated, and that the leases are removed
 * from the set.
 */
public class RenewalFailureTest extends TestBase implements Test {
    /** Wiggle room for various timing parameters */
    final static private long slop = 10000;

    /** Max renwal length to grant */
    private long renewGrant;

    /** Number of renewals to wait until generating failures */
    private int renewalsUntilFailure;

    /** 
     * Object which exports the necessary remote interface to
     * handle lease renewal requests and dispaches to the right owner
     */
    private LeaseBackEndImpl home;

    /** Should we try shuting down the service under test? */
    private boolean tryShutdown;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	this.parse();
        return this;
    }

    /**
     * Parse our args
     * <DL>
     *
     * <DT>-renew_count <var>int</var><DD> Number of times the lease
     * should be renewed before generating errors
     *
     * <DT>-renew_grant <var>int</var><DD> Length of max renewal
     * grants in milliseconds
     * </DL> 
     *
     * <DT>-tryShutdown <DD>If used the test will kill the VM the service
     * is running in after adding the client lease to the set and again
     * after removing the lease from the set.
     * </DL> 
     */
    protected void parse() throws Exception {
	super.parse();
	renewGrant = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.renew_grant", 60 * 1000);
	renewalsUntilFailure = getConfig().getIntConfigVal("org.apache.river.test.impl.norm.renew_count", 0);
	tryShutdown = getConfig().getBooleanConfigVal("org.apache.river.test.impl.norm.tryShutdown", false);
    }

    /**
     * Create a FailingOwner and corresponding lease and register it with
     * the LRS.
     */
    private void createAndRegisterLease(Throwable        toThrow, 
					boolean          definite,
					LeaseRenewalSet  set)
        throws RemoteException					
    {

	final long now = System.currentTimeMillis();
	final long initExp = now + renewGrant;
	final FailingOwner owner = new FailingOwner(toThrow,
						    definite,
						    renewalsUntilFailure, 
						    renewGrant);
	final Lease lease = home.newLease(owner, initExp);
	set.renewFor(lease, Lease.FOREVER);
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet     set = lrs.createLeaseRenewalSet(Lease.FOREVER);
        set = prepareSet(set);
	addLease(prepareNormLease(set.getRenewalSetLease()), false);

	// Register for failure events
	final OurListener listener = new OurListener();
        listener.export();
	final MarshalledInstance handback = new MarshalledInstance(new Long(347));
	EventRegistration reg = 
	    set.setRenewalFailureListener(listener, handback.convertToMarshalledObject());
	reg = prepareNormEventRegistration(reg);

	if (!set.equals(reg.getSource())) 
	    throw new TestException("Source object in event registration is not set");

	if (reg.getID() != LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID)
	    throw new TestException("Event ID in registration is not correct");

	if (!reg.getLease().equals(prepareNormLease(set.getRenewalSetLease())))
	    throw new TestException("Lease in registration is not correct");

	listener.setRegInfo(reg, handback);

	if (tryShutdown) {
	    shutdown(0);
	    Thread.sleep(10000);
        }

	// Create owners, leases, and registrations.
	logger.log(Level.INFO, "Creating leases and adding them to set");

	home = new LeaseBackEndImpl(17);
        home.export();
	createAndRegisterLease(
	    new RemoteException("Synthetic RemoteException"), false, set);
	createAndRegisterLease(
	    new NoSuchObjectException("Synthetic NoSuchObjectException"), true,
	    set);
	createAndRegisterLease(
	    new RuntimeException("Synthetic RuntimeException"), true, set);
	createAndRegisterLease(
	    new Error("Synthetic Error"), true, set);
	createAndRegisterLease(
	    new UnknownLeaseException("Synthetic UnknownLeaseException"), true,
	    set);
	createAndRegisterLease(
	    new LeaseDeniedException("Synthetic LeaseDeniedException"), true,
	    set);
	createAndRegisterLease(
	    new OutOfMemoryError("Synthetic OutOfMemoryError"), 
	    false, set);
	createAndRegisterLease(
	    new LinkageError("Synthetic LinkageError"), 
	    false, set);
	createAndRegisterLease(
	    new MarshalException("Synthetic MarshalException",
				  new InvalidClassException("Synthetic")), 
				  true, set);
	createAndRegisterLease(
	    new MarshalException("Synthetic MarshalException", null),
				 false, set);
	createAndRegisterLease(
	    new MarshalException("Synthetic MarshalException", 
				 new IOException()),
				 false, set);
	createAndRegisterLease(
	    new UnmarshalException("Synthetic UnmarshalException",
				   new InvalidClassException("Synthetic")), 
				   true, set);
	createAndRegisterLease(
	    new UnmarshalException("Synthetic UnmarshalException", null),
				   false, set);
	createAndRegisterLease(
	    new UnmarshalException("Synthetic UnmarshalException", 
				   new IOException()),
				   false, set);
	createAndRegisterLease(
	    new ServerException("Synthetic ServerException", 
				new RemoteException()),
				false, set);
	createAndRegisterLease(
	    new ServerException("Synthetic ServerException", 
				new NoSuchObjectException("Synthetic")),
				true, set);
	createAndRegisterLease(
	    new ServerException("Synthetic ServerException", 
				new UnmarshalException("Synthetic UnmarshalException",
						       new InvalidClassException("Synthetic"))),
				true, set);


	// Wait for failures to hapen and events to be recieved
	final long sleepTime = 
	    (renewalsUntilFailure + 1) * renewGrant + // Time until errors
	    renewGrant * 2;  // Allow for propagation and make sure
			     // renews don't happen after failed renewals
	logger.log(Level.INFO, "Sleeping for " + sleepTime + "ms");
	Thread.sleep(sleepTime);

	// Try to remove all the lease (should get null back each time)
	Lease leases[] = home.getLeases();
	for (int i=0; i<leases.length; i++) {
	    final Lease lease = leases[i];
	    final Lease rtnLease = set.remove(lease);
	    if (rtnLease != null) 
		throw new TestException(rtnLease + " was not removed");
	}

	if (tryShutdown) {
	    shutdown(0);
	    Thread.sleep(10000);

	    // Check removal again
	    for (int i=0; i<leases.length; i++) {
		final Lease lease = leases[i];
		final Lease rtnLease = set.remove(lease);
		if (rtnLease != null) 
		    throw new TestException(rtnLease + " was not removed");
	    }
	}

	// Check with each owner to make sure everything happend according to 
	// plan
	LeaseOwner owners[] = home.getOwners();
	for (int i=0; i<owners.length; i++) {
	    final FailingOwner owner = (FailingOwner)owners[i];
	    final String rslt = owner.didPass();
	    if (rslt != null) {
		throw new TestException(rslt);
	    }			    
	}

	// Check with event handler to make sure it did not detect 
	// any problems
	final String listenerRslt = listener.didPass();
	if (listenerRslt != null) {
	    throw new TestException(listenerRslt);
	}
    }
    
    /** Listener class that does some checking and dispaches to the owner */
    private class OurListener extends RemoteListener {
	// $$$ should probably keep a map of sequence numbers and make
	// sure they get incremented like we expect.

	/** Event registration we are expecting events from  */
	private EventRegistration registation;

	/** Handback object we expect to see */
	private MarshalledInstance handback;

	/** Set to a discriptive non-null value if there is an error */
	private String rslt = null;

	/**
	 * Simple constructor
	 */
	private OurListener() throws RemoteException {
	}

	/** 
	 * Set the registion and handback so we can do basic error checking
	 */
	private void setRegInfo(EventRegistration er, MarshalledInstance hb) {
	    registation = er;
	    handback = hb;
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
	    if (registation == null) {
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

	    // Pass the event on to owner
	    try {
		final RenewalFailureEvent rfe = (RenewalFailureEvent)theEvent;
		try {		    
		    logger.log(Level.INFO, "Received a RenewalFailureEvent with a " +
				"throwable of " + rfe.getThrowable());
		    final TestLease l = (TestLease)rfe.getLease();
		    final FailingOwner owner = 
			(FailingOwner)home.getOwner(l);
		    owner.logEvent(rfe);
		} catch (ClassCastException e) {
		    logger.log(Level.INFO, "Problem logging event");
		    e.printStackTrace();
		    setRsltIfNeeded("Service sent a failure event with a " +
		        "lease that was not of the type " +
			"TestLease");				    
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
	    } catch (RuntimeException e) {
		logger.log(Level.INFO, "Problem logging event");
		e.printStackTrace();
		setRsltIfNeeded("Unexpected runtime exception");
	    } catch (Error e) {
		logger.log(Level.INFO, "Problem logging event");
		e.printStackTrace();
		setRsltIfNeeded("Unexpected error");
	    }
		
	}
    }
}	
