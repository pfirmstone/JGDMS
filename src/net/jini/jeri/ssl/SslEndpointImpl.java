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

import org.apache.river.jeri.internal.connection.BasicConnManagerFactory;
import org.apache.river.jeri.internal.connection.ConnManager;
import org.apache.river.jeri.internal.connection.ConnManagerFactory;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.security.AuthenticationPermission;

/**
 * Provides the implementation of SslEndpoint so that the implementation can be
 * inherited by HttpsEndpoint without revealing the inheritance in the public
 * API.
 *
 * 
 */
class SslEndpointImpl extends Utilities implements ConnectionEndpoint {

    /* -- Fields -- */

    /** Client logger */
    static final Logger logger = CLIENT_LOGGER;

    /**
     * Weak key map that maps connection endpoints to weak references to the
     * associated ConnManager.  The weak values insure that the keys will not
     * be strongly held, since the connection manager has a strong reference to
     * the associated connection endpoint.
     */
    private static final Map connectionMgrs = new WeakHashMap();

    /** The size of the connection context cache. */
    private static final int CACHE_SIZE = 4;
    
    /** The factory for default connection managers. */
    private static final ConnManagerFactory connectionManagerFactory =
	new BasicConnManagerFactory();

    /** The associated endpoint. */
    final Endpoint endpoint;

    /** The name of the server host. */
    final String serverHost;

    /** The server port. */
    final int port;

    /** The factory for creating sockets, or null to use default sockets. */
    final SocketFactory socketFactory;

    /**
     * Whether to disable calling Socket.connect -- set when used by discovery
     * providers.
     */
    volatile boolean disableSocketConnect;

    /** A cache for recently computed connection contexts. All access synchronized*/
    private final ConnectionContextCache[] connectionContextCache =
	new ConnectionContextCache[CACHE_SIZE];

    /** Next index for a connectionContextCache miss; counts down, not up. 
     *  Access synchronized on connectionContextCache
     */
    private int cacheNext;

    /** The connection manager for this endpoint or null if not yet set. 
     *  All access to reference synchronized on connectionMgrs */
    private ConnManager connectionManager;

    /* -- Constructors -- */

    /** Creates an instance of this class. */
    SslEndpointImpl(Endpoint endpoint,
		    String serverHost,
		    int port,
		    SocketFactory socketFactory)
    {
	this.endpoint = endpoint;
	this.serverHost = serverHost;
	this.port = port;
	this.socketFactory = socketFactory;
    }

    /* -- Methods -- */

    /** Returns a string representation of this object. */
    public String toString() {
	return getClassName(this) + fieldsToString();
    }

    /** Returns a string representation of the fields of this object. */
    final String fieldsToString() {
	return "[" + serverHost + ":" + port + 
	    (socketFactory != null ? ", " + socketFactory : "") + "]";
    }

    /* -- Implement Endpoint -- */

    /** Returns a hash code value for this object. */
    public int hashCode() {
	return getClass().hashCode()
	    ^ serverHost.hashCode()
	    ^ port
	    ^ (socketFactory != null ? socketFactory.hashCode() : 0);
    }

    /**
     * Two instances of this class are equal if they have the same actual
     * class; have the same values for server host and port; and have socket
     * factories that are either both null, or have the same actual class and
     * are equal.
     */
    public boolean equals(Object object) {
	if (object == null || object.getClass() != getClass()) {
	    return false;
	}
	SslEndpointImpl other = (SslEndpointImpl) object;
	return serverHost.equals(other.serverHost)
	    && port == other.port
	    && Util.sameClassAndEquals(socketFactory, other.socketFactory);
    }

    /** Implements Endpoint.newRequest */
    final OutboundRequestIterator newRequest(
	InvocationConstraints constraints)
    {
	if (constraints == null) {
	    throw new NullPointerException("Constraints cannot be null");
	}
	try {
	    return newRequest(getCallContext(constraints));
	} catch (UnsupportedConstraintException e) {
	    return new ExceptionOutboundRequestIterator(e);
	} catch (SecurityException e) {
	    return new ExceptionOutboundRequestIterator(e);
	}
    }

