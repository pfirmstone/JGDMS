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
import java.rmi.RemoteException;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;

/**
 * This interface is implemented by servers that export a
 * JavaSpaces technology service.  The operations in this interface
 * are the public methods that all such spaces support.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see Entry
 */
public interface JavaSpace {
    /**
     * Write a new entry into the space.
     *
     * @param entry  the entry to write
     * @param txn the transaction object, if any, under which to 
     *            perform the write
     * @param lease  the requested lease time, in milliseconds
     * @return a lease for the entry that was written to the space 
     *
     * @throws TransactionException if a transaction error occurs
     * @throws RemoteException if a communication error occurs
     */
    Lease write(Entry entry, Transaction txn, long lease)
	throws TransactionException, RemoteException;

    /**
     * Wait for no time at all.  This is used as a timeout value in
     * various read and take calls.
     *
     * @see #read
     * @see #readIfExists
     * @see #take
     * @see #takeIfExists
     */
    long NO_WAIT = 0;

    /**
     * Read any matching entry from the space, blocking until one exists.
     * Return <code>null</code> if the timeout expires.
     *
     * @param tmpl      The template used for matching.  Matching is
     *			done against <code>tmpl</code> with <code>null</code>
     *			fields being wildcards ("match anything") other
     *			fields being values ("match exactly on the
     *			serialized form").
     * @param txn	The transaction (if any) under which to work.
     * @param timeout	How long the client is willing to wait for a
     *			transactionally proper matching entry.  A
     *			timeout of <code>NO_WAIT</code> means to wait
     *			no time at all; this is equivalent to a wait
     *			of zero.
     *
     * @return a copy of the entry read from the space
     * @throws UnusableEntryException if any serialized field of the entry 
     *         being read cannot be deserialized for any reason 
     * @throws TransactionException if a transaction error occurs
     * @throws InterruptedException if the thread in which the read
     *         occurs is interrupted
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if a negative timeout value is used
     */
    Entry read(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException, 
	       InterruptedException, RemoteException;

    /**
     * Read any matching entry from the space, returning
     * <code>null</code> if there is currently is none.  Matching and
     * timeouts are done as in <code>read</code>, except that blocking
     * in this call is done only if necessary to wait for transactional
     * state to settle.
     *
     * @param tmpl      The template used for matching.  Matching is
     *			done against <code>tmpl</code> with <code>null</code>
     *			fields being wildcards ("match anything") other
     *			fields being values ("match exactly on the
     *			serialized form").
     * @param txn	The transaction (if any) under which to work.
     * @param timeout	How long the client is willing to wait for a
     *			transactionally proper matching entry.  A
     *			timeout of <code>NO_WAIT</code> means to wait
     *			no time at all; this is equivalent to a wait
     *			of zero.
     *
     * @return a copy of the entry read from the space
     * @throws UnusableEntryException if any serialized field of the entry 
     *         being read cannot be deserialized for any reason 
     * @throws TransactionException if a transaction error occurs
     * @throws InterruptedException if the thread in which the read
     *         occurs is interrupted
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if a negative timeout value is used
     * @see #read
     */
    Entry readIfExists(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException, 
	       InterruptedException, RemoteException;

    /**
     * Take a matching entry from the space, waiting until one exists.
     * Matching is and timeout done as for <code>read</code>.
     *
     * @param tmpl      The template used for matching.  Matching is
     *			done against <code>tmpl</code> with <code>null</code>
     *			fields being wildcards ("match anything") other
     *			fields being values ("match exactly on the
     *			serialized form").
     * @param txn	The transaction (if any) under which to work.
     * @param timeout	How long the client is willing to wait for a
     *			transactionally proper matching entry.  A
     *			timeout of <code>NO_WAIT</code> means to wait
     *			no time at all; this is equivalent to a wait
     *			of zero.
     *
     * @return the entry taken from the space
     * @throws UnusableEntryException if any serialized field of the entry 
     *         being read cannot be deserialized for any reason 
     * @throws TransactionException if a transaction error occurs
     * @throws InterruptedException if the thread in which the take
     *         occurs is interrupted
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if a negative timeout value is used
     * @see #read
     */
    Entry take(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException, 
	       InterruptedException, RemoteException;

    /**
     * Take a matching entry from the space, returning
     * <code>null</code> if there is currently is none.  Matching is
     * and timeout done as for <code>read</code>, except that blocking
     * in this call is done only if necessary to wait for transactional
     * state to settle.
     *
     * @param tmpl      The template used for matching.  Matching is
     *			done against <code>tmpl</code> with <code>null</code>
     *			fields being wildcards ("match anything") other
     *			fields being values ("match exactly on the
     *			serialized form").
     * @param txn	The transaction (if any) under which to work.
     * @param timeout	How long the client is willing to wait for a
     *			transactionally proper matching entry.  A
     *			timeout of <code>NO_WAIT</code> means to wait
     *			no time at all; this is equivalent to a wait
     *			of zero.
     *
     * @return the entry taken from the space
     * @throws UnusableEntryException if any serialized field of the entry 
     *         being read cannot be deserialized for any reason 
     * @throws TransactionException if a transaction error occurs
     * @throws InterruptedException if the thread in which the take
     *         occurs is interrupted
     * @throws RemoteException if a communication error occurs
     * @throws IllegalArgumentException if a negative timeout value is used
     * @see #read
     */
    Entry takeIfExists(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException, 
	       InterruptedException, RemoteException;

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
	       long lease, MarshalledObject handback)
	throws TransactionException, RemoteException;

    /**
     * The process of serializing an entry for transmission to a JavaSpaces
     * service will be identical if the same entry is used twice. This is most
     * likely to be an issue with templates that are used repeatedly to search
     * for entries with <code>read</code> or <code>take</code>. The client-side 
     * implementations of <code>read</code> and <code>take</code> cannot
     * reasonably avoid this duplicated effort, since they have no efficient
     * way of checking whether the same template is being used without
     * intervening modification.
     *
     * The <code>snapshot</code> method gives the JavaSpaces service implementor
     * a way to reduce the impact of repeated use of the same entry. Invoking
     * <code>snapshot</code> with an <code>Entry</code> will return another
     * <code>Entry</code> object that contains a <i>snapshot</i> of the
     * original entry. Using the returned snapshot entry is equivalent to
     * using the unmodified original entry in all operations on the same
     * JavaSpaces service. Modifications to the original entry will not
     * affect the snapshot. You can <code>snapshot</code> a <code>null</code>
     * template; <code>snapshot</code> may or may not return null given a
     * <code>null</code> template.
     *
     * The entry returned from <code>snapshot</code> will be guaranteed
     * equivalent to the original unmodified object only when used with
     * the space. Using the snapshot with any other JavaSpaces service
     * will generate an <code>IllegalArgumentException</code> unless the
     * other space can use it because of knowledge about the JavaSpaces
     * service that generated the snapshot. The snapshot will be a different
     * object from the original, may or may not have the same hash code,
     * and <code>equals</code> may or may not return <code>true</code>
     * when invoked with the original object, even if the original
     * object is unmodified.
     *
     * A snapshot is guaranteed to work only within the virtual machine
     * in which it was generated. If a snapshot is passed to another 
     * virtual machine (for example, in a parameter of an RMI call),
     * using it--even with the same JavaSpaces service--may generate
     * an <code>IllegalArgumentException</code>.
     *
     * @param e the entry to take a snapshot of.
     * @return a snapshot of the entry.
     * @throws RemoteException if a communication error occurs
     */
    Entry snapshot(Entry e) throws RemoteException;
}

