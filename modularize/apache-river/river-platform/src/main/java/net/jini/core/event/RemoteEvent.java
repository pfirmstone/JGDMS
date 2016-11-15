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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream.GetField;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.rmi.MarshalledObject;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;

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
 * and x has sequence number m and y has sequence number n, then if m &lt; n 
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
 * Immutable since 3.0.0
 * 
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class RemoteEvent extends java.util.EventObject {

    private static final long serialVersionUID = 1777278867291906446L;
    
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("source", Object.class),
	    new ObjectStreamField("eventID", long.class),
	    new ObjectStreamField("seqNum", long.class),
	    new ObjectStreamField("handback", MarshalledObject.class),
	    new ObjectStreamField("miHandback", MarshalledInstance.class)
	};

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
    @Deprecated
    protected MarshalledObject handback;
    
    /**
     * The registration handback object.
     * 
     * @serial
     */
    protected MarshalledInstance miHandback;

    private static Object check(GetArg arg) throws IOException {
	Object source = Valid.notNull(arg.get("source", null),"source cannot be null");
	arg.get("eventID", 0L);
	long seqNum = arg.get("seqNum", -1L);
	if (seqNum < 0) throw new InvalidObjectException("seqNum may have overflowed, less than zero");
	arg.get("handback", null, MarshalledObject.class); // Type check
	try{
	    arg.get("miHandback", null, MarshalledInstance.class); // Type check
	} catch (IllegalArgumentException ex){} // Ignore, earlier version.
	return source;
    }
    
    public RemoteEvent(GetArg arg) throws IOException {
	this (arg, check(arg));
    }
    
    private RemoteEvent(GetArg arg, Object source) throws IOException{
	super(source);
	this.source = source;
	eventID = arg.get("eventID", -1L);
	seqNum = arg.get("seqNum", -1L);
	handback = (MarshalledObject) arg.get("handback", null);
	try{
	    miHandback = arg.get("miHandback", null, MarshalledInstance.class); // Type check
	} catch (IllegalArgumentException ex){} // Ignore, earlier version.
    }

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
    @Deprecated
    public RemoteEvent(Object source, long eventID, long seqNum,
		       MarshalledObject handback) {
	super(source);
	this.eventID = eventID;
	this.seqNum = seqNum;
	this.handback = handback;
	this.miHandback = null;
    }
    
    /**
     * Constructs a RemoteEvent object.
     * <p>
     * The abstract state contained in a RemoteEvent object includes a 
     * reference to the object in which the event occurred, a long which 
     * identifies the kind of event relative to the object in which the 
     * event occurred, a long which indicates the sequence number of this 
     * instance of the event kind, and a MarshalledInstance that is to be 
     * handed back when the notification occurs. The combination of the 
     * event identifier and the object reference obtained from the 
     * RemoteEvent object should uniquely identify the event type.
     * 
     * @param source    an <tt>Object</tt> representing the event source
     * @param eventID   a <tt>long</tt> containing the event identifier
     * @param seqNum    a <tt>long</tt> containing the event sequence number
     * @param miHandback  a <tt>MarshalledInstance</tt> that was passed in 
     *                  as part of the original event registration.
     */
    public RemoteEvent(Object source, long eventID, long seqNum,
		       MarshalledInstance miHandback) {
	super(source);
	this.eventID = eventID;
	this.seqNum = seqNum;
	this.miHandback = miHandback;
	handback = null;
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
     * @deprecated Use {@link #getRegistrationInstance() } instead.
     */
    @Deprecated
    public MarshalledObject getRegistrationObject() {
	return handback;
    }
    
    /**
     * Returns the handback object that was provided as a parameter to
     * the event interest registration method, if any.  Note that if the 
     * handback object was a MarshalledObject, it will be returned as a
     * MarshalledInstance.
     *
     * @return the MarshalledInstance that was provided as a parameter to
     *         the event interest registration method, if any. 
     *	       Or the MarshalledObject that was provided, converted to a 
     *	       MarshalledInstance.
     */
    public MarshalledInstance getRegistrationInstance() {
	if ( miHandback == null && handback != null) return new MarshalledInstance(handback);
	return miHandback;
    }

    /**
     * Serialization support
     * @param stream ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws java.io.IOException if a problem occurs during de-serialization.
     */
    private void readObject(java.io.ObjectInputStream stream)
	throws java.io.IOException, ClassNotFoundException
    {
	GetField fields = stream.readFields();
	super.source = fields.get("source", null);
	eventID = fields.get("eventID", 0L);
	seqNum = fields.get("seqNum", 0L);
	handback = (MarshalledObject) fields.get("handback", null);
	try {
	    miHandback = (MarshalledInstance) fields.get("miHandback", null);
	} catch (IllegalArgumentException ex){} // Ignore, previous serial form.
    }
       
    /**
     * In River 3.0.0, RemoteEvent became immutable and all state made private,
     * previously all fields were protected and non final.
     * <p>
     * This change breaks compatibility for subclasses that access these fields
     * directly.  For other classes, all fields were accessible
     * via public API getter methods.
     * <p>
     * To allow an upgrade path for subclasses that extend RemoteEvent and
     * provide public setter methods for these fields, it is recommended to
     * override all public methods and maintain state independently using 
     * transient fields.  The subclass should also use RemoteEvent getter 
     * methods to set these transient fields during de-serialization.
     * <p>
     * writeObject, instead of writing RemoteEvent fields, writes the 
     * result of all getter methods to the ObjectOutputStream, during serialization,
     * preserving serial form compatibility wither earlier versions, while 
     * also allowing mutable subclasses to maintain full serial compatibility.
     * <p>
     * Mutable subclasses honoring this contract will be compatible with all 
     * versions since Jini 1.0.
     * 
     * @param stream
     * @throws java.io.IOException 
     */
    private void writeObject(java.io.ObjectOutputStream stream) throws java.io.IOException
    {
	ObjectOutputStream.PutField fields = stream.putFields();
	fields.put("source", getSource());
	fields.put("eventID", getID());
	fields.put("seqNum", getSequenceNumber());
	fields.put("handback", getRegistrationObject());
	fields.put("miHandback", getRegistrationInstance());
	stream.writeFields();
    }
}
