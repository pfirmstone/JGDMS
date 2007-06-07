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
 * Interface implemented by classes which perform the client (discovering) side
 * of unicast discovery.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface UnicastDiscoveryClient extends DiscoveryFormatProvider {

    /**
     * Checks and returns normally if this client is capable of fulfilling the
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
     * Performs the client side of unicast discovery, obtaining the returned
     * response data over the provided socket using the given default and
     * codebase verifier class loaders and collection of object stream context
     * objects in a manner that satisfies the specified absolute constraints.
     * Byte buffers containing the data sent and received so far over the given
     * socket (for the unicast discovery protocol 2 handshake) are provided for
     * use by formats which integrity protect or otherwise incorporate the
     * handshake data.  <code>null</code> constraints are considered equivalent
     * to empty constraints.
     *
     * @param socket the socket on which to perform unicast discovery
     * @param constraints the constraints to apply to unicast discovery, or
     * <code>null</code>
     * @param defaultLoader the class loader value (possibly <code>null</code>)
     * to be passed as the <code>defaultLoader</code> argument to
     * <code>RMIClassLoader</code> methods when unmarshalling the registrar
     * proxy
     * @param verifierLoader the class loader value (possibly
     * <code>null</code>) to pass to {@link
     * net.jini.security.Security#verifyCodebaseIntegrity
     * Security.verifyCodebaseIntegrity}, if codebase integrity verification is
     * used when unmarshalling the registrar proxy
     * @param context the collection of context information objects (possibly
     * <code>null</code>) to use when unmarshalling the registrar proxy
     * @param sent a buffer containing the data already sent
     * @param received a buffer containing the data already received
     * @return the received unicast response data
     * @throws IOException if an error occurs in interpreting received data or
     * in formatting data to send
     * @throws UnsupportedConstraintException if unable to satisfy the
     * specified constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions
     * @throws ClassNotFoundException if the class of the discovered registrar
     * cannot be resolved
     * @throws NullPointerException if <code>socket</code>, <code>sent</code>,
     * or <code>received</code> is <code>null</code>
     */
    UnicastResponse doUnicastDiscovery(Socket socket,
				       InvocationConstraints constraints,
				       ClassLoader defaultLoader,
				       ClassLoader verifierLoader,
				       Collection context,
				       ByteBuffer sent,
				       ByteBuffer received)
	throws IOException, ClassNotFoundException;
}
