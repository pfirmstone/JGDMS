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

package net.jini.jeri.ssl;

import org.apache.river.action.GetLongAction;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.security.Permission;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.ContextPermission;
import net.jini.io.context.ServerSubject;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.security.Security;

/**
 * Implementation of Connection used by SslEndpoint.
 *
 * 
 */
class SslConnection extends Utilities implements Connection {

    /* -- Fields -- */

    /**
     * The maximum time a client session should be used before expiring --
     * non-final to facilitate testing.  Use 23.5 hours as the default to allow
     * the client to negotiate a new session before the server timeout, which
     * defaults to 24 hours.
     */
    private final long maxClientSessionDuration =
	((Long) Security.doPrivileged(
	    new GetLongAction("org.apache.river.jeri.ssl.maxClientSessionDuration",
			      (long) (23.5 * 60 * 60 * 1000)))).longValue();

    /** Client logger */
    private static final Logger logger = CLIENT_LOGGER;

    /** The server host */
    final String serverHost;

    /** The server port */
    final int port;

    /**
     * The socket factory for creating plain sockets, or null to use default
     * sockets.
     */
    final SocketFactory socketFactory;

    /** The call context specified when the connection was made */
    final CallContext callContext;

    /**
     * The SSLContext -- only shared by connections with the same host, port,
     * suite, and principals.
     */
    private final SSLContext sslContext;

    /** The factory for creating SSL sockets. */
    final SSLSocketFactory sslSocketFactory;	

    /** The authentication manager. */
    private final ClientAuthManager authManager;

    /**
     * The socket -- used only by the HTTPS subclass ({@code HttpsConnection}),
     * which drives TLS over an HTTP-proxy tunnel with an {@code SSLSocket}.  The
     * direct SSL transport uses {@link #engineChannel} instead ({@code null}
     * socket).
     */
    volatile SSLSocket sslSocket;

    /**
     * The {@code SSLEngine}-over-{@code SocketChannel} driver for the direct SSL
     * transport, or {@code null} when the HTTPS subclass is driving TLS over an
     * {@code SSLSocket} tunnel instead.  This is the P1 keystone: the TLS record
     * layer is driven by an {@code SSLEngine} obtained from the same
     * {@code SSLContext} + {@code AuthManager} + {@code SubjectCredentials} the
     * socket path used, so the SPIFFE/mTLS identity machinery is unchanged.
     */
    volatile SslEngineChannel engineChannel;

    /** The currently active cipher suite */
    volatile private String activeCipherSuite;

    /** The current session */
    volatile private SSLSession session;

    /** True if the connection has been closed. */
    volatile boolean closed;

    /* -- Methods -- */

    /**
     * Creates a connection.
     *
     * @param callContext the call context to establish
     * @param serverHost the server host to connect to
     * @param port the server port to connect to
     * @param socketFactory the socket factory, or null to use default sockets
     */
    SslConnection(CallContext callContext,
		  String serverHost,
		  int port,
		  SocketFactory socketFactory)
    {
	this.serverHost = serverHost;
	this.port = port;
	this.socketFactory = socketFactory;
	if (callContext == null) {
	    throw new NullPointerException("Call context cannot be null");
	}
	this.callContext = callContext;
	SSLContextInfo info = getClientSSLContextInfo(callContext);
	sslContext = info.sslContext;
	sslSocketFactory = sslContext.getSocketFactory();
	authManager = (ClientAuthManager) info.authManager;
    }

