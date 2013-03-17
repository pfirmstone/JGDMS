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
package net.jini.space;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.RemoteEvent;

/**
 * A <code>RemoteEvent</code> marking the transition of an
 * <code>Entry</code> from {@link
 * JavaSpace05#registerForAvailabilityEvent <em>unavailable</em> to
 * <em>available</em>}.<p>
 * 
 * Note, by the time the event is delivered, the
 * <code>Entry</code> whose transition triggered this event may
 * have transitioned to a state where it is no longer visible
 * and/or available.<p>
 *
 * @see JavaSpace05
 * @since 2.1 
 */
public abstract class AvailabilityEvent extends RemoteEvent {
    private static final long serialVersionUID = 1L;

    /** 
     * <code>true</code> if this event signals a
     * transition from invisible to visible as well
     * as unavailable to available.
     *
     * @serial 
     */
    private final boolean visibilityTransition;

    /**
     * Create a new <code>AvailabilityEvent</code> instance.
     * @param source    the event source
     * @param eventID   the event identifier
     * @param seqNum    the event sequence number
     * @param handback  the handback object
     * @param visibilityTransition <code>true</code> if this event
     *                  must also signal a transition from
     *                  invisible to visible
     * @throws NullPointerException if <code>source</code> is
     *        <code>null</code>
     */
    protected AvailabilityEvent(JavaSpace        source,
				long             eventID,
				long             seqNum,
				MarshalledObject handback,
				boolean          visibilityTransition) 
    {
	super(source, eventID, seqNum, handback);
	this.visibilityTransition = visibilityTransition;
    }

    /**
     * @throws InvalidObjectException if {@link #source} is <code>null</code>
     * or is not a {@link JavaSpace}
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (getSource() == null)
	    throw new InvalidObjectException("null source reference");

	if (!(getSource() instanceof JavaSpace)) 
	    throw new InvalidObjectException("source is not a JavaSpace");
    }

    /** 
     * @throws InvalidObjectException if called
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "AvailabilityEvent should always have data");
    }

    /**
     * Returns a copy of the {@link Entry} whose transition
     * triggered this event. The returned <code>Entry</code> must
     * be unmarshalled in accordance with the <a
     * href=http://www.jini.org/standards/index.html>Jini
     * Entry Specification</a>.
     * 
     * @return a copy of the {@link Entry} whose transition
     *         triggered this event
     * @throws UnusableEntryException if the <code>Entry</code>
     *         can't be unmarshalled in the client. The next call
     *         must re-attempt unmarshalling the
     *         <code>Entry</code> 
     */
    public abstract Entry getEntry() throws UnusableEntryException;

    /**
     * Returns a <em>snapshot</em> of the {@link Entry} whose
     * transition triggered this event. Snapshots are defined in
     * section JS.2.6 of the <a
     * href=http://www.jini.org/standards/index.html>
     * JavaSpaces Service Specification</a> and are an
     * alternative representation of a given <code>Entry</code>
     * produced by a particular space for use with that same
     * space. Passing a snapshot to a space is generally more
     * efficient than passing the original <code>Entry</code>.<p>
     * 
     * Any snapshot returned by this method will meet the same
     * contract as the object returned by passing the result of
     * {@link #getEntry getEntry} to {@link JavaSpace#snapshot
     * JavaSpace.snapshot}.<p>
     *
     * Generally there is a cost associated with calling the
     * <code>JavaSpace.snapshot</code> method and thus creating a
     * snapshot using that method is usually only worthwhile if
     * the resulting snapshot is used more than once. The cost of
     * invoking this method should be low and should be worthwhile
     * even if the resulting snapshot is used only once. <p>
     * 
     * @return a <em>snapshot</em> of the {@link Entry} whose
     *         transition triggered this event 
     */
    public abstract Entry getSnapshot();

    /**
     * Returns <code>true</code> if the transition that triggered
     * this event was a transition from {@linkplain
     * JavaSpace05#registerForAvailabilityEvent <em>invisible to
     * visible</em>} as well as a transition from unavailable to
     * available, and <code>false</code> otherwise. <p>
     *
     * @return <code>true</code> if the transition that triggered
     * this event was a transition from invisible to visible as
     * well as a transition from unavailable to available, and
     * <code>false</code> otherwise 
     */
    public boolean isVisibilityTransition() {
	return visibilityTransition;
    }
}

