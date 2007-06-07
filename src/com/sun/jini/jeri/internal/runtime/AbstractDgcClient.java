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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.action.GetLongAction;
import com.sun.jini.thread.NewThreadAction;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * AbstractDgcClient implements the client-side behavior of RMI's
 * distributed garbage collection system abstractly with respect to
 * the types used to represent transport endpoints, object IDs, and
 * live remote references.  The actual types used for endpoints,
 * object IDs, and live references depends on the concrete subclass.
 *
 * The entry point into the machinery of AbstractDgcClient is the
 * "registerRefs" method: when a live reference enters the scope of
 * this AbstractDgcClient (the current virtual machine, for example),
 * it should be registered with that method in order for it to
 * participate in distributed garbage collection.
 *
 * When the first live reference to a particular remote object is
 * registered, a "dirty" call is made to the server-side distributed
 * garbage collector at the remote object's endpoint, which, if
 * successful, returns a lease guaranteeing that the server-side DGC
 * will not collect the remote object for a certain period of time.
 * While live references to remote objects at a particular endpoint
 * exist, this AbstractDgcClient will continue to make more "dirty"
 * calls to renew its lease on the referenced remote objects.
 *
 * This AbstractDgcClient tracks the local reachability of registered
 * live references (using phantom references).  When all of the live
 * reference instances for a particular remote object become garbage
 * collected locally, a "clean" call is made to the server-side
 * distributed garbage collector, indicating that the server no longer
 * needs to keep the remote object alive for this client.
 *
 * Internally, AbstractDgcClient holds and manipulates transport
 * endpoints, object IDs, and live references with references of type
 * java.lang.Object; it is assumed that their actual classes define
 * "equals" and "hashCode" in a meaningful way.
 *
 * Concrete subclasses must provide additional behavior for the actual
 * endpoint, object ID, and live reference types by implementing the
 * abstract protected methods of this class (see below).  In
 * particular, the "getDgcProxy" method should return an object that
 * implements the actual protocol for DGC "dirty" and "clean" calls
 * for the endpoint type being used, the "freeEndpoint" method may
 * make use of the indication that a particular endpoint no longer has
 * references, and the "getRefEndpoint" and "getRefObjectID" methods
 * should return the endpoint and object ID contained in a particular
 * live reference object.  A concrete subclass should also provide a
 * type-safe equivalent of "registerRefs" that delegates to this
 * class's "registerRefs" method.
 *
 * @author Sun Microsystems, Inc.
 **/
abstract class AbstractDgcClient {

    /** lease duration to request (usually ignored by server) */
    private static final long leaseValue =		// default 10 minutes
	((Long) AccessController.doPrivileged(new GetLongAction(
	    "com.sun.jini.jeri.dgc.leaseValue", 600000)))
	    .longValue();

    /** maximum interval between retries of failed clean calls */
    private static final long cleanInterval =		// default 3 minutes
	((Long) AccessController.doPrivileged(new GetLongAction(
	    "com.sun.jini.jeri.dgc.cleanInterval", 180000)))
	    .longValue();

    /** minimum lease duration that we bother to honor */
    private static final long minimumDuration =		// default 5 seconds
	((Long) AccessController.doPrivileged(new GetLongAction(
	    "com.sun.jini.jeri.dgc.minimumDuration", 5000)))
	    .longValue();

    /** minimum retry count for dirty calls that fail */
    private static final int dirtyFailureRetries = 5;

    /** retry count for clean calls that fail with ConnectException */
    private static final int cleanConnectRetries = 3;

    /** constant empty Object array for lease renewal optimization */
    private static final Object[] emptyObjectArray = new Object[0];

    /** next sequence number for DGC calls (access synchronized on class) */
    private static long nextSequenceNum = Long.MIN_VALUE;

    /**
     * endpoint table: maps generic endpoint to EndpointEntry
     * (lock guards endpointTable)
     */
    private final Map endpointTable = new HashMap(5);

