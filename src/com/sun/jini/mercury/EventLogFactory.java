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


import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.LinkedList;
import java.util.List;

import net.jini.core.event.RemoteEvent;
 
/**
 * This class serves as a factory for generating <tt>EventLogIterator</tt>
 * objects. The iterator objects are cached so that subsequent calls 
 * for the same iterator return the same object.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class EventLogFactory {

    /** <tt>Map</tt> that contains references to generated iterators */
    private HashMap iterators = new HashMap();

    /** 
     * Method to return the iterator object for the designated 
     * <tt>Uuid</tt>. The <tt>File</tt> argument determines where the
     * persistence store will be maintained and is only used for the
     * first instance of the iterator. (Note that upon recovery
     * from a restart/crash the <tt>Uuid</tt> will already exist so the
     * <tt>logPath</tt> arg will not be used.)
     * Subsequent calls for the same <tt>Uuid</tt> will return the 
     * original object.
     */
    public EventLogIterator iterator(Uuid uuid, File logPath) {

        // Try to get reference from cache
        EventLogIteratorImpl eli = (EventLogIteratorImpl)iterators.get(uuid);
        if (eli == null) { // doesn't exist, so create one
            eli = new EventLogIteratorImpl(uuid, logPath);
	    try { 
		eli.init(); // initialize the iterator
	    } catch (IOException ioe) {
		// ignore ... the next usage of this
		// object will throw IOException
	    }
            iterators.put(uuid, eli); // add to cache
	}
            
	return eli;
    }
    
    /** 
     * Method to return the iterator object for the designated 
     * <tt>Uuid</tt>. 
     * Subsequent calls for the same <tt>Uuid</tt> will return the 
     * original object.
     */
    public EventLogIterator iterator(Uuid uuid) {

        // Try to get reference from cache
        EventLogIteratorImpl eli = (EventLogIteratorImpl)iterators.get(uuid);
        if (eli == null) { // doesn't exist, so create one
            eli = new EventLogIteratorImpl(uuid);
	    try { 
		eli.init(); // initialize the iterator
	    } catch (IOException ioe) {
		throw new InternalMailboxException(
                    "Received unexpected IOException from"
                    + " a non-persistent log", ioe);
	    }
            iterators.put(uuid, eli); // add to cache
	}
            
	return eli;
    }

    /**
     * Remove the <TT>EventLogIterator</TT> associated with the given
     * <TT>Uuid</TT>. This is (presumably) called to flush an existing
     * <TT>EventLogIterator</TT> whose storage location has changed.
     * This way, the next call to <TT>iterator</TT> will produce a new
     * object instead of returning the cached version.
     */
    public void remove(Uuid uuid) {
        iterators.remove(uuid); // remove from cache, if it's there
    }

    /**
     * Private class which implements the <tt>EventLogIterator</tt>
     * interface. This class delegates to an <tt>EventLog</tt> for most
     * of its functionality.
     */
    private static class EventLogIteratorImpl 
	implements EventLogIterator
    {
      
        /** The associated <tt>Uuid</tt> for this iterator */
        private final Uuid uuid;

        /** The associated <tt>EventLog</tt> for this iterator */
        private EventLog log = null; 

        /** 
         * Simple constructor that assigns the <tt>Uuid</tt>
         * field to the appropriate internal field and creates
         * a  persistent <tt>EventLog</tt> object using the provided
         * <tt>File</tt> argument.
         */
        EventLogIteratorImpl(Uuid id, File logPath) {
            uuid = id;
	    log = new PersistentEventLog(id, logPath);
        }

        /** 
         * Simple constructor that assigns the <tt>Uuid</tt>
         * field to the appropriate internal field and creates
	 * a transient event log.
	 */
        EventLogIteratorImpl(Uuid id) {
            uuid = id;
	    log = new TransientEventLog(id);
        }

	// Inherit documentation from supertype
        public void init() throws IOException {
	    log.init();
	}

        // Inherit documentation from supertype
        public void add(RemoteEvent evt) throws IOException, 
		IllegalArgumentException 
	{
            log.add(evt);
	}
            
        // Inherit documentation from supertype
        public boolean hasNext()  throws IOException {
            return !log.isEmpty();
        }

        // Inherit documentation from supertype
        public RemoteEvent next() throws IOException, 
		ClassNotFoundException, NoSuchElementException 
	{
            return log.next();
        }

        // Inherit documentation from supertype
        public void remove() throws IOException, IllegalStateException {
            log.remove();
        }

        // Inherit documentation from supertype
        public RemoteEventData[] readAhead(int maxEvents) 
            throws IOException, ClassNotFoundException 
        {
            return log.readAhead(maxEvents);
        }

        // Inherit documentation from supertype
        public void moveAhead(Object cookie) throws IOException {
            log.moveAhead(cookie);
        }
        
        // Inherit documentation from supertype
        public void destroy() throws IOException {
            log.close();
	    log.delete();
        }
    }
}


