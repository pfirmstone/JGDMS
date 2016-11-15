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
package net.jini.space;

import java.rmi.MarshalledObject;
import java.rmi.MarshalException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.entry.UnusableEntriesException;

/**
 * The <code>JavaSpace05</code> interface extends the {@link
 * JavaSpace} interface to provide methods that allow clients to
 * perform batch operations on the space (which are generally more
 * efficient than performing an equivalent set of singleton
 * operations) and to more easily develop applications where a
 * given {@link Entry} needs to be read by multiple clients.
 * Implementations of the <code>JavaSpace</code> interface are not
 * required to implement this interface.<p>
 *
 * Unless otherwise noted, the effects of any invocation of a
 * method defined by this interface must be visible to any
 * operation on the space that is started after the invocation
 * returns normally. Note, the effects of a method invocation that
 * throws a {@link RemoteException} are not necessarily visible
 * when the exception is thrown. <p>
 *
 * All of the methods of this interface take one or more {@link
 * Collection}s as arguments. Each such <code>Collection</code>
 * must be treated as immutable by implementations and must not be
 * changed by the client during the course of any method
 * invocation to which they have been passed.<p>
 *
 * This interface is not a remote interface. Each implementation of
 * this interface exports a proxy object that implements this
 * interface local to the client.  Each method of the interface
 * takes as one of its arguments a <code>Collection</code> of
 * <code>Entry</code> instances. The entries themselves must be
 * serialized in accordance with the <a
 * href=http://www.jini.org/standards/index.html>Jini Entry
 * Specification</a> and will not be altered by the
 * call. Typically, the <code>Collection</code> holding the entries
 * will not be serialized at all. If one of these entries can't be
 * serialized, a {@link MarshalException} will be thrown. Aside
 * from the handling of these <code>Collection</code> of
 * <code>Entry</code> parameters, all methods defined by this
 * interface otherwise obey normal Java(TM) Remote Method
 * Invocation remote interface semantics.
 *
 * @see <a href=http://www.jini.org/standards/index.html>
 *      JavaSpaces Service Specification</a>
 * @see <a href=http://www.jini.org/standards/index.html>
 *      Jini Entry Specification</a>
 * @since 2.1
 */
public interface JavaSpace05 extends JavaSpace {
    /**
     * This method provides an overload of the {@link
     * JavaSpace#write JavaSpace.write} method that allows new
     * copies of multiple {@link Entry} instances to be stored in
     * the space using a single call. The client may specify a
     * {@link Transaction} for the operation to be performed
     * under. Each <code>Entry</code> to be stored in the space
     * has a separate requested initial lease duration. <p>
     *
     * The effect on the space of an invocation of this method
     * successfully storing an <code>Entry</code> is the same as if
     * the <code>Entry</code> had been successfully stored by a
     * call to the singleton form of <code>write</code> under
     * <code>txn</code> with the given requested initial lease
     * duration. This method returns the proxies to the leases for
     * each newly stored <code>Entry</code> by returning a {@link
     * List} of {@link Lease} instances. The <em>i</em> th element
     * of the returned <code>List</code> will be a proxy for the
     * lease on the <code>Entry</code> created from the <em>i</em>
     * th element of <code>entries</code>. <p>
     *
     * If an invocation of this method returns normally, then a new
     * copy of each element of <code>entries</code> must have been
     * stored in the space. A new copy of each element will be
     * stored even if there are duplicates (either in terms of
     * object identity or of entry equivalence) in
     * <code>entries</code>. <p>
     * 
     * The order in which the entries stored by an invocation of
     * this method will become visible in the space is unspecified,
     * and different observers may see the entries become visible
     * in different orders.<p>
     *
     * If a {@link TransactionException}, {@link
     * SecurityException}, {@link IllegalArgumentException}, or
     * {@link NullPointerException} is thrown, no entries will
     * have been added to the space by this operation. If a {@link
     * RemoteException} is thrown, either new copies of all of the
     * elements of <code>entries</code> will have been stored or
     * no entries will have been stored; that is, in the case of a
     * <code>RemoteException</code>, the storing of new entries in
     * the space will either fail or succeed as a unit. <p>
     *
     * @param entries a {@link List} of {@link Entry} instances to
     *                be written to the space
     * @param txn the {@link Transaction} this operation should be
     *                performed under, may be <code>null</code>
     * @param leaseDurations a <code>List</code> of
     *                {@link Long}s representing the
     *                requested initial lease durations
     * @return a <code>List</code> of {@link Lease} instances, one
     *         for each element of <code>entries</code>, may be
     *         immutable. The space will not keep a reference to
     *         the result
     * @throws TransactionException if <code>txn</code> is
     *         non-<code>null</code> and is not usable by the
     *         space
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if <code>entries</code> and
     *         <code>leaseDurations</code> are not the same length
     *         or are empty, if any element of <code>entries</code>
     *         is not an instance of <code>Entry</code>, if any
     *         element of <code>leaseDurations</code> is not an
     *         instance of <code>Long</code>, or if any element of
     *         <code>leaseDurations</code> is a negative value
     *         other than {@link Lease#ANY Lease.ANY}
     * @throws NullPointerException if either <code>entries</code>
     *         or <code>leaseDurations</code> is <code>null</code>
     *         or contains a <code>null</code> value
     */
    public List write(List        entries,
		      Transaction txn,
		      List        leaseDurations)
	throws TransactionException, RemoteException;

