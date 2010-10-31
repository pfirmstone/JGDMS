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
package com.sun.jini.outrigger;

import com.sun.jini.landlord.Landlord;

import com.sun.jini.start.ServiceProxyAccessor;

import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.TransactionParticipant;

import net.jini.id.Uuid;
import net.jini.space.InternalSpaceException;

/**
 * This interface is the private wire protocol to the Outrigger
 * implementations of JavaSpaces<sup><font size=-2>TM</font></sup> 
 * technology.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see EntryRep
 */
interface OutriggerServer extends TransactionParticipant, Landlord, 
    OutriggerAdmin, ServiceProxyAccessor, Remote 
{
    /**
     * Marker interface for objects that represent state
     * that the server finds useful to share across sub-queries.
     * @see #read
     * @see #take
     * @see #readIfExists
     * @see #takeIfExists
     */
    interface QueryCookie {
    } 

    /**
     * Write a new entry into the space.
     *
     * @exception TransactionException A transaction error occurred
     */
    long[] write(EntryRep entry, Transaction txn, long lease)
	throws TransactionException, RemoteException;

    /**
     * Find an entry in the space that matches the passed template and
     * is visible to the passed transaction. Depending on the state of
     * the space and the arguments this call may block if no entry can
     * be immediately returned. The proxy can specify the maximum
     * period it is willing to wait for a response using the
     * <code>timeout</code> parameter.  The proxy may choose to
     * breakup a query from the client with a very long timeout into a
     * set of <em>sub-queries</em>. In such cases it may get a
     * <code>QueryCookie</code> as response to the sub-queries, in
     * these cases it should pass the <code>QueryCookie</code> to the
     * next sub-query (if any) associated with the same request from
     * the client.
     * <p>
     * If a match is found it is returned as an <code>EntryRep</code>.
     * If <code>txn</code> is non-<code>null</code> the 
     * entry is read locked by the transaction, this allows
     * other queries to read, but not take the entry. The lock
     * will be released when the transaction is aborted or prepared.
     * <p>
     * If no match is found the call will block for up to the
     * specified timeout for a match to appear. If there
     * is still no match available the call will return a 
     * <code>QueryCookie</code>.
     *     
     * @param tmpl The template that describes the entry being 
     *             searched for. May be <code>null</code> if
     *             any visible entry is acceptable.
     * @param txn  The transaction the operation should be
     *             performed under. Maybe be <code>null</code>.
     *             If non-null and entry is found it
     *             will read locked/removed under this 
     *             transaction.
     * @param timeout The maximum number of milliseconds this
     *             call should block in the server before
     *             returning an answer (this not necessarily
     *             the timeout the client asked for.) A value
     *             of 0 indicates the initial search should
     *             be performed, but if no match can be found
     *             <code>null</code> or a <code>QueryCookie</code>
     *             (as appropriate) should be returned immediately.
     * @param cookie If this call is a continuation of 
     *         an earlier query, the cookie from the 
     *         last sub-query.
     * @throws RemoteException if a network failure occurs.
     * @throws TransactionException if there is a problem
     *         with the specified transaction such as 
     *         it can not be joined, or leaves the active
     *         state before the call is complete.
     * @throws InterruptedException if the thread in the server
     *         is interrupted before the query can be completed.
     * @throws SecurityException if the server decides
     *         the caller has insufficient privilege to carry
     *         out the operation.
     * @throws IllegalArgumentException if a negative timeout value is used
     * @throws InternalSpaceException if there is an internal problem
     *         with the server.  
     */
    Object read(EntryRep tmpl, Transaction txn, long timeout,
		QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException;

    /**
     * Find an entry in the space that matches the passed template and
     * is visible to the passed transaction. Depending on the state of
     * the space and the arguments this call may block if no entry can
     * be immediately returned. The proxy can specify the maximum
     * period it is willing to wait for a response using the
     * <code>timeout</code> parameter.  The proxy may choose to
     * breakup a query from the client with a very long timeout into a
     * set of <em>sub-queries</em>. In such cases it may get a
     * <code>QueryCookie</code> as response to the sub-queries, in
     * these cases it should pass the <code>QueryCookie</code> to the
     * next sub-query (if any) associated with the same request from
     * the client.   
     * <p>
     * If a match is found it is returned as an <code>EntryRep</code>.
     * If <code>txn</code> is non-<code>null</code> the 
     * entry is read locked by the transaction, this allows
     * other queries to read, but not take the entry. The lock
     * will be released when the transaction is aborted or prepared.
     * <p>
     * If no match can be initially found the call will block until
     * either the timeout expires or for a detectable period of time
     * there are no entries in the space (visible to the transaction
     * or not) that match the passed template. If at some point
     * there are no matching entries in the space <code>null</code>
     * will be returned. If the timeout expires and there are matching
     * entries in the space but none are visible to the passed
     * transaction a <code>QueryCookie</code> will be returned.
     *     
     * @param tmpl The template that describes the entry being 
     *             searched for. May be <code>null</code> if
     *             any visible entry is acceptable.
     * @param txn   The transaction the operation should be
     *             performed under. Maybe be <code>null</code>.
     *             If non-null and entry is found it
     *             will read locked/removed under this 
     *             transaction.
     * @param timeout The maximum number of milliseconds this
     *             call should block in the server before
     *             returning an answer (this not necessarily
     *             the timeout the client asked for.) A value
     *             of 0 indicates the initial search should
     *             be performed, but if no match can be found
     *             <code>null</code> or a <code>QueryCookie</code>
     *             (as appropriate) should be returned immediately.
     * @param cookie If this call is a continuation of 
     *         an earlier query, the cookie from the 
     *         last sub-query.
     * @throws RemoteException if a network failure occurs.
     * @throws TransactionException if there is a problem
     *         with the specified transaction such as 
     *         it can not be joined, or leaves the active
     *         state before the call is complete.
     * @throws InterruptedException if the thread in the server
     *         is interrupted before the query can be completed.
     * @throws SecurityException if the server decides
     *         the caller has insufficient privilege to carry
     *         out the operation.
     * @throws IllegalArgumentException if a negative timeout value is used
     * @throws InternalSpaceException if there is an internal problem
     *         with the server.  
     */
    Object readIfExists(EntryRep tmpl, Transaction txn, long timeout,
			QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException;


    /**
     * Find and remove an entry in the space that matches the passed
     * template and is visible to the passed transaction. Depending on
     * the state of the space and the arguments this call may block if
     * no entry can be immediately returned. The proxy can specify the
     * maximum period it is willing to wait for a response using the
     * <code>timeout</code> parameter.  The proxy may choose to
     * breakup a query from the client with a very long timeout into a
     * set of <em>sub-queries</em>. In such cases it may get a
     * <code>QueryCookie</code> as response to the sub-queries, in
     * these cases it should pass the <code>QueryCookie</code> to the
     * next sub-query (if any) associated with the same request from
     * the client.
     * <p>
     * If a match is found it is returned as an <code>EntryRep</code>.
     * If <code>txn</code> is <code>null</code> the entry is removed
     * from the space. If <code>txn</code> is non-<code>null</code> the 
     * entry is exclusively locked by the transaction and will be removed
     * from the space if the transaction is committed.
     * <p>
     * If no match is found the call will block for up to the
     * specified timeout for a match to appear. If there
     * is still no match available the call will return a 
     * <code>QueryCookie</code>.
     *     
     * @param tmpl The template that describes the entry being 
     *             searched for. May be <code>null</code> if
     *             any visible entry is acceptable.
     * @param txn   The transaction the operation should be
     *             performed under. Maybe be <code>null</code>.
     *             If non-null and entry is found it
     *             will read locked/removed under this 
     *             transaction.
     * @param timeout The maximum number of milliseconds this
     *             call should block in the server before
     *             returning an answer (this not necessarily
     *             the timeout the client asked for.) A value
     *             of 0 indicates the initial search should
     *             be performed, but if no match can be found
     *             <code>null</code> or a <code>QueryCookie</code>
     *             (as appropriate) should be returned immediately.
     * @param cookie If this call is a continuation of 
     *         an earlier query, the cookie from the 
     *         last sub-query.
     * @throws RemoteException if a network failure occurs.
     * @throws TransactionException if there is a problem
     *         with the specified transaction such as 
     *         it can not be joined, or leaves the active
     *         state before the call is complete.
     * @throws InterruptedException if the thread in the server
     *         is interrupted before the query can be completed.
     * @throws SecurityException if the server decides
     *         the caller has insufficient privilege to carry
     *         out the operation.
     * @throws IllegalArgumentException if a negative timeout value is used
     * @throws InternalSpaceException if there is an internal problem
     *         with the server.  
     */
    Object take(EntryRep tmpl, Transaction txn, long timeout,
		QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException;

    /**
     * Find and remove an entry in the space that matches the passed
     * template and is visible to the passed transaction. Depending on
     * the state of the space and the arguments this call may block if
     * no entry can be immediately returned. The proxy can specify the
     * maximum period it is willing to wait for a response using the
     * <code>timeout</code> parameter.  The proxy may choose to
     * breakup a query from the client with a very long timeout into a
     * set of <em>sub-queries</em>. In such cases it may get a
     * <code>QueryCookie</code> as response to the sub-queries, in
     * these cases it should pass the <code>QueryCookie</code> to the
     * next sub-query (if any) associated with the same request from
     * the client.   
     * <p>
     * If a match is found it is returned as an <code>EntryRep</code>.
     * If <code>txn</code> is <code>null</code> the entry is removed
     * from the space. If <code>txn</code> is non-<code>null</code> the 
     * entry is exclusively locked by the transaction and will be removed
     * from the space if the transaction is committed.
     * <p> 
     * If no match can be initially found the call will block until
     * either the timeout expires or for a detectable period of time
     * there are no entries in the space (visible to the transaction
     * or not) that match the passed template. If at some point there
     * are no matching entries in the space <code>null</code> will be
     * returned. If the timeout expires and there are matching entries
     * in the space but none are visible to the passed transaction a
     * <code>QueryCookie</code> will be returned.
     *     
     * @param tmpl The template that describes the entry being 
     *             searched for. May be <code>null</code> if
     *             any visible entry is acceptable.
     * @param txn   The transaction the operation should be
     *             performed under. Maybe be <code>null</code>.
     *             If non-null and entry is found it
     *             will read locked/removed under this 
     *             transaction.
     * @param timeout The maximum number of milliseconds this
     *             call should block in the server before
     *             returning an answer (this not necessarily
     *             the timeout the client asked for.) A value
     *             of 0 indicates the initial search should
     *             be performed, but if no match can be found
     *             <code>null</code> or a <code>QueryCookie</code>
     *             (as appropriate) should be returned immediately.
     * @param cookie If this call is a continuation of 
     *             an earlier query, the cookie from the 
     *             last sub-query.
     * @throws RemoteException if a network failure occurs.
     * @throws TransactionException if there is a problem
     *         with the specified transaction such as 
     *         it can not be joined, or leaves the active
     *         state before the call is complete.
     * @throws InterruptedException if the thread in the server
     *         is interrupted before the query can be completed.
     * @throws SecurityException if the server decides
     *         the caller has insufficient privilege to carry
     *         out the operation.
     * @throws IllegalArgumentException if a negative timeout value is used
     * @throws InternalSpaceException if there is an internal problem
     *         with the server. 
     */
    Object takeIfExists(EntryRep tmpl, Transaction txn, long timeout,
			QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException;

    /**
     * When entries are written that match this template notify the
     * given <code>listener</code>. Matching is done as for <code>read</code>.
     */
    EventRegistration
	notify(EntryRep tmpl, Transaction txn, RemoteEventListener listener,
	       long lease, MarshalledObject handback)
	throws TransactionException, RemoteException;

    /**
     * Write a set of entires into the space.
     * @return an array of longs that can be used to construct the 
     *         leases on the client side. The array will have 3
     *         elements for each lease, the first will be the 
     *         duration, followed by the high order bits of the
     *         <code>Uuid</code> and then the lower order bits
     *         of the <code>Uuid</code>.
     * @exception TransactionException A transaction error occurred
     */
    long[] write(EntryRep[] entries, Transaction txn, long[] leaseTimes)
        throws TransactionException, RemoteException;

    /**
     * Find and remove up to <code>limit</code> entries in the space
     * that match one or more of the passed templates and are visible
     * to the passed transaction. Depending on the state of the space
     * and the arguments this call may block if no entry can be
     * immediately returned. The proxy can specify the maximum period
     * it is willing to wait for a response using the
     * <code>timeout</code> parameter.  The proxy may choose to
     * breakup a query from the client with a very long timeout into a
     * set of <em>sub-queries</em>. In such cases it may get a
     * <code>QueryCookie</code> as response to the sub-queries, in
     * these cases it should pass the <code>QueryCookie</code> to the
     * next sub-query (if any) associated with the same request from
     * the client.
     * <p>
     * If matchs are found they are returned as in an array of
     * <code>EntryRep</code>.  If <code>txn</code> is
     * <code>null</code> the entries are removed from the space. If
     * <code>txn</code> is non-<code>null</code> the entries are
     * exclusively locked by the transaction and will be removed from
     * the space if the transaction is committed.
     * <p>
     * If there are no matches the call will block for up to the
     * specified timeout for a match to appear. If there is still no
     * match available the call will return a
     * <code>QueryCookie</code>.
     *     
     * @param tmpls The templates that describes the entries being 
     *             searched for
     * @param tr   The transaction the operation should be 
     *             performed under. Maybe be <code>null</code>.
     *             If non-null and entries are found they
     *             will removed under this transaction.
     * @param timeout The maximum number of milliseconds this
     *             call should block in the server before
     *             returning an answer (this not necessarily
     *             the timeout the client asked for.) A value
     *             of 0 indicates the initial search should
     *             be performed, but if no match can be found
     *             a <code>QueryCookie</code> should be
     *             returned immediately.
     * @param limit The maximum number of entries that should be taken
     * @param cookie If this call is a continuation of 
     *         an earlier query, the cookie from the 
     *         last sub-query.
     * @throws RemoteException if a network failure occurs.
     * @throws TransactionException if there is a problem
     *         with the specified transaction such as 
     *         it can not be joined, or leaves the active
     *         state before the call is complete.
     * @throws SecurityException if the server decides
     *         the caller has insufficient privilege to carry
     *         out the operation.
     * @throws IllegalArgumentException if a negative timeout value is used
     *         or if a non-positive limit value is used
     * @throws InternalSpaceException if there is an internal problem
     *         with the server.  
     */
    Object take(EntryRep[] tmpls, Transaction tr, long timeout,
		int limit, QueryCookie cookie)
	throws TransactionException, RemoteException;

    /**
     * When entries that match one or more of the passed templates
     * transition from invisible to visible notify the give
     * <code>listener</code>. Matching is done as for
     * <code>read</code>.
     * @param tmpls the templates that specify what entries should
     *              generate events
     * @param txn   if non-<code>null</code> entries that become
     *              visible to <code>txn</code> should generate events even
     *              if <code>txn</code> is never committed. Registration is 
     *              terminated when <code>txn</code> leaves the active state
     * @param visibilityOnly if <code>true</code>, events will
     *              be generated for this registration only when a
     *              matching <code>Entry</code> transitions from
     *              invisible to visible, otherwise events will be
     *              generated when a matching <code>Entry</code>
     *              makes any transition from unavailable to
     *              available
     * @param listener object to notify when an entry becomes (re)visible
     * @param leaseTime initial requested lease time for the registration
     * @param handback object to be included with every notification
     * @return An object with information on the registration
     * @throws TransactionException if <code>txn</code> is 
     *         non-<code>null</code> and not active or otherwise invalid
     */
    EventRegistration registerForAvailabilityEvent(EntryRep[] tmpls,
	    Transaction txn, boolean visibilityOnly, 
            RemoteEventListener listener, long leaseTime, 
            MarshalledObject handback)
        throws TransactionException, RemoteException;

    /**
     * Start a new contents query. Returns a
     * <code>MatchSetData</code> with the initial batch of
     * entries and (if applicable) the <code>Uuid</code> and initial
     * lease duration. If the entire result set is contained in the
     * returned <code>MatchSetData</code> the <code>Uuid</code>
     * will be <code>null</code> and the lease duration will be
     * -<code>1</code>.
     * @param tmpls the templates to use for the iteration
     * @param tr the transaction to perform the iteration under,
     *           may be <code>null</code>
     * @param leaseTime the requested lease time
     * @param limit the maximum number of entries to return
     * @return A <code>MatchSetData</code> with the initial batch
     * of entries and (if applicable) the <code>Uuid</code> and initial
     * lease duration.  Initial batch will be the empty array if
     * there are no matching entries in the space
     * @throws TransactionException if
     *         <code>tr</code> is non-<code>null</code> and can't be used
     * @throws IllegaleArgumentException if limit is non-positive or
     *         leaseTime is less than -1
     * @throws NullPointerException if tmpls is <code>null</code>
     */
    public MatchSetData contents(EntryRep[] tmpls, Transaction tr, 
				 long leaseTime, long limit)
        throws TransactionException, RemoteException;


    /**
     * Return the next batch of entries associated with the specified
     * contents query. If the returned array is not full then the
     * query is complete.
     * @param contentsQueryUuid the id of the contents query
     * @param entryUuid the id of the last entry in the last batch.
     *        If this does not match what the server has on recored
     *        it will re-send the previous batch.
     * @return an array of <code>EntryRep</code>s representing 
     *         the next batch of entries from the query. Query
     *         is complete if array is not full. Returns an empty
     *         array if there are no entries left
     * @throws NoSuchObjectException if the server has no record
     *         of <code>contentsQueryUuid</code>
     */
    public EntryRep[] nextBatch(Uuid contentsQueryUuid, Uuid entryUuid)
        throws RemoteException;


    /**
     * Return the admin proxy for this space.
     */
    Object getAdmin() throws RemoteException;
}
