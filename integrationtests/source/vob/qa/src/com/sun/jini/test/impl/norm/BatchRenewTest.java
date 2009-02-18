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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import java.rmi.RemoteException;

import net.jini.core.lease.Lease;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

import com.sun.jini.test.share.TestBase;


/** 
 * Test creates a lease a number of leases and adds them with
 * diffrent desiered expiration and renew durations wait for
 * some time and makes sure that only the leases that should
 * have expired expire.
 */
public class BatchRenewTest extends TestBase {
    /** Ammount of slop we are willing to tolerate around renewals */
    private long slop;

    /** Should we try shuting down the service under test? */
    private boolean tryShutdown;

    /** Array of LeaseBackEndImpl */
    private LeaseBackEndImpl homes[] = new LeaseBackEndImpl[5];

    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	this.parse();
    }

    /**
     * Parse our args
     * <DL>
     *
     * <DT>-slop <var>int</var><DD> Amount of slop in milliseconds we
     * are willing to tolrate in renewal requests (compared to the ideal
     * request) and in missing expirations.
     *
     * <DT>-tryShutdown <DD>If used the test will kill the VM the service
     * is running in after adding the client lease to the set and again
     * after removing the lease from the set.
     * </DL> 
     */
    protected void parse() throws Exception {
	super.parse();
	slop = getConfig().getLongConfigVal("slop", 5000);
	tryShutdown = getConfig().getBooleanConfigVal("tryShutdown", false);
    }

    /**
     * Create a new three arg form BaseOwner 
     * @param initialDuration This inital duration the lease should have
     * @param maxExtension Max renewal to grant
     * @param desiredDuration Duration we want
     * @param desiredRenewal Renewal LRS should request
     */
    private BaseOwner createOwner(long initialDuration, long maxExtension,
				  long desiredDuration, long desiredRenewal)
    {
	final long now = System.currentTimeMillis();
	long desiredExpiration = desiredDuration + now;
	final long initalExpiration = now + initialDuration;

	if (desiredDuration == Lease.FOREVER) {
	    desiredExpiration = Lease.FOREVER;
	}

	if (desiredExpiration == Lease.FOREVER)
	    return new ForeverOwner(initalExpiration, maxExtension, 
	        desiredExpiration, slop, desiredRenewal, this, false);
	else 
	    return new ThreeArgFiniteOwner(initalExpiration, maxExtension,
		desiredExpiration, slop, desiredRenewal, this);
    }

    /**
     * Create a new two arg form BaseOwner
     * @param initialDuration This inital duration the lease should have
     * @param maxExtension Max renewal to grant
     * @param desiredDuration Duration we want
     */
    private BaseOwner createOwner(long initialDuration, long maxExtension,
				  long desiredDuration)
    {
	final long now = System.currentTimeMillis();
	long desiredExpiration = desiredDuration + now;
	final long initalExpiration = now + initialDuration;

	if (desiredDuration == Lease.FOREVER) {
	    desiredExpiration = Lease.FOREVER;
	}

	if (desiredExpiration == Lease.FOREVER)
	    return new ForeverOwner(initalExpiration, maxExtension, 
	        desiredExpiration, slop, Lease.FOREVER, this, true);
	else 
	    return new TwoArgFiniteOwner(initalExpiration, maxExtension,
                desiredExpiration, slop, this);
    }

    private LeaseBackEndImpl createBackEnd(LeaseRenewalSet set) 
	throws RemoteException
    {
	// Create the object that will serve as the landlord
	final BaseOwner owners[] = new BaseOwner[15];
	final LeaseBackEndImpl home = new LeaseBackEndImpl(owners.length);
	int j = 0;

	// Three Arg renewFors

	// Desired exp before end of test, desired renewal < max grant
	owners[j++] = createOwner(60000,  60000, 300000,        30000);

	// Desired exp FOREVER, desired renewal = ANY
	owners[j++] = createOwner(30000,  45000,  Lease.FOREVER, Lease.ANY);

	// Desired exp FOREVER, desired renewal > max grant
	owners[j++] = createOwner(45000,  45000,  Lease.FOREVER, 60000);

	// Desired exp FOREVER, desired renewal < max grant
	owners[j++] = createOwner(45000,  600000, Lease.FOREVER, 75000);

	// Desired exp < current exp
	owners[j++] = createOwner(400000, 45000,  350000,        60000);

	// Desired exp after end of test, desired renewal < max grant
	owners[j++] = createOwner(30000,  60000,  600000,        45000);

	// Desired exp after end of test, desired renewal > max grant
	owners[j++] = createOwner(30000,  30000,  650000,        45000);

	// Desired exp before end of test, desired renewal > max grant
	owners[j++] = createOwner(60000,  30000,  250000,        60000);

	// Two arg renewFors

	// Desired exp before end of test
	owners[j++] = createOwner(60000,  60000, 300000);

	// Desired exp FOREVER
	owners[j++] = createOwner(30000,  45000,  Lease.FOREVER);

	// Desired exp FOREVER
	owners[j++] = createOwner(45000,  45000,  Lease.FOREVER);

	// Desired exp FOREVER
	owners[j++] = createOwner(45000,  600000, Lease.FOREVER);

	// Desired exp < current exp
	owners[j++] = createOwner(400000, 45000,  350000);

	// Desired exp after end of test
	owners[j++] = createOwner(30000,  30000,  650000);

	// Desired exp before end of test
	owners[j++] = createOwner(60000,  30000,  250000);


	for (int i=0; i<owners.length; i++) {
	    final BaseOwner owner = owners[i];

	    final Lease lease = home.newLease(owner, owner.getExpiration());
	    if (owner.isTwoArg())
		set.renewFor(lease, owner.getDesiredDuration());
	    else
		set.renewFor(lease, owner.getDesiredDuration(),
			     owner.getDesiredRenewal());
	}

	return home;
    }


    private void checkOwners(LeaseOwner owners[]) throws TestException {
	for (int i=0; i<owners.length; i++) {
	    final BaseOwner owner = (BaseOwner)owners[i];
	    final String rslt = owner.didPass();
	    if (rslt != null) {
		throw new TestException (rslt);
	    }
	}
    }

    private void checkHomes(boolean failOnRenewAll) throws TestException {
	for (int i=0; i<homes.length; i++) {
	    final LeaseBackEndImpl home = homes[i];
	    logger.log(Level.INFO, home.getTotalRenewCalls() + " renwal calls, " +
			home.getTotalRenewAllCalls() + " renewAll calls, " +
			home.getAverageBatchSize() + " leases/batch");

	    if (failOnRenewAll && home.getTotalRenewAllCalls() < 1)
		throw new TestException ("One of the homes has gotten 0 renewAll calls");

	    if (failOnRenewAll && home.getAverageBatchSize() < 1.1) 
		throw new TestException ("One of the homes has very small batches (" +
		     home.getAverageBatchSize() + ")");

	    final LeaseOwner owners[] = home.getOwners();
	    checkOwners(owners);
	}
    }


    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	addLease(prepareNormLease(set.getRenewalSetLease()), false);

	// Add a local lease to get things primed
	long now = System.currentTimeMillis();
	set.renewFor(LocalLease.getLocalLease(now + 600000, 600000, 1, now),
		     Lease.FOREVER);

	for (int i=0; i<homes.length; i++) {
	    homes[i] = createBackEnd(set);
	}

	if (tryShutdown) 
	    shutdown(0);

	final long totalWait = 300000 + slop * 2;
	boolean middleShutdown = false;

	synchronized (this) {
	    checkHomes(false);
	    final long start = System.currentTimeMillis();
	    long elapsed = 0;

	    while (elapsed < totalWait) {
		long waitFor = totalWait - elapsed;
		
		if (!middleShutdown && tryShutdown) 
		    waitFor = totalWait/2 - elapsed;
		
		logger.log(Level.INFO, "Sleeping for " + waitFor);
		wait(waitFor);
		
		if (!middleShutdown && tryShutdown) {
		    middleShutdown = true;
		    shutdown(0);
		}
		checkHomes(true);       		    
		elapsed = System.currentTimeMillis() - start;
	    }
	    checkHomes(true);
	}
    }
}