    /**
     * This method provides an overload of the {@link
     * JavaSpace#take JavaSpace.take} method that attempts to
     * remove, optionally under a {@link Transaction}, and return
     * one or more entries from the space. Each {@link Entry}
     * taken will match one or more elements of the passed {@link
     * Collection} of templates, and all of the taken entries will
     * be visible to the passed <code>Transaction</code>. If there
     * are initially no matches in the space that are visible to
     * the passed <code>Transaction</code>, an invocation of this
     * method will block for up to a specified timeout for one or
     * more matches to appear. <p>
     *
     * The effect on the space of an invocation of this method
     * successfully taking an <code>Entry</code> will be the same
     * as if the <code>Entry</code> had been taken using the
     * singleton version of this method and passing
     * <code>txn</code> as the <code>Transaction</code>. <p>
     *
     * The <code>tmpls</code> parameter must be a
     * <code>Collection</code> of <code>Entry</code> instances to
     * be used as templates. All of the entries taken must match
     * one or more of these templates. The <code>tmpls</code>
     * parameter may contain <code>null</code> values and may
     * contain duplicates. An <code>Entry</code> is said to be
     * <em>available</em> to an invocation of this method if the
     * <code>Entry</code> could have been returned by an
     * invocation of the singleton <code>take</code> method using
     * <code>txn</code>. <p>
     *
     * If the method succeeds, a non-<code>null</code>
     * <code>Collection</code> will be returned. The
     * <code>Collection</code> will contain a copy of each
     * <code>Entry</code> that was taken. If no entries were taken,
     * the <code>Collection</code> will be empty.  Each
     * <code>Entry</code> taken will be represented by a distinct
     * <code>Entry</code> instance in the returned
     * <code>Collection</code>, even if some of the entries are
     * equivalent to others taken by the operation. There will be
     * no <code>null</code> elements in the returned 
     * <code>Collection</code>.<p>
     *
     * If one or more of the entries taken cannot be unmarshalled
     * in the client, an {@link UnusableEntriesException} is
     * thrown. The exception's {@link
     * UnusableEntriesException#getEntries
     * UnusableEntriesException.getEntries} method will return a
     * <code>Collection</code> with a copy of each
     * <code>Entry</code> that could be unmarshalled. The {@link
     * UnusableEntriesException#getUnusableEntryExceptions
     * UnusableEntriesException.getUnusableEntryExceptions} method
     * will return a <code>Collection</code> with an {@link
     * UnusableEntryException} for each <code>Entry</code> that
     * could not be unmarshalled. Every <code>Entry</code> taken
     * by the invocation will either be represented in the
     * <code>Collection</code> returned by <code>getEntries</code>
     * or in the <code>Collection</code> returned by
     * <code>getUnusableEntryExceptions</code>. <p>
     *
     * If there is at least one matching <code>Entry</code>
     * available in the space, an invocation of this method must
     * take at least one <code>Entry</code>. If more than one
     * matching <code>Entry</code> is available, the invocation may
     * take additional entries. It must not take more than
     * <code>maxEntries</code>, but an implementation may chose to
     * take fewer entries from the space than the maximum available or
     * the maximum allowed by <code>maxEntries</code>.  If for
     * whatever reason, an invocation of this method takes fewer
     * entries than the maximum number of available matching
     * entries, how an implementation selects which entries should be
     * taken by the invocation and which are left in the space is
     * unspecified. How consumption of entries is arbitrated
     * between conflicting queries is also unspecified. <p>
     *
     * If there are initially no matching entries in the space, an
     * invocation of this method should block for up to
     * <code>timeout</code> milliseconds for a match to appear. If
     * one or more matches become available before
     * <code>timeout</code> expires, one or more of the newly
     * available entries should be taken and the method should
     * return without further blocking. If for some reason the
     * invocation can't block for the full timeout and no entries
     * have been taken, the invocation must fail with a {@link
     * RemoteException} or {@link TransactionException} as
     * appropriate. <p>
     *
     * If an invocation of this method removes (or locks) more than
     * one <code>Entry</code>, the order in which the removal (or
     * locking) occurs is undefined, and different observers may see
     * the removal or locking of the entries in different
     * orders. <p>
     *
     * If a <code>TransactionException</code>, {@link
     * SecurityException}, {@link IllegalArgumentException}, or
     * {@link NullPointerException} is thrown, no entries will
     * have been taken. If a <code>RemoteException</code> is
     * thrown, up to <code>maxEntries</code> may have been taken
     * by this operation. <p>
     *
     * @param tmpls a {@link Collection} of {@link Entry}
     *              instances, each representing a template. All
     *              of the entries taken by an invocation of this
     *              method will match one or more elements of
     *              <code>tmpls</code>
     * @param txn the {@link Transaction} this operation should be
     *              performed under, may be <code>null</code>
     * @param timeout if there are initially no available
     *              matches in the space, the maximum number of
     *              milliseconds to block waiting for a match to
     *              become available
     * @param maxEntries the maximum number of entries that may be
     *              taken by this method
     * @return a <code>Collection</code> that contains a copy of each
     *         <code>Entry</code> taken from the space by this
     *         method. The space will not keep a reference to
     *         this <code>Collection</code>. May be immutable
     * @throws UnusableEntriesException if one or more of the
     *         entries taken can't be unmarshalled in the client
     * @throws TransactionException if <code>txn</code> is
     *         non-<code>null</code> and is not usable by the
     *         space.
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if any non-<code>null</code>
     *         element of <code>tmpls</code> is not an instance of
     *         <code>Entry</code>, if <code>tmpls</code> is empty,
     *         if <code>timeout</code> is negative, or if
     *         <code>maxEntries</code> is non-positive
     * @throws NullPointerException if <code>tmpls</code> is null
     */
    public Collection take(Collection  tmpls, 
			   Transaction txn, 
			   long        timeout, 
			   long        maxEntries)
	throws UnusableEntriesException, TransactionException,
	       RemoteException;

