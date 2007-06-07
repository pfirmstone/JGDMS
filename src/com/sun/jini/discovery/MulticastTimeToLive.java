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

package com.sun.jini.discovery;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import net.jini.core.constraint.InvocationConstraint;

/**
 * Represents a constraint on the time to live (TTL) value set on outgoing
 * multicast request and multicast announcement packets.  Lookup services and
 * discovery clients can use this constraint to specify the range of multicast
 * transmissions used in discovery.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class MulticastTimeToLive
    implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 8899039913861829419L;

    /** The maximum permissible time to live value. */
    public static final int MAX_TIME_TO_LIVE = 0xFF;

    /**
     * The time to live value.
     *
     * @serial
     */
    private final int ttl;

    /**
     * Creates a <code>MulticastTimeToLive</code> constraint for the given time
     * to live value.
     *
     * @param ttl the time to live value
     * @throws IllegalArgumentException if the given value is negative or
     * greater than {@link #MAX_TIME_TO_LIVE}.
     */
    public MulticastTimeToLive(int ttl) {
	if (ttl < 0 || ttl > MAX_TIME_TO_LIVE) {
	    throw new IllegalArgumentException("invalid time to live");
	}
	this.ttl = ttl;
    }

    /**
     * Returns the time to live value.
     *
     * @return the time to live value
     */
    public int getTimeToLive() {
	return ttl;
    }

    public int hashCode() {
	return MulticastTimeToLive.class.hashCode() + ttl;
    }

    public boolean equals(Object obj) {
	return obj instanceof MulticastTimeToLive &&
	       ttl == ((MulticastTimeToLive) obj).ttl;
    }

    public String toString() {
	return "MulticastTimeToLive[" + ttl + "]";
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (ttl < 0 || ttl > MAX_TIME_TO_LIVE) {
	    throw new InvalidObjectException("invalid time to live");
	}
    }
}
