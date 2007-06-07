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
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.lease.ExpirationWarningEvent;
import net.jini.lease.LeaseRenewalSet;
import net.jini.security.ProxyPreparer;

import com.sun.jini.landlord.LeasedResource;
import com.sun.jini.norm.event.EventFactory;
import com.sun.jini.norm.event.EventType;
import com.sun.jini.norm.event.EventTypeGenerator;
import com.sun.jini.norm.event.SendMonitor;

/**
 * Norm's internal representation of LeaseRenewalSets.  Unless otherwise
 * noted the methods of this class assume synchronization being handled
 * by the caller.
 *
 * @author Sun Microsystems, Inc.
 */
class LeaseSet implements Serializable, LeasedResource {
    private static final long serialVersionUID = 2;

    /**
     * Expiration time of the set
     * @serial
     */
    private long expiration;

    /**
     * We keep two copies of the expiration time guarded by different 
     * locks so the the client lease renewal threads don't have to wait
     * on the set's lock.
     */
    private transient ExpirationTime expiration2;
  
    /**
     * ID that uniquely identifies this set
     * @serial
     */
    private final Uuid ID;

    /**
     * A collection of the client leases in this set (in wrapped form).
     * @serial
     */ 
    private final Set leases = new HashSet();

    /**
     * A table that maps client leases to client lease wrappers.
     */
    private transient LeaseTable leaseTable;

    /**
     * Time before <code>expiration</code> to send expiration warning events
     * @serial
     */
    private long minWarning = NormServer.NO_LISTENER;

    /**
     * The event type for expiration warning events
     * @serial
     */
    private EventType warningEventType;

    /**
     * The current sequence number for expiration warning events
     * @serial
     */
    private long warningSeqNum;

    /**
     * The event type for failure events
     * @serial
     */
    private EventType failureEventType;

    /**
     * <code>PersistentStore</code> that changes should be logged to.
     */
    private transient PersistentStore store;

    /**
     * The <code>NormServerBaseImpl</code> are attached to
     */
    private transient NormServerBaseImpl normServerBaseImpl;

    // Constructors and state restoration
    /**
     * Simple constructor.  Note expiration will be set when we allocate
     * a lease for this set.
     * @param ID for this set
     * @param generator object set can use to create new event type objects
     * @param store <code>PersistentStore<code> that changes should be logged
     *              to
     * @param normServerBaseImpl the <code>NormServerBaseImpl</code> that 
     *              created this set
     */
    LeaseSet(Uuid ID, EventTypeGenerator generator, PersistentStore store,
	     NormServerBaseImpl normServerBaseImpl)
    {
	this.store = store;
	this.normServerBaseImpl = normServerBaseImpl;
	this.ID = ID;

	// For completeness
	expiration2 = new ExpirationTime(expiration);
	leaseTable = new LeaseTable();

	final SendMonitor sendMonitor = 
	    normServerBaseImpl.newSendMonitor(this);	

	try {
	    warningEventType = 
		generator.newEventType(
		    sendMonitor, LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID);
	    failureEventType =
		generator.newEventType(
		    sendMonitor, LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID);
	} catch (IOException e) {
	    // Because we are passing null for the listener we will
	    // never get an exception
	    throw new AssertionError();
	}
    }

    /**
     * Override readObject so we can restore expiration2
     */
    private void readObject(ObjectInputStream in) 
	throws IOException, ClassNotFoundException 
    {
	in.defaultReadObject();
	
	// Restore the 2nd copy
	expiration2 = new ExpirationTime(expiration);

	// Create lease table, but only populate after restoring lease wrapper
	// transient state
	leaseTable = new LeaseTable();
    }