    protected AbstractDgcClient() {
    }

    /**
     * A DgcProxy is a proxy for invoking DGC operations on a server-side
     * DGC implementation at a particular endpoint.  A DgcProxy instance
     * for a given endpoint is obtained from concrete class (using the
     * getDgcProxy method) and used by the abstract implementation.
     */
    protected interface DgcProxy {
	long dirty(long sequenceNum, Object[] ids, long duration)
	    throws RemoteException;
	void clean(long sequenceNum, Object[] ids, boolean strong)
	    throws RemoteException;
    }
    /** Returns a proxy for making DGC calls to the given endpoint. */
    protected abstract DgcProxy getDgcProxy(Object endpoint);
    /** Indicates that resources for the given endpoint may be freed. */
    protected abstract void freeEndpoint(Object endpoint);
    /** Returns the endpoint in the given live reference. */
    protected abstract Object getRefEndpoint(Object ref);
    /** Returns the object ID in the given live reference. */
    protected abstract Object getRefObjectID(Object ref);

    /**
     * Registers the live reference instances in the supplied collection to
     * participate in distributed garbage collection.
     *
     * All of the live references in the list must be for remote objects at
     * the given endpoint.
     */
    protected final void registerRefs(Object endpoint, Collection refs) {
	/*
	 * Look up the given endpoint and register the refs with it.
	 * The retrieved entry may get removed from the global endpoint
	 * table before EndpointEntry.registerRefs() is able to acquire
	 * its lock; in this event, it returns false, and we loop and
	 * try again.
	 */
	EndpointEntry epEntry;
	do {
	    epEntry = getEndpointEntry(endpoint);
	} while (!epEntry.registerRefs(refs));
    }

    /**
     * Gets the next sequence number to be used for a dirty or clean
     * operation from this AbstractDgcClient.  This method should only be
     * called while synchronized on the EndpointEntry whose data structures
     * the operation affects.
     */
    private static synchronized long getNextSequenceNum() {
	return nextSequenceNum++;
    }

    /**
     * Given the length of a lease and the time that it was granted,
     * computes the absolute time at which it should be renewed, giving
     * room for reasonable computational and communication delays.
     */
    private static long computeRenewTime(long grantTime, long duration) {
	/*
	 * REMIND: This algorithm should be more sophisticated, waiting
	 * a longer fraction of the lease duration for longer leases.
	 */
	return grantTime + (duration / 2);
    }

    /**
     * Looks up the EndpointEntry for the given endpoint.  An entry is
     * created if one does not already exist.
     */
    private EndpointEntry getEndpointEntry(Object endpoint) {
	synchronized (endpointTable) {
	    EndpointEntry entry = (EndpointEntry) endpointTable.get(endpoint);
	    if (entry == null) {
		entry = new EndpointEntry(endpoint);
		endpointTable.put(endpoint, entry);
		/*
		 * If the endpoint table was previously empty, we are now
		 * interested in special assistance from the local garbage
		 * collector for aggressively discovering unreachable live
		 * remote references (by notifying our phantom references),
		 * so that DGC "clean" calls can be sent in a timely fashion.
		 *
		 * Without guaranteed access to something like the
		 * sun.misc.GC API, however, we currently have no
		 * practical way of getting such special assistance.
		 */
	    }
	    return entry;
	}
    }

    /**
     * EndpointEntry encapsulates the client-side DGC information specific
     * to a particular endpoint.  Of most significance is the table that
     * maps live reference objects to RefEntry objects and the renew/clean
     * thread that handles asynchronous client-side DGC operations.
     */
    private final class EndpointEntry {

	/** the endpoint that this EndpointEntry is for */
	private final Object endpoint;
	/** synthesized reference to the remote server-side DGC */
	private final DgcProxy dgcProxy;
	/** renew/clean thread for handling lease renewals and clean calls */
	private final Thread renewCleanThread;
	/** reference queue for phantom references */
	private final ReferenceQueue refQueue = new ReferenceQueue();

