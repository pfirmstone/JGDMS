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
package com.sun.jini.norm.event;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.security.ProxyPreparer;

import com.sun.jini.constants.ThrowableConstants;
import com.sun.jini.logging.Levels;
import com.sun.jini.thread.RetryTask;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;

/**
 * Representation of an event type the supports a single registrant.
 * The registrant, event ID, and event sequence number information
 * portions of this object are preserved when serialized.  The send
 * monitor, and currently scheduled event sends are not.
 *
 * @author Sun Microsystems, Inc.
 */
// $$$ I am not sure if this class does too much locking, or too little.
// It does too little in that it does not do any locking during serialization
// It does too much in that Norm's current locking discipline means that for
// the most part this class does not have to worry about multiple threads
// of control entering at one time.
// Before we make this a general utility we have to re-think the locking
// strategy.
public class EventType implements Serializable {
    private static final long serialVersionUID = 2;

    /** Logger for logging messages for this class */
    private static final Logger logger = Logger.getLogger("com.sun.jini.norm");

    /**
     * Listener registered for events of this type.  Stored in 
     * marshalled form so object the <code>EventType</code> object can
     * be recovered even if the listener's codebase is not available.
     * If this field is <code>null</code>, <code>listener</code> and 
     * <code>handback</code> will be also.  
     * @serial
     */
    private MarshalledObject marshalledListener;

    /** Transient cache of listener in unmarshalled form */
    private transient RemoteEventListener listener;

    /**
     * The proxy preparer to use to prepare a newly unmarshalled listener, or
     * null if this instance was created using an already prepared listener,
     * which is how instances are created initially.
     */
    private transient ProxyPreparer recoveredListenerPreparer;

    /**
     * Handback object associated with current listener.
     * @serial
     */
    private MarshalledObject handback;

    /** 
     * Sequence number of the current listener/handback pair, incremented
     * every time a new listener is set (even if the objects 
     * are equivalent)
     * @serial
     */
    private long registrationNumber;

    /**
     * Last event sequence number we used
     * @serial
     */
    private long lastSeqNum;

    /**
     * Our event ID
     * @serial
     */
    private long evID;

    /** 
     * Object we check with to ensure leases have not expired and notify
     * when we get a definite exception during an event send attempt.
     * If this field is <code>null</code> generator will be also.
     */
    private transient SendMonitor monitor;

    /** 
     * Event type generator that created us. If this field is
     * <code>null</code> monitor will be also.
     */
    private transient EventTypeGenerator generator;

    /**
     * Simple constructor.  Initially the last sequence number is set to 0.
     * @param generator EventTypeGenerator that is creating this event type
     * @param monitor Object which is to monitor the sending of events
     *	      of this type
     * @param evID event ID of this event type
     * @param listener the listener events of this type should go to
     * @param handback the object that should be passed to listener
     *        as part of the event
     * @throws IOException if the listener cannot be serialized 
     */
    EventType(EventTypeGenerator generator, SendMonitor monitor, long evID,
	      RemoteEventListener listener, MarshalledObject handback)
        throws IOException
    {
	if (generator == null) {
	    throw new NullPointerException("EventType(): Must create event " +
                "type objects with a non-null generator");
	}

	if (monitor == null) {
	    throw new NullPointerException("EventType(): Must create event " +
                "type objects with a non-null monitor");
	}

	this.generator = generator;
	this.monitor = monitor;
	this.evID = evID;
	setLastSequenceNumber(0);
	setListener(listener, handback);
    }    

    /** Utility method to null out listener */
    private void clearListener() {
	listener = null;
	handback = null;
	marshalledListener = null;
    }

    /**
     * (Re)set the listener for this type of event.  Any pending
     * events that have not yet been sent will be sent to the new
     * listener, passing the new handback.  Setting the listener to
     * <code>null</code> will cancel the sending of all pending
     * events.  
     *
     * @param listener the listener events of this type should go to
     * @param handback the object that should be passed to listener
     *        as part of the event
     * @throws IOException if listener cannot be serialized
     */
    public synchronized void setListener(RemoteEventListener listener, 
					 MarshalledObject    handback)
        throws IOException
    {
	registrationNumber++;
	
	if (listener == null) {
	    clearListener();
	} else {	    
	    marshalledListener = new MarshalledObject(listener);
	    this.listener = listener;
	    this.handback = handback;
	}
    }

    /**
     * Returns <code>true</code> if there is a listener registered for this 
     * event type.
     */
    public boolean haveListener() {
	return marshalledListener != null;
    }

