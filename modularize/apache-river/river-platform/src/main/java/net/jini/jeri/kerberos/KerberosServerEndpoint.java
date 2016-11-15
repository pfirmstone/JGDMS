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
package net.jini.jeri.kerberos;

import org.apache.river.action.GetIntegerAction;
import org.apache.river.jeri.internal.connection.BasicServerConnManager;
import org.apache.river.jeri.internal.connection.ServerConnManager;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.security.auth.AuthPermission;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.jeri.kerberos.KerberosUtil.Config;
import net.jini.jeri.kerberos.KerberosUtil.ConfigIter;
import net.jini.security.Security;
import net.jini.security.SecurityContext;
import org.apache.river.jeri.internal.runtime.LocalHost;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;

/**
 * A {@link ServerEndpoint} implementation that uses Kerberos as the
 * underlying network security protocol to support security related
 * invocation constraints for remote requests.  Instances of this
 * class are referred to as the server endpoints of the Kerberos
 * provider, while instances of {@link KerberosEndpoint} are referred
 * to as the endpoints of the provider. <p>
 *
 * Instances of this class are intended to be created for use with the
 * {@link net.jini.jeri.BasicJeriExporter} class.  Calls to {@link
 * #enumerateListenEndpoints enumerateListenEndpoints} return
 * instances of {@link KerberosEndpoint}. <p>
 *
 * This class supports at least the following standard constraints:
 * <p>
 *
 * <ul>
 * <li>{@link net.jini.core.constraint.Integrity#YES}
 * <li>{@link net.jini.core.constraint.Confidentiality}
 * <li>{@link net.jini.core.constraint.ClientAuthentication#YES}
 * <li>{@link net.jini.core.constraint.ConnectionAbsoluteTime},
 *     trivially, since this only takes effect on the client side
 * <li>{@link net.jini.core.constraint.ConnectionRelativeTime},
 *     trivially, since this only takes effect on the client side
 * <li>{@link net.jini.core.constraint.ServerAuthentication#YES}
 * <li>{@link net.jini.core.constraint.ClientMaxPrincipal}, when it
 *     contains at least one {@link KerberosPrincipal}
 * <li>{@link net.jini.core.constraint.ClientMaxPrincipalType}, when
 *     it contains the <code>KerberosPrincipal</code> class
 * <li>{@link net.jini.core.constraint.ClientMinPrincipal}, when it
 *     contains exactly one <code>KerberosPrincipal</code>
 * <li>{@link net.jini.core.constraint.ClientMinPrincipalType}, when
 *     it contains only the <code>KerberosPrincipal</code> class
 * <li>{@link net.jini.core.constraint.ServerMinPrincipal}, when it
 *     contains exactly one <code>KerberosPrincipal</code>
 * <li>{@link net.jini.core.constraint.Delegation}
 * <li>{@link net.jini.core.constraint.ConstraintAlternatives}, if the
 *     elements all have the same actual class and at least one
 *     element is supported
 * </ul>
 *
 * To get an instance of <code>KerberosServerEndpoint</code>, one of
 * the <code>getInstance</code> methods of the class has to be
 * invoked.  The returned server endpoint instance encapsulates a set
 * of properties that it will later use to receive and dispatch
 * inbound requests.  The following describes how some of these
 * properties are chosen: <p>
 *
 * <ul>
 * <li><code>serverSubject</code> - The {@link Subject} that contains
 *     the principal and credential to be used by the server endpoint
 *     to authenticate itself to its remote callers.  The subject is
 *     either provided by the caller of the <code>getInstance</code>
 *     method as a non-<code>null</code> argument, or extracted from
 *     the access control context of the current thread when the
 *     <code>getInstance</code> method is called.  The later value is
 *     also referred to as the default server subject.
 * <li><code>serverPrincipal</code> - The
 *     <code>KerberosPrincipal</code> that the server endpoint will
 *     authenticate itself as to all clients.  If the caller of the
 *     <code>getInstance</code> method provides a
 *     non-<code>null</code> <code>serverPrincipal</code> argument, it
 *     is used without any further checking; otherwise the default
 *     server principal will be used.  The default server principal
 *     can be any <code>KerberosPrincipal</code> instance in the
 *     <code>serverSubject</code>'s principal set, whose corresponding
 *     <code>KerberosKey</code> is found in the subject's private
 *     credential set and is still valid, provided the caller has been
 *     granted the {@link net.jini.security.AuthenticationPermission}
 *     with the principal as its local principal and
 *     <code>listen</code> as its action.
 * <li><code>serverHost</code> - The host name that will be
 *     encapsulated in <code>KerberosEndpoint</code> instances created
 *     by the server endpoint.  If a non-<code>null serverHost</code>
 *     is provided by the caller, it will be used; otherwise the
 *     default value is used, which is the IP address of the local
 *     host, as obtained from {@link InetAddress#getLocalHost
 *     InetAddress.getLocalHost}.  The host name does not affect the
 *     behavior of the listen operation itself, which always listens
 *     on all of the local system's network addresses, unless a
 *     <code>ServerSocketFactory</code> is provided by the caller, in
 *     which case the factory will be in charge.
 * </ul>
 *
 * This class permits specifying a {@link SocketFactory} for creating
 * the {@link Socket} instances that the associated
 * <code>KerberosEndpoint</code> instances use to make remote
 * connections back to the server, and a {@link ServerSocketFactory}
 * for creating the {@link ServerSocket} instances that the server
 * endpoint uses to accept remote connections. <p>
 *
 * A <code>SocketFactory</code> used with instances of this class
 * should be serializable, and should implement {@link Object#equals
 * Object.equals} to return <code>true</code> when passed an instance
 * that represents the same (functionally equivalent) socket
 * factory. A <code>ServerSocketFactory</code> used with instances of
 * this class should implement <code>Object.equals</code> to return
 * <code>true</code> when passed an instance that represents the same
 * (functionally equivalent) server socket factory. <p>
 *
 * This class uses the <a
 * href="../connection/doc-files/mux.html">Jini extensible remote
 * invocation (Jini ERI) multiplexing protocol</a> to map outgoing
 * requests to the underlying secure connection streams. <p>
 *
 * The secure connection streams in this provider are implemented
 * using the Kerberos Version 5 GSS-API Mechanism, defined in <a
 * href="http://www.ietf.org/rfc/rfc1964.txt">RFC 1964</a>, over
 * socket connections between client and server endpoints. <p>
 *
 * Note that, because Kerberos inherently requires client authentication,
 * this transport provider does not support distributed garbage collection
 * (DGC); if DGC is enabled using {@link net.jini.jeri.BasicJeriExporter},
 * all DGC remote calls through this provider will silently fail.
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * This class uses the following {@link Logger} to log information
 * at the following logging levels: <p>
 * 
 * <table border="1" cellpadding="5" summary="Describes logging to the
 *     server logger performed by endpoint classes in this package at
 *     different logging levels">
 * 
 *     <caption halign="center" valign="top"><b><code>
 * 	net.jini.jeri.kerberos.server</code></b></caption>
 * 
 *     <tr> <th scope="col"> Level <th scope="col"> Description
 *     <tr> <td> {@link java.util.logging.Level#WARNING WARNING}
 *          <td> unexpected failure while accepting connections on the created
 *               <code>ServerSocket</code>.
 *     <tr> <td> {@link org.apache.river.logging.Levels#FAILED FAILED}
 * 
 *          <td> problems with permission checking, server principal and
 *               Kerberos key presence checking, {@link
 *               org.ietf.jgss.GSSCredential} creation, socket connect
 *               acception, {@link org.ietf.jgss.GSSContext}
 *               establishment, credential expiration, or wrap/unwrap
 *               GSS tokens
 *     <tr> <td> {@link org.apache.river.logging.Levels#HANDLED HANDLED}
 *          <td> failure to set TCP no delay or keep alive properties on
 *               sockets
 *     <tr> <td> {@link java.util.logging.Level#FINE FINE}
 *          <td> server endpoint creation, {@link
 *               net.jini.jeri.ServerCapabilities#checkConstraints
 *               checkConstraints} results, server socket creation,
 *               socket connect acceptance, server connection
 *               creation/destruction, <code>GSSContext</code>
 *               establishment
 *     <tr> <td> {@link java.util.logging.Level#FINEST FINEST}
 *          <td> data message encoding/decoding using
 *               <code>GSSContext</code>
 * </table> <p>
 *
 * When the <code>ListenEndpoint.listen</code> method of this
 * implementation is invoked, a search is conducted on the private
 * credentials of the <code>serverSubject</code>, the first valid
 * <code>KerberosKey</code> whose principal equals to the
 * <code>serverPrincipal</code> is chosen as the server credential for
 * the listen operation.  The presence of this server credential in
 * the <code>serverSubject</code> as well as its validity are checked
 * both when a new incoming connection is received and a new request
 * arrives on an established connection; if the checks fail, the
 * listen operation or the connection will be aborted permanently. <p>
 *
 * This implementation uses the standard <a
 * href="http://www.ietf.org/rfc/rfc2853.txt">Java(TM) GSS-API</a>.
 * Additionally, for each inbound connection established, it invokes
 * {@link GSSUtil#createSubject GSSUtil.createSubject} to construct a
 * <code>Subject</code> instance, which encapsulates the principal and
 * delegated credential, if any, of the corresponding remote caller.
 *
 * 
 * @see KerberosEndpoint
 * @see KerberosTrustVerifier
 * @since 2.0
 */