    /** 
     * Restore the transient state of the set that can't be restored 
     * automatically after a log recovery.
     * @param generator event type generator associated with this set's events
     * @param store <code>PersistentStore<code> that changes should be 
     *        logged to
     * @param normServerBaseImpl the <code>normServerBaseImpl</code> that 
     *        created this set
     * @param recoveredListenerPreparer the proxy preparer to use to prepare
     *	      recovered listeners
     * @return an iterator over the set of client leases
     */
    Iterator restoreTransientState(EventTypeGenerator generator, 
				   PersistentStore store,
				   NormServerBaseImpl normServerBaseImpl,
				   ProxyPreparer recoveredListenerPreparer)
    {
	this.normServerBaseImpl = normServerBaseImpl;
	this.store = store;

	final SendMonitor sendMonitor = 
	    normServerBaseImpl.newSendMonitor(this);	
	warningEventType.restoreTransientState(
	    generator, sendMonitor, recoveredListenerPreparer);
	failureEventType.restoreTransientState(
	    generator, sendMonitor, recoveredListenerPreparer);

	// Instead of logging the sequence number each time we send
	// a warning event (like we do for renewal failures), we just
	// record the current sequence number as of every snapshot and
	// assume we won't send more than Integer.MAX_VALUE warning events
	// between snapshots, nor will we crash and restart more than
	// ~ Long.MAX_VALUE/Integer.MAX_VALUE times.
	warningEventType.setLastSequenceNumber(warningSeqNum +
					       Integer.MAX_VALUE);
	normServerBaseImpl.updateLeaseCount(leases.size());
	return leases.iterator();
    }

    /**
     * Return the wrapper for the specified client lease, or null if not
     * found.
     */
    ClientLeaseWrapper getClientLeaseWrapper(Lease clientLease) {
	return leaseTable.get(clientLease);
    }

    /**
     * Utility method to replace a client lease in the lease set.  Returns true
     * if an equal lease was not already present.  Does not update the lease
     * table.
     */
    private boolean replace(ClientLeaseWrapper clw) {
	boolean found = leases.remove(clw);
	leases.add(clw);
	return !found;
    }

    /**
     * Add or update the specified wrapped client lease to the set.
     * @param clw lease to be added or updated
     */
    void update(ClientLeaseWrapper clw) {
	boolean added = replace(clw);
	leaseTable.put(clw);
	final Object u = new UpdateClientLease(this, clw);
	store.update(u);
	if (added) {
	    normServerBaseImpl.updateLeaseCount(1);
	}
    }

    /**
     * Add a lease already in the lease set to the lease table.  Used during
     * recovery to add the wrapper to the lease table only after it has been
     * given its recovery proxy preparer and attempting to unpack the lease has
     * attempted.
     */
    void addToLeaseTable(ClientLeaseWrapper clw) {
	leaseTable.put(clw);
    }

    /**
     * Return true if the passed wrapper is in the set 
     */
    boolean doesContainWrapper(ClientLeaseWrapper clw) {
	return leases.contains(clw);
    }

    /**
     * Utility method to remove a client lease.  Returns true if the lease is
     * removed.
     */
    private boolean removeInternal(ClientLeaseWrapper clw) {
	if (leases.remove(clw)) {
	    leaseTable.remove(clw);
	    return true;
	}
	return false;
    }

    /**
     * Remove the specified wrapped client lease from the set.
     * @param clw lease to removed
     * @return false if the lease has already been removed, or 
     * if logically the lease is no longer in the set (e.g. its
     * membership expiration has been reached)
     */
    boolean remove(ClientLeaseWrapper clw) {
	if (!removeInternal(clw)) {
	    // if we are here the lease must have already been 
	    // removed by some other thread, don't bother logging
	    return false;
	}

	// Even if the membership expiration was expired we still
	// want to update the log
	final Object u = new RemoveClientLease(this, clw);
	store.update(u);
	normServerBaseImpl.updateLeaseCount(-1);

	return (clw.getMembershipExpiration() > System.currentTimeMillis());
    }

    /**
     * Destroy a lease set
     * @return a <code>Set</code> with all of the sets WrappedClientLeases
     */
    Set destroy() {
	setExpiration(-1);
	final Object u = new CancelLeaseSet(getUuid());
	store.update(u);
	normServerBaseImpl.updateLeaseCount(-leases.size());
	return leases;
    }

