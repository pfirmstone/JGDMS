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

import java.io.IOException;
import java.util.NoSuchElementException;

import net.jini.core.event.RemoteEvent;

/**
 * Interface implemented by event storage objects.
 * This class encapsulates the details of reading/writing events from/to
 * some underlying persistence mechanism.
 *
 * This interface makes certain assumptions. First, the <tt>next</tt> and
 * <tt>remove</tt> methods are intended to be called in pairs. If 
 * <tt>remove</tt> is not called, then subsequent calls to <tt>next</tt> 
 * will attempt to return the same object. Calling <tt>remove</tt> 
 * essentially advances the read pointer to the next object, if any. 
 * Second, if any <tt>IOExceptions</tt> are encountered during the reading
 * or writing of an event the associated read/write pointer is advanced
 * past the offending event. This means that events can be lost if I/O 
 * errors are encountered.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */

interface EventLog {

    /**
     * Initializes the state of this <tt>EventLog</tt> object.
     * The required functionality can potentially throw an 
     * <tt>IOException</tt> and has therefore been separated from
     * from object construction.
     *
     * @exception IOException if an I/O error occurs
     */
    void init() throws IOException; 
    
    /**
     * Writes the given <tt>RemoteEvent</tt> to the underlying
     * storage mechanism, if possible. If an <tt>IOException</tt>
     * occurs, then the write cannot be guaranteed.
     *
     * @exception IOException if an I/O error occurs
     */
    void add(RemoteEvent event) throws IOException;
    
    /**
     * Return the next <tt>RemoteEvent</tt> to be read. Note that
     * <tt>next</tt> is meant to be used in conjunction with 
     * <tt>remove</tt>. Subsequent calls to <tt>next</tt> will
     * return the same event until <tt>remove</tt> is called, which
     * actually updates the read pointer to the next event (indicating
     * that the previously read event was successfully processed).
     *
     * @exception IOException if an I/O error occurs
     *
     * @exception ClassNotFoundException if a class for the serialized
     *                object could not be found
     *
     * @exception NoSuchElementException if no event is available 
     */
    RemoteEvent next() throws IOException, ClassNotFoundException;
        
    /**
     * Returns <tt>true</tt> if this log contains no events and
     * false otherwise.
     */
    boolean isEmpty() throws IOException;
    
    /**
     * Effectively removes the last read event from the log.
     * It does this by advancing the read pointer to the 
     * next available event, if any.
     *
     * @exception NoSuchElementException if no events are available
     */
    void remove() throws IOException;
    
    /**
     * Return an array of <tt>RemoteEventData</tt> with a limit of 
     * <tt>maxEvents</tt> elements. Note that
     * <tt>readAhead</tt> is meant to be used in conjunction with 
     * <tt>moveAhead</tt>. Subsequent calls to <tt>readAhead</tt> with
     * the same argument value will return the same set of events until 
     * <tt>moveAhead</tt> is called, which
     * actually updates the read pointer to the next unread event (indicating
     * that the previously read events were successfully processed).
     *
     * @param maxEvents maximum number of events/elements to return
     *
     * @exception IOException if an I/O error occurs
     *
     * @exception ClassNotFoundException if a class for the serialized
     *                object could not be found
     *
     * @exception NoSuchElementException if no event is available 
     */
    RemoteEventData[] readAhead(int maxEvents) 
        throws IOException, ClassNotFoundException;
    
    /**
     * Effectively removes the last set of read events from the log.
     * It does this by advancing the read pointer to the 
     * next available event after the event associated with the provided
     * cookie object.
     *
     * @param cookie object associated with event to read past. This object 
     * should have been obtained from a call to 
     * <code>getCookie()</code> on a <code>RemoteEventData</code> object
     * obtained from a call to <code>readAhead</code> on this event log. 
     *
     * @exception IOException if there was a problem advancing the read pointer.
     * @exception NullPointerException if <code>cookie</code> is null.
     * @exception ClassCastException if <code>cookie</code> 
     * is not an expected type.
     *
     */
    void moveAhead(Object cookie) throws IOException;
        
    /**
     * Close this log and release any associated runtime resources.
     */
    void close() throws IOException;
    
    /**
     * Delete associated storage resources for this log.
     * 
     * @exception IOException if an IO error occurs
     */
    void delete() throws IOException;
    
}