public final class KerberosServerEndpoint implements ServerEndpoint {

    /** JERI Kerberos server transport logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.kerberos.server");

    /**
     * Pool of threads for executing tasks in system thread group:
     * used for the accept threads
     */
    private static final Executor systemThreadPool =
	(Executor) Security.doPrivileged(new GetThreadPoolAction(false));
    
    /** The server subject */
    private Subject serverSubject;

    /** Internal lock for class-wide synchronizaton */
    private static final Object classLock = new Object();

    /** GSSManager instance used by this server endpoint */
    private static GSSManager gssManager;

    /** Maximum size of the soft cache */
    private static final int maxCacheSize = ((Integer) Security.doPrivileged(
	new GetIntegerAction("org.apache.river.jeri.kerberos." +
			     "KerberosServerEndpoint.maxCacheSize",
			     256))).intValue();

    /** Access control context cache, keyed by constraints */
    private final KerberosUtil.SoftCache softCache;
    
    private static final LocalHost LOCAL_HOST 
            = new LocalHost(logger, KerberosServerEndpoint.class);

    /** The principal used for server authentication */
    private KerberosPrincipal serverPrincipal;

    /** The host name that clients should use to connect to this server. */
    private String serverHost;

    /** The server port */
    private final int port;

    /**
     * the socket factory that <code>KerberosEndpoint</code> objects
     * created by listening on this
     * <code>KerberosServerEndpoint</code> will use to create
     * {@link Socket}s
     */
    private final SocketFactory csf;

    /**
     * the socket factory that this
     * <code>KerberosServerEndpoint</code> uses to create {@link
     * ServerSocket}s
     */
    private final ServerSocketFactory ssf;

    /** ListenEndpoint instance used by this endpoint */
    private final ListenEndpointImpl listenEndpoint;

    /**
     * ServerConnManager instance used by this server endpoint.  This
     * field is not private or static or final because it might have
     * to be reset for discovery usage in
     * <code>KerberosEndpoint</code>.
     */
    ServerConnManager serverConnManager = new BasicServerConnManager();

    /** Static constraints will be used for unfulfilledConstraints */
    private static final InvocationConstraints INTEGRITY_REQUIRED_CONSTRAINTS =
        new InvocationConstraints(Integrity.YES, null);

    /** Static constraints  will be used for unfulfilledConstraints */
    private static final InvocationConstraints
    INTEGRITY_PREFERRED_CONSTRAINTS =
        new InvocationConstraints(null, Integrity.YES);

    //-----------------------------------
    //           constructors
    //-----------------------------------

    /**
     * Creates a Kerberos server endpoint with the specified subject,
     * server principal, server host, port, and socket factories. <p>
     *
     * @param serverSubject the server subject to use for
     *        authenticating the server. If <code>null</code>, the
     *        subject associated with the current access control
     *        context will be used.
     * @param serverPrincipal the principal server should authenticate
     *        as. If <code>null</code>, then the default server
     *        principal will be used.
     * @param serverHost the name or IP address of the server host the
     *        <code>KerberosEndpoint</code> instances created by this
     *        server endpoint will connect to. If <code>null</code>,
     *        the default server host will be used.
     * @param port the port this server endpoint will listen on, 0 to
     *        use any free port
     * @param csf the <code>SocketFactory</code> to be used by the
     *        <code>KerberosEndpoint</code> created by this server
     *        endpoint to create sockets, or <code>null</code> to let
     *        the <code>KerberosEndpoint</code> create {@link Socket}s
     *        directly.
     * @param ssf the <code>ServerSocketFactory</code> to use for this
     *        <code>KerberosServerEndpoint</code>, or
     *        <code>null</code> to let the
     *        <code>KerberosServerEndpoint</code> create {@link
     *        ServerSocket}s directly.
     * @throws UnsupportedConstraintException if the caller has not
     *         been granted the right
     *         <code>AuthenticationPermission</code>, or there is no
     *         default server subject
     *         (<code>serverSubject.getSubject(AccessController.getContext())
     *         </code> returns <code>null</code>), or no appropriate
     *         Kerberos principal and corresponding Kerberos key can
     *         be found in the server subject
     * @throws SecurityException if there is a security manager and
     *         the following condition is true:
     *
     *         <ul>
     *         <li>The passed in serverPrincipal is <code>null</code>,
     *             the caller has been granted {@link
     *             AuthPermission}<code>("getSubject") </code>, but
     *             no <code>listen</code>
     *             <code>AuthenticationPermission</code> whose local
     *             principal is a principal in the server subject's
     *             principal set, which is required for accessing any
     *             private credentials corresponding to the principal
     *             in the server subject.
     *          </ul>
     *
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>0</code> to
     *         <code>65535</code>
     */
    private KerberosServerEndpoint(Subject serverSubject,
				   KerberosPrincipal serverPrincipal,
				   String serverHost, int port,
				   SocketFactory csf, ServerSocketFactory ssf)
	throws UnsupportedConstraintException
    {
	boolean useCurrentSubject = serverSubject == null;
	boolean usePrincipalInSubject = serverPrincipal == null;

	if (useCurrentSubject) {
	    final AccessControlContext acc = AccessController.getContext();
	    serverSubject = (Subject) AccessController.doPrivileged(
		new PrivilegedAction() {
			public Object run() {
			    return Subject.getSubject(acc);
			}
		    });
	}

	Exception detailedException = null;
	if (usePrincipalInSubject) {
	    if (serverSubject == null) {
		detailedException = new UnsupportedConstraintException(
		    "Forgot JAAS login?  Using default " +
		    "serverSubject but no subject is associated " +
		    "with the current access control context.");
	    } else {
		try {
		    serverPrincipal = findServerPrincipal(serverSubject);
		} catch (Exception e) {
		    detailedException = e;
		}
	    }
	} else if (useCurrentSubject) {
	    try {
                /* caller provided principal, but want to use the
                   current subject, should only proceed if caller has
                   the listen AuthenticationPermission */
		KerberosUtil.checkAuthPermission(
		    serverPrincipal, null, "listen");
		if (serverSubject == null) {
		    detailedException = new UnsupportedConstraintException(
			"Forgot JAAS login?  Using default " +
			"serverSubject but no subject is associated " +
			"with the current access control context.");
		}
	    } catch (SecurityException e) {
		serverSubject = null;
		// will throw a SecurityException in enumerateListenEndpoints
	    }
	}

	if (detailedException != null) {
	    if (logger.isLoggable(Levels.FAILED)) {
		KerberosUtil.logThrow(
		    logger, Levels.FAILED, this.getClass(), 
		    "constructor", "construction failed", null, 
		    detailedException);
	    }
	    KerberosUtil.secureThrow(
		detailedException, new UnsupportedConstraintException(
		    "Either the caller has not been granted the right " +
		    "AuthenticationPermission, or there is no default " +
		    "server subject (<code>Subject.getSubject(" +
		    "AccessController.getContext())</code> returns " +
		    "<code>null</code>), or no appropriate Kerberos " +
		    "principal and its corresponding key can be found in " +
		    "the current subject."));
	}

	// have succeeded principal, subject, and auth permission checks
	this.serverSubject = serverSubject;
	this.serverPrincipal = serverPrincipal;

	if (port < 0 || port > 0xFFFF) {
	    throw new IllegalArgumentException(
		"port number out of range 0-65535: port = " + port);
	}

	this.serverHost = serverHost;
	this.port = port;
	this.csf = csf;
	this.ssf = ssf;
	softCache = new KerberosUtil.SoftCache(maxCacheSize);
	listenEndpoint = new ListenEndpointImpl();
	logger.log(Level.FINE, "created {0}", this);
    }

    //-----------------------------------
    //          public methods
    //-----------------------------------

    /**
     * Returns a <code>KerberosServerEndpoint</code> instance with the
     * specified port, using the default server subject, server
     * principal, and server host.
     *
     * @param port the port this server endpoint will listen on, 0 to
     *        use any free port
     * @return a <code>KerberosServerEndpoint</code> instance
     * @throws UnsupportedConstraintException if the caller has not
     *         been granted the right
     *         <code>AuthenticationPermission</code>, or there is no
     *         default server subject
     *         (<code>serverSubject.getSubject(AccessController.getContext())
     *         </code> returns <code>null</code>), or no appropriate
     *         Kerberos principal and corresponding Kerberos key can
     *         be found in the server subject
     * @throws SecurityException if there is a security manager and
     *         the following condition is true:
     *
     *         <ul>
     *         <li>The caller has been granted
     *             {@link AuthPermission}<code>("getSubject") </code>,
     *             but no <code>listen</code>
     *             <code>AuthenticationPermission</code> whose local
     *             principal is a principal in the server subject's
     *             principal set, which is required for accessing any
     *             private credentials corresponding to the principal
     *             in the server subject.
     *         </ul>
     *
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>0</code> to
     *         <code>65535</code>
     */
    public static KerberosServerEndpoint getInstance(int port)
	throws UnsupportedConstraintException
    {
	return new KerberosServerEndpoint(null, null, null, port, null, null);
    }

