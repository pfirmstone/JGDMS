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
import java.lang.ref.SoftReference;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Represents a constraint on delegation, such that if delegation is permitted,
 * it be permitted only for a range of absolute times. The mechanisms and
 * credentials used to support this are not specified by this constraint. Each
 * end of the range is itself specified as a range, yielding four absolute
 * times, all specified in milliseconds from midnight, January 1, 1970 UTC.
 * The four times have the following semantics:
 * <ul>
 * <li><code>minStart</code> - delegation must not be permitted any earlier
 * than this time
 * <li><code>maxStart</code> - delegation must be permitted from this time
 * onwards
 * <li><code>minStop</code> - delegation must be permitted up until at least
 * this time
 * <li><code>maxStop</code> - delegation must not be permitted after this time
 * </ul>
 * To accommodate clock skew between systems, it may be desirable to specify
 * start times that are earlier than the current time.
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
 * @see DelegationRelativeTime
 * @since 2.0
 */
@AtomicSerial
public final class DelegationAbsoluteTime
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = -2807470616717350051L;

    /**
     * The minimum start time in milliseconds from midnight, January 1, 1970
     * UTC.
     *
     * @serial
     */
    private final long minStart;
    /**
     * The maximum start time in milliseconds from midnight, January 1, 1970
     * UTC.
     *
     * @serial
     */
    private final long maxStart;
    /**
     * The minimum stop time in milliseconds from midnight, January 1, 1970
     * UTC.
     *
     * @serial
     */
    private final long minStop;
    /**
     * The maximum stop time in milliseconds from midnight, January 1, 1970
     * UTC.
     *
     * @serial
     */
    private final long maxStop;

    /**
     * SoftReference containing a SimpleDateFormat instance, or null.
     */
    private static SoftReference formatterRef;

    /**
     * Creates a constraint with the specified absolute times.
     *
     * @param minStart the minimum start time in milliseconds from midnight,
     * January 1, 1970 UTC
     * @param maxStart the maximum start time in milliseconds from midnight,
     * January 1, 1970 UTC
     * @param minStop the minimum stop time in milliseconds from midnight,
     * January 1, 1970 UTC
     * @param maxStop the maximum stop time in milliseconds from midnight,
     * January 1, 1970 UTC
     * @throws IllegalArgumentException if <code>minStart</code> is greater
     * than <code>maxStart</code>, or <code>maxStart</code> is greater than
     * <code>minStop</code>, or <code>minStop</code> is greater than
     * <code>maxStop</code>
     */
    public DelegationAbsoluteTime(long minStart,
				  long maxStart,
				  long minStop,
				  long maxStop)
    {
	this(minStart, maxStart, minStop, maxStop,
		check(minStart, maxStart, minStop, maxStop));
    }
    
    /**
     * Normal constructor check invariants.
     */
    private static boolean check(long minStart,
				   long maxStart,
				   long minStop,
				   long maxStop)
    {
	if (minStart > maxStart || maxStart > minStop || minStop > maxStop) {
	    throw new IllegalArgumentException("illegal times");
	}
	return true;
    }
    
    /**
     * private constructor that validates AtomicSerial construction.
     * @param deserialization
     * @param minStart
     * @param maxStart
     * @param minStop
     * @param maxStop
     * @throws InvalidObjectException 
     */
    private DelegationAbsoluteTime(boolean deserialization,
				   long minStart,
				   long maxStart,
				   long minStop,
				   long maxStop ) throws InvalidObjectException
    {
	this(minStart, maxStart, minStop, maxStop,
		deserialization ? validate(minStart, maxStart, minStop, maxStop):
			check(minStart, maxStart, minStop, maxStop));
    }
    
    private DelegationAbsoluteTime(long minStart,
				   long maxStart,
				   long minStop,
				   long maxStop,
				   boolean check)
    {
	this.minStart = minStart;
	this.maxStart = maxStart;
	this.minStop = minStop;
	this.maxStop = maxStop;
    }

    /**
     * Creates a constraint with the specified dates. The arguments passed to
     * the constructor are neither modified nor retained; subsequent changes
     * to those arguments have no effect on the instance created.
     *
     * @param minStart the minimum start date
     * @param maxStart the maximum start date
     * @param minStop the minimum stop date
     * @param maxStop the maximum stop date
     * @throws NullPointerException if any argument is <code>null</code>
     * @throws IllegalArgumentException if <code>minStart</code> is later
     * than <code>maxStart</code>, or <code>maxStart</code> is later than
     * <code>minStop</code>, or <code>minStop</code> is later than
     * <code>maxStop</code>
     */
    public DelegationAbsoluteTime(Date minStart,
				  Date maxStart,
				  Date minStop,
				  Date maxStop)
    {
	this(minStart.getTime(), maxStart.getTime(),
	     minStop.getTime(), maxStop.getTime());
    }
    
    /**
     * AtomicSerial public constructor.
     * @param arg
     * @throws IOException 
     */
    public DelegationAbsoluteTime(GetArg arg) throws IOException{
	this(true, 
	    arg.get("minStart", 0L),
	    arg.get("maxStart", 0L),
	    arg.get("minStop", 0L),
	    arg.get("maxStop", 0L));
    }
    
    /**
     * Deserialization input validation
     * 
     * @param minStart
     * @param maxStart
     * @param minStop
     * @param maxStop
     * @return
     * @throws InvalidObjectException 
     */
    private static boolean validate(long minStart, long maxStart, long minStop, long maxStop) throws InvalidObjectException{
	if (minStart > maxStart || maxStart > minStop || minStop > maxStop) {
	    throw new InvalidObjectException("invalid times");
	}
	return true;
    }

    /**
     * Returns the minimum start time in milliseconds from midnight, January 1,
     * 1970 UTC.
     *
     * @return the minimum start time in milliseconds from midnight, January 1,
     * 1970 UTC
     */
    public long getMinStart() {
	return minStart;
    }

    /**
     * Returns the maximum start time in milliseconds from midnight, January 1,
     * 1970 UTC.
     *
     * @return the maximum start time in milliseconds from midnight, January 1,
     * 1970 UTC
     */
    public long getMaxStart() {
	return maxStart;
    }

    /**
     * Returns the minimum stop time in milliseconds from midnight, January 1,
     * 1970 UTC.
     *
     * @return the minimum stop time in milliseconds from midnight, January 1,
     * 1970 UTC
     */
    public long getMinStop() {
	return minStop;
    }

    /**
     * Returns the maximum stop time in milliseconds from midnight, January 1,
     * 1970 UTC.
     *
     * @return the maximum stop time in milliseconds from midnight, January 1,
     * 1970 UTC
     */
    public long getMaxStop() {
	return maxStop;
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return (int)(DelegationAbsoluteTime.class.hashCode() +
		     minStart + maxStart + minStop + maxStop);
    }

    /**
     * Two instances of this class are equal if both have the same minimum
     * start, the same maximum start, the same minimum stop, and the same
     * maximum stop.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof DelegationAbsoluteTime)) {
	    return false;
	}
	DelegationAbsoluteTime dc = (DelegationAbsoluteTime) obj;
	return (minStart == dc.minStart && maxStart == dc.maxStart &&
		minStop == dc.minStop && maxStop == dc.maxStop);
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	SimpleDateFormat formatter = getFormatter();
	FieldPosition pos = new FieldPosition(0);
	StringBuffer buf = new StringBuffer(95);
	buf.append("DelegationAbsoluteTime[start: ");
	format(minStart, maxStart, formatter, buf, pos);
	buf.append(", stop: ");
	format(minStop, maxStop, formatter, buf, pos);
	buf.append(']');
	return buf.toString();
    }

    /**
     * Format a min,max time pair.
     */
    private static void format(long min,
			       long max,
			       SimpleDateFormat formatter,
			       StringBuffer buf,
			       FieldPosition pos)
    {
	if (min == max) {
	    formatter.format(new Date(min), buf, pos);
	} else {
	    buf.append('[');
	    formatter.format(new Date(min), buf, pos);
	    buf.append(", ");
	    formatter.format(new Date(max), buf, pos);
	    buf.append(']');
	}
    }

    /**
     * Returns a formatter for "yyyy.MM.dd HH:mm:ss.SSSS zzz".
     */
    private static synchronized SimpleDateFormat getFormatter() {
	SimpleDateFormat formatter = null;
	if (formatterRef != null) {
	    formatter = (SimpleDateFormat) formatterRef.get();
	}
	if (formatter == null) {
	    formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSSS zzz",
					     Locale.US);
	    formatterRef = new SoftReference(formatter);
	}
	return formatter;
    }

    /**
     * Verifies that <code>minStart</code> is less than or equal to
     * <code>maxStart</code>, <code>maxStart</code> is less than or equal to
     * <code>minStop</code>, and <code>minStop</code> is less than or equal to
     * <code>maxStop</code>.
     *
     * @throws InvalidObjectException if <code>minStart</code> is greater
     * than <code>maxStart</code>, or <code>maxStart</code> is greater than
     * <code>minStop</code>, or <code>minStop</code> is greater than
     * <code>maxStop</code>
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	if (minStart > maxStart || maxStart > minStop || minStop > maxStop) {
	    throw new InvalidObjectException("invalid times");
	}
    }
}
