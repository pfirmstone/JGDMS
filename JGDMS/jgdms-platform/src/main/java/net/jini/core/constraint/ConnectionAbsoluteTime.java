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
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Represents a constraint on the absolute time by which a network connection
 * must be established. The precise meaning of this will vary across
 * communication mechanisms, but in the typical case of
 * {@link java.net.Socket}-based communication, the intention is that this
 * constraint controls the timeout parameter of the
 * {@link java.net.Socket#connect(java.net.SocketAddress,int) connect} method.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
public final class ConnectionAbsoluteTime
			implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 8039977689366799322L;
    
    /**
     * Argument names and types for {@link AtomicSerial}
     * @return Serial arguments
     */
    public static SerialForm[] serialForm(){
        return new SerialForm []{
            new SerialForm("time", Long.TYPE)
        };
    }
    
    /**
     * Provides access to internal state for {@link AtomicSerial}
     * @serialField time long
     * Deadline for connection establishment in milliseconds from midnight,
     * January 1, 1970 UTC.
     * 
     * @param arg arguments to populate with serial arguments
     * @param c the object to serialize.
     * @throws IOException 
     */
    public static void serialize (PutArg arg, ConnectionAbsoluteTime c) throws IOException{
        arg.put("time", c.time);
        arg.writeArgs();
    }

    /**
     * Deadline for connection establishment in milliseconds from midnight,
     * January 1, 1970 UTC.
     *
     * @serial
     */
    private final long time;

    /**
     * Creates a constraint with the specified deadline for connection
     * establishment.
     *
     * @param time the deadline for connection establishment in milliseconds
     * from midnight, January 1, 1970 UTC
     */
    public ConnectionAbsoluteTime(long time) {
	this.time = time;
    }
    
    public ConnectionAbsoluteTime(GetArg arg) throws IOException{
	this(arg.get("time", 0));
    }

    /**
     * Returns the deadline for connection establishment.
     *
     * @return the deadline for connection establishment in milliseconds from
     * midnight, January 1, 1970 UTC
     */
    public long getTime() {
	return time;
    }

    /**
     * Returns a hash code value for this object.
     */
    @Override
    public int hashCode() {
	return (int)(ConnectionAbsoluteTime.class.hashCode() + time);
    }

    /**
     * Two instances of this class are equal if both have the same deadline.
     */
    @Override
    public boolean equals(Object obj) {
	if (!(obj instanceof ConnectionAbsoluteTime)) {
	    return false;
	}
	ConnectionAbsoluteTime cc = (ConnectionAbsoluteTime) obj;
	return time == cc.time;
    }

    /**
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
	return "ConnectionAbsoluteTime[" + time + "]";
    }
}
