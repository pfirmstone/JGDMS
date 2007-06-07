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
 * Represents a constraint on the version of the discovery protocol used to
 * contact lookup services.  Lookup services and discovery clients can use this
 * constraint to control what version(s) of the multicast request, multicast
 * announcement and unicast discovery protocols are used to exchange data with
 * each other.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class DiscoveryProtocolVersion
    implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 1781016120938012150L;

    /** Use discovery protocol version 1. */
    public static final DiscoveryProtocolVersion ONE =
	new DiscoveryProtocolVersion(Discovery.PROTOCOL_VERSION_1);

    /** Use discovery protocol version 2. */
    public static final DiscoveryProtocolVersion TWO =
	new DiscoveryProtocolVersion(Discovery.PROTOCOL_VERSION_2);

    /**
     * The protocol version number.
     *
     * @serial
     */
    private final int version;

    /**
     * Returns a <code>DiscoveryProtocolVersion</code> constraint for the given
     * version number.
     *
     * @return a constraint for the given version number
     */
    public static DiscoveryProtocolVersion getInstance(int version) {
	switch (version) {
	    case Discovery.PROTOCOL_VERSION_1:
		return ONE;
	    case Discovery.PROTOCOL_VERSION_2:
		return TWO;
	    default:
		return new DiscoveryProtocolVersion(version);
	}
    }

    private DiscoveryProtocolVersion(int version) {
	if (version <= 0) {
	    throw new IllegalArgumentException("invalid version");
	}
	this.version = version;
    }

    /**
     * Returns the protocol version number.
     *
     * @return the protocol version number
     */
    public int getVersion() {
	return version;
    }

    public int hashCode() {
	return DiscoveryProtocolVersion.class.hashCode() + version;
    }

    public boolean equals(Object obj) {
	return obj instanceof DiscoveryProtocolVersion &&
	       version == ((DiscoveryProtocolVersion) obj).version;
    }

    public String toString() {
	return "DiscoveryProtocolVersion[" + version + "]";
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (version <= 0) {
	    throw new InvalidObjectException("invalid version");
	}
    }
}
