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
import org.apache.river.jeri.internal.connection.BasicServerConnManager;
import org.apache.river.jeri.internal.connection.ServerConnManager;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.security.AuthenticationPermission;
import net.jini.security.Security;
import net.jini.security.SecurityContext;
import org.apache.river.jeri.internal.runtime.LocalHost;

/**
 * Provides the implementation of SslServerEndpoint so that the implementation
 * can be inherited by HttpsServerEndpoint without revealing the inheritance in
 * the public API.
 *
 * 
 */
class SslServerEndpointImpl extends Utilities {

    /* -- Fields -- */

    /** Server logger */
    static final Logger logger = serverLogger;

    /**
     * The maximum time a session should be used before expiring -- non-final
     * to facilitate testing.  Use 24 hours to allow the client, which uses
     * 23.5 hours, to renegotiate a new session before the server timeout.
     */
    private final long maxServerSessionDuration =
	((Long) Security.doPrivileged(
	    new GetLongAction("org.apache.river.jeri.ssl.maxServerSessionDuration",
			      24L * 60L * 60L * 1000L))).longValue();

    /**
     * Executes a Runnable in a system thread -- used for listener accept
     * threads.
     */
    static final Executor systemExecutor = (Executor)
	Security.doPrivileged(new GetThreadPoolAction(false));

    /** The default server connection manager. */
    private static final ServerConnManager defaultServerConnectionManager =
	new BasicServerConnManager();
    
    private static final LocalHost LOCAL_HOST = new LocalHost(logger, SslServerEndpointImpl.class);

    /** The associated server endpoint. */
    private final ServerEndpoint serverEndpoint;

    /** The server subject, or null if the server is anonymous. */
    private final Subject serverSubject;

    /**
     * The principals to use for authentication, or null if the server is
     * anonymous.
     */
    final Set serverPrincipals;

    /**
     * The host name that clients should use to connect to this server, or null
     * if enumerateListenEndpoints should compute the default.
     */
    final String serverHost;

    /** The server port */
    final int port;

    /** The socket factory for use in the associated Endpoint. */
    final SocketFactory socketFactory;

    /** The server socket factory. */
    final ServerSocketFactory serverSocketFactory;

    /**
     * The permissions needed to authenticate when listening on this endpoint,
     * or null if the server is anonymous.  Effectively immutable array.
     */
    private final Permission[] listenPermissions;

    /** The listen endpoint. */
    private final ListenEndpoint listenEndpoint;

    /** The factory for creating JSSE sockets -- set by sslInit */
    private SSLSocketFactory sslSocketFactory; // Synchronized on this

    /**
     * The authentication manager for the SSLContext for this endpoint -- set
     * by sslInit.
     */
    private ServerAuthManager authManager; // Synchronized on this

    /** The server connection manager. */
    ServerConnManager serverConnectionManager = defaultServerConnectionManager;

    /* -- Methods -- */

    /** Creates an instance of this class. */
    SslServerEndpointImpl(ServerEndpoint serverEndpoint,
			  Subject serverSubject,
			  X500Principal[] serverPrincipals,
			  String serverHost,
			  int port,
			  SocketFactory socketFactory,
			  ServerSocketFactory serverSocketFactory)
    {
	this.serverEndpoint = serverEndpoint;
	boolean useCurrentSubject = serverSubject == null;
	if (useCurrentSubject) {
	    final AccessControlContext acc = AccessController.getContext();
	    serverSubject = (Subject) AccessController.doPrivileged(
		new PrivilegedAction() {
		    public Object run() {
			return Subject.getSubject(acc);
		    }
		});
	}
	this.serverPrincipals = (serverPrincipals == null)
	    ? computePrincipals(serverSubject)
	    : checkPrincipals(serverPrincipals);
	/* Set listenPermissions before calling hasListenPermissions */
        Permission [] listenPermissions;
	if (this.serverPrincipals == null) {
	    listenPermissions = null;
            } else {
	    listenPermissions =
		new AuthenticationPermission[this.serverPrincipals.size()];
	    int i = 0;
	    for (Iterator iter = this.serverPrincipals.iterator();
		 iter.hasNext();
		 i++)
	    {
		Principal p = (Principal) iter.next();
		listenPermissions[i] = new AuthenticationPermission(
		    Collections.singleton(p), null, "listen");
	    }
	}
	if (this.serverPrincipals == null ||
	    /* Don't use current subject without any permission */
	    (useCurrentSubject &&
	     serverPrincipals != null &&
	     !hasListenPermissions()))
	{
	    this.serverSubject = null;
	    listenPermissions = null;
	} else {
            this.serverSubject = serverSubject;
	}
        this.listenPermissions = listenPermissions;
	this.serverHost = serverHost;
	if (port < 0 || port > 0xFFFF) {
	    throw new IllegalArgumentException("Invalid port: " + port);
	}
	this.port = port;
	this.socketFactory = socketFactory;
	this.serverSocketFactory = serverSocketFactory;
	listenEndpoint = createListenEndpoint();
    }

