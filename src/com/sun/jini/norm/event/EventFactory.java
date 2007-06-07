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

import java.rmi.MarshalledObject;
import net.jini.core.event.RemoteEvent;

/**
 * Factory interface for creating
 * <code>net.jini.core.event.RemoteEvent</code>.  An object of this
 * type is passed to each call of <code>EventType.sendEvent</code> and
 * is used by the <code>EventType</code> object to generate the
 * concrete <code>RemoteEvent</code> associated with a given event
 * occurrence on demand.  Providing a factory to <code>sendEvent</code>
 * instead of an actual <code>RemoteEvent</code> object allows
 * <code>eventType</code> object to send an event originally
 * intended for one listener to another.  
 *
 * @author Sun Microsystems, Inc.
 *
 * @see EventType 
 * @see EventType#sendEvent
 */
public interface EventFactory {
    /**
     * Create the concrete <code>RemoteEvent</code> for the associated
     * event occurrence.  Implementations should allow for the
     * possibility of being called with the same argument more that
     * once, especially the same eventID and seqNum.  The factory
     * should not mutate the event after it returns it. 
     * <p>
     * The caller will own no locks when calling this method.
     * @param eventID the event ID the new event should have
     * @param seqNum  the sequence number the new event object should have
     * @param handback the handback the new event object should have
     * @return the new event object
     */
    public RemoteEvent createEvent(long             eventID, 
				   long             seqNum, 
				   MarshalledObject handback);
}
