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

package net.jini.jeri.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.action.GetBooleanAction;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;

/**
 * An implementation of the {@link Endpoint} abstraction that uses TCP
 * sockets (instances of {@link Socket}) for the underlying
 * communication mechanism.
 *
 * <p><code>TcpEndpoint</code> instances contain a host name and a TCP
 * port number, as well as an optional {@link SocketFactory} for
 * customizing the type of <code>Socket</code> to use.  The host name
 * and port number are used as the remote address to connect to when
 * making socket connections.
 *
 * <p><code>TcpEndpoint</code> uses the <a
 * href="../connection/doc-files/mux.html">Jini extensible remote
 * invocation (Jini ERI) multiplexing protocol</a> to map outgoing
 * requests to socket connections.
 *
 * <p>A <code>SocketFactory</code> used with a
 * <code>TcpEndpoint</code> should be serializable and must implement
 * {@link Object#equals Object.equals} to obey the guidelines that are
 * specified for <code>equals</code> methods of {@link Endpoint}
 * instances.
 *
 * 
 * @see TcpServerEndpoint
 * @since 2.0
 **/
@AtomicSerial
public final class TcpEndpoint
    implements Endpoint, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = -2840731722681368933L;

    /**
     * weak set of canonical instances; in order to use WeakHashMap,
     * maps canonical instances to weak references to themselves
     **/
    private static final Map<TcpEndpoint,WeakReference<TcpEndpoint>> internTable 
            = new WeakHashMap<TcpEndpoint,WeakReference<TcpEndpoint>>();

    /** client transport logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.tcp.client");

    /** whether or not to use NIO-based sockets if possible */
    private static final boolean useNIO =		// default false
	((Boolean) AccessController.doPrivileged(new GetBooleanAction(
	    "org.apache.river.jeri.tcp.useNIO"))).booleanValue();

    /**
     * The host that this <code>TcpEndpoint</code> connects to.
     *
     * @serial
     **/
    private final String host;

    /**
     * The TCP port that this <code>TcpEndpoint</code> connects to.
     *
     * @serial
     **/
    private final int port;

    /**
     * The socket factory that this <code>TcpEndpoint</code> uses to
     * create <code>Socket</code> objects.
     *
     * @serial
     **/
    private final SocketFactory sf;

    private transient volatile ConnectionManager connectionManager;

    /**
     * Returns a <code>TcpEndpoint</code> instance for the given
     * host name and TCP port number.
     *
     * <p>The <code>SocketFactory</code> contained in the returned
     * <code>TcpEndpoint</code> will be <code>null</code>, indicating
     * that this endpoint will create <code>Socket</code> objects
     * directly.
     *
     * @param host the host for the endpoint to connect to
     *
     * @param port the TCP port on the given host for the endpoint to
     * connect to
     *
     * @return a <code>TcpEndpoint</code> instance
     *
     * @throws IllegalArgumentException if the port number is out of
     * the range <code>1</code> to <code>65535</code> (inclusive)
     *
     * @throws NullPointerException if <code>host</code> is
     * <code>null</code>
     **/
    public static TcpEndpoint getInstance(String host, int port) {
	return intern(new TcpEndpoint(host, check(host, port), null));
    }

    /**
     * Returns a <code>TcpEndpoint</code> instance for the given host
     * name and TCP port number that contains the given
     * <code>SocketFactory</code>.
     *
     * <p>If the socket factory argument is <code>null</code>, then
     * this endpoint will create <code>Socket</code> objects directly.
     *
     * @param host the host for the endpoint to connect to
     *
     * @param port the TCP port on the given host for the endpoint to
     * connect to
     *
     * @param sf the <code>SocketFactory</code> to use for this
     * <code>TcpEndpoint</code>, or <code>null</code>
     *
     * @return a <code>TcpEndpoint</code> instance
     *
     * @throws IllegalArgumentException if the port number is out of
     * the range <code>1</code> to <code>65535</code> (inclusive)
     *
     * @throws NullPointerException if <code>host</code> is
     * <code>null</code>
     **/
    public static TcpEndpoint getInstance(String host, int port,
					  SocketFactory sf)
    {
	return intern(new TcpEndpoint(host, check(host,port), sf));
    }

    /**
     * Returns canonical instance equivalent to given instance.
     **/
    private static TcpEndpoint intern(TcpEndpoint endpoint) {
	synchronized (internTable) {
	    Reference<TcpEndpoint> ref = (WeakReference) internTable.get(endpoint);
	    if (ref != null) {
		TcpEndpoint canonical = ref.get();
		if (canonical != null) {
		    return canonical;
		}
	    }
	    endpoint.connectionManager =
		new ConnectionManager(
                        new ConnectionEndpointImpl(
                                endpoint.getHost(),
                                endpoint.getPort(),
                                endpoint.getSocketFactory()
                        )
                );
	    internTable.put(endpoint, new WeakReference<TcpEndpoint>(endpoint));
	    return endpoint;
	}
    }

    private static int checkSerial(String host, int port) throws InvalidObjectException{
	try {
	    return check(host, port);
	} catch (RuntimeException e){
	    InvalidObjectException ex = 
		    new InvalidObjectException("Invariants not satisfed: " 
			    + e.getMessage());
	    ex.initCause(e);
	    throw ex;
	}
    }
    
    /**
     *  Invariant checks.
     */
    private static int check(String host, int port){
	if (host == null) {
	    throw new NullPointerException("null hostname");
	}
	if (port < 1 || port > 0xFFFF) {
	    throw new IllegalArgumentException(
	        "port number out of range: " + port);
	}
	return port;
    }

    TcpEndpoint(GetArg arg) throws IOException {
	this(arg.get("host", null, String.class),
	    checkSerial(arg.get("host", null, String.class), arg.get("port", 0)),
	    arg.get("sf", null, SocketFactory.class)
	);
    }

    /**
     * Constructs a new instance.
     **/
    private TcpEndpoint(String host, int port, SocketFactory sf) {
	this.host = host;
	this.port = port;
	this.sf = sf;
    }

    /*
     * [This is not a doc comment to prevent its appearance in
     * TcpEndpoint's serialized form specification.]
     *
     * Resolves deserialized instance to equivalent canonical instance.
     */
    private Object readResolve() {
	return intern(this);
    }

    /**
     * Returns the host that this <code>TcpEndpoint</code> connects to.
     *
     * @return the host that this endpoint connects to
     **/
    public String getHost() {
	return host;
    }

    /**
     * Returns the TCP port that this <code>TcpEndpoint</code> connects to.
     *
     * @return the TCP port that this endpoint connects to
     **/
    public int getPort() {
	return port;
    }

    /**
     * Returns the <code>SocketFactory</code> that this endpoint uses
     * to create <code>Socket</code> objects.
     *
     * @return the socket factory that this endpoint uses to create
     * sockets, or <code>null</code> if no factory is used
     **/
    public SocketFactory getSocketFactory() {
	return sf;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned <code>OutboundRequestIterator</code>'s {@link
     * OutboundRequestIterator#next next} method behaves as follows:
     *
     * <blockquote>
     *
     * Initiates an attempt to communicate the request to this remote
     * endpoint.
     *
     * <p>When the implementation of this method needs to create a new
     * <code>Socket</code>, it will do so by invoking one of the
     * <code>createSocket</code> methods on the
     * <code>SocketFactory</code> of this <code>TcpEndpoint</code>
     * (which produced this iterator) if non-<code>null</code>, or it
     * will create a <code>Socket</code> directly otherwise.
     *
     * <p>When the implementation needs to connect a
     * <code>Socket</code>, if the host name to connect to (this
     * <code>TcpEndpoint</code>'s host name) resolves to multiple
     * addresses (according to {@link InetAddress#getAllByName
     * InetAddress.getAllByName}), it attempts to connect to the first
     * resolved address; if that attempt fails with an
     * <code>IOException</code> or a <code>SecurityException</code>,
     * it then attempts to connect to the next address; and this
     * iteration continues as long as there is another resolved
     * address and the attempt to connect to the previous address
     * fails with an <code>IOException</code> or a
     * <code>SecurityException</code>.  If the host name resolves to
     * just one address, the implementation makes one attempt to
     * connect to that address.  If the host name does not resolve to
     * any addresses (<code>InetAddress.getAllByName</code> would
     * throw an <code>UnknownHostException</code>), the implementation
     * still makes an attempt to connect the <code>Socket</code> to
     * that host name, which could result in an
     * <code>UnknownHostException</code>.  If the final connection
     * attempt fails with an <code>IOException</code> or a
     * <code>SecurityException</code>, then if any connection attempt
     * failed with an <code>IOException</code>, this method throws an
     * <code>IOException</code>, and otherwise (if all connection
     * attempts failed with a <code>SecurityException</code>), this
     * method throws a <code>SecurityException</code>.
     *
     * <p>If there is a security manager:
     *
     * <ul>
     *
     * <li>If a new connection is to be created, the security
     * manager's {@link SecurityManager#checkConnect(String,int)
     * checkConnect} method is invoked with this
     * <code>TcpEndpoint</code>'s host and <code>-1</code> for the
     * port; if this results in a <code>SecurityException</code>, this
     * method throws that exception.  <code>checkConnect</code> is
     * also invoked for each connection attempt, with the remote IP
     * address (or the host name, if it could not be resolved) and
     * port to connect to; this could result in a
     * <code>SecurityException</code> for that attempt.  (Note that
     * the implementation may carry out these security checks
     * indirectly, such as through invocations of
     * <code>InetAddress.getAllByName</code> or <code>Socket</code>'s
     * constructors or <code>connect</code> method.)
     *
     * <li><p>In order to reuse an existing connection for the
     * communication, the current security context must have all of
     * the permissions that would be necessary if the connection were
     * being created.  Specifically, it must be possible to invoke
     * <code>checkConnect</code> in the current security context with
     * this <code>TcpEndpoint</code>'s host and <code>-1</code> for
     * the port without resulting in a <code>SecurityException</code>,
     * and it also must be possible to invoke
     * <code>checkConnect</code> with the remote IP address and port
     * of the <code>Socket</code> without resulting in a
     * <code>SecurityException</code> (if the remote socket address is
     * unresolved, its host name is used instead).  If no existing
     * connection satisfies these requirements, then this method must
     * behave as if there are no existing connections.
     *
     * </ul>
     *
     * <p>Throws {@link NoSuchElementException} if this iterator does
     * not support making another attempt to communicate the request
     * (that is, if <code>hasNext</code> would return
     * <code>false</code>).
     *
     * <p>Throws {@link IOException} if an I/O exception occurs while
     * performing this operation, such as if a connection attempt
     * timed out or was refused.
     *
     * <p>Throws {@link SecurityException} if there is a security
     * manager and an invocation of its <code>checkConnect</code>
     * method fails.
     *
     * </blockquote>
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public OutboundRequestIterator
	newRequest(final InvocationConstraints constraints)
    {
	if (constraints == null) {
	    throw new NullPointerException();
	}

	try {
	    Constraints.Distilled distilled =
		Constraints.distill(constraints, false);
	    return connectionManager.newRequest(new Handle(distilled));

	} catch (final UnsupportedConstraintException e) {
	    return new OutboundRequestIterator() {
		private boolean nextCalled = false;
		public boolean hasNext() { return !nextCalled; }
		public OutboundRequest next() throws IOException {
		    if (!hasNext()) { throw new NoSuchElementException(); }
		    nextCalled = true;
		    e.fillInStackTrace(); // REMIND: is this cool?
		    throw e;
		}
	    };
	}
    }

    /**
     * Returns the hash code value for this <code>TcpEndpoint</code>.
     *
     * @return the hash code value for this <code>TcpEndpoint</code>
     **/
    public int hashCode() {
	return host.hashCode() ^ port ^
	    (sf != null ? sf.hashCode() : 0);
    }

    /**
     * Compares the specified object with this
     * <code>TcpEndpoint</code> for equality.
     *
     * <p>This method returns <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object is also a <code>TcpEndpoint</code>,
     *
     * <li>the host and port in the specified object are equal to the
     * host and port in this object, and
     *
     * <li>either this object and the specified object both have no
     * <code>SocketFactory</code> or the <code>SocketFactory</code> in
     * the specified object has the same class and is equal to the one
     * in this object.
     *
     * </ul>
     *
     * @param obj the object to compare with
     *
     * @return <code>true</code> if <code>obj</code> is equivalent to
     * this object; <code>false</code> otherwise
     **/ 
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof TcpEndpoint)) {
	    return false;
	}
	TcpEndpoint other = (TcpEndpoint) obj;
	return
	    host.equals(other.host) &&
	    port == other.port &&
	    Util.sameClassAndEquals(sf, other.sf);
    }

    /**
     * Returns <code>true</code> if the specified object (which is not
     * yet known to be trusted) is equivalent in trust, content, and
     * function to this known trusted object, and <code>false</code>
     * otherwise.
     *
     * <p>This method returns <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object is also a <code>TcpEndpoint</code>,
     *
     * <li>the host and port in the specified object are equal to the
     * host and port in this object, and
     *
     * <li>either this object and the specified object both have no
     * <code>SocketFactory</code> or the <code>SocketFactory</code> in
     * the specified object has the same class and is equal to the one
     * in this object.
     *
     * </ul>
     **/
    public boolean checkTrustEquivalence(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof TcpEndpoint)) {
	    return false;
	}
	TcpEndpoint other = (TcpEndpoint) obj;
	return
	    host.equals(other.host) &&
	    port == other.port &&
	    Util.sameClassAndEquals(sf, other.sf);
    }

    /**
     * Returns a string representation of this
     * <code>TcpEndpoint</code>.
     *
     * @return a string representation of this
     * <code>TcpEndpoint</code>
     **/
    public String toString() {
	return "TcpEndpoint[" + host + ":" + port +
	    (sf != null ? "," + sf : "") + "]";
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    /**
     * @throws InvalidObjectException if the host name is
     * <code>null</code> or if the port number is out of the range
     * <code>1</code> to <code>65535</code> (inclusive)
     **/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	checkSerial(host, port);
	}

    /**
     * OutboundRequestHandle implementation.
     **/
    private class Handle implements OutboundRequestHandle {

	private final Constraints.Distilled distilled;

	Handle(Constraints.Distilled distilled) {
	    this.distilled = distilled;
	}

	TcpEndpoint getTcpEndpoint() {
	    return TcpEndpoint.this;
	}

	Constraints.Distilled getDistilledConstraints() {
	    return distilled;
	}

	InvocationConstraints getUnfulfilledConstraints() {
	    return distilled.getUnfulfilledConstraints();
	}
    }

    /**
     * ConnectionEndpoint implementation.
     *
     * Instances of this class should never get exposed to anything
     * other than our ConnectionManager, which we trust to operate
     * correctly, so we do not bother to validate request handles and
     * connections passed in.
     **/
    private static class ConnectionEndpointImpl implements ConnectionEndpoint {
        private final String host;
        private final int port;
        private final SocketFactory sf;

	ConnectionEndpointImpl(String host, int port, SocketFactory sf) {
            this.host = host;
            this.port = port;
            this.sf = sf;
        }

	/**
	 * Invoked by ConnectionManager to create a new connection.
	 **/
	public Connection connect(OutboundRequestHandle handle)
	    throws IOException
	{
	    Handle h = (Handle) handle;
	    Constraints.Distilled distilled = h.getDistilledConstraints();

	    Socket socket = connectToHost(distilled);

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "connected socket {0}", socket);
	    }

	    setSocketOptions(socket);

	    return new ConnectionImpl(socket);
	}

	/**
	 * Returns a socket connected to this endpoint's host and
	 * port, according the specified constraints.  If the host
	 * name resolves to multiple addresses, attempts to connect to
	 * each of them in order until one succeeds.
	 **/
	private Socket connectToHost(Constraints.Distilled distilled)
	    throws IOException
	{
	    InetAddress[] addresses;
	    try {
		addresses = InetAddress.getAllByName(host);
	    } catch (UnknownHostException uhe) {
		try {
		    /*
		     * Creating the InetSocketAddress attempts to
		     * resolve the host again; in J2SE 5.0, there is a
		     * factory method for creating an unresolved
		     * InetSocketAddress directly.
		     */
		    return connectToSocketAddress(
			new InetSocketAddress(host, port), distilled);
		} catch (IOException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    ConnectionEndpointImpl.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { host + ":" + port }, e);
		    }
		    throw e;
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    ConnectionEndpointImpl.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { host + ":" + port }, e);
		    }
		    throw e;
		}
	    } catch (SecurityException e) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			ConnectionEndpointImpl.class, "connectToHost",
			"exception resolving host {0}",
			new Object[] { host }, e);
		}
		throw e;
	    }
	    IOException lastIOException = null;
	    SecurityException lastSecurityException = null;
	    for (int i = 0; i < addresses.length; i++) {
		SocketAddress socketAddress =
		    new InetSocketAddress(addresses[i], port);
		try {
		    return connectToSocketAddress(socketAddress, distilled);
		} catch (IOException e) {
		    if (logger.isLoggable(Levels.HANDLED)) {
			LogUtil.logThrow(logger, Levels.HANDLED,
			    ConnectionEndpointImpl.class, "connectToHost",
			    "exception connecting to {0}",
			    new Object[] { socketAddress }, e);
		    }
		    lastIOException = e;
		    if (e instanceof SocketTimeoutException) {
			break;
		    }
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.HANDLED)) {
			LogUtil.logThrow(logger, Levels.HANDLED,
			    ConnectionEndpointImpl.class, "connectToHost",
			    "exception connecting to {0}",
			    new Object[] { socketAddress }, e);
		    }
		    lastSecurityException = e;
		}
	    }
	    if (lastIOException != null) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			ConnectionEndpointImpl.class, "connectToHost",
			"exception connecting to {0}",
			new Object[] { host + ":" + port },
			lastIOException);
		}
		throw lastIOException;
	    }
	    assert lastSecurityException != null;
	    if (logger.isLoggable(Levels.FAILED)) {
		LogUtil.logThrow(logger, Levels.FAILED,
		    ConnectionEndpointImpl.class, "connectToHost",
		    "exception connecting to {0}",
		    new Object[] { host + ":" + port },
		    lastSecurityException);
	    }
	    throw lastSecurityException;
	}

	/**
	 * Returns a socket connected to the specified address, with a
	 * timeout governed by the specified constraints.
	 **/
	private Socket connectToSocketAddress(SocketAddress socketAddress,
					      Constraints.Distilled distilled)
	    throws IOException
	{
	    int timeout;
	    if (distilled.hasConnectDeadline()) {
		long now = System.currentTimeMillis();
		long deadline = distilled.getConnectDeadline();
		if (deadline <= now) {
		    throw new SocketTimeoutException(
			"deadline past before connect attempt");
		}
		assert now > 0; // if not, we could overflow
		long delta = deadline - now;
		// assume that no socket connect will last over 24 days
		timeout = (delta > Integer.MAX_VALUE ? 0 : (int) delta);
	    } else {
		timeout = 0;
	    }

	    Socket socket = newSocket();
	    boolean ok = false;
	    try {
		socket.connect(socketAddress, timeout);
		ok = true;
		return socket;
	    } finally {
		if (!ok) {
		    try {
			socket.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}

	/**
	 * Returns a new unconnected socket, using this endpoint's
	 * socket factory if non-null.
	 **/
	private Socket newSocket() throws IOException {
	    Socket socket;
	    if (sf != null) {
		socket = sf.createSocket();
	    } else {
		if (useNIO) {
		    socket = SocketChannel.open().socket();
		} else {
		    socket = new Socket();
		}
	    }

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   (sf == null ? "created socket {0}" :
			    "created socket {0} using factory {1}"),
			   new Object[] { socket, sf });
	    }

	    return socket;
	}

	/**
	 * Invoked by ConnectionManager to reuse an existing
	 * connection.
	 **/
	public Connection connect(OutboundRequestHandle handle,
				  Collection active,
				  Collection idle)
	{
	    if (active == null || idle == null) {
		throw new NullPointerException();
	    }

	    /*
	     * The transport level aspects of all constraints
	     * supported by this transport provider are always
	     * satisfied by all open connections, so we don't need to
	     * consider constraints here.
	     */

	    boolean checkedResolvePermission = false;
	    for (Iterator i = active.iterator(); i.hasNext();) {
		ConnectionImpl c = (ConnectionImpl) i.next();
		if (!checkedResolvePermission) {
		    try {
			checkResolvePermission();
		    } catch (SecurityException e) {
			if (logger.isLoggable(Levels.FAILED)) {
			    LogUtil.logThrow(logger, Levels.FAILED,
				ConnectionEndpointImpl.class, "connect",
				"exception resolving host {0}",
				new Object[] { host }, e);
			}
			throw e;
		    }
		    checkedResolvePermission = true;
		}
		try {
		    c.checkConnectPermission();
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
				   "reusing connection {0}", c.getSocket());
		    }
		    return c;
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.HANDLED)) {
			LogUtil.logThrow(logger, Levels.HANDLED,
			    ConnectionEndpointImpl.class, "connect",
			    "access to reuse connection {0} denied",
			    new Object[] { c.getSocket() }, e);
		    }
		}
	    }
	    for (Iterator i = idle.iterator(); i.hasNext();) {
		ConnectionImpl c = (ConnectionImpl) i.next();
		if (!checkedResolvePermission) {
		    try {
			checkResolvePermission();
		    } catch (SecurityException e) {
			if (logger.isLoggable(Levels.FAILED)) {
			    LogUtil.logThrow(logger, Levels.FAILED,
				ConnectionEndpointImpl.class, "connect",
				"exception resolving host {0}",
				new Object[] { host }, e);
			}
			throw e;
		    }
		    checkedResolvePermission = true;
		}
		try {
		    c.checkConnectPermission();
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
				   "reusing connection {0}", c.getSocket());
		    }
		    return c;
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.HANDLED)) {
			LogUtil.logThrow(logger, Levels.HANDLED,
			    ConnectionEndpointImpl.class, "connect",
			    "access to reuse connection {0} denied",
			    new Object[] { c.getSocket() }, e);
		    }
		}
	    }
	    return null;
	}

	private void checkResolvePermission() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkConnect(host, -1);
	    }
	}
    }

    /**
     * Connection implementation.
     *
     * Instances of this class should never get exposed to anything
     * other than our ConnectionManager, which we trust to operate
     * correctly, so we do not bother to validate request handles
     * passed in.
     **/
    private static class ConnectionImpl implements Connection {

	private final Socket socket;

	// socket attributes cached to work around 4720952:
	private final InetSocketAddress inetSocketAddress;
	private final int socketPort;

	ConnectionImpl(Socket socket) {
	    assert socket.isConnected();

	    this.socket = socket;
	    inetSocketAddress =
		(InetSocketAddress) socket.getRemoteSocketAddress();
	    socketPort = socket.getPort();
	}

	Socket getSocket() {
	    return socket;
	}

	public InputStream getInputStream() throws IOException {
	    return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
	    return socket.getOutputStream();
	}

	public SocketChannel getChannel() {
	    return socket.getChannel();
	}

	public void populateContext(OutboundRequestHandle handle,
				    Collection context)
	{
	    if (context == null) {
		throw new NullPointerException();
	    }
	}

	public InvocationConstraints
	    getUnfulfilledConstraints(OutboundRequestHandle handle)
	{
	    Handle h = (Handle) handle;
	    return h.getUnfulfilledConstraints();
	}

	public void writeRequestData(OutboundRequestHandle handle,
				     OutputStream out)
	{
	    if (out == null) {
		throw new NullPointerException();
	    }
	}

	public IOException readResponseData(OutboundRequestHandle handle,
					    InputStream in)
	{
	    if (in == null) {
		throw new NullPointerException();
	    }
	    return null;
	}

	public void close() {
	    try {
		socket.close();
	    } catch (Exception e) {
	    }

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "closed socket {0}", socket);
	    }
	}

	void checkConnectPermission() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		if (inetSocketAddress.isUnresolved()) {
		    sm.checkConnect(inetSocketAddress.getHostName(),
				    socketPort);
		} else {
		    sm.checkConnect(
			inetSocketAddress.getAddress().getHostAddress(),
			socketPort);
		}
	    }
	}
    }

    /**
     * Attempts to set desired socket options for a connected socket
     * (TCP_NODELAY and SO_KEEPALIVE); ignores SocketException.
     **/
    private static void setSocketOptions(Socket socket) {
	try {
	    socket.setTcpNoDelay(true);
	} catch (SocketException e) {
	    if (logger.isLoggable(Levels.HANDLED)) {
		LogUtil.logThrow(logger, Levels.HANDLED,
				 TcpEndpoint.class, "setSocketOptions",
				 "exception setting TCP_NODELAY on socket {0}",
				 new Object[] { socket }, e);
	    }
	}
	try {
	    socket.setKeepAlive(true);
	} catch (SocketException e) {
	    if (logger.isLoggable(Levels.HANDLED)) {
		LogUtil.logThrow(logger, Levels.HANDLED,
				 TcpEndpoint.class, "setSocketOptions",
				"exception setting SO_KEEPALIVE on socket {0}",
				 new Object[] { socket }, e);
	    }
	}
    }
}
