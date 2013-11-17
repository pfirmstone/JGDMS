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
import java.io.ObjectInputStream;
import net.jini.core.lease.Lease;

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
public class EventRegistration implements java.io.Serializable {

    private static final long serialVersionUID = 4055207527458053347L;

    /**
     * The event identifier.
     *
     * @serial
     */
    private final long eventID;

    /**
     * The event source.
     *
     * @serial
     */
    private final Object source;

    /**
     * The registration lease.
     *
     * @serial
     */
    private final Lease lease;

    /**
     * The current sequence number.
     *
     * @serial
     */
    private final long seqNum;

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
     */
    public Object getSource() {
	return source;
    }

    /** 
     * Returns the Lease object for this registration. 
     * 
     * @return the Lease object for this registration.
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
     */
    public long getSequenceNumber() {
	return seqNum;
    }
    
    /**
     * Default read object, in case of future evolution.
     * @serial
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
        in.defaultReadObject();
    }
}
