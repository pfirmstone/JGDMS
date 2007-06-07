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
 * Represents a constraint on the size (in bytes) of multicast packets used in
 * the multicast request and multicast announcement discovery protocols.
 * Lookup services and discovery clients can use this constraint to limit the
 * size of multicast request and announcement packets sent, so as to avoid
 * fragmentation or loss when the packets traverse routers.  This constraint
 * can also be used to control the size of the buffers used to receive incoming
 * multicast request and announcement packets.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class MulticastMaxPacketSize
    implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 2277375127808559673L;

    /** The minimum allowable multicast packet size limit. */
    public static final int MIN_MAX_PACKET_SIZE = 512;

    /**
     * The multicast packet size limit.
     *
     * @serial
     */
    private final int size;

    /**
     * Creates a <code>MulticastMaxPacketSize</code> constraint for the given
     * multicast packet size limit.
     *
     * @param size the multicast packet size limit
     * @throws IllegalArgumentException if the given size is less than
     * {@link #MIN_MAX_PACKET_SIZE}.
     */
    public MulticastMaxPacketSize(int size) {
	if (size < MIN_MAX_PACKET_SIZE) {
	    throw new IllegalArgumentException("invalid size");
	}
	this.size = size;
    }

    /**
     * Returns the multicast packet size limit.
     *
     * @return the multicast packet size limit
     */
    public int getSize() {
	return size;
    }

    public int hashCode() {
	return MulticastMaxPacketSize.class.hashCode() + size;
    }

    public boolean equals(Object obj) {
	return obj instanceof MulticastMaxPacketSize &&
	       size == ((MulticastMaxPacketSize) obj).size;
    }

    public String toString() {
	return "MulticastMaxPacketSize[" + size + "]";
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (size <= MIN_MAX_PACKET_SIZE) {
	    throw new InvalidObjectException("invalid size");
	}
    }
}