    /** Computes the principals in the subject available for authentication */
    private static Set computePrincipals(Subject subject) {
	if (subject == null) {
	    return null;
	}
	/* Get principals from the subject */
	X500PrivateCredential[] credentials =
	    (X500PrivateCredential[]) AccessController.doPrivileged(
		new SubjectCredentials.GetAllPrivateCredentialsAction(
		    subject));
	Set result = SubjectCredentials.getPrincipals(
	    subject, ANY_KEY_ALGORITHM, credentials);
	/* Remove ones we don't have authentication listen permission for. */
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    for (Iterator iter = result.iterator(); iter.hasNext(); ) {
		Principal p = (Principal) iter.next();
		try {
		    sm.checkPermission(
			new AuthenticationPermission(
			    Collections.singleton(p), null, "listen"));
		} catch (SecurityException e) {
		    logger.log(Levels.HANDLED,
			       "compute principals for server endpoint " +
			       "caught exception",
			       e);
		    iter.remove();
		}
	    }
	}
	return result.isEmpty() ? null : result;
    }

    /**
     * Returns true if the caller has AuthenticationPermission for listen on
     * this endpoint.
     */
    private boolean hasListenPermissions() {
	try {
	    checkListenPermissions(false);
	    return true;
	} catch (SecurityException e) {
	    logger.log(Levels.HANDLED,
		       "check listen permissions for server endpoint " +
		       "caught exception",
		       e);
	    return false;
	}
    }

    /**
     * Checks that principals is not empty and contains no nulls, and returns
     * it as a set.  Returns null if no principals are specified.
     */
    private static Set checkPrincipals(X500Principal[] principals) {
	if (principals.length == 0) {
	    return null;
	}
	Set result = new HashSet(principals.length);
	for (int i = principals.length; --i >= 0; ) {
	    X500Principal p = principals[i];
	    if (p == null) {
		throw new NullPointerException(
		    "Server principal cannot be null");
	    }
	    result.add(p);
	}
	return result;
    }

    /**
     * Initializes the sslSocketFactory and authManager fields.  Wait to do
     * this until needed, because creating the SSLContext requires initializing
     * the secure random number generator, which can be time consuming.
     */
    private void sslInit() {
	assert Thread.holdsLock(this);
	SSLContextInfo info = getServerSSLContextInfo(
	    serverSubject, serverPrincipals);
	sslSocketFactory = info.sslContext.getSocketFactory();
	authManager = (ServerAuthManager) info.authManager;
    }

    /** Returns the SSLSocketFactory, calling sslInit if needed. */
    final SSLSocketFactory getSSLSocketFactory() {
	synchronized (this) {
	    if (sslSocketFactory == null) {
		sslInit();
	    }
            return sslSocketFactory;
	}
    }

    /** Returns the ServerAuthManager, calling sslInit if needed. */
    final ServerAuthManager getAuthManager() {
	synchronized (this) {
	    if (authManager == null) {
		sslInit();
	    }
	return authManager;
	}
    }

    /** Returns a hash code value for this object. */
    public int hashCode() {
	return getClass().hashCode()
	    ^ System.identityHashCode(serverSubject)
	    ^ (serverPrincipals == null ? 0 : serverPrincipals.hashCode())
	    ^ (serverHost == null ? 0 : serverHost.hashCode())
	    ^ port
	    ^ (socketFactory != null ? socketFactory.hashCode() : 0)
	    ^ (serverSocketFactory != null
	       ? serverSocketFactory.hashCode() : 0);
    }

    /**
     * Two instances of this class are equal if they have the same actual
     * class; have server subjects that compare equal using ==; have server
     * principals that are either both null or are equal when compared as the
     * elements of a Set; have the same server host and port; have socket
     * factories that are either both null, or have the same actual class and
     * are equal; and have server socket factories that are either both null,
     * or have the same actual class and are equal.
     */
    public boolean equals(Object object) {
	if (object == null || object.getClass() != getClass()) {
	    return false;
	}
	SslServerEndpointImpl other = (SslServerEndpointImpl) object;
	return serverSubject == other.serverSubject
	    && safeEquals(serverPrincipals, other.serverPrincipals)
	    && safeEquals(serverHost, other.serverHost)
	    && port == other.port
	    && Util.sameClassAndEquals(socketFactory, other.socketFactory)
	    && Util.sameClassAndEquals(serverSocketFactory,
				       other.serverSocketFactory);
    }

    /** Returns a string representation of this object. */
    public String toString() {
	return getClassName(this) + fieldsToString();
    }

    /** Returns a string representation of the fields of this object. */
    final String fieldsToString() {
	return "[" +
	    (serverPrincipals == null
	     ? ""
	     : serverPrincipals.toString() + ", ") +
	    (serverHost == null ? "" : serverHost + ":") +
	    port + 
	    (serverSocketFactory != null ? ", " + serverSocketFactory : "") +
	    (socketFactory != null ? ", " + socketFactory : "") +
	    "]";
    }

    /* -- Implement ServerCapabilities -- */

    final InvocationConstraints checkConstraints(
	InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	try {
	    checkListenPermissions(false);
	} catch (SecurityException e) {
	    if (logger.isLoggable(Levels.FAILED)) {
		logThrow(logger, Levels.FAILED,
			 SslServerEndpoint.class, "checkConstraints",
			 "check constraints for {0}\nwith {1}\nthrows",
			 new Object[] { this, constraints },
			 e);
	    }
	    throw e;
	}
	Set clientPrincipals = getClientPrincipals(constraints);
	if (clientPrincipals == null) {
	    clientPrincipals = Collections.singleton(UNKNOWN_PRINCIPAL);
	}
	Map serverKeyTypes = new HashMap();
	List certPaths =
	    SubjectCredentials.getCertificateChains(serverSubject);
	if (certPaths != null) {
	    for (int i = certPaths.size(); --i >= 0; ) {
		CertPath chain = (CertPath) certPaths.get(i);
		X509Certificate cert = SubjectCredentials.firstX509Cert(chain);
		X500Principal principal = SubjectCredentials.getPrincipal(
		    serverSubject, cert);
		if (principal != null) {
		    Collection keyTypes =
			(Collection) serverKeyTypes.get(principal);
		    if (keyTypes == null) {
			keyTypes = new ArrayList(1);
			serverKeyTypes.put(principal, keyTypes);
		    }
		    keyTypes.add(cert.getPublicKey().getAlgorithm());
		}
	    }
	}
	String[] suites = getSupportedCipherSuites();
	for (int suiteIndex = suites.length; --suiteIndex >= 0; ) {
	    String suite = suites[suiteIndex];
	    String suiteKeyType = getKeyAlgorithm(suite);
	    Iterator sIter =
		(serverPrincipals == null
		 ? Collections.EMPTY_SET : serverPrincipals).iterator();
	    X500Principal server;
	    do {
		if (sIter.hasNext()) {
		    server = (X500Principal) sIter.next();
		    assert server != null;
		    Collection keyTypes =
			(Collection) serverKeyTypes.get(server);
		    if (keyTypes == null || !keyTypes.contains(suiteKeyType)) {
			continue;
		    }
		} else {
		    server = null;
		}
		Iterator cIter = clientPrincipals.iterator();
		Principal client;
		do {
		    if (cIter.hasNext()) {
			client = (Principal) cIter.next();
			assert client != null;
		    } else {
			client = null;
		    }
		    InvocationConstraints unfulfilledConstraints =
			getUnfulfilledConstraints(
			    suite, client, server, constraints);
		    if (unfulfilledConstraints != null) {
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(Level.FINE,
				       "check constraints for {0}\n" +
				       "with {1}\nreturns {2}",
				       new Object[] {
					   serverEndpoint, constraints,
					   unfulfilledConstraints
				       });
			}
			return unfulfilledConstraints;
		    }
		} while (client != null);
	    } while (server != null);
	}
	UnsupportedConstraintException uce =
	    new UnsupportedConstraintException(
	    "Constraints are not supported: " + constraints);
	if (logger.isLoggable(Levels.FAILED)) {
	    logThrow(
		logger, Levels.FAILED,
		SslServerEndpoint.class, "checkConstraints",
		"check constraints for {0}\nwith {1}\nthrows",
		new Object[] { serverEndpoint, constraints },
		uce);
	}
	throw uce;
    }

    /**
     * Returns null if the constraints are not supported, else any integrity
     * constraints required or preferred by the arguments.
     */
    static InvocationConstraints getUnfulfilledConstraints(
	String cipherSuite,
	Principal client,
	Principal server,
	InvocationConstraints constraints)
    {
	boolean supported = false;
	for (int i = 2; --i >= 0; ) {
	    boolean integrity = i == 0;
	    ConnectionContext context = ConnectionContext.getInstance(
		cipherSuite, client, server, integrity,
		false /* clientSide */, constraints);
	    if (context != null) {
		if (context.getIntegrityRequired()) {
		    return INTEGRITY_REQUIRED;
		} else if (context.getIntegrityPreferred()) {
		    return INTEGRITY_PREFERRED;
		} else {
		    supported = true;
		}
	    }
	}
	return supported ? InvocationConstraints.EMPTY : null;
    }

    /* -- Implement ServerEndpoint -- */

    final Endpoint enumerateListenEndpoints(ListenContext listenContext)
	throws IOException
    {
        String resolvedHost = LOCAL_HOST.check(this.serverHost, this);

        Endpoint result = createEndpoint(
            resolvedHost,
            checkCookie(listenContext.addListenEndpoint(listenEndpoint)));
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                       "enumerate listen endpoints for {0}\nreturns {1}",
                       new Object[] { this, result });
        }
        return result;
    }

    /** Creates a listen endpoint for this server endpoint. */
    ListenEndpoint createListenEndpoint() {
	return new SslListenEndpoint();
    }

    /**
     * Creates an endpoint for this server endpoint corresponding to the
     * specified server host and listen cookie.
     */
    Endpoint createEndpoint(String serverHost, SslListenCookie cookie) {
	return SslEndpoint.getInstance(
	    serverHost, cookie.getPort(), socketFactory);
    }

    /**
     * Checks that the argument is a valid listen cookie for this server
     * endpoint.
     */
    private SslListenCookie checkCookie(ListenCookie cookie) {
	if (!(cookie instanceof SslListenCookie)) {
	    throw new IllegalArgumentException(
		"Cookie must be of type SslListenCookie: " + cookie);
	}
	SslListenCookie sslListenCookie = ((SslListenCookie) cookie);
	ListenEndpoint cookieListenEndpoint
	    = sslListenCookie.getListenEndpoint();

	if (!listenEndpoint.equals(cookieListenEndpoint)) {
	    throw new IllegalArgumentException(
		"Cookie has wrong listen endpoint: found " +
		cookieListenEndpoint + ", expected " + listenEndpoint);
	}
	return sslListenCookie;
    }

    /**
     * Check for permission to listen on this endpoint, but only checking
     * socket permissions if checkSocket is true.
     */
    final void checkListenPermissions(boolean checkSocket) {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    if (checkSocket) {
		sm.checkListen(port);
	    }
	    if (listenPermissions != null) {
		for (int i = listenPermissions.length; --i >= 0; ) {
		    sm.checkPermission(listenPermissions[i]);
		}
	    }
	}
    }

    /** Implements ListenEndpoint */
    class SslListenEndpoint extends Utilities implements ListenEndpoint {

	/* inherit javadoc */
	public void checkPermissions() {
	    checkListenPermissions(true);
	}

	/* inherit javadoc */
	public ListenHandle listen(RequestDispatcher requestDispatcher)
	    throws IOException
	{
	    if (requestDispatcher == null) {
		throw new NullPointerException(
		    "Request dispatcher cannot be null");
	    }
	    checkCredentials();
	    ServerSocket serverSocket = serverSocketFactory != null
		? serverSocketFactory.createServerSocket(port)
		: new ServerSocket(port);
	    return createListenHandle(requestDispatcher, serverSocket);
	}

	/**
	 * Check that the subject has credentials for the principals specified
	 * when the server endpoint was created.
	 */
	private void checkCredentials() throws UnsupportedConstraintException {
	    if (serverSubject == null) {
		return;
	    }
	    checkListenPermissions(false);
	    Set principals = serverSubject.getPrincipals();
	    /* Keep track of progress; remove entry when check is done */
            boolean nullServerPrincipals = serverPrincipals == null;
	    Map progress = new HashMap(nullServerPrincipals ? 0 : serverPrincipals.size());
            if (!nullServerPrincipals){
                for (Iterator i = serverPrincipals.iterator(); i.hasNext(); ) {
                    X500Principal p = (X500Principal) i.next();
                    if (!principals.contains(p)) {
                        throw new UnsupportedConstraintException(
                            "Missing principal: " + p);
                    }
                    progress.put(p, X500Principal.class);
                }
            }
	    X500PrivateCredential[] privateCredentials =
		(X500PrivateCredential[]) AccessController.doPrivileged(
		    new SubjectCredentials.GetAllPrivateCredentialsAction(
			serverSubject));
	    List certPaths =
		SubjectCredentials.getCertificateChains(serverSubject);
	    if (certPaths != null) {
		for (int i = certPaths.size(); --i >= 0; ) {
		    CertPath chain = (CertPath) certPaths.get(i);
		    X509Certificate firstCert = firstX509Cert(chain);
		    X500Principal p = firstCert.getSubjectX500Principal();
		    if (progress.containsKey(p)) {
			try {
			    checkValidity(chain, null);
			} catch (CertificateException e) {
			    progress.put(p, e);
			    continue;
			}
			progress.put(p, CertPath.class);
			for (int j = privateCredentials.length; --j >= 0; ) {
			    X509Certificate cert =
				privateCredentials[j].getCertificate();
			    if (firstCert.equals(cert)) {
				progress.remove(p);
				break;
			    }
			}
		    }
		}
	    }
	    if (!progress.isEmpty()) {
		X500Principal p =
		    (X500Principal) progress.keySet().iterator().next();
		Object result = progress.get(p);
		if (result == X500Principal.class) {
		    throw new UnsupportedConstraintException(
			"Missing public credentials: " + p);
		} else if (result == CertPath.class) {
		    throw new UnsupportedConstraintException(
			"Missing private credentials: " + p);
		} else {
		    throw new UnsupportedConstraintException(
			"Problem with certificates: " + p + "\n" + result,
			(CertificateException) result);
		}
	    }
	}

	/**
	 * Creates a listen handle for the specified dispatcher and server
	 * socket.
	 */
	ListenHandle createListenHandle(RequestDispatcher requestDispatcher,
					ServerSocket serverSocket)
	    throws IOException
	{
	    return new SslListenHandle(requestDispatcher, serverSocket);
	}

	/** Returns a hash code value for this object. */
	public int hashCode() {
	    return getClass().hashCode()
		^ System.identityHashCode(serverSubject)
		^ (serverPrincipals == null ? 0 : serverPrincipals.hashCode())
		^ port
		^ (serverSocketFactory != null
		   ? serverSocketFactory.hashCode() : 0);
	}

	/**
	 * Two instances of this class are equal if they have the same actual
	 * class; have server subjects that compare equal using
	 * <code>==</code>; have server principals that are either both
	 * <code>null</code> or compare equal using <code>equals</code>; have
	 * the same port; and have server socket factories that are both null,
	 * or have the same actual class and are equal. Note that the server
	 * host and socket factory are ignored.
	 */
	public boolean equals(Object object) {
	    if (this == object) {
		return true;
	    } else if (object == null || getClass() != object.getClass()) {
		return false;
	    }
	    SslServerEndpointImpl other =
		((SslListenEndpoint) object).getImpl();
	    return serverSubject == other.serverSubject
		&& safeEquals(serverPrincipals, other.serverPrincipals)
		&& port == other.port
		&& Util.sameClassAndEquals(serverSocketFactory,
					   other.serverSocketFactory);
	}

	/**
	 * Returns the SslServerEndpointImpl associated with this listen
	 * endpoint.
	 */
	private SslServerEndpointImpl getImpl() {
	    return SslServerEndpointImpl.this;
	}
    }

    /** Implements ListenHandle */
    class SslListenHandle extends Utilities implements ListenHandle {

	/** The request handler */
	private final RequestDispatcher requestDispatcher;

	/** The server socket used to accept connections */
	final ServerSocket serverSocket;

	/** The security context at the time of the listen. */
	private final SecurityContext securityContext;

	/** Whether the listen handle has been closed. */
	private boolean closed = false;

	/** Set of connections created by this listen handle */
	private final Set connections = new HashSet();

	/** Used to throttle accept failures */
        private final Object failureLock = new Object();
	private long acceptFailureTime = 0;
	private int acceptFailureCount = 0;

	/** Creates a listen handle */
	SslListenHandle(RequestDispatcher requestDispatcher,
			ServerSocket serverSocket)
	    throws IOException
	{
	    this.requestDispatcher = requestDispatcher;
	    this.serverSocket = serverSocket;
	    securityContext = Security.getContext();
	    systemExecutor.execute(
		new Runnable() {
		    public void run() {
			acceptLoop();
		    }
		},
		toString());
	    logger.log(Level.FINE, "created {0}", this);
	}

	/** Handles new socket connections. */
	final void acceptLoop() {
	    while (true) {
		Socket socket = null;
		Throwable exception;
		SslServerConnection connection = null;
		try {
		    socket = serverSocket.accept();

		    /* Send data without delay */
		    try {
			socket.setTcpNoDelay(true);
		    } catch (SocketException e) {
		    }
		    /*
		     * Send periodic pings so we can tell if the connection
		     * dies.
		     */
		    try {
			socket.setKeepAlive(true);
		    } catch (SocketException e) {
		    }

		    connection = serverConnection(socket);
		    synchronized (this) {
			if (closed) {
			    try {
				connection.closeInternal(
				    false /* removeFromListener */);
			    } catch (IOException e) {
			    }
			    break;
			}
			connections.add(connection);
		    }
		    final SslServerConnection finalConnection = connection;
		    AccessController.doPrivileged(
			securityContext.wrap(
			    new PrivilegedAction() {
				public Object run() {
				    handleConnection(
					finalConnection, requestDispatcher);
				    return null;
				}
			    }),
			securityContext.getAccessControlContext());
		    continue;
		} catch (Exception e) {
		    exception = e;
		} catch (Error e) {
		    exception = e;
		}
		/* Problem occurred */
		boolean closedSync;
		synchronized (this) {
		    closedSync = closed;
		}
		if (!closedSync && logger.isLoggable(Level.INFO)) {
		    String msg = "handling connection {0} throws";
		    Object arg = connection;
		    if (connection == null) {
			msg = "accepting connection on {0} throws";
			arg = this;
		    }
		    logThrow(logger, Level.INFO,
			     SslListenHandle.class, "acceptLoop",
			     msg, new Object[] { arg }, exception);
		}
		if (socket != null) {
		    try {
			socket.close();
		    } catch (IOException e) {
		    }
		}
		if (closedSync) {
		    break;
		}
		boolean knownFailure =
		    (exception instanceof Exception ||
		     exception instanceof OutOfMemoryError ||
		     exception instanceof NoClassDefFoundError);
		if (!(knownFailure && continueAfterAcceptFailure(exception))) {
		    try {
			serverSocket.close();
		    } catch (IOException e) {
		    }
		    if (!knownFailure) {
			throw (Error) exception;
		    } else {
			return;
		    }
		}
	    }
	}

	/**
	 * Throttles the accept loop after ServerSocket.accept throws
	 * an exception, and decides whether to continue at all.  The
	 * current code is borrowed from the JRMP implementation; it
	 * always continues, but it delays the loop after bursts of
	 * failed accepts.
	 */
	private boolean continueAfterAcceptFailure(Throwable t) {
	    /*
	     * If we get a burst of NFAIL failures in NMSEC milliseconds,
	     * then wait for ten seconds.  This is to ensure that individual
	     * failures don't cause hiccups, but sustained failures don't
	     * hog the CPU in futile accept-fail-retry looping.
	     */
	    final int NFAIL = 10;
	    final int NMSEC = 5000;
	    long now = System.currentTimeMillis();
            boolean fail = false;
            synchronized (failureLock){
                if (acceptFailureTime == 0L ||
                    (now - acceptFailureTime) > NMSEC)
                {
                    // failure time is very old, or this is first failure
                    acceptFailureTime = now;
                    acceptFailureCount = 0;
                } else {
                    // failure window was started recently
                    acceptFailureCount++;
                    if (acceptFailureCount >= NFAIL) {
                        fail = true;
                    }
                }
            }
            if (fail) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignore) {
                    /* Why are we ignoring the interrupt and not 
                     * restoring the interrupted status?
                     */
                    Thread.currentThread().interrupt();
                }
                // no need to reset counter/timer
            }
	    
	    return true;
	}

	/** Returns a string representation of this object. */
	public String toString() {
	    return getClassName(this) + "[" +
		serverHost + ":" + getPort() + "]";
	}

	/** Returns a connection for the specified socket. */
	SslServerConnection serverConnection(Socket socket)
	    throws IOException
	{
	    return new SslServerConnection(this, socket);
	}

	/** Handles a newly accepted server connection. */
	void handleConnection(SslServerConnection connection,
			      RequestDispatcher requestDispatcher)
	{
	    serverConnectionManager.handleConnection(
		connection, requestDispatcher);
	}

	/** Returns the port on which this handle is listening. */
	private int getPort() {
	    return serverSocket.getLocalPort();
	}

	/* inherit javadoc */
	public synchronized void close() {
	    if (!closed) {
		logger.log(Level.FINE, "closing {0}", this);
		closed = true;
		try {
		    serverSocket.close();
		} catch (IOException e) {
		}
		for (Iterator i = connections.iterator(); i.hasNext(); ) {
		    SslServerConnection connection =
			(SslServerConnection) i.next();
		    try {
			/*
			 * Call closeInternal because close would call back to
			 * remove the connection and invalidate the iterator.
			 */
			connection.closeInternal(
			    false /* removeFromListener */);
		    } catch (IOException e) {
		    }
		    i.remove();
		}
	    }
	}

	/**
	 * Called when a connection is closed without a call to close on this
	 * listener.
	 */
	synchronized void noteConnectionClosed(
	    SslServerConnection connection)
	{
	    connections.remove(connection);
	}

	/* inherit javadoc */
	public ListenCookie getCookie() {
	    return new SslListenCookie(getPort());
	}
    }

    /** Implements ListenCookie */
    final class SslListenCookie implements ListenCookie {
	private final int port;

	SslListenCookie(int port) {
	    this.port = port;
	}

	/** Returns the port on which the associated handle is listening. */
	final int getPort() {
	    return port;
	}

	/**
	 * Returns the listen endpoint associated with this listen cookie.
	 */
	final ListenEndpoint getListenEndpoint() {
	    return listenEndpoint;
	}
    }

    /** Implements ServerConnection */
    class SslServerConnection extends Utilities implements ServerConnection {

	/** The listen handle that accepted this connection */
	private final SslListenHandle listenHandle;

	/** The JSSE socket used for communication */
	final SSLSocket sslSocket;

	/** The inbound request handle for this connection. */
	private final InboundRequestHandle requestHandle =
	    new InboundRequestHandle() { };

	/**
	 * The session for this connection's socket, or null if not retrieved
	 * yet.  Check that the current session matches to prevent new
	 * handshakes.
	 */
	private final SSLSession session;

	/**
	 * The client subject -- depends on session being set.  This instance
	 * is read-only. 
	 */
	private final Subject clientSubject;

	/** The client principal -- depends on session being set. */
	private final X500Principal clientPrincipal;

	/** The server principal -- depends on session being set. */
	private final X500Principal serverPrincipal;

	/**
	 * The authentication permission required for this connection, or null
	 * if the server is anonymous -- depends on session being set.
	 */
	private final AuthenticationPermission authPermission;

	/** The cipher suite -- depends on session being set. */
	private final String cipherSuite;
        
	/** True if the connection has been closed. */
	volatile boolean closed;

	/** Creates a server connection */
	SslServerConnection(SslListenHandle listenHandle, Socket socket)
	    throws IOException
	{
	    this.listenHandle = listenHandle;

	    sslSocket = (SSLSocket) getSSLSocketFactory().createSocket(
		socket, socket.getInetAddress().getHostName(),
		socket.getPort(), true /* autoClose */);
	    sslSocket.setEnabledCipherSuites(getSupportedCipherSuites());

	    /* Need to put in server mode before requesting client auth. */
	    sslSocket.setUseClientMode(false);
	    sslSocket.setWantClientAuth(true);
            try {
                session = sslSocket.getSession();
                sslSocket.setEnableSessionCreation(false);
                cipherSuite = session.getCipherSuite();
                if ("NULL".equals(getKeyExchangeAlgorithm(cipherSuite))) {
                    throw new SecurityException("Handshake failed");
                }
                clientSubject = getClientSubject(sslSocket);
                clientPrincipal = clientSubject != null
                    ? ((X500Principal)
                       clientSubject.getPrincipals().iterator().next())
                    : null;
                X509Certificate serverCert =
                    getAuthManager().getServerCertificate(session);
                serverPrincipal = serverCert != null
                    ? serverCert.getSubjectX500Principal() : null;
                if (serverPrincipal != null) {
                    authPermission = new AuthenticationPermission(
                        Collections.singleton(serverPrincipal),
                        (clientPrincipal != null
                         ? Collections.singleton(clientPrincipal) : null),
                        "accept");
                } else {
                    authPermission = null;
                }
            } catch (SecurityException e){
                throw new IOException("Unable to create session", e);
            }
	    logger.log(Level.FINE, "created {0}", this);
	}

	/* inherit javadoc */
	public String toString() {
	    String sessionString;
	    synchronized (this) {
		sessionString = session == null ? "" : session + ", ";
	    }
	    return getClassName(this) + "[" +
		sessionString + 
		serverHost + ":" + sslSocket.getLocalPort() + "<=" +
		sslSocket.getInetAddress().getHostName() + ":" +
		sslSocket.getPort() +
		"]";
	}

	/* -- Implement ServerConnection -- */

	/* inherit javadoc */
	public InputStream getInputStream() throws IOException {
	    return sslSocket.getInputStream();
	}

	/* inherit javadoc */
	public OutputStream getOutputStream() throws IOException {
	    return sslSocket.getOutputStream();
	}

	/* inherit javadoc */
	public SocketChannel getChannel() {
	    return null;
	}

	/* inherit javadoc */
	public InboundRequestHandle processRequestData(InputStream in,
						       OutputStream out)
	{
	    if (in == null || out == null) {
		throw new NullPointerException("Arguments cannot be null");
	    }
	    SecurityException exception;
	    try {
		long now = System.currentTimeMillis();
		decacheSession();
		long create = session.getCreationTime();
		long expiration = create + maxServerSessionDuration;
		/* Check for rollover */
		if (expiration < create) {
		    expiration = Long.MAX_VALUE;
		}
		if (expiration < now) {
		    /*
		     * The session has expired.  Invalidate the session so that
		     * it is not reused, and throw an exception.
		     */
		    session.invalidate();
		    throw new SecurityException("Session has expired");
		}
		if (serverPrincipal != null) {
		    getAuthManager().checkCredentials(session, clientSubject);
		}
		return requestHandle;
	    } catch (SecurityException e) {
		exception = e;
	    } catch (GeneralSecurityException e) {
		exception = new SecurityException(e.getMessage());
		exception.initCause(e);
	    }
	    try {
		out.close();
	    } catch (IOException e2) {
	    }
	    if (logger.isLoggable(Levels.FAILED)) {
		logThrow(logger, Levels.FAILED,
			 SslServerConnection.class, "processRequestData",
			 "process request data for session {0}\nclient {1}\n" +
			 "throws",
			 new Object[] { session, subjectString(clientSubject) },
			 exception);
	    }
	    throw exception;
	}

	/**
	 * Make sure the cached session is up to date, and set session-related
	 * fields if needed.
	 */
	private void decacheSession() {
            SSLSession socketSession = sslSocket.getSession();
            if (session == socketSession) {
                return;
            } else if ( !session.isValid()){
                throw new SecurityException("Session invalid");
            } else {
                /*
                 * We disable session creation as soon as we notice the
                 * first session, but it is possible that a second
                 * handshake could have started by then, so check that we
                 * have the same session.  -tjb[31.Jan.2003]
                 */
                throw new SecurityException(
                    "New handshake occurred on socket");
            }
	}

	/**
	 * Returns the read-only <code>Subject</code> associated with the
	 * client host connected to the other end of the connection on the
	 * specified <code>SSLSocket</code>.  Returns null if the client is
	 * anonymous.
	 */
	private Subject getClientSubject(SSLSocket socket) {
	    SSLSession session = socket.getSession();
	    try {
		Certificate[] certificateChain = session.getPeerCertificates();
		if (certificateChain != null
		    && certificateChain.length > 0
		    && certificateChain[0] instanceof X509Certificate)
		{
		    X509Certificate cert =
			(X509Certificate) certificateChain[0];
		    return new Subject(
			true,
			Collections.singleton(cert.getSubjectX500Principal()),
			Collections.singleton(
			    getCertFactory().generateCertPath(
				Arrays.asList(certificateChain))),
			Collections.EMPTY_SET);
		}
	    } catch (SSLPeerUnverifiedException e) {
	    } catch (CertificateException e) {
		logger.log(Levels.HANDLED,
			   "get client subject caught exception", e);
	    }
	    return null;
	}

	/* inherit javadoc */
	public void checkPermissions(InboundRequestHandle requestHandle) {
	    check(requestHandle);
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		try {
		    sm.checkAccept(sslSocket.getInetAddress().getHostAddress(),
				   sslSocket.getPort());
		    if (authPermission != null) {
			sm.checkPermission(authPermission);
		    }
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			logThrow(logger, Levels.FAILED,
				 SslServerConnection.class, "checkPermissions",
				 "check permissions for {0} throws",
				 new Object[] { this },
				 e);
		    }
		    throw e;
		}
	    }
	}

	/**
	 * Checks that the argument is the request handle for this connection.
	 */
	private void check(InboundRequestHandle requestHandle) {
	    if (requestHandle == null) {
		throw new NullPointerException("Request handle cannot be null");
	    } else if (requestHandle != this.requestHandle) {
		throw new IllegalArgumentException(
		    "Wrong request handle: found " + requestHandle +
		    ", expected " + this.requestHandle);
	    }
	}

	/* inherit javadoc */
	public InvocationConstraints checkConstraints(
	    InboundRequestHandle requestHandle,
	    InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    check(requestHandle);
	    if (constraints == null) {
		throw new NullPointerException("Constraints cannot be null");
	    }
	    InvocationConstraints result = getUnfulfilledConstraints(
		cipherSuite, clientPrincipal, serverPrincipal, constraints);
	    if (result == null) {
		UnsupportedConstraintException uce = 
		    new UnsupportedConstraintException(
			"Constraints are not supported: " + constraints);
		if (logger.isLoggable(Levels.FAILED)) {
		    logThrow(logger, Levels.FAILED,
			     SslServerConnection.class, "checkConstraints",
			     "check constraints for {0}\nwith {1}\n" +
			     "throws",
			     new Object[] {
				 SslServerConnection.this, constraints
			     },
			     uce);
		}
		throw uce;
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "check constraints for {0}\nwith {1}\nreturns {2}",
			   new Object[] {
			       SslServerConnection.this, constraints,
			       result
			   });
	    }
	    return result;
	}

	/* inherit javadoc */
	public void populateContext(InboundRequestHandle requestHandle,
				    Collection context)
	{
	    check(requestHandle);
	    Util.populateContext(context, sslSocket.getInetAddress());
	    Util.populateContext(context, clientSubject);
	}

	/* inherit javadoc */
	public void close() throws IOException {
	    closeInternal(true);
	}

	/**
	 * Like close, but does not call noteConnectionClosed unless
	 * removeFromListener is true.
	 */
	void closeInternal(boolean removeFromListener)
	    throws IOException
	{
	    synchronized (this) {
		if (closed) {
		    return;
		}
		logger.log(Level.FINE, "closing {0}", this);
		closed = true;
		sslSocket.close();
	    }
	    if (removeFromListener) {
		listenHandle.noteConnectionClosed(this);
	    }
	}
    }
}
