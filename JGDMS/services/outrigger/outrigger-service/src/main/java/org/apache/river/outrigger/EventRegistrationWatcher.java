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
package org.apache.river.outrigger;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.security.ProxyPreparer;
import net.jini.space.JavaSpace;
import org.apache.river.landlord.LeasedResource;

/**
 * Subclass of <code>TransitionWatcher</code> for event
 * registrations. Also represents the registration itself.
 */
abstract class EventRegistrationWatcher extends TransitionWatcher 
    implements EventRegistrationRecord
{
    /** 
     * The current expiration time of the registration
     * Protected, but only for use by subclasses.
     */
    volatile long expiration;

    /** 
     * The UUID that identifies this registration 
     * Protected, but only for use by subclasses.
     * Should not be changed.
     */
    Uuid cookie;

    /**
     * The handback associated with this registration (DER-only:
     * always a canonical {@link MarshalledInstance}, never a legacy
     * {@code java.rmi.MarshalledObject} -- JGDMS 4.0.0 withdrew the MO
     * surface end-to-end).
     * Protected, but only for use by subclasses.
     * Should not be changed.
     */
    MarshalledInstance handback;

    /** 
     * The event ID associated with this registration
     * Protected, but only for use by subclasses.
     * Should not be changed.
     */
    long eventID;

    /**
     * The current sequence number.
     */
    private long currentSeqNum = 0;

    /**
     * The server-side CEL filter set gating this registration's deliveries (SOW
     * Part&nbsp;B, unit&nbsp;B3, site&nbsp;E). {@link FilterSet#EMPTY} for an
     * unfiltered registration. Consulted in {@link #process} <em>after</em> the
     * per-registration txn entitlement gate (the subclass {@code isInterested}
     * already established it, and {@code process} runs only for interested
     * watchers), so a client's predicate never runs against an entry it is not
     * entitled to observe (INV-1). {@code volatile} for safe publication to the
     * single {@code OperationJournal} delivery thread.
     *
     * <p><b>Durability note (B3 fail-closed):</b> the filter is <em>not</em>
     * persisted, so a filtered registration is registered in-memory only; the
     * durable {@code log.registerOp} is skipped for a filtered registration, so
     * a restart drops it (the client's lease lapses) rather than resurrecting it
     * <em>unfiltered</em> (which would be a fail-open). Persisting the envelope
     * to restore durability is a follow-up.
     */
    private volatile FilterSet filters = FilterSet.EMPTY;

//    /**
//     * The sequence number of the last event successfully 
//     * delivered.  Protected, but only for use by subclasses.
//     */
//    volatile long lastSeqNumDelivered = -1;

    /**
     * The <code>TemplateHandle</code> associated with this
     * watcher.
     */
    private TemplateHandle owner;

    /**
     * Used during log recovery to create a mostly empty
     * <code>EventRegistrationWatcher</code>.  
     * <p> 
     * Note, we set the time stamp and tie-breaker here instead of
     * getting them from the log. This means they will be inconstant
     * with their value from the last VM we were in, but since they
     * never leak out and events are read-only anyway this should not
     * be a problem (this also allows us to keep the tie-breaker and
     * time stamp in final fields).
     *
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     * @param currentSeqNum Sequence number to start with.
     */
    EventRegistrationWatcher(long timestamp, long startOrdinal, 
			     long currentSeqNum) 
    {
	super(timestamp, startOrdinal);
	this.currentSeqNum = currentSeqNum;
        handback = null;
        cookie = null;
        eventID = 0;
    }

    /**
     * Create a new <code>EventRegistrationWatcher</code>.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     * @param cookie The unique identifier associated
     *        with this watcher. Must not be <code>null</code>.
     * @param handback The handback object that
     *        should be sent along with event
     *        notifications to the the listener.
     * @param eventID The event ID for event type
     *        represented by this object.
     * @throws NullPointerException if the <code>cookie</code>
     *        argument is <code>null</code>.
     */
    EventRegistrationWatcher(long timestamp, long startOrdinal, Uuid cookie,
	MarshalledInstance handback, long eventID)
    {
	super(timestamp, startOrdinal);

	if (cookie == null)
	    throw new NullPointerException("cookie must be non-null");

	this.cookie = cookie;
	this.handback = handback;
	this.eventID = eventID;
    }

    /**
     * Process the given transition by queuing up a task with the
     * notifier for event delivery. Assumes the passed entry matches
     * the template in the <code>TemplateHandle</code> associated with
     * this watcher and that <code>isInterested</code> returned
     * <code>true</code>. If <code>remove</code> has been called or
     * the expiration time of this watcher has passed, this call
     * should have no effect. This call may cause the watcher to be
     * removed.
     * @param transition A <code>EntryTransition</code> that
     *              describes the transition and what
     *              entry is transitioning. This method
     *              will assume that <code>transition.getHandle</code>
     *              returns a non-null value.
     * @param now   An estimate of the current time (not the time
     *              when the event occured).
     * @throws NullPointerException if <code>transition</code> is 
     *         <code>null</code>.  
     */
    void process(EntryTransition transition, long now) {
	boolean doneFor = false;

	// lock before checking the time and so we can update currentSeqNum
	synchronized (this) {
	    if (owner == null)
		return; // Must have been removed

	    if (now > expiration) {
		doneFor = true;
	    } else {
		/* B3 (site E): process() runs only for an interested watcher, so the
		 * subclass's txn entitlement gate already holds (INV-1). If a CEL
		 * filter is in force, evaluate it against the transitioning entry
		 * here, BEFORE consuming a sequence number or enqueueing delivery --
		 * predicate-false / fail-closed => no delivery to this registrant and
		 * no seqnum consumed. FilterEval is wrapped catch-everything, so an
		 * escaping fault can never kill this (the single journal) thread. */
		final FilterSet f = filters;
		if (f.isEmpty()
			|| FilterEval.matches(f, transition.getHandle().rep())) {
		    currentSeqNum++;
		    owner.getServer().enqueueDelivery(
			    new BasicEventSender(currentSeqNum, eventID, handback));
		}
	    }
	}

	if (doneFor)
	    cancel();
    }


    /**
     * Return the remote listener associated with this 
     * <code>EventRegistrationWatcher</code>. Optionally
     * prepare the listener if it has been recovered from
     * the store and not yet re-prepared.
     * @return the remote listener associated with this 
     * <code>EventRegistrationWatcher</code>. 
     * @throws IOException if the listener can not
     *         be unmarshalled. May throw {@link RemoteException}
     *         if the call to the preparer does
     * @throws ClassNotFoundException if the listener
     *         needs to be unmarshalled and a necessary
     *         class can not be found.
     * @throws SecurityException if the <code>prepareProxy</code>
     *         call does.
     */
    abstract RemoteEventListener getListener(ProxyPreparer preparer)
	throws ClassNotFoundException, IOException;

    /**
     * Associate a <code>TemplateHandle</code> with this object.  May
     * only be called once on any given
     * <code>EventRegistrationWatcher</code> instance.
     *
     * @param h The <code>TemplateHandle</code> associated
     *          with this watcher.
     * @return <code>true</code> if the handle was succfully added,
     *         and <code>false</code> if the watcher has already
     *         been removed.
     * @throws NullPointerException if <code>h</code> is 
     *        <code>null</code> 
     */
    boolean addTemplateHandle(TemplateHandle h) {
	if (h == null) 
	    throw 
		new NullPointerException("TemplateHandle must be non-null");
        synchronized (this){
            if (owner != null)
                throw new AssertionError("Can only call addTemplateHandle once");

            owner = h;
        }
	return true;
    }

    /**
     * Set the expiration time of this object.  This method may be
     * called before <code>setTemplateHandle</code> is called.
     * Assumes locking is handled by the caller.
     * @param newExpiration The expiration time.
     */
    public synchronized void setExpiration(long newExpiration) {
	expiration = newExpiration;
    }

    public long getExpiration() {
	return expiration;
    }

    /**
     * Install the CEL {@link FilterSet} gating this registration's deliveries
     * (B3, site&nbsp;E). Called once at registration, before the watcher is made
     * visible to the journal. {@code null}/EMPTY leaves it unfiltered.
     */
    void setFilters(FilterSet filters) {
	this.filters = (filters == null) ? FilterSet.EMPTY : filters;
    }

    /** @return the CEL {@link FilterSet} gating this registration ({@link FilterSet#EMPTY} if unfiltered). */
    FilterSet filters() {
	return filters;
    }

    /**
     * Get the unique identifier associated with this object.  This
     * method may be called before <code>setTemplateHandle</code> is
     * called. 
     * @return The unique identifier associated with this
     * watcher.
     */
    public synchronized Uuid getCookie() {
	return cookie;
    }

    /**
     * Overridden by subclasses if there is any cleanup work they need
     * to do as part of <code>cancel</code> or
     * <code>removeIfExpired</code>. Called after releasing the lock
     * on <code>this</code>.  Will be called at most once.  
     * @param owner A reference to the owner.
     * @param expired <code>true</code> if being called from 
     *        <code>removeIfExpired</code> and false otherwise. 
     */
    void cleanup(TemplateHandle owner, boolean expired) 
    {}

    /**
     * The heavy lifting of removing ourselves.
     * @param now The current time (or a bit earlier).
     * @param doIt If <code>true</code> ignore
     *             <code>now</code> and just remove <code>this</code>
     *             object.
     * @return <code>true</code> if this call removed
     *         <code>this</code> object, <code>false</code> if
     *         it had already been done. Should be ignored if 
     *         <code>doIt</code> is <code>false</code>.
     */
    private boolean doRemove(long now, boolean doIt) {
	final TemplateHandle owner;
	synchronized (this) {
	    if (this.owner == null)
		return false; // already removed

	    // Is this a force, or past our expiration?
	    if (!doIt && (now < expiration))
		return false; // don't remove, not our time

	    owner = this.owner;
	    expiration = Long.MIN_VALUE; //Make sure no one tries to renew us
	    this.owner = null;       //We might stick around don't hold owner
	}

	cleanup(owner, !doIt);
	owner.getServer().removeEventRegistration(this);	    
	owner.removeTransitionWatcher(this);	
	return true; // we did the deed
    }

    void removeIfExpired(long now) {
	doRemove(now, false);
    }

    public boolean cancel() {
	return doRemove(0, true);
    }

    /**
     * Common implementation of <code>EventSender</code>.
     */
    private class BasicEventSender implements EventSender {
        
        private final long seqNum;
        private final long eventID;
        private final MarshalledInstance handback;
        
        BasicEventSender(long seqNum, long eventID, MarshalledInstance handback){
            this.seqNum = seqNum;
            this.eventID = eventID;
            this.handback = handback;
        }
        
	public void sendEvent(JavaSpace source, long now, ProxyPreparer preparer)
	    throws UnknownEventException, IOException, ClassNotFoundException
	{
            // Forget all the tricky crap, just send it.
//	    boolean doneFor = false;
//	    long seqNum = -1;
//
//	    synchronized (EventRegistrationWatcher.this) {
//		if (owner == null)
//		    return; // Our watcher must have been removed, we're done
//
//		if (getExpiration() < now) {
//		    doneFor = true; // Our watcher is expired, remove it
//		} else if (currentSeqNum <= lastSeqNumDelivered) {
//		    return; // Someone already sent our event, we're done
//		} else {
//		    // We need to send an event!
//		    seqNum = currentSeqNum;
//		}
//	    
//
//                // cancel is a synchronized method with the same lock.
//                if (doneFor) {
//                    cancel();
//                    return;
//                }
//
//                /* The only way to get here is through a path that sets
//                 * seqNum to the non-initial values
//                 */
//                assert seqNum != -1;
//
//
//
//                // success!, update lastSeqNumDelivered, but don't go backward
//	    
//		if (seqNum > lastSeqNumDelivered)
//		    lastSeqNumDelivered = seqNum;
//	    }
            
             /* If we are here then we need to send an event (probably
	     * someone could have sent our event between the time
	     * we released the lock and now).
	     */
	    getListener(preparer).notify(
		new RemoteEvent(source, eventID, seqNum, handback));
	}
	
	
	public void cancelRegistration() {
	    cancel();
	}

//	/**
//	 * Return the <code>EventRegistrationWatcher</code> this
//	 * object is part of (exits because
//	 * <code>(BasicEventSender)other).EventRegistrationWatcher.
//	 * this</code> does not work.
//	 */
//	private EventRegistrationWatcher getOwner() {
//	    return EventRegistrationWatcher.this;
//	}

	/**
	 * Run after another event sender if it is for the same
	 * registration. No sense sending the same event twice. Don't
	 * care which one goes first (though the second probably won't
	 * run since <code>lastSeqNumDelivered</code> will probably
	 * equal <code>currentSeqNum</code> when it runs).  
	 */
//	public boolean runAfter(EventSender other) {
//	    if (!(other instanceof BasicEventSender))
//		return false;
//
//	    return EventRegistrationWatcher.this ==
//		((BasicEventSender)other).getOwner();
//	}
    }
}