	/* mutable instance state (below) is guarded by this object's lock */

	/** true if this entry has been removed from the global table */
	private boolean removed = false;

	/** table of refs held for endpoint: maps object ID to RefEntry */
	private final Map refTable = new HashMap(5);
	/** set of RefEntry instances from last (failed) dirty call */
	private Set invalidRefs = new HashSet(5);

	/** absolute time to renew current lease to this endpoint */
	private long renewTime = Long.MAX_VALUE;
	/** absolute time current lease to this endpoint will expire */
	private long expirationTime = Long.MIN_VALUE;
	/** count of recent dirty calls that have failed */
	private int dirtyFailures = 0;
	/** absolute time of first recent failed dirty call */
	private long dirtyFailureStartTime;
	/** (average) elapsed time for recent failed dirty calls */
	private long dirtyFailureDuration;

	/** true if renew/clean thread may be interrupted */
	private boolean interruptible = false;

	/** set of clean calls that need to be made */
	private final Set pendingCleans = new HashSet(5);

	private EndpointEntry(final Object endpoint) {
	    this.endpoint = endpoint;
	    dgcProxy = getDgcProxy(endpoint);
	    renewCleanThread = (Thread)	AccessController.doPrivileged(
		new NewThreadAction(new RenewCleanThread(),
				    "RenewClean-" + endpoint, true));
	    renewCleanThread.start();
	}

	/**
	 * Registers the live reference instances in the supplied list to
	 * participate in distributed garbage collection.
	 *
	 * This method returns false if this entry was removed from the
	 * global endpoint table (because it was empty) before these refs
	 * could be registered.  In that case, a new EndpointEntry needs
	 * to be looked up.
	 *
	 * This method must NOT be invoked while synchronized on this
	 * EndpointEntry.
	 */
	boolean registerRefs(Collection refs) {
	    assert !Thread.holdsLock(this);

	    Set refsToDirty = null;	// entries for refs needing dirty
	    long sequenceNum;		// sequence number for dirty call

	    synchronized (this) {
		if (removed) {
		    return false;
		}

		Iterator iter = refs.iterator();
		while (iter.hasNext()) {
		    Object ref = iter.next();
		    assert getRefEndpoint(ref).equals(endpoint);

		    Object objectID = getRefObjectID(ref);
		    RefEntry refEntry = (RefEntry) refTable.get(objectID);
		    if (refEntry == null) {
			refEntry = new RefEntry(objectID);
			refTable.put(objectID, refEntry);
			if (refsToDirty == null) {
			    refsToDirty = new HashSet(5);
			}
			refsToDirty.add(refEntry);
		    }

		    refEntry.addInstanceToRefSet(ref);
		}

		if (refsToDirty == null) {
		    return true;
		}

		refsToDirty.addAll(invalidRefs);
		invalidRefs.clear();

		sequenceNum = getNextSequenceNum();
	    }

	    makeDirtyCall(refsToDirty, sequenceNum);
	    return true;
	}

	/**
	 * Removes the given RefEntry from the ref table.  If that makes
	 * the ref table empty, remove this entry from the global endpoint
	 * table.
	 *
	 * This method must ONLY be invoked while synchronized on this
	 * EndpointEntry.
	 */
	private void removeRefEntry(RefEntry refEntry) {
	    assert Thread.holdsLock(this);
	    assert !removed;
	    assert refTable.containsKey(refEntry.getObjectID());

	    refTable.remove(refEntry.getObjectID());
	    invalidRefs.remove(refEntry);
	    if (refTable.isEmpty()) {
		synchronized (endpointTable) {
		    endpointTable.remove(endpoint);
		    freeEndpoint(endpoint);
		    /*
		     * If the endpoint table is now empty, we are no longer
		     * interested in special assistance from the local garbage
		     * collector for aggressively discovering unreachable
		     * live remote references, if we had been getting such
		     * special assistance in the first place.
		     */
		}
		removed = true;
	    }
	}