    /**
     * Establishes a cipher suite on this connection as specified by the call
     * context.
     *
     * @throws UnsupportedSecurityException if the requested constraints cannot
     *	       be supported
     * @throws IOException if an I/O failure occurs
     * @throws SecurityException if the current access control context does not
     *	       have the proper AuthenticationPermission
     */
    final void establishCallContext() throws IOException {
	Exception exception;
	try {
	    establishNewSocket();
	    if (callContext.clientAuthRequired
		&& !authManager.getClientAuthenticated())
	    {
		Exception credExcept =
		    authManager.getClientCredentialException();
		/*
		 * Don't throw the exception that occurred when getting client
		 * credentials if the caller doesn't have access to the
		 * subject.
		 */
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
		    try {
			sm.checkPermission(GET_SUBJECT_PERMISSION);
		    } catch (SecurityException e) {
			credExcept = null;
		    }
		}
		if (credExcept instanceof SecurityException) {
		    exception = (SecurityException) credExcept;
		} else {
		    exception = new UnsupportedConstraintException(
			"Client not authenticated", credExcept);
		}
	    } else {
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			       "new connection for {0}\ncreates {1}",
			       new Object[] { callContext, this });
		}
		return;
	    }
	} catch (SSLProtocolException e) {
	    /*
	     * Don't throw an UnsupportedConstraintException -- this is a
	     * problem within the SSL implementation.
	     */
	    exception = e;
	} catch (SSLException e) {
	    exception = new UnsupportedConstraintException(e.getMessage(), e);
	} catch (IOException e) {
	    exception = e;
	} catch (SecurityException e) {
	    exception = e;
	}
	if (logger.isLoggable(Levels.FAILED)) {
	    logThrow(logger, Levels.FAILED,
		     SslConnection.class, "establishCallContext",
		     "new connection for {0}\nthrows",
		     new Object[] { callContext },
		     exception);
	}
	closeSocket();

	if (exception instanceof IOException) {
	    throw (IOException) exception;
	} else {
	    throw (SecurityException) exception;
	}
    }

    /** Closes the socket/engine for this connection. */
    private void closeSocket() {
	SslEngineChannel ec = engineChannel;
	if (ec != null) {
	    try {
		ec.close();
	    } catch (IOException e) {
	    }
	    engineChannel = null;
	}
	if (sslSocket != null) {
	    try {
		sslSocket.close();
	    } catch (IOException e) {
	    }
	    sslSocket = null;
	}
	if (ec != null || sslSocket != null) {
	    session = null;
	    activeCipherSuite = null;
	}
    }

    /**
     * Attempts to create a new connection for the call context and cipher
     * suites, driving the TLS 1.3 handshake with an {@code SSLEngine} over a
     * blocking {@code SocketChannel} (the P1 keystone).  The HTTPS subclass
     * overrides this to drive TLS over an {@code SSLSocket} HTTP-proxy tunnel.
     *
     * @throws SSLException if the suites cannot be supported
     * @throws IOException if an I/O failure occurs
     */
    void establishNewSocket() throws IOException {
	if (socketFactory != null || callContext.endpointImpl.disableSocketConnect) {
	    /*
	     * A user-supplied SocketFactory (whose Socket generally has no
	     * associated SocketChannel), or a discovery-provider endpoint that
	     * disables Socket.connect: drive TLS over the SSLSocket path so those
	     * callers keep working.  The engine keystone path is used for the
	     * default (channel-backed) transport.
	     */
	    Socket socket = createPlainSocket(serverHost, port);
	    sslSocket = (SSLSocket) sslSocketFactory.createSocket(
		socket, serverHost, port, /* autoClose */ true);
	    establishSuites();
	    return;
	}
	SocketChannel ch = connectChannel(serverHost, port, callContext.connectionTime);
	establishEngine(ch);
    }

    /**
     * Opens and connects a blocking {@code SocketChannel} to the given host and
     * port, honouring the absolute connection deadline and trying each resolved
     * address in turn -- the channel analogue of {@link #connectToHost}.
     */
    private SocketChannel connectChannel(String host, int port, long connectionTime)
	throws IOException
    {
	InetAddress[] addresses;
	try {
	    addresses = InetAddress.getAllByName(host);
	} catch (UnknownHostException uhe) {
	    return connectChannelAddress(
		new InetSocketAddress(host, port), connectionTime);
	}
	IOException lastIOException = null;
	SecurityException lastSecurityException = null;
	for (int i = 0; i < addresses.length; i++) {
	    SocketAddress socketAddress = new InetSocketAddress(addresses[i], port);
	    try {
		return connectChannelAddress(socketAddress, connectionTime);
	    } catch (IOException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    LogUtil.logThrow(logger, Levels.HANDLED,
				     SslConnection.class, "connectChannel",
				     "exception connecting to {0}",
				     new Object[] { socketAddress }, e);
		}
		lastIOException = e;
		if (e instanceof SocketTimeoutException) {
		    break;
		}
	    } catch (SecurityException e) {
		lastSecurityException = e;
	    }
	}
	if (lastIOException != null) {
	    throw lastIOException;
	}
	assert lastSecurityException != null;
	throw lastSecurityException;
    }

    /** Connects a fresh blocking SocketChannel to one address with a deadline. */
    private SocketChannel connectChannelAddress(SocketAddress socketAddress,
						long connectionTime)
	throws IOException
    {
	int timeout = computeTimeout(connectionTime);
	SocketChannel ch = SocketChannel.open();
	boolean ok = false;
	try {
	    Socket s = ch.socket();
	    try {
		s.setTcpNoDelay(true);
	    } catch (SocketException e) {
	    }
	    try {
		s.setKeepAlive(true);
	    } catch (SocketException e) {
	    }
	    /*
	     * SocketChannel.connect() ignores the socket read timeout, so use
	     * the socket's connect(addr, timeout) for the connect deadline, then
	     * keep the channel in blocking mode for the handshake and I/O.
	     */
	    if (timeout > 0) {
		s.connect(socketAddress, timeout);
	    } else {
		ch.connect(socketAddress);
	    }
	    ch.configureBlocking(true);
	    ok = true;
	    return ch;
	} finally {
	    if (!ok) {
		try {
		    ch.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Drives the client-side TLS 1.3 handshake over the given (connected,
     * blocking) channel using an {@code SSLEngine} from this connection's
     * {@code SSLContext}.  Client-auth credential selection, peer trust
     * validation, cert&harr;Subject mapping and SPIFFE matching all run through
     * the same {@code AuthManager} JSSE callbacks the socket path used.
     */
    final void establishEngine(SocketChannel ch) throws IOException {
	boolean ok = false;
	try {
	    ch.configureBlocking(true);
	    SSLEngine engine = sslContext.createSSLEngine(serverHost, port);
	    engine.setUseClientMode(true);
	    /*
	     * Restrict the enabled suites to those requested by the call
	     * context and supported by JSSE, mirroring establishSuites() on the
	     * socket path.  Endpoint identification is deliberately NOT set:
	     * JGDMS authenticates the peer by SPIFFE/X.500 identity via the
	     * AuthManager, not by hostname (consistent with the SSLSocket path
	     * and the UDS transport).
	     */
	    String[] ciphers = removeUnsupportedCiphers(
		engine.getSupportedCipherSuites(), callContext.cipherSuites);
	    SSLParameters params = engine.getSSLParameters();
	    params.setCipherSuites(ciphers);
	    /*
	     * Pin the protocol to TLS 1.3.  The engine path relies on "TLS 1.3
	     * has no renegotiation" to drop the SSLSocket path's mid-stream
	     * identity-swap guard; that premise must be ENFORCED, not assumed, so
	     * a TLS 1.2 (renegotiable) handshake is structurally excluded here
	     * rather than merely by the default SSLContext protocol.  The engine
	     * still fail-closes at decacheSession if the negotiated protocol is
	     * not TLSv1.3 (SslEngineChannel.getSession()/decacheSession).
	     */
	    params.setProtocols(new String[]{ "TLSv1.3" });
	    engine.setSSLParameters(params);

	    final SslEngineChannel ec = new SslEngineChannel(
		engine, ch, serverHost + ":" + port);
	    /*
	     * Drive the handshake inline on the calling thread (a virtual thread
	     * in the JGDMS model), preserving the caller's exact security context
	     * for the AuthenticationPermission checks in chooseClientAlias.  The
	     * SSLEngine's NEED_TASK delegated tasks run inline too (see
	     * SslEngineChannel.runDelegatedTasks) -- the explicit, tested
	     * virtual-thread-first policy the QUIC engine loop also assumes.
	     */
	    /*
	     * Bound each handshake read by the call context's connection deadline
	     * when one is set (mirroring connectChannelAddress), else the generous
	     * default inactivity timeout -- a stalled peer cannot hang the caller.
	     */
	    ec.handshake(clientHandshakeTimeout(callContext.connectionTime));
	    session = engine.getSession();
	    /*
	     * Fail-closed: the negotiated protocol MUST be TLS 1.3.  Belt-and-
	     * suspenders with setProtocols above -- if anything (a future SSLContext
	     * default, a provider quirk) let a non-1.3 protocol through, reject the
	     * connection rather than run without the renegotiation guarantee.
	     */
	    if (!"TLSv1.3".equals(session.getProtocol())) {
		throw new SSLException(
		    "Refusing non-TLS-1.3 client connection: negotiated "
		    + session.getProtocol()
		    + " (the SSLEngine transport requires TLS 1.3)");
	    }
	    /*
	     * Restore the SSLSocket path's post-handshake setEnableSessionCreation
	     * (false): once the session is established, no new session may be
	     * created on this engine.  Under TLS 1.3 this blocks any post-handshake
	     * session establishment, closing the "new handshake on the connection"
	     * hazard by construction.
	     */
	    engine.setEnableSessionCreation(false);
	    engineChannel = ec;
	    activeCipherSuite = session.getCipherSuite();
	    releaseClientSSLContextInfo(callContext, sslContext, authManager);
	    ok = true;
	} finally {
	    if (!ok) {
		try {
		    ch.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * The per-read handshake progress timeout (F2) for a client engine
     * handshake: the remaining time until the call context's absolute
     * connection deadline when one is set (never negative), else the generous
     * default.  {@code connectionTime == -1} means "no deadline" -> the default.
     */
    private static long clientHandshakeTimeout(long connectionTime) {
	if (connectionTime == -1) {
	    return SslEngineChannel.DEFAULT_HANDSHAKE_TIMEOUT_MILLIS;
	}
	long remaining = connectionTime - System.currentTimeMillis();
	if (remaining <= 0) {
	    /* Deadline already passed; use a minimal positive bound so the
	     * handshake fails fast rather than blocking. */
	    return 1L;
	}
	return Math.min(remaining, SslEngineChannel.DEFAULT_HANDSHAKE_TIMEOUT_MILLIS);
    }


    /**
     * Attempts to establish the call context and suites on the current socket.
     *
     * @throws SSLException if the requested suites cannot be supported
     * @throws IOException if an I/O failure occurs
     */
    final void establishSuites() throws IOException {
	String [] supportedCiphers = sslSocket.getSupportedCipherSuites();
	String [] ciphers = removeUnsupportedCiphers(supportedCiphers, callContext.cipherSuites);
	sslSocket.setEnabledCipherSuites(ciphers);
	sslSocket.startHandshake();
	session = sslSocket.getSession();
	activeCipherSuite = session.getCipherSuite();
	sslSocket.setEnableSessionCreation(false);
	releaseClientSSLContextInfo(callContext, sslContext, authManager);
    }
    
    private String[] removeUnsupportedCiphers(String[] supported, String[] requested){
	Set<String> supportedCiphers = new HashSet<String>(Arrays.asList(supported));
	int length = requested.length;
	List<String> result = new ArrayList(length);
	for (int i=0; i<length; i++){
	    if (supportedCiphers.contains(requested[i])) result.add(requested[i]);
	}
	return result.toArray(new String[result.size()]);
    }

    /**
     * Creates a plain socket to use for communication with the specified host
     * and port.
     */
    final Socket createPlainSocket(String host, int port) throws IOException {
	Socket socket;
	if (!callContext.endpointImpl.disableSocketConnect) {
	    /* Connect with proper timeout */
	    socket = connectToHost(host, port, callContext.connectionTime);
	} else {
	    socket = newSocket();
	}
	return socket;
    }


    private static int computeTimeout(long connectionTime)
	throws IOException
    {
	int timeout;
	long current = System.currentTimeMillis();
	if (connectionTime == -1) {
	    timeout = 0;
	} else if (connectionTime < current) {
	    throw new IOException("Connection not made within specified time");
	} else if (connectionTime - current > Integer.MAX_VALUE) {
	    timeout = 0;
	} else {
	    timeout = (int) (connectionTime - current);
	}
	return timeout;
    }

    /**
     * Returns a socket connected to the specified host and port,
     * according to the specified constraints.  If the host name
     * resolves to multiple addresses, attempts to connect to each of
     * them in order until one succeeds.
     **/
    private Socket connectToHost(String host, int port, long connectionTime)
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
		    new InetSocketAddress(host, port), connectionTime);
	    } catch (IOException e) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,	
			    SslConnection.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { host + ":" + port }, e);
		}
		throw e;
	    } catch (SecurityException e) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			    SslConnection.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { host + ":" + port }, e);
		}
		throw e;
	    }
	} catch (SecurityException e) {
	    if (logger.isLoggable(Levels.FAILED)) {
		LogUtil.logThrow(logger, Levels.FAILED,
				 SslConnection.class, "connectToHost",
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
		return connectToSocketAddress(socketAddress, connectionTime);
	    } catch (IOException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    LogUtil.logThrow(logger, Levels.HANDLED,
				     SslConnection.class, "connectToHost",
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
				     SslConnection.class, "connectToHost",
				     "exception connecting to {0}",
				     new Object[] { socketAddress }, e);
		}
		lastSecurityException = e;
	    }
	}
	if (lastIOException != null) {
	    if (logger.isLoggable(Levels.FAILED)) {
		LogUtil.logThrow(logger, Levels.FAILED,
				 SslConnection.class, "connectToHost",
				 "exception connecting to {0}",
				 new Object[] { host + ":" + port },
				 lastIOException);
	    }
	    throw lastIOException;
	}
	assert lastSecurityException != null;
	if (logger.isLoggable(Levels.FAILED)) {
	    LogUtil.logThrow(logger, Levels.FAILED,
			     SslConnection.class, "connectToHost",
			     "exception connecting to {0}",
			     new Object[] { host + ":" + port },
			     lastSecurityException);
	}
	throw lastSecurityException;
    }

    /**
     * Returns a socket connected to the specified address, with a
     * timeout governed by the specified absolute connection time.
     **/
    private Socket connectToSocketAddress(SocketAddress socketAddress,
					  long connectionTime)
	throws IOException
    {
	int timeout = computeTimeout(connectionTime);
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
	Socket socket = socketFactory != null
	    ? socketFactory.createSocket() : new Socket();
	/* Send data without delay */
	try {
	    socket.setTcpNoDelay(true);
	} catch (SocketException e) {
	}
	/* Send periodic pings so we can tell if the connection dies. */
	try {
	    socket.setKeepAlive(true);
	} catch (SocketException e) {
	}
	return socket;
    }

    /** Returns a string representation of this object. */
    public String toString() {
	String sessionString = (session == null) ? "" : session + ", ";
	String local;
	if (engineChannel != null) {
	    local = "engine";
	} else if (sslSocket != null) {
	    local = Integer.toString(sslSocket.getLocalPort());
	} else {
	    local = "???";
	}
	return getClassName(this) + "[" +
	    sessionString + local +
	    "=>" + serverHost + ":" + port + "]";
    }

    /* -- Implement Connection -- */

    /* inherit javadoc */
    public InputStream getInputStream() throws IOException {
	SslEngineChannel ec = engineChannel;
	if (ec != null) {
	    return ec.getInputStream();
	} else if (sslSocket != null) {
	    return sslSocket.getInputStream();
	} else {
	    throw new IOException("No connection established");
	}
    }

    /* inherit javadoc */
    public OutputStream getOutputStream() throws IOException {
	SslEngineChannel ec = engineChannel;
	if (ec != null) {
	    return ec.getOutputStream();
	} else if (sslSocket != null) {
	    return sslSocket.getOutputStream();
	} else {
	    throw new IOException("No connection established");
	}
    }

    /* inherit javadoc */
    public SocketChannel getChannel() {
	/*
	 * Always null: the underlying SocketChannel (when the engine path is
	 * used) carries CIPHERTEXT, and the JERI mux's channel path would read
	 * it directly, bypassing TLS.  Plaintext flows through the wrap/unwrap
	 * streams instead -- exactly as the SSLSocket transport routed plaintext
	 * through the socket streams and returned null here.
	 */
	return null;
    }

    /* inherit javadoc */
    public void populateContext(OutboundRequestHandle handle,
				Collection context)
    {
	CallContext.coerce(handle, callContext.endpoint);
	if (context == null) {
	    throw new NullPointerException("Context cannot be null");
	}
	// Add the authenticated server Subject to the context so that
	// PreferredProxyCodebaseProvider.resolve() can scope DigestGrants to
	// the actual TLS-verified server identity rather than to client-declared
	// ServerMinPrincipal constraints.  Only added when the server has
	// authenticated (serverPrincipal != null after the TLS handshake).
	X500Principal serverX500 = authManager.getServerPrincipal();
	if (serverX500 != null) {
	    Set<Principal> principals = new HashSet<Principal>();
	    principals.add(serverX500);
	    // Also include SPIFFE principal(s) from the URI Subject
	    // Alternative Name(s) in the server certificate, if present.  These
	    // must be real SpiffePrincipal instances (resolved by name via
	    // Utilities) because this Subject's principals are evaluated by the
	    // security policy in PreferredProxyCodebaseProvider, which keys on
	    // the principal's runtime class -- see
	    // Utilities.spiffePrincipalsFromCertificate.
	    SSLSession currentSession = session;
	    if (currentSession != null) {
		try {
		    Certificate[] peerCerts = currentSession.getPeerCertificates();
		    if (peerCerts != null && peerCerts.length > 0
			    && peerCerts[0] instanceof X509Certificate) {
			principals.addAll(
			    Utilities.spiffePrincipalsFromCertificate(
				(X509Certificate) peerCerts[0]));
		    }
		} catch (SSLPeerUnverifiedException e) {
		    // Server is anonymous — no peer certificates; ignore.
		}
	    }
	    final Subject serverSubject = new Subject(
		    true,
		    Collections.unmodifiableSet(principals),
		    Collections.emptySet(),
		    Collections.emptySet());
	    context.add(new ServerSubject() {
		private static final Permission GET_SERVER_SUBJECT_PERM =
		    new ContextPermission(
			"net.jini.io.context.ServerSubject.getServerSubject");
		@Override
		public Subject getServerSubject() {
		    SecurityManager sm = System.getSecurityManager();
		    if (sm != null) {
			sm.checkPermission(GET_SERVER_SUBJECT_PERM);
		    }
		    return serverSubject;
		}
	    });
	}
    }

    /* inherit javadoc */
    public InvocationConstraints getUnfulfilledConstraints(
	OutboundRequestHandle handle)
    {
	CallContext callContext = CallContext.coerce(
	    handle, this.callContext.endpoint);
	return callContext.getUnfulfilledConstraints();
    }

    /* inherit javadoc */
    public void writeRequestData(OutboundRequestHandle handle,
				 OutputStream stream)
    {
	CallContext.coerce(handle, callContext.endpoint);
	if (stream == null) {
	    throw new NullPointerException("Stream cannot be null");
	}
	/* No per-request data needed */
    }

    /* inherit javadoc */
    public IOException readResponseData(OutboundRequestHandle handle,
					InputStream stream)
    {
	CallContext.coerce(handle, callContext.endpoint);
	if (stream == null) {
	    throw new NullPointerException("Stream cannot be null");
	}
	/* No per-response data needed */
	return null;
    }

    /* inherit javadoc */
    public synchronized void close() throws IOException {
	if (!closed) {
	    logger.log(Level.FINE, "closing {0}", this);
	    closed = true;
	    closeSocket();
	}
    }

    /**
     * Returns true if this connection is compatible with the specified call
     * context.
     */
    final boolean useFor(CallContext otherCallContext) {
	assert callContext.endpoint.equals(otherCallContext.endpoint);

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "try {0}\nwith {1}\nfor {2}",
		       new Object[] { this, callContext, otherCallContext });
	}

	/* Check that connection is established */
	if (session == null) {
	    logger.log(Level.FINEST, "connection {0} is not established",
		       this);
	    return false;
	}

	/* Check if session is expired */
	if (checkSessionExpired()) {
	    logger.log(Level.FINE, "connection {0} session is expired",
		       this);
	    return false;
	}

	/* Check client subject -- only use if both specified and '==' */
	if (callContext.clientSubject != otherCallContext.clientSubject) {
	    logger.log(Level.FINEST, "connection has wrong subject");
	    return false;
	}

	/* Check client principals */
	X500Principal clientPrincipal = authManager.getClientPrincipal();
	if (clientPrincipal == null) {
	    if (otherCallContext.clientAuthRequired) {
		logger.log(Level.FINEST,
			   "connection has no client authentication");
		return false;
	    }
	} else if (otherCallContext.clientPrincipals != null &&
		   !otherCallContext.clientPrincipals.contains(clientPrincipal))
	{
	    logger.log(Level.FINEST, "connection has wrong client principal");
	    return false;
	}

	/* Check server principals */
	X500Principal serverPrincipal = authManager.getServerPrincipal();
	if (serverPrincipal != null &&
	    otherCallContext.serverPrincipals != null &&
	    !otherCallContext.serverPrincipals.contains(serverPrincipal))
	{
	    logger.log(Level.FINEST, "connection has wrong server principal");
	    return false;
	}

	/* Check that active suite is one of the requested suites */
	String[] requestedSuites = otherCallContext.cipherSuites;
	int requestedPos = position(activeCipherSuite, requestedSuites);
	if (requestedPos < 0) {
	    if (closed) {
		logger.log(Level.FINEST, "connection is closed");
	    } else {
		logger.log(Level.FINEST, "connection has wrong suite");
	    }
	    return false;
	}

	/*
	 * Check that suites that would be better than the suite active on the
	 * connection are also better for this connection's call context,
	 * meaning that they probably wouldn't have worked anyway.
	 */
	String[] connectionSuites = callContext.cipherSuites;
	int connectionPos = position(activeCipherSuite, connectionSuites);
	assert connectionPos >= 0 : "Couldn't find connection suite";
	for (int i = requestedPos; --i >= 0; ) {
	    String suite = requestedSuites[i];
	    int p = position(suite, connectionSuites);
	    if (p < 0 || p >= connectionPos) {
		if (closed) {
		    logger.log(Level.FINEST, "connection is closed");
		} else {
		    logger.log(Level.FINEST,
			   "connection did not try all better suites");
		}
		return false;
	    }
	}

	/* Check client authentication credentials */
	if (clientPrincipal != null) {
	    Exception exception;
	    try {
		authManager.checkAuthentication();
		exception = null;
	    } catch (SecurityException e) {
		exception = e;
	    } catch (UnsupportedConstraintException e) {
		exception = e;
	    }
	    if (exception != null) {
		if (logger.isLoggable(Level.FINEST)) {
		    logThrow(logger, Level.FINEST,
			     SslConnection.class, "useFor",
			     "connection {0} has missing subject credentials",
			     new Object[] { this },
			     exception);
		}
		return false;
	    }
	}

	/* Looks OK */
	logger.log(Level.FINEST, "connection OK");
	return true;
    }

    /**
     * Checks if the session currently active on the connection has been active
     * for longer than maxClientSessionDuration and, if so, invalidates the
     * session.
     */
    private boolean checkSessionExpired() {
	long create = session.getCreationTime();
	long expiration = create + maxClientSessionDuration;
	/* Check for rollover */
	if (expiration < create) {
	    expiration = Long.MAX_VALUE;
	}
	if (expiration <= System.currentTimeMillis()) {
	    session.invalidate();
	    return true;
	} else {
	    return false;
	}
    }


    /**
     * Return HTTP proxy host if present, an empty string otherwise.
     */
    protected String getProxyHost() {
	return "";
    }

    /**
     * Determine whether the caller has "connect" SocketPermission for the
     * connection's underlying socket.
     *
     * @return true if there is an underlying socket and the caller has
     * permission to use it, or false if there is no underlying socket.
     *
     * @throws SecurityException if the underlying socket exists but
     * the caller does not have permission to use it.
     */
    boolean checkConnectPermission() {
	InetSocketAddress address;
	SslEngineChannel ec = engineChannel;
	if (ec != null) {
	    try {
		address = (InetSocketAddress) ec.getRemoteAddress();
	    } catch (IOException e) {
		return false;
	    }
	    if (address == null) {
		return false;
	    }
	} else {
	    Socket socket = sslSocket;
	    if (socket == null) {
		return false;
	    }
	    address = (InetSocketAddress) socket.getRemoteSocketAddress();
	}

	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    if (address.isUnresolved()) {
		sm.checkConnect(address.getHostName(), address.getPort());
	    } else {
		sm.checkConnect(address.getAddress().getHostAddress(),
				address.getPort());
	    }
	}
	return true;
    }
}