    /**
     * Return an array of leases in marshalled form.  For each lease we have
     * in unmarshalled form serialize using the duration format, for each
     * lease we can't unmarshal just use the MarshalledInstance we have on
     * hand.  If the set is empty return null.
     */
    MarshalledInstance[] getLeases() {
	final long now = System.currentTimeMillis();
	final Iterator i = leases.iterator();
	final List l = new ArrayList(leases.size());

	while (i.hasNext()) {
	    final ClientLeaseWrapper clw = (ClientLeaseWrapper) i.next();

	    // Check to make sure the leases membership expiration has not 
	    // passed
	    if (now > clw.getMembershipExpiration())
		continue;

	    l.add(clw.getMarshalledClientLease());
	}

	if (l.isEmpty()) {
	    return null;
	} else {
	    return (MarshalledInstance[]) l.toArray(
		new MarshalledInstance[l.size()]);
	}
    }

    /** 
     * Set/update/clear the expiration warning listener.
     * @param listener the new listener
     * @param minWarning how long before the lease on the set expires should
     *        the event be sent
     * @param handback the new handback
     * @return if <code>listener</code> is non-<code>null</code> return
     * an <code>EventRegistration</code> otherwise return <code>null</code>
     * @throws IOException if listener cannot be serialized 
     */
    EventRegistration setExpirationWarningListener(
	RemoteEventListener listener, 
	long                minWarning,
	MarshalledObject    handback)
        throws IOException     					       
    {
	this.minWarning = minWarning;
	warningEventType.setListener(listener, handback);

	final Object u = new WarningEventRegistration(this);
	store.update(u);

	if (listener == null) 
	    return null;
	
	final SetProxy proxy = newSetProxy();

	return new EventRegistration(
	    warningEventType.getEventID(),
	    proxy, 
	    proxy.getRenewalSetLease(), 
	    warningEventType.getLastSequenceNumber());
    }

    /**
     * Set/update/clear the renewal failure listener
     * @param listener the new listener
     * @param handback the new handback
     * @return if <code>listener</code> is non-<code>null</code> return
     * an <code>EventRegistration</code> otherwise return <code>null</code>
     * @throws IOException if listener can not be serialized 
     */
    EventRegistration setRenewalFailureListener(
	RemoteEventListener listener, 
	MarshalledObject    handback)
        throws IOException     					       
    {
	failureEventType.setListener(listener, handback);

	final Object u = new FailureEventRegistration(this);
	store.update(u);

	if (listener == null) 
	    return null;

	final SetProxy proxy = newSetProxy();

	return new EventRegistration(
	    failureEventType.getEventID(),
	    proxy, 
	    proxy.getRenewalSetLease(), 
	    failureEventType.getLastSequenceNumber());
    }

    /**
     * Handle failures to renew a lease by removing the lease from the set
     * and if needed schedule sending an event.
     * @param clw the wrapped client lease for the lease that could not
     *            be removed
     */
    void renewalFailure(ClientLeaseWrapper clw) {
	if (!removeInternal(clw)) {
	    // If we are here the lease must have already been
	    // removed by some other thread, don't bother logging or
	    // sending an event.
	    return;
	}

	Object u;
	EventFactory factory;
	long seqNum;

	try {
	    factory = clw.newFailureFactory(newSetProxy());
	    seqNum = failureEventType.sendEvent(factory);	
	} catch (IOException e) {
	    // We need to log the event even if we can't send it.
	    // Update the sequence number since an event has
	    // occurred. Note, we only get here if creating the
	    // factory fails (failureEventType.sendEvent() does not
	    // throw IOException) so we there is no danger of
	    // incrementing the sequence number twice
	    // $$$ is this the right thing to do?
	    seqNum = failureEventType.bumpSequenceNumber();
	}

	u = new RenewalFailure(this, clw, seqNum);
	store.update(u);
    }

    /**
     * Send an expiration warning event for this set
     */
    void sendWarningEvent() {
	warningSeqNum = warningEventType.sendEvent(new WarningFactory(this));
    }

    /**
     * Nested class that implements <code>EventFactory</code> that
     * generates <code>ExpirationWarningEvent</code>s.
     */
    private class WarningFactory implements EventFactory {
	private static final long serialVersionUID = 1L;

	/** The source for the event */
	final private SetProxy proxy;