	/**
	 * Makes a DGC dirty call to this entry's endpoint, for the
	 * object IDs corresponding to the given set of refs and with
	 * the given sequence number.
	 *
	 * This method must NOT be invoked while synchronized on this
	 * EndpointEntry.
	 */
	private void makeDirtyCall(Set refEntries, long sequenceNum) {
	    assert !Thread.holdsLock(this);

	    Object[] ids;
	    if (refEntries != null) {
		ids = createObjectIDArray(refEntries);
	    } else {
		ids = emptyObjectArray;
	    }

	    long startTime = System.currentTimeMillis();
	    try {
		long duration = dgcProxy.dirty(sequenceNum, ids, leaseValue);

		synchronized (this) {
		    dirtyFailures = 0;

		    if (duration < 0) {
			setRenewTime(Long.MAX_VALUE);
			invalidRefs.addAll(refTable.values());
		    } else {
			setRenewTime(
			    computeRenewTime(startTime,
					     Math.max(duration,
						      minimumDuration)));
			expirationTime = startTime + duration;
		    }
		}

	    } catch (NoSuchObjectException e) {
		synchronized (this) {
		    setRenewTime(Long.MAX_VALUE);
		    invalidRefs.addAll(refTable.values());
		}
	    } catch (Exception e) {
		long endTime = System.currentTimeMillis();

		synchronized (this) {
		    dirtyFailures++;

		    if (dirtyFailures == 1) {
			/*
			 * If this was the first recent failed dirty call,
			 * reschedule another one immediately, in case there
			 * was just a transient network problem, and remember
			 * the start time and duration of this attempt for
			 * future calculations of the delays between retries.
			 */
			dirtyFailureStartTime = startTime;
			dirtyFailureDuration = endTime - startTime;
			setRenewTime(endTime);
		    } else {
			/*
			 * For each successive failed dirty call, wait for a
			 * (binary) exponentially increasing delay before
			 * retrying, to avoid network congestion.
			 */
			int n = dirtyFailures - 2;
			if (n == 0) {
			    /*
			     * Calculate the initial retry delay from the
			     * average time elapsed for each of the first
			     * two failed dirty calls.  The result must be
			     * at least 1000ms, to prevent a tight loop.
			     */
			    dirtyFailureDuration =
				Math.max((dirtyFailureDuration +
					  (endTime - startTime)) >> 1, 1000);
			}
			long newRenewTime =
			    endTime + (dirtyFailureDuration << n);

			/*
			 * Continue if the last known held lease has not
			 * expired, or else at least a fixed number of times,
			 * or at least until we've tried for a fixed amount
			 * of time (the default lease value we request).
			 */
			if (newRenewTime < expirationTime ||
			    dirtyFailures < dirtyFailureRetries ||
			    newRenewTime < dirtyFailureStartTime + leaseValue)
			{
			    setRenewTime(newRenewTime);
			} else {
			    /*
			     * Give up: postpone lease renewals until next
			     * ref is registered for this endpoint.
			     */
			    setRenewTime(Long.MAX_VALUE);
			}
		    }

		    if (refEntries != null) {
			/*
			 * Add all of these refs to the set of refs for this
			 * endpoint that may be invalid (this AbstractDgcClient
			 * may not be in the server's referenced set), so that
			 * we will attempt to explicitly dirty them again in
			 * the future.
			 */
			invalidRefs.addAll(refEntries);
			
			/*
			 * Record that a dirty call has failed for all of these
			 * refs, so that clean calls for them in the future
			 * will be strong.
			 */
			Iterator iter = refEntries.iterator();
			while (iter.hasNext()) {
			    RefEntry refEntry = (RefEntry) iter.next();
			    refEntry.markDirtyFailed();
			}
		    }

		    /*
		     * If the last known held lease will have expired before
		     * the next renewal, all refs might be invalid.
		     */
		    if (renewTime >= expirationTime) {
			invalidRefs.addAll(refTable.values());
		    }
		}
	    }
	}
	
