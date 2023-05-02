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
package org.apache.river.test.impl.outrigger.leasing;

import java.util.logging.Level;

// java classes
import java.rmi.*;

// jini classes
import net.jini.core.lease.Lease;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// Shared classes
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.TestBase;
import java.util.concurrent.atomic.AtomicLong;


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
    private Lease lease = null;

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

        if (renewWait < 0) {
            renewTime = expTime - durTime / 2;
        } else {
            renewTime = renewWait + curTime;
        }
    }
    private long renewals = 0;
    private boolean cancel;
    private long shutdownTime = -1;
    private long restartSleep = 10000;

    /**
     * Method should acquire some resource under a lease and return
     * the Lease.  Note the returned value will be stored in
     * the <code>lease</code> field
     * @return Lease
     * @throws TestException 
     * @see LeaseUsesTestBase#lease
     */
    protected abstract Lease acquireResource() throws TestException;

    /**
     * Method should test to see if the resource acquired by
     * acquireResource() is still available, returning
     * <code>true</code> if is and <code>false</code> if it is not.
     * If some other exception occurs the method should call fail
     * @return true if still available
     * @throws TestException  
     */
    protected abstract boolean isAvailable() throws TestException;

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

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
     * </DL>
     * @throws Exception 
     */
    protected void parse() throws Exception {
        super.parse();
        synchronized (this){
            // Get values from property file for this test.
            QAConfig config = getConfig();
            renewals = config.getIntConfigVal("org.apache.river.test.share.renew", 0);
            cancel = config.getBooleanConfigVal("org.apache.river.test.share.cancel", false);
            renewWait = config.getLongConfigVal("org.apache.river.test.share.renew_wait", -1);
            shutdownTime = config.getLongConfigVal("org.apache.river.test.share.shutdownTime", -1);
            restartSleep = config.getLongConfigVal("org.apache.river.test.share.restartSleep", 10000);
            cancelSlop = config.getLongConfigVal("org.apache.river.test.share.cancel_slop", 0);

            // Log out test options.
            logger.log(Level.INFO, "renewals = {0}", renewals);
            logger.log(Level.INFO, "cancel = {0}", cancel);
            logger.log(Level.INFO, "renewWait = {0}", renewWait);
            logger.log(Level.INFO, "shutdownTime = {0}", shutdownTime);
            logger.log(Level.INFO, "restartSleep = {0}", restartSleep);
            logger.log(Level.INFO, "cancelSlop = {0}", cancelSlop);
        }
    }

    public synchronized void run() throws Exception {
        lease = acquireResource();
        addLease(lease, true);
        setTimes();

        if (shutdownTime > 0) {
            final long curTime = System.currentTimeMillis();
            shutdownTime = curTime + shutdownTime;
        }
        logger.log(Level.INFO, "Resource acquired");
        logRequest("resource", lease);

        if (!isAcceptable(lease)) {
            throw new TestException("Lease had an improper duration");
        }

        if (cancel && renewals <= 0) {
	    cancel();
        } else {
            logger.log(Level.INFO, "Expire Test: Slop = {0}", slop);

            while (true) {

                /*
                 * We measure the time twice so propagation delays do
                 * not cause the test to fail.  The test only fails on
                 * availability if:
                 *
                 *    Before the check was made the lease had expired,
                 *    but the resource was still available, or;
                 *
                 *    after the check was made the lease had not
                 *    expired but the resource was unavailable.
                 *
                 * In both cases modulo slop.
                 *
                 * This eliminates the possibility of the test failing
                 * because the service rightfully believes the
                 * resource is available, but propagation delays cause
                 * isAvailable to return after expiration, or because
                 * isAvaialable is called before expiration, but
                 * because of propagation delays the service thinks
                 * the resource is unavailable.
                 */
                final long preTime = System.currentTimeMillis();
                final boolean stillThere;
                final long postTime;
                stillThere = isAvailable();

                /*
                 * We also use postTime as an approximation of the
                 * current time for the remainder of this iteration
                 */
                postTime = System.currentTimeMillis();

                /*
                 * Check for late expiration against preTime
                 * postTime - slop eliminates overflow problems when
                 * expTime == FOREVER
                 */
                if (stillThere && (preTime - slop > expTime)) {
                    // Check for early expiration against postTime
                    logger.log(Level.WARNING, "expTime: {0}, (preTime - slop): {1}, still there: {2}",
                            new Object[]{expTime, preTime - slop, stillThere});
                    throw new TestException(
                            "Resource was available after lease expiration");
                }


                if (!stillThere && (postTime < expTime - slop)) {
                    // Check for early expiration against postTime
                    logger.log(Level.WARNING, "postTime: {0}, (expTime - slop): {1}, still there: {2}",
                            new Object[]{postTime, expTime - slop, stillThere});
                    throw new TestException(
                            "Resource was not available before lease expiration");
                }

                if (!stillThere) {

                    // No use testing once it is gone
                    break;
                }
                // Do we need to renew
                if (renewals > 0 && postTime > renewTime) {
		    lease.renew(durationRequest);
		    resourceRequested();
                    setTimes();
                    logRequest("renew", lease);

                    if (!isAcceptable(lease)) {
                        throw new TestException(
                                "Renewed lease had an improper duration");
                    }
                    renewals--;
                } else if (renewals == 0 && cancel) {
		    cancel();

                    /*
                     * If we are here the cancel worked, need to break
                     * so we don't see if it is there (which it won't be)
                     * and fail the test
                     */
                    break;
                } else if (shutdownTime > 0 && shutdownTime < postTime) {
                    try {
                        shutdownTime = -1; // Oneshot
                        shutdown(0, restartSleep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // Should never happen, and if it does we don't care
                    }
                }
            }
        }
    }

    private void cancel() throws Exception {

        // Make sure the resource is there
        logger.log(Level.INFO, "Cancel Test: checking for availability");

        if (!isAvailable()) {
            throw new TestException("Resource was never available");
        }
        synchronized (this){
            logger.log(Level.INFO, "Cancel Test: canceling lease");
            lease.cancel();

            /*
             * We could poll and loop here, but one big sleep is much
             * easer to code
             */
            if (cancelSlop > 0) {
                logger.log(Level.INFO, 
                           "Sleeping for {0}" + " milliseconds to "
                           + "allow cancel to propagate... time: {1}", new Object[] {cancelSlop, System.currentTimeMillis()});
                Thread.sleep(cancelSlop);
                logger.log(Level.INFO, "awake: {0}", System.currentTimeMillis());
            }
        }
        logger.log(Level.INFO, 
		   "Cancel Test: checking to make sure resource " + "is gone");

        if (isAvailable()) {
            throw new TestException("Resource was available after cancel");
        }
    }
}
