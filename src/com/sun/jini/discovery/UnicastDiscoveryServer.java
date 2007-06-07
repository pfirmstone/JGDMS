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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;

/**
 * Interface implemented by classes which handle the server (lookup service)
 * side of unicast discovery.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface UnicastDiscoveryServer extends DiscoveryFormatProvider {

    /**
     * Checks and returns normally if this server is capable of fulfilling the
     * given absolute constraints.  <code>null</code> constraints are
     * considered equivalent to empty constraints.
     *
     * @param constraints the constraints to check, or <code>null</code>
     * @throws UnsupportedConstraintException if unable to satisfy the
     * specified constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions
     */
    void checkUnicastDiscoveryConstraints(InvocationConstraints constraints)
	throws UnsupportedConstraintException;

    /**
     * Handles the server side of unicast discovery, transmitting the given
     * response data over the provided socket using the given collection of
     * object stream context objects in a manner that satisfies the specified
     * absolute constraints and client subject checker (if any).  Byte buffers
     * containing the data received and sent so far over the given socket (for
     * the unicast discovery protocol 2 handshake) are provided for use by
     * formats which integrity protect or otherwise incorporate the handshake
     * data.  <code>null</code> constraints are considered equivalent to empty
     * constraints.
     *
     * @param response the unicast response data to transmit
     * @param socket the socket on which to handle unicast discovery
     * @param constraints the constraints to apply to unicast discovery, or
     * <code>null</code>
     * @param checker the object to use to check the client subject, or
     * <code>null</code>
     * @param context the collection of context information objects to use when
     * marshalling the registrar proxy
     * @param received a buffer containing the data already received
     * @param sent a buffer containing the data already sent
     * @throws IOException if an error occurs in interpreting received data or
     * in formatting data to send
     * @throws UnsupportedConstraintException if unable to satisfy the
     * specified constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions, or if the client subject check
     * fails
     * @throws NullPointerException if <code>response</code>,
     * <code>socket</code>, <code>context</code>, <code>received</code>, or
     * <code>sent</code> is <code>null</code>
     */
    void handleUnicastDiscovery(UnicastResponse response,
				Socket socket,
				InvocationConstraints constraints,
				ClientSubjectChecker checker,
				Collection context,
				ByteBuffer received,
				ByteBuffer sent)
	throws IOException;
}
