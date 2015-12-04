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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.ServerEndpoint;

/**
 * Represents an established server-side connection.  For example, a
 * TCP-based <code>ServerConnection</code> implementation typically
 * contains a {@link Socket}.
 *
 * <p><code>ServerConnection</code> is implemented by a
 * connection-based {@link ServerEndpoint} implementation that uses
 * {@link ServerConnectionManager} for managing connections.  A
 * <code>ServerConnection</code> is created by the implementation for
 * newly-accepted connections and passed to the manager's {@link
 * ServerConnectionManager#handleConnection handleConnection} method.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ServerConnection {

    /**
     * Returns an input stream that reads data from this connection.
     *
     * @return an input stream that reads data from this connection
     *
     * @throws IOException if an I/O exception occurs
     **/
    InputStream getInputStream() throws IOException;

    /**
     * Returns an output stream that writes data to this connection.
     *
     * @return an output stream that writes data to this connection
     *
     * @throws IOException if an I/O exception occurs
     **/
    OutputStream getOutputStream() throws IOException;

    /**
     * Returns a socket channel that performs I/O on this connection,
     * or <code>null</code> if no socket channel is available.  If a
     * non-<code>null</code> socket channel is returned, it is
     * connected.
     *
     * @return a socket channel that performs I/O on this connection,
     * or <code>null</code>
     **/
    SocketChannel getChannel();

    /**
     * Reads from the specified input stream any per-request data required by
     * this connection for an inbound request, writes any required response
     * data to the specified output stream, and returns a handle for the
     * request.
     *
     * <p>This method is invoked by
     * <code>ServerConnectionManager</code> with the request input
     * stream and the response output stream of the {@link
     * InboundRequest} that it creates for the request when the
     * request is first received.  This method reads information that
     * was sent by {@link Connection#writeRequestData
     * Connection.writeRequestData} and writes information to be read
     * by {@link Connection#readResponseData
     * Connection.readResponseData}.  This method can be used, for
     * example, to process per-request information about delegation,
     * client authentication, or client privileges.
     *
     * <p>If, for security reasons, this method determines that the
     * request must not be processed further (for example, because
     * client authentication failed), this method should close the
     * response output stream and throw a
     * <code>SecurityException</code> after writing any response data.
     *
     * <p>There may be multiple requests in progress concurrently over
     * this connection, and data read from and written to the
     * specified streams may be buffered and multiplexed with data
     * from other requests on this connection's underlying streams.
     * Therefore, this method should only read data from the request
     * input stream and write data to the response output stream and
     * must not otherwise read from or write to this connection's
     * underlying streams.
     *
     * @param in the request input stream of the request
     *
     * @param out the response output stream of the request
     *
     * @return a handle to identify the request in later invocations
     * on this connection
     *
     * @throws NullPointerException if <code>in</code> or
     * <code>out</code> is <code>null</code>
     *
     * @throws IOException if an I/O exception occurs
     *
     * @throws SecurityException if a security exception occurs
     **/
    InboundRequestHandle processRequestData(InputStream in, OutputStream out)
	throws IOException;

    /**
     * Implements {@link InboundRequest#checkPermissions
     * InboundRequest.checkPermissions} for a request with the
     * specified handle.
     *
     * @param handle the handle for the request
     *
     * @throws SecurityException if the current security context does
     * not have the permissions necessary to receive the request
     *
     * @throws IllegalArgumentException if the specified handle was
     * not returned from this connection's
     * <code>processRequestData</code> method
     *
     * @throws NullPointerException if <code>handle</code> is
     * <code>null</code>
     **/
    void checkPermissions(InboundRequestHandle handle);

    /** 
     * Implements {@link InboundRequest#checkConstraints
     * InboundRequest.checkConstraints} for a request with the
     * specified handle.
     *
     * @param handle the handle for the request
     *
     * @param constraints the constraints that must be satisfied
     *
     * @return the constraints that must be at least partially
     * implemented by higher layers
     *
     * @throws UnsupportedConstraintException if the transport layer
     * aspects of any of the specified requirements are not satisfied
     * by this request
     *
     * @throws IllegalArgumentException if the specified handle was
     * not returned from this connection's
     * <code>processRequestData</code> method
     *
     * @throws NullPointerException if <code>handle</code> or
     * <code>constraints</code> is <code>null</code>
     **/
    InvocationConstraints checkConstraints(InboundRequestHandle handle,
					   InvocationConstraints constraints)
	throws UnsupportedConstraintException;

    /**
     * Populates the supplied collection with context information
     * representing a request with the specified handle.  This method
     * is used to implement {@link InboundRequest#populateContext
     * InboundRequest.populateContext} for such requests; the context
     * may also be populated by the connection manager.
     *
     * @param handle the handle for the request 
     *
     * @param context the context collection to populate
     *
     * @throws IllegalArgumentException if the specified handle was
     * not returned from this connection's
     * <code>processRequestData</code> method
     *
     * @throws NullPointerException if <code>handle</code> or
     * <code>context</code> is <code>null</code>
     *
     * @throws UnsupportedOperationException if <code>context</code>
     * is unmodifiable
     **/
    void populateContext(InboundRequestHandle handle, Collection context);

    /**
     * Closes this connection.
     *
     * @throws IOException if an I/O exception occurs
     **/
    void close() throws IOException;
}