	/**
	 * Sets the absolute time at which the lease for this entry should
	 * be renewed.
	 *
	 * This method must ONLY be invoked while synchronized on this
	 * EndpointEntry.
	 */
	private void setRenewTime(long newRenewTime) {
	    assert Thread.holdsLock(this);

	    if (newRenewTime < renewTime) {
		renewTime = newRenewTime;
		if (interruptible) {
		    AccessController.doPrivileged(new PrivilegedAction() {
			public Object run() {
			    renewCleanThread.interrupt();
			    return null;
			}
		    });
		}
	    } else {
		renewTime = newRenewTime;
	    }
	}

	/**
	 * RenewCleanThread handles the asynchronous client-side DGC activity
	 * for this entry: renewing the leases and making clean calls.
	 */
	private class RenewCleanThread implements Runnable {

	    public void run() {
		do {
		    long timeToWait;
		    RefEntry.PhantomLiveRef phantom = null;
		    boolean needRenewal = false;
		    Set refsToDirty = null;
		    long sequenceNum = Long.MIN_VALUE;

		    synchronized (EndpointEntry.this) {
			/*
			 * Calculate time to block (waiting for phantom
			 * reference notifications).  It is the time until the
			 * lease renewal should be done, bounded on the low
			 * end by 1 ms so that the reference queue will always
			 * get processed, and if there are pending clean
			 * requests (remaining because some clean calls
			 * failed), bounded on the high end by the maximum
			 * clean call retry interval.
			 */
			long timeUntilRenew =
			    renewTime - System.currentTimeMillis();
			timeToWait = Math.max(timeUntilRenew, 1);
			if (!pendingCleans.isEmpty()) {
			    timeToWait = Math.min(timeToWait, cleanInterval);
			}

			/*
			 * Set flag indicating that it is OK to interrupt this
			 * thread now, such as if a earlier lease renewal time
			 * is set, because we are only going to be blocking
			 * and can deal with interrupts.
			 */
			interruptible = true;
		    }

		    try {
			/*
			 * Wait for the duration calculated above for any of
			 * our phantom references to be enqueued.
			 */
			phantom = (RefEntry.PhantomLiveRef)
			    refQueue.remove(timeToWait);
		    } catch (InterruptedException e) {
		    }

		    synchronized (EndpointEntry.this) {
			/*
			 * Set flag indicating that it is NOT OK to interrupt
			 * this thread now, because we may be undertaking I/O
			 * operations that should not be interrupted (and we
			 * will not be blocking arbitrarily).
			 */
			interruptible = false;
			Thread.interrupted();	// clear interrupted state

			/*
			 * If there was a phantom reference enqueued, process
			 * it and all the rest on the queue, generating
			 * clean requests as necessary.
			 */
			if (phantom != null) {
			    processPhantomRefs(phantom);
			}

			/*
			 * Check if it is time to renew this entry's lease.
			 */
			long currentTime = System.currentTimeMillis();
			if (currentTime > renewTime) {
			    needRenewal = true;
			    if (currentTime >= expirationTime) {
				invalidRefs.addAll(refTable.values());
			    }
			    if (!invalidRefs.isEmpty()) {
				refsToDirty = invalidRefs;
				invalidRefs = new HashSet(5);
			    }
			    sequenceNum = getNextSequenceNum();
			}
		    }

		    if (needRenewal) {
			makeDirtyCall(refsToDirty, sequenceNum);
		    }

		    if (!pendingCleans.isEmpty()) {
			makeCleanCalls();
		    }
		} while (!removed || !pendingCleans.isEmpty());
	    }
	}

