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

import java.io.IOException;
import java.util.NoSuchElementException;

import net.jini.core.event.RemoteEvent;

/** 
 * <code>EventLogIterator</code> provides an abstraction for accessing 
 * the events
 * stored on behalf of a particular registration.  The methods closely
 * resemble the <tt>java.util.iterator</tt> interface with the 
 * exception of the <code>throws</code> clauses and the additional 
 * <tt>destroy</tt> and <tt>add</tt> methods.
 *
 * The semantics for this iterator are the same as <tt>java.util.Iterator</tt>
 * in that <tt>next</tt> and <tt>remove</tt> are intended to be called
 * in pairs.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
interface EventLogIterator {
    /** 
     * Store the given <tt>RemoteEvent</tt> event.
     * @throws IOException if an I/O errors occurs
     */
    void add(RemoteEvent re) throws IOException;

    /**  
     * Return true if there are any events in the collection and 
     * false otherwise.
     * @throws IOException if an I/O errors occurs
     */
    boolean hasNext() throws IOException;

    /** 
     * Return the next event in the collection. 
     * @throws IOException if an I/O errors occurs
     * @throws NoSuchElementException if there are no available
     *    <code>RemoteEvent</code>s
     * @throws ClassNotFoundException if there was a problem deserializing
     *    the stored <code>RemoteEvent</code>
     */
    RemoteEvent next() throws IOException, NoSuchElementException,
			      ClassNotFoundException;

    /** 
     * Remove the event at the iterator's current cursor position.
     * It is expected that the cursor position will be updated to 
     * point to the next unread event object, if any, upon return 
     * from this method.
     * @throws IOException if an I/O errors occurs
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
     * should have been obtained from a previous call to <code>readAhead</code>
     * on this event log.
     *
     * @exception IOException if there was a problem advancing the read pointer.
     */
    void moveAhead(Object cookie) throws IOException;
        
    /** 
     * Destroy the collection of stored events. 
     * @throws IOException if an I/O errors occurs
     */
    void destroy() throws IOException;
}

