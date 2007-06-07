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

import java.rmi.MarshalledObject;
import net.jini.core.event.RemoteEventListener;
import net.jini.space.InternalSpaceException;
import net.jini.id.Uuid;
import net.jini.security.ProxyPreparer;

/**
 * Subclass of EventRegistrationWatcher for transactional
 * event registrations.
 */
class TransactableEventWatcher extends EventRegistrationWatcher
    implements Transactable
{
    /** The listener that should be notified of matches */
    private final RemoteEventListener listener;

    /** 
     * The transaction (represented as a <code>Txn</code>) this
     * event registration is associated with.
     */
    private final Txn txn;
    
    /**
     * Create a new <code>TransactableEventWatcher</code>.
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
     * @param listener The object to notify of
     *        matches.
     * @param txn The transaction this registration is
     *        associated with.
     * @throws NullPointerException if the <code>cookie</code>,
     *         <code>listener</code>, or <code>txn</code> arguments are
     *         <code>null</code>.
     */
    TransactableEventWatcher(long timestamp, long startOrdinal, Uuid cookie,
	MarshalledObject handback, long eventID, 
	RemoteEventListener listener, Txn txn)
    {
	super(timestamp, startOrdinal, cookie, handback, eventID);

	if (listener == null)
	    throw new NullPointerException("listener must be non-null");

	if (txn == null)
	    throw new NullPointerException("txn must be non-null");

	this.listener = listener;
	this.txn = txn;
    }

    boolean isInterested(EntryTransition transition, long ordinal) {
	// If it is not a new entry we don't care
	if (!transition.isNewEntry())
	    return false;

	// If it occurred before our registration we don't care
	if (ordinal <= startOrdinal)
	    return false;

	final TransactableMgr transitionTxn = transition.getTxn();

	// We care if the transition under the null transaction or ours.
	return (transitionTxn == null) || (transitionTxn == txn);
    }

    RemoteEventListener getListener(ProxyPreparer preparer) {
	return listener;
    }
    
    /**
     * Just need to terminate this registration and return 
     * <code>NOTCHANGED</code>.
     */
    public int prepare(TransactableMgr mgr, OutriggerServerImpl space) {
	cancel();
	return NOTCHANGED;
    }

    /**
     * This should never happen since we always return
     * <code>NOTCHANGED</code> from <code>prepare</code>.
     */
    public void commit(TransactableMgr mgr, OutriggerServerImpl space) {
	throw new InternalSpaceException
	    ("committing a TransactableRegistrationWatcher");
			   
    }

    /**
     * Just need to terminate this registration.
     */
    public void abort(TransactableMgr mgr, OutriggerServerImpl space) {
	// prepare does the right thing, and should forever
	prepare(mgr, space);
    }
}