	/**
	 * Processes the notification of the given phantom reference and any
	 * others that are on this entry's reference queue.  Each phantom
	 * reference is removed from its RefEntry's ref set.  All ref
	 * entries that have no more registered instances are collected
	 * into up to two batched clean call requests: one for refs
	 * requiring a "strong" clean call, and one for the rest.
	 *
	 * This method must ONLY be invoked while synchronized on this
	 * EndpointEntry.
	 */
	private void processPhantomRefs(RefEntry.PhantomLiveRef phantom) {
	    assert Thread.holdsLock(this);

	    Set strongCleans = null;
	    Set normalCleans = null;

	    do {
		RefEntry refEntry = phantom.getRefEntry();
		refEntry.removeInstanceFromRefSet(phantom);
		if (refEntry.isRefSetEmpty()) {
		    if (refEntry.hasDirtyFailed()) {
			if (strongCleans == null) {
			    strongCleans = new HashSet(5);
			}
			strongCleans.add(refEntry);
		    } else {
			if (normalCleans == null) {
			    normalCleans = new HashSet(5);
			}
			normalCleans.add(refEntry);
		    }
		    removeRefEntry(refEntry);
		}
	    } while ((phantom =
		(RefEntry.PhantomLiveRef) refQueue.poll()) != null);

	    if (strongCleans != null) {
		pendingCleans.add(
		    new CleanRequest(getNextSequenceNum(),
				     createObjectIDArray(strongCleans),
				     true));
	    }
	    if (normalCleans != null) {
		pendingCleans.add(
		    new CleanRequest(getNextSequenceNum(),
				     createObjectIDArray(normalCleans),
				     false));
	    }
	}

	/**
	 * Makes all of the clean calls described by the clean requests in
	 * this entry's set of "pending cleans".  Clean requests for clean
	 * calls that succeed are removed from the "pending cleans" set.
	 *
	 * This method must NOT be invoked while synchronized on this
	 * EndpointEntry.
	 */
	private void makeCleanCalls() {
	    assert !Thread.holdsLock(this);

	    Iterator iter = pendingCleans.iterator();
	    while (iter.hasNext()) {
		CleanRequest request = (CleanRequest) iter.next();
		try {
		    dgcProxy.clean(request.sequenceNum, request.objectIDs,
				  request.strong);
		    iter.remove();
		} catch (NoSuchObjectException e) {
		    iter.remove();
		} catch (Exception e) {
		    if (e instanceof ConnectException ||
			e instanceof ConnectIOException)
		    {
			/*
			 * If we get a ConnectException, the target DGC likely
			 * has gone away, in which case we shouldn't bother
			 * retrying this clean request forever.  Then again,
			 * the server could just be heavily loaded, so we will
			 * give a finite number of retry opportunities to
			 * clean requests that fail this way.
			 *
			 * A similar (but different) argument can be made for
			 * ConnectIOException.
			 */
			if (++request.connectFailures >= cleanConnectRetries) {
			    iter.remove();
			}
		    } else {
			// possible transient failure, retain clean request
		    }
		}
	    }
	}

	/**
	 * Creates an array of object IDs (needed for the DGC remote calls)
	 * from the ids in the given set of refs.
	 */
	private Object[] createObjectIDArray(Set refEntries) {
	    Object[] ids = new Object[refEntries.size()];
	    Iterator iter = refEntries.iterator();
	    for (int i = 0; i < ids.length; i++) {
		ids[i] = ((RefEntry) iter.next()).getObjectID();
	    }
	    return ids;
	}

	/**
	 * RefEntry encapsulates the client-side DGC information specific to
	 * a particular object ID of an endpoint (a unique live reference
	 * value).
	 *
	 * In particular, it contains a set of phantom references to all of
	 * the live reference instances for the given object ID and endpoint
	 * in this VM that have been registered with this AbstractDgcClient
	 * (but not yet garbage collected locally).
	 */
	private class RefEntry {

