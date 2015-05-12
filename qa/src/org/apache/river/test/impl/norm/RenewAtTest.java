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

import net.jini.core.lease.Lease;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

import org.apache.river.test.share.TestBase;


/** 
 * Test creates a lease a number of leases and adds them with
 * diffrent desiered expiration and renew durations wait for
 * some time and makes sure that only the leases that should
 * have expired expire.
 */
public class RenewAtTest extends TestBase implements Test {
    /** Ammount of slop we are willing to tolerate around renewals */
    private long slop;

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
     * Create a new RenewAtOwner
     * @param initialDuration This inital duration the lease should have
     * @param maxExtension Max renewal to grant
     * @param desiredDuration Duration we want
     * @param desiredRenewal Renewal LRS should request
     */
    private RenewAtOwner createOwner(long initialDuration, long maxExtension,
				     long desiredDuration, long desiredRenewal)
    {
	final long now = System.currentTimeMillis();
	long desiredExpiration = desiredDuration + now;

	if (desiredDuration == Lease.FOREVER) {
	    desiredExpiration = Lease.FOREVER;
	}

	return new RenewAtOwner(initialDuration + now, maxExtension,
		       desiredExpiration, slop, desiredRenewal, this);
    }

    private void checkOwners(LeaseBackEndImpl home) throws TestException {
	final LeaseOwner owners[] = home.getOwners(); 
	for (int i=0; i<owners.length; i++) {
	    final RenewAtOwner owner = (RenewAtOwner)owners[i];
	    final String rslt = owner.didPass();
	    if (rslt != null) {
		throw new TestException(rslt);
	    }
	}
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
        set = prepareSet(set);
	addLease(prepareNormLease(set.getRenewalSetLease()), false);

	long now = System.currentTimeMillis();

	// Add a local lease to get things primed
	set.renewFor(LocalLease.getLocalLease(now + 600000, 600000, 1, 1), Lease.FOREVER);

	// Create the object that will serve as the landlord
	final RenewAtOwner owners[] = new RenewAtOwner[8];
	final LeaseBackEndImpl home = new LeaseBackEndImpl(owners.length);
        home.export();
	int j = 0;

	// Desired exp before end of test, desired renewal < max grant
	owners[j++] = createOwner(60000,  60000,  300000,        30000);

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

	for (int i=0; i<owners.length; i++) {
	    final RenewAtOwner owner = (RenewAtOwner)owners[i];

	    final Lease lease = home.newLease(owner, owner.getExpiration());
	    set.renewFor(lease, owner.getDesiredDuration(),
			 owner.getDesiredRenewal());
	}

	if (tryShutdown) 
	    shutdown(0);

	final long totalWait = 300000 + slop * 2;
	boolean middleShutdown = false;

	synchronized (this) {
	    checkOwners(home);
	    final long start = System.currentTimeMillis();
	    long elapsed = 0;

	    while (elapsed < totalWait) {
		try {
		    long waitFor = totalWait - elapsed;

		    if (!middleShutdown && tryShutdown) 
			waitFor = totalWait/2 - elapsed;

		    logger.log(Level.INFO, "Sleeping for " + waitFor);
		    wait(waitFor);

		    if (!middleShutdown && tryShutdown) {
			middleShutdown = true;
			shutdown(0);
		    }
		} catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
		    throw new TestException("wait interupted:" + e.getMessage());
		}

		checkOwners(home);       		    
		elapsed = System.currentTimeMillis() - start;
	    }

	    checkOwners(home);
	}
    }
}


