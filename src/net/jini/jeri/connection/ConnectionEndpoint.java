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

package net.jini.jeri.connection;

import java.io.IOException;
import java.util.Collection;
import net.jini.jeri.Endpoint;

/**
 * Represents a remote communication endpoint to establish connections
 * to.  For example, a TCP-based <code>ConnectionEndpoint</code>
 * implementation typically contains the remote host address and TCP
 * port to connect to.
 *
 * <p><code>ConnectionEndpoint</code> is implemented by
 * connection-based {@link Endpoint} implementations that use {@link
 * ConnectionManager} for managing connections.  A
 * <code>ConnectionManager</code> is created for a given
 * <code>ConnectionEndpoint</code>, and it uses that
 * <code>ConnectionEndpoint</code> to choose an established connection
 * for a given request or to establish a new connection for a given
 * request.
 *
 * <p>An instance of this interface should implement {@link
 * Object#equals Object.equals} to obey the guidelines that are
 * specified for <code>equals</code> methods of {@link Endpoint}
 * instances.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ConnectionEndpoint {

    /**
     * Returns a new connection that can be used to send a request for
     * the specified handle.
     *
     * <p>The actual network connection might not be completely
     * established when this method returns; connection establishment
     * (including any permission checks required) may proceed
     * asynchronously.
     *
     * <p>Either this method or the returned connection must
     * eventually check and throw a <code>SecurityException</code> if
     * the calling thread (at the point of the check) does not have
     * the requisite permissions to send an outbound request for the
     * specified handle.  If an exception is thrown, data written to
     * the connection's output stream must not have been transmitted
     * to the server, and the client's identity must not have been
     * revealed to the server.
     *
     * <p>Either this method or the returned connection must
     * eventually check and throw an <code>IOException</code> if the
     * client or server does not have the requisite principals and
     * credentials to allow the client to send an outbound request for
     * the specified handle.  If an exception is thrown, data written
     * to the connection's output stream must not have been
     * transmitted to the server.
     *
     * @param handle the handle for the request
     *
     * @return a new connection that can be used to send a request for
     * the specified handle
     *
     * @throws IOException if an I/O exception occurs
     *
     * @throws SecurityException if a security exception occurs
     *
     * @throws IllegalArgumentException if the specified handle was
     * not created for use with an equivalent connection endpoint
     *
     * @throws NullPointerException if <code>handle</code> is
     * <code>null</code>
     **/
    Connection connect(OutboundRequestHandle handle)
	throws IOException;

    /**
     * Returns an existing or new connection that can be used to send
     * a request for the specified handle, or <code>null</code> if a
     * new connection needs to be created in a way that requires
     * synchronous I/O.
     *
     * <p>This method is passed any existing connections, both active
     * and idle, that might be suitable for use.  The active
     * connections have other requests in progress; the idle
     * connections do not.  All other things being equal, an active
     * connection should be chosen over an idle one, and an idle
     * connection should be chosen over creating a new one.  An active
     * (or idle) connection, however, might be less suitable than an
     * idle (or new) one for various reasons, such as being too
     * expensive relative to the constraints that need to be
     * satisfied.
     *
     * <p>This method is permitted to alter the state of an idle
     * connection (for example, to renegotiate the constraints in
     * force), but any I/O for that purpose must be performed either
     * asynchronously or at subsequent I/O operations on the
     * connection, and it must be completed before any data written to
     * the connection's output stream is actually transmitted.  An
     * implementation that performs any such I/O must distinguish it
     * from I/O performed on the connection's streams.
     *
     * <p>This method is permitted to return a new connection, but it
     * must not perform any synchronous I/O to establish the
     * connection; such I/O must be performed either asynchronously or
     * at subsequent I/O operations on the connection.  If synchronous
     * I/O is required to create a new connection, this method should
     * return <code>null</code>.
     *
     * <p>This method should not assume that the collections are
     * modifiable and should not assume that their contents will
     * remain valid after this method returns.
     *
     * <p>Either this method or the returned connection must
     * eventually check and throw a <code>SecurityException</code> if
     * the calling thread (at the point of the check) does not have
     * the requisite permissions to send an outbound request for the
     * specified handle.  If an exception is thrown, data written to
     * the connection's output stream must not have been transmitted
     * to the server, and the client's identity must not have been
     * revealed to the server.
     *
     * <p>The returned connection must eventually check and throw an
     * <code>IOException</code> if the client or server does not have
     * the requisite principals and credentials to allow the client to
     * send an outbound request for the specified handle.  If an
     * exception is thrown, data written to the connection's output
     * stream must not have been transmitted to the server.
     *
     * <p>If an existing active connection is returned, this method
     * must ensure that the security and credential checks for the
     * current request will not cause the checks for existing active
     * requests to unnecessarily fail or to incorrectly succeed, and
     * vice versa.  Therefore, in practice, this method should only
     * return an existing active connection if all of the security and
     * credential checks are made before this method returns.
     *
     * <p>Note that a <code>ConnectionManager</code> never makes
     * concurrent invocations of this method; implementations should
     * take that into consideration when deciding if and how long this
     * method should block.
     *
     * @param handle the handle for the request
     *
     * @param active the connections with requests in progress
     *
     * @param idle the connections with no requests in progress
     *
     * @return a connection that can be used to send an outbound
     * request for the specified handle, or <code>null</code>
     *
     * @throws SecurityException if a security exception occurs
     *
     * @throws IllegalArgumentException if the specified handle was
     * not created for use with an equivalent connection endpoint, or
     * a connection in either collection was not created by an
     * equivalent connection endpoint
     *
     * @throws NullPointerException if any argument is
     * <code>null</code> or any element of either collection is
     * <code>null</code>
     **/
    Connection connect(OutboundRequestHandle handle,
		       Collection active,
		       Collection idle);
}
