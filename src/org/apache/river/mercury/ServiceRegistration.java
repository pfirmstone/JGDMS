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
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.security.ProxyPreparer;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;
import org.apache.river.landlord.LeasedResource;

/**
 * The <tt>ServiceRegistration</tt> class serves as the server-side abstraction
 * that maintains client registration state information. It implements the
 * <tt>LeasedResource</tt> interface to allow it to be used with the
 * <tt>Landlord</tt> framework. It implements the <tt>Comparable</tt> 
 * interface so that it can be used in <tt>SortedMap</tt> collections.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
@AtomicSerial
class ServiceRegistration implements LeasedResource, Comparable, Serializable {

    private static final long serialVersionUID = 2L;

    /** Unique identifier object 
     * @serialField 
     */
    private final Uuid cookie;

    /** The current expiration for this registration
     * @serialField 
     */
    private volatile long expiration = 0;
    
    /** The prepared, client-provided notification target. */
    // This field is transient in order to allow readObject/writeObject
    // to un/wrap the the listener to/from a MarshalledObject. 
    // This is done to prevent the corruption of the input stream.
    // For example, if the associated codebase of the listener object
    // is no longer valid, then the stream is corrupted since the object
    // can't be reconstructed.  Wrapping the reference in a 
    // MarshalledObject always allows us to read from the stream.
    // We might not be able to reconstruct the listener field, but
    // the rest of the objects in the stream will be fine.
    private transient RemoteEventListener preparedEventTarget = null; 

    /** The marshalled form of the client-provided notification target.
     * @serialField 
     */
    private MarshalledObject marshalledEventTarget; 

    /** Event log iterator. */
    // This field is transient because event state info is persisted
    // separately from the registration state info. It is (re)constructed
    // upon service initialization.
    private transient EventLogIterator eventIterator;

    /** 
     * Map of collected <tt>EventID</tt>'s that resulted in an 
     * UnknownEventException for the current <code>
     * eventTarget</code>. This <code>Map</code> is checked
     * upon each mailbox notification for this registration. 
     * If the received event has the same <tt>EventID</tt> 
     * as the one contained in this <code>Map</code> then an 
     * <code>UnknownEventException</code>
     * is propagated back to the sender and the event is not logged.
     * This structure is also consulted before each event delivery request.
     * If the event to be delivered has an <tt>EventID</tt> that is 
     * contained in this <tt>Map</tt>, then event delivery is cancelled
     * for that particular event. Note that this structure gets cleared
     * whenever a target listener is (re)set on the assumption that an
     * active (re)set will provide a new/better target listener that might
     * be able to handle these events.
     * @serialField 
     */
    private final Map<EventID,UnknownEventException> unknownEvents;

    /** 
     * Unique identifier object for the currently enabled 
     * (client-side) remote event iterator.
     * @serialField 
     */
    private Uuid remoteEventIteratorID;
    
    /**
     * Lock object used to coordinate event delivery via the iterator.
     * Has to be a serializable object versus just a plain Object.
     * 
     * WTF?  Never use a String lock!!!
     * @serialField 
     */
    private final Object iteratorNotifier;
    
    private transient volatile Condition iteratorCondition;

    public ServiceRegistration(GetArg arg) throws IOException{
	this(arg.get("cookie", null, Uuid.class),
	    arg.get("eventIterator", null, EventLogIterator.class),
	    arg.get("iteratorCondition", null, Condition.class),
	    Valid.copyMap(
		arg.get("unknownEvents", null, Map.class),
		new ConcurrentHashMap<EventID,UnknownEventException>(),
		EventID.class, 
		UnknownEventException.class
	    ),
	    arg.get("marshalledEventType", null, MarshalledObject.class)
	);
    }

    /** Convenience constructor */
    public ServiceRegistration(Uuid cookie, EventLogIterator eventIterator, Condition iteratorCondition) {
	this(cookie, eventIterator, iteratorCondition, new ConcurrentHashMap<EventID,UnknownEventException>(), null);
    }
    
    private ServiceRegistration(Uuid cookie, 
	    EventLogIterator eventIterator, 
	    Condition iteratorCondition,
	    Map<EventID,UnknownEventException> unknownEvents,
	    MarshalledObject marshalledEventTarget)
    {
	this.marshalledEventTarget = marshalledEventTarget;
	this.unknownEvents = unknownEvents;
        this.cookie = cookie;
        this.eventIterator = eventIterator;
        this.iteratorCondition = iteratorCondition;
        this.iteratorNotifier = null;
    }

    // inherit javadoc from parent
    public void setExpiration(long newExpiration) {
	 expiration = newExpiration;
    }

    // inherit javadoc from parent
    public long getExpiration() { 
	return expiration;
    }

    // inherit javadoc from parent
    public Uuid getCookie() {
	return cookie;
    }
    
    /**
     * For use after deserialization only.
     * @param iteratorCondition 
     */
    void setCondition(Condition iteratorCondition){
        this.iteratorCondition = iteratorCondition;
    }
    
    public Condition getIteratorCondition(){
        return iteratorCondition;
    }

    /** 
     * Return the identity map of EventIDs that caused an 
     * UnknownEventException to be generated for the current
     * notification target. A Map is used instead of a generic
     * Collection since the space/time tradeoff for searches
     * seems worth it.
     */
    public Map getUnknownEvents() {
	return unknownEvents;
    }

    /** 
     * Get the reference to the prepared, 
     * client-supplied notification target 
     */
    public synchronized RemoteEventListener getEventTarget() {
	return preparedEventTarget;

    }

    /** Set the reference to the client-supplied notification target */
    public synchronized void setEventTarget(RemoteEventListener preparedTarget) 
	throws IOException
    {
        if (preparedTarget == null) {
	    preparedEventTarget = null;
	    marshalledEventTarget = null;
	} else {
	    preparedEventTarget = preparedTarget;
	    marshalledEventTarget = 
                new MarshalledInstance(preparedTarget).convertToMarshalledObject();
	}
    }
    
    /** Get the remote iterator id */
    public synchronized Uuid getRemoteEventIteratorID() {
	return remoteEventIteratorID;
    }
    
    /** Set the remote iterator id */
    public synchronized void setRemoteEventIteratorID(Uuid id) {
	remoteEventIteratorID = id;

    }
    
    /**
     * Returns <code>true</code> if an event target is currently set and
     * false otherwise. 
     */
    public synchronized boolean hasEventTarget() { 
        return (marshalledEventTarget != null);
    }
     
    /*
     * Method that restores internal, transient state.
     * Note:
     * This method attempts to prepare the target listener object, which 
     * might involve a remote invocation. Therefore, calling this method
     * while holding a lock should be avoided.
     */
    public void restoreTransientState(ProxyPreparer targetPreparer) 
        throws IOException, ClassNotFoundException 
    {
        if (targetPreparer == null) {
            throw new NullPointerException(
	        "targetPreparer cannot be null");
        }
        synchronized (this){
            if (marshalledEventTarget != null) {
                RemoteEventListener unprepared = 
                    (RemoteEventListener) new MarshalledInstance(marshalledEventTarget).get(false);
                preparedEventTarget = (RemoteEventListener)
                    targetPreparer.prepareProxy(unprepared);
            }
        }
	/* 
	 * Note: Would like to defer preparation until listener
	 * is needed (i.e. in getEventTarget()). Unfortunately, 
	 * listeners are obtained within "locks" and proxy
	 * preparation (i.e. possible remote calls) should
	 * not be done while holding those locks.
	 */
    }

    /** 
     * Get the  reference to the registration's associated 
     * <tt>EventLogIterator</tt>
     */
    public synchronized EventLogIterator iterator() {
        return eventIterator; 
    }

    /** 
     * Set the reference for this registration's 
     * <tt>EventLogIterator</tt> 
     */
    public synchronized void setIterator(EventLogIterator iter) {
        eventIterator = iter; 
    }

    /**
     * Primary sort by leaseExpiration, secondary by leaseID.  The
     * secondary sort is immaterial, except to ensure a total order
     * (required by TreeMap).
     */
    public synchronized int compareTo(Object obj) {
        ServiceRegistration reg = (ServiceRegistration)obj;
        if (this == reg)
            return 0;
        if ( expiration < reg.expiration ||
             (expiration == reg.expiration &&
             (   (cookie.getMostSignificantBits() < 
		     reg.cookie.getMostSignificantBits())
              && (cookie.getLeastSignificantBits() < 
		     reg.cookie.getLeastSignificantBits()) ) ))
            return -1;
        return 1;
    }

    // inherit documentation from supertype
    public String toString () {
        return getClass().getName() + ":" + cookie.toString();
    }

//TODO - implement readObject() for structural checks

    /**
     * Utility method to display debugging information to the
     * provided <tt>Logger</tt>
     */
    synchronized void dumpInfo(Logger logger) {
            logger.log(Level.FINEST, "{0}", this.toString());
            logger.log(Level.FINEST, "Expires at: {0}", new Date(expiration));
            logger.log(Level.FINEST, "Prepared target is: {0}", preparedEventTarget);
            logger.log(Level.FINEST, "Marshalled target is: {0}", marshalledEventTarget);
            logger.log(Level.FINEST, "Unknowns: {0}", 
	        Integer.valueOf(unknownEvents.size()));
	    // eventIterator is null upon recovery phase  which
	    // is before the transient state gets rebuilt.
	    if (eventIterator != null) {
	        try { 
                    logger.log(Level.FINEST, "hasNext: {0}",
		        Boolean.valueOf(eventIterator.hasNext()));
	        } catch (IOException ioe) {
                    logger.log(Level.FINEST, "hasNext exception.", ioe);
	        }
	    }
    }
    }
