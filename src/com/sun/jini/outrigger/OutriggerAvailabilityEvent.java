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
package com.sun.jini.outrigger;

import java.io.InvalidObjectException;
import java.rmi.MarshalledObject;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.RemoteEvent;
import net.jini.space.JavaSpace;
import net.jini.space.AvailabilityEvent;

/**
 * Outrigger's implementation of <code>AvailabilityEvent</code>
 */
class OutriggerAvailabilityEvent extends AvailabilityEvent {
    private static final long serialVersionUID = 1L;

    /** The entry that triggered the event */
    final private EntryRep rep;

    /**
     * Constructs an OutriggerAvailabilityEvent object.
     * 
     * @param source    an <code>Object</code> representing the event source
     * @param eventID   a <code>long</code> containing the event identifier
     * @param seqNum    a <code>long</code> containing the event sequence number
     * @param handback  a <code>MarshalledObject</code> that was passed in 
     *                  as part of the original event registration.
     * @param visibilityTransition <code>true</code> if this event
     *                  must also signal a transition from
     *                  invisible to visible
     * @param rep       the entry that triggered the event
     */
    OutriggerAvailabilityEvent(JavaSpace source, long eventID, 
	long seqNum, MarshalledObject handback, boolean visibilityTransition,
	EntryRep rep) 
    {
	super(source, eventID, seqNum, handback, visibilityTransition);
	this.rep = rep;
    }

    /** 
     * @throws InvalidObjectException if called
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "OutriggerAvailabilityEvent should always have data");
    }

    public Entry getEntry() throws UnusableEntryException {
	return rep.entry();
    }

    public Entry getSnapshot() {
	return new SnapshotRep(rep);
    }
}
