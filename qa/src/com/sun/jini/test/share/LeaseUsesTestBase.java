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
package com.sun.jini.test.share;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.rmi.*;
import net.jini.core.lease.Lease;


/**
 * Base class for tests which acquire some resource under a lease and
 * then use until the lease expires. Also tests to see if the lease
 * has the appropriate duration.  Have variations that cancel and/or
 * renew the lease.
 */
public abstract class LeaseUsesTestBase extends LeaseGrantTestBase {
    /**
     * Lease being used
     */
    protected Lease lease = null;

    // Time lease will expire
    private long expTime;

    // Time lease of lease duration
    private long durTime;

    // How long until the lease should be renewed
    private long renewTime;

    // What to set renewTime to, if < 0 the half of duration
    private long renewWait;

    // Time to let cancel to propgate
    private long cancelSlop;

    // Set renew and exp times
    private void setTimes() {
	final long curTime = System.currentTimeMillis();

	expTime = lease.getExpiration();
	durTime = expTime - curTime;
	
	if (renewWait < 0)
	    renewTime = expTime - durTime / 2;
	else
	    renewTime = renewWait + curTime;
    }

    protected long renewals = 0;
    protected boolean cancel;
    protected long shutdownTime = -1;
    protected long restartSleep = 10000;

    /** 
     * Method should acquire some resource under a lease and return
     * the Lease.  Note the returned value will be stored in
     * the <code>lease</code> field
     * @see LeaseUsesTestBase#lease
     */
    protected abstract Lease acquireResource() throws TestException;

    /**
     * Method should test to see if the resource acquired by
     * acquireResource() is still available, returning
     * <code>true</code> if is and <code>false</code> if it is not. 
     * If some other exception occurs the method should call fail
     */
    protected abstract boolean isAvailable() throws TestException;

    /**
     * Parse our args
     * <DL>
     *
     * <DT>-cancel<DD> Makes the test run the cancel variation of the
     * test where the lease is canceled before it expires.
     *
     * <DT>-renew <var>int</var><DD> Renew the lease <var>int</var>
     * times using the initial duration befor letting it expire or
     * canceling it.  Defaults to 0 (no renewals.) 
     *
     * <DT>-cancel_slop <var>int</var><DD> Allow for a leased resource
     * to disappear up to <var>int</var> milliseconds after cancel is
     * called.  Provided for tests that don't directly test resource
     * availability, most tests should not use this option. Defaults to
     * 0.
     * </DL> */
    protected void parse() throws Exception {
	super.parse();

	renewals = getConfig().getIntConfigVal("com.sun.jini.test.share.renew", 0);
	cancel = getConfig().getBooleanConfigVal("com.sun.jini.test.share.cancel", false);
	renewWait = getConfig().getLongConfigVal("com.sun.jini.test.share.renew_wait", -1);
	shutdownTime = getConfig().getLongConfigVal("com.sun.jini.test.share.shutdownTime", -1);
	restartSleep = getConfig().getLongConfigVal("com.sun.jini.test.share.restartSleep", 10000);
	cancelSlop = getConfig().getLongConfigVal("com.sun.jini.test.share.cancel_slop", 0);
    }

    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	parse();
        return this;
    }

    public void run() throws Exception {
	lease = acquireResource(); 
	addLease(lease, true);
	setTimes();

	if (shutdownTime > 0) {
	    final long curTime = System.currentTimeMillis();
	    shutdownTime = curTime + shutdownTime;
	}

	logger.log(Level.INFO, "Resource acquired");
	logRequest("resource", lease);

	if (!isAcceptable(lease))
	    throw new TestException("Lease had an improper duration");

	if (cancel && renewals <= 0) {
	    cancel();
	} else {
	    logger.log(Level.INFO, "Expire Test: Slop = " + slop);
	    while (true) {
		// We measure the time twice so propagation delays do
		// not cause the test to fail.  The test only fails on
		// availability if:
		//
		//    Before the check was made the lease had expired,
		//    but the resource was still available, or;
		//
		//    after the check was made the lease had not
		//    expired but the resource was unavailable.
		//
		// In both cases modulo slop.
		//
		// This eliminates the possibility of the test failing
		// because the service rightfully believes the
		// resource is available, but propagation delays cause
		// isAvailable to return after expiration, or because
		// isAvaialable is called before expiration, but
		// because of propagation delays the service thinks
		// the resource is unavailable.

		final long preTime = System.currentTimeMillis();
		final boolean stillThere = isAvailable();

		// We also use postTime as an approximation of the 
		// current time for the remainder of this iteration
		final long postTime = System.currentTimeMillis();

		// Check for late expiration against preTime
		// postTime - slop elemnates overflow problems when
		// expTime == FOREVER
		if (stillThere && (preTime - slop > expTime))
		    throw new TestException("Resource was available after lease expiration");

		// Check for early expiration against postTime
		if (!stillThere && (postTime < expTime - slop))
		    throw new TestException("Resource was not available before lease expiration");

		if (!stillThere) break;
		// No use testing once it is gone

		// Do we need to renew
		if (renewals > 0 && postTime > renewTime) {
		    try {
			lease.renew(durationRequest);
			resourceRequested();
		    } catch (Exception e) {
			throw new TestException("Could not renew lease:" + e.getMessage());
		    }
		    setTimes();
		    logRequest("renew", lease);

		    if (!isAcceptable(lease))
			throw new TestException("Renewed lease had an improper duration");
		    renewals--;
		} else if (renewals == 0 && cancel) {
		    cancel();
		    // If we are here the cancel worked, need to break
		    // so we don't see if it is there (which it won't be) 
		    // and fail the test
		    break;
		} else if (shutdownTime > 0 && shutdownTime < postTime) {
		    try {
			shutdownTime = -1; // Oneshot
			shutdown(0, restartSleep);
		    } catch (InterruptedException e) {
			// Should never happen, and if it does we don't care
		    } catch (Throwable e) {
			throw new TestException("Shutdown Failed:" + e.getMessage());
		    }
		}
	    }
	}
    }

    private void cancel() throws TestException {
	// Make sure the resource is there
	logger.log(Level.INFO, "Cancel Test: checking for availability");
	if (!isAvailable())
	    throw new TestException("Resource was never available");
	
	try {
	    logger.log(Level.INFO, "Cancel Test: canceling lease");
	    lease.cancel();
	} catch (Exception e) {
	    throw new TestException("Could not cancel lease", e);
	}

	// We could poll and loop here, but one big sleep is much
	// easer to code
	if (cancelSlop > 0) {
	    try {
		logger.log(Level.INFO, "Sleeping for " + cancelSlop + " milliseconds to " +
			  "allow cancel to propagate...");
		Thread.sleep(cancelSlop);
		logger.log(Level.INFO, "awake");
	    } catch (InterruptedException e) {
		// Should not happen
		throw new TestException("Cancel slop sleep interupted!");
	    }
	}
	
	logger.log(Level.INFO, "Cancel Test: checking to make sure resource "+
		    "is gone");
	if (isAvailable())
	    throw new TestException("Resource was available after cancel");
    }
}


