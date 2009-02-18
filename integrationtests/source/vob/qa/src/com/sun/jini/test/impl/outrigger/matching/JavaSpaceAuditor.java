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
package com.sun.jini.test.impl.outrigger.matching;

import java.util.logging.Level;

// java.rmi
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

// net.jini
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.space.JavaSpace;

// others
import java.io.PrintWriter;
import java.util.Map;
import java.util.Collection;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrust;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * Wrapper around a JavaSpace that logs writes to and takes from the
 * space and provides audit methods so the underlying space's state
 * can be checked against what the auditor thinks it should be.
 *
 * Caveats, the audit methods will not work properly if:
 * <ul>
 * <li> there is more than client for the JavaSpace
 * <li> two or more entries match each other
 * <li> the equals() method of the entries is more discriminating that
 *      the JavaSpace match criteria
 * </ul>
 *
 * @see net.jini.space.JavaSpace
 * @author John W. F. McClain
 */
public class JavaSpaceAuditor implements JavaSpace {
    private JavaSpace space; // JavaSpace we are auditing

    protected static Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");

    /*
     * Our log of what we think is currently in the JavaSpace. The
     * log is structured as a Map keyed on JavaSpace entries. The
     * values are Lists of EntryHolders which are basicly structs that
     * hold the instrumented lease that was generated when the Entry
     * was written to the underlying space, the actual entry that was
     * written and other fields describing that status of the entry.
     *
     * The log is also used for synchronization.  They major
     * synchronization problem for this class is that the log can (the
     * Map or the Lists) can not be modified while we are iterating
     * over it, so we grab the logs lock before every iteration or
     * modification.
     */
    final private Map log = new Hashtable();

    /*
     * List in which we record all of the events that have been
     * registered for.
     */
    final private List events = new LinkedList();

    // Number of takes that have thrown RemoteException
    private int badTakeCount = 0;

    // Number of writes that have been tried through this space
    private int writeAttemptCount = 0;

    // Number of writes that have succeded without an exception
    private int successfulWriteCount = 0;

    // Number of takes that have been tried through this space
    private int takesAttemptCount = 0;

    // Number of takes that have succeded without an exception
    private int successfulTakeCount = 0;

    // The configuration
    private Configuration configuration;

    /*
     * Number of times a non-null take has happend
     * and we cound not removed the result from the log
     */
    private int logRemovalFailures = 0;

    /**
     * Create a new <code>JavaSpaceAuditor</code> to tack the state of
     * the specified JavaSpace.
     * 
     * @param configuration the test configuration
     * @param space the space to track
     */
    public JavaSpaceAuditor(Configuration configuration, JavaSpace space) {
        this.space = space;
	this.configuration = configuration;
    }

    /**
     * Wrapper around <code>JavaSpace.write()</code> that logs the
     * write. The returned proxy is not prepared, since the caller
     * is responsible for that.
     *
     * @see net.jini.space.JavaSpace#write
     */
    public Lease write(Entry entry, Transaction txn, long lease)
            throws TransactionException, RemoteException {
        Lease theirLease = null;
        boolean inDoubt = false;
        RemoteException writeEx = null;

        try {
            writeAttemptCount++;
            theirLease = space.write(entry, txn, lease);
            successfulWriteCount++;
        } catch (RemoteException e) {

            /*
             * Don't know if the write succeeded or not, assume it
             * did but mark the entry as possibly not being in
             * the underlying space
             */
            writeEx = e;
            inDoubt = true;
	}

        /*
         * $$$ Need to piggy back on the transaction and remove
         * the entry from the log if the transaction fails
         */
        final EntryHolder holder = new EntryHolder(entry, theirLease, inDoubt);

        // Update the log
        final MatchEntry me = newMatchEntry(entry);
        List match = (List) log.get(me);

        /*
         * Add the entry to the log, we do this under
         * synchronization so we will not change the log while we
         * are iterating over it
         */
        synchronized (log) {
            if (match == null) {

                // We don't have a log entry that matches, create a new one
                match = new LinkedList();
                match.add(holder);
                log.put(me, match);
            } else {

                // Simply add to the existing entry, no log.put(necessay)
                match.add(holder);
            }
        }

        // Iterate over the log events and tell them to exspect this event
        try {
            synchronized (events) {
                for (Iterator i = events.iterator(); i.hasNext();) {
                    final MonitoredSpaceListener l = (MonitoredSpaceListener)
                            i.next();
                    l.expect(entry, inDoubt);
                }
            }

            /*
             * Nether of these exceptions should hapen, since the
             * write would have failed if they would
             */
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("JavaSpaceAuditor:"
                    + "Illegal entry for write");
        } catch (IOException e) {
            throw new IllegalArgumentException("JavaSpaceAuditor:"
                    + "Illegal entry for write");
        }

        // If there was a RemoteException re-throw it
        if (writeEx != null) {
            throw writeEx;
        }

        // Otherwise return the instrumented lease
        return holder.lease;
    }

