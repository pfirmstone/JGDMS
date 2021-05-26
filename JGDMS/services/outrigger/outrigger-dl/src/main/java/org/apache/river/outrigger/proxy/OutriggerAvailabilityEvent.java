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
package org.apache.river.outrigger.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.rmi.MarshalledObject;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.export.ProxyAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.space.AvailabilityEvent;
import net.jini.space.JavaSpace;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Outrigger's implementation of <code>AvailabilityEvent</code>
 */
@AtomicSerial
public class OutriggerAvailabilityEvent extends AvailabilityEvent implements ProxyAccessor {
    private static final long serialVersionUID = 1L;

    /** The entry that triggered the event */
    final private EntryRep rep;

    private static GetArg check(GetArg arg) 
	    throws IOException, ClassNotFoundException{
	EntryRep rep = arg.get("rep", null, EntryRep.class);
	if (rep == null)
	    throw new InvalidObjectException(
	    "OutriggerAvailabilityEvent should always have data");
	return arg;
    }
    
    OutriggerAvailabilityEvent(GetArg arg) 
	    throws IOException, ClassNotFoundException{
	super(check(arg));
	rep = (EntryRep) arg.get("rep", null);
    }

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
     * @deprecated
     */
    @Deprecated
    public OutriggerAvailabilityEvent(JavaSpace source, long eventID, 
	long seqNum, MarshalledObject handback, boolean visibilityTransition,
	EntryRep rep) 
    {
	super(source, eventID, seqNum, handback, visibilityTransition);
	this.rep = rep;
    }
    
    /**
     * Constructs an OutriggerAvailabilityEvent object.
     * 
     * @param source    an <code>Object</code> representing the event source
     * @param eventID   a <code>long</code> containing the event identifier
     * @param seqNum    a <code>long</code> containing the event sequence number
     * @param handback  a <code>MarshalledInstance</code> that was passed in 
     *                  as part of the original event registration.
     * @param visibilityTransition <code>true</code> if this event
     *                  must also signal a transition from
     *                  invisible to visible
     * @param rep       the entry that triggered the event
     */
    public OutriggerAvailabilityEvent(JavaSpace source, long eventID, 
	long seqNum, MarshalledInstance handback, boolean visibilityTransition,
	EntryRep rep) 
    {
	super(source, eventID, seqNum, handback, visibilityTransition);
	this.rep = rep;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
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

    public Object getProxy() {
	Object src = getSource();
	if (src instanceof SpaceProxy2){
	    return ((SpaceProxy2)src).getProxy();
	}
	return src;
    }
    
    EntryRep getEntryRep(){
	return rep;
    }
}
