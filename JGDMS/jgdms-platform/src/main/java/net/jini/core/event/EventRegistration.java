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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import net.jini.core.lease.Lease;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * A utility class for use as a return value for event-interest registration
 * methods.  Objects of this class are meant to encapsulate the information
 * needed by a client to identify a notification as a response to a
 * registration request and to maintain that registration request.  It is
 * not mandatory for an event-interest registration method to use this class.
 * <p>
 * A registration of interest in some kind of event that occurs within the
 * scope of a transaction is leased in the same way as other event interest
 * registrations.  However, the duration of the registration is the minimum
 * of the length of the lease and the duration of the transaction.  Simply
 * put, when the transaction ends (either because of a commit or an abort)
 * the interest registration also ends.  This is true even if the lease for
 * the event registration has not expired and no call has been made to
 * cancel the lease.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public final class EventRegistration implements java.io.Serializable {

    private static final long serialVersionUID = 4055207527458053347L;
    
    private final static String EVENT_ID = "eventID";
    private final static String SOURCE = "source";
    private final static String LEASE = "lease";
    private final static String SEQ_NUM = "seqNum";
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(EVENT_ID, Long.TYPE),
            new SerialForm(SOURCE, Object.class),
            new SerialForm(LEASE, Lease.class),
            new SerialForm(SEQ_NUM, Long.TYPE)
        };
    }
    
    public static void serialize(PutArg arg, EventRegistration e) throws IOException{
        putArgs(arg, e);
        arg.writeArgs();
    }
    
    private static void putArgs(PutField fields, EventRegistration e){
        fields.put(EVENT_ID, e.eventID);
        fields.put(SOURCE, e.source);
        fields.put(LEASE, e.lease);
        fields.put(SEQ_NUM, e.seqNum);
    }

    /**
     * The event identifier.
     *
     * @serial
     * @since 1.0
     */
    private final long eventID;

    /**
     * The event source.
     *
     * @serial
     * @since 1.0
     */
    private final Object source;

    /**
     * The registration lease.
     *
     * @serial
     * @since 1.0
     */
    private final Lease lease;

    /**
     * The current sequence number.
     *
     * @serial
     * @since 1.0
     */
    private final long seqNum;

    private static boolean check(GetArg arg) throws IOException{
	arg.get("eventID", 0L);
	Object source = arg.get("source", null);
	if (source == null) throw new InvalidObjectException("source cannot be null");
	Object lease = arg.get("lease", null);
	if (!(lease instanceof Lease)) throw new InvalidObjectException(
		"lease cannot be null and must be an instance of Lease");
	long seqNum = arg.get("seqNum", 0L);
	if (seqNum < 0) throw new InvalidObjectException("seqNum must be greater than zero, possible overflow");
	return true;
    }
    
    private EventRegistration(boolean check, GetArg arg) throws IOException{
	eventID = arg.get("eventID", 0L);
	source = arg.get("source", null);
	lease = (Lease) arg.get("lease", null);
	seqNum = arg.get("seqNum", 0L);
    }
    
    /**
     * Deserialization constructor.
     * @param arg
     * @throws IOException 
     * @since 3.1
     * @see AtomicSerial
     */
    public EventRegistration(GetArg arg) throws IOException{
	this(check(arg), arg);
    }

    /**
     * Constructs an <tt>EventRegistration</tt> object.
     * 
     * Immutable since 3.0.0
     *
     * @param eventID  a <tt>long</tt> representing the event identifier
     * @param source   an <tt>Object</tt> representing the event source
     * @param lease    the registration <tt>Lease</tt> object
     * @param seqNum   a <tt>long</tt> representing the current
     *                 sequence number
     * @since 1.0
     */
    public EventRegistration(long eventID, Object source,
			     Lease lease, long seqNum) {
	this.eventID = eventID;
	this.source = source;
	this.lease = lease;
	this.seqNum = seqNum;
    }

    /**
     * Returns the identifier that will be used in all RemoteEvents generated
     * for this interest registration.
     *
     * @return a long used to identify all RemoteEvents that are generated
     *         for this interest registration.
     * @see RemoteEvent#getID
     * @since 1.0
     */
    public long getID() {
	return eventID;
    }

    /**
     * Returns the source that will be used in all RemoteEvents generated
     * for this interest registration.
     *
     * @return an Object that represents the source of all RemoteEvents 
     *         for this interest registration
     * @see java.util.EventObject#getSource
     * @since 1.0
     */
    public Object getSource() {
	return source;
    }

    /** 
     * Returns the Lease object for this registration. 
     * 
     * @return the Lease object for this registration.
     * @since 1.0
     */
    public Lease getLease() {
	return lease;
    }

    /**
     * Returns the value of the sequence number on the event kind that was
     * current when the registration was granted, allowing comparison with
     * the sequence number in any subsequent notifications.
     *
     * @return a long representing the value of the sequence number on the 
     *         event type that was current when the registration was 
     *         granted, allowing comparison with the sequence number in any 
     *         subsequent notifications.
     * @since 1.0
     */
    public long getSequenceNumber() {
	return seqNum;
    }
    
    /**
     * Default writeObject method.
     * @serial
     * @param out
     * @throws IOException 
     * @since 3.1
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	putArgs(out.putFields(), this);
        out.writeFields();
    }
    
    /**
     * Default read object, in case of future evolution.
     * @serial
     * @param in ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     * @since 3.0
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
        in.defaultReadObject();
    }
}