    /**
     * Convince method to get the listener.
     * @return the listener, or <code>null</code> if the listener can't be
     *	       unpacked or prepared, or there is no listener
     */
    private RemoteEventListener getListener() {
	if (!haveListener()) 
	    return null;

	if (listener != null) 
	    return listener;

	// There is a listener, but it is not unpacked yet, try to unpack
	RemoteEventListener unpreparedListener = null;
	try {
	    unpreparedListener =
		(RemoteEventListener) marshalledListener.get();
	} catch (IOException e) {
	    logger.log(Levels.HANDLED,
		       "Problem unmarshalling listener -- will retry later",
		       e);
	    // $$$ is this really the right thing to do?
	    // we probably really have a corrupted marshalledListener here
	} catch (ClassNotFoundException e) {
	    logger.log(Levels.HANDLED,
		       "Problem unmarshalling listener -- will retry later",
		       e);
	}

	if (unpreparedListener != null) {
	    // Prepare the listener
	    try {
		listener = (RemoteEventListener)
		    recoveredListenerPreparer.prepareProxy(unpreparedListener);
	    } catch (RemoteException e) {
		logger.log(Levels.HANDLED,
			   "Problem preparing listener -- will retry later",
			   e);
	    } catch (SecurityException e) {
		logger.log(Levels.HANDLED,
			   "Problem preparing listener -- will retry later",
			   e);
	    }
	}

	return listener;
    }

    /**
     * Atomically clear the current registration if its sequence
     * number matches the passed in sequence number.  If the
     * replacement occurs it will have the same effect as calling
     * <code>setListener(null, null)</code>.
     * <p>
     * Can be used by code that needs to remove event registrations in
     * response to exceptions thrown during event delivery without
     * risking clobbering new registrations.
     * @param oldSequenceNumber sequence number of the 
     *        registration that had a problem 
     * @return <code>true</code> if the state of the object was
     * changed and <code>false</code> otherwise
     * @see EventType#setListener 
     */
    public synchronized boolean clearListenerIfSequenceMatch(
        long oldSequenceNumber) 
    {
	if (oldSequenceNumber == registrationNumber) {
	    clearListener();
	    return true;
	}

	return false;
    }

    /**
     * Set the object's notion of the last sequence number.  The next event
     * scheduled to be sent will have a sequence number one greater than
     * the value past to this call.
     * <p>
     * Note: this method is not synchronized.
     * @param seqNum value for the last sequence number
     */
    public void setLastSequenceNumber(long seqNum) {
	lastSeqNum = seqNum;
    }

    /**
     * Return the sequence number of the last event that was scheduled
     * to be sent.  Intended primarily for creating
     * <code>EventRegistration</code> objects and the like.
     */
    public synchronized long getLastSequenceNumber () {
	return lastSeqNum;
    }
    
    /**
     * Return the <code>long</code> that was uniquely associated with this
     * object when it was created.
     */
    public long getEventID() {
	return evID;
    }

    /**
     * Schedule the sending of an event.  This event will be sent to
     * the currently registered listener.  If the listener changes
     * before the the event is successfully sent the event will be sent
     * to the new listener.  If the current listener is
     * <code>null</code> this call will have no affect aside from
     * incrementing the sequence number.
     * @param factory an object that will be used to create the
     * <code>Event</code> object when necessary
     * @return the sequence number assigned to the event
     * @throws IllegalStateException if this method is called
     *         after the object has be deserialized and before
     *         <code>restoreTransientState</code> has been called
     * @see EventType#restoreTransientState
     */
    public synchronized long sendEvent(EventFactory factory) {
	if (generator == null) {
	    // Have not had our state restored, complain
	    throw new IllegalStateException("EventType.sendEvent:" +
	        "called before state was fully restored");
	}

	// Even if there is no listener, an event has occurred, so
	// increment the sequence number (note this a stronger
	// guarantee that the Jini Distributed Event Specification,
	// but one that is required by the LRS spec).
	lastSeqNum++;

	// If we don't have a listener we don't need do anything else
	if (!haveListener())
	    return lastSeqNum;
	
	final TaskManager mgr = generator.getTaskManager();
	final WakeupManager wMgr = generator.getWakeupManager();
	mgr.add(new SendTask(mgr, wMgr, factory, lastSeqNum));
	
	return lastSeqNum;
    }

    /**
     * Increment the sequence number and return the result. This
     * method is useful if an event occurs that needs to be noted
     * but from some reason it is impossible to deliver the event.
     *
     * @return the new value for the sequence number
     */
    public synchronized long bumpSequenceNumber() {
	return ++lastSeqNum;
    }