	/**
	 * Create a new WarningFactory 
	 * @param set The set generating this event
	 */
	WarningFactory(LeaseSet set) {
	    proxy = set.newSetProxy();
	}

	// Inherit java doc from super type
	public RemoteEvent createEvent(long             eventID, 
				       long             seqNum, 
				       MarshalledObject handback) 
	{
	    return new ExpirationWarningEvent(proxy, seqNum, handback);
	}	
    }

    /**
     * Create a new SetProxy for this set that has a lease with the
     * current expiration     
     */
    private SetProxy newSetProxy() {
	return normServerBaseImpl.newSetProxy(this); 
    }


    /**
     * Return true if there is a non-<code>null</code> listener registered
     * for the expiration warning event.
     * <p>
     * Note, this method assumes the current thread owns the set's lock
     */
    boolean haveWarningRegistration() {
	return warningEventType.haveListener();
    }

    /**
     * Return the absolute time when a expiration warning should be sent.
     */
    long getWarningTime() {
	return expiration - minWarning;
    }

    /**
     * Log the renewal of a client lease.
     * @param clw the wrapper for the client lease that was renewed
     */
    void logRenewal(ClientLeaseWrapper clw) {
	if (!leases.contains(clw)) {
	    // Some other thread must have removed this lease from
	    // the set after renewal, don't bother logging change
	    return;
	}

	final Object u = new UpdateClientLease(this, clw);
	store.update(u);
    }

    // Methods need to meet contract of LeasedResource	
    // Inherit java doc from super type
    public void setExpiration(long newExpiration) {
	expiration = newExpiration;
	// Update the 2nd copy
	expiration2.set(expiration);
    }

    // Inherit java doc from super type
    public long getExpiration() {
	return expiration;
    }

    /**
     * This method is used by the client lease renewal threads to make
     * sure that the set associated with the lease they are renewing is 
     * non-expired.  Note, the method check expiration2, not expiration
     * so the renewal thread does not need to block on the set's lock.
     * @param now the current time in milliseconds since the beginning of 
     *            the epoch
     */
    boolean ensureCurrent(long now) {
	return expiration2.ensureCurrent(now);
    }
 
    // Inherit java doc from super type
    public Uuid getCookie() {
	return ID;
    }

    /**
     * Return the <code>Uuid</code> for this set.  */
    Uuid getUuid() {
	return ID;
    }

    /**
     * If the passed registrationNumber number matches the 
     * current registrationNumber for the passed event 
     * clear the current registration and persist the change
     */
    void definiteException(EventType type, RemoteEvent ev,
			   long registrationNumber) 
    {
	final boolean changed = 
	    type.clearListenerIfSequenceMatch(registrationNumber);
	if (changed) {
	    // Need to log the change
	    Object u;
	    if (ev instanceof ExpirationWarningEvent) {
		u = new WarningEventRegistration(LeaseSet.this);
	    } else {
		u = new FailureEventRegistration(LeaseSet.this);
	    }
	    store.update(u);
	}
    }

    /**
     * Returns whether to isolate renewal sets or batch leases across sets for
     * all lease renewal sets associated with this set's service.
     */
    protected boolean isolateSets() {
	return normServerBaseImpl.isolateSets();
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "LeaseSet" + ID;
    }

    // Inner classes
    /**
     * Utility class that holds and guards the second copy of our expiration 
     * time.
     */
    private static class ExpirationTime {
	private static final long serialVersionUID = 1L;

	/** 
	 * The expiration time in milliseconds since the beginning of the 
	 * epoch 
	 */
	private long expirationTime;

	/** Simple constructor */
	private ExpirationTime(long initVal) {
	    expirationTime = initVal;
	}

	/** Update the current expiration time */
	private synchronized void set(long newTime) {
	    expirationTime = newTime;
	}

	/** 
	 * Return true if expiration time has not been reached 
	 * @param now the current time in milliseconds since the beginning of 
	 *            the epoch
	 */
	private synchronized boolean ensureCurrent(long now) {
	    return now <= expirationTime;
	}
    }
	
    // All the inner classes we use to log changes to a lease set

