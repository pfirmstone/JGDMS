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


package com.sun.jini.test.spec.renewalservice;

import java.util.logging.Level;

// java.rmi
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;

// 
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// com.sun.jini
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.test.share.OpCountingOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * ExpireSetSleepTest asserts that a lease renewal set is nullified when
 * its lease expires but including that any leases in the set are not
 * renewed after the set expires.
 * 
 */
public class ExpireSetSleepTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    protected TestLeaseProvider leaseProvider = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    protected OpCountingOwner owner = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    protected long renewGrant = 0;

    /**
     * The default value for renewGrant
     */
    protected final long DEFAULT_RENEW_GRANT = 150 * 1000; // 2.5 minutes

    /**
     * grants for each of the three leases used in this test 
     */
    protected long grant1 = 0;
    protected long grant2 = 0;
    protected long grant3 = 0;

    /**
     * grant for the renewal set lease 
     */
    protected long setLeaseGrant = 0;

    /**
     * Sets up the testing environment.
     */
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ExpireSetSleepTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(4);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // calculate (hopefully) sane grant times for each lease
       grant1 = getConfig().getLongConfigVal(property, renewGrant);

       grant2 = getConfig().getLongConfigVal(property, renewGrant) * 2;

       grant3 = getConfig().getLongConfigVal(property, renewGrant) * 3;

       // create an owner for the lease
       owner = new OpCountingOwner(renewGrant);
       return this;
    }

    /**
     * This method asserts that a lease renewal set is nullified when
     * its lease expires but including that any leases in the set are not
     * renewed after the set expires.
     *
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 108.</P>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ExpireSetSleepTest: In run() method.");

	// get a lease renewal set w/ duration of 1/5 the max grant time
	logger.log(Level.FINE, "Creating the lease renewal set.");
	setLeaseGrant = renewGrant / 5;
	logger.log(Level.FINE, "Lease duration == " + setLeaseGrant);
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(setLeaseGrant);
	set = prepareSet(set);
	
	// get three leases with their repective durations
	logger.log(Level.FINE, "Creating the leases to be managed.");

	logger.log(Level.FINE, "Duration (lease #1) == " + grant1);
	TestLease testLease1 = 
	    leaseProvider.createNewLease(owner, rstUtil.durToExp(grant1));

	logger.log(Level.FINE, "Duration (lease #2) == " + grant2);
	TestLease testLease2 = 
	    leaseProvider.createNewLease(owner, rstUtil.durToExp(grant2));

	logger.log(Level.FINE, "Duration (lease #3) == " + grant3);
	TestLease testLease3 = 
	    leaseProvider.createNewLease(owner, rstUtil.durToExp(grant3));

	// start managing the leases for as long as we can
	logger.log(Level.FINE, "Adding managed leases to lease renewal set.");
	set.renewFor(testLease1, Lease.FOREVER);
	set.renewFor(testLease2, Lease.FOREVER);
	set.renewFor(testLease3, Lease.FOREVER);
	
	// allow the lease of the renewal set to expire
	logger.log(Level.FINE, "Getting the renewal set lease.");
	Lease setLease = prepareLease(set.getRenewalSetLease());
	boolean success = expireRenewalSetLease(setLease);
	if (success == false) {
	    String message = "Lease did not expire as expected.\n";
	    throw new TestException(message);
	}

	// assert that any attempt to use the set results in an exception
	try {
	    Lease managedLease = set.remove(testLease1);
	    String message = "Performed successful remove operation on\n";
	    message += " renewal set after its lease had expired.";
	    throw new TestException(message);
	} catch (NoSuchObjectException ex) {
	    // we passed so just keep on going ...
	}

	try {
	    Lease managedLease = leaseProvider.createNewLease(owner,
							      renewGrant);
	    set.renewFor(managedLease, Lease.FOREVER);
	    managedLease.cancel();
	    String message = "Performed successful add operation on\n";
	    message += " renewal set after its lease had expired.";
	    throw new TestException(message);
	} catch (NoSuchObjectException ex) {
	    // we passed so just keep on going ...
	}

	// Wait for the lease we put into the set to expire.
	// Note, we picked expiration times for the leases which
	// we know the renewal service won't renew before the set
	// expires so the expiration time are copies of the lease
	// object have should be the actual expiration times of
	// the lease

	String rslt = expireClientLease(testLease1, "lease #1");
	if (null != rslt) {
	    throw new TestException(rslt);
	}

	rslt = expireClientLease(testLease2, "lease #2");
	if (null != rslt) {
	    throw new TestException(rslt);
	}

	rslt = expireClientLease(testLease3, "lease #3");
	if (null != rslt) {
	    throw new TestException(rslt);
	}

	// assert that no calls have been made to renew or cancel
	long renewCalls = owner.getRenewCalls();
	long batchRenewCalls = owner.getBatchRenewCalls();
	long cancelCalls = owner.getCancelCalls();
	long batchCancelCalls = owner.getBatchCancelCalls();

	// there should be no renewal attempts
	if (renewCalls > 0 || batchRenewCalls > 0) {
	    String message = "The LRS called renew on the leases in " +
		" error.";
	    throw new TestException(message);
	}

	// cancel is strictly forbidden anytime
	if (cancelCalls > 0 || batchCancelCalls > 0) {
	    String message = "The LRS called cancel on the leases in " +
		" error.";
	    throw new TestException(message);
	}
    }

    /**
     * This method determines how the renewal set lease is canceled.
     * 
     * <P>Notes:</P>
     * This method is sort of a template method to support reuse of test code.
     * 
     * @param lease the lease to expire
     * 
     */
    protected boolean expireRenewalSetLease(Lease lease) 
                       throws UnknownLeaseException, RemoteException,
                              InterruptedException   {

	return rstUtil.waitForLeaseExpiration(lease,
					      "for set lease to expire.");
    }

    /**
     * Utiltity method that waits on the given lease expiring. Returns
     * <code>null</code> if the lease expires and an error message
     * otherwise.
     * @param lease The lease we are waiting on
     * @param name  The name of the lease we are waiting on
     */
    private String expireClientLease(Lease lease, String name) 
        throws UnknownLeaseException, RemoteException, InterruptedException
    {
	final boolean success = rstUtil.waitForLeaseExpiration(lease,
				    "Waiting for " + name + " to expire");
	if (success == false) {
	    return name + " did not expire as expected\n";
	}

	return null;
    }


} // ExpireSetSleepTest

