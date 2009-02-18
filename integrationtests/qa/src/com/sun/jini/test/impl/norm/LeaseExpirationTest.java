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

// Test harness specific classes
import java.io.PrintWriter;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;

import net.jini.core.lease.Lease;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.EventRegistration;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.ExpirationWarningEvent;

import com.sun.jini.test.share.TestBase;

/** 
 * Test creates a number of leases places them in a renewal set and lets
 * the set's lease expire.  Fails if the leases it places in the set
 * are renewed after the set expires.
 */
public class LeaseExpirationTest extends TestBase {

    /** Ammount of slop we are willing to tolerate around renewals */
    private long slop;

    /** Max renwal length to grant */
    private long renewGrant;

    /** Length of lease to ask for on set */
    private long setLeaseLength;

    /** Number of times to renew lease on set */
    private int setRenewals;

    /** Number of leases to place in set */
    private int leaseCount;

    /** If <code>true</code> we should register for expiration warning events */
    private boolean shouldRegister;

    /** Min warning time we will request if shouldRegister is true */
    private long minWarning;

    /** Should we try shuting down the service under test? */
    private boolean tryShutdown;

    /** 
     * Listener for expiration warning events, also tracks when
     * the set lease should expire
     */
    private OurListener listener;

    /** The current expiration time of the set's lease */
    private long setLeaseCurrentExpiration;

    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	this.parse();
    }

    /**
     * com.sun.jini.test.impl.norm.slop (long): 
     *   Amount of slop in milliseconds we are willing to tolrate.  
     *   Renewals that take place up to slop milliseconds after the 
     *   lease is sceduled to expire will not fail the test.
     * <p>
     * com.sun.jini.test.impl.norm.lease_length (long): 
     *   Length of lease to ask for when creating the set.
     * <p>
     * com.sun.jini.test.impl.norm.set_renewals (int): 
     *   Number of times to renew the lease on the set.
     * <p>
     * com.sun.jini.test.impl.norm.lease_count (int): 
     *   Number of leases to create and place in set.
     * <p>
     * com.sun.jini.test.impl.norm.renew_grant (long): 
     *   Length of max renewal grants in milliseconds.
     * <p>
     * com.sun.jini.test.impl.norm.warning (boolean): 
     *   If used the test will register for expiration warning events.
     * <p>
     * com.sun.jini.test.impl.norm.min_warning (long): 
     *   If warning is set the number of milliseconds before expiration 
     *   we want a warning event.
     * <p>
     * com.sun.jini.test.impl.norm.tryShutdown (boolean): 
     *   If used the test will kill the VM the service
     *   is running in after adding the client leases to the set and again
     *   after all the leases should have expired.
     */
    protected void parse() throws Exception {
	super.parse();
	slop = getConfig().getLongConfigVal(
            "com.sun.jini.test.impl.norm.slop", 5000);
	setLeaseLength = getConfig().getLongConfigVal(
            "com.sun.jini.test.impl.norm.lease_length", 60 * 1000);
	setRenewals = getConfig().getIntConfigVal(
            "com.sun.jini.test.impl.norm.set_renewals", 0);
	leaseCount = getConfig().getIntConfigVal(
            "com.sun.jini.test.impl.norm.lease_count", 10);
	renewGrant = getConfig().getLongConfigVal(
            "com.sun.jini.test.impl.norm.renew_grant", 10 * 1000);
	shouldRegister = getConfig().getBooleanConfigVal(
            "com.sun.jini.test.impl.norm.warning", false);
	minWarning = getConfig().getLongConfigVal(
            "com.sun.jini.test.impl.norm.min_warning", 10000);
	tryShutdown = getConfig().getBooleanConfigVal(
            "com.sun.jini.test.impl.norm.tryShutdown", false);
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	final LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(setLeaseLength);
	set = prepareSet(set);
	final Lease setLease = prepareNormLease(set.getRenewalSetLease());
	setLeaseCurrentExpiration = setLease.getExpiration();
	addLease(setLease, true);

	listener = new OurListener(setLease, setRenewals);

	// If we need to register for warning events
	if (shouldRegister) {	    
	    logger.log(Level.INFO, "Registering for warning events");

	    final MarshalledObject handback = 
		new MarshalledObject(new Long(348));
	    EventRegistration reg = set.setExpirationWarningListener(
                listener, minWarning, handback);
            reg = prepareNormEventRegistration(reg);

	    if (!set.equals(reg.getSource())) {
		throw new TestException(
                    "Source object in event registration is not set");
            }
	    if (reg.getID() != LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID){
		throw new TestException(
                    "Event ID in registration is not correct");
            }
	    if (!reg.getLease().equals(
                prepareNormLease(set.getRenewalSetLease())))
            {
		throw new TestException(
                    "Lease in registration is not correct");
            }
	    listener.setRegInfo(reg, handback);
	}

	// Create owners and leases
	final LeaseBackEndImpl home = new LeaseBackEndImpl(leaseCount);
	for (int i = 0; i < leaseCount; i++) {
	    final long now = System.currentTimeMillis();
	    final long initExp = now + renewGrant;
	    final OurOwner owner = new OurOwner(initExp);
	    final Lease l = home.newLease(owner, initExp);
	    set.renewFor(l, Lease.FOREVER, Lease.ANY);
	}

        if (tryShutdown) {
            logger.log(Level.INFO, "Shuting down at: " 
                + System.currentTimeMillis() + " milliseconds");
            shutdown(0);
        }

	if (!listener.waitExpire((1+setRenewals) * (setLeaseLength+slop))) {
	    throw new TestException(
                "The expected number of expiration warning events did "
		+ "not occur in the alloted time");
        }

	// Wait for the leases that we put in the set to expire
	long sleepTime = renewGrant * 2;
	logger.log(Level.INFO, "Sleeping for " + sleepTime + "ms to let our leases"
                + "expire and give renewals time to propagate");
	Thread.sleep(sleepTime);

        if (tryShutdown) {
            logger.log(Level.INFO, "Shuting down at: " 
                + System.currentTimeMillis() + " milliseconds");
            shutdown(0);
	    logger.log(Level.INFO, "Sleeping for " + sleepTime + "ms to let our leases"
                + "expire and give renewals time to propagate (again)");
	    Thread.sleep(sleepTime);
	}

	// Ask each of the owner if there were any problems
	LeaseOwner owners[] = home.getOwners();
	for (int i = 0; i < owners.length; i++) {
	    final OurOwner owner = (OurOwner)owners[i];
	    final String result = owner.didPass();
	    if (result != null) {
		throw new TestException(result);
	    }			    
	} 

	// Ask the listener if there were any problems
	final String result = listener.didPass();
	if (result != null) {
	    throw new TestException(result);
	}
    }


    private class OurOwner extends BaseOwner {
	private OurOwner(long initialExpiration) {
	    super(initialExpiration, 
		  LeaseExpirationTest.this.renewGrant,
		  Lease.FOREVER, LeaseExpirationTest.this.slop,
		  Lease.ANY, LeaseExpirationTest.this);
	}

	/**
	 * Check not only for the right extension, but also 
	 * if the set's lease should have expired by now
	 */
	boolean isValidExtension(long extension) {
	    if (listener.isPast(now)) {
		setRsltIfNeeded("Service renewed lease after set expired, " +
				 listener.howPast(now) + " ms late");
	    }
	    if (extension != Lease.ANY) {
		setRsltIfNeeded("Service asked for an extension of " +
				extension + ", not Lease.ANY");
		return false;
	    }
	    return true;
	}

	boolean isTwoArg() {return false;}

	/**
	 * Return null if we dected no error, and a disciptive string otherwise
	 */
	String didPass() {
	    if (rslt != null) {
		return rslt;
            }

	    // It is ok if the lease has expired, however, it should
	    // not expire before the set's lease did.
	    synchronized (this) {
		if (expiration < setLeaseCurrentExpiration)
		    rslt = "Lease expired before the set did";
	    }
	    return rslt;	    
	} 
    }


    private class OurListener extends RemoteListener {
	/** Event registration we are expecting events from  */
	private EventRegistration registation;

	/** Handback object we expect to see */
	private MarshalledObject handback;

	/** Set to a discriptive non-null value if there is an error */
	private String result = null;

	/** Lease on renewal set */
	private Lease setLease;

	/** Set after last renewal to the expiration time */
	private long setExpiration = -1;

	/** semaphore that tracks how many renewals we have left */
	private Semaphore renewalsLeft;
	
	/**
	 * Simple constructor
	 * @param setLease The lease on the set
	 * @param renewals number of times to renew the set's lease
	 */
	private OurListener(Lease setLease, int renewals) 
	    throws RemoteException 
	{
	    this.setLease = setLease;
	    renewalsLeft = new Semaphore(renewals);
	    if (renewalsLeft.get() == 0) {
		setExpiration = setLease.getExpiration();
	    }
	}

	/** 
	 * Set the registion and handback so we can do basic error checking
	 */
	private void setRegInfo(EventRegistration er, MarshalledObject hb) {
	    registation = er;
	    handback = hb;
	}

	/**
	 * Are we past the time when we should not be reciving events and
	 * lease renewals.
	 * @param now The current time
	 */
	private synchronized boolean isPast(long now) {
	    if (setExpiration > 0) {
		return now > setExpiration + slop;
            }
	    return false;
	}

	/**
	 * Return the diffrence between when the last action should have
	 * be taken by the test and now
	 */
	private synchronized long howPast(long now) {
	    return setExpiration - now;
	}

	/**
	 * Set result string if it is not already set
	 */
	private void setRsltIfNeeded(String newResult) {
	    if (result == null) {
		result = newResult;
	    }
	}

	/**
	 * Block until the set's lease should have expired
	 * @param timeout maximum time to wait for all of the
	 *                renewal calls to occur.
	 * @return false if the timeout expires and true otherwise
	 * @throws InterruptedException if this thread is interupted
	 */
	private boolean waitExpire(long timeout) throws InterruptedException {
	    final int count = renewalsLeft.waitOnZero(timeout);

	    if (count != 0) 
		return false;

	    final long now = System.currentTimeMillis();
	    synchronized (this) {
		if (setExpiration > now) {
		    logger.log(Level.INFO, "Waiting for " + (setExpiration - now) 
                        + " ms for set lease to expire");
		    wait(setExpiration - now);
                }
	    }
	    return true;
	}
	
	/**
	 * @return null if we detected no error; a descriptive string otherwise
	 */
	private String didPass() {
	    if (renewalsLeft.get() != 0) {
		setRsltIfNeeded("Did not receive enough warning events");
	    }
	    return result;
	}

	public void notify(RemoteEvent theEvent) {
	    logger.log(Level.INFO, "Recived event");

	    if (registation == null) {
		logger.log(Level.INFO, "OurListener not initialized");
		setRsltIfNeeded("TEST CODE ERROR: Event received before " +
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

	    final long now = System.currentTimeMillis();	    
	    final long predicted = setLeaseCurrentExpiration - minWarning;
	    final long diff = Math.abs(now - predicted);
	    if (diff > slop) {
		logger.log(Level.INFO, "Event recived outside window");
		setRsltIfNeeded("Warning event was received at " + now +
				"; should have been received at " + predicted + 
				" (a diff of " + diff + "ms");
		return;
	    }

	    if (isPast(now)) {
		logger.log(Level.INFO, "Event recived after lease expiration");
		setRsltIfNeeded("Warning event received after lease should " +
				"have expired (" + howPast(now) + " ms late)");

		return;
	    }

	    // Check Lease and type of event
	    try {
		final ExpirationWarningEvent ewe = 
		    (ExpirationWarningEvent)theEvent;
		final Lease eventLease = 
                    prepareNormLease(ewe.getRenewalSetLease());

		if (!setLease.equals(eventLease)) {
		    logger.log(Level.INFO, "Lease miss-match");
		    setRsltIfNeeded("Lease in warning event did not " +
				    "match set lease");
		    return;
		}

		final long expDiff = 
		    Math.abs(setLeaseCurrentExpiration - 
			     eventLease.getExpiration());

		if (expDiff > slop) {
		    logger.log(Level.INFO, "Lease in event has wrong expiration time" +
			 " has " + eventLease.getExpiration() + " should " +
			 " have " + setLeaseCurrentExpiration + "a delta of " +
			 expDiff);
		    setRsltIfNeeded("Lease in event had wrong expiration " +
				    "time (" + expDiff + "ms off)");
		    return;
		}

		// deal with renewing the lease
		if (renewalsLeft.get() > 0) {
		    logger.log(Level.INFO, "Renewing set lease");
		    eventLease.renew(setLeaseLength);
		    setLeaseCurrentExpiration = eventLease.getExpiration();

		    if (renewalsLeft.get() == 1) {
			synchronized (this) {
			    setExpiration = eventLease.getExpiration();
			}
		    }

		    renewalsLeft.dec();
		}
	    } catch (ClassCastException e) {
		setRsltIfNeeded("Event was not of type ExpirationWarningEvent");
		return;
	    } catch (Exception e) {
		e.printStackTrace();
		setRsltIfNeeded("Recived exception durring renewal " +
				e.getMessage());
		return;
	    }
	}
    }

}

