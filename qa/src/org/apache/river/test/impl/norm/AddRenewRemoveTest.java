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
 * Test creates a lease, places it in a lease renewal set, waits for 
 * to be renewed a designated number of times and then removes it.
 */
public class AddRenewRemoveTest extends TestBase implements Test {
    /** Ammount of slop we are willing to tolerate around renewals */
    private long slop;

    /** Membership duration for set */    
    private long membershipDuration;

    /** Max renwal length to grant */
    private long renewGrant;

    /** Number of renewals to wait for */
    private int renewCount;

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
     * <DT>-renew_count <var>int</var><DD> Number of times the lease
     * should be removed before removing the lease from the set
     *
     * <DT>-membership_duration <var>int</var><DD> Number of milliseconds
     * the lease should be in the set.
     * 
     * <DT>-renew_grant <var>int</var><DD> Length of max renewal
     * grants in milliseconds
     *
     * <DT>-tryShutdown <DD>If used the test will kill the VM the service
     * is running in after adding the client lease to the set and again
     * after removing the lease from the set.
     * </DL> 
     */
    protected void parse() throws Exception {
	super.parse();
	slop = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.slop", 8000);
	membershipDuration = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.membership_duration",
						  60 * 60 * 1000);
	renewGrant = getConfig().getLongConfigVal("org.apache.river.test.impl.norm.renew_grant", 60 * 1000);
	renewCount = getConfig().getIntConfigVal("org.apache.river.test.impl.norm.renew_count", 2);
	tryShutdown = getConfig().getBooleanConfigVal("org.apache.river.test.impl.norm.tryShutdown", false);
	System.out.println("renewCount = " + renewCount);
	System.out.println("renewGrant = " + renewGrant);
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	LeaseRenewalService lrs = (LeaseRenewalService)services[0];
	LeaseRenewalSet set = 
	    lrs.createLeaseRenewalSet((renewCount * renewGrant + slop) * 2);
	set = prepareSet(set);
	addLease(prepareNormLease(set.getRenewalSetLease()), false);

	final long now = System.currentTimeMillis();

	final LeaseBackEndImpl home = new LeaseBackEndImpl(1);
        home.export();
	final long initExp = now + renewGrant;
	final RenewingOwner owner = new RenewingOwner(initExp, renewCount, 
						    renewGrant,
						    now + membershipDuration,
						    slop);
	final Lease lease = home.newLease(owner, initExp);
	logger.log(Level.FINER, "calling renewFor with duration " + (now + membershipDuration) + " at " + System.currentTimeMillis());
	set.renewFor(lease, membershipDuration);
	logger.log(Level.FINER, "call to renewFor complete at " + System.currentTimeMillis());

	if (tryShutdown) 
	    shutdown(0);

	final String rslt = 
	    owner.waitForRenewals(renewCount * renewGrant + slop);

	if (rslt != null)
	    throw new TestException(rslt);
	// Try to remove the lease
	final Lease rl = set.remove(lease);
	final long removeTime = System.currentTimeMillis();

	if (rl == null)
	    throw new TestException("Could not remove lease");
	else if (!rl.equals(lease))
	    throw new TestException("Removed lease (" + rl + ") was not equals() to lease we " +
				 "added (" + lease + ")");

	final Lease rl2 = set.remove(lease);

	if (rl2 != null)
	    throw new TestException("Lease still in set after removal");

	if (tryShutdown) {
	    shutdown(0);

	    // Try to remove the lease again
	    final Lease rl3 = set.remove(lease);

	    if (rl3 != null)
		throw new TestException("Lease still in set after removal and shutdown");
	}

	// Make sure that lease does not get renewed after it is removed
	// Sleep until lease would expire
	final long sleepTime =
	    (owner.getExpiration() - System.currentTimeMillis()) * 2;

	if (sleepTime > 0) 
	    Thread.sleep(sleepTime);

	if (owner.getLastRenewTime() > removeTime + slop)
	    throw new TestException("Lease was renewed after removal from set");
    }
}
