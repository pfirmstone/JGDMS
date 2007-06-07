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
package com.sun.jini.mercury;

import net.jini.id.Uuid;
import net.jini.core.event.RemoteEvent;

import java.io.IOException;

import com.sun.jini.logging.Levels;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.NoSuchElementException;

/**
 * Class that implements the interface for an <tt>EventLog</tt>. 
 * This class encapsulates the details of reading/writing events from/to
 * some non-persistent mechanism.
 *
 * This class makes certain assumptions. First, the <tt>next</tt> and
 * <tt>remove</tt> methods are intended to be called in pairs. If 
 * <tt>remove</tt> is not called, then subsequent calls to <tt>next</tt> 
 * will attempt to return the same object. Calling <tt>remove</tt> 
 * essentially advances the read pointer to the next object, if any. 
 *
 * There is also an implicit assumption of external synchronization by the
 * caller. That is, only one calling thread will be accessing the log at a time.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */

class TransientEventLog implements EventLog {

    //
    // Class fields
    //

    /** <tt>Logger</tt> used for persistence-related debugging messages */
    private static final Logger persistenceLogger = 
	MailboxImpl.persistenceLogger;

    //
    // Object fields
    //

    /** The associated <tt>Uuid</tt> for this <tt>EventLog</tt>. */
    private Uuid uuid = null;

    /** The associated, non-persistent storage for events */
    private List entries = null;

    /** 
     * Flag that is used to determine whether or not this object 
     * has been closed. 
     */
    private boolean closed = false;

    /**
     * Flag that is used to determine whether or not this object
     * has been initialized.
     */
    private boolean initialized = false;
    
    /**
     * Helper class used to hold a remote event and a sequence id.
     */
    private static class RemoteEventHolder {
        private final long id;
        private final RemoteEvent remoteEvent;
        RemoteEventHolder(long stamp, RemoteEvent re) {
            id = stamp;
            remoteEvent = re;
        }
        long getID() { return id; }
        RemoteEvent getRemoteEvent() { return remoteEvent; }
    }
    
    /**
     * Counter used to produce event ids.
     */
    private long eventCounter = 1L;
    
    /**
     * Simple constructor that takes a <tt>Uuid</tt> argument.
     *
     * @exception IllegalArgumentException if the argument is null
     */
    TransientEventLog(Uuid uuid) {
        if (uuid == null) 
            throw new IllegalArgumentException("Uuid cannot be null");
        this.uuid = uuid;
	entries = Collections.synchronizedList(new LinkedList());

        if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "TransientEventLog for: {0}", uuid);
        }
    }

    // Inherit documentation from supertype
    public void init() throws IOException {
        if (initialized)
            throw new InternalMailboxException(
		"Trying to re-initialize event log "
		+ "for: " + uuid);
        initialized = true;
    }
    
    /**
     * Asserts that the log is in a valid state.
     *
     * @exception IOException if the log is in an invalid state
     */
    private void stateCheck() throws IOException {
	if (!initialized)
            throw new IOException("Trying to use an uninitialized "
		+ "event log for: " + uuid);
	if (closed)
            throw new IOException("Attempt to access closed log file for : "
		+ uuid);
    }

    // Inherit documentation from supertype
    public void add(RemoteEvent event) throws IOException {
	stateCheck();
        long id = eventCounter++; 
        RemoteEventHolder data = new RemoteEventHolder(id, event);
	entries.add(data);
        printControlData(persistenceLogger, "TransientEventLog::add");
    }

    // Inherit documentation from supertype
    public RemoteEvent next() throws IOException {
	stateCheck();
        // Check if empty
	if (isEmpty()) 
	    throw new NoSuchElementException();

        printControlData(persistenceLogger, "TransientEventLog::next");
        RemoteEventHolder data = (RemoteEventHolder)entries.get(0);
        return (RemoteEvent)data.getRemoteEvent();
    }
    
    // Inherit documentation from supertype
    public RemoteEventData[] readAhead(int maxEvents) throws IOException {
	stateCheck();
        
        if (maxEvents < 0)
            throw new IllegalArgumentException();
        
        if (maxEvents == 0)
            return new RemoteEventData[0];
        
        // Check if empty
	if (isEmpty()) 
	    throw new NoSuchElementException();

        printControlData(persistenceLogger, "TransientEventLog::readAhead");
        int limit = (maxEvents < entries.size())?maxEvents:entries.size();
        RemoteEventHolder[] evts = (RemoteEventHolder[])
            entries.subList(0, limit).toArray(new RemoteEventHolder[0]);
        RemoteEventData[] set = new RemoteEventData[evts.length];
        for (int i=0; i<set.length; i++) {
            set[i] = new RemoteEventData(
                evts[i].getRemoteEvent(), new Long(evts[i].getID()));
        }
        return set;
    }
    
    // Inherit documentation from supertype
    public boolean isEmpty() throws IOException {
	stateCheck();
        return entries.isEmpty();
    }

    // Inherit documentation from supertype
    public void remove() throws IOException {
	stateCheck();
	try {
	    entries.remove(0);
	} catch (IndexOutOfBoundsException iob) {
	    throw new NoSuchElementException();
	}
        printControlData(persistenceLogger, "TransientEventLog::remove");
    }

    // Inherit documentation from supertype
    public void moveAhead(Object cookie) throws IOException {
	stateCheck();

	if (cookie == null) return;
        
	if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "moveAhead past {0}", 
                cookie);
	}
        // TODO - trap ClassCastException and throw?
        long lastID = ((Long)cookie).longValue();

	if (lastID >= eventCounter) {
	    throw new NoSuchElementException();
	}
        
        RemoteEventHolder rh = null;
        ListIterator iter = entries.listIterator();
        while (iter.hasNext()) {
            rh = (RemoteEventHolder)iter.next();
            if (rh.getID() <= lastID) {
                iter.remove();
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
                        "Removing event with ID {0}", 
                        new Long(rh.getID()));
                }
            } else {
                break;
            }
                
        }
        printControlData(persistenceLogger, "TransientEventLog::moveAhead");
    }
    
    // Inherit documentation from supertype
    public void close() throws IOException {
	stateCheck();
        closed = true;
	if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "TransientEventLog::close for {0}", uuid);
	}
	// Do nothing
    }

    // Inherit documentation from supertype
    public void delete() throws IOException {
        if (!closed)
            throw new IOException("Cannot delete log until it is closed");
	entries.clear();
	if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "TransientEventLog::destroy for {0}", uuid);
	}
    }

    
    /**
     * Output state information to the given <tt>Logger</tt>.
     * This is intended for debugging purposes only.
     */
    private void printControlData(Logger logger, String msg) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "{0}", msg);
            logger.log(Level.FINEST, "ID: {0}", uuid);
            logger.log(Level.FINEST, "NumEvents: {0}", 
	        new Long(entries.size()));
	}
    }
}
