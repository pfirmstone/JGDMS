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

import com.sun.jini.discovery.ClientSubjectChecker;
import com.sun.jini.discovery.DiscoveryProtocolException;
import com.sun.jini.discovery.UnicastDiscoveryServer;
import com.sun.jini.discovery.UnicastResponse;
import com.sun.jini.jeri.internal.connection.ServerConnManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import javax.net.ServerSocketFactory;
import javax.security.auth.Subject;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.ClientSubject;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;

/**
 * Provides an abstract server endpoint-based UnicastDiscoveryServer
 * implementation, which serves as a superclass for server-side providers for
 * the net.jini.discovery.ssl and net.jini.discovery.kerberos unicast discovery
 * formats.
 */
public abstract class EndpointBasedServer
    extends EndpointBasedProvider implements UnicastDiscoveryServer
{
    /**
     * Constructs instance with the given format name and object providing
     * access to non-public endpoint operations.
     */
    protected EndpointBasedServer(String formatName,
				  EndpointInternals endpointInternals)
    {
	super(formatName, endpointInternals);
    }

    // documentation inherited from UnicastDiscoveryServer
    public void checkUnicastDiscoveryConstraints(
					    InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	ServerEndpoint ep = getServerEndpoint(null);
	checkIntegrity(ep.checkConstraints(constraints));
    }

    // documentation inherited from UnicastDiscoveryServer
    public void handleUnicastDiscovery(UnicastResponse response,
				       Socket socket,
				       InvocationConstraints constraints,
				       ClientSubjectChecker checker,
				       Collection context,
				       ByteBuffer received,
				       ByteBuffer sent)
	throws IOException
    {
	if (response == null || socket == null ||
	    received == null || sent == null)
	{
	    throw new NullPointerException();
	}
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	ServerEndpoint ep = 
	    getServerEndpoint(new PrearrangedServerSocketFactory(socket));
	ServerConnManagerImpl mgr = new ServerConnManagerImpl();
	endpointInternals.setServerConnManager(ep, mgr);

	ListenContextImpl lc = new ListenContextImpl();
	ep.enumerateListenEndpoints(lc);
	ServerConnection conn = mgr.getServerConnection();
	try {
	    InputStream in = new BufferedInputStream(conn.getInputStream());
	    OutputStream out = new BufferedOutputStream(conn.getOutputStream());
	    InboundRequestHandle handle = conn.processRequestData(in, out);
	    conn.checkPermissions(handle);
	    checkIntegrity(conn.checkConstraints(handle, constraints));
	    if (checker != null) {
		checker.checkClientSubject(getClientSubject(conn, handle));
	    }

	    byte[] hash = calcHandshakeHash(received, sent);
	    byte[] clientHash = new byte[hash.length];
	    new DataInputStream(in).readFully(clientHash);
	    if (!Arrays.equals(clientHash, hash)) {
		throw new DiscoveryProtocolException(
		    "handshake hash mismatch");
	    }

	    Plaintext.writeUnicastResponse(out, response, context);
	    out.flush();
	} finally {
	    conn.close();
	    lc.getListenHandle().close();
	}
    }

    /**
     * Returns a server endpoint which uses the given server socket factory, if
     * non-null.  Other parameters of the server endpoint, such as the listen
     * port, are irrelevant from the standpoint of this class and can be chosen
     * arbitrarily.
     */
    protected abstract ServerEndpoint getServerEndpoint(
						ServerSocketFactory factory)
	throws UnsupportedConstraintException;

    /**
     * Returns the subject that the client of the given connection has
     * authenticated as, or null if the client is not authenticated.
     */
    private static Subject getClientSubject(ServerConnection connection,
					    InboundRequestHandle handle)
    {
	Collection ctx = new ArrayList();
	connection.populateContext(handle, ctx);
	for (Iterator i = ctx.iterator(); i.hasNext(); ) {
	    Object obj = i.next();
	    if (obj instanceof ClientSubject) {
		return ((ClientSubject) obj).getClientSubject();
	    }
	}
	return null;
    }

    /**
     * Server connection manager that stores the connection it is given to
     * handle.
     */
    private static class ServerConnManagerImpl implements ServerConnManager {

	private ServerConnection conn = null;

	ServerConnManagerImpl() {
	}

	public synchronized void handleConnection(ServerConnection conn,
						  RequestDispatcher disp)
	{
	    if (conn == null || disp == null) {
		throw new NullPointerException();
	    }
	    this.conn = conn;
	    notifyAll();
	}

	synchronized ServerConnection getServerConnection() {
	    while (conn == null) {
		try { wait(); } catch (InterruptedException e) {}
	    }
	    return conn;
	}
    }

    /**
     * Listen context that listens on the endpoint it is given, and stores the
     * resulting handle.
     */
    private static class ListenContextImpl implements ListenContext {

	private ListenHandle handle = null;

	public ListenCookie addListenEndpoint(ListenEndpoint endpoint)
	    throws IOException
	{
	    handle = endpoint.listen(new RequestDispatcher() {
		public void dispatch(InboundRequest req) {
		    throw new AssertionError("dispatch should not occur");
		}
	    });
	    return handle.getCookie();
	}

	ListenHandle getListenHandle() {
	    return handle;
	}
    }

    /**
     * Server socket that returns a prearranged socket once from its accept
     * method, and then blocks on subsequent calls to accept until closed.
     */
    private static class PrearrangedServerSocket extends ServerSocket {

	private Socket socket;
	private boolean closed = false;

	PrearrangedServerSocket(Socket socket) throws IOException {
	    this.socket = socket;
	}

	public synchronized Socket accept() throws IOException {
	    if (!closed && socket != null) {
		Socket s = socket;
		socket = null;
		return s;
	    }
	    while (!closed) {
		try { wait(); } catch (InterruptedException e) {}
	    }
	    throw new SocketException("socket closed");
	}

	public int getLocalPort() {
	    /*
	     * Although this port number is bogus, it has to be plausible,
	     * otherwise calling enumerateListenEndpoints on the server
	     * endpoint will fail since an endpoint cannot be created.
	     */
	    return 1;
	}

	public synchronized void close() throws IOException {
	    if (!closed) {
		closed = true;
		notifyAll();
	    }
	}
    }

    /**
     * Server socket factory that returns a PrearrangedServerSocket for a
     * prearranged socket.
     */
    private static class PrearrangedServerSocketFactory
	extends ServerSocketFactory
    {
	private final ServerSocket ssocket;

	PrearrangedServerSocketFactory(Socket socket) throws IOException {
	    ssocket = new PrearrangedServerSocket(socket);
	}

	public ServerSocket createServerSocket() throws IOException {
	    return ssocket;
	}

	public ServerSocket createServerSocket(int port) throws IOException {
	    return ssocket;
	}

	public ServerSocket createServerSocket(int port, int backlog)
	    throws IOException
	{
	    return ssocket;
	}

	public ServerSocket createServerSocket(int port,
					       int backlog,
					       InetAddress addr)
	    throws IOException
	{
	    return ssocket;
	}
    }
}
