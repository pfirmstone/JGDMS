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
import java.io.NotSerializableException;
import java.rmi.MarshalledObject;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
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
 * <p>
 * The event state fields are {@code final}; a {@code RemoteEvent} is immutable
 * once constructed.
 * <p>
 * {@code RemoteEvent} crosses the wire only via its {@code @AtomicSerial} form
 * (the validating {@link #RemoteEvent(GetArg)} constructor). java.io
 * serialization is disabled -- {@code readObject}/{@code writeObject} throw
 * {@link java.io.NotSerializableException} -- so a crafted java.io stream
 * cannot reconstruct an instance while bypassing {@code @AtomicSerial}
 * validation. Because java.io deserialization invokes the superclass
 * {@code readObject} first, this also blocks JOSS reconstruction of every
 * subclass.
 * <p>
 * <b>{@code source} and the {@code Any} wire form.</b> {@code source} is inherited from
 * {@link java.util.EventObject} as a raw {@code Object} -- a decades-stable public contract
 * ("an Object representing the event source") with no narrower common type across real event
 * sources. On the wire it is routed through the STD-006 {@code Any} form (design memo
 * {@code docs/der-type-model-and-element-rule.md} §2.1/§3/§4): the same closed, self-describing,
 * context-tagged CHOICE already used for a genuinely unresolvable collection element type. A
 * scalar (e.g. {@code Integer}, {@code String}) travels via a direct scalar arm; an
 * {@code @AtomicSerial} object or a dynamic {@link java.lang.reflect.Proxy} over one travels
 * through the identical gated reconstruction path as any typed {@code @AtomicSerial} field --
 * {@code Any} is not a second admission door. Arbitrary {@code Serializable}/JOSS content is
 * unrepresentable inside {@code Any} by construction. This routing requires no change to this
 * class: {@link #serialForm()} keeps declaring {@code source} as {@code Object.class},
 * {@link #serialize} keeps putting it unchanged, and {@link #check(GetArg)} keeps its existing
 * {@code Valid.notNull} invariant.
 * <p>
 * <b>Layer-2 narrowing pattern for subclasses.</b> Because {@code source}'s wire form carries no
 * type commitment beyond the closed {@code Any} subset (memo §8.2), {@code RemoteEvent} itself
 * cannot narrow it further -- "any Object" genuinely is its contract. Almost every concrete
 * subclass, however, knows exactly what its own {@code source} really is (e.g. a lookup service's
 * own event names the lookup service itself as the source). The prescribed pattern for such a
 * subclass is to <b>defensively construct a plain {@code RemoteEvent} from the {@code GetArg}
 * inside its own static {@code check(GetArg)} method ({@code new RemoteEvent(arg)} --
 * validated, side-effect-free), call {@link #getSource()} on it, and type-check/narrow the
 * result to the subclass's specific expected type</b> -- rejecting with
 * {@link java.io.InvalidObjectException} on mismatch -- before the subclass's own constructor
 * proceeds. For example:
 * <pre>{@code
 * private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException {
 *     RemoteEvent base = new RemoteEvent(arg);
 *     Object source = base.getSource();
 *     if (!(source instanceof MyExpectedSourceType)) {
 *         throw new InvalidObjectException("source must be a MyExpectedSourceType");
 *     }
 *     return arg;
 * }
 * }</pre>
 * Constructing the base instance (rather than reading {@code arg.get("source", ...)} directly
 * from the subclass's own frame) also sidesteps a real {@code GetArg} class-namespace trap: a
 * pre-{@code super} {@code arg.get(...)} call from a subclass's static {@code check} method
 * resolves the CALLER'S OWN serial-field namespace (per {@code AtomicSerial.GetArg}'s per-caller
 * dispatch), not {@code RemoteEvent}'s -- so a direct {@code arg.get("source", null)} from a
 * subclass frame silently resolves to that subclass's own (typically empty) namespace and
 * returns the default, never the real source. {@code new RemoteEvent(arg)} reads {@code source}
 * from {@code RemoteEvent}'s own frame, correctly. This pattern applies specifically where a
 * <em>concrete</em> subclass narrows an <em>ancestor's</em> {@code Any}-resolved slot read from
 * outside that ancestor's own declaring frame -- it does NOT apply to a class narrowing its own
 * directly-declared field (an ordinary same-class {@code check(GetArg)} assertion is correct and
 * sufficient there; there is no cross-namespace risk to defend against when the read already
 * happens in the field's own declaring frame).
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class RemoteEvent extends java.util.EventObject {

    private static final long serialVersionUID = 1777278867291906446L;

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("source", Object.class),
	    new SerialForm("eventID", long.class),
	    new SerialForm("seqNum", long.class),
	    new SerialForm("miHandback", MarshalledInstance.class)
        };
    }

    public static void serialize(PutArg arg, RemoteEvent r) throws IOException{
        arg.put("source", r.source);
        arg.put("eventID", r.eventID);
        arg.put("seqNum", r.seqNum);
        arg.put("miHandback", r.miHandback);
        arg.writeArgs();
    }

    /**
     * The event identifier.
     * @since 1.0
     * @serial
     */
    protected final long eventID;

    /**
     * The event sequence number.
     * @since 1.0
     * @serial
     */
    protected final long seqNum;

    /**
     * The handback object.
     * @since 1.0
     * @serial
     * @deprecated
     */
    @Deprecated
    protected final MarshalledObject handback;

    /**
     * The registration handback object.
     * @since 3.1
     * @serial
     */
    protected final MarshalledInstance miHandback;

    private static Object check(GetArg arg) throws IOException, ClassNotFoundException {
	Object source = Valid.notNull(arg.get("source", null),"source cannot be null");
	arg.get("eventID", 0L);
	long seqNum = arg.get("seqNum", -1L);
	if (seqNum < 0) throw new InvalidObjectException("seqNum may have overflowed, less than zero");
	arg.get("handback", null, MarshalledObject.class); // Type check, remains for earlier serial form.
	try{
	    arg.get("miHandback", null, MarshalledInstance.class); // Type check
	} catch (IllegalArgumentException ex){} // Ignore, earlier version.
	return source;
    }
    
    /**
     * Deserialization constructor.
     * @param arg
     * @throws IOException
     * @throws ClassNotFoundException 
     * @see AtomicSerial
     */
    public RemoteEvent(GetArg arg) throws IOException, ClassNotFoundException {
	this (arg, check(arg));
    }
    
    private RemoteEvent(GetArg arg, Object source) throws IOException, ClassNotFoundException{
	super(source);
	this.source = source;
	eventID = arg.get("eventID", -1L);
	seqNum = arg.get("seqNum", -1L);
	handback = arg.get("handback", null, MarshalledObject.class);
	MarshalledInstance mi;
	try{
	    mi = arg.get("miHandback", null, MarshalledInstance.class); // Type check
	} catch (IllegalArgumentException ex){ // Ignore, earlier version.
	    mi = null;
	}
	miHandback = mi;
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
     * @since 1.0
     * @deprecated Use {@link #RemoteEvent(Object, long, long, MarshalledInstance)}
     *             instead; java.rmi.MarshalledObject drops the DER schema fields that
     *             a MarshalledInstance carries.
     */
    @Deprecated(forRemoval = true)
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
     * @since 3.1
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
     * @since 1.0
     */
    public long getID() {
	return eventID;
    }

    /** 
     * Returns the sequence number of this event. 
     *
     * @return a long representing the sequence number of this event.
     * @since 1.0
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
     * @deprecated Use {@link #getRegistrationInstance() } instead;
     *             java.rmi.MarshalledObject drops the DER schema fields that a
     *             MarshalledInstance carries (and conversion fails for a DER instance).
     * @since 1.0
     */
    @Deprecated(forRemoval = true)
    public MarshalledObject getRegistrationObject() {
	if (handback == null && miHandback != null) return miHandback.convertToMarshalledObject();
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
     * @since 3.1
     */
    public MarshalledInstance getRegistrationInstance() {
	if ( miHandback == null && handback != null) return new MarshalledInstance(handback);
	return miHandback;
    }

    /**
     * java.io deserialization is disabled; reconstruct via {@code @AtomicSerial}
     * (the {@link #RemoteEvent(GetArg)} constructor) instead. Poisoning this
     * superclass hook also blocks JOSS reconstruction of every subclass, since
     * java.io invokes the superclass {@code readObject} first.
     *
     * @throws NotSerializableException always
     */
    private void readObject(java.io.ObjectInputStream in)
	throws java.io.IOException, ClassNotFoundException
    {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (GetArg)");
    }

    /**
     * @throws NotSerializableException always
     */
    private void readObjectNoData() throws java.io.ObjectStreamException {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (GetArg)");
    }

    /**
     * java.io serialization is disabled; this class is marshalled via its
     * {@code @AtomicSerial} form ({@link #serialize}) instead.
     *
     * @throws NotSerializableException always
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException
    {
	throw new NotSerializableException(
	    "java.io serialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (PutArg)");
    }
}