	    /**
	     * the object ID that this RefEntry is for (the endpoint is
	     * implied by the outer EndpointEntry instance)
	     */
	    private final Object objectID;

	    /*
	     * mutable instance state (below) is guarded by outer
	     * EndpointEntry's lock
	     */

	    /** set of phantom references to registered instances */
	    private final Set refSet = new HashSet(5);
	    /** true if a dirty call containing this ref has failed */
	    private boolean dirtyFailed = false;

	    RefEntry(Object objectID) {
		this.objectID = objectID;
	    }

	    /**
	     * Returns the object ID that this entry is for.
	     */
	    Object getObjectID() {
		return objectID;
	    }

	    /**
	     * Adds a live reference to the set of registered instances for
	     * this entry.
	     *
	     * This method must ONLY be invoked while synchronized on this
	     * RefEntry's EndpointEntry.
	     */
	    void addInstanceToRefSet(Object ref) {
		assert Thread.holdsLock(EndpointEntry.this);
		assert getRefObjectID(ref).equals(objectID);

		/*
		 * Only keep a phantom reference to the registered instance,
		 * so that it can be garbage collected normally (and we can be
		 * notified when that happens).
		 */
		refSet.add(new PhantomLiveRef(ref));
	    }

	    /**
	     * Removes a PhantomLiveRef from the set of registered instances.
	     *
	     * This method must ONLY be invoked while synchronized on this
	     * RefEntry's EndpointEntry.
	     */
	    void removeInstanceFromRefSet(PhantomLiveRef phantom) {
		assert Thread.holdsLock(EndpointEntry.this);
		assert refSet.contains(phantom);
		refSet.remove(phantom);
	    }

	    /**
	     * Returns true if there are no registered live reference
	     * instances for this entry still reachable in this VM.
	     *
	     * This method must ONLY be invoked while synchronized on this
	     * RefEntry's EndpointEntry.
	     */
	    boolean isRefSetEmpty() {
		assert Thread.holdsLock(EndpointEntry.this);
		return refSet.size() == 0;
	    }

	    /**
	     * Records that a dirty call that explicitly contained this
	     * entry's ref value has failed.
	     *
	     * This method must ONLY be invoked while synchronized on this
	     * RefEntry's EndpointEntry.
	     */
	    void markDirtyFailed() {
		assert Thread.holdsLock(EndpointEntry.this);
		dirtyFailed = true;
	    }

	    /**
	     * Returns true if a dirty call that explicitly contained this
	     * entry's ref value has failed (and therefore a clean call for
	     * the ref value needs to be marked "strong").
	     *
	     * This method must ONLY be invoked while synchronized on this
	     * RefEntry's EndpointEntry.
	     */
	    boolean hasDirtyFailed() {
		assert Thread.holdsLock(EndpointEntry.this);
		return dirtyFailed;
	    }

	    /**
	     * PhantomLiveRef is a PhantomReference to a live reference
	     * instance, used to detect when the particular live reference
	     * becomes permanently unreachable in this VM.
	     */
	    class PhantomLiveRef extends PhantomReference {
	
		PhantomLiveRef(Object ref) {
		    super(ref, EndpointEntry.this.refQueue);
		}

		RefEntry getRefEntry() {
		    return RefEntry.this;
		}
	    }
	}
    }

    /**
     * CleanRequest holds the data for the arguments of a clean call
     * that needs to be made.
     */
    private static class CleanRequest {

	long sequenceNum;
	Object[] objectIDs;
	boolean strong;

	/** how many times this request has failed with ConnectException */
	int connectFailures = 0;

	CleanRequest(long sequenceNum, Object[] objectIDs, boolean strong) {
	    this.sequenceNum = sequenceNum;
	    this.objectIDs = objectIDs;
	    this.strong = strong;
	}
    }
}
