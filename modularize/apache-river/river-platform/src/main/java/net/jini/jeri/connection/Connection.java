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
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;

/**
 * Represents an established client-side connection.  For example, a
 * TCP-based <code>Connection</code> implementation typically contains
 * a {@link Socket}.
 *
 * <p><code>Connection</code> is implemented by connection-based
 * {@link Endpoint} implementations that use {@link ConnectionManager}
 * for managing connections.  A <code>Connection</code> is created by
 * the implementation for newly-established connections and is
 * obtained by a <code>ConnectionManager</code> from its {@link
 * ConnectionEndpoint}'s <code>connect</code> methods.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface Connection {

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
     * Populates the supplied collection with context information
     * representing a request with the specified handle.  This method
     * is used to implement {@link OutboundRequest#populateContext
     * OutboundRequest.populateContext} for such requests; the context
     * may also be populated by the connection manager.
     *
     * @param handle the handle for the request 
     *
     * @param context the context collection to populate
     *
     * @throws IllegalArgumentException if the specified handle was
     * not created for use with a connection endpoint equivalent to
     * the one used to create this connection
     *
     * @throws NullPointerException if <code>handle</code> or
     * <code>context</code> is <code>null</code>
     *
     * @throws UnsupportedOperationException if <code>context</code>
     * is unmodifiable
     **/
    void populateContext(OutboundRequestHandle handle,
			 Collection context);

    /**
     * Implements {@link OutboundRequest#getUnfulfilledConstraints
     * OutboundRequest.getUnfulfilledConstraints} for a request with
     * the specified handle.
     *
     * @param handle the handle for the request
     *
     * @return the constraints for the request that must be partially
     * or fully implemented by higher layers
     *
     * @throws IllegalArgumentException if the specified handle was
     * not created for use with a connection endpoint equivalent to
     * the one used to create this connection
     *
     * @throws NullPointerException if <code>handle</code> is
     * <code>null</code>
     **/
    InvocationConstraints getUnfulfilledConstraints(
					  OutboundRequestHandle handle);

    /**
     * Writes to the specified stream any per-request data required by
     * this connection for a request with the specified handle.
     *
     * <p>This method is invoked by <code>ConnectionManager</code>
     * with the request output stream of the {@link OutboundRequest}
     * that it creates for the request.  This method can be used, for
     * example, to convey per-request information about delegation,
     * client authentication, or client privileges.
     *
     * <p>There may be multiple requests in progress concurrently over
     * this connection, and data written to the specified stream may
     * be buffered and multiplexed with data from other requests
     * before being written to this connection's underlying output
     * stream.  Therefore, this method should only write data to the
     * specified stream and must not read any data from this
     * connection's underlying input stream; data can, however, be
     * subsequently read with {@link #readResponseData
     * readResponseData}.
     *
     * @param handle the handle for the request
     *
     * @param stream the request output stream of the request
     *
     * @throws IOException if an I/O exception occurs
     *
     * @throws SecurityException if a security exception occurs
     *
     * @throws IllegalArgumentException if the specified handle was
     * not created for use with a connection endpoint equivalent to
     * the one used to create this connection
     *
     * @throws NullPointerException if <code>handle</code> or
     * <code>stream</code> is <code>null</code>
     **/
    void writeRequestData(OutboundRequestHandle handle,
			  OutputStream stream)
	throws IOException;

    /**
     * Reads from the specified stream any per-response data required
     * by this connection for a request with the specified handle.
     *
     * <p>This method returns <code>null</code> if the information
     * read (if any) indicates that the constraints are satisfied, and
     * it returns an exception if the constraints could not be
     * satisfied.  If an exception is returned rather than thrown, the
     * delivery status of a corresponding {@link OutboundRequest} will
     * be <code>false</code>.
     *
     * <p>This method is invoked by <code>ConnectionManager</code>
     * with the response input stream of the
     * <code>OutboundRequest</code> that it creates for the request
     * and the same handle that was passed to {@link #writeRequestData
     * writeRequestData}.  This method can be used to read information
     * produced by {@link ServerConnection#processRequestData
     * ServerConnection.processRequestData} in response to the
     * information sent by <code>writeRequestData</code>.
     *
     * <p>There may be multiple requests in progress concurrently over
     * this connection, and data read from the specified stream may
     * have been buffered and multiplexed with data from other
     * requests being read from this connection's underlying input
     * stream.  Therefore, this method should only read data from the
     * specified stream and must not write any data to this
     * connection's underlying output stream.
     *
     * @param handle the handle for the request
     *
     * @param stream the response input stream of the request
     *
     * @return <code>null</code> if the constraints are satisfied, or
     * an exception if the constraints could not be satisfied
     *
     * @throws IOException if an I/O exception occurs
     *
     * @throws SecurityException if a security exception occurs
     *
     * @throws IllegalArgumentException if the specified handle was
     * not created for use with a connection endpoint equivalent to
     * the one used to create this connection
     *
     * @throws NullPointerException if <code>handle</code> or
     * <code>stream</code> is <code>null</code>
     **/
    IOException readResponseData(OutboundRequestHandle handle,
				 InputStream stream)
	throws IOException;

    /**
     * Closes this connection.
     *
     * @throws IOException if an I/O exception occurs
     **/
    void close() throws IOException;
}
