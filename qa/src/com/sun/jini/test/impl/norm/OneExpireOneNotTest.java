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

import java.io.PrintWriter;

import java.rmi.RemoteException;

import net.jini.core.lease.Lease;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.share.LeaseBackEndImpl;
import com.sun.jini.test.share.LeaseOwner;
import com.sun.jini.test.share.ForeverOwner;
import com.sun.jini.test.share.TrackingOwner;

/**
 * Try to make sure that client leases associated with a set that has
 * expired do not affect client leases in a set that has not expired.
 * Create two sets and a number of leases that can be batched together
 * and expire at the same time as the sets.  Place half the leases in
 * one set and half in the other.  Let one set expire.  Make sure that
 * leases in the expired set don't get renewed after the set expires,
 * and that the other leases don't expire.
 */
public class OneExpireOneNotTest extends QATestEnvironment implements Test {
    /** The service under test */
    private LeaseRenewalService lrs;

    /** the name of service for which these test are written */
    static protected final String SERVICE_NAME =
	"net.jini.lease.LeaseRenewalService";

    /** How long a lease to get on the expiring set */
    private long setDuration = 0;

    /** The time allowed for network transfers to take place  */
    private long latencySlop = 0;

    /** The maximum time granted for a lease by a renew operation.  */
    private long renewGrant = 0;

    /** Numbe of client leases to create */
    private int leaseCount = 0;

    /**
     * Sets up the testing environment.
     */
    public Test construct(QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // output the name of this test
       logger.log(Level.FINE, "Test Name = " + this.getClass().getName());
	
       // Announce where we are in the test
       logger.log(Level.FINE, "OneExpireOneNotTest:In setup() method.");

       // capture an instance of the Properties file.
       QAConfig config = (QAConfig)getConfig();

       String property = "com.sun.jini.test.impl.norm.setDuration";
       setDuration = getConfig().getLongConfigVal(property, 120000);

       property = "com.sun.jini.test.impl.norm.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, 30000);

       property = "com.sun.jini.test.impl.norm.leaseCount";
       leaseCount = getConfig().getIntConfigVal(property, 40);
       // Make leaseCount event so filling the sets is easer
       if (leaseCount % 2 != 0) 
	   leaseCount++;

       // capture the max time allowed for network transfer
       property = "com.sun.jini.test.impl.norm.latencySlop";
       latencySlop = getConfig().getLongConfigVal(property, 2000);

       // Get an LRS
       logger.log(Level.FINE, "Getting a " + SERVICE_NAME);
       lrs = (LeaseRenewalService)getManager().startService(SERVICE_NAME);
       return this;
    }

    public void run() throws Exception {
	// Announce where we are in the test
	logger.log(Level.FINE, "OneExpireOneNotTest: In run() method.");

	long setCreation = System.currentTimeMillis();
	LeaseRenewalSet setExpire = lrs.createLeaseRenewalSet(setDuration);
	setExpire = prepareSet(setExpire);
	logger.log(Level.FINE, "OneExpireOneNotTest: Expire set created");
	LeaseRenewalSet setKeep = lrs.createLeaseRenewalSet(Lease.FOREVER);
	setKeep = prepareSet(setKeep);
	Lease expsLease = setExpire.getRenewalSetLease();
        expsLease = (Lease) getConfig().prepare(
            "test.normLeasePreparer", expsLease);

	final LeaseBackEndImpl backend = new LeaseBackEndImpl(leaseCount);
        backend.export();

	for (int i=0; i<leaseCount; i+=2) {
	    long initExpiration = System.currentTimeMillis() + renewGrant;
	    LeaseOwner o = new ForeverOwner(initExpiration, renewGrant,
	        latencySlop, Lease.FOREVER, this, true, getConfig());
	    Lease l = backend.newLease(o, initExpiration);
	    setKeep.renewFor(l, Lease.FOREVER);

	    initExpiration = System.currentTimeMillis() + renewGrant;
	    o = new ExpiryOwner(initExpiration, renewGrant,
	        latencySlop, Lease.FOREVER, this, true, getConfig(), expsLease);
	    l = backend.newLease(o, initExpiration);
	    long delta = System.currentTimeMillis() - setCreation;
	    System.out.println("Delta => " + delta);
	    setExpire.renewFor(l, Lease.FOREVER);
	}

	// Sleep until the first lease expires + its initial length, or we 
	// detect a failure
	synchronized (this) {
	    while (true) {
		LeaseOwner[] owners = backend.getOwners();

		// See if there have been any failures
		for (int i=0; i<owners.length; i++) {
		    final TrackingOwner owner = (TrackingOwner)owners[i];
		    final String rslt = owner.didPass();
		    if (rslt != null) {
			throw new TestException( rslt);
		    }
		}

		final long now = System.currentTimeMillis();

		// If we have waited long enough break
		if (now - expsLease.getExpiration() >= setDuration)
		    break;

		try {
		    wait(expsLease.getExpiration() - now + setDuration);
		} catch (InterruptedException e) {
		    throw new TestException(
			"Unexpected InterruptedException.");
		}
	    }
	}
    }

    /**
     * Subclass of forever owner that makes sure that the set
     * containing the owned lease has not expired
     */
    private class ExpiryOwner extends ForeverOwner {

	/** Lease of the set containing the lease we own */
	final private Lease renewalSetLease;

	/** 
	 * Simple constructor 
	 * @param initialExpiration Initial expiration time for lease.
	 * @param maxExtension Maximum time this owner will be willing to extend 
	 *                     the lease
	 * @param slop Allowable variance from desired expiration when making a
	 *             renewal request.
	 * @param desiredRenewal
	 *             Expect value of the renewDuration parameter
	 * @param notifyOnFailure
	 *             Object to notify if there is a failure
	 * @param isTwoArg Should the assocated lease be registered
	 *             with the one or two arg form
	 * @param util QA harnss utility object
	 * @param renewalSetLease The lease of the renewal set the
	 *                        lease we are an owner of will be placed in
	 */
	public ExpiryOwner(long initialExpiration, long maxExtension,
	    long slop, long desiredRenewal, Object notifyOnFailure,
	    boolean isTwoArg, QAConfig config, Lease renewalSetLease) 
	{
	    super(initialExpiration, maxExtension, 
		  slop, desiredRenewal, notifyOnFailure, isTwoArg, config);
	    this.renewalSetLease = renewalSetLease;
	}

	// Inherit java doc from super type
	protected boolean isValidExtension(long extension) {
	    // Check to make sure the set has not expired
            boolean t = false;
            synchronized (this){
                t = now - slop > renewalSetLease.getExpiration();
            }
            if (t) {
                // The set has expired, this renewal should not
                // be happending
                setRsltIfNeeded("Expire Owner:LRS asked for a renewal " +
                                "after renewal set expiration!");
                return false;
            }
	    return super.isValidExtension(extension);
	}
	
	/**
	 * Override to not flag a failure if the lease we own has
	 * expired and the the set's lease has as well.
	 */
	public synchronized String didPass() {
	    final String rslt = getRslt();
	    if (rslt != null) 
		return rslt;
	    
	    final long now = System.currentTimeMillis();
	    
	    if (renewalSetLease.getExpiration() < now) {
		// We don't care it our lease has expired or not
		return null;
	    }

	    return super.didPass();
	}
    }

    protected LeaseRenewalSet prepareSet(LeaseRenewalSet set) 
        throws TestException
    {
	Object s = getConfig().prepare("test.normRenewalSetPreparer", set);
	return (LeaseRenewalSet) s;
    }
}
