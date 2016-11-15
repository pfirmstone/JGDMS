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
 * Represents a constraint on the maximum amount of time to wait for a
 * network connection to be established. The precise meaning of this will
 * vary across communication mechanisms, but in the typical case of
 * {@link java.net.Socket}-based communication, the intention is that this
 * constraint controls the timeout parameter of the
 * {@link java.net.Socket#connect(java.net.SocketAddress,int) connect} method.
 * <p>
 * The duration is translated into an absolute end time at the point of a
 * remote call by adding the caller's current time.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
public final class ConnectionRelativeTime
			implements RelativeTimeConstraint, Serializable
{
    private static final long serialVersionUID = 6854732178792183150L;

    /**
     * The maximum connection duration in milliseconds.
     *
     * @serial
     */
    private final long time;

    /**
     * Creates a constraint with the specified duration.
     *
     * @param time the maximum connection duration in milliseconds
     * @throws IllegalArgumentException if the argument is less than zero
     */
    public ConnectionRelativeTime(long time) {
	this(check(time), true);
    }
    
    /**
     * AtomicSerial constructor
     * @param arg
     * @throws IOException 
     */
    public ConnectionRelativeTime(GetArg arg) throws IOException{
	this(validate(arg.get("time", -1)), true);
    }
    
    private ConnectionRelativeTime(long time, boolean check){
	this.time = time;
    }
    
    private static long validate(long time) throws InvalidObjectException{
	if (time < 0) {
	    throw new InvalidObjectException("invalid duration");
	}
	return time;
    }
    
    private static long check(long time){
	if (time < 0) {
	    throw new IllegalArgumentException("invalid duration");
	}
	return time;
    }

    /**
     * Returns the maximum connection duration in milliseconds.
     *
     * @return the maximum connection duration in milliseconds
     */
    public long getTime() {
	return time;
    }

    /**
     * Returns a {@link ConnectionAbsoluteTime} instance with time obtained
     * by adding the specified base time argument to the duration value
     * from this instance. If the addition results in overflow, a time value
     * of <code>Long.MAX_VALUE</code> is used.
     */
    public InvocationConstraint makeAbsolute(long baseTime) {
	return new ConnectionAbsoluteTime(add(time, baseTime));
    }

    private static long add(long dur, long time) {
	long ntime = time + dur;
	if (ntime < 0 && time > 0) {
	    ntime = Long.MAX_VALUE;
	}
	return ntime;
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return (int)(ConnectionRelativeTime.class.hashCode() + time);
    }

    /**
     * Two instances of this class are equal if both have the same duration.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof ConnectionRelativeTime)) {
	    return false;
	}
	ConnectionRelativeTime cc = (ConnectionRelativeTime) obj;
	return time == cc.time;
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ConnectionRelativeTime[" + time + "]";
    }

    /**
     * Verifies that <code>time</code> is greater than or equal to zero.
     *
     * @throws InvalidObjectException if <code>time</code> is less than zero
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	if (time < 0) {
	    throw new InvalidObjectException("invalid duration");
	}
    }
}
