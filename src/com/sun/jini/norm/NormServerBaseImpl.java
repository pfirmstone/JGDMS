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
package com.sun.jini.norm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;

import com.sun.jini.config.Config;
import com.sun.jini.constants.ThrowableConstants;
import com.sun.jini.constants.VersionConstants;
import com.sun.jini.landlord.FixedLeasePeriodPolicy;
import com.sun.jini.landlord.Landlord.RenewResults;
import com.sun.jini.landlord.LandlordUtil;
import com.sun.jini.landlord.LeaseFactory;
import com.sun.jini.landlord.LeasePeriodPolicy;
import com.sun.jini.landlord.LocalLandlord;
import com.sun.jini.logging.Levels;
import com.sun.jini.lookup.entry.BasicServiceType;
import com.sun.jini.norm.event.EventType;
import com.sun.jini.norm.event.EventTypeGenerator;
import com.sun.jini.norm.event.SendMonitor;
import com.sun.jini.norm.lookup.JoinState;
import com.sun.jini.proxy.ThrowThis;
import com.sun.jini.start.LifeCycle;
import com.sun.jini.reliableLog.LogException;
import com.sun.jini.reliableLog.LogHandler;
import com.sun.jini.thread.InterruptedStatusThread;

/**
 * Base class for implementations of NormServer.  Provides a complete
 * non-activatable (but still logging) implementation.
 *
 * @author Sun Microsystems, Inc.
 */
