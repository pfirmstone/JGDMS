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

package net.jini.jeri.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.jeri.internal.http.ConnectionTimer;
import org.apache.river.jeri.internal.http.HttpClientConnection;
import org.apache.river.jeri.internal.http.HttpClientManager;
import org.apache.river.jeri.internal.http.HttpClientSocketFactory;
import org.apache.river.jeri.internal.http.HttpSettings;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;

/**
 * An implementation of the {@link Endpoint} abstraction that uses HTTP
 * messages sent over TCP sockets (instances of {@link Socket}) for the
 * underlying communication mechanism.
 *
 * <p><code>HttpEndpoint</code> instances contain a host name and a
 * TCP port number, as well as an optional {@link SocketFactory} for
 * customizing the type of <code>Socket</code> to use.  The host name
 * and port number are used as the remote address to connect to when
 * making socket connections.  Note that constructing an
 * <code>HttpEndpoint</code> with a <code>SocketFactory</code>
 * instance that produces SSL sockets does not result in an endpoint
 * that is fully HTTPS capable.
 *
 * <p><code>HttpEndpoint</code> instances map outgoing requests to HTTP
 * request/response messages; when possible, underlying TCP connections are
 * reused for multiple non-overlapping outgoing requests.  Outbound request
 * data is sent as the <code>entity-body</code> of an HTTP POST request;
 * inbound response data is received as the <code>entity-body</code> of the
 * corresponding HTTP response message. For information on HTTP, refer to <a
 * href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>.
 * 
 * <p><code>HttpEndpoint</code> can be configured via system properties to send
 * HTTP messages through an intermediary HTTP proxy server.  It also supports
 * basic and digest HTTP authentication, specified in <a
 * href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>.  The mechanisms
 * involved in configuring each of these features are the same as those used by 
 * {@link java.net.HttpURLConnection}; for details, see the 
 * {@link net.jini.jeri.http} package documentation.
 *
 * <p>A <code>SocketFactory</code> used with an
 * <code>HttpEndpoint</code> should be serializable and must implement
 * {@link Object#equals Object.equals} to obey the guidelines that are
 * specified for <code>equals</code> methods of {@link Endpoint}
 * instances.
 *
 * 
 * @see HttpServerEndpoint
 * @since 2.0
 **/