    /**
     * @param connectionManager the connectionManager to set
     */
    void setConnectionManager(ConnManager connectionManager) {
        synchronized (connectionMgrs){
            this.connectionManager = connectionManager;
            connectionMgrs.put(
                            this, new WeakReference(connectionManager));
        }
    }

    /**
     * An outbound request iterator that throws an IOException or a
     * SecurityException.
     */
    private static final class ExceptionOutboundRequestIterator
	implements OutboundRequestIterator
    {
	private final Exception exception;
	private boolean done = false;

	ExceptionOutboundRequestIterator(Exception exception) {
	    this.exception = exception;
	}

	public synchronized boolean hasNext() {
	    return !done;
	}

	public synchronized OutboundRequest next() throws IOException {
	    if (done) {
		throw new NoSuchElementException();
	    }
	    done = true;
	    if (exception instanceof SecurityException) {
		throw (SecurityException) exception;
	    } else {
		throw (IOException) exception;
	    }
	}
    }

    /** Implements Endpoint.newRequest when the constraints are supported. */
    OutboundRequestIterator newRequest(CallContext callContext) {
	return getConnectionManager().newRequest(callContext);
    }

    /** Returns the connection manager for this endpoint. */
    private ConnManager getConnectionManager() {
	synchronized (connectionMgrs) {
	    if (connectionManager == null) {
		Reference ref = (Reference) connectionMgrs.get(this);
		connectionManager =
		    (ref != null) ? (ConnManager) ref.get() : null;
		if (connectionManager == null) {
		    connectionManager = connectionManagerFactory.create(this);
		    connectionMgrs.put(
			this, new WeakReference(connectionManager));
		}
	    }
	    return connectionManager;
	}
    }