    /**
     * Wrapper around <code>JavaSpace.read()</code>.
     *
     * @see net.jini.space.JavaSpace#read
     */
    public Entry read(Entry tmpl, Transaction txn, long timeout)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        return space.read(tmpl, txn, timeout);
    }

    /**
     * Wrapper around <code>JavaSpace.readIfExists()</code>.
     *
     * @see net.jini.space.JavaSpace#readIfExists
     */
    public Entry readIfExists(Entry tmpl, Transaction txn, long timeout)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        return space.readIfExists(tmpl, txn, timeout);
    }

    /**
     * Update the state of the log after a take.
     */
    private Entry takeUpdateLog(Entry entry) {
        if (entry == null) {
            return entry;
        }

        // Is the entry in our log
        final MatchEntry me = newMatchEntry(entry);
        List match = (List) log.get(me);

        // No, proably another client, not much else we can do...
        if (match == null) {
            logRemovalFailures++;
            return entry;
        }

        /*
         * Remote the entry to the log, we do this under
         * synchronization so we will not change the log while we
         * are iterating over it
         */
        synchronized (log) {

            // Remote one entry from the list
            match.remove(0);

            // Are there any duplicates?
            if (!match.isEmpty()) {

                /*
                 * Yes :-(, we don't actually know which one we
                 * got, need to mark the rest as may be gone
                 */
                for (Iterator i = match.iterator(); i.hasNext();) {
                    ((EntryHolder) i.next()).ambiguousRemoval = true;
                }
            } else {

                // No, remove this entry it from the log
                log.remove(match);
            }
        }
        return entry;
    }

    /**
     * Remove an EntryHolder from the log. Used by our lease
     * implementaion
     */
    private void removeHolder(Entry entry, InstrLease lease) {
        final MatchEntry me = newMatchEntry(entry);
        List match = (List) log.get(me);

        if (match == null) {
            return;
        }

        /*
         * Remote the entry to the log, we do this under
         * synchronization so we will not change the log while we
         * are iterating over it
         */
        synchronized (log) {

            // Iterate over log
            for (Iterator i = match.iterator(); i.hasNext();) {
                EntryHolder eh = (EntryHolder) i.next();

                if (eh.lease == lease) {

                    // Found it
                    i.remove();
                    break;
                }
            }

            // Was it the last one?
            if (match.isEmpty()) {
                log.remove(match);
            }
        }
    }

    /**
     * Wrapper around <code>JavaSpace.take()</code> that logs the
     * take.
     *
     * @see net.jini.space.JavaSpace#take
     */
    public Entry take(Entry tmpl, Transaction txn, long timeout)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        Entry rslt;

        try {
            takesAttemptCount++;
            rslt = space.take(tmpl, txn, timeout);
            successfulTakeCount++;
        } catch (RemoteException e) {
            badTakeCount++;
            throw e;
        }

        /*
         * $$$ need to piggy back on any transaction and restore the
         * badTakeCount
         */

        // Update the log
        return takeUpdateLog(rslt);
    }

    /**
     * Wrapper around <code>JavaSpace.takeIfExists()</code> that logs the
     * take.
     *
     * @see net.jini.space.JavaSpace#takeIfExists
     */
    public Entry takeIfExists(Entry tmpl, Transaction txn, long timeout)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        Entry rslt;

        try {
            takesAttemptCount++;
            rslt = space.takeIfExists(tmpl, txn, timeout);
            successfulTakeCount++;
        } catch (RemoteException e) {
            badTakeCount++;
            throw e;
        }

        /*
         * $$$ need to piggy back on any transaction and restore the
         * badTakeCount
         */

        // Update the log
        return takeUpdateLog(rslt);
    }

    /**
     * Wrapper around <code>JavaSpace.notify()</code>.  Note, the
     * auditor attempts to track if the correct number of event
     * notifications are received, this is an impossible task if the
     * notify & write calls are made from different threads and the
     * writes match the notify templates.
     *
     * Additionally the event tracking code makes no attempt to track
     * lease expiration, thus it may expect more events than it
     * legally gets if the associated lease expires or is canceled.
     *
     * @see net.jini.space.JavaSpace#notify
     */
    public EventRegistration notify(Entry tmpl, Transaction txn,
            RemoteEventListener listener, long lease, MarshalledObject handback)
            throws TransactionException, RemoteException {
        MonitoredSpaceListener passThrough;

        try {
            passThrough = new MonitoredSpaceListener(configuration,
                                                     tmpl, 
						     listener, 
						     handback);

            /*
             * Note if ether of these would happen the space.notify call
             * would throw an IllegalArgumentException
             */
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("JavaSpaceAuditor:Illegal "
                    + "template for notify");
        } catch (IOException e) {
            throw new IllegalArgumentException("JavaSpaceAuditor:Illegal "
                    + "template for notify");
        }
        synchronized (events) {
            events.add(passThrough);
        }
        // NOTE: this used to be declare final
        EventRegistration rslt = space.notify(tmpl, txn, passThrough,
                lease, handback);
	try {
	    String preparerName = "test.outriggerEventRegistrationPreparer";
	    rslt = (EventRegistration) 
		   QAConfig.getConfig().prepare(preparerName, rslt);
	} catch (TestException e) {
	    throw new RemoteException("Configuration error", e);
	}
        passThrough.complete(rslt);
        return rslt;
    }

    /**
     * Wrapper around <code>JavaSpace.snapshot()</code>.
     *
     * @see net.jini.space.JavaSpace#snapshot
     */
    public Entry snapshot(Entry e) throws RemoteException {
        return space.snapshot(e);
    }

    /**
     * Return a newly allocated List of JavaSpace <code>Entry</code>s
     * that have been written and not removed. There may be more
     * entries in the return list than in the JavaSpace if :
     * <ul>
     * <li> There is another client taking entries from the space
     * <li> There has been a take or write that threw a
     *      RemoteException
     * <li> There were duplicate entries and one or more of their
     *      leases have run out.
     * </ul>
     *
     * @param leaseThreshold Do not include entries in the list if
     *                       there are fewer than this many
     *                       milliseconds until the lease
     *                       expire. Negative values may cause some
     *                       Entries who's leases have expired to
     *                       be included.
     */
    public List getLoggedEntries(long leaseThreshold) {
        List rslt = new LinkedList();
        final long leaseCutoff = System.currentTimeMillis() - leaseThreshold;

        // Don't want to be adding/removing entries while iterating
        synchronized (log) {
            for (Iterator i = log.values().iterator(); i.hasNext();) {
                List lst = (List) i.next();

                for (Iterator j = lst.iterator(); j.hasNext();) {
                    EntryHolder holder = (EntryHolder) j.next();

                    if (holder.lease.getExpiration() < leaseCutoff) {
                        continue;
                    }
                    rslt.add(holder.entry);
                }
            }
        }
        return rslt;
    }

    /**
     * Remove all the entries in the associated JavaSpace until
     * <code>taker.query()</code> returns <code>null</code> using
     * the passed template.  At the very least the template should
     * match every entry written to the space through this auditor.
     *
     * After the <code>taker.query()</code> returns <code>null</code>
     * a <code>AuditorSummary</code> will be created and returned
     * reflecting the state of the new state of the auditor.
     * @see AuditorSummary
     */
    public AuditorSummary emptySpace(TakeQuery taker, Entry tmpl)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {

        // Do takes until we get a null return
        while (null != taker.query(this, tmpl));
        return summarize();
    }

    /**
     * Create a <code>AuditorSummary</code> object discribing the
     * current state of the auditor.
     */
    public AuditorSummary summarize() {
        final AuditorSummary rslt = new AuditorSummary();
        rslt.failedTakeCount = badTakeCount;
        rslt.writeAttemptCount = writeAttemptCount;
        rslt.successfulWriteCount = successfulWriteCount;
        rslt.takesAttemptCount = takesAttemptCount;
        rslt.successfulTakeCount = successfulTakeCount;
        rslt.logRemovalFailureCount = logRemovalFailures;

        /*
         * Walk over to log and catergorize the entries that are left
         * Don't want to be adding/removing entries while iterating
         */
        synchronized (log) {
            for (Iterator i = log.values().iterator(); i.hasNext();) {
                List lst = (List) i.next();

                for (Iterator j = lst.iterator(); j.hasNext();) {
                    EntryHolder holder = (EntryHolder) j.next();
                    rslt.totalEntries++;

                    if (holder.writeInDoubt || holder.ambiguousRemoval) {
                        if (holder.writeInDoubt) {
                            rslt.writesInDoubtCount++;
                        }

                        if (holder.ambiguousRemoval) {
                            rslt.ambiguousEntryTakeCount++;
                        }
                    } else { // Clean
                        rslt.cleanEntries++;
                        final long timeTellEnd = holder.lease.getExpiration() -
                                System.currentTimeMillis();

                        if (rslt.longestLease < timeTellEnd) {
                            rslt.longestLease = timeTellEnd;
                        }
                    }
                }
            }
        }

        /*
         * Walk over the record of notify events we have registered
         * for and collect their state
         */
        synchronized (events) {
            for (Iterator i = events.iterator(); i.hasNext();) {
                final MonitoredSpaceListener listener = (MonitoredSpaceListener)
                        i.next();
                final String errors = listener.getErrors(false);

                if (errors != null) {
                    if (rslt.eventFailures == null) {
                        rslt.eventFailures = new LinkedList();
                    }
                    rslt.eventFailures.add(errors);
                }
            }
        }
        return rslt;
    }

    /**
     * Dump the log to the designated stream
     */
    public void dumpLog() {
        synchronized (log) {
            for (Iterator i = log.values().iterator(); i.hasNext();) {
                List lst = (List) i.next();

                for (Iterator j = lst.iterator(); j.hasNext();) {
                    EntryHolder holder = (EntryHolder) j.next();
                    logger.log(Level.INFO, holder.entry.toString());
                }
            }
        }
    }

    private static MatchEntry newMatchEntry(Entry e) {
        try {
            return new MatchEntry(e);

            /*
             * The only way these exception could happen is if
             * e is not a proper entry class
             */
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("JavaSpaceAuditor:"
                    + "Illegal entry");
        } catch (IOException ex) {
            throw new IllegalArgumentException("JavaSpaceAuditor:"
                    + "Illegal entry");
        }
    }


    /**
     * Inner class that wraps the JavaSpace entries we use as keys for
     * the log hash table.  The main goal here is not to rel on the
     * entries def on equals() and hashCode().
     */
    static private class MatchEntry extends Template {

        MatchEntry(Entry e) throws IllegalAccessException, IOException {
            super(e);
        }

        public boolean equals(Object o) {
            if (o instanceof MatchEntry) {
                return matchFieldAreEqual((Template) o);
            }
            return false;
        }

        public int hashCode() {
            return sourceHashCode();
        }
    }


    /**
     * Inner class that acts as a struct to hold all the data we need
     * to assocate with a given entry.
     */
    private class EntryHolder {

        // An entry that has been writen to but not taken from the JavaSpace
        final Entry entry;

        // Lease for the entry
        final InstrLease lease;

        // true if we are not sure if this made it into the space
        boolean writeInDoubt;

        /*
         * true if this is a duplicate of another entry and one of
         * them got removed
         */
        boolean ambiguousRemoval = false;

        EntryHolder(Entry e, Lease l, boolean doubt) {
            entry = e;
	    if (space instanceof RemoteMethodControl) {
		lease = new ConstrainableInstrLease(e, l);
	    } else {
		lease = new InstrLease(e, l);
	    }
            writeInDoubt = doubt;
        }
    }

    /**
     * Implementation of Lease interface wrapped around a Lease obtained
     * from a write into a JavaSpace.  This allows the auditor to
     * catch Lease cancels and renewals and keep its records current
     */
    class InstrLease implements Lease {
        private final Entry entry;
        Lease lease; // The real lease

        /**
         * Creates a new InstrLease assocated with
         * <code>holder.entry</code> that to the client will act like
         * <code>holder.lease</code>.
         */
        InstrLease(Entry entry, Lease lease) {
            this.lease = lease;
            this.entry = entry;
        }

        /**
         * Wrapper around <code>Lease.getExpiration()</code>
         *
         * @see net.jini.core.lease.Lease#getExpiration
         */
        public long getExpiration() {
            return lease.getExpiration();
        }

        /**
         * Wrapper around <code>Lease.cancel()</code>.  May remove
         * assocated entry from log
         *
         * @see net.jini.core.lease.Lease#cancel
         */
        public void cancel() throws UnknownLeaseException, RemoteException {
            lease.cancel();
            removeHolder(entry, this);
        }

        /**
         * Wrapper around <code>Lease.renew()</code>.
         *
         * @see net.jini.core.lease.Lease#renew
         */
        public void renew(long renewExpiration)
                throws LeaseDeniedException, UnknownLeaseException,
                RemoteException {
            lease.renew(renewExpiration);
        }

        /**
         * Wrapper around <code>Lease.getSerialFormat()</code>
         *
         * @see net.jini.core.lease.Lease#getSerialFormat
         */
        public int getSerialFormat() {
            return lease.getSerialFormat();
        }

        /**
         * Wrapper around <code>Lease.setSerialFormat()</code>
         *
         * @see net.jini.core.lease.Lease#setSerialFormat
         */
        public void setSerialFormat(int format) {
            lease.setSerialFormat(format);
        }

        /**
         * Wrapper around <code>Lease.canBatch()</code>
         *
         * @see net.jini.core.lease.Lease#canBatch
         */
        public boolean canBatch(Lease lease) {
            return lease.canBatch(lease);
        }

        /**
         * Wrapper around <code>Lease.leaseMap()</code>
         *
         * @see net.jini.core.lease.Lease#leaseMap
         */
        public LeaseMap createLeaseMap(long renew) {
            LeaseMap map = lease.createLeaseMap(renew);
	    return map;
        }
    }

    class ConstrainableInstrLease extends InstrLease
					  implements RemoteMethodControl
    {
        ConstrainableInstrLease(Entry entry, Lease lease) {
	    super(entry, lease);
        }

	public MethodConstraints getConstraints() {
	    return ((RemoteMethodControl) lease).getConstraints();
	}

	public RemoteMethodControl setConstraints(MethodConstraints constraints) 
	{
	    return ((RemoteMethodControl) lease).setConstraints(constraints);
	}
    }  
}
