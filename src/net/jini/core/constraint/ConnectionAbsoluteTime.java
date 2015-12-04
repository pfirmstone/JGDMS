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

import java.io.Serializable;

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
public final class ConnectionAbsoluteTime
			implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 8039977689366799322L;

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
    public int hashCode() {
	return (int)(ConnectionAbsoluteTime.class.hashCode() + time);
    }

    /**
     * Two instances of this class are equal if both have the same deadline.
     */
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
    public String toString() {
	return "ConnectionAbsoluteTime[" + time + "]";
    }
}
