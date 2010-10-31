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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.rmi.MarshalledObject;
import net.jini.core.event.RemoteEventListener;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.security.ProxyPreparer;

/**
 * Subclass of <code>AvailabilityRegistrationWatcher</code> for
 * non-transactional persistent availability/visibility event
 * registrations.  
 */
class StorableAvailabilityWatcher extends AvailabilityRegistrationWatcher
    implements StorableResource
{
    /** The listener that should be notified of matches */
    private StorableReference listener;

    /**
     * Used during log recovery to create a mostly empty
     * <code>StorableAvailabilityWatcher</code>.  
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
     * @throws NullPointerException if the <code>notifier</code>
     *         argument is null.  
     */
    StorableAvailabilityWatcher(long timestamp, long startOrdinal,
			      long currentSeqNum) 
    {
	super(timestamp, startOrdinal, currentSeqNum);
    }

    /**
     * Create a new <code>StorableAvailabilityWatcher</code>.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     * @param cookie The unique identifier associated
     *        with this watcher. Must not be <code>null</code>.
     * @param visibilityOnly pass <code>true</code> if client
     *        only wants visibility events
     * @param handback The handback object that
     *        should be sent along with event
     *        notifications to the the listener.
     * @param eventID The event ID for event type
     *        represented by this object. 
     * @param listener The object to notify of
     *        matches.
     * @throws NullPointerException if the <code>cookie</code>,
     *        or <code>listener</code> arguments are <code>null</code>.
     */
    StorableAvailabilityWatcher(long timestamp, long startOrdinal, Uuid cookie, 
        boolean visibilityOnly, MarshalledObject handback, long eventID, 
        RemoteEventListener listener)
    {
	super(timestamp, startOrdinal, cookie, visibilityOnly, handback,
	      eventID);

	if (listener == null)
	    throw new NullPointerException("listener must be non-null");
	this.listener = new StorableReference(listener);
    }
    
    boolean isInterested(EntryTransition transition, long ordinal) {
	if (ordinal <= startOrdinal)
	    return false;

	if (transition.getTxn() != null)
	    return false;

	if (!transition.isAvailable())
	    return false;

	if (visibilityOnly && !transition.isVisible())
	    return false;

	if (transition.hasProcessed(this))
	    return false;

	transition.processedBy(this);
	return true;
    }

    RemoteEventListener getListener(ProxyPreparer preparer) 
	throws ClassNotFoundException, IOException
    {
	return (RemoteEventListener)listener.get(preparer);
    }

    /**
     * Overridden by subclasses if there is any cleanup work they need
     * to do as part of <code>cancel</code> or
     * <code>removeIfExpired</code>. Called after releasing the lock
     * on <code>this</code>.  Will be called at most once.  
     * @param server A reference to the owner.
     * @param expired <code>true</code> if being called from 
     *        <code>removeIfExpired</code> and false otherwise. 
     */
    void cleanup(OutriggerServerImpl server, boolean expired) {
	if (expired)
	    server.scheduleCancelOp(cookie);
	else 
	    server.cancelOp(cookie, false);
    }

        /**  
     * Store the persistent fields 
     */
    public void store(ObjectOutputStream out) throws IOException {
	cookie.write(out);
	out.writeLong(expiration);
	out.writeLong(eventID);
	out.writeBoolean(visibilityOnly);
	out.writeObject(handback);
	out.writeObject(listener);
    }

    /**
     * Restore the persistent fields
     */
    public void restore(ObjectInputStream in) 
	throws IOException, ClassNotFoundException 
    {
	cookie = UuidFactory.read(in);
	expiration = in.readLong();
	eventID = in.readLong();
	visibilityOnly = in.readBoolean();
	handback = (MarshalledObject)in.readObject();	
	listener = (StorableReference)in.readObject();
	if (listener == null)
	    throw new StreamCorruptedException(
		"Stream corrupted, should not be null");
    }
}

