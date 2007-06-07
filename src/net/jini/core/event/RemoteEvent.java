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

package net.jini.core.event;

import java.rmi.MarshalledObject;

/**
 * The base class or superclass for remote events.
 * <p>
 * The abstract state contained in a RemoteEvent object includes a reference
 * to the object in which the event occurred, a long which identifies the
 * kind of event relative to the object in which the event occurred, a long
 * which indicates the sequence number of this instance of the event kind,
 * and a MarshalledObject that is to be handed back when the notification
 * occurs.  The combination of the event identifier and the object reference
 * obtained from the RemoteEvent object should uniquely identify the event
 * type.
 * <p>
 * The sequence number obtained from the RemoteEvent object is an increasing
 * value that can act as a hint to the number of occurrences of this event
 * relative to some earlier sequence number.  Any object that generates a
 * RemoteEvent is required to insure that for any two RemoteEvent objects
 * with the same event identifier, the sequence number of those events differ
 * if and only if the RemoteEvent objects are a response to different events.
 * This guarantee is required to allow notification calls to be idempotent.
 * A further guarantee is that if two RemoteEvents, x and y, come from the
 * same source and have the same event identifier, then x occurred before y
 * if and only if the sequence number of x is lower than the sequence number
 * of y.
 * <p>
 * A stronger guarantee is possible for those generators of RemoteEvents
 * that can support it.  This guarantee states that not only do sequence
 * numbers increase, but they are not skipped.  In such a case, if
 * RemoteEvent x and y have the same source and the same event identifier,
 * and x has sequence number m and y has sequence number n, then if m < n 
 * there were exactly n-m-1 events of the same event type between the event 
 * that triggered x and the event that triggered y. Such sequence numbers 
 * are said to be "fully ordered".
 * <p>
 * An event registration that occurs within a transaction is considered to be
 * scoped by that transaction. This means that any occurrence of the kind of
 * event of interest that happens as part of the transaction will cause a
 * notification to be sent to the recipients indicated by the registration
 * that occurred in the transaction.  Such events must have a separate event
 * identifier to allow third-party store and forward entities to distinguish
 * between an event that happens within a transaction and those that happen
 * outside of the transaction.  Notifications of these events will not be
 * sent to entities that registered interest in this kind of event outside
 * the scope of the transaction until and unless the transaction is committed.
 * <p>
 * Because of this, notifications sent from inside a transaction will have a
 * different sequence number than the notifications of the same events would
 * have outside of the transaction.  Within a transaction, all RemoteEvent
 * objects for a given kind of event are given a sequence number relative to
 * the transaction. This is true even if the event that triggered the
 * RemoteEvent object being sent occurs outside of the scope of the
 * transaction (but is visible within the transaction).
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class RemoteEvent extends java.util.EventObject {

    private static final long serialVersionUID = 1777278867291906446L;

    /**
     * The event source.
     *
     * @serial
     */
    protected Object source;

    /**
     * The event identifier.
     *
     * @serial
     */
    protected long eventID;

    /**
     * The event sequence number.
     *
     * @serial
     */
    protected long seqNum;

    /**
     * The handback object.
     *
     * @serial
     */
    protected MarshalledObject handback;

    /**
     * Constructs a RemoteEvent object.
     * <p>
     * The abstract state contained in a RemoteEvent object includes a 
     * reference to the object in which the event occurred, a long which 
     * identifies the kind of event relative to the object in which the 
     * event occurred, a long which indicates the sequence number of this 
     * instance of the event kind, and a MarshalledObject that is to be 
     * handed back when the notification occurs. The combination of the 
     * event identifier and the object reference obtained from the 
     * RemoteEvent object should uniquely identify the event type.
     * 
     * @param source    an <tt>Object</tt> representing the event source
     * @param eventID   a <tt>long</tt> containing the event identifier
     * @param seqNum    a <tt>long</tt> containing the event sequence number
     * @param handback  a <tt>MarshalledObject</tt> that was passed in 
     *                  as part of the original event registration.
     */
    public RemoteEvent(Object source, long eventID, long seqNum,
		       MarshalledObject handback) {
	super(source);
	this.source = source;
	this.eventID = eventID;
	this.seqNum = seqNum;
	this.handback = handback;
    }

    /**
     * Returns the event identifier, used to identify the kind of event
     * relative to the object in which the event occurred.
     *
     * @return a long representing the event identifier relative to the 
     *         object in which the event occurred.
     * @see EventRegistration#getID
     */
    public long getID() {
	return eventID;
    }

    /** 
     * Returns the sequence number of this event. 
     *
     * @return a long representing the sequence number of this event.
     */
    public long getSequenceNumber() {
	return seqNum;
    }

    /**
     * Returns the handback object that was provided as a parameter to
     * the event interest registration method, if any.
     *
     * @return the MarshalledObject that was provided as a parameter to
     *         the event interest registration method, if any. 
     */
    public MarshalledObject getRegistrationObject() {
	return handback;
    }

    /**
     * Serialization support
     */
    private void readObject(java.io.ObjectInputStream stream)
	throws java.io.IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	super.source = source;
    }
}