@AtomicSerial
public final class HttpEndpoint
    implements Endpoint, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = -7094180943307123931L;
    
    /** set of canonical instances */
    private static final Map internTable = new WeakHashMap();

    /** HTTP client manager */
    private static final HttpClientManager clientManager;
    /** idle connection timer */
    private static final ConnectionTimer connTimer;
    static {
	HttpSettings hs = getHttpSettings();
	clientManager = new HttpClientManager(hs.getResponseAckTimeout());
	connTimer = new ConnectionTimer(hs.getConnectionTimeout());
    }

    /** client transport logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.http.client");

    /**
     * The host that this <code>HttpEndpoint</code> connects to.
     *
     * @serial
     **/
    private final String host;

    /**
     * The TCP port that this <code>HttpEndpoint</code> connects to.
     *
     * @serial
     **/
    private final int port;

    /**
     * The socket factory that this <code>HttpEndpoint</code> uses to
     * create {@link Socket} objects.
     *
     * @serial
     **/
    private final SocketFactory sf;
    
    /** idle connection cache */
    private transient Set connections;
    /** current proxy host, or empty string if not proxied */
    private transient String proxyHost;
    /** current proxy port, or -1 if not proxied */
    private transient int proxyPort;
    /** true if using persistent connections */
    private transient boolean persist;
    /** Time at which the server endpoint was last pinged. */
    private transient long timeLastVerified;

    /**
     * Returns an <code>HttpEndpoint</code> instance for the given host name
     * and TCP port number.  Note that if HTTP proxying is in effect, then an
     * explicit host name or IP address (i.e., not "localhost") must be
     * provided, or else the returned <code>HttpEndpoint</code> will be unable
     * to properly send requests through the proxy.
     *
     * <p>The {@link SocketFactory} contained in the returned
     * <code>HttpEndpoint</code> will be <code>null</code>, indicating
     * that this endpoint will create {@link Socket} objects directly.
     *
     * @param host the host for the endpoint to connect to
     *
     * @param port the TCP port on the given host for the endpoint to
     * connect to
     * 
     * @return an <code>HttpEndpoint</code> instance
     *
     * @throws IllegalArgumentException if the port number is out of
     * the range <code>1</code> to <code>65535</code> (inclusive)
     *
     * @throws NullPointerException if <code>host</code> is
     * <code>null</code>
     **/
    public static HttpEndpoint getInstance(String host, int port) {
	return intern(new HttpEndpoint(host, port, null));
    }

    /**
     * Returns an <code>HttpEndpoint</code> instance for the given
     * host name and TCP port number that contains the given {@link
     * SocketFactory}.  Note that if HTTP proxying is in effect, then
     * an explicit host name or IP address (i.e., not "localhost")
     * must be provided, or else the returned
     * <code>HttpEndpoint</code> will be unable to properly send
     * requests through the proxy.
     *
     * <p>If the socket factory argument is <code>null</code>, then
     * this endpoint will create {@link Socket} objects directly.
     *
     * @param host the host for the endpoint to connect to
     *
     * @param port the TCP port on the given host for the endpoint to
     * connect to
     *
     * @param sf the <code>SocketFactory</code> to use for this
     * <code>HttpEndpoint</code>, or <code>null</code>
     *
     * @return an <code>HttpEndpoint</code> instance
     *
     * @throws IllegalArgumentException if the port number is out of
     * the range <code>1</code> to <code>65535</code> (inclusive)
     *
     * @throws NullPointerException if <code>host</code> is
     * <code>null</code>
     **/
    public static HttpEndpoint getInstance(String host, int port,
					   SocketFactory sf)
    {
	return intern(new HttpEndpoint(host, port, sf));
    }

    /**
     * Returns canonical instance equivalent to given instance.
     **/
    private static HttpEndpoint intern(HttpEndpoint endpoint) {
	synchronized (internTable) {
	    Reference ref = (SoftReference) internTable.get(endpoint);
	    if (ref != null) {
		HttpEndpoint canonical = (HttpEndpoint) ref.get();
		if (canonical != null) {
		    return canonical;
		}
	    }
	    endpoint.init();
	    internTable.put(endpoint, new SoftReference(endpoint));
	    return endpoint;
	}
    }

    /**
     * Constructs a new (not fully initialized) instance.
     **/
    private HttpEndpoint(String host, int port, SocketFactory sf) {
	if (host == null) {
	    throw new NullPointerException();
	}
	if (port < 1 || port > 0xFFFF) {
	    throw new IllegalArgumentException(
	        "port number out of range: " + port);
	}

	this.host = host;
	this.port = port;
	this.sf = sf;
    }

    /*
     * [This is not a doc comment to prevent its appearance in
     * HttpEndpoint's serialized form specification.]
     *
     * Resolves deserialized instance to equivalent canonical instance.
     */
    private Object readResolve() {
	return intern(this);
    }

    /**
     * Initializes new instance obtained either from private constructor or
     * deserialization.
     **/
    private void init() {
	connections = new HashSet(5);
	proxyHost = "";
	proxyPort = -1;
    }

    /**
     * Returns the host that this <code>HttpEndpoint</code> connects to.
     *
     * @return the host that this endpoint connects to
     **/
    public String getHost() {
	return host;
    }

    /**
     * Returns the TCP port that this <code>HttpEndpoint</code> connects to.
     *
     * @return the TCP port that this endpoint connects to
     **/
    public int getPort() {
	return port;
    }

    /**
     * Returns the {@link SocketFactory} that this endpoint uses to
     * create {@link Socket} objects.
     *
     * @return the socket factory that this endpoint uses to create
     * sockets, or <code>null</code> if this endpoint creates sockets
     * directly
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
     * <code>SocketFactory</code> of this <code>HttpEndpoint</code>
     * (which produced this iterator) if non-<code>null</code>, or it
     * will create a <code>Socket</code> directly otherwise.
     *
     * <p>When the implementation needs to connect a
     * <code>Socket</code>, if the host name to connect to (if an HTTP
     * proxy is to be used for the communication, the proxy's host
     * name; otherwise, this <code>HttpEndpoint</code>'s host name)
     * resolves to multiple addresses (according to {@link
     * InetAddress#getAllByName InetAddress.getAllByName}), it
     * attempts to connect to the first resolved address; if that
     * attempt fails with an <code>IOException</code> or (as is
     * possible in the case that an HTTP proxy is not to be used) a
     * <code>SecurityException</code>, it then attempts to connect to
     * the next address; and this iteration continues as long as there
     * is another resolved address and the attempt to connect to the
     * previous address fails with an <code>IOException</code> or a
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
     * <p>If there is a security manager and an HTTP proxy is to be
     * used for the communication, the security manager's {@link
     * SecurityManager#checkConnect(String,int) checkConnect} method
     * is invoked with this <code>HttpEndpoint</code>'s host and port;
     * if this results in a <code>SecurityException</code>, this
     * method throws that exception.
     * 
     * <p>If there is a security manager and an HTTP proxy is not to
     * be used for the communication:
     *
     * <ul>
     *
     * <li>If a new connection is to be created, the security
     * manager's {@link SecurityManager#checkConnect(String,int)
     * checkConnect} method is invoked with this
     * <code>HttpEndpoint</code>'s host and <code>-1</code> for the
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
     * this <code>HttpEndpoint</code>'s host and <code>-1</code> for
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
	return new OutboundRequestIterator() {
	    private boolean nextCalled = false;
	    private OutboundRequest currentRequest;
	    public OutboundRequest next() throws IOException {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		nextCalled = true;
		currentRequest = nextRequest(constraints);
		return currentRequest;
	    }
	    public boolean hasNext() {
		// NYI: determine if HTTP failure suggests retry
		return !nextCalled;
	    }
	};
    }

    private OutboundRequest nextRequest(InvocationConstraints constraints)
	throws IOException
    {
	final Constraints.Distilled distilled =
	    Constraints.distill(constraints, false);

	final OutboundRequest request = nextRequest(distilled);

	// must wrap to provide getUnfulfilledConstraints implementation
	return new OutboundRequest() {
	    public void populateContext(Collection context) {
		request.populateContext(context);
	    }
	    public InvocationConstraints getUnfulfilledConstraints() {
		return distilled.getUnfulfilledConstraints();
	    }
	    public OutputStream getRequestOutputStream() {
		return request.getRequestOutputStream();
	    }
	    public InputStream getResponseInputStream() {
		return request.getResponseInputStream();
	    }
	    public boolean getDeliveryStatus() {
		return request.getDeliveryStatus();
	    }
	    public void abort() { request.abort(); }
	};
    }


    /**
     * Describes an action to be performed on a Connection.
     */
    private interface ConnectionAction {
	Object run(Connection conn) throws IOException;
    }


    /**
     * Find an existing connection and perform the specified action on
     * the connection. If there is no suitable existing connection, then
     * create a connection meeting the specified constraints and perform
     * the specified action on the connection.
     */
    private Object connectionAction(final Constraints.Distilled distilled,
				    final String phost, final int pport,
				    final boolean ppersist,
				    ConnectionAction action)
	throws IOException
    {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "about to perform nextRequest on {0}", this );
	}

	boolean usingProxy = (phost.length() != 0);
	Connection conn;
	synchronized (connections) {
	    if (!(proxyHost.equals(phost) &&
		  proxyPort == pport &&
		  persist == ppersist))
	    {
		proxyHost = phost;
		proxyPort = pport;
		persist = ppersist;
		shedConnections();
	    }

	    boolean checkedResolvePermission = false;
	    for (Iterator i = connections.iterator(); i.hasNext();) {
		conn = (Connection) i.next();
		if (!usingProxy) {
		    if (!checkedResolvePermission) {
			try {
			    checkResolvePermission();
			} catch (SecurityException e) {
			    if (logger.isLoggable(Levels.FAILED)) {
				LogUtil.logThrow(logger, Levels.FAILED,
				    HttpEndpoint.class, "nextRequest",
				    "exception resolving host {0}",
				    new Object[] { host }, e);
			    }
			    throw e;
			}
			checkedResolvePermission = true;
		    }
		    try {
			conn.checkConnectPermission();
		    } catch (SecurityException e) {
			if (logger.isLoggable(Levels.HANDLED)) {
			    LogUtil.logThrow(logger, Levels.HANDLED,
				HttpEndpoint.class, "nextRequest",
				"access to reuse connection {0} denied",
				new Object[] { conn.getSocket() }, e);
			}
			continue;
		    }
		}
		i.remove();
		if (connTimer.cancelTimeout(conn)) {
		    try {
			Object obj = action.run(conn);
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(Level.FINE,
				       "nextRequest on existing connection {0}",
				       conn.getSocket());
			}
			return obj;
		    } catch (IOException ex) {
			if (logger.isLoggable(Levels.HANDLED)) {
			    LogUtil.logThrow(logger, Levels.HANDLED,
					     HttpEndpoint.class,
					     "nextRequest",
					     "nextRequest on existing " +
					     "connection {0} throws",
					     new Object[] { this }, ex);
			}
		    }
		}
		conn.shutdown(true);
	    }
	}

	try {
	    if (!usingProxy) {
		conn = new Connection(host, port, distilled);
	    } else {
		conn = (Connection) AccessController.doPrivileged(
		    new PrivilegedExceptionAction() {
			public Object run() throws IOException {
			    return new Connection(host, port,
						  phost, pport, ppersist,
						  distilled);
			}
		    });
	    }
	} catch (PrivilegedActionException e) {
	    throw (IOException) e.getCause();
	}

	try {
	    Object obj = action.run(conn);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "nextRequest on new connection {0}", this);
	    }
	    return obj;
	} catch (IOException ex) {
	    if (logger.isLoggable(Levels.FAILED)) {
		LogUtil.logThrow(logger, Levels.FAILED, HttpEndpoint.class,
				 "nextRequest", "nextRequest on new " +
				 "connection {0} throws IOException",
				 new Object[] { this }, ex);
	    }
	    throw ex;
	}
    }


    private OutboundRequest nextRequest(final Constraints.Distilled distilled)
	throws IOException
    {
	HttpSettings settings = getHttpSettings();
	String phost = settings.getProxyHost(host);
	boolean usingProxy = (phost.length() != 0);
	int pport;
	boolean ppersist;

	// If looking for a connection through an HTTP proxy, and if the
	// pingProxyConnectionTimeout has passed, then ping the server
	// endpoint to verify that the endpoint is alive and reachable.
	// This reduces the likelihood that a server endpoint having
	// terminated or having become unreachable won't be noticed
	// until after some or all of the data destined for the server
	// has already been sent to the proxy.

	if (!usingProxy) {
	    pport = -1;
	    ppersist = true;
	} else {
	    pport = settings.getProxyPort();
	    ppersist = !settings.getDisableProxyPersistentConnections();

	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkConnect(host, port);
	    }

	    long now = System.currentTimeMillis();
	    if (settings.getPingProxyConnections() &&
		now - timeLastVerified >
		                      settings.getPingProxyConnectionTimeout())
	    {
		Object obj = connectionAction(distilled, phost, pport, ppersist,
		          new ConnectionAction() {
			      public Object run(Connection conn)
				  throws IOException
			      {
				  return Boolean.valueOf(conn.ping());
			      }
			  });
		if (!((Boolean) obj).booleanValue()) {
		    throw new IOException("HTTP ping via proxy failed.");
		}
		timeLastVerified = System.currentTimeMillis();
	    }
	}

	// Find a suitable existing connection or create a new
	// connection, and set up a request on that connection.

	Object obj = connectionAction(distilled, phost, pport, ppersist,
		      new ConnectionAction() {
			  public Object run(Connection conn)
			      throws IOException
			  {
			      return conn.newRequest();
			  }
		      });
	return (OutboundRequest) obj;
    }
    
    
    /**
     * Closes all idle connections cached by this HTTP endpoint.
     **/
    private void shedConnections() {
	synchronized (connections) {
	    Object[] conns = connections.toArray();
	    connections.clear();
	    for (int i = 0; i < conns.length; i++) {
		((Connection) conns[i]).shutdown(true);
	    }
	}
    }

    private void checkResolvePermission() {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkConnect(host, -1);
	}
    }
    
    /**
     * Returns the hash code value for this <code>HttpEndpoint</code>.
     *
     * @return the hash code value for this <code>HttpEndpoint</code>
     **/
    public int hashCode() {
	return host.hashCode() ^ port ^
	    (sf != null ? sf.hashCode() : 0);
    }

    /**
     * Compares the specified object with this
     * <code>HttpEndpoint</code> for equality.
     *
     * <p>This method returns <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object is also an <code>HttpEndpoint</code>,
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
	} else if (!(obj instanceof HttpEndpoint)) {
	    return false;
	}
	HttpEndpoint other = (HttpEndpoint) obj;
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
     * <li>the specified object is also an <code>HttpEndpoint</code>,
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
	} else if (!(obj instanceof HttpEndpoint)) {
	    return false;
	}
	HttpEndpoint other = (HttpEndpoint) obj;
	return
	    host.equals(other.host) &&
	    port == other.port &&
	    Util.sameClassAndEquals(sf, other.sf);
    }

    /**
     * Returns a string representation of this
     * <code>HttpEndpoint</code>.
     *
     * @return a string representation of this
     * <code>HttpEndpoint</code>
     **/
    public String toString() {
	return "HttpEndpoint[" + host + ":" + port + 
	    (sf != null ? "," + sf : "") + "]";
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
	check(host, port);
    }
    
    /**
     * AtomicSerial constructor.
     * @param arg
     * @throws IOException 
     */
    public HttpEndpoint(GetArg arg) throws IOException{
	this(arg.get("host", null, String.class),
	     arg.get("port", 0),
	     arg.get("sf", null, SocketFactory.class)
	);
    }
    
    private static String check(String host, int port) throws InvalidObjectException{
	if (host == null) {
	    throw new InvalidObjectException("null host");
	}
	if (port < 1 || port > 0xFFFF) {
	    throw new InvalidObjectException(
	        "port number out of range: " + port);
	}
	return host;
    }

    private HttpEndpoint(SocketFactory sf, String host, int port) throws InvalidObjectException{
	this(check(host,port), port, sf);
    }
    

    /**
     * Returns current HTTP system property settings.
     **/
    static HttpSettings getHttpSettings() {
	return (HttpSettings) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return HttpSettings.getHttpSettings(false);
		}
	    });
    }

    /**
     * SocketFactory -> HttpClientSocketFactory adapter.
     **/
    private static final class SocketFactoryAdapter
	implements HttpClientSocketFactory
    {
	private final SocketFactory sf;
	private final Constraints.Distilled distilled;

	SocketFactoryAdapter(SocketFactory sf,
			     Constraints.Distilled distilled)
	{
	    this.sf = sf;
	    this.distilled = distilled;
	}
	
	public Socket createSocket(String host, int port) throws IOException {
	    Socket socket = connectToHost(host, port, distilled);

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "connected socket {0}", socket);
	    }

	    setSocketOptions(socket);
	    
	    return socket;
	}

	/**
	 * Returns a socket connected to the specified host and port,
	 * according to the specified constraints.  If the host name
	 * resolves to multiple addresses, attempts to connect to each
	 * of them in order until one succeeds.
	 **/
	private Socket connectToHost(String host,
				     int port,
				     Constraints.Distilled distilled)
	    throws IOException
	{
	    InetAddress[] addresses;
	    try {
		addresses = InetAddress.getAllByName(host);
	    } catch (UnknownHostException uhe) {
		try {
		    /*
		     * Calling the InetSocketAddress constructor attempts to
		     * resolve the host again; since J2SE 5.0, there is a
		     * factory method for creating an unresolved
		     * InetSocketAddress directly.
		     */
		    return connectToSocketAddress(
			InetSocketAddress.createUnresolved(host, port), distilled);
		} catch (IOException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    SocketFactoryAdapter.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { host + ":" + port }, e);
		    }
		    throw e;
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    SocketFactoryAdapter.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { host + ":" + port }, e);
		    }
		    throw e;
		}
	    } catch (SecurityException e) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			SocketFactoryAdapter.class, "connectToHost",
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
			    SocketFactoryAdapter.class, "connectToHost",
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
			    SocketFactoryAdapter.class, "connectToHost",
			    "exception connecting to {0}",
			    new Object[] { socketAddress }, e);
		    }
		    lastSecurityException = e;
		}
	    }
	    if (lastIOException != null) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			SocketFactoryAdapter.class, "connectToHost",
			"exception connecting to {0}",
			new Object[] { host + ":" + port },
			lastIOException);
		}
		throw lastIOException;
	    }
	    assert lastSecurityException != null;
	    if (logger.isLoggable(Levels.FAILED)) {
		LogUtil.logThrow(logger, Levels.FAILED,
		    SocketFactoryAdapter.class, "connectToHost",
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
		socket = new Socket();
	    }

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   (sf == null ? "created socket {0}" :
			    "created socket {0} using factory {1}"),
			   new Object[] { socket, sf });
	    }

	    return socket;
	}

	public Socket createTunnelSocket(Socket s) throws IOException {
	    // proxy tunneling never used
	    throw new UnsupportedOperationException();
	}
    }
    
    /**
     * HTTP connection for sending requests.
     **/
    private final class Connection extends HttpClientConnection {
	
	private final String proxyHost;
	private final int proxyPort;

	/**
	 * Creates a direct connection to given host/port.
	 **/
	Connection(String host, int port, Constraints.Distilled distilled)
	    throws IOException
	{
	    super(host, port,
		  new SocketFactoryAdapter(sf, distilled), clientManager);
	    proxyHost = "";
	    proxyPort = -1;
	}

	/**
	 * Creates a proxied connection to given host/port.
	 **/
	Connection(String host,
		   int port,
		   String proxyHost,
		   int proxyPort,
		   boolean persist,
		   Constraints.Distilled distilled)
	    throws IOException
	{
	    super(host, port, proxyHost, proxyPort, false, persist,
		  new SocketFactoryAdapter(sf, distilled), clientManager);
	    this.proxyHost = proxyHost;
	    this.proxyPort = proxyPort;
	}
	
	/**
	 * Adds connection to idle connection cache, schedules timeout.
	 **/
	protected void idle() {
	    synchronized (connections) {
		if (proxyHost.equals(HttpEndpoint.this.proxyHost) &&
		    proxyPort == HttpEndpoint.this.proxyPort &&
		    persist == HttpEndpoint.this.persist)
		{
		    connections.add(this);
		    connTimer.scheduleTimeout(this, false);
		} else {
		    super.shutdown(true);
		}
	    }
	}
	
	/**
	 * Attempts to close connection.
	 **/
	public boolean shutdown(boolean force) {
	    Socket sock = getSocket();
	    boolean socketClosed;
	    synchronized (connections) {
		socketClosed = super.shutdown(force);
		if (socketClosed) {
		    connections.remove(this);
		    connTimer.cancelTimeout(this);
		}
	    }
	    if (socketClosed) {
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "closed socket {0}", sock);
		}
		return true;
	    }
	    return false;
	}

	void checkConnectPermission() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		Socket socket = getSocket();
		InetSocketAddress address =
		    (InetSocketAddress) socket.getRemoteSocketAddress();
		if (address.isUnresolved()) {
		    sm.checkConnect(address.getHostName(), socket.getPort());
		} else {
		    sm.checkConnect(address.getAddress().getHostAddress(),
				    socket.getPort());
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
				 HttpEndpoint.class, "setSocketOptions",
				 "exception setting TCP_NODELAY on socket {0}",
				 new Object[] { socket }, e);
	    }
	}
	try {
	    socket.setKeepAlive(true);
	} catch (SocketException e) {
	    if (logger.isLoggable(Levels.HANDLED)) {
		LogUtil.logThrow(logger, Levels.HANDLED,
				 HttpEndpoint.class, "setSocketOptions",
				"exception setting SO_KEEPALIVE on socket {0}",
				 new Object[] { socket }, e);
	    }
	}
    }
}
