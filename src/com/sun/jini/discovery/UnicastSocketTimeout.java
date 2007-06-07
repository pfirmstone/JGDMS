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
 * Represents a constraint on the timeout set on sockets used for unicast
 * discovery.  Lookup services and discovery clients can use this constraint to
 * specify the maximum length of time that reads of unicast discovery data will
 * block.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class UnicastSocketTimeout 
    implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 6500477426762925657L;

    /**
     * The socket timeout.
     *
     * @serial
     */
    private final int timeout;

    /**
     * Creates a <code>UnicastSocketTimeout</code> constraint for the given
     * timeout.
     *
     * @param timeout the socket timeout
     * @throws IllegalArgumentException if the given timeout is negative
     */
    public UnicastSocketTimeout(int timeout) {
	if (timeout < 0) {
	    throw new IllegalArgumentException("invalid timeout");
	}
	this.timeout = timeout;
    }

    /**
     * Returns the socket timeout.
     *
     * @return the socket timeout
     */
    public int getTimeout() {
	return timeout;
    }

    public int hashCode() {
	return UnicastSocketTimeout.class.hashCode() + timeout;
    }

    public boolean equals(Object obj) {
	return obj instanceof UnicastSocketTimeout &&
	       timeout == ((UnicastSocketTimeout) obj).timeout;
    }

    public String toString() {
	return "UnicastSocketTimeout[" + timeout + "]";
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (timeout < 0) {
	    throw new InvalidObjectException("invalid timeout");
	}
    }
}