    /**
     * Restore the transient state of this object after recovering it
     * from a serialization stream.  None of the arguments can be
     * <code>null</code>.
     * <p>
     * Note: this method is not synchronized.  
     * @param generator the <code>EventTypeGenerator</code> that was used
     *        to create this EventType object originally
     * @param monitor the object that monitors the progress of events
     *        set by this object
     * @param recoveredListenerPreparer the proxy preparer to use to prepare
     *	      listeners recovered from persistent storage
     */
    public void restoreTransientState(EventTypeGenerator generator,  
                                      SendMonitor monitor,
				      ProxyPreparer recoveredListenerPreparer)
    {
	if (generator == null) {
	    throw new NullPointerException("EventType.restoreTransientState:" +
	        "Must call with a non-null generator");
	}
	if (monitor == null) {
	    throw new NullPointerException("EventType.restoreTransientState:" +
	        "Must call with a non-null monitor");
	}
	if (recoveredListenerPreparer == null) {
	    throw new NullPointerException("EventType.restoreTransientState:" +
	        "Must call with a non-null recoveredListenerPreparer");
	}
	this.generator = generator;
	this.monitor = monitor;
	this.recoveredListenerPreparer = recoveredListenerPreparer;
	generator.recoverEventID(evID);
    }

    /**
     * Subclass of <code>RetryTask</code> used by <code>EventType</code>
     * to send events.
     */
    private class SendTask extends RetryTask {
	/** Max time we are willing to let a send attempt to go on for */
	final static private long MAX_TIME = 1000 * 60 * 60 * 24; //~ 1 Day
	
	/** Factory used to create the <code>RemoteEvent</code> to be sent */
	final private EventFactory eventFactory;

	/** Sequence number the event should have */
	final private long seqNum;

	/** Cached event */
	private RemoteEvent event;
	
	/** 
	 * Registration sequence number of the listener/handback pair
	 * event was built for
	 */
	private long eventForRegistrationNumber = -1;

	/**
	 * Simple constructor.
	 * @param taskManager <code>TaskManager</code> this task is to be
	 *                    put into
	 * @param eventFactory <code>EventFactory</code> that will be used
	 *                     to create the event to be sent
	 * @param seqNum      the sequence number of the event
	 */
	private SendTask(TaskManager taskManager, WakeupManager wakeupManager,
			 EventFactory eventFactory, long seqNum)
	{
	    super(taskManager, wakeupManager);
	    this.eventFactory = eventFactory;
	    this.seqNum = seqNum;
	}

	// Inherit java doc from super type
	public boolean tryOnce() {
	    final long now = System.currentTimeMillis();
	    if (now - startTime() > MAX_TIME)
		return true;	// we have been trying too long, stop here

	    if (!EventType.this.monitor.isCurrent())
		return true;	// lease gone, stop here

	    // Local copies of listener and handback so they won't
	    // be clobbered by setListener calls
	    RemoteEventListener listener;
	    MarshalledObject handback;
	    long registrationNumber;
	    
	    synchronized (EventType.this) {
		if (!EventType.this.haveListener())
		    return true; // No currently registered listener, stop here

		listener = EventType.this.getListener();
		if (listener == null) {
		    return false; // There is a listener, but we can't unpack
				  // it -- schedule a retry later
		}
		handback = EventType.this.handback;
		registrationNumber = EventType.this.registrationNumber;
	    }

	    // If the handback has changed we need to create a new
	    // event object (we approximate this test by checking the
	    // registrationNumber

	    if (event == null || 
		eventForRegistrationNumber != registrationNumber) 
	    {
		event = eventFactory.createEvent(EventType.this.evID, seqNum,
						 handback);
		eventForRegistrationNumber = registrationNumber;
	    }

	    // Try sending 
	    try {
		listener.notify(event);
		return true;
	    } catch (Throwable t) {
		// Classify the exception using ThrowableConstants, if
		// it is a bad object or uncategorized (which must be
		// a UnknownEventException) drop the event
		// registration, if it is a bad invocation mark the
		// try is done (since re-sending this event won't work), 
		// but don't drop the registration.  If indefinite return 
		// false so a retry will be scheduled.
		final int cat = ThrowableConstants.retryable(t);
		if (cat == ThrowableConstants.INDEFINITE) {
		    logger.log(Levels.HANDLED,
			       "Problem sending event -- will retry later",
			       t);
		    return false;
		} else if (cat == ThrowableConstants.BAD_INVOCATION) {
		    logger.log(Level.INFO, "Problem sending event", t);
		    return true;
		} else {
		    EventType.this.monitor.definiteException(EventType.this,
		        event, registrationNumber, t);
		    logger.log(Level.INFO, "Problem sending event", t);
		    return true;
		}
	    }
	    
	}

	// Inherit java doc from super type
	public boolean runAfter(List tasks, int size) {
	    // We don't need to run these tasks in any particular order
	    return false;
	}
    }
}
