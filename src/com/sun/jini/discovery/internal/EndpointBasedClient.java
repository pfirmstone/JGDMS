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

package com.sun.jini.discovery.internal;

import com.sun.jini.discovery.UnicastDiscoveryClient;
import com.sun.jini.discovery.UnicastResponse;
import com.sun.jini.jeri.internal.connection.ConnManager;
import com.sun.jini.jeri.internal.connection.ConnManagerFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.NoSuchElementException;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.OutboundRequestHandle;

/**
 * Provides an abstract endpoint-based UnicastDiscoveryClient implementation,
 * which serves as a superclass for client-side providers for the
 * net.jini.discovery.ssl and net.jini.discovery.kerberos unicast discovery
 * formats.
 */
public abstract class EndpointBasedClient
    extends EndpointBasedProvider implements UnicastDiscoveryClient
{
    /**
     * Constructs instance with the given format name and object providing
     * access to non-public endpoint operations.
     */
    protected EndpointBasedClient(String formatName,
				  EndpointInternals endpointInternals)
    {
	super(formatName, endpointInternals);
    }

    // documentation inherited from UnicastDiscoveryClient
    public void checkUnicastDiscoveryConstraints(
					InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	ConnectionInfo ci = getConnectionInfo(null, constraints);
	checkIntegrity(endpointInternals.getUnfulfilledConstraints(ci.handle));
    }

    // documentation inherited from UnicastDiscoveryClient
    public UnicastResponse doUnicastDiscovery(
					Socket socket,
					InvocationConstraints constraints,
					ClassLoader defaultLoader,
					ClassLoader verifierLoader,
					Collection context,
					ByteBuffer sent,
					ByteBuffer received)
	throws IOException, ClassNotFoundException
    {
	if (socket == null || sent == null || received == null) {
	    throw new NullPointerException();
	}
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	ConnectionInfo ci = getConnectionInfo(socket, constraints);
	Connection conn = ci.endpoint.connect(ci.handle);
	try {
	    boolean integrity =
		checkIntegrity(conn.getUnfulfilledConstraints(ci.handle));
	    OutputStream out =
		new BufferedOutputStream(conn.getOutputStream());
	    conn.writeRequestData(ci.handle, out);
	    out.write(calcHandshakeHash(sent, received));
	    out.flush();

	    InputStream in = new BufferedInputStream(conn.getInputStream());
	    IOException e = conn.readResponseData(ci.handle, in);
	    if (e != null) {
		throw e;
	    }
	    return Plaintext.readUnicastResponse(
		in, defaultLoader, integrity, verifierLoader, context);
	} finally {
	    conn.close();
	}
    }

    /**
     * Returns an endpoint which uses the given socket factory, if non-null,
     * and may incorporate information (such as the expected server principal)
     * from the given set of constraints, if non-null.  Other parameters of the
     * endpoint, such as the target host and port, are irrelevant from the
     * standpoint of this class and can be chosen arbitrarily.  Throws an
     * UnsupportedConstraintException if the given constraints lack information
     * needed to construct the endpoint.
     */
    protected abstract Endpoint getEndpoint(SocketFactory factory,
					    InvocationConstraints constraints)
	throws UnsupportedConstraintException;

    /**
     * Returns components needed to initiate a connection over the given socket
     * in accordance with the specified constraints.
     */
    private ConnectionInfo getConnectionInfo(Socket socket,
					     InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	Endpoint ep = 
	    getEndpoint(new PrearrangedSocketFactory(socket), constraints);
	ConnManagerFactoryImpl factory = new ConnManagerFactoryImpl();
	endpointInternals.setConnManagerFactory(ep, factory);
	endpointInternals.disableSocketConnect(ep);

	OutboundRequestIterator iter = ep.newRequest(constraints);
	ConnectionInfo ci = factory.getConnectionInfo();
	if (ci != null) {
	    return ci;
	}

	/*
	 * Connection manager was never consulted, so constraints must not be
	 * supported.  The resulting endpoint-fabricated outbound request
	 * iterator should throw UnsupportedConstraintException from its next
	 * method.
	 */
	if (iter.hasNext()) {
	    try {
		iter.next();
	    } catch (UnsupportedConstraintException e) {
		throw e;
	    } catch (IOException e) {
	    }
	}
	throw new AssertionError("expected UnsupportedConstraintException");
    }

    /**
     * Components for initiating a connection.
     */
    private static class ConnectionInfo {

	final ConnectionEndpoint endpoint;
	final OutboundRequestHandle handle;

	ConnectionInfo(ConnectionEndpoint endpoint,
		       OutboundRequestHandle handle)
	{
	    this.endpoint = endpoint;
	    this.handle = handle;
	}
    }

    /**
     * Factory which produces connection managers that capture the
     * OutboundRequestHandle and ConnectionEndpoint instances used to initiate
     * connections, and then return empty OutboundRequestIterators.
     */
    private static class ConnManagerFactoryImpl implements ConnManagerFactory {

	private ConnectionInfo connInfo = null;

	ConnManagerFactoryImpl() {
	}

	public ConnManager create(final ConnectionEndpoint endpoint) {
	    if (endpoint == null) {
		throw new NullPointerException();
	    }
	    return new ConnManager() {
		public OutboundRequestIterator newRequest(
						  OutboundRequestHandle handle)
		{
		    if (handle == null) {
			throw new NullPointerException();
		    }
		    connInfo = new ConnectionInfo(endpoint, handle);

		    return new OutboundRequestIterator() {
			public boolean hasNext() {
			    return false;
			}
			public OutboundRequest next() throws IOException {
			    throw new NoSuchElementException();
			}
		    };
		}
	    };
	}

	ConnectionInfo getConnectionInfo() {
	    return connInfo;
	}
    }

    /**
     * Socket factory which returns a prearranged socket.
     */
    private static class PrearrangedSocketFactory extends SocketFactory {

	private final Socket socket;

	PrearrangedSocketFactory(Socket socket) {
	    this.socket = socket;
	}

	public Socket createSocket() throws IOException {
	    return socket;
	}

	public Socket createSocket(String host, int port) throws IOException {
	    return socket;
	}

	public Socket createSocket(InetAddress host, int port)
	    throws IOException
	{
	    return socket;
	}

	public Socket createSocket(String host,
				   int port,
				   InetAddress localHost,
				   int localPort)
	    throws IOException
	{
	    return socket;
	}

	public Socket createSocket(InetAddress host,
				   int port,
				   InetAddress localHost,
				   int localPort)
	    throws IOException
	{
	    return socket;
	}

	public String toString() {
	    return "PrearrangedSocketFactory[" + socket + "]";
	}
    }
}