    /**
     * Returns a context for making a remote call with the specified
     * constraints and the current subject. This method does not perform
     * communication with the remote server.
     *
     * Throws a SecurityException if lack of authentication permissions
     * needed for requirements prevent the use of all contexts.
     *
     * Throws UnsupportedConstraintException if some requirements cannot
     * be satisfied.
     *
     * Returns a CallContext if throwing an exception or returning null
     * would reveal information about the current subject that the
     * caller does not have permission to know.
     */
    private CallContext getCallContext(InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	final AccessControlContext acc = AccessController.getContext();
	Subject clientSubject = (Subject) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return Subject.getSubject(acc);
		}
	    });
	Set clientPrincipals = getClientPrincipals(constraints.requirements());
	boolean requiredClient = clientPrincipals != null;
	boolean constrainedServer = getServerPrincipals(constraints) != null;
	Boolean getSubject = null;
	if (!requiredClient) {
	    /* Try using principals from Subject instead */
	    if (clientSubject == null) {
		clientPrincipals = Collections.EMPTY_SET;
	    } else {
		/*
		 * XXX: Work around BugID 4892841, Subject.getPrincipals(Class)
		 * not thread-safe against changes to principals.
		 * -tjb[18.Jul.2003]
                 * 
                 * This was fixed in Java 1.5 which is now our minimum
                 * supported version.
		 */
		synchronized (clientSubject.getPrincipals()) {
		    clientPrincipals =
			clientSubject.getPrincipals(X500Principal.class);
		}
	    }
	    if (clientPrincipals.isEmpty()) {
		getSubject = getSubjectPermitted();
		if (getSubject == Boolean.FALSE) {
		    /* Don't reveal that the client Subject has no principals.
		     * Provide a dummy Principal (no credentials) which cannot
		     * be authenticated in order to follow the same code path
		     * as if there were a Subject principal.
		     */
		    clientPrincipals =
			Collections.singleton(UNKNOWN_PRINCIPAL);
		}
	    }
	}

	/* Compute a list of ConnectionContexts between principals (as above):
	 * (a) principals named in client constraints, if any; otherwise
	 * (b) principals named in the client Subject, if any; otherwise
	 * (c) a dummy principal or no principal
	 * and the principals named in server constraints (if any). These
	 * contexts will satisfy most constraints.
	 */
	List contexts = new CopyOnRemoveList(
	    getConnectionContexts(constraints, clientPrincipals));
	if (constrainedServer) {
	    /* Server principals were named in constraints.  Remove from the
	     * context list any ConnectionContexts for which there is no
	     * permission to authenticate the context's client principal with
	     * the context's server principal.
	     */
	    try {
		contexts = checkAuthenticationPermissions(contexts);
	    } catch (SecurityException e) {
		/* This SecurityException indicates that there are no
		 * remaining valid ConnectionContexts, either because there
		 * are no client constraint principals and no client subject
		 * principals, or because none of these principals was given
		 * permission to authenticate with any server constraint
		 * principal.
		 *
		 * If there were no client constraint principals, then
		 * throwing an exception here would reveal that at least one
		 * client subject principal existed and did not have
		 * authentication permission.
		 *
		 * If there were client constraint principals, or if the
		 * caller could determine Subject principals for itself, then
		 * pass on the SecurityException.
		 */
		if (!requiredClient && getSubject == null) {
		    getSubject = getSubjectPermitted();
		}
		if (requiredClient || getSubject == Boolean.TRUE) {
		    if (logger.isLoggable(Levels.FAILED)) {
			logThrow(
			    logger, Levels.FAILED,
			    SslEndpointImpl.class, "getCallContext",
			    "new request for {0}\nwith {1}\nand {2}\nthrows",
			    new Object[] { endpoint, constraints,
					   subjectString(clientSubject) },
			    e);
		    }
		    throw e;
		} else {
		    /* Don't reveal that the client Subject has no principals.
		     * Provide a dummy Principal (no credentials) which cannot
		     * be authenticated in order to follow the same code path
		     * as if there were a Subject principal.
		     */
		    CallContext result = createCallContext(
			getConnectionContexts(
			    constraints,
			    Collections.singleton(UNKNOWN_PRINCIPAL)),
			null);
		    if (logger.isLoggable(Levels.FAILED)) {
			logThrow(
			    logger, Levels.FAILED,
			    SslEndpointImpl.class, "getCallContext",
			    "new request for {0}\nwith {1}\nand {2}\n" +
			    "will fail but cannot throw " +
			    "because caller has no subject access\n" +
			    "returns {3}\n" +
			    "caught exception",
			    new Object[] { endpoint, constraints,
					   subjectString(clientSubject),
					   result },
			    e);
		    }
		    return result;
		}
	    }
	}
	UnsupportedConstraintException unsupported = null;
	if (contexts.isEmpty()) {
	    unsupported = new UnsupportedConstraintException(
		"Constraints not supported: " + constraints);
	} else {
	    boolean checkSubject;
	    if (constrainedServer) {
		checkSubject = true;
	    } else {
		if (getSubject == null) {
		    getSubject = getSubjectPermitted();
		}
		checkSubject = (getSubject == Boolean.TRUE);
	    }
	    if (checkSubject) {
		/* Check subject if caller has any access */
		try {
		    contexts = checkSubject(contexts, clientSubject,
					    constrainedServer, constraints);
		} catch (UnsupportedConstraintException e) {
		    unsupported = e;
		}
	    }
	}
	if (unsupported != null) {
	    if (logger.isLoggable(Levels.FAILED)) {
		logThrow(logger, Levels.FAILED,
			 SslEndpointImpl.class, "getCallContext",
			 "new request for {0}\nwith {1}\nand {2}\nthrows",
			 new Object[] { endpoint, constraints,
					subjectString(clientSubject) },
			 unsupported);
	    }
	    throw unsupported;
	} else {
	    CallContext result = createCallContext(contexts, clientSubject);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "new request for {0}\nwith {1}\nand {2}\n" +
			   "returns {3}",
			   new Object[] {
			       endpoint, constraints,
			       subjectString(clientSubject), result
			   });
	    }
	    return result;
	}
    }

    /** Convert connection contexts to a call context */
    private CallContext createCallContext(List contexts,
					  Subject clientSubject)
    {
	boolean clientAuthRequired = true;
	boolean clientAuthPermitted = false;
	Set clientPrincipals = null;
	Set serverPrincipals = null;
	List suites = new ArrayList();
	boolean integrityRequired = false;
	boolean integrityPreferred = false;
	long connectionTimeout = -1;
	int max = contexts.size();
	for (int i = 0; i < max; i++) {
	    ConnectionContext context = (ConnectionContext) contexts.get(i);
	    if (context.client == null) {
		clientAuthRequired = false;
	    } else {
		clientAuthPermitted = true;
		if (clientPrincipals == null) {
		    clientPrincipals = new HashSet();
		}
		/*
		 * Don't include the unknown principal, but still should record
		 * that principals were specified by making clientPrincipals
		 * non-null.
		 */
		if (context.client != UNKNOWN_PRINCIPAL) {
		    clientPrincipals.add(context.client);
		}
	    }
	    if (context.server != null &&
		context.server != UNKNOWN_PRINCIPAL)
	    {
		if (serverPrincipals == null) {
		    serverPrincipals = new HashSet();
		}
		serverPrincipals.add(context.server);
	    }
	    if (!suites.contains(context.cipherSuite)) {
		suites.add(context.cipherSuite);
	    }
	    if (context.getIntegrityRequired()) {
		integrityRequired = true;
	    } else if (context.getIntegrityPreferred()) {
		integrityPreferred = true;
	    }
	    if (context.getConnectionTime() != -1 &&
		(connectionTimeout == -1 ||
		 connectionTimeout > context.getConnectionTime()))
	    {
		connectionTimeout = context.getConnectionTime();
	    }
	}
	return new CallContext(
	    endpoint, this,
	    clientAuthPermitted ? clientSubject : null,
	    clientAuthRequired, clientPrincipals, serverPrincipals, suites,
	    integrityRequired, integrityPreferred, connectionTimeout);
    }

    /**
     * Returns a list of the contexts which are supported by principals and
     * credentials in the Subject.  Throws an UnsupportedConstraintException if
     * none of the contexts are supported, otherwise returns a non-empty list.
     */

    private static List checkSubject(List contexts,
				     Subject clientSubject,
				     boolean constrainedServer,
				     InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	Map publicCreds = getPublicCredentials(clientSubject);
	X500PrivateCredential[] privateCreds =
	    (clientSubject != null && constrainedServer)
	    ? (X500PrivateCredential[]) AccessController.doPrivileged(
		new SubjectCredentials.GetAllPrivateCredentialsAction(
		    clientSubject))
	    : new X500PrivateCredential[0];
	Set missingPublic = new HashSet();
	Set missingPrivate = new HashSet();
	/* Check principals and credentials */
      top:
	for (int i = contexts.size(); --i >= 0; ) {
	    ConnectionContext context = (ConnectionContext) contexts.get(i);
	    if (context.client == null) {
		continue;			/* Anonymous client OK */
	    }
	    Collection certs = (Collection) publicCreds.get(context.client);
	    if (certs == null) {
		logger.log(Levels.HANDLED,
			   "missing principal or public credentials: {0}",
			   context.client);
		contexts.remove(i);
		missingPublic.add(context.client);
	    } else if (constrainedServer) {
		/*
		 * Checked authentication permissions, so OK to check private
		 * credentials.
		 */
		for (int j = privateCreds.length; --j >= 0; ) {
		    X509Certificate cert = privateCreds[j].getCertificate();
		    if (cert != null) {		/* null if destroyed */
			if (certs.contains(cert)) {
			    continue top;	/* Private credentials OK */
			}
		    }
		}
		logger.log(Levels.HANDLED, "missing private credentials: {0}",
			   context.client);
		contexts.remove(i);
		missingPrivate.add(context.client);
	    }
	}
	if (!contexts.isEmpty()) {
	    return contexts;
	} else {
	    throw new UnsupportedConstraintException(
		"Constraints not supported: " + constraints + ";" +
		(missingPublic.isEmpty() ? ""
		 : ("\nmissing principals or public credentials: " +
		    missingPublic)) +
		(missingPrivate.isEmpty() ? ""
		 : "\nmissing private credentials: " + missingPrivate));
	}
    }

    /**
     * Checks if the caller has permission to get the current subject,
     * returning Boolean.TRUE or FALSE.
     */
    private static Boolean getSubjectPermitted() {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    try {
		sm.checkPermission(GET_SUBJECT_PERMISSION);
	    } catch (SecurityException e) {
		return Boolean.FALSE;
	    }
	}
	return Boolean.TRUE;
    }

    /**
     * Removes the contexts for which the client does not have authentication
     * permission.  Throws SecurityException if lack of permission prevents any
     * contexts from being used.
     */
    private static List checkAuthenticationPermissions(List contexts) {
	if (contexts.isEmpty()) {
	    return contexts;
	}
	SecurityManager sm = System.getSecurityManager();
	if (sm == null) {
	    return contexts;
	}
	Map perms = new HashMap();
	Set exceptions = new HashSet();
	for (int i = contexts.size(); -- i >= 0; ) {
	    ConnectionContext context = (ConnectionContext) contexts.get(i);
	    if (context.client == null) {
		/* Client anonymous -- OK */
		continue;
	    } else if (context.server == UNKNOWN_PRINCIPAL) {
		/* Server not known -- can't check any permissions */
		break;
	    }
	    AuthenticationPermission p = new AuthenticationPermission(
		Collections.singleton(context.client),
		Collections.singleton(context.server),
		"connect");
	    Object value = perms.get(p);
	    if (Boolean.FALSE.equals(value)) {
		contexts.remove(i);
	    } else if (!Boolean.TRUE.equals(value)) {
		try {
		    sm.checkPermission(p);
		    perms.put(p, Boolean.TRUE);
		} catch (SecurityException e) {
		    logger.log(
			Levels.HANDLED,
			"check authentication permission caught exception",
			e);
		    perms.put(p, Boolean.FALSE);
		    exceptions.add(e);
		    contexts.remove(i);
		}
	    }
	}
	if (!contexts.isEmpty()) {
	    return contexts;
	} else if (exceptions.size() == 1) {
	    throw (SecurityException) exceptions.iterator().next();
	} else {
	    throw new SecurityException(exceptions.toString());
	}
    }

    /**
     * Returns a map that maps each principal in the subject to a set of the
     * associated X.500 public credentials.
     */
    private static Map getPublicCredentials(Subject subject) {
	Map publicCreds = new HashMap();
	List certPaths = SubjectCredentials.getCertificateChains(subject);
	if (certPaths != null) {
	    for (int i = certPaths.size(); --i >= 0; ) {
		CertPath chain = (CertPath) certPaths.get(i);
		X509Certificate cert = SubjectCredentials.firstX509Cert(chain);
		X500Principal p =
		    SubjectCredentials.getPrincipal(subject, cert);
		if (p != null) {
		    Collection certs = (Collection) publicCreds.get(p);
		    if (certs == null) {
			certs = new ArrayList(1);
			publicCreds.put(p, certs);
		    }
		    certs.add(cert);
		}
	    }
	}
	return publicCreds;
    }

    /**
     * A List that supports removing items by making a copy of the underlying
     * list.
     */
    private static final class CopyOnRemoveList extends AbstractList {
	private List list;
	private boolean modified;

	CopyOnRemoveList(List list) {
	    this.list = list;
	}

	public Object get(int index) {
	    return list.get(index);
	}

	public int size() {
	    return list.size();
	}

	public Object remove(int index) {
	    if (!modified) {
		list = new ArrayList(list);
		modified = true;
	    }
	    return list.remove(index);
	}
    }

    /**
     * Defines a structure to cache a ConnectionContexts for specific
     * constraints and client principals.
     */
    private static final class ConnectionContextCache {
	final InvocationConstraints constraints;
	final Set clientPrincipals;
	final List connectionContexts;

	ConnectionContextCache(InvocationConstraints constraints,
			       Set clientPrincipals,
			       List connectionContexts)
	{
	    this.constraints = constraints;
	    this.clientPrincipals = clientPrincipals;
	    this.connectionContexts = connectionContexts;
	}
    }

    /**
     * Gets an unmodifiable list of the ConnectionContexts for the
     * specified constraints and client principals.
     */
    private List getConnectionContexts(InvocationConstraints constraints,
				       Set clientPrincipals)
    {
	synchronized (connectionContextCache) {
	    for (int i = CACHE_SIZE; --i >= 0; ) {
		ConnectionContextCache cache = connectionContextCache[i];
		if (cache != null &&
		    cache.constraints.equals(constraints) &&
		    cache.clientPrincipals.equals(clientPrincipals))
		{
		    logger.log(Level.FINEST, "used connection cache");
		    return cache.connectionContexts;
		}
	    }
	}
	Set serverPrincipals = getServerPrincipals(constraints);
	if (serverPrincipals == null) {
	    serverPrincipals = Collections.singleton(UNKNOWN_PRINCIPAL);
	}
	List contexts = Collections.unmodifiableList(
	    computeConnectionContexts(
		getSupportedCipherSuites(), clientPrincipals, serverPrincipals,
		constraints));
	synchronized (connectionContextCache) {
	    connectionContextCache[cacheNext] =
		new ConnectionContextCache(
		    constraints, clientPrincipals, contexts);
	    if (cacheNext == 0) {
		cacheNext = CACHE_SIZE;
	    }
	    cacheNext--;
	}
	return contexts;
    }

    /** Used for sorting ConnectionContexts by preferences and suite order. */
    private static final class ComparableConnectionContext
	implements Comparable
    {
	final ConnectionContext context;
	private final int suiteIndex;

	ComparableConnectionContext(ConnectionContext context,
				    int suiteIndex)
	{
	    this.context = context;
	    this.suiteIndex = suiteIndex;
	}

	public int compareTo(Object object) {
	    ComparableConnectionContext other =
		(ComparableConnectionContext) object;
	    /* Lower value for more preferences */
	    int result =
		other.context.getPreferences() - context.getPreferences();
	    if (result == 0) {
		/* Lower value for lower index */
		result = suiteIndex - other.suiteIndex;
	    }
	    return result;
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer("ComparableConnectionContext[");
	    context.fieldsToString(sb);
	    sb.append(", index: ").append(suiteIndex);
	    sb.append("]");
	    return sb.toString();
	}
    }

    /**
     * Computes a list of ConnectionContexts for the specified set of
     * suites, client and server principals, and constraints, sorted by
     * preferences and suite order.
     */
    private static List computeConnectionContexts(
	String[] suites,
	Set clients,
	Set servers,
	InvocationConstraints constraints)
    {
	List result = new ArrayList(
	    suites.length * (clients.size() + 1) * (servers.size() + 1));
	for (int suiteIndex = suites.length; --suiteIndex >= 0; ) {
	    String suite = suites[suiteIndex];
	    Iterator cIter = clients.iterator();
	    Principal client;
	    do {
		if (cIter.hasNext()) {
		    client = (Principal) cIter.next();
		    assert client != null;
		} else {
		    client = null;
		}
		Iterator sIter = servers.iterator();
		Principal server;
		do {
		    if (sIter.hasNext()) {
			server = (Principal) sIter.next();
			assert server != null;
		    } else {
			server = null;
		    }
		    for (int i = 2; --i >= 0; ) {
			boolean integrity = i == 0;
			ConnectionContext context =
			    ConnectionContext.getInstance(
				suite, client, server, integrity,
				true /* clientSide */, constraints);
			if (context != null) {
			    result.add(
				new ComparableConnectionContext(
				    context, suiteIndex));
			}
		    }
		} while (server != null);
	    } while (client != null);
	}
	Collections.sort(result);
	logger.log(Level.FINEST, "compute connection contexts produces {0}",
		   result);
	for (int i = result.size(); --i >= 0; ) {
	    ComparableConnectionContext ccc =
		(ComparableConnectionContext) result.get(i);
	    result.set(i, ccc.context);
	}
	return result;
    }

    /* -- Implement ConnectionEndpoint -- */

    /** Creates a new connection. */
    public Connection connect(OutboundRequestHandle handle) throws IOException {
	SslConnection connection = new SslConnection(
	    CallContext.coerce(handle, endpoint), serverHost, port,
	    socketFactory);
	connection.establishCallContext();
	return connection;
    }

    /** Chooses a connection from existing connections. */
    public Connection connect(OutboundRequestHandle handle,
			      Collection active,
			      Collection idle)
    {
	CallContext context = CallContext.coerce(handle, endpoint);
	if (active == null || idle == null) {
	    throw new NullPointerException("Arguments cannot be null");
	}
	Connection result = null;
	/*
	 * Choose the first connection with an appropriate subject, an active
	 * suite that is one of the requested suites, and one for which all the
	 * requested suites better than the active one were also better for its
	 * call context.  That insures that we've gotten the best connection we
	 * can, assuming a new handshake were to make similar decisions.
	 */
	boolean checkedResolvePermission = false;
	for (Iterator iter = new ConnectionsIterator(endpoint, active, idle);
	     iter.hasNext(); )
	{
	    SslConnection connection = (SslConnection) iter.next();
	    if (connection.useFor(context)) {
		String phost = connection.getProxyHost();
		boolean usingProxy = (phost.length() != 0);

		if (usingProxy) {
		    SecurityManager sm = System.getSecurityManager();
		    if (sm != null) {
			try {
			    sm.checkConnect(serverHost, port);
			} catch (SecurityException e) {
			    if (logger.isLoggable(Levels.FAILED)) {
				logThrow(logger, Levels.FAILED,
					 SslEndpointImpl.class, "connect",
					 "choose connection for {0}\nthrows",
					 new Object[] { this }, e);
			    }
			    throw e;
			}
		    }
		} else {
		    if (!checkedResolvePermission) {
			try {
			    checkResolvePermission();
			} catch (SecurityException e) {
			    if (logger.isLoggable(Levels.FAILED)) {
				LogUtil.logThrow(logger, Levels.FAILED,
					   SslEndpointImpl.class, "connect",
					   "exception resolving host {0}",
					   new Object[] { serverHost }, e);
			    }
			    throw e;
			}
			checkedResolvePermission = true;
		    }
		    try {
			if (!connection.checkConnectPermission()) {
			    continue;
			}
		    } catch (SecurityException e) {
			if (logger.isLoggable(Levels.HANDLED)) {
			    LogUtil.logThrow(logger, Levels.HANDLED,
				SslEndpointImpl.class, "nextRequest",
				"access to reuse connection {0} denied",
				new Object[] { connection }, e);
			}
			continue;
		    }
		}
		result = connection;
		break;
	    }
	}
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		       "choose connection for {0}\nwith active {1}\n" +
		       "and idle {2}\nreturns {3}",
		       new Object[] { handle, active, idle, result });
	}
	return result;
    }

    private void checkResolvePermission() {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkConnect(serverHost, -1);
	}
    }

    /**
     * Defines an iterator over active and idle connections which performs
     * error checking on connections.
     */
    private static final class ConnectionsIterator implements Iterator {
	private final Endpoint endpoint;
	private Collection active;	/* Set to null when exhausted */
	private final Collection idle;
	private Iterator iter;

	ConnectionsIterator(Endpoint endpoint,
			    Collection active,
			    Collection idle)
	{
	    this.endpoint = endpoint;
	    this.active = active;
	    this.idle = idle;
	    iter = active.iterator();
	    if (!iter.hasNext()) {
		this.active = null;
		iter = idle.iterator();
	    }
	}

	public boolean hasNext() {
	    return iter.hasNext();
	}

	public Object next() {
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }
	    Object next = iter.next();
	    if (next == null) {
		throw new NullPointerException("Connection cannot be null");
	    } else if (!(next instanceof SslConnection)) {
		throw new IllegalArgumentException(
		    "Connection must be of type SslConnection: " + next);
	    }
	    SslConnection result = (SslConnection) next;
	    if (!endpoint.equals(result.callContext.endpoint)) {
		throw new IllegalArgumentException(
		    "Connection has wrong endpoint: found " +
		    result.callContext.endpoint + ", expected " + endpoint);
	    }
	    if (!iter.hasNext() && active != null) {
		active = null;
		iter = idle.iterator();
	    }
	    return result;
	}

	public void remove() {
	    throw new UnsupportedOperationException();
	}
    }
}