    /**
     * Returns a <code>KerberosServerEndpoint</code> instance with the
     * specified server host and port, using the default server
     * subject and server principal.
     *
     * @param serverHost the name or IP address of the server host the
     *        <code>KerberosEndpoint</code> instances created by this
     *        server endpoint will connect to. If <code>null</code>,
     *        the default server host will be used.
     * @param port the port this server endpoint will listen on, 0 to
     *        use any free port
     * @return a <code>KerberosServerEndpoint</code> instance
     * @throws UnsupportedConstraintException if the caller has not
     *         been granted the right
     *         <code>AuthenticationPermission</code>, or there is no
     *         default server subject
     *         (<code>serverSubject.getSubject(AccessController.getContext())
     *         </code> returns <code>null</code>), or no appropriate
     *         Kerberos principal and corresponding Kerberos key can
     *         be found in the server subject
     * @throws SecurityException if there is a security manager and
     *         the following condition is true:
     *
     *         <ul>
     *         <li>The caller has been granted
     *             {@link AuthPermission}<code>("getSubject") </code>,
     *             but no <code>listen</code>
     *             <code>AuthenticationPermission</code> whose local
     *             principal is a principal in the server subject's
     *             principal set, which is required for accessing any
     *             private credentials corresponding to the principal
     *             in the server subject.
     *         </ul>
     *
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>0</code> to
     *         <code>65535</code>
     */
    public static KerberosServerEndpoint getInstance(
	String serverHost, int port)
	throws UnsupportedConstraintException
    {
	return new KerberosServerEndpoint(
	    null, null, serverHost, port, null, null);
    }

    /**
     * Returns a <code>KerberosServerEndpoint</code> instance with the
     * specified server host, port, and socket factories, using the
     * default server subject and server principal.
     *
     * @param serverHost the name or IP address of the server host the
     *        <code>KerberosEndpoint</code> instances created by this
     *        server endpoint will connect to. If <code>null</code>,
     *        the default server host will be used.
     * @param port the port this server endpoint will listen on, 0 to
     *        use any free port
     * @param csf the <code>SocketFactory</code> to be used by the
     *        <code>KerberosEndpoint</code> created by this server
     *        endpoint to create sockets, or <code>null</code> to let
     *        the <code>KerberosEndpoint</code> create {@link Socket}s
     *        directly.
     * @param ssf the <code>ServerSocketFactory</code> to use for this
     *        <code>KerberosServerEndpoint</code>, or
     *        <code>null</code> to let the
     *        <code>KerberosServerEndpoint</code> create {@link
     *        ServerSocket}s directly.
     * @return a <code>KerberosServerEndpoint</code> instance
     * @throws UnsupportedConstraintException if the caller has not
     *         been granted the right
     *         <code>AuthenticationPermission</code>, or there is no
     *         default server subject
     *         (<code>serverSubject.getSubject(AccessController.getContext())
     *         </code> returns <code>null</code>), or no appropriate
     *         Kerberos principal and corresponding Kerberos key can
     *         be found in the server subject
     * @throws SecurityException if there is a security manager and
     *         the following condition is true:
     *
     *         <ul>
     *         <li>The caller has been granted
     *             {@link AuthPermission}<code>("getSubject") </code>,
     *             but no <code>listen</code>
     *             <code>AuthenticationPermission</code> whose local
     *             principal is a principal in the server subject's
     *             principal set, which is required for accessing any
     *             private credentials corresponding to the principal
     *             in the server subject.
     *         </ul>
     *
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>0</code> to
     *         <code>65535</code>
     */
    public static KerberosServerEndpoint getInstance(
	String serverHost, int port, SocketFactory csf,
	ServerSocketFactory ssf)
	throws UnsupportedConstraintException
    {
	return new KerberosServerEndpoint(
	    null, null, serverHost, port, csf, ssf);
    }

    /**
     * Returns a <code>KerberosServerEndpoint</code> instance with the
     * specified server subject, server principal, server host, and
     * port.
     *
     * @param serverSubject the server subject to use for
     *        authenticating the server. If <code>null</code>, the
     *        subject associated with the current access control
     *        context will be used.
     * @param serverPrincipal the principal server should authenticate
     *        as. If <code>null</code>, then the default server
     *        principal will be used.
     * @param serverHost the name or IP address of the server host the
     *        <code>KerberosEndpoint</code> instances created by this
     *        server endpoint will connect to. If <code>null</code>,
     *        the default server host will be used.
     * @param port the port this server endpoint will listen on, 0 to
     *        use any free port
     * @return a <code>KerberosServerEndpoint</code> instance
     * @throws UnsupportedConstraintException if the caller has not
     *         been granted the right
     *         <code>AuthenticationPermission</code>, or there is no
     *         default server subject
     *         (<code>serverSubject.getSubject(AccessController.getContext())
     *         </code> returns <code>null</code>), or no appropriate
     *         Kerberos principal and corresponding Kerberos key can
     *         be found in the server subject
     * @throws SecurityException if there is a security manager and
     *         the following condition is true:
     *
     *         <ul>
     *         <li>The passed in serverPrincipal is <code>null</code>,
     *             the caller has the
     *             {@link AuthPermission}<code>("getSubject")
     *             </code>, but no <code>listen</code>
     *             <code>AuthenticationPermission</code> whose local
     *             principal is a principal in the server subject's
     *             principal set, which is required for accessing any
     *             private credentials corresponding to the principal
     *             in the server subject.
     *         </ul>
     *
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>0</code> to
     *         <code>65535</code>
     */
    public static KerberosServerEndpoint getInstance(
	Subject serverSubject, KerberosPrincipal serverPrincipal,
	String serverHost, int port)
	throws UnsupportedConstraintException
    {
	return new KerberosServerEndpoint(
	    serverSubject, serverPrincipal, serverHost, port, null, null);
    }

    /**
     * Returns a <code>KerberosServerEndpoint</code> instance with the
     * specified server subject, server principal, server host, port,
     * and socket factories.
     *
     * @param serverSubject the server subject to use for
     *        authenticating the server. If <code>null</code>, the
     *        subject associated with the current access control
     *        context will be used.
     * @param serverPrincipal the principal server should authenticate
     *        as. If <code>null</code>, then the default server
     *        principal will be used.
     * @param serverHost the name or IP address of the server host the
     *        <code>KerberosEndpoint</code> instances created by this
     *        server endpoint will connect to. If <code>null</code>,
     *        the default server host will be used.
     * @param port the port this server endpoint will listen on, 0 to
     *        use any free port
     * @param csf the <code>SocketFactory</code> to be used by the
     *        <code>KerberosEndpoint</code> created by this server
     *        endpoint to create sockets, or <code>null</code> to let
     *        the <code>KerberosEndpoint</code> create {@link Socket}s
     *        directly.
     * @param ssf the <code>ServerSocketFactory</code> to use for this
     *        <code>KerberosServerEndpoint</code>, or
     *        <code>null</code> to let the
     *        <code>KerberosServerEndpoint</code> create {@link
     *        ServerSocket}s directly.
     * @return a <code>KerberosServerEndpoint</code> instance
     * @throws UnsupportedConstraintException if the caller has not
     *         been granted the right
     *         <code>AuthenticationPermission</code>, or there is no
     *         default server subject
     *         (<code>serverSubject.getSubject(AccessController.getContext())
     *         </code> returns <code>null</code>), or no appropriate
     *         Kerberos principal and corresponding Kerberos key can
     *         be found in the server subject
     * @throws SecurityException if there is a security manager and
     *         the following condition is true:
     *
     *         <ul>
     *         <li>The passed in serverPrincipal is <code>null</code>,
     *             the caller has the {@link
     *             AuthPermission}<code>("getSubject") </code>, but
     *             no <code>listen</code>
     *             <code>AuthenticationPermission</code> whose local
     *             principal is a principal in the server subject's
     *             principal set, which is required for accessing any
     *             private credentials corresponding to the principal
     *             in the server subject.
     *         </ul>
     *
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>0</code> to
     *         <code>65535</code>
     */
    public static KerberosServerEndpoint getInstance(
	Subject serverSubject, KerberosPrincipal serverPrincipal,
	String serverHost, int port, SocketFactory csf,
	ServerSocketFactory ssf)
	throws UnsupportedConstraintException
    {
	return new KerberosServerEndpoint(
	    serverSubject, serverPrincipal, serverHost, port, csf, ssf);
    }

    /**

     * Returns the host name that will be used in {@link
     * KerberosEndpoint} instances created by listening on this
     * object.
     *
     * @return the host name to use in <code>KerberosEndpoint</code>
     *         instances created by listening on this object
     */
    public String getHost() {
	return serverHost;
    }

    /**
     * Returns the TCP port that the <code>ListenEndpoint</code>s
     * created by this server endpoint listen on.
     *
     * @return the TCP port that the endpoints listen on
     */
    public int getPort() {
	return port;
    }

    /**
     * Returns the principal that this server endpoint will
     * authenticate itself as.
     *
     * @return the principal that this server endpoint will
     *         authenticate as
     */
    public KerberosPrincipal getPrincipal() {
	return serverPrincipal;
    }