    /**
     * Creates a {@linkplain MatchSet match set} that can be used to
     * exhaustively read through all of the matching entries in
     * the space that are visible to the passed {@link
     * Transaction} and remain visible for the lifetime of the
     * match set. May also yield additional entries that match but
     * are only visible for part of the lifetime of the match
     * set. <p>
     *
     * The <code>tmpls</code> parameter must be a {@link
     * Collection} of {@link Entry} instances to be used as
     * templates. All of the entries placed in the match set will
     * match one or more of these templates. <code>tmpls</code> may
     * contain <code>null</code> values and may contain
     * duplicates. An <code>Entry</code> is said to be
     * <em>visible</em> to an invocation of this method if the
     * <code>Entry</code> could have been returned by a singleton
     * {@link JavaSpace#read JavaSpace.read} using the same
     * transaction. <p>
     *
     * The resulting match set must initially contain all of the
     * visible matching entries in the space. During the lifetime
     * of the match set an <code>Entry</code> may be, but is not
     * required to be, added to the match set if it becomes
     * visible. If the match set becomes empty, no more entries can
     * be added and the match set enters the {@linkplain MatchSet
     * exhausted} state.<p>
     * 
     * Normally there are three conditions under which an
     * <code>Entry</code> might be removed from the match set:
     * 
     * <ul>
     *
     * <li> Any <code>Entry</code> yielded by an invocation of the
     * {@link MatchSet#next MatchSet.next} method on the match
     * set (either as the return value of a successful call or
     * embedded in an {@link UnusableEntryException}) must be
     * removed from the match set.
     *
     * <li> Any <code>Entry</code> that remains in the match set
     * after <code>maxEntries</code> entries are yielded by
     * <code>next</code> invocations must be removed from the
     * match set. In such a case, the criteria used to select which
     * entries are yielded by <code>next</code> calls and which
     * get removed from the set at the end is unspecified.
     * 
     * <li> Any <code>Entry</code> that during the lifetime of the
     * match set becomes invisible may at the discretion of the
     * implementation be removed from the match set.
     *
     * </ul>
     * <p>
     *
     * An implementation may decide to remove an <code>Entry</code>
     * from the set for other reasons.  If it does so, however, it
     * must {@linkplain MatchSet invalidate} the set.<p>
     *
     * If <code>txn</code> is non-<code>null</code> and still
     * active, any <code>Entry</code> removed from the match set by
     * a <code>next</code> call must be locked as if it had been
     * returned by a read operation using <code>txn</code>. An
     * implementation may establish the read lock on the
     * <code>Entry</code> any time between when the
     * <code>Entry</code> is added to the match set and when the
     * <code>Entry</code> is removed from the match set by an
     * invocation of <code>next</code>. These read locks are not
     * released when the match set reaches either the exhausted
     * state or the invalidated state. If from the space's
     * perspective the <code>txn</code> leaves the active state,
     * the space must remove from the match set any entries in the
     * match set that have not yet been read locked. This may
     * require the match set to be invalidated. <p>
     * 
     * If the match set is leased and <code>leaseDuration</code>
     * is positive, the initial duration of the lease must be less
     * than or equal to <code>leaseDuration</code>. If
     * <code>leaseDuration</code> is {@link Lease#ANY Lease.ANY},
     * the initial duration of the lease can be any positive value
     * desired by the implementation. <p>
     *
     * If there are {@linkplain net.jini.core.constraint remote
     * method constraints} associated with an invocation of this
     * method, any remote communications performed by or on behalf
     * of the match set's <code>next</code> method will be
     * performed in compliance with these constraints, not with the
     * constraints (if any) associated with <code>next</code>. <p>
     *
     * @param tmpls a {@link Collection} of {@link Entry}
     *              instances, each representing a template. All
     *              the entries added to the resulting match set will
     *              match one or more elements of <code>tmpls</code>
     * @param txn   the {@link Transaction} this operation should be
     *              performed under, may be <code>null</code>
     * @param leaseDuration the requested initial lease time on
     *              the resulting match set
     * @param maxEntries the maximum number of entries to remove
     *              from the set via {@link MatchSet#next MatchSet.next} 
     *              calls
     * @return A proxy to the newly created {@linkplain MatchSet match set}
     * @throws TransactionException if <code>txn</code> is
     *         non-<code>null</code> and is not usable by the
     *         space
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if any non-<code>null</code>
     *         element of <code>tmpls</code> is not an instance of
     *         <code>Entry</code>, if <code>tmpls</code> is empty,
     *         if <code>leaseDuration</code> is neither positive
     *         nor {@link Lease#ANY Lease.ANY}, or if
     *         <code>maxEntries</code> is non-positive
     * @throws NullPointerException if <code>tmpls</code> is
     *         <code>null</code> 
     */
    public MatchSet contents(Collection  tmpls, 
			     Transaction txn,
			     long        leaseDuration, 
			     long        maxEntries)
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
     * JavaSpace#take JavaSpace.take} using <code>txn</code> to a
     * state where it could be returned. An <code>Entry</code>
     * makes a transition from <em>invisible to visible</em> when
     * it goes from being in a state where it could not be returned
     * by a {@link JavaSpace#read JavaSpace.read} using
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
     * @param handback the {@link MarshalledObject} to be 
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
				     MarshalledObject    handback)
	throws TransactionException, RemoteException;
}