    /**
     * Class used to log changes to the set expiration time.  
     */
    static class ChangeSetExpiration extends LeaseSetOperation {
	private static final long serialVersionUID = 1L;

	/**
	 * Updated expiration time
	 * @serial
	 */
	private long expiration;

	/**
	 * Simple constructor
	 * @param set that changed
	 * @param expiration the new expiration time
	 */
	ChangeSetExpiration(LeaseSet set, long expiration) {
	    super(set.getUuid());
	    this.expiration = expiration;
	}
	
	// Inherit java doc from super type
	void apply(LeaseSet set) {
	    set.setExpiration(expiration);
	}
    }

    /**
     * Class used to log adding or updating a client lease to the set
     */
    private static class UpdateClientLease extends LeaseSetOperation {
	private static final long serialVersionUID = 1L;

	/**
	 * Wrapped version of client lease
	 * @serial
	 */
	private ClientLeaseWrapper clw;

	/**
	 * Simple constructor
	 * @param set that changed
	 * @param clw Wrapped client lease
	 */
	private UpdateClientLease(LeaseSet set, ClientLeaseWrapper clw) {
	    super(set.getUuid());
	    this.clw = clw;
	}

	// Inherit java doc from super type
	void apply(LeaseSet set) {
	    set.replace(clw);
	}
    }

    /**
     * Class used to log the removal of a client lease from the set
     */
    private static class RemoveClientLease extends LeaseSetOperation {
	private static final long serialVersionUID = 1L;

	/**
	 * Client lease to be removed
	 * @serial
	 */
	private ClientLeaseWrapper clw;

	/**
	 * Simple constructor
	 * @param set that changed
	 * @param clw Wrapped client lease
	 */
	private RemoveClientLease(LeaseSet set, ClientLeaseWrapper clw) {
	    super(set.getUuid());
	    this.clw = clw;
	}

	
	// Inherit java doc from super type
	void apply(LeaseSet set) {
	    set.leases.remove(clw);
	}
    }

    /**
     * Class used to log a renewal failure
     */
    private static class RenewalFailure extends RemoveClientLease {
	private static final long serialVersionUID = 1L;

	/**
	 * Event ID of the corresponding renewal failure event (if any)
	 * @serial
	 */
	private long evID;

	/**
	 * Simple constructor
	 * @param set that changed
	 * @param clw Wrapped client lease
	 * @param evID event ID of the renewal event that was sent
	 *             to mark this renewal failure
	 */
	private RenewalFailure(LeaseSet set, ClientLeaseWrapper clw,
			       long evID)
	{
	    super(set, clw);
	    this.evID = evID;
	}
	
	// Inherit java doc from super type
	void apply(LeaseSet set) {
	    super.apply(set);
	    set.failureEventType.setLastSequenceNumber(evID);
	}
    }

    /**
     * Class used to log changes to warning event registrations
     */
    private static class WarningEventRegistration extends LeaseSetOperation {
	private static final long serialVersionUID = 1L;

	/**
	 * Warning time associated with warning event registration
	 * @serial
	 */
	private long warningTime;

	/**
	 * EventType object that resulted from registration change
	 * @serial
	 */
	private EventType registration;

	/**
	 * Simple constructor
	 * @param set that changed
	 */
	private WarningEventRegistration(LeaseSet set) {
	    super(set.getUuid());
	    warningTime = set.minWarning;
	    registration = set.warningEventType;
	}

	
	// Inherit java doc from super type
	void apply(LeaseSet set) throws StoreException {
	    set.minWarning = warningTime;
	    set.warningEventType = registration;
	}
    }

    /**
     * Class used to log changes to failure event registrations
     */
    private static class FailureEventRegistration extends LeaseSetOperation {
	private static final long serialVersionUID = 1L;

	/**
	 * EventType object that resulted from registration change
	 * @serial
	 */
	private EventType registration;

	/**
	 * Simple constructor
	 * @param set that changed
	 */
	private FailureEventRegistration(LeaseSet set) {
	    super(set.getUuid());
	    registration = set.failureEventType;
	}

	
	// Inherit java doc from super type
	void apply(LeaseSet set) throws StoreException {
	    set.failureEventType = registration;
	}
    }
}
