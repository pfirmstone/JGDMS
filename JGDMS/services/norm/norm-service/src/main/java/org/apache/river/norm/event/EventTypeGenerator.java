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
package org.apache.river.norm.event;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.AccessControlContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.thread.NamedThreadFactory;
import org.apache.river.thread.wakeup.WakeupManager;

/**
 * Factory class for <code>EventType</code> objects.  All
 * <code>EventType</code> objects created by the same generator (or
 * associated with the same generator by a
 * <code>EventType.restoreTransientState</code> call) will use the same
 * thread pool to manage their event send threads.
 *
 * @author Sun Microsystems, Inc.
 * @see EventType 
 * @see EventType#restoreTransientState
 */
@AtomicSerial
public class EventTypeGenerator implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Next event ID.
     * @serial
     */
    private long nextEvID;

    /**
     * ExecutorService used to send events
     */
    private transient ExecutorService taskManager;

    /**
     * Wakeup manager used by the event sending tasks to schedule 
     * retries.
     */
    private transient WakeupManager wakeupManager;

    /**
     * Create a new <code>EventType</code> object specify the 
     * event id it should have.
     *
     * @param eventID  the event ID of this type
     * @param monitor  Object to callback when an event sending
     *        attempt fails with a definite exception and to 
     *        ensure that the lease on the event is still current.
     *        May not be <code>null</code>.
     * @return the new <code>EventType</code> object
     * @throws IOException if listener cannot be serialized 
     */
    public EventType newEventType(SendMonitor monitor, long eventID, AccessControlContext context)
	throws IOException
    {
	return new EventType(this, monitor, eventID, null, null, context);
    }

    /**
     * Called by event types during transient state recovery to ensure
     * the generator knows about there event ID.
     * <p>
     * Note: this method is not synchronized.
     * @param evID event ID of recovered <code>EventType</code> object
     */
    synchronized void recoverEventID(long evID) {
	if (evID >= nextEvID)
	    nextEvID = evID + 1;
    }

    /**
     * Return the ExecutorService that <code>EventType</code> objects created
     * by this generator should use to send their events.
     */
    ExecutorService getExecutorService() {
	return taskManager;
    }

    /**
     * Return the wakeup manager that <code>EventType</code> objects created
     * by this generator should use to send their events.
     */
    WakeupManager getWakeupManager() {
	return wakeupManager;
    }

    /**
     * Terminate any independent treads started by event types
     * associated with this generator.
     */
    public void terminate() {
	taskManager.shutdown();
	wakeupManager.stop();
	wakeupManager.cancelAll();
    }

    /**
     * Override <code>readObject</code> to create a <code>TaskManager</code> 
     * and a <code>WakeupManager</code>.
     * @see ObjectInputStream#defaultReadObject
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	synchronized(this){
	    // fill in the object from the stream 
	    in.defaultReadObject();

	    taskManager = new ThreadPoolExecutor(
			10,
			10, /* Ignored */
			15,
			TimeUnit.SECONDS, 
			new LinkedBlockingQueue<Runnable>(), /* Unbounded Queue */
			new NamedThreadFactory("EventTypeGenerator", false)
	    );
	    wakeupManager = 
		new WakeupManager(new WakeupManager.ThreadDesc(null, false));   
	}
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException{
	synchronized(this){
	    out.defaultWriteObject();
	}
    }
    
    public EventTypeGenerator(GetArg arg) throws IOException{
	this(arg.get("nextEvID", 1L));
    }
    
    private EventTypeGenerator(long nextEvID){
	this.nextEvID = nextEvID;
	this.wakeupManager = new WakeupManager(new WakeupManager.ThreadDesc(null, false));
	this.taskManager = new ThreadPoolExecutor(
		10,
		10, /* Ignored */
		15,
		TimeUnit.SECONDS,
		new LinkedBlockingQueue<Runnable>(), /* Unbounded queue */
		new NamedThreadFactory("EventTypeGenerator", false)
	);
    }
    
    public EventTypeGenerator(){
	this.nextEvID = 1;
	this.wakeupManager = new WakeupManager(new WakeupManager.ThreadDesc(null, false));
	this.taskManager = new ThreadPoolExecutor(
		10,
		10, /* Ignored */
		15,
		TimeUnit.SECONDS,
		new LinkedBlockingQueue<Runnable>(), /* Unbounded queue */
		new NamedThreadFactory("EventTypeGenerator", false)
	);
    }
}
