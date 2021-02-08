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
package org.apache.river.mercury;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicObjectInput;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadObject;

/**
 * The <code>EventID</code> class is used to represent a unique event
 * registration.  This class maintains the two pieces of information 
 * that make a registration unique: the event's source and ID attributes.
 *
 * It's used by the mailbox code in order to maintain a list 
 * of <tt>EventID</tt>s that caused an <code>UnknownEventException</code> 
 * to be received during an event notification attempt for a given event.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
@AtomicSerial
class EventID implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The event source */
    private transient Object source = null;

    /** 
     * The event ID. 
     * @serial
     */
    private final long id;

    public EventID(GetArg arg) throws IOException {
	this(null,
	    arg.get("id", 0L),
	    check(((RO)arg.getReader()).source, "Null source read from stream")
	);
    }

    /** 
     * Simple constructor that assigns the provided arguments
     * to the appropriate internal fields
     */
    public EventID(Object source, long id) {
        this(null, id, check(source,"Source argument must be non-null"));
    }

    /** 
     * Convenience constructor.
     * Initializes the object with attributes extracted from the
     * given <code>RemoteEvent</code> argument.
     *
     * @exception IllegalArgumentException 
     *                if a null argument is provided
     */
    public EventID(RemoteEvent evt) {
        this(check(evt, "RemoteEvent argument must be non-null" ),
		evt.getID(),
		check(evt.getSource(),"Source argument must be non-null")
		);
    }

    private static <T> T check(T nullCheck, String message){
	if (nullCheck == null) throw new IllegalArgumentException(message);
	return nullCheck;
    }

    /** 
     * Convenience initialization method.
     * Note: private scoping prevents a subclass from inadvertently 
     * overriding this method.
     *
     * @exception IllegalArgumentException 
     *                if a null <tt>source</tt> argument is provided.
     */
    private EventID(Object check, long id, Object source) {
	this.source = source;
	this.id = id;
    }

    /** 
     * Return true if the given object is equal to <code>this</code> object
     * and false otherwise.  Two <code>EventID</code> objects are considered 
     * equal if their source and ID attributes are equal.
     */
    public boolean equals (Object o) {
        if (this == o)  
            return true;

        if (o == null)
            return false;

        if (o.getClass() != getClass()) 
            return false;

	EventID eid = (EventID)o;
        return (source.equals(eid.source) &&
                id == eid.id);
    }

    // inherit documentation from supertype
    @Override
    public int hashCode() {
        // TODO - find a better hash algorithm?
        return (int)id; // just truncate for now
    }

    // inherit documentation from supertype
    @Override
    public String toString() {
        return getClass().getName() + "[source=" + source + "]"
           + "[id=" + id + "]";
    }

    /**
     * @serialData Use default semantics for the <tt>id</tt> field but 
     * write the <tt>source</tt> field out as a <tt>MarshalledObject</tt>.
     * The <tt>source</tt> field is written out as a <tt>MarshalledObject</tt>
     * to preserve the codebase annotation as well as to 
     * guarantee that the object can be successfully read from the stream
     * (even if the codebase is unavailable at the time the object is 
     * being reconstituted).
     *
     * @exception IOException if an I/O error occurs
     */
    private void writeObject(ObjectOutputStream stream)
        throws IOException
    {
        stream.defaultWriteObject();
        stream.writeObject(new MarshalledInstance(source).convertToMarshalledObject());
    }

    /**
     * Initialize <code>id</code> field using default semantics but 
     * then unmarshal the value of <code>source</code> from the stream.
     *
     * @exception IOException if an I/O error occurs
     *
     * @exception ClassNotFoundException if a class of a serialized object 
     *                                   cannot be found
     */
    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();
        MarshalledObject mo = (MarshalledObject)stream.readObject();

        try {
            source = new MarshalledInstance(mo).get(false);
        } catch (Throwable e) {
            if (e instanceof Error &&
                !(e instanceof LinkageError ||
                  e instanceof OutOfMemoryError ||
                  e instanceof StackOverflowError)) {
                throw (Error)e;
	    }
        }
    }
    
    private static class RO implements ReadObject {
	
	Object source;

	@Override
	public void read(AtomicObjectInput stream) throws IOException, ClassNotFoundException {
	    MarshalledObject mo = (MarshalledObject)stream.readObject(MarshalledObject.class);
	    try {
		source = new MarshalledInstance(mo).get(false);
	    } catch (Throwable e) {
		if (e instanceof Error &&
		    !(e instanceof LinkageError ||
		      e instanceof OutOfMemoryError ||
		      e instanceof StackOverflowError)) {
		    throw (Error)e;
}
	    }
	}

        @Override
        public void read(ObjectInput input) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException("Not supported."); //To change body of generated methods, choose Tools | Templates.
        }
	
    }
}
