/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.space;

import java.rmi.RemoteException;
import java.util.Collection;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.io.MarshalledInstance;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;

/**
 *
 * @author peter
 */
public interface TupleSpace extends JavaSpace05 {
    
    /**
     * When entries are written that match this template notify the
     * given <code>listener</code> with a <code>RemoteEvent</code> that
     * includes the <code>handback</code> object.  Matching is done as
     * for <code>read</code>.
     *
     * @param tmpl      The template used for matching.  Matching is
     *			done against <code>tmpl</code> with <code>null</code>
     *			fields being wildcards ("match anything") other
     *			fields being values ("match exactly on the
     *			serialized form").
     * @param txn	The transaction (if any) under which to work.
     * @param listener  The remote event listener to notify.
     * @param lease  the requested lease time, in milliseconds
     * @param handback  An object to send to the listener as part of the 
     *                  event notification.
     * @return the event registration to the the registrant
     * @throws TransactionException if a transaction error occurs
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if the lease time requested 
     *         is not Lease.ANY and is negative
     * @see #read
     * @see net.jini.core.event.EventRegistration
     */
    EventRegistration
	notify(Entry tmpl, Transaction txn, RemoteEventListener listener,
	       long lease, MarshalledInstance handback)
	throws TransactionException, RemoteException;
    
    /**
     * Register for events triggered when a matching {@link Entry}
     * transitions from unavailable to available.  The resulting
     * events will be instances of the {@link AvailabilityEvent}
     * class and the {@link AvailabilityEvent#getEntry
     * AvailabilityEvent.getEntry} method will return a copy of
     * the <code>Entry</code> whose transition triggered the
     * event.<p>
     *
     * An <code>Entry</code> makes a transition from
     * <em>unavailable to available</em> when it goes from being in
     * a state where it could not be returned by a {@link
     * TupleSpace#take TupleSpace.take} using <code>txn</code> to a
     * state where it could be returned. An <code>Entry</code>
     * makes a transition from <em>invisible to visible</em> when
     * it goes from being in a state where it could not be returned
     * by a {@link TupleSpace#read TupleSpace.read} using
     * <code>txn</code> to a state where it could be
     * returned. Note, any transition from invisible to visible is
     * also a transition from unavailable to available, but an
     * already visible entry can be unavailable and then make a
     * transition from unavailable to available. Because the entry
     * was already visible, this transition would not be a
     * transition from invisible to visible.<p>
     *
     * The <code>tmpls</code> parameter must be a {@link
     * Collection} of <code>Entry</code> instances to be used as
     * templates. Events will be generated when an
     * <code>Entry</code> that matches one or more of these
     * templates makes an appropriate transition. A single
     * transition will generate only one event per registration, in
     * particular the transition of an <code>Entry</code> that
     * matches multiple elements of <code>tmpls</code> must still
     * generate exactly one event for this registration. If a given
     * <code>Entry</code> undergoes multiple applicable transitions
     * while the registration is active, each must generate a
     * separate event.<p>
     * 
     * Events are not generated directly by the transition of
     * matching entries, but instead by an abstract observer set up
     * in the space for each registration. The observer may see the
     * transitions out of order and as a result the order of the
     * events generated for this registration (as determined by the
     * sequence numbers assigned to the events) may be different
     * from the order of the transitions themselves. Additionally,
     * each registration will have its own abstract observer and
     * different observers may see the same sequence of transitions
     * in different orders. As a result, given a set of transitions
     * that trigger events for two different registrations, the
     * order of the events generated for one registration may
     * differ from the order of the events generated for the
     * other. <p>
     *
     * A non-<code>null</code> {@link EventRegistration} object
     * will be returned.  Each registration will be assigned an
     * event ID. The event ID will be unique at least with respect
     * to all other active event registrations for
     * <code>AvailabilityEvent</code>s on this space with a
     * non-equivalent set of templates, a different transaction,
     * and/or a different value for the
     * <code>visibilityOnly</code> flag. The event ID can be
     * obtained by calling the {@link EventRegistration#getID
     * EventRegistration.getID} method on the returned
     * <code>EventRegistration</code>. The returned
     * <code>EventRegistration</code> object's {@link
     * EventRegistration#getSource EventRegistration.getSource}
     * method will return a reference to the space.<p>
     * 
     * Registrations are leased. <code>leaseDurations</code>
     * represents the client's desired initial lease duration.  If
     * <code>leaseDuration</code> is positive, the initial lease
     * duration will be a positive value less than or equal to
     * <code>leaseDuration</code>. If <code>leaseDuration</code> is
     * {@link Lease#ANY Lease.ANY}, the space is free to pick any
     * positive initial lease duration it desires. A proxy for the
     * lease associated with the registration can be obtained by
     * calling the returned <code>EventRegistration</code>'s {@link
     * EventRegistration#getLease EventRegistration.getLease}
     * method.<p>
     *
     * A registration made with a non-<code>null</code> value for
     * <code>txn</code> is implicitly dropped when the space
     * observes <code>txn</code> has left the active state.<p>
     *
     * @param tmpls a {@link Collection} of {@link Entry}
     *              instances, each representing a
     *              template. Events for this registration will be
     *              generated by the transitions of entries
     *              matching one or more elements of
     *              <code>tmpls</code>
     * @param txn   the {@link Transaction} this operation should be
     *              performed under, may be <code>null</code>
     * @param visibilityOnly if <code>true</code>, events will
     *              be generated for this registration only when a
     *              matching <code>Entry</code> transitions from
     *              invisible to visible, otherwise events will be
     *              generated when a matching <code>Entry</code>
     *              makes any transition from unavailable to
     *              available
     * @param listener the object to which events generated for
     *              this registration should be delivered
     * @param leaseDuration the requested initial lease time on
     *              the resulting event registration
     * @param handback the {@link MarshalledInstance} to be 
     *              returned by the {@link
     *              RemoteEvent#getRegistrationObject 
     *              RemoteEvent.getRegistrationObject} method of
     *              the events generated for this registration
     * @return an {@link EventRegistration} object with
     *         information on this registration
     * @throws TransactionException if <code>txn</code> is
     *         non-<code>null</code> and is not usable by the
     *         space
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if any non-<code>null</code>
     *         element of <code>tmpls</code> is not an instance of
     *         <code>Entry</code>, if <code>tmpls</code> is empty,
     *         or if <code>leaseDuration</code> is neither
     *         positive nor {@link Lease#ANY Lease.ANY}
     * @throws NullPointerException if <code>tmpls</code> or
     *         <code>listener</code> is <code>null</code> 
     */
    public EventRegistration 
	registerForAvailabilityEvent(Collection          tmpls, 
				     Transaction         txn,
				     boolean             visibilityOnly,
				     RemoteEventListener listener,
				     long                leaseDuration, 
				     MarshalledInstance  handback)
	throws TransactionException, RemoteException;
    
}