abstract class NormServerBaseImpl
    implements NormServer, LocalLandlord, ServerProxyTrust, ProxyAccessor
{
    /** Current version of log format */
    private static final int CURRENT_LOG_VERSION = 2;

    /** Logger and configuration component name for Norm */
    static final String NORM = "com.sun.jini.norm";

    /** Logger for logging information about this instance */
    static final Logger logger = Logger.getLogger(NORM);

    /** Whether this server is persistent. */
    final boolean persistent;

    /** The login context, for logging out */
    LoginContext loginContext;

    /** The location of our persistent storage, or null if not persistent. */
    String persistenceDirectory;

    /** Proxy preparer for leases supplied through the API */
    private ProxyPreparer leasePreparer;

    /**
     * Proxy preparer for leases recovered from persistent storage, or null if
     * not persistent.
     */
    private ProxyPreparer recoveredLeasePreparer;

    /** Proxy preparer for listeners supplied through the API */
    private ProxyPreparer listenerPreparer;

    /**
     * Proxy preparer for listeners recovered from persistent storage, or null
     * if not persistent.
     */
    private ProxyPreparer recoveredListenerPreparer;

    /**
     * Proxy preparer for lookup locators supplied through the API, and not
     * including initial lookup locators.
     */
    private ProxyPreparer locatorPreparer;

    /**
     * Proxy preparer for lookup locators recovered from persistent storage, or
     * null if not persistent.
     */
    private ProxyPreparer recoveredLocatorPreparer;

    /** The exporter for exporting and unexporting */
    Exporter exporter;

    /** Object to notify when this service is destroyed, or null */
    private LifeCycle lifeCycle;

    /** The unique ID for this server. */
    private Uuid serverUuid;

    /** Our JoinManager */
    private JoinState joinState;

    /** Map of Uuids to LeaseSets */
    private Map setTable = Collections.synchronizedMap(new HashMap());

    /** Lease renewal manager that actually renews the leases */
    private LeaseRenewalManager lrm;

    /** Object that expires sets and generates expiration warning events */
    private LeaseExpirationMgr expMgr;

    /** Factory for creating leases */
    private LeaseFactory leaseFactory;

    /** Policy we use for granting and renewing renewal set leases */
    private LeasePeriodPolicy setLeasePolicy;

    /**
     * Whether to isolate leases in their own renewal sets as opposed to
     * batching leases across sets.
     */
    private boolean isolateSets;

    /** Our persistant store */
    private PersistentStore store;

    /** Factory we use to create ClientLeaseWrapper IDs */
    private UIDGenerator idGen = new UIDGenerator() ;

    /** List of leases that have been renewed but not persisted */
    private List renewedList = new LinkedList();

    /**
     * Thread that pulls wrapped client leases off the renewedList and logs
     * them to disk
     */
    private RenewLogThread renewLogger;

    /** Object used to generate new event types */
    private EventTypeGenerator generator;

    /**
     * Object that transfers events from the renewal manager to
     * us so we can remove dead leases and send events
     */
    private LRMEventListener lrmEventListener;

    /** Log file must contain this many records before snapshot allowed */
    private int logToSnapshotThresh;

    /** Weight factor applied to snapshotSize when deciding to take snapshot */
    private float snapshotWt;

    /** Inner server proxy */
    NormServer serverProxy = null;

    /** Outer service proxy */
    LeaseRenewalService normProxy = null;

    /** Admin proxy */
    private AdminProxy adminProxy;

    /** Thread that performs snapshots when signaled */
    private SnapshotThread snapshotter;

    /** Lock protecting startup and shutdown */
    private final ReadyState ready = new ReadyState();

    /** Keep track of the number of leases. */
    private final CountLeases countLeases = new CountLeases();

    ////////////////////////////////
    // Methods defined in NormServer

    // Inherit java doc from super type
    public void renewFor(Uuid id, Lease leaseToRenew,
			 long membershipDuration, long renewDuration) 
	throws RemoteException, ThrowThis
    {
	ready.check();

	// Lookup the set
	final LeaseSet set = getSet(id);

	if (leaseToRenew == null) {
	    throw new NullPointerException("LeaseRenewalSet.renewFor:Must " +
					   "pass a non-null lease");
	}

	if ((membershipDuration != Lease.FOREVER) &&
	    (renewDuration == Lease.ANY))
	{
	    throw new IllegalArgumentException(
	        "LeaseRenewalSet.renewFor:renewDuration can only be " +
		"Lease.ANY if membershipDuration is Lease.FOREVER");
	}

	if (!(renewDuration == Lease.ANY || renewDuration == Lease.FOREVER ||
	      renewDuration > 0))
	{
	    throw new IllegalArgumentException(
	        "LeaseRenewalSet.renewFor:renewDuration can only be " +
		"Lease.ANY, Lease.FOREVER, or positive");
	}

	leaseToRenew = (Lease) leasePreparer.prepareProxy(leaseToRenew);

	// Ensure that the lease is not a current lease granted by this server
	if (leaseToRenew instanceof ReferentUuid) {
	    Uuid cookie = ((ReferentUuid) leaseToRenew).getReferentUuid();
	    LeaseSet setForLease = (LeaseSet) setTable.get(cookie);
	    if (setForLease != null) {
		synchronized (setForLease) {
		    if (isCurrent(setForLease)) {
			throw new IllegalArgumentException(
			    "Cannot add leases granted by a " +
			    "LeaseRenewalService to a set created by " +
			    "that service");
		    }
		}
	    }
	}
	
	// If we are told to dump the codebase of the lease we are adding
	if (logger.isLoggable(Level.FINE)) {
	    final Class lc = leaseToRenew.getClass();
	    logger.log(Level.FINE,
		       "Adding lease of class {0} with annotation {1}",
		       new Object[] {
			   leaseToRenew.getClass(),
			   RMIClassLoader.getClassAnnotation(lc) });
	}

	// Add the lease to the set
	add(set, leaseToRenew, membershipDuration, renewDuration); 
    }

    /**
     * Prevents access to the service before it is ready or after it starts to
     * shutdown.  Each public entrypoint to the service should call check or
     * shutdown, and initialization should call ready when the service is ready
     * to use. 
     */
    private static final class ReadyState {
	private static final int INITIALIZE = 0;
	private static final int READY = 1;
	private static final int SHUTDOWN = 2;
	private int state = INITIALIZE;

	/**
	 * Checks if the service is ready to use, waiting if it is
	 * initializing, and throwing IllegalStateException if it is shutting
	 * down.
	 */
	synchronized void check() {
	    while (true) {
		switch (state) {
		  case INITIALIZE:
		      try {
			  wait();
		      } catch (InterruptedException e) {
		      }
		      break;
		case READY:
		    return;
		default:
		    throw new IllegalStateException(
			"Norm service is unavailable");
		}
	    }
	}

	/**
	 * Marks the service ready for use, throwing IllegalStateException if
	 * it is shutting down.
	 */
	synchronized void ready() {
	    switch (state) {
	    case INITIALIZE:
		state = READY;
		notifyAll();
		break;
	    case READY:
		break;
	    default:
		throw new IllegalStateException("Norm service is unavailable");
	    }
	}

	/**
	 * Marks the service as shutting down, waiting if it is initializing,
	 * and throwing IllegalStateException if it is already shutting down.
	 */
	synchronized void shutdown() {
	    check();
	    state = SHUTDOWN;
	    notifyAll();
	}
    }

    /** Keeps track of the number of leases. */
    private static class CountLeases {
	private int count;

	private synchronized void updateCount(int change) {
	    count += change;
	    assert count >= 0;
	}

	private synchronized int getCount() {
	    return count;
	}
    }

    /** Update the number of leases being managed by this server. */
    void updateLeaseCount(int change) {
	countLeases.updateCount(change);
    }

    /**
     * Add the lease to the set.
     *
     * @param set the LeaseSet to add the leaseToRenew to
     * @param leaseToRenew the lease the client wants managed
     * @param membershipDuration the length of time the client
     *        wants the lease managed for
     * @param renewDuration the length of time the client want the 
     *        lease renewed for each time it is renewed
     * @throws ThrowThis if the set no longer exists
     */
    private void add(LeaseSet set, Lease leaseToRenew,
		     long membershipDuration, long renewDuration) 
	throws ThrowThis
    {
	try {
	    store.acquireMutatorLock();	    
	    synchronized (set) {
		ensureCurrent(set);

		// Get a wrapper for the lease
		final long now = System.currentTimeMillis();
		ClientLeaseWrapper clw =
		    set.getClientLeaseWrapper(leaseToRenew);
		if (clw == null) {
		    // We don't know about this lease, create a new wrapper
		    try {
			clw = new ClientLeaseWrapper(
			    leaseToRenew, idGen.newID(), renewedList, set,
			    membershipDuration, renewDuration, now);
		    } catch (IOException e) {
			throw new IllegalArgumentException(
			    "NormServerBaseImpl.renewFor:Handed lease " +
			    "that can't be marshalled");
		    }
		} else {
		    // We know about this lease -- update its stats
		    clw.update(membershipDuration, renewDuration, now);
		}

		set.update(clw);
		lrm.renewUntil(clw, clw.getMembershipExpiration(), 
			       clw.getRenewDuration(), lrmEventListener);

		// $$$ What if this lease was just dropped by the LRM
		// (say because there was some problem renewing the
		// lease)?  Are we going to lose the lease because
		// we will shortly process a renewalFailureEvent?
		// Is it a problem if we do? Presumably re-adding it
		// is not going to fix the underlying problem...
	    }
	} finally {
	    store.releaseMutatorLock();
	}    
    }


    // Inherit java doc from super type
    public Lease remove(Uuid id, Lease leaseToRemove)
	throws RemoteException, ThrowThis
    {
	ready.check();
	final LeaseSet set = getSet(id);

	if (leaseToRemove == null) {
	    throw new NullPointerException("LeaseRenewalSet.remove:Must " +
	        "pass a non-null lease");
	}

	leaseToRemove = (Lease) leasePreparer.prepareProxy(leaseToRemove);
	logger.log(Level.FINE, "Removing lease {0}", leaseToRemove);

	// The most up-to-date ref to the lease we have
	Lease rslt = null;
	try {
	    store.acquireMutatorLock();	    
	    synchronized (set) {
		ensureCurrent(set);
		
		final ClientLeaseWrapper clw 
		    = set.getClientLeaseWrapper(leaseToRemove);

		if (clw == null) {
		    // Lease must have been removed already
		    return null;
		}

		try {
		    lrm.remove(clw);
		} catch (UnknownLeaseException e) {
		    // This can happen if there was some problem
		    // renewing the lease or its LRM expiration just
		    // ran out. Since we are removing the lease anyway
		    // ignore. 
		}

		final boolean present = set.remove(clw);	

		// Only return a non-null result if the removed lease
		// had not be removed already
		if (present) {
		    // At this point we can assume clw is no
		    // longer deformed
		    rslt = clw.getClientLease();
		}
	    }
	} finally {
	    store.releaseMutatorLock();
	}    

	if (rslt == null) 
	    return null;

	// Whenever we serialize a lease we have to make sure that
	// its serial form will stay the same during the
	// serialization.  Since we have removed it from the set we
	// don't have to worry about this lease being serialized to disk
	// any more so changing the serial format here should be safe.
	rslt.setSerialFormat(Lease.DURATION);
	return rslt;
    }

    // Inherit java doc from super type
    public GetLeasesResult getLeases(Uuid id) throws ThrowThis {
	ready.check();
	final LeaseSet set = getSet(id);

	// We are not modifying the set so we don't need the mutator lock
	// $$$ Do we need a reader lock, or is the lock on the set enough?
	// $$$ Need to make sure we really don't mutate any persistent state
	// or have issues with serializing the leases.
	synchronized (set) {
	    ensureCurrent(set);
	    return new GetLeasesResult(set.getLeases());
	}
    }


    // Inherit java doc from super type
    public EventRegistration setExpirationWarningListener(
				 Uuid		     id,
				 RemoteEventListener listener, 
				 long		     minWarning, 
				 MarshalledObject    handback)
	throws RemoteException, ThrowThis
    {
	ready.check();
	final LeaseSet set = getSet(id);

	if (listener == null) {
	    minWarning = NO_LISTENER;
	    handback = null;
	} else if (minWarning < 0) {
	    throw new IllegalArgumentException(
	        "LeaseRenewalSet.setExpirationWarningListener:minWarning " +
		"must be positive");
	} else {
	    listener = (RemoteEventListener) listenerPreparer.prepareProxy(
		listener);
	}

	try {
	    store.acquireMutatorLock();	    
	    synchronized (set) {
		ensureCurrent(set);

		try {
		    final boolean haveBefore = set.haveWarningRegistration();
		    final EventRegistration rslt =
			set.setExpirationWarningListener(
			    listener, minWarning, handback);
		    final boolean haveAfter = set.haveWarningRegistration();

		    if (haveAfter || (haveBefore != haveAfter)) {
			// Either we had a registration before and we
			// don't now, we do now and not before, or we
			// had one before and we still do.  In the
			// first two cases we have to wack the
			// expiration manager so it can schedule the
			// right task.  In the last case wack the
			// expiration manager so it can reschedule in case
			// minWarning has changed.
			expMgr.reschedule(set);
		    }

		    return rslt;
		} catch (IOException e) {
		    // This means the listener could not be serialized,
		    // re-throw as an IllegalArgumentException 
		    throw new IllegalArgumentException("Passed a listener " +
			      "that could not be serialized");
		}
	    }
	} finally {
	    store.releaseMutatorLock();
	}    
    }

    /**
     * Remote a set if its expiration time has been reached.
     */
    void expireIfTime(LeaseSet set) {
	try {
	    store.acquireMutatorLock();

	    synchronized (set) {
		if (isCurrent(set)) {
		    // Someone must have renewed the lease...don't expire
		    return;
		}

		removeSet(set);
	    }
	} finally {
	    store.releaseMutatorLock();
	}
    }

    /**
     * Schedule the sending of an expiration warning event.
     * This could be a method on the set itself but this keeps all
     * of the high level synchronization logic in one file.
     */
    void sendWarningEvent(LeaseSet set) {
	// We don't need to acquire the store lock because we 
	// won't be mutating any persistent state.
	// We will be reading state of the set so we do need to
	// sync on it to ensure we get a constant view
	synchronized (set) {
	    if (!isCurrent(set)) {
		// Must have just been canceled or expired, don't send event
		return;
	    }

	    set.sendWarningEvent();
	}
    }

    // Inherit java doc from super type
    public EventRegistration setRenewalFailureListener(
				 Uuid		     id,
				 RemoteEventListener listener, 
				 MarshalledObject    handback)
	throws RemoteException, ThrowThis
    {
	ready.check();
	final LeaseSet set = getSet(id);

	if (listener == null) {
	    handback = null;
	} else {
	    listener = (RemoteEventListener) listenerPreparer.prepareProxy(
		listener);
	}

	try {
	    store.acquireMutatorLock();	    
	    synchronized (set) {
		ensureCurrent(set);

		try {
		    return set.setRenewalFailureListener(
			listener, handback);
		} catch (IOException e) {
		    // This means the listener could not be serialized,
		    // re-throw as an IllegalArgumentException 
		    throw new IllegalArgumentException("Passed a listener " +
			      "that could not be serialized");
		}
	    }
	} finally {
	    store.releaseMutatorLock();
	}    
    }

    /**
     * Handle failures to renew a lease by removing the lease from its set
     * and if needed schedule sending an event.
     * @param clw the wrapped client lease for the lease that could not
     *            be renewed.  <code>clw.isDeformed</code> must be
     *            <code>false</code>.
     */
    void renewalFailure(ClientLeaseWrapper clw) {
	final LeaseSet set = clw.getLeaseSet();
	if (set == null) {
	    // set must have just been removed, no state to update, no
	    // events to send, just return
	    return;
	}

	try {
	    store.acquireMutatorLock();	    

	    synchronized (set) {
		logger.log(Level.INFO, "Lease renewal failed for {0}", clw);

		if (!isCurrent(set)) {
		    // expired, no state to update, no
		    // events to send, just return
		    return;
		}

		set.renewalFailure(clw);

		// Remove from LRM.  Should already be
                // gone from LRM, but it might have been added back in
                // between the time the renewal failure occurred and
                // when we got around to processing it (that is now).
                // Making sure it is gone will keep things
                // consistent.
		try {
		    lrm.remove(clw);
		} catch (UnknownLeaseException e) {
		    // As long as the lease is gone we don't care
		}
	    }
	} finally {
	    store.releaseMutatorLock();
	}    	
    }


    /**
     * Remove a lease that has reached its desired expiration.
     * @param clw the wrapped client lease for the lease that we are done with
     */
    void desiredExpirationReached(ClientLeaseWrapper clw) {
	final LeaseSet set = clw.getLeaseSet();
	if (set == null) {
	    // set must have just been removed, no state to update, no
	    // events to send, just return
	    return;
	}

	try {
	    store.acquireMutatorLock();	    

	    synchronized (set) {
		if (!isCurrent(set)) {
		    // expired, no state to update, no events to send,
		    // just return
		    return;
		}

		// Make sure the lease is still in the set it thinks
		// it is
		if (!set.doesContainWrapper(clw)) {
		    // Must have been removed somewhere else
		    // forget about this event
		    return;
		}
		    
		// The client could have re-added the lease to the set
		// after the event occured but before we processed the
		// event.  Check to make sure it really should be
		// removed.
		final long desiredExpiration = clw.getMembershipExpiration();
		if (desiredExpiration > System.currentTimeMillis()) {  
		    // Not dead yet...still in the rest of our tables,
		    // just need to make sure it is in the LRM
		    lrm.renewUntil(clw, clw.getMembershipExpiration(),
				   clw.getRenewDuration(), lrmEventListener);
		    return;
		}

		// They could have re-added the lease but in way that
		// it should generate a renewal failure event. Note if
		// we are here the lease must be pass its desired
		// expiration
		if (clw.getExpiration() < desiredExpiration) {
		    // This time just return, the LRM will generate a
		    // renewal failure event, or may have
		    // already. Either way we don't need to add the
		    // lease back and failure event will remove it
		    // from the other tables
		    return;
		}

		// If we are here the lease should be removed
		logger.log(Level.FINE,
			   "Reached desired expiration for lease {0}",
			   clw);

		set.remove(clw);

		// Remove from LRM. Should already be
                // gone from LRM, but it might have been added back in
                // between the time the renewal failure occurred and
                // when we got around to processing it (that is now).
                // Make sure it is gone will keep things
                // consistent.
		try {
		    lrm.remove(clw);
		} catch (UnknownLeaseException e) {
		    // As long as the lease is gone we don't care
		}
	    }
	} finally {
	    store.releaseMutatorLock();
	}    	
    }

    /**
     * The implementation of <code>SendMonitor</code> we use to track 
     * event delivery threads.  Each set gets its own object.
     */
    private class SendMonitorImpl implements SendMonitor {
	/** Set this is the monitor for */
	final private LeaseSet set;

	/**
	 * Simple constructor.
	 * @param set the set this monitor is associated with
	 */
	private SendMonitorImpl(LeaseSet set) {
	    this.set = set;
	}

	// Methods needed to meet contract of SendMonitor
	
	// Inherit java doc from super type
	public void definiteException(EventType           type,
				      RemoteEvent         ev,
				      long                registrationNumber,
				      Throwable           t)
	{
	    // This may be more locking than we need (especially
	    // locking on the set) since EventType objects already
	    // perform a lot of locking, but this discipline is
	    // consistent with the rest of Norm and is therefore less
	    // likely to lead to bugs down the road.
	    try {
		store.acquireMutatorLock();	    

		if (!NormServerBaseImpl.isCurrent(set)) {
		    // Set is dead, don't bother updating its state
		    return;
		}

		synchronized (set) {
		    set.definiteException(type, ev, registrationNumber);
		}
	    } finally {
		store.releaseMutatorLock();
	    }    
	}
	
	// Inherit java doc from super type
	public boolean isCurrent() {
	    synchronized (set) {
		return NormServerBaseImpl.isCurrent(set);
	    }
	}
    }


    /**
     * Method used to remove membership expired leases from the server.
     * Assumes that they have already been removed from the set.
     * @param deadLeases an iterator with the leases that have to be 
     *                   removed
     */
    private void removeClientLeases(Iterator deadLeases) {
	while(deadLeases.hasNext()) {
	    ClientLeaseWrapper clw = (ClientLeaseWrapper) deadLeases.next();

	    // Remove from lrm
	    try {
		lrm.remove(clw);
	    } catch (UnknownLeaseException e) {
		// This can happen if there was some problem renewing
		// the lease, or its LRM expiration just ran out.
		// Since we are remove the lease any way ignore.
	    }
	}
    }

    /**
     * Throw a NoSuchObjectException, wrapped in a ThrowThis, if the
     * passed set has expired. Assumes set is locked.
     */
    private static void ensureCurrent(LeaseSet set) throws ThrowThis {
	if (!isCurrent(set)) {
	    throw new ThrowThis(new NoSuchObjectException("Set has expired"));
	}
    }

    /**
     * Returns true if the lease on the lease set is still current, else
     * false.
     */
    private static boolean isCurrent(LeaseSet set) {
	return set.getExpiration() > System.currentTimeMillis();
    }

    /** 
     * Return the set with the specified id, or throw a
     * NoSuchObjectException, wrapped in a ThrowThis if the set can't be found
     */
    private LeaseSet getSet(Uuid id) throws ThrowThis {
	final LeaseSet rslt = (LeaseSet) setTable.get(id);
	if (rslt == null) 
	    throw new ThrowThis(new NoSuchObjectException("Can't find set"));
	return rslt;
    }


    /////////////////////////////////////////
    // Methods defined in LeaseRenewalService

    // Inherit java doc from super type
    public LeaseRenewalSet createLeaseRenewalSet(long leaseDuration) {
	ready.check();
	final Uuid newID      = UuidFactory.generate();
	final LeaseSet newSet = new LeaseSet(newID, generator, store, this);
	
	LeasePeriodPolicy.Result leasePeriod;
	try {
	    leasePeriod = setLeasePolicy.grant(newSet, leaseDuration);
	} catch (LeaseDeniedException e) {
	    // This will never happen because we never use a policy that
	    // denies granting (or renewing) a lease; complain bitterly
	    // and throw a runtime exception
	    logger.log(Level.WARNING,
		       "Got LeaseDeniedException creating lease -- " +
		       "this should not happen!",
		       e);
	    throw new InternalNormException("Error creating lease", e);
	}

	Lease newLease = leaseFactory.newLease(newID, leasePeriod.expiration);
	newSet.setExpiration(leasePeriod.expiration);
	try {
	    store.acquireMutatorLock();
	    final Object u = new CreateLeaseSet(newSet);
	    store.update(u);
	    expMgr.register(newSet);
	    setTable.put(newID, newSet);
	} finally {
	    store.releaseMutatorLock();
	}

	LeaseRenewalSet result = SetProxy.create(serverProxy, newID, newLease);
	logger.log(Level.FINE, "Created lease renewal set {0}", result);
	return result;
    }

    //////////////////////////////
    // Callbacks used by LeaseSet

    /**
     * Method used by <code>LeaseSet</code> when it needs to cons up
     * a SetProxy with an up-to-date Lease.  Assumes the appropriate 
     * locks have been obtained.
     */
    SetProxy newSetProxy(LeaseSet set) {
	Lease l = leaseFactory.newLease(set.getUuid(), set.getExpiration());
	return SetProxy.create(serverProxy, set.getUuid(), l);
    }

    /**
     *  Create a new <code>SendMonitorImpl</code>
     */
    // $$$ This feels a bit questionable, one of the places where this
    // is called back is in LeaseSet's constructor! So the constructor is
    // letting a reference to the constructed object escape before the object
    // is done.  There are a couple alternatives but I am not sure they are
    // better, the best one is probably to defer creation of the set's 
    // EventType object until first use and grab the necessary SendMonitor
    // then, but then we will need all sorts of null guards...
    SendMonitor newSendMonitor(LeaseSet set) {
	return new SendMonitorImpl(set);
    }

    //////////////////////////////////////////
    // Implement ServerProxyTrust
    /**
     * @throws UnsupportedOperationException if the server proxy does not
     *	       implement both {@link RemoteMethodControl} and {@link
     *	       TrustEquivalence}
     */
    public TrustVerifier getProxyVerifier() {
	return new ProxyVerifier(serverProxy, serverUuid);
    }

    //////////////////////////////////////////
    // Thread to persist client lease renewals
    private class RenewLogThread extends InterruptedStatusThread {
	/** Create a daemon thread */
	private RenewLogThread() {
	    super("log renewals thread");
	    setDaemon(true);
	}

	public void run() {
	    while (!hasBeenInterrupted()) {
		try {
		    ClientLeaseWrapper clw;
		    synchronized (renewedList) {
			// If there is an item on the list pull it off for
			// processing, otherwise wait and try again
			if (renewedList.isEmpty()) {
			    try {
				renewedList.wait();
				continue; // go back to top of loop
			    } catch (InterruptedException e) {
				// someone wants us dead
				return;
			    }
			} else {
			    clw = (ClientLeaseWrapper) renewedList.remove(0);
			}			
		    }

		    if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER,
				   "Attempting to renew lease {0} at {1}",
				   new Object[] {
				       clw, 
				       new Long(System.currentTimeMillis()) });
		    }

		    // A lease was renewed, log the new state
		    final LeaseSet set = clw.getLeaseSet();
		    if (set == null) {
			// set must have just been removed, no state
			// to update, go to next item in list
			continue;
		    }
		    
		    try {	
			store.acquireMutatorLock();  
			synchronized (set) {
			    if (!isCurrent(set)) {
				// expired, no state to update, go to next item
				// in list
				continue;
			    }
			    
			    clw.clearRenewed();
			    // Small window here where the lease can
			    // be renewed, it's renewed flag is re-set
			    // and because it was cleared the clw ends
			    // up on renewed list, we then come back
			    // to this thread and log the new
			    // state, and come back later and log that
			    // state again.  Since this is just
			    // slightly wasteful, not incorrect, this
			    // is ok. [Reversing these two lines of
			    // course would be wrong...]
			    set.logRenewal(clw);	
			}
		    } finally {
			store.releaseMutatorLock();
		    }

		    // Give other threads a chance to run
		    Thread.yield();
		} catch (RuntimeException e) {
		    logger.log(
			Level.INFO,
			"Runtime exception in RenewLogThread -- restarting",
			e);
		}
	    }
	}
    }


    //////////////////////////////
    // Methods defined in Landlord

    // Inherit java doc from super type
    public long renew(Uuid cookie, long extension) 
	throws UnknownLeaseException, LeaseDeniedException
    {
	ready.check();
	final LeaseSet set = (LeaseSet) setTable.get(cookie);
	if (set == null) 
	    throw new UnknownLeaseException("No lease for cookie:" + cookie);
	try {
	    store.acquireMutatorLock();	    

	    synchronized (set) {
		if (!isCurrent(set)) {
		    // Lease has expired, don't renew
		    throw new UnknownLeaseException(
			"Lease has already expired");
		}
		// No one can expire the lease, so it is safe to update.
		// $$$ Might be better to make an extra call to currentTime
		// and calculate the new duration just be for returning
		long oldExpiration = set.getExpiration();
		LeasePeriodPolicy.Result leasePeriod =
		    setLeasePolicy.renew(set, extension);

		// Log update 
		final Object u = new LeaseSet.ChangeSetExpiration(
		    set, leasePeriod.expiration);
		store.update(u);

		set.setExpiration(leasePeriod.expiration);
		expMgr.reschedule(set);
		return leasePeriod.duration;
	    }
	} finally {
	    store.releaseMutatorLock();
	}
    }

    // Inherit java doc from super type
    public void cancel(Uuid cookie) throws UnknownLeaseException {
	ready.check();
	final LeaseSet set = (LeaseSet) setTable.get(cookie);
	if (set == null) 
	    throw new UnknownLeaseException("No lease for cookie:" + cookie);

	try {
	    store.acquireMutatorLock();
	    
	    synchronized (set) {
		if (!isCurrent(set)) {
		    //Someone else beat us to it, just return
		    return;
		}

		removeSet(set);
	    }
	} finally {
	    store.releaseMutatorLock();
	}
    }

    /**
     * Do the heavy lifting on removing a set, assumes the locks on the
     * set and store have been acquired.
     */
    private void removeSet(LeaseSet set) {
	// handle the possibility of a race calling removeSet twice for a set
	if (setTable.remove(set.getUuid()) != null) {
	    // set.destroy will persist the removal of the set, and 
	    // change the set's expiration to -1 which will cause any
	    // other operations on the set to throw NoSuchObjectException
	    // or UnknownLeaseException (they will all lock on the set
	    // and ensure current before doing anything substantive)
	    final Set leases = set.destroy(); 
	    removeClientLeases(leases.iterator());
	}
    }

    // Inherit java doc from super type
    public RenewResults renewAll(Uuid[] cookies, long[] extensions) {
	ready.check();
	/* Cookie types checked in individual renew calls */
	return LandlordUtil.renewAll(this, cookies, extensions);
    }

    // Inherit java doc from super type
    public Map cancelAll(Uuid[] cookies) {
	ready.check();
	/* Cookie types checked in individual cancel calls */
	return LandlordUtil.cancelAll(this, cookies);	    
    }

    ////////////////////////////////////////////////
    // Methods and classes needed by PersistentStore

    /**
     * Called by <code>PersistentStore</code> after every update to give
     * server a chance to trigger a snapshot.
     * @param updateCount number of updates since last snapshot
     */
    void updatePerformed(int updateCount) {
	// First check to see if the size of the log is larger than a 
	// minimum threshold, this keeps snapshots from happening when 
	// the state of the server is very small (like initially when
	// the snapshot size is zero)
	if (updateCount >= logToSnapshotThresh) {
	    // Compare the size of log to what next snapshot would be
	    final int snapshotSize = setTable.size() + countLeases.getCount();
	    if ((float) updateCount >= snapshotWt*((float) snapshotSize)) {
		// Both conditions meet, trigger snapshot
		snapshotter.takeSnapshot();
	    }
	}
    }

    /**
     * Perform the 3rd stage of log recovery, restoring the various pieces of
     * transient state (populating the LRM, restoring various transient
     * fields).
     */
    void restoreTransientState() {
	final long now = System.currentTimeMillis();

	for (Iterator i = setTable.values().iterator(); i.hasNext(); ) {

	    final LeaseSet set = (LeaseSet) i.next();

	    // Has this set expired?
	    if (now > set.getExpiration()) {
		// yes it has, remove it and move on
		i.remove();
		// This is all we have to do because none of the other
		// bookkeeping has been done yet for this set
		continue;
	    }

	    final Iterator leases = set.restoreTransientState(
		generator, store, this, recoveredListenerPreparer);
	
	    // Go through all the leases in the set and add them to 
	    // the right tables.
	    while (leases.hasNext()) {
		// We sync here so all the updates will be flushed to
		// memory before the the LRM's threads start working on
		// this lease
		ClientLeaseWrapper clw;
		synchronized (this) {
		    clw = (ClientLeaseWrapper) leases.next();

		    // Let the clw recover its state	    
		    clw.recoverTransient(
			renewedList, idGen, set, recoveredLeasePreparer);
		}

		// Note: there is no race condition here because
		// lrmEventListener and renewedList will buffer any
		// renewals/renewal failure until the rest of the server
		// is ready to handle them

		// Note: We may be adding client leases who's
		// desired expirations were before `now'. However, this is
		// ok as the LRM will generate either a renewal failure
		// (if the actual expiration of the lease is before
		// the desired expiration) or a desired expiration 
		// reached event (if the actual expiration of the lease
		// equal to or after the desired expiration), which in
		// turn will cause the "right things" to happen. Also
                // all the other methods are smart enough not be confused by 
		// leases that have reached their desired expiration
		// but have not yet been removed.

		final Throwable lt = clw.getLastFailure();

		if ((lt == null) || 
		    (ThrowableConstants.retryable(lt) == 
		     ThrowableConstants.INDEFINITE)) 
		{
		    lrm.renewUntil(clw, clw.getMembershipExpiration(), 
				   clw.getRenewDuration(), lrmEventListener);
		} else {
		    // This lease has already suffered a definite failure,
		    // don't let the LRM renew it.  Note we will only get
		    // here if we logged a renewal failure, but could not
		    // log the LeaseRenewalEvent from the LRM
		    lrmEventListener.notify(new LeaseRenewalEvent(lrm,
			clw, clw.getMembershipExpiration(), lt));
		}
	    }
	}
    }

    /**
     * Implementation of <code>LogHandler</code> used by NormServerBaseImpl
     */
    private class OurLogHandler extends LogHandler {
	// Inherit java doc from super type
	// Snapshot format
	//     Version
	//     Server unique ID
	//     Generator we use to create EventType objects
	//     The number of LeaseSet objects in the setTable
	//     All of the LeaseSet objects
	public void snapshot(OutputStream out) throws Exception {
	    final long now = System.currentTimeMillis();
	    final ObjectOutputStream oostream = new ObjectOutputStream(out);

	    oostream.writeInt(CURRENT_LOG_VERSION);
	    oostream.writeObject(serverUuid);
	    oostream.writeObject(generator);
	    oostream.writeInt(setTable.size());
	    final Collection sets = setTable.values();
	    for (Iterator i = sets.iterator(); i.hasNext();) {
		final LeaseSet set = (LeaseSet) i.next();

		// Grab lock on set so any concurrent getLeases() calls
		// will not be corrupted
		synchronized (set) { 
		    // Note, we used to drop desired expiration reached
		    // client leases here, but that was a bug.  In particular
		    // there could have been a renewal failure event 
		    // that we have not yet been notified of 		    
		    oostream.writeObject(set);
		}
	    }
	    oostream.flush();

	    // $$$ We are missing an optimization here: as we write each
	    // set we could check to see if any of the client leases
	    // in that set have been renewed, but not logged, and clear
	    // them from the renewedList (this assumes that the
	    // renewal changes the persisted state of the wrapper, not
	    // the processing by logRenewal()).  This is not a bug
	    // since any clw we pull off the renewedList and persist
	    // after this point will have state at least as up-to-date
	    // as this snapshot.
	}

	// Inherit java doc from super type
	public void recover(InputStream in) throws Exception {
	    final ObjectInputStream oistream = new ObjectInputStream(in);

	    int version;
	    version = oistream.readInt();

	    if (version != CURRENT_LOG_VERSION) {
		    throw new CorruptedStoreException("Incompatible version " +
		        "ID in log, looking for " + CURRENT_LOG_VERSION +
			", got " + version);

	    }

	    serverUuid = (Uuid) oistream.readObject();
	    generator = (EventTypeGenerator) oistream.readObject();
	    final int size = oistream.readInt();
	    setTable = Collections.synchronizedMap(new HashMap(size));
	    for (int i = 0; i < size; i++) {
		final LeaseSet set = (LeaseSet) oistream.readObject();
		setTable.put(set.getUuid(), set);
	    }
	}

	// Inherit java doc from super type
	public void applyUpdate(Object update) throws Exception {
	    final LoggedOperation op = (LoggedOperation) update;
	    op.apply(setTable);
	}
    }

    /**
     * Return a string summarizing the inventory of the server 
     */
    private String inventory() {
	return countLeases.getCount() + " client leases, " + 
	    setTable.size() + " sets.";
    }

    /**
     * Thread that performs the actual snapshots, done in a separate thread
     * so it will not hang up in-progress remote calls
     */
    private class SnapshotThread extends InterruptedStatusThread {
	/** Create a daemon thread */
	private SnapshotThread() {
	    super("snapshot thread");
	    setDaemon(true);
	}

	/** Signal this thread that is should take a snapshot */
	private synchronized void takeSnapshot() {
	    notifyAll();
	}

	public void run() {
	    while (!hasBeenInterrupted()) {
		synchronized (this) {
		    try {
			wait();
		    } catch (InterruptedException e) {
			return;
		    }
		}

		try {
		    if (logger.isLoggable(Level.FINER)) {
			logger.log(
			    Level.FINER, "Taking snapshot: {0}", inventory());
		    }

		    store.snapshot();

		} catch (InterruptedIOException e) {
		    // Some one wants us dead
		    return;
		} catch (Exception e) {
		    if (e instanceof LogException &&
			((LogException) e).detail instanceof
			InterruptedIOException)
		    {
			return;
		    }
		    /* $$$
		     * if taking the snapshot fails for any reason,
		     * then one of the following must be done:
		     *   -- output the problem to a file and exit
		     *   -- output the problem to a file and continue
		     *   -- set an "I have a problem" attribute and
		     *      then send a notification
		     * this issue will be addressed at a later time
		     */
		    logger.log(Level.WARNING, "Snapshot failed", e);
		}
	    }
	}
    }

    ///////////////////////////////////
    // Methods defined in Administrable

    // Inherit java doc from super type
    public Object getAdmin() {
	ready.check();
	return adminProxy;
    }

    ///////////////////////////////
    // Methods defined in JoinAdmin

    // Inherit java doc from super type
    public Entry[] getLookupAttributes() {
	ready.check();
	return joinState.getAttributes();
    }

    // Inherit java doc from super type
    public void addLookupAttributes(Entry[] attrSets) {
	ready.check();
	joinState.addAttributes(attrSets, true);
	logger.log(Level.CONFIG, "Added attributes");
    }

    // Inherit java doc from super type
    public void modifyLookupAttributes(Entry[] attrSetTemplates, 
				       Entry[] attrSets) 
    {
	ready.check();
	joinState.modifyAttributes(attrSetTemplates, attrSets, true);
	logger.log(Level.CONFIG, "Modified attributes");
    }
  
    // Inherit java doc from super type
    public String[] getLookupGroups() {
	ready.check();
	return joinState.getGroups();
    }

    // Inherit java doc from super type
    public void addLookupGroups(String[] groups) {
	ready.check();
	joinState.addGroups(groups);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Added lookup groups: {0}",
		       toString(groups));
	}
    }

    // Inherit java doc from super type
    public void removeLookupGroups(String[] groups) {
	ready.check();
	joinState.removeGroups(groups);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Removed lookup groups: {0}",
		       toString(groups));
	}
    }

    // Inherit java doc from super type
    public void setLookupGroups(String[] groups) {
	ready.check();
	joinState.setGroups(groups);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Set lookup groups: {0}",
		       toString(groups));
	}
    }

    // Inherit java doc from super type
    public LookupLocator[] getLookupLocators() {
	ready.check();
	return joinState.getLocators();
    }

    // Inherit java doc from super type
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	ready.check();
	for (int i = locators.length; --i >= 0; ) {
	    locators[i] = (LookupLocator) locatorPreparer.prepareProxy(
		locators[i]);
	}	    
	joinState.addLocators(locators);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Added lookup locators: {0}",
		       toString(locators));
	}
    }

    // Inherit java doc from super type
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	ready.check();
	for (int i = locators.length; --i >= 0; ) {
	    locators[i] = (LookupLocator) locatorPreparer.prepareProxy(
		locators[i]);
	}	    
	joinState.removeLocators(locators);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Removed lookup locators: {0}",
		       toString(locators));
	}
    }

    // Inherit java doc from super type
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	ready.check();
	for (int i = locators.length; --i >= 0; ) {
	    locators[i] = (LookupLocator) locatorPreparer.prepareProxy(
		locators[i]);
	}	    
	joinState.setLocators(locators);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Set lookup locators: {0}",
		       toString(locators));
	}
    }

    /** Returns the contents of an array as a string. */
    private static String toString(Object[] array) {
	if (array == null) {
	    return "null";
	}
	StringBuffer sb = new StringBuffer(String.valueOf(array[0]));
	for (int i = 1; i < array.length; i++) {
	    sb.append(", ").append(array[i]);
	}
	return sb.toString();
    }

    //////////////////////////////////
    // Methods defined in DestroyAdmin

    // Inherit java doc from super type
    public void destroy() throws RemoteException {
	ready.shutdown();
	logger.log(Level.INFO, "Destroying Norm service");

	joinState.terminateJoin();
	lrmEventListener.interrupt();
	renewLogger.interrupt();
	snapshotter.interrupt();
	expMgr.terminate();
	generator.terminate();
	lrm.clear();

	logger.log(Level.FINEST, "Independent threads interrupted");

	new DestroyThread().start();
	logger.log(Level.FINEST, "Destroy thread started");
    }

    /**
     * Unexport our stub appropriately.
     * @param force terminate in progress calls if necessary
     * @return true if unexport succeeds
     */
    boolean unexport(boolean force) throws NoSuchObjectException {
	return exporter.unexport(force);
    }

    /**
     * Method subclasses can override to perform any necessary post
     * log destruction cleanup.
     */
    void postDestroy() {
    }
    
    /**
     * Termination thread code.  We do this in a separate thread to
     * avoid deadlock, because Activatable.inactive will block until
     * in-progress RMI calls are finished.
     */
    private class DestroyThread extends Thread {
        /** Maximum delay for unexport attempts */
        private static final long MAX_DELAY = 2 * 60 * 1000;

	/** Create a non-daemon thread */
	private DestroyThread() {
	    super("DestroyThread");
	    /* override inheritance from RMI daemon thread */
	    setDaemon(false);
	}

	public void run() {
	    logger.log(Level.FINEST, "DestroyThread running");
	    
	    /*
	     * Work for up to MAX_DELAY to try to nicely
	     * unexport our stub, but if that does not work just end
	     */
	    final long end_time = System.currentTimeMillis() + MAX_DELAY;
	    boolean unexported = false;

	    try {
		while ((!unexported) &&
		       (System.currentTimeMillis() < end_time))
                {
		    /* wait for any pending operations to complete */
		    logger.log(Level.FINEST,
			       "Calling unexport (force=false)...");

		    unexported = unexport(false);

		    logger.log(Level.FINEST, "...rslt = " + unexported);

		    if (!unexported) {
			Thread.yield();
		    }
		}
	    } catch (NoSuchObjectException e) {
		logger.log(Level.FINEST, "...rslt = NoSuchObjectException");

		unexported = true; // This works too
	    } catch (Throwable t) {
		logger.log(Level.FINEST, "...rslt = ", t);
	    }

	    if (!unexported) {
		/* Attempt to forcefully export the service */
		try {
		    logger.log(Level.FINEST, "Calling unexport (force=true)");

		    unexport(true);
		} catch (NoSuchObjectException e) {
		    // This works too
		}
	    }

	    // Try to join the independent threads before deleting the store
	    try {
		logger.log(Level.FINEST, "Joining independent threads");

		lrmEventListener.join(MAX_DELAY);
		renewLogger.join(MAX_DELAY);
		snapshotter.join(MAX_DELAY);	       
	    } catch (InterruptedException e) {
		// Will not happen
	    }
   
	    try {
		logger.log(Level.FINEST, "Destroying store");

		store.destroy();
	    } catch (Exception t) {
		logger.log(Level.INFO,
			   "While destroying persistent store -- " +
			   "destroy continuing",
			   t);
	    }	    

	    if (lifeCycle != null) {
		/* Unregister the service implementation */
		lifeCycle.unregister(this);
	    }

	    logger.log(Level.FINEST, "Calling postDestroy");

	    postDestroy();

	    if (loginContext != null) {
		try {
		    logger.log(Level.FINEST, "Logging out");
		    loginContext.logout();
		} catch (Exception e) {
		    logger.log(
			Level.INFO, "Exception while logging out", e);
		}
	    }

	    logger.log(Level.FINEST, "Ending DestroyThread");
	}
    }

    /* -- Implement ServiceProxyAccessor -- */

    /** {@inheritDoc} */
    public Object getServiceProxy() {
	ready.check();
	return normProxy;
    }

    /* -- Implement ProxyAccessor -- */

    /** {@inheritDoc} */
    public Object getProxy() {
	/* Don't wait until ready to return the server proxy */
	return serverProxy;
    }

    ////////////////////
    // Server setup code

    /** Returns a string representation of this object. */
    public String toString() {
	String className = getClass().getName();
	className = className.substring(className.lastIndexOf('.') + 1);
	return className + "[" + serverUuid + "]";
    }

    /**
     * Simple container for an alternative return a value so we
     * can provide more detailed diagnostics.
     */
    class InitException extends Exception {
	private static final long serialVersionUID = 1;
	private InitException(String message, Throwable nested) {
	    super(message, nested);
	}
    }

    /**
     * Portion of construction that is common between the activatable and not
     * activatable cases.  This method performs the minimum number of
     * operations before establishing the Subject, and logs errors.
     */
    void init(String[] configOptions, LifeCycle lifeCycle)
	throws Exception
    {
	try {
	    final Configuration config = ConfigurationProvider.getInstance(
		configOptions, getClass().getClassLoader());
	    this.lifeCycle = lifeCycle;
	    loginContext = (LoginContext) config.getEntry(
		NORM, "loginContext", LoginContext.class, null);
	    if (loginContext == null) {
		initAsSubject(config);
	    } else {
		loginContext.login();
		try {
		    Subject.doAsPrivileged(
			loginContext.getSubject(),
			new PrivilegedExceptionAction() {
			    public Object run() throws Exception {
				initAsSubject(config);
				return null;
			    }
			},
			null);
		} catch (PrivilegedActionException e) {
		    throw e.getCause();
		}
	    }
	    ready.ready();
	    logger.log(Level.INFO, "Norm service started: {0}", this);
	} catch (Throwable e) {
	    initFailed(e);
	}
    }

    /**
     * Log information about failing to initialize the service and rethrow the
     * appropriate exception.
     *
     * @param e the exception produced by the failure
     */
    static void initFailed(Throwable e) throws Exception {
	String message = null;
	if (e instanceof InitException) {
	    message = e.getMessage();
	    e = e.getCause();
	}
	if (logger.isLoggable(Level.SEVERE)) {
	    if (message != null) {
		logThrow(Level.SEVERE, "initFailed",
			 "Unable to start Norm service: {0}",
			 new Object[] { message }, e);
	    } else {
		logger.log(Level.SEVERE, "Unable to start Norm service", e);
	    }
	}
	if (e instanceof Exception) {
	    throw (Exception) e;
	} else if (e instanceof Error) {
	    throw (Error) e;
	} else {
	    IllegalStateException ise =
		new IllegalStateException(e.getMessage());
	    ise.initCause(e);
	    throw ise;
	}
    }

    /** Logs a throw */
    private static void logThrow(Level level, String method,
				 String msg, Object[] msgParams, Throwable t)
    {
	LogRecord r = new LogRecord(level, msg);
	r.setLoggerName(logger.getName());
	r.setSourceClassName(NormServerBaseImpl.class.getName());
	r.setSourceMethodName(method);
	r.setParameters(msgParams);
	r.setThrown(t);
	logger.log(r);
    }

    /**
     * Common construction for activatable and non-activatable cases, run
     * under the proper Subject.
     */
    void initAsSubject(Configuration config) throws Exception {
	/* Get configuration entries first */
	if (persistent) {
	    persistenceDirectory = (String) Config.getNonNullEntry(
		config, NORM, "persistenceDirectory", String.class);
	    snapshotWt = Config.getFloatEntry(
		config, NORM, "persistenceSnapshotWeight",
		10, 0, Float.MAX_VALUE);
	    logToSnapshotThresh = Config.getIntEntry(
		config, NORM, "persistenceSnapshotThreshold",
		200, 0, Integer.MAX_VALUE);
	}
	leasePreparer = (ProxyPreparer) Config.getNonNullEntry(
	    config, NORM, "leasePreparer", ProxyPreparer.class,
	    new BasicProxyPreparer());
	listenerPreparer = (ProxyPreparer) Config.getNonNullEntry(
	    config, NORM, "listenerPreparer", ProxyPreparer.class,
	    new BasicProxyPreparer());
	locatorPreparer = (ProxyPreparer) Config.getNonNullEntry(
	    config, NORM, "locatorPreparer", ProxyPreparer.class,
	    new BasicProxyPreparer());
	if (persistent) {
	    recoveredLeasePreparer = (ProxyPreparer) Config.getNonNullEntry(
		config, NORM, "recoveredLeasePreparer", ProxyPreparer.class,
		new BasicProxyPreparer());
	    recoveredListenerPreparer =
		(ProxyPreparer) Config.getNonNullEntry(
		    config, NORM, "recoveredListenerPreparer",
		    ProxyPreparer.class, new BasicProxyPreparer());
	    recoveredLocatorPreparer = (ProxyPreparer) Config.getNonNullEntry(
		config, NORM, "recoveredLocatorPreparer", ProxyPreparer.class,
		new BasicProxyPreparer());
	}
	setLeasePolicy = (LeasePeriodPolicy) Config.getNonNullEntry(
	    config, NORM, "leasePolicy", LeasePeriodPolicy.class,
	    new FixedLeasePeriodPolicy(
		2 * 60 * 60 * 1000 /* max */, 60 * 60 * 1000 /* default */));
	isolateSets = ((Boolean) config.getEntry(
	    NORM, "isolateSets", boolean.class, Boolean.FALSE)).booleanValue();
	try {
	    lrm = (LeaseRenewalManager) Config.getNonNullEntry(
		config, NORM, "leaseManager", LeaseRenewalManager.class);
	} catch (NoSuchEntryException e) {
	    lrm = new LeaseRenewalManager(config);
	}
	exporter = getExporter(config);

	serverProxy = (NormServer) exporter.export(this);

	boolean done = false;
	try {
	    // We use some of these during the recovery process
	    expMgr = new LeaseExpirationMgr(this);
	    generator = new EventTypeGenerator();
	    lrmEventListener = new LRMEventListener(this);
	    renewLogger = new RenewLogThread();
	    snapshotter = new SnapshotThread();

	    try {
		store = new PersistentStore(
		    persistenceDirectory, new OurLogHandler(), this);
		// Creating the store completes the first two stages of 
		// log recovery (reading the snapshot and the updates)
		// Perform the last stage here of restoring transient state
		restoreTransientState();
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER, "Log recovered: {0}",
			       inventory());
		}

	    } catch (CorruptedStoreException e) {
		throw new InitException("Log corrupted, can't recover ", e);
	    } catch (StoreException e) {
		throw new InitException("Can't recover log", e);
	    }

	    if (serverUuid == null) {
		serverUuid = UuidFactory.generate();
	    }
	    normProxy = NormProxy.create(serverProxy, serverUuid);
	    adminProxy = AdminProxy.create(serverProxy, serverUuid);

	    // Create new baseline snapshot
	    try {
		store.snapshot();
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(
			Level.FINER, "Completed new baseline snapshot: {0}",
			inventory());
		}
	    } catch (IOException e) {
		throw new InitException(
		    "Can't create new baseline snapshot", e);
	    }

	    Entry[] serviceAttributes = {
		new ServiceInfo(
		    "Lease Renewal Service",		// name
		    "Sun Microsystems, Inc.",		// manufacturer
		    "Sun Microsystems, Inc.",		// vender
		    VersionConstants.SERVER_VERSION,	// version
		    "",					// model
		    ""),				// serialNumber
		new BasicServiceType("Lease Renewal Service")
	    };
	    try {
		joinState = new JoinState(
		    normProxy, lrm, config, serviceAttributes,
		    recoveredLocatorPreparer,
		    new ServiceID(serverUuid.getMostSignificantBits(),
				  serverUuid.getLeastSignificantBits()));
		store.addSubStore(joinState);
	    } catch (StoreException e) {
		throw new InitException("Can't create JoinState", e);
	    }

	    leaseFactory = new LeaseFactory(serverProxy, serverUuid);

	    // $$$ By rights this code should be in
	    // restoreTransientState(), however we can't have an independent
	    // thread running around changing persistant state util we get
	    // to this point (I think the only real issue is the baseline
	    // snapshot) and once we place a set in the expMgr it the
	    // underlying wakeup queue will start running which can cause
	    // calls to expireIfTime() (and sendWarningEvent() though those
	    // should not be a problem since they don't log anything).
	    //
	    // I would prefer to ether modify WakeupQueue() to have a "start
	    // now" method (equivalent to how we create lrmEventListener above
	    // but call start() bellow) or be able to hold an exclusive
	    // snapshot lock until the initial snapshot is done.  Ether should
	    // allow this code to be moved into restoreTransientState

	    for (Iterator i = setTable.values().iterator(); i.hasNext(); ) {
		final LeaseSet set = (LeaseSet) i.next();
		synchronized (set) {
		    expMgr.schedule(set);
		}
	    }

	    lrmEventListener.start();
	    renewLogger.start();
	    snapshotter.start();
	    done = true;
	} finally {
	    if (!done) {
		try {
		    unexport(true);
		} catch (Exception e) {
		    logger.log(
			Level.INFO,
			"Unable to unexport after failure during startup",
			e);
		}
	    }
	}
    }

    /** Returns whether to isolate renewal sets or batch lease across sets. */
    boolean isolateSets() {
	return isolateSets;
    }

    /**
     * Creates an instance of this class.
     *
     * @param persistent whether this server is persistent
     */
    NormServerBaseImpl(boolean persistent) {
	this.persistent = persistent;
    }

    /**
     * Returns the exporter to use to export this server.
     *
     * @param config the configuration to use for supplying the exporter
     * @return the exporter to use to export this server
     * @throws ConfigurationException if a problem occurs retrieving entries
     *	       from the configuration
     */
    Exporter getExporter(Configuration config)
	throws ConfigurationException
    {
	return (Exporter) Config.getNonNullEntry(
	    config, NORM, "serverExporter", Exporter.class,
	    new BasicJeriExporter(
		TcpServerEndpoint.getInstance(0), new BasicILFactory()));
    }
}
