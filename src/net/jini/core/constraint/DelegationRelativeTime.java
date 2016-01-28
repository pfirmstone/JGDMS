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

package net.jini.core.constraint;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Represents a constraint on delegation, such that if delegation is permitted,
 * it be permitted only for a range of time measured relative to the start of
 * the remote call. The mechanisms and credentials used to support this are
 * not specified by this constraint. Each end of the range is itself specified
 * as a range, yielding four durations, all specified in milliseconds. If
 * <code>t</code> represents the current time at the start of a remote call,
 * then the four durations have the following semantics:
 * <ul>
 * <li><code>minStart</code> - delegation must not be permitted any earlier
 * than time <code>t + minStart</code>
 * <li><code>maxStart</code> - delegation must be permitted from time
 * <code>t + maxStart</code> onwards
 * <li><code>minStop</code> - delegation must be permitted up until at least
 * time <code>t + minStop</code>
 * <li><code>maxStop</code> - delegation must not be permitted after time
 * <code>t + maxStop</code>
 * </ul>
 * The durations are translated into absolute end times at the point of a
 * remote call by adding the caller's current time. To accommodate clock skew
 * between systems, it is permitted (and may be desirable) to specify negative
 * values for the minimum and maximum start durations, to include start times
 * that are earlier than the current time.
 * <p>
 * The use of an instance of this class does not directly imply a
 * {@link ClientAuthentication#YES} constraint or a {@link Delegation#YES}
 * constraint; those must be specified separately to ensure that the client
 * actually authenticates itself and that delegation is actually used.
 * Because this constraint is conditional on delegation, it does not conflict
 * with {@link ClientAuthentication#NO} or {@link Delegation#NO}.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientAuthentication
 * @see Delegation
 * @see DelegationAbsoluteTime
 * @since 2.0
 */
@AtomicSerial
public final class DelegationRelativeTime
			implements RelativeTimeConstraint, Serializable
{
    private static final long serialVersionUID = 7148935984332761810L;

    /**
     * The minimum start duration in milliseconds.
     *
     * @serial
     */
    private final long minStart;
    /**
     * The maximum start duration in milliseconds.
     *
     * @serial
     */
    private final long maxStart;
    /**
     * The minimum stop duration in milliseconds.
     *
     * @serial
     */
    private final long minStop;
    /**
     * The maximum stop duration in milliseconds.
     *
     * @serial
     */
    private final long maxStop;

    /**
     * Creates a constraint with the specified durations.
     *
     * @param minStart the minimum start duration in milliseconds
     * @param maxStart the maximum start duration in milliseconds
     * @param minStop the minimum stop duration in milliseconds
     * @param maxStop the maximum stop duration in milliseconds
     * @throws IllegalArgumentException if <code>minStart</code> is greater
     * than <code>maxStart</code>, or <code>maxStart</code> is greater than
     * <code>minStop</code>, or <code>minStop</code> is greater than
     * <code>maxStop</code>, or <code>minStop</code> is less than zero
     */
    public DelegationRelativeTime(long minStart,
				  long maxStart,
				  long minStop,
				  long maxStop)
    {
	if (minStart > maxStart || maxStart > minStop || minStop > maxStop ||
	    minStop < 0)
	{
	    throw new IllegalArgumentException("invalid durations");
	}
	this.minStart = minStart;
	this.maxStart = maxStart;
	this.minStop = minStop;
	this.maxStop = maxStop;
    }
    
    private DelegationRelativeTime(long minStart,
				   long maxStart,
				   long minStop,
				   long maxStop,
				   boolean check) throws InvalidObjectException
    {
	this(validate(minStart, maxStart, minStop, maxStop),
		minStart, maxStart, minStop, maxStop);
    }
    
    private DelegationRelativeTime(boolean check,
				   long minStart,
				   long maxStart,
				   long minStop,
				   long maxStop)
    {
	this.minStart = minStart;
	this.maxStart = maxStart;
	this.minStop = minStop;
	this.maxStop = maxStop;
    }
    
    
    
    public DelegationRelativeTime(GetArg arg) throws IOException{
	this(arg.get("minStart", 0L),
	     arg.get("maxStart", 0L),
	     arg.get("minStop", 0L),
	     arg.get("maxStop", 0L),
	     true);
    }
    
    private static boolean validate(long minStart,
				   long maxStart,
				   long minStop,
				   long maxStop) throws InvalidObjectException
    {
	if (minStart > maxStart || maxStart > minStop || minStop > maxStop ||
	    minStop < 0)
	{
	    throw new InvalidObjectException("invalid durations");
	}
	return true;
    }
	

    /**
     * Returns the minimum start duration in milliseconds.
     *
     * @return the minimum start duration in milliseconds
     */
    public long getMinStart() {
	return minStart;
    }

    /**
     * Returns the maximum start duration in milliseconds.
     *
     * @return the maximum start duration in milliseconds
     */
    public long getMaxStart() {
	return maxStart;
    }

    /**
     * Returns the minimum stop duration in milliseconds.
     *
     * @return the minimum stop duration in milliseconds
     */
    public long getMinStop() {
	return minStop;
    }

    /**
     * Returns the maximum stop duration in milliseconds.
     *
     * @return the maximum stop duration in milliseconds
     */
    public long getMaxStop() {
	return maxStop;
    }

    /**
     * Returns a {@link DelegationAbsoluteTime} instance with times obtained
     * by adding the specified base time argument to the duration values
     * from this instance. If an addition results in underflow or overflow, a
     * time value of <code>Long.MIN_VALUE</code> or
     * <code>Long.MAX_VALUE</code> is used, respectively.
     */
    public InvocationConstraint makeAbsolute(long baseTime) {
	return new DelegationAbsoluteTime(add(minStart, baseTime),
					  add(maxStart, baseTime),
					  add(minStop, baseTime),
					  add(maxStop, baseTime));
    }

    private static long add(long dur, long time) {
	long ntime = time + dur;
	if (ntime >= 0 && time < 0 && dur < 0) {
	    ntime = Long.MIN_VALUE;
	} else if (ntime < 0 && time > 0 && dur > 0) {
	    ntime = Long.MAX_VALUE;
	}
	return ntime;
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return (int)(DelegationRelativeTime.class.hashCode() +
		     minStart + maxStart + minStop + maxStop);
    }

    /**
     * Two instances of this class are equal if both have the same minimum
     * start, the same maximum start, the same minimum stop, and the same
     * maximum stop.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof DelegationRelativeTime)) {
	    return false;
	}
	DelegationRelativeTime dc = (DelegationRelativeTime) obj;
	return (minStart == dc.minStart && maxStart == dc.maxStart &&
		minStop == dc.minStop && maxStop == dc.maxStop);
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	String s = "DelegationRelativeTime[start: ";
	if (minStart == maxStart) {
	    s += minStart + ", stop: ";
	} else {
	    s += "[" + minStart + ", " + maxStart + "], stop: ";
	}
	if (minStop == maxStop) {
	    s += minStop + "]";
	} else {
	    s += "[" + minStop + ", " + maxStop + "]]";
	}
	return s;
    }

    /**
     * Verifies that <code>minStart</code> is less than or equal to
     * <code>maxStart</code>, <code>maxStart</code> is less than or equal to
     * <code>minStop</code>, <code>minStop</code> is less than or equal to
     * <code>maxStop</code>, and <code>minStop</code> is greater than or equal
     * to zero.
     *
     * @throws InvalidObjectException if <code>minStart</code> is greater
     * than <code>maxStart</code>, or <code>maxStart</code> is greater than
     * <code>minStop</code>, or <code>minStop</code> is greater than
     * <code>maxStop</code>, or <code>minStop</code> is less than zero
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	if (minStart > maxStart || maxStart > minStop || minStop > maxStop ||
	    minStop < 0)
	{
	    throw new InvalidObjectException("invalid durations");
	}
    }
}