    /**
     * Returns the socket factory that the associated {@link
     * KerberosEndpoint} instances, which are created by listening on
     * the <code>ListenEndpoint</code> instances of this server
     * endpoint, use to create {@link Socket} instances, or
     * <code>null</code> if they use default sockets.
     *
     * @return the socket factory that associated endpoints use to
     *         create sockets, or <code>null</code> if they use
     *         default sockets
     */
    public SocketFactory getSocketFactory() {
	return csf;
    }

    /**
     * Returns the server socket factory that this server endpoint
     * uses to create {@link ServerSocket} instances, or
     * <code>null</code> if it uses default server sockets.
     *
     * @return the server socket factory that this server endpoint
     *         uses to create server sockets, or <code>null</code> if
     *         it uses default server sockets
     */
    public ServerSocketFactory getServerSocketFactory() {
	return ssf;
    }

    /* -- Implement ServerCapabilities -- */

    // Javadoc is inherited from the ServerCapabilities interface
    public InvocationConstraints checkConstraints(
	InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints == null)
	    throw new NullPointerException();

	InvocationConstraints unfulfilledConstraints;
	try {
	    // check for unsupportable constraints
	    for (Iterator iter = constraints.requirements().iterator();
		 iter.hasNext(); )
	    {
		InvocationConstraint c = (InvocationConstraint)iter.next();
		if (!KerberosUtil.isSupportableConstraint(c)) {
		    throw new UnsupportedConstraintException(
			"A constraint unsupportable by this endpoint " +
			"has been required: " + c);
		}
	    }

	    if (getKey(serverSubject, serverPrincipal) == null) {
		throw new UnsupportedConstraintException(
		    "Failed to find a valid Kerberos key " +
		    "corresponding to serverPrincipal (" + serverPrincipal +
		    ") in serverSubject.");
	    }

	    // now check whether the constraints are satisfiable
	    
	    // first find all client principal candidates in constraints
	    HashSet cpCandidates = new HashSet();
	    for (Iterator iter = constraints.requirements().iterator();
		 iter.hasNext(); )
	    {
		if (!KerberosUtil.collectCpCandidates(
		    (InvocationConstraint) iter.next(), cpCandidates))
		{
		    throw new UnsupportedConstraintException(
			"Client principal constraint related conflicts " +
			"found in the given set of constraints: " + 
			constraints);
		}
	    }

	    if (cpCandidates.size() == 0) {
		/* no client principal constraints is required, anyone
		   will pass */
		cpCandidates.add(new KerberosPrincipal("anyone"));
	    }

	    // look for a satisfiable config
	    boolean doable = false;
	    ConfigIter citer =
		new ConfigIter(cpCandidates, serverPrincipal, true);
	  outer:
	    while (citer.hasNext()) {
		Config config = citer.next();
		for (Iterator iter = constraints.requirements().iterator();
		     iter.hasNext(); )
		{
		    InvocationConstraint c =
			(InvocationConstraint) iter.next();
		    if (!KerberosUtil.isSatisfiable(config, c))
			continue outer;
		}
		doable = true;
		break;
	    }

	    if (!doable) {
		throw new UnsupportedConstraintException(
		    "Conflicts found in the given set of constraints: " +
		    constraints);
	    }
	
	    unfulfilledConstraints = InvocationConstraints.EMPTY;
	    if (KerberosUtil.containsConstraint(
		constraints.requirements(), Integrity.YES))
	    {
		unfulfilledConstraints =
		    KerberosUtil.INTEGRITY_REQUIRED_CONSTRAINTS;
	    } else if (KerberosUtil.containsConstraint(
		constraints.preferences(), Integrity.YES))
	    {
		unfulfilledConstraints =
		    KerberosUtil.INTEGRITY_PREFERRED_CONSTRAINTS;
	    }
	} catch (UnsupportedConstraintException uce) {
	    if (logger.isLoggable(Levels.FAILED)) {
		KerberosUtil.logThrow(
		    logger, Levels.FAILED, this.getClass(), 
		    "checkConstraints", "check constraints for " +
		    "{0}\nwith {1}\nthrows",
		    new Object[] {this, constraints}, uce);
	    }
	    throw uce;
	} catch (SecurityException se) {
	    if (logger.isLoggable(Levels.FAILED)) {
		KerberosUtil.logThrow(
		    logger, Levels.FAILED, this.getClass(), 
		    "checkConstraints", "check constraints for " +
		    "{0}\nwith {1}\nthrows",
		    new Object[] {this, constraints}, se);
	    }
	    throw se;
	}
	
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "checkConstraints() has determined " +
		       "that this endpoint can support the given " +
		       "constraints:\n{0}.\nWhile assistances are needed " +
		       "from upper layers to satisfy constraints:\n{1}",
		       new Object[] {constraints, unfulfilledConstraints});
	}

	return unfulfilledConstraints;
    }

    /* -- Implement ServerEndpoint -- */

    /**
     * Passes the {@link net.jini.jeri.ServerEndpoint.ListenEndpoint
     * ListenEndpoint} for this <code>KerberosServerEndpoint</code> to
     * <code>listenContext</code>, which will ensure an active listen
     * operation on the endpoint, and returns a <code>KerberosEndpoint</code>
     * instance corresponding to the listen operation chosen by
     * <code>listenContext</code>. <p>
     *
     * If this server endpoint's server host is <code>null</code>,
     * then the endpoint returned will contain the default server
     * host. This method computes the default by invoking {@link
     * InetAddress#getLocalHost InetAddress.getLocalHost} to obtain an
     * <code>InetAddress</code> for the local host. If
     * <code>InetAddress.getLocalHost</code> throws an
     * <code>UnknownHostException</code>, this method throws an
     * <code>UnknownHostException</code>.
     * The default host name will be the string returned by invoking 
     * {@link InetAddress#getHostAddress getHostAddress} on the obtained
     * <code>InetAddress</code>. If there is a security manager, its {@link
     * SecurityManager#checkConnect(String,int) checkConnect} method
     * will be invoked with the string returned by invoking {@link
     * InetAddress#getHostName getHostName} on that same
     * <code>InetAddress</code> as the host argument and
     * <code>-1</code> as the port argument; this could result in a
     * <code>SecurityException</code>. <p>
     *
     * This method invokes <code>addListenEndpoint</code> on
     * <code>listenContext</code> once, passing a <code>ListenEndpoint</code>
     * as described below.  If <code>addListenEndpoint</code> throws an
     * exception, then this method throws that exception.  Otherwise, this
     * method returns a <code>KerberosEndpoint</code> instance with the host
     * name described above, the TCP port number bound by the listen operation
     * represented by the {@link net.jini.jeri.ServerEndpoint.ListenHandle
     * ListenHandle} returned by <code>addListenEndpoint</code>, and the same
     * server principal and <code>SocketFactory</code> as this
     * <code>KerberosServerEndpoint</code>. <p>
     *
     * The <code>ListenEndpoint</code> passed to
     * <code>addListenEndpoint</code> represents the server subject, server
     * principal, TCP port number, and <code>ServerSocketFactory</code> of this
     * <code>KerberosServerEndpoint</code>.  Its methods behave as follows:
     * <p>
     *
     * {@link net.jini.jeri.ServerEndpoint.ListenEndpoint#listen ListenHandle
     * listen(RequestDispatcher)}:
     *
     * <blockquote>
     *
     * Listens for requests received on this endpoint's TCP port, dispatching
     * them to the supplied <code>RequestDispatcher</code> in the form of
     * {@link net.jini.jeri.InboundRequest} instances. <p>
     *
     * When the implementation of this method needs to create a new
     * <code>ServerSocket</code>, it will do so by invoking one of the
     * <code>createServerSocket</code> methods that returns a bound server
     * socket on the contained <code>ServerSocketFactory</code> if
     * non-<code>null</code>, or it will create a <code>ServerSocket</code>
     * directly otherwise. <p>
     *
     * If there is a security manager, its {@link SecurityManager#checkListen
     * checkListen} method will be invoked with this endpoint's TCP port; this
     * could result in a <code>SecurityException</code>. In addition, the
     * security manager's {@link SecurityManager#checkPermission
     * checkPermission} method will be invoked with an {@link
     * net.jini.security.AuthenticationPermission} containing the server
     * principal of this endpoint and the <code>listen</code> action; this
     * could also result in a <code>SecurityException</code>.  Furthermore,
     * before a given <code>InboundRequest</code> gets dispatched to the
     * supplied request dispatcher, the security manager's {@link
     * SecurityManager#checkAccept checkAccept} method must have been
     * successfully invoked in the security context of this
     * <code>listen</code> invocation with the remote IP address and port of
     * the <code>Socket</code> used to receive the request, and the security
     * manager's <code>checkPermission</code> method must have been
     * successfully invoked in the same context with an
     * <code>AuthenticationPermission</code> containing the server principal
     * of this endpoint as local principal, the client's authenticated
     * principal as peer principal, and the <code>accept</code> action. The
     * <code>checkPermissions</code> method of the dispatched
     * <code>InboundRequest</code> also performs these latter security checks.
     * (Note that in some cases, the implementation may carry out some of
     * these security checks indirectly, such as through invocations of
     * <code>ServerSocket</code>'s constructors or <code>accept</code>
     * method.) <p>
     *
     * Once a <code>ListenEndpoint</code> of this provider starts to
     * listen, each time a new inbound request comes in, it verifies
     * that the <code>serverSubject</code> of the corresponding server
     * endpoint still holds a valid <code>KerberosKey</code>
     * corresponding to its <code>serverPrincipal</code>.  It rejects
     * the request if the verification fails. This guarantees that as
     * soon as the <code>KerberosKey</code> for the principal in the
     * <code>Subject</code> is destroyed (logout) or expired, no more
     * new requests will be accepted. <p>
     *
     * Requests will be dispatched in a {@link PrivilegedAction} wrapped by a
     * {@link SecurityContext} obtained when this method was invoked, with the
     * {@link AccessControlContext} of that <code>SecurityContext</code> in
     * effect. <p>
     *
     * Dispatched requests will implement {@link
     * net.jini.jeri.InboundRequest#populateContext populateContext} to
     * populate the given collection with an element that implements the
     * {@link net.jini.io.context.ClientHost} interface, and an element that
     * implements the {@link net.jini.io.context.ClientSubject} interface. The
     * <code>ClientHost</code> element implements {@link
     * net.jini.io.context.ClientHost#getClientHost getClientHost} to return
     * the IP address of the <code>Socket</code> that the request was received
     * over (see {@link Socket#getInetAddress}). <p>
     *
     * Throws {@link IOException} if an I/O exception occurs while performing
     * this operation, such as if the TCP port is already in use. <p>
     *
     * Throws {@link SecurityException} if there is a security manager and an
     * invocation of its <code>checkListen</code> or
     * <code>checkPermission</code> method fails. <p>
     *
     * Throws {@link NullPointerException} if <code>requestDispatcher</code>
     * is <code>null</code>
     *
     * </blockquote>
     *
     * {@link net.jini.jeri.ServerEndpoint.ListenEndpoint#checkPermissions
     * void checkPermissions()}:
     *
     * <blockquote>
     *
     * Verifies that the current security context has all of the security
     * permissions necessary to listen for requests on this endpoint. <p>
     *
     * If there is a security manager, its <code>checkListen</code> method
     * will be invoked with this endpoint's TCP port; this could result in a
     * <code>SecurityException</code>. In addition, the security manager's
     * {@link SecurityManager#checkPermission checkPermission} method will be
     * invoked with an {@link net.jini.security.AuthenticationPermission}
     * containing the server principal of this endpoint and the
     * <code>listen</code> action; this could also result in a
     * <code>SecurityException</code>. <p>
     *
     * Throws {@link SecurityException} if there is a security manager and an
     * invocation of its <code>checkListen</code> or
     * <code>checkPermission</code> method fails.
     *
     * </blockquote>
     *
     * {@link Object#equals boolean equals(Object)}:
     *
     * <blockquote>
     *
     * Compares the specified object with this <code>ListenEndpoint</code> for
     * equality. <p>
     *
     * This method returns <code>true</code> if and only if the specified
     * object is also a <code>ListenEndpoint</code> produced by a
     * <code>KerberosServerEndpoint</code>, and the two listen endpoints both
     * have server subjects that compare equal using <code>==</code>; have the
     * same server principal; have the same values for TCP port; and have
     * server socket factories that are either both <code>null</code>, or have
     * the same actual class and are equal.
     *
     * </blockquote>
     *
     * @throws SecurityException if there is a security manager, and
     *         either its {@link SecurityManager#checkListen
     *         checkListen} method fails, or <code>serverHost</code>
     *         is <code>null</code> and the security manager's {@link
     *         SecurityManager#checkConnect checkConnect} method
     *         fails; or if the calling thread does not have
     *         permission to authenticate as the endpoint's server
     *         principal when listening for connections
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws UnknownHostException if this instance's server host
     *	       is <code>null</code> and
     *	       <code>InetAddress.getLocalHost</code> throws an
     *         <code>UnknownHostException</code>
     * @throws UnsupportedConstraintException if the server subject is
     *         missing any of the endpoint's server principals or
     *         the associated credentials
     */
    public Endpoint enumerateListenEndpoints(ListenContext listenContext)
	throws IOException
    {
        serverHost = LOCAL_HOST.check(serverHost, this);

	ListenCookieImpl cookie = checkListenCookie(
	    listenContext.addListenEndpoint(listenEndpoint));
	return KerberosEndpoint.getInstance(
	    serverHost, cookie.getLocalPort(), serverPrincipal, csf);
    }

    /** Returns a hash code value for this object. */
    public int hashCode() {
	return getClass().getName().hashCode() ^
	    System.identityHashCode(serverSubject) ^
	    serverPrincipal.hashCode() ^ 
	    (serverHost != null ? serverHost.hashCode() : 0) ^ port ^
	    (ssf != null ? ssf.hashCode() : 0) ^
	    (csf != null ? csf.hashCode() : 0);
    }

    /**
     * Two instances of this class are equal if they have server
     * subjects that compare equal using <code>==</code>; have the
     * same server principal; have the same values for server host and
     * port; have socket factories that are either both
     * <code>null</code>, or have the same actual class and are equal;
     * and have server socket factories that are either both
     * <code>null</code>, or have the same actual class and are equal.
     */
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof KerberosServerEndpoint)) {
	    return false;
	}
	KerberosServerEndpoint ose = (KerberosServerEndpoint) obj;
	return serverSubject == ose.serverSubject &&
	    serverPrincipal.equals(ose.serverPrincipal) &&
	    Util.equals(serverHost, ose.serverHost) &&
	    port == ose.port &&
	    Util.sameClassAndEquals(csf, ose.csf) &&
	    Util.sameClassAndEquals(ssf, ose.ssf);
    }

    /** Returns a string representation of this object. */
    public String toString() {
	return "KerberosServerEndpoint[serverPrincipal=" + serverPrincipal +
            " serverHost= " + serverHost +
	    " serverPort= " + port +
	    (ssf == null ? "" : " ssf = " + ssf.toString()) +
	    (csf == null ? "" : " csf = " + csf.toString()) + "]";
    }

    //-----------------------------------
    //          private methods
    //-----------------------------------

    /** Pick the serverPrincipal from the given subject. */
    private static KerberosPrincipal findServerPrincipal(Subject subject) 
	throws UnsupportedConstraintException
    {
	Set pset = subject.getPrincipals();
	Set kpset = new HashSet(pset.size()); // Kerberos principals
	synchronized (pset) {
	    for (Iterator iter = pset.iterator(); iter.hasNext();) {
		Object p = iter.next();
		if (p instanceof KerberosPrincipal)
		    kpset.add(p);
	    }
	}

	if (kpset.isEmpty()) {
	    throw new UnsupportedConstraintException(
		"No KerberosPrincipal found in the serverSubject.");
	}

	boolean hasAuthPerm = false;
	for (Iterator iter = kpset.iterator(); iter.hasNext();) {
	    KerberosPrincipal kp = (KerberosPrincipal) iter.next();
	    try {
		if (getKey(subject, kp) != null) {
		    return kp;
		} else {
		    // if reach here, has auth perm, but no key
		    hasAuthPerm = true;
		}
	    } catch (SecurityException e) {}
	}

	// note that exceptions throw here are logged in the constructor
	if (hasAuthPerm) {
	    throw new UnsupportedConstraintException(
		"Cannot find any Kerberos key in the serverSubject " +
		"corresponding to one of its principals.");
	} else {
	    throw new SecurityException(
		"Caller does not have AuthenticationPermission " +
		"to access Kerberos keys of any principal in " +
		"the serverSubject.");
	}
    }

    /**
     * Get the a valid Kerberos key corresponding to the specified
     * server principal from the given subject.
     * 
     * @param subject the subject from which the key will be
     *        extracted, cannot be null
     * @param principal the principal of the requested key, can not be
     *        null
     * @return the first valid Kerberos key found in the server
     *         subject corresponding to the given principal , or null
     *         if none can be found.
     * @throws SecurityException if caller does not have the required
     *         AuthenticationPermission
     */
    private static KerberosKey getKey(
	final Subject subject, final KerberosPrincipal principal)
    {
	// SecurityException will be logged by callers
	KerberosUtil.checkAuthPermission(principal, null, "listen");
	if (subject == null)
	    return null; // did not properly get the serverSubject
	return (KerberosKey) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    Set creds = subject.getPrivateCredentials();
		    synchronized (creds) {
			for (Iterator iter = creds.iterator();
			     iter.hasNext();)
			{
			    Object cred = iter.next();
			    if (cred instanceof KerberosKey) {
				KerberosKey key = (KerberosKey) cred;
				if (!key.isDestroyed() && 
				    key.getPrincipal().equals(principal))
				{
				    return key;
				}
			    }
			}
		    }
		    return null;
		}
	    });
    }
    
    /**
     * Make sure that the passed in listen cookie has the right type,
     * and has an equivalent ListenEndpoint to this KerberosServerEndpoint's
     * listenEndpoint.
     */
    private ListenCookieImpl checkListenCookie(Object cookie) {
	if (!(cookie instanceof ListenCookieImpl)) {
	    throw new IllegalArgumentException(
		"Cookie with unexpected type: " + cookie);
	}
	ListenCookieImpl listenCookie = (ListenCookieImpl) cookie;
	if (!listenEndpoint.equals(listenCookie.getListenEndpoint())) {
	    throw new IllegalArgumentException(
		"ListenEndpoint mis-match, enclosing lep is:\n" +
		listenEndpoint + "\nwhile cookie's enclosing lep is:\n" +
		listenCookie.getListenEndpoint());
	}
	return listenCookie;
    }

    //-----------------------------------
    //       private inner classes
    //-----------------------------------

    private final class ListenEndpointImpl implements ListenEndpoint {

	// Javadoc is inherited from the ListenEndpoint interface
	public void checkPermissions() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		try {
		    sm.checkListen(port);
		    KerberosUtil.checkAuthPermission(
			serverPrincipal, null, "listen");
		} catch (SecurityException se) {
		    if (logger.isLoggable(Levels.FAILED)) {
			KerberosUtil.logThrow(
			    logger, Levels.FAILED, this.getClass(), 
			    "checkPermissions", "check permissions for " +
			    "{0}\nthrows", new Object[] {this}, se);
		    }
		    throw se;
		}
	    }
	}

	// Javadoc is inherited from the ListenEndpoint interface
	public ListenHandle listen(RequestDispatcher requestDispatcher)
	    throws IOException
	{
	    if (requestDispatcher == null)
		throw new NullPointerException("null dispatcher is passed in");

	    KerberosKey serverKey;
	    GSSCredential serverCred;
	    try {
		// make sure that serverPrincipal is in serverSubject
		if (serverSubject != null &&
		    !serverSubject.getPrincipals().contains(serverPrincipal))
		{
		    throw new UnsupportedConstraintException(
			"Failed to find serverPrincipal " + serverPrincipal + 
			"in serverSubject's principal set, cannot listen.");
		}

		// getKey checks AuthenticationPermission "listen"
		serverKey = getKey(serverSubject, serverPrincipal);

		if (serverKey == null) {
		    throw new UnsupportedConstraintException(
			"No valid Kerberos key in the server subject for " + 
			serverPrincipal + ", cannot listen.");
		}

		synchronized (classLock) {
		    if (gssManager == null) {
			gssManager = GSSManager.getInstance();
		    }
		}

		try {
		    serverCred = (GSSCredential) Security.doPrivileged(
			new PrivilegedExceptionAction() {
				public Object run() throws GSSException {
				    return KerberosUtil.getGSSCredential(
					serverSubject, serverPrincipal,
					gssManager, GSSCredential.ACCEPT_ONLY);
				}
			    });
		} catch (PrivilegedActionException pe) {
		    GSSException ge = (GSSException) pe.getException();
		    throw new UnsupportedConstraintException(
			"Failed to get GSSCredential for server principal: " +
			serverPrincipal, ge);
		}
	    } catch (UnsupportedConstraintException uce) {
		if (logger.isLoggable(Levels.FAILED)) {
		    KerberosUtil.logThrow(
			logger, Levels.FAILED, this.getClass(), 
			"listen", "listen for {0}\nthrows",
			new Object[] {this}, uce);
		}
		throw uce;
	    } catch (SecurityException se) {
		if (logger.isLoggable(Levels.FAILED)) {
		    KerberosUtil.logThrow(
			logger, Levels.FAILED, this.getClass(), 
			"listen", "listen for {0}\nthrows",
			new Object[] {this}, se);
		}
		throw se;
	    }

	    ServerSocket serverSocket;
	    boolean done = false;
	    try {
		if (ssf != null) {
		    serverSocket = ssf.createServerSocket(port);
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "created {0} using factory " +
				   "{1}", new Object[]{serverSocket, ssf});
		    }
		} else {
		    serverSocket = new ServerSocket(port);
		    logger.log(Level.FINE, "created {0}", serverSocket);
		}
		done = true;
	    } finally {
		if (!done) {
		    try {
			serverCred.dispose();
		    } catch (GSSException e) {}
		}
	    }

	    ListenHandleImpl listenHandle = new ListenHandleImpl(
		requestDispatcher, serverKey, serverCred, serverSocket,
		Security.getContext());
	    listenHandle.startAccepting();
	    return listenHandle;
	}

	/** Returns a hash code value for this object. */
	public int hashCode() {
	    int hash = getClass().getName().hashCode() ^
		System.identityHashCode(serverSubject) ^ 
		serverPrincipal.hashCode() ^ port;
	    if (ssf != null)
		hash ^= ssf.hashCode();
	    return hash;
	}

	/**
	 * Two instances of this class are equal if they are instances
	 * of the same concrete class; have server subjects that
	 * compare equal using <code>==</code>; have the same server
	 * principal; have the same port; and have server socket
	 * factories that are both null, or have the same actual class
	 * and are equal.  Note that two listen endpoints can be equal
	 * without having the same server host or client socket
	 * factory.
	 */
	public boolean equals(Object obj) {
	    if (obj == this) {
		return true;
	    } else if (!(obj instanceof ListenEndpointImpl)) {
		return false;
	    }
	    ListenEndpointImpl ole = (ListenEndpointImpl) obj;
	    KerberosServerEndpoint ose = ole.getServerEndpoint();
	    return serverSubject == ose.serverSubject &&
		serverPrincipal.equals(ose.serverPrincipal) &&
		port == ose.port &&
		Util.sameClassAndEquals(ssf, ose.ssf);
	}

	/** Returns a string representation of this object. */
	public String toString() {
	    return "KerberosServerEndpoint.ListenEndpointImpl" +
		"[serverPrincipal=" + serverPrincipal +
		" serverPort = " + port +
		(ssf == null ? "" : " ssf = " + ssf.toString()) +
		(csf == null ? "" : " csf = " + csf.toString()) + "]";
	}

	private KerberosServerEndpoint getServerEndpoint() {
	    return KerberosServerEndpoint.this;
	}
    }

    private final class ListenCookieImpl implements ListenCookie {

	int localPort;

	ListenCookieImpl(int localPort) {
	    this.localPort = localPort;
	}
	
	ListenEndpointImpl getListenEndpoint() {
	    return listenEndpoint;
	}
	
	int getLocalPort() {
	    return localPort;
	}
    }

    /**
     * A runnable class which creates a server socket and listens for
     * client connections. It also maintains a reference to each of
     * the accepted connections that have not been closed yet.
     */
    private final class ListenHandleImpl implements ListenHandle {

	private final RequestDispatcher dispatcher;

	/** The security context at the time of the listen. */
	private final SecurityContext securityContext;

	/** The Kerberos key used for server authentication */
	private KerberosKey serverKey; // cached for the calling listen
	
	/** The credential this server uses to authenticate itself */
	final GSSCredential serverCred; // cached for establishContext in conn

	/** ListenCookie of the listen operation */
	ListenCookieImpl listenCookie;

	private final ServerSocket serverSocket;
	private final Set connections = new HashSet();
	private final Object lock = new Object();
	private boolean closed = false;

	private long acceptFailureTime = 0L; // local to accept thread
	private int acceptFailureCount;	     // local to accept thread

	ListenHandleImpl(RequestDispatcher dispatcher, KerberosKey serverKey,
			 GSSCredential serverCred, ServerSocket serverSocket,
			 SecurityContext securityContext)
	{
	    this.dispatcher = dispatcher;
	    this.serverKey = serverKey;
	    this.serverCred = serverCred;
	    this.serverSocket = serverSocket;
	    this.securityContext = securityContext;
	    listenCookie = new ListenCookieImpl(serverSocket.getLocalPort());
	}

	/**
	 * Starts the accept loop.
	 */
	void startAccepting() {
	    systemThreadPool.execute(
		new Runnable() {
		    public void run() {
			executeAcceptLoop();
		    }
		}, toString() + " accept loop");
	}

	/**
	 * Executes the accept loop.
	 *
	 * The accept loop runs with the full privileges of this code;
	 * "accept" SocketPermissions are checked against the direct
	 * user of this endpoint later, when the
	 * ServerConnectionManager calls checkPermissions on the
	 * InboundRequest before passing it to the RequestDispatcher.
	 */
	private void executeAcceptLoop() {
	    while (true) {
		Socket socket = null;
		ServerConnectionImpl connection = null;
		boolean done = false;
		try {
		    socket = serverSocket.accept();
		    synchronized (lock) {
			if (closed)
			    break;
			connection = new ServerConnectionImpl(socket, this);
			connections.add(connection);
		    }
		    logger.log(Level.FINE, "{0} accepted", connection);
		    /* instead of checking server key presence here,
		       it will be checked in processRequestData */
		    ConnectionHandler handler = new ConnectionHandler(
			connection, dispatcher, securityContext);
		    systemThreadPool.execute(handler, handler.toString());
		    done = true;
		} catch (Throwable t) {
		    synchronized (lock) {
			if (closed)
			    break;
		    }
		    try {
			if (logger.isLoggable(Level.WARNING)) {
			    KerberosUtil.logThrow(
				logger, Level.WARNING, this.getClass(),
				"executeAcceptLoop",
				"accept loop for {0} throws",
				new Object[] {this}, t);
			}
		    } catch (Throwable tt) {}
		    boolean knownFailure =
			(t instanceof Exception ||
			 t instanceof OutOfMemoryError ||
			 t instanceof NoClassDefFoundError);
		    if (!(knownFailure && continueAfterAcceptFailure(t))) {
			/*
			 * REMIND: Do we really want to give up here,
			 * for other Errors?
			 *
			 * Note: if reach here, we are in trouble already.
			 * Close serverSocket instead of the listen
			 * operation completely will free the port
			 * but left the established connections alone.
			 */
			try {
			    serverSocket.close();
			} catch (IOException e) {}
			if (knownFailure)
			    return;
			throw (Error) t;
		    }
		} finally {
		    if (!done) {
			if (connection != null) {
			    connection.close();
			} else if (socket != null) {
			    try {
				socket.close();
			    } catch (IOException e) {}
			}
		    }
		}
	    }
	}

	// Javadoc is inherited from the ListenHandle interface
	public ListenCookie getCookie() {
	    return listenCookie;
	}
	
	/** Close the server socket and all accepted connections */
	public void close() {
	    synchronized (lock) {
		if (closed)
		    return;
		closed = true;
	    }

	    /*
	     * Iterating over connections without synchronization is
	     * safe at this point because no other thread will access
	     * it without verifying that closed is false in a
	     * synchronized block first.
	     */
	    for (Iterator iter = connections.iterator(); iter.hasNext(); ) {
		((ServerConnectionImpl) iter.next()).close();
	    }
	    connections.clear();

	    try {
		serverSocket.close();
	    } catch (IOException e) {}

	    try {
		serverCred.dispose();
	    } catch (GSSException e) {}

	    logger.log(Level.FINE, "Listen operation {0} has been closed",
		       this);
	}

	/** Returns a string representation of this listen handle. */
	public String toString() {
	    return "KerberosServerEndpoint.ListenHandleImpl" +
		"[serverPrincipal=" + serverPrincipal +
		" portListening = " + serverSocket.getLocalPort() +
		(ssf == null ? "" : " ssf = " + ssf.toString()) +
		(csf == null ? "" : " csf = " + csf.toString()) + "]";
	}

	/**
	 * Remove a server connection from the connection list of this
	 * listen operation.  Only effective if the listen operation
	 * has not been closed yet.
	 */
	void remove(ServerConnectionImpl connection) {
	    synchronized (lock) {
		if (!closed)
		    connections.remove(connection);
	    }
	}

	/**
	 * Check whether the server key for this listen operation is
	 * still valid and present in the server subject.
	 *
	 * @return true if the server key is still valid and present
	 *         in the server subject, false otherwise.
	 */
	private boolean checkKey() {
	    if (serverKey.isDestroyed())
		return false;
	    // caller is responsible for checking AuthenticationPermission
	    return ((Boolean) AccessController.doPrivileged(
		new PrivilegedAction() {
			public Object run() {
			    Set creds = serverSubject.getPrivateCredentials();
			    synchronized (creds) {
				for (Iterator iter = creds.iterator();
				     iter.hasNext(); )
				{
				    if (serverKey == iter.next())
					return Boolean.TRUE;
				}
			    }
			    return Boolean.FALSE;
			}
		    })).booleanValue();
	}

	/**
	 * Throttles the accept loop after ServerSocket.accept throws
	 * an exception, and decides whether to continue at all.  The
	 * current code is borrowed from the JRMP implementation; it
	 * always continues, but it delays the loop after bursts of
	 * failed accepts. <p>
	 *
	 * This method is copied from
	 * net.jini.jeri.tcp.TcpServerEndpoint. <p>
	 */
	private boolean continueAfterAcceptFailure(Throwable t) {
	    /*
	     * If we get a burst of NFAIL failures in NMSEC
	     * milliseconds, then wait for ten seconds.  This is to
	     * ensure that individual failures don't cause hiccups,
	     * but sustained failures don't hog the CPU in futile
	     * accept-fail-retry looping.
	     */
	    final int NFAIL = 10;
	    final int NMSEC = 5000;
	    long now = System.currentTimeMillis();
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
		    try {
			Thread.sleep(10000);
		    } catch (InterruptedException ignore) {
		    }
		    // no need to reset counter/timer
		}
	    }
	    return true;
	}
    }

    /**
     * A class responsible for establishing a GSS context for the
     * corresponding connection, and supply the connection to this
     * endpoint's <code>ServerConnManager</code> for asynchronous
     * processing of incoming secure remote calls.
     */
    private final class ConnectionHandler implements Runnable {
	/** Server connection handled by this handler */
	private final ServerConnectionImpl connection;

	/** Request dispatcher for the connection */
	private final RequestDispatcher dispatcher;

	/** The security context at listen time */
	private final SecurityContext securityContext;

	ConnectionHandler(ServerConnectionImpl connection,
			  RequestDispatcher dispatcher,
			  SecurityContext securityContext) {
	    this.connection = connection;
	    this.dispatcher = dispatcher;
	    this.securityContext = securityContext;
	}

	public void run() {
	    Throwable t = null;
	    try {
		AccessController.doPrivileged(new PrivilegedExceptionAction() {
			public Object run() throws Exception {
			    // JDK1.4.2 jgss requires current subject
			    // to be set right during the whole
			    // process of context establishment.
			    try {
				Subject.doAs(
				    serverSubject,
				    new PrivilegedExceptionAction() {
					public Object run()
					    throws IOException, 
					    GSSException
					{
					    connection.establishContext();
					    return null;
					}
				    });
			    } catch (PrivilegedActionException pe) {
				throw pe.getException();
			    }
			    logger.log(Level.FINE, "established " +
				       "GSSContext for {0}", connection);
			    return null;
			}
		    });
		AccessController.doPrivileged(securityContext.wrap(
		    new PrivilegedAction() {
			    public Object run() {
				serverConnManager.handleConnection(
				    connection, dispatcher);
				return null;
			    }
			}), securityContext.getAccessControlContext());
	    } catch (PrivilegedActionException pe) {
		t = pe.getException();
	    } catch (Throwable throwable) {
		t = throwable;
	    }

	    if (t != null) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    KerberosUtil.logThrow(
			logger, Levels.HANDLED, this.getClass(), "run",
			"connection handling thread {0} throws",
			new Object[] {this}, t);
		}
		connection.close();
	    }
	}

	public String toString() {
	    return "KerberosServerEndpoint.ConnectionHandler" +
		"[serverPrincipal=" + serverPrincipal +
		" localPort=" + connection.sock.getLocalPort() +
		" remotePort=" + connection.sock.getPort() + "]";
	}
    }

    /** Implementation class of the server side connection abstraction */
    private final class ServerConnectionImpl extends KerberosUtil.Connection
	implements ServerConnection
    {
	private final ListenHandleImpl listenHandle; // used in close()
	private GSSCredential clientCred;
	private Subject clientSubject;
	private InputStream istream;     // input/output streams exported to
	private OutputStream ostream;    // upper layers
	private InboundRequestHandleImpl handleWithEncryption;
	private InboundRequestHandleImpl handleWithoutEncryption;
	private final Object lock = new Object();
	private boolean closed;

	/** Construct a server connection */
	ServerConnectionImpl(Socket sock, ListenHandleImpl listenHandle)
	    throws IOException
	{
	    super(sock);
	    this.listenHandle = listenHandle;
	    connectionLogger = logger;

	    try {
		sock.setTcpNoDelay(true);
	    } catch (SocketException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    KerberosUtil.logThrow(
			logger, Levels.HANDLED, this.getClass(), "constructor",
			"failed to setTcpNoDelay option for {0}",
			new Object[] {sock}, e);
		}
	    }

	    try {
		sock.setKeepAlive(true);
	    } catch (SocketException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    KerberosUtil.logThrow(
			logger, Levels.HANDLED, this.getClass(), "constructor",
			"failed to setKeepAlive option for {0}",
			new Object[] {sock}, e);
		}
	    }

	    istream = new KerberosUtil.ConnectionInputStream(this);
	    ostream = new KerberosUtil.ConnectionOutputStream(this);
	}

	// Javadoc is inherited from the ServerConnection interface
	public InputStream getInputStream() throws IOException {
	    return istream;
	}

	// Javadoc is inherited from the ServerConnection interface
	public OutputStream getOutputStream() throws IOException {
	    return ostream;
	}

	// javadoc is inherited from the Connection interface
	public SocketChannel getChannel() {
	    return null; // does not support channel for now
	}

	// Javadoc is inherited from the ServerConnection interface
	public InboundRequestHandle processRequestData(
	    InputStream in, OutputStream out) throws IOException
	{
	    try {
		if (clientCred != null) {
		    try {
			if (clientCred.getRemainingLifetime() <= 0) {
			    close();
			    throw new SecurityException(
			    "Delegated client credential expired.");
			}
		    } catch (GSSException e) {
			close();
			SecurityException se = new SecurityException(
			    "Failed to getRemainingLifetime from the " +
			    "delegated client credential.");
			se.initCause(e);
			throw se;
		    }
		}

		if (!serverSubject.getPrincipals().contains(serverPrincipal)) {
		    throw new SecurityException(
			"serverSubject no longer contains serverPrincipal: " +
			serverPrincipal + ", failing the connection...");
		} else if (!listenHandle.checkKey()) {
		    // AuthenticationPermission has been checked in listen
		    throw new SecurityException(
			"serverSubject no longer contains the server key " +
			"or the server key has been destroyed, failing " +
			"the connection...");
		}
		
		return doEncryption ? handleWithEncryption :
		    handleWithoutEncryption;

	    } catch (SecurityException se) {
		if (logger.isLoggable(Levels.FAILED)) {
		    KerberosUtil.logThrow(
			logger, Levels.FAILED, this.getClass(),
			"processRequestData", "connection {0} throws",
			new Object[] {this}, se);
		}
		close();
		throw se;
	    }
	}

	// Javadoc is inherited from the ServerConnection interface
	public void checkPermissions(InboundRequestHandle handle) {
	    checkRequestHandle(handle);
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkAccept(sock.getInetAddress().getHostAddress(),
			       sock.getPort());
		KerberosUtil.checkAuthPermission(
		    serverPrincipal, clientPrincipal, "accept");
	    }
	}
	    
	// Javadoc is inherited from the ServerConnection interface
	public InvocationConstraints checkConstraints(
	    InboundRequestHandle handle,
	    InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    InboundRequestHandleImpl rh = checkRequestHandle(handle);

	    if (constraints == null) {
		throw new NullPointerException(
		    "constraints can not be null");
	    }
	    
	    CacheKey key = new CacheKey(rh, constraints);
	    Object result = softCache.get(key);
	    
	    if (result != null) {
		if (result instanceof UnsupportedConstraintException)
		    throw (UnsupportedConstraintException) result;
		return (InvocationConstraints) result;
	    }

	    // no cached result, has to do the whole analysis	    
	    for (Iterator iter = constraints.requirements().iterator();
		 iter.hasNext(); )
	    {
		try {
		    InvocationConstraint c =
			(InvocationConstraint) iter.next();
		    if (!KerberosUtil.isSupportableConstraint(c)) {
			UnsupportedConstraintException e =
			    new UnsupportedConstraintException(
				"A constraint unsupportable by this " +
				"endpoint has been required: " + c);
			softCache.put(key, e);
			throw e;
		    }
		    
		    if (!KerberosUtil.isSatisfiable(rh.config, c)) {
			UnsupportedConstraintException e =
			    new UnsupportedConstraintException(
				"A required constraint (" + c + ") is not " +
				"satisfied by this connection: " + this);
			softCache.put(key, e);
			throw e;
		    }
		} catch (UnsupportedConstraintException uce) {
		    if (logger.isLoggable(Levels.FAILED)) {
			KerberosUtil.logThrow(
			    logger, Levels.FAILED, this.getClass(),
			    "checkConstraints", "connection {0}\ndoes not " +
			    "satisfies {1},\nthrows",
			    new Object[] {this, constraints}, uce);
		    }
		    throw uce;
		}
	    }
	    
	    // constraints need upper layer help to be fully fulfilled
	    InvocationConstraints unfulfilledConstraints =
		InvocationConstraints.EMPTY;

	    if (KerberosUtil.containsConstraint(
		constraints.requirements(), Integrity.YES))
	    {
		unfulfilledConstraints =
		    KerberosUtil.INTEGRITY_REQUIRED_CONSTRAINTS;
	    } else if (KerberosUtil.containsConstraint(
		constraints.preferences(), Integrity.YES))
	    {
		unfulfilledConstraints =
		    KerberosUtil.INTEGRITY_PREFERRED_CONSTRAINTS;
	    }

	    softCache.put(key, unfulfilledConstraints);
	    return unfulfilledConstraints;
	}

	// javadoc is inherited from the ServerConnection interface
	public void populateContext(
	    InboundRequestHandle handle, Collection context)
	{
	    checkRequestHandle(handle);
	    Util.populateContext(context, sock.getInetAddress());
	    Util.populateContext(context, clientSubject);
	}

	// Javadoc is inherited from the ServerConnection interface
	public void close() {
	    synchronized (lock) {
		if (closed)
		    return;
		closed = true;
	    }
	    listenHandle.remove(this);
	    super.close(); // super.close() logs this event
	}

	/** Returns a string representation of this object. */
	public String toString() {
	    StringBuilder b = new StringBuilder(
		"KerberosServerEndpoint.ServerConnectionImpl[");
	    b.append("clientPrincipal=").append(clientPrincipal);
	    b.append(" serverPrincipal=").append(serverPrincipal);
	    b.append(" doEncryption=").append(doEncryption);
	    b.append(" doDelegation=").append(doDelegation);
	    b.append(" client=").append(sock.getInetAddress().getHostName());
	    b.append(":").append(sock.getPort());
	    b.append(" server=").append(sock.getLocalAddress().getHostName());
	    b.append(":").append(sock.getLocalPort());
	    b.append(']');
	    return b.toString();
	}

	/** Carry out the GSS context establishment message exchanges */
	void establishContext() throws IOException, GSSException {
            try {
                /* gssContext is defined in parent class for receiving
                incoming requests from the client */
                gssContext = gssManager.createContext(listenHandle.serverCred);
                byte[] token = null;
                while (!gssContext.isEstablished()) {
                    token = new byte[dis.readInt()];
                    dis.readFully(token);
                    token = gssContext.acceptSecContext(token, 0, token.length);
                    /*
                    * Send a token to the peer if one was generated by
                    * acceptSecContext
                    */
                    if (token != null) {
                        dos.writeInt(token.length);
                        dos.write(token);
                        dos.flush();
                    }
                }
                
                if (!gssContext.getIntegState()) {
                    // this exception is logged by caller of this method
                    throw new IOException("Established GSSContext does not " +
                            "support integrity.");
                }
                
                /*
                * Note that gssContext.getConfState() on client and
                * server side might not match each other, the meaningful
                * value will have to be acquired from the message
                * property of each token received.
                */
                doEncryption = gssContext.getConfState();
                doDelegation = gssContext.getCredDelegState();
                GSSName clientName = gssContext.getSrcName();
                clientPrincipal = new KerberosPrincipal(clientName.toString());
                if (gssContext.getCredDelegState())
                    clientCred = gssContext.getDelegCred();
                Class gssUtilClass = Class.forName("com.sun.security.jgss.GSSUtil");
                Class [] parameterTypes = new Class [] {GSSName.class, GSSCredential.class};
                
                Method createSubjectMethod = gssUtilClass.getMethod("createSubject", parameterTypes);
                Object [] args = new Object []{clientName, clientCred};
                clientSubject = (Subject) createSubjectMethod.invoke(null, args);
                clientSubject.setReadOnly();
                
                /* these handles need to be initialized after context
                establishment, which sets client principal and deleg */
                handleWithEncryption = new InboundRequestHandleImpl(true);
                handleWithoutEncryption = new InboundRequestHandleImpl(false);
            } catch (ClassNotFoundException ex) {
                throw new IOException("Unable to create client Subject", ex);
            } catch (NoSuchMethodException ex) {
                throw new IOException("Unable to create client Subject", ex);
            } catch (SecurityException ex) {
                throw new IOException("Unable to create client Subject", ex);
            } catch (IllegalAccessException ex) {
                throw new IOException("Unable to create client Subject", ex);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Unable to create client Subject", ex);
            } catch (InvocationTargetException ex) {
                throw new IOException("Unable to create client Subject", ex);
            }
	}

	/**
	 * Make sure that the passed in inbound request handle has the
	 * right type, and was previously instantiated in this
	 * connection.
	 */
	private InboundRequestHandleImpl checkRequestHandle(Object h) {
	    if (h != handleWithEncryption && h != handleWithoutEncryption) {
		throw new IllegalArgumentException(
		    "Unknown InboundRequestHandle: " + h);
	    }
	    return (InboundRequestHandleImpl) h;
	}

	private final class InboundRequestHandleImpl
	    implements InboundRequestHandle
	{
	    final Config config;

	    InboundRequestHandleImpl(boolean encry) {
		/* doEncryption is switchable, cannot be directly
		   extracted from the enclosing connection */
		config = new Config(clientPrincipal, serverPrincipal,
				    encry, doDelegation);
	    }
	}

	/** The key used for the softcache of this server endpoint. */
	private final class CacheKey {
	    
	    private final InboundRequestHandleImpl handle;
	    private final InvocationConstraints constraints;
	    
	    /** Construct a Key object */
	    CacheKey(InboundRequestHandleImpl handle,
		     InvocationConstraints constraints)
	    {
		this.handle = handle;
		this.constraints = constraints;
	    }

	    public int hashCode() {
		// identityHashCode() should be faster
		return handle.hashCode() ^
		    System.identityHashCode(constraints);
	    }
	    
	    /** Use <code>==</code> to compare content */
	    public boolean equals(Object o) {
		if (o == this) {
		    return true;
		} else if (!(o instanceof CacheKey)) {
		    return false;
		}
		CacheKey okey = (CacheKey) o;
		return handle == okey.handle &&
		    constraints == okey.constraints;
	    }
	}
    }
}
