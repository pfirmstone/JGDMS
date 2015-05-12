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
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;

import net.jini.core.lease.Lease;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

import org.apache.river.test.share.TestBase;


/** 
 * Test creates a number leases, places it in a lease renewal set, uses
 * getLeases to ensure that they are all still there, removes one, makes 
 * sure that the other ones are still in the set and the removed one is not.
 * Finally, it lets the membership expiration elapse on one of the leases
 * still in the set and then calls getLeases again to ensure that all the other
 * leases are still present.
 */
public class GetLeasesTest extends TestBase implements Test {

    /** Membership duration for short lease */    
    private long membershipDuration;

    /** Should we try shuting down the service under test? */
    private boolean tryShutdown;

    /** The set of leases we think are in the renewal set */
    final private Set leases = new HashSet();

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	this.parse();
        return this;
    }

    /**
     * org.apache.river.test.impl.norm.membership_duration (long):
     *     number of milliseconds the short lease should be in the set
     * <p>
     * org.apache.river.test.impl.norm.tryShutdown (boolean):
     *     if used the test will kill the VM the service
     *     is running in after adding the client lease to the set and again
     *     after removing the lease from the set.
     */
    protected void parse() throws Exception {
	super.parse();
	membershipDuration = getConfig().getLongConfigVal(
            "org.apache.river.test.impl.norm.membership_duration", 60 * 1000);
	tryShutdown = getConfig().getBooleanConfigVal(
            "org.apache.river.test.impl.norm.tryShutdown", false);
    }

    private void check(List fromRenewalSet,String msg) throws TestException {
	if (!fromRenewalSet.containsAll(leases) || 
	    !leases.containsAll(fromRenewalSet))
	{
	    logger.log(Level.INFO, "Renewal set does not have what it should have");
	    logger.log(Level.INFO, "What it should have");
	    for (final Iterator i = leases.iterator(); i.hasNext(); ) {
		logger.log(Level.INFO, "     " + i.next());
	    }

	    logger.log(Level.INFO, "What it does have");
	    for (final Iterator i = fromRenewalSet.iterator(); i.hasNext(); ) {
		logger.log(Level.INFO, "     " + i.next());
	    }

	    throw new TestException(msg);
	}
    }

    public void run() throws Exception {
	specifyServices(new Class[]{LeaseRenewalService.class});
	LeaseRenewalService lrs = (LeaseRenewalService)services[0];

	// Create the lease
	// The leases that will remain in the set the whole time
	final long now = System.currentTimeMillis();
	leases.add(LocalLease.getLocalLease(
            (4 * membershipDuration) + now, 60 * 1000, 0, 0));
	leases.add(LocalLease.getLocalLease(
            (4 * membershipDuration) + now, 60 * 1000, 1, 1));

	// The lease we will remove
	final Lease toRemove = LocalLease.getLocalLease(
            (4 * membershipDuration) + now, 60 * 1000, 2, 2);
	leases.add(toRemove);

	// The lease who's membership expiration we will let expire
	final Lease toExpire = LocalLease.getLocalLease(
            (4 * membershipDuration) + now, 60 * 1000, 3, 3);
	leases.add(toExpire);

	LeaseRenewalSet set = 
	    lrs.createLeaseRenewalSet(4 * membershipDuration);
	set = prepareSet(set);
	addLease(prepareNormLease(set.getRenewalSetLease()), false);

	logger.log(Level.INFO, "Adding leases to renewal set");

	for (final Iterator i=leases.iterator(); i.hasNext(); ) {
	    final Lease toAdd = (Lease)i.next();
	    if (toAdd.equals(toExpire)) {
		set.renewFor(toAdd, membershipDuration);
	    } else {
		set.renewFor(toAdd, Lease.FOREVER);
	    }
	}

	final long start = System.currentTimeMillis();

	if (tryShutdown) {
            logger.log(Level.INFO, "Shuting down at: " + start + " milliseconds");
	    shutdown(0);
        }

	logger.log(Level.INFO, "Calling getLeases(), looking for all 4 leases");
	check(Arrays.asList(set.getLeases()),
	      "Set is missing or holding leases it should not");

	logger.log(Level.INFO, "Removing 1 lease from the set");
	final Lease removed = set.remove(toRemove);
	if (removed == null || !removed.equals(toRemove)) {
	    throw new TestException("Could not remove lease");
        }
	leases.remove(toRemove);

	if (tryShutdown) {
            logger.log(Level.INFO, "Shuting down at: " 
                + System.currentTimeMillis() + " milliseconds");
	    shutdown(0);
        }

	logger.log(Level.INFO, "Calling getLeases(), looking for 3 leases");
	check(Arrays.asList(set.getLeases()),"Set is missing or "
            + "holding leases it should not after removing one");

	// Wait for expiration to be reached on toExpire
	final long sleepTime =
	    (membershipDuration - (System.currentTimeMillis() - start)) 
            + 10000;
	if (sleepTime > 0) {
	    logger.log(Level.INFO, "Sleeping for: " + sleepTime + " milliseconds");
	    Thread.sleep(sleepTime);
	}
	leases.remove(toExpire);

	if (tryShutdown) {
            logger.log(Level.INFO, "Shuting down at: " 
                + System.currentTimeMillis() + " milliseconds");
	    shutdown(0);
        }

	logger.log(Level.INFO, "Calling getLeases(), looking for 2 leases");
	check(Arrays.asList(set.getLeases()),
	    "Set is missing or holding leases it should not after expire");
    }

}

