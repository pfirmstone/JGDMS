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
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.KerberosEndpointInternalsAccess;
import org.apache.river.jeri.internal.connection.BasicConnManagerFactory;
import org.apache.river.jeri.internal.connection.ConnManager;
import org.apache.river.jeri.internal.connection.ConnManagerFactory;
import org.apache.river.jeri.internal.connection.ServerConnManager;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
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
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.jeri.kerberos.KerberosUtil.Config;
import net.jini.jeri.kerberos.KerberosUtil.ConfigIter;
import net.jini.security.AuthenticationPermission;
import net.jini.security.Security;
import net.jini.security.proxytrust.TrustEquivalence;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;

/**
 * An {@link Endpoint} implementation that uses Kerberos as the
 * underlying network security protocol to support security related
 * invocation constraints its caller specified for the corresponding
 * remote request.  Instances of this class are referred to as the
 * endpoints of the Kerberos provider, while instances of {@link
 * KerberosServerEndpoint} are referred to as the server endpoints of
 * the provider.<p>
 *
 * Instances of this class are intended to be created by the {@link
 * net.jini.jeri.BasicJeriExporter} class when it calls {@link
 * net.jini.jeri.ServerEndpoint#enumerateListenEndpoints
 * enumerateListenEndpoints} on instances of
 * <code>KerberosServerEndpoint</code>. <p>
 *
 * The {@link KerberosTrustVerifier} may be used for establishing
 * trust in remote proxies that use instances of this class. <p>
 *
 * This class supports at least the following standard constraints: <p>
 *
 * <ul>
 * <li>{@link net.jini.core.constraint.Integrity#YES}
 * <li>{@link net.jini.core.constraint.Confidentiality}
 * <li>{@link net.jini.core.constraint.ClientAuthentication#YES}
 * <li>{@link net.jini.core.constraint.ConnectionAbsoluteTime}
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
 * </ul> <p>
 *
 * An endpoint of this provider uses Kerberos Ticket Granting Tickets
 * (TGTs), which are stored as private credentials in the {@link
 * Subject} associated with the access control context of the current
 * thread, to authenticate the caller of the remote request to the
 * server. <p>
 *
 * A TGT is an instance of {@link KerberosTicket} whose server
 * principal is of the form <code>"krbtgt/REALM1@REALM2"</code>.  The
 * client principal of the TGT indicates what principal the endpoint
 * can use the TGT to authenticate the caller as to the server. <p>
 *
 * Instances of this class contain a server host name, a server TCP
 * port number, a server principal, as well as an optional {@link
 * SocketFactory} for customizing the type of <code>Socket</code> to
 * use. <p>
 *
 * A <code>SocketFactory</code> used with instances of this class
 * should be serializable, and should implement {@link Object#equals
 * Object.equals} to return <code>true</code> when passed an instance
 * that represents the same (functionally equivalent) socket
 * factory. <p>
 *
 * To make a remote request through an endpoint of this provider, the
 * caller or initiator of the request supplies the endpoint with a set
 * of <code>InvocationConstraints</code>.  The client principal used
 * for the request is a <code>KerberosPrincipal</code> that appears in
 * the principal set of the current subject, allowable by the given
 * set of constraints, whose corresponding TGT is in the private
 * credential set of the subject, and the <code>connect</code>
 * <code>AuthenticationPermission</code> has been granted to the
 * current access control context with the client principal as the
 * <code>local</code> principal and the <code>serverPrincipal</code>
 * contained in this endpoint as the <code>peer</code> principal. <p>
 *
 * If the set of constraints for the request specifies that encryption
 * and/or delegation is required or preferred, they will be enforced
 * by the provider.  If encryption is unspecified in the constraints,
 * the provider may or may not encrypt messages exchanged with the
 * server.  If delegation is unspecified in the constraints, the
 * provider will not do delegation for the request.  For delegation to
 * happen, the TGT for the request has to be forwardable. <p>
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
 * This Kerberos provider implementation uses the <a
 * href="http://www.ietf.org/rfc/rfc2853.txt"> Java(TM) GSS-API</a> to
 * provide the underlying Kerberos network authentication protocol
 * support. <p>
 *
 * The implementation does not automatically renew any renewable TGTs
 * in the <code>Subject</code> corresponding to any outbound
 * request. The assumption is that an endpoint of this provider should
 * merely be a consumer of the principals and credentials of the
 * <code>Subject</code>, and never change its content. But if new TGTs
 * are added into the <code>Subject</code> or old TGTs in the
 * <code>Subject</code> are renewed by means outside this provider,
 * the endpoint will pick up and use these new TGTs for new requests
 * after the old ones have expired. <p>
 *
 * This class uses the following {@link Logger} to log information at
 * the following logging levels: <p>
 *
 * <table border="1" cellpadding="5" summary="Describes logging to the
 *     client logger performed by endpoint classes in this package at
 *     different logging levels">
 * 
 *     <caption halign="center" valign="top"><b><code>
 * 	net.jini.jeri.kerberos.client</code></b></caption>
 * 
 *     <tr> <th scope="col"> Level <th scope="col"> Description
 * 
 *     <tr> <td> {@link java.util.logging.Level#WARNING WARNING}
 *          <td> failure to register with discovery provider
 *     <tr> <td> {@link org.apache.river.logging.Levels#FAILED FAILED}
 *          <td> problem to support constraint requirements, connect to
 *               server through socket, establish {@link
 *               org.ietf.jgss.GSSContext} to server over established
 *               connections, or wrap/unwrap GSS tokens
 *     <tr> <td> {@link org.apache.river.logging.Levels#HANDLED HANDLED}
 *          <td> exceptions caught attempting to set TCP no delay or keep
 *               alive properties on sockets, connect a socket, or reuse
 *               a connection
 *     <tr> <td> {@link java.util.logging.Level#FINE FINE}
 *          <td> endpoint creation, {@link
 *               net.jini.jeri.Endpoint#newRequest newRequest}
 *               invocation, request handle creation, connection
 *               configuration decesions, socket creation, connection
 *               open/close, connection reuse decesions,
 *               <code>GSSContext</code> establishment
 *     <tr> <td> {@link java.util.logging.Level#FINEST FINEST}
 *          <td> data message encoding/decoding using
 *               <code>GSSContext</code>
 * </table> <p>
 *
 * Instances of this class recognize the following system properties:
 * <p>
 * 
 * <ul>
 * <li>org.apache.river.jeri.kerberos.KerberosEndpoint.minGssContextLifetime -
 *     Minimum number of seconds of remaining lifetime a {@link
 *     GSSContext} of an existing connection has to have before it can
 *     be considered as a candidate connection to be chosen for a new
 *     request. The default is 30.
 * <li>org.apache.river.jeri.kerberos.KerberosEndpoint.maxGssContextRetries -
 *     <a href="http://www.ietf.org/rfc/rfc1510.txt">RFC 1510</a>
 *     specifies that if a KDC or server receives two authenticators
 *     with the same client and server pair and timestamps of the
 *     same microsecond, the second will be considered a replay
 *     and will be rejected.  This means if multiple session ticket
 *     requests of the same client and server principal pair and
 *     microsecond timestamps are received at a KDC, only the first
 *     one will succeed, and the rest will be considered replays
 *     and will be rejected by the KDC.  For this reason, the Kerberos
 *     provider catches the "replay" exception and retries the
 *     corresponding <code>GSSContext</code> initialization
 *     handshake.  This system property controls the maximum number
 *     of retries a <code>KerberosEndpoint</code> will conduct.  The
 *     default is 3.
 * </ul> <p>
 *
 * 
 * @see KerberosServerEndpoint
 * @see KerberosTrustVerifier
 * @since 2.0
 */
public final class KerberosEndpoint
    implements Endpoint, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = -880347439811805543L;

    /** JERI Kerberos client transport logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.kerberos.client");

    /** 
     * Name or ip address of the server host.
     *
     * @serial
     */
    private final String serverHost;

    /**
     * Port that the server is listening on for incoming connection
     * requests.
     *
     * @serial
     */
    private final int serverPort;

    /**
     * Principal of which the server is capable of authenticating as.
     *
     * @serial
     */
    private final KerberosPrincipal serverPrincipal;

    /**
     * The socket factory that this <code>KerberosEndpoint</code> uses
     * to create <code>java.net.Socket</code> objects.
     *
     * @serial
     */
    private final SocketFactory csf;

    /** Internal lock for class-wide synchronizaton */
    private static final Object classLock = new Object();

    /** GSSManager instance used by all endpoints in a JVM */
    private static GSSManager gssManager;

    /**
     * Maximum number of entries allowed in the soft cache of a
     * Kerberos endpoint. The default is 64.
     */
    private static final int maxCacheSize =
	((Integer) AccessController.doPrivileged(
	    new GetIntegerAction(
		"org.apache.river.jeri.kerberos.KerberosEndpoint.maxCacheSize",
		64))).intValue();

    /**
     * Minimum number of seconds of life time a {@link GSSContext} of
     * an existing connection has to have before it can be considered
     * as a candidate connection to be chosen for a new
     * request. The default is 30.
     */
    private static final int minGssContextLifetime =
	((Integer) AccessController.doPrivileged(
	    new GetIntegerAction(
		"org.apache.river.jeri.kerberos.KerberosEndpoint." +
		"minGssContextLifetime", 30))).intValue();

    /** Maximum retries for initial {@link GSSContext} handshake. */
    private static final int maxGssContextRetries =
	((Integer) AccessController.doPrivileged(
	    new GetIntegerAction(
		"org.apache.river.jeri.kerberos.KerberosEndpoint." +
		"maxGssContextRetries", 3))).intValue();

    /**
     * A cache maintains soft reference to its values. This cache is
     * keyed by security constraints and subject used for the
     * corresponding request, its value entries encapsulate the result
     * of the analysis of the constraints and principals in the
     * subject, without checking permissions and private credentials.
     */
    private transient KerberosUtil.SoftCache softCache;

    /**
     * The <code>ConnectionEndpoint</code> this endpoint passes
     * to its <code>connManager</code> to create connections.
     */
    private transient ConnectionEndpointImpl connectionEndpoint;

    /**
     * The <code>ConnManager</code> this endpoint uses to create
     * connections.
     */
    private transient ConnManager connManager;

    /**
     * If set to true, causes the endpoint not to connect sockets it
     * obtains from its socket factory.
     */
    private transient boolean disableSocketConnect;

    /**
     * Weak set of canonical instances; in order to use WeakHashMap,
     * maps canonical instances to weak references to themselves.
     */
    private static final Map internTable = new WeakHashMap(5);

    /* Register a back door interface for use by discovery providers. */
    static {
	KerberosEndpointInternals.registerDiscoveryBackDoor();
    }

    //-----------------------------------
    //           constructors
    //-----------------------------------

    /**
     * Creates an endpoint of this Kerberos provider.
     *
     * @param serverHost the name or ip address of the server host
     *        this endpoint will connect to
     * @param serverPort the server port
     * @param serverPrincipal principal the server can authenticate as
     * @param csf the <code>SocketFactory</code> to use for this
     *        <code>KerberosEndpoint</code>, or <code>null</code>
     * @throws NullPointerException if <code>serverHost</code> or
     *         <code>serverPrincipal</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>1</code> to
     *         <code>65535</code>
     */
    private KerberosEndpoint(String serverHost, int serverPort,
			     KerberosPrincipal serverPrincipal,
			     SocketFactory csf)
    {
	if (serverHost == null)
	    throw new NullPointerException("serverHost is null");

	if (serverPort <= 0 || serverPort > 0xFFFF) {
	    throw new IllegalArgumentException(
	        "server port number out of range 1-65535: serverPort = " +
		serverPort);
	}

	if (serverPrincipal == null)
	    throw new NullPointerException("serverPrincipal is null");

	this.serverHost = serverHost;
	this.serverPort = serverPort;
	this.serverPrincipal = serverPrincipal;
	this.csf = csf;
	logger.log(Level.FINE, "created {0}", this);
    }

    //-----------------------------------
    //          public methods
    //-----------------------------------

    /**
     * Returns a <code>KerberosEndpoint</code> instance for the given
     * server host name, TCP port number, and server
     * principal. Internally this endpoint uses {@link Socket} objects
     * to connect to its server endpoint.
     *
     * @param serverHost the host for the endpoint to connect to
     * @param serverPort the TCP port on the given host for the
     *        endpoint to connect to
     * @param serverPrincipal principal the server can authenticate as
     * @return a <code>KerberosEndpoint</code> instance
     * @throws NullPointerException if <code>serverHost</code> or
     *         <code>serverPrincipal</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>1</code> to
     *         <code>65535</code>
     */
    public static KerberosEndpoint
	getInstance(String serverHost, int serverPort,
		    KerberosPrincipal serverPrincipal)
    {
	return intern(new KerberosEndpoint(serverHost, serverPort,
					   serverPrincipal, null));
    }

    /**
     * Returns a <code>KerberosEndpoint</code> instance for the given
     * server host name, TCP port number, server principal, and
     * <code>SocketFactory</code>.
     *
     * <p>If the socket factory argument is <code>null</code>, then
     * this endpoint will create {@link Socket} objects directly.
     *
     * @param serverHost the host for the endpoint to connect to
     * @param serverPort the TCP port on the given host for the
     *        endpoint to connect to
     * @param serverPrincipal principal the server can authenticate as
     * @param csf the <code>SocketFactory</code> to use for this
     *        <code>KerberosEndpoint</code>, or <code>null</code>
     * @return a <code>KerberosEndpoint</code> instance
     * @throws NullPointerException if <code>serverHost</code> or
     *         <code>serverPrincipal</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>serverPort</code> is
     *         not in the range of <code>1</code> to
     *         <code>65535</code>
     *
     * @see javax.net.SocketFactory
     */
    public static KerberosEndpoint
	getInstance(String serverHost, int serverPort,
		    KerberosPrincipal serverPrincipal,
		    SocketFactory csf)
    {
	return intern(new KerberosEndpoint(serverHost, serverPort,
					   serverPrincipal, csf));
    }

    /**
     * Returns the server host that this endpoint connects to.
     *
     * @return the server host that this endpoint connects to
     */
    public String getHost() {
	return serverHost;
    }

    /**
     * Returns the TCP port that this endpoint connects to.
     *
     * @return the TCP port that this endpoint connects to
     */
    public int getPort() {
	return serverPort;
    }

    /**
     * Returns the principal this endpoint requires the server
     * to authenticate as.
     *
     * @return the server principal
     */
    public KerberosPrincipal getPrincipal() {
	return serverPrincipal;
    }

    /**
     * Returns the <code>SocketFactory</code> that this endpoint uses
     * to create {@link Socket} objects.
     *
     * @return the socket factory that this endpoint uses to create
     *         sockets, or <code>null</code> if no factory is used
     */
    public SocketFactory getSocketFactory() {
	return csf;
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
     * <code>SocketFactory</code> of this <code>KerberosEndpoint</code>
     * (which produced this iterator) if non-<code>null</code>, or it
     * will create a <code>Socket</code> directly otherwise.
     *
     * <p>When the implementation needs to connect a
     * <code>Socket</code>, if the host name to connect to (this
     * <code>KerberosEndpoint</code>'s host name) resolves to multiple
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
     * <code>KerberosEndpoint</code>'s host and <code>-1</code> for the
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
     * communication, the current security context must have the
     * <code>KerberosPrincipal</code>, and permissions required to use it,
     * that would be necessary if the the connection were being created.  In
     * addition, it must be possible to invoke <code>checkConnect</code> in the
     * current security context with this <code>KerberosEndpoint</code>'s host
     * and <code>-1</code> for the port without resulting in a
     * <code>SecurityException</code>, and it also must be possible to invoke
     * <code>checkConnect</code> with the remote IP address and port
     * of the <code>Socket</code> without resulting in a
     * <code>SecurityException</code> (if the remote socket address is
     * unresolved, its host name is used instead).  If no existing
     * connection satisfies these requirements, then this method must
     * behave as if there are no existing connections.
     *
     * </ul>
     *
     * <p>Throws {@link java.util.NoSuchElementException}
     * if this iterator does not support making another attempt to communicate
     * the request (that is, if <code>hasNext</code> would return
     * <code>false</code>).
     *
     * <p>Throws {@link IOException} if an I/O exception occurs while
     * performing this operation, such as if a connection attempt
     * timed out or was refused or there are unsupported or conflicting
     * constraints.
     *
     * <p>Throws {@link SecurityException} if there is a security
     * manager and an invocation of its <code>checkConnect</code>
     * method fails.  Also, a <code>SecurityException</code> may be thrown if
     * the caller does not have the appropriate
     * <code>AuthenticationPermission</code>.
     *
     * </blockquote>
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public OutboundRequestIterator newRequest(
	InvocationConstraints constraints)
    {
        if (constraints == null)
	    throw new NullPointerException("constraints cannot be null");

	logger.log(Level.FINE, "newRequest requested with constraints:\n" +
		   "{0}", constraints);

	Subject clientSubject = (Subject) Security.doPrivileged(
	    new PrivilegedAction() {
		    public Object run() {
			return Subject.getSubject(
			    AccessController.getContext());
		    }
		});

	CacheKey key = new CacheKey(clientSubject, constraints);
	RequestHandleImpl handle = (RequestHandleImpl) softCache.get(key);

	if (handle == null || !handle.reusable(clientSubject)) {
	    handle = new RequestHandleImpl(clientSubject, constraints);
	    
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "new request handle has been " +
			   "constructed:\n{0}", new Object[] {handle});
	    }
	    
	    softCache.put(key, handle);
	}

	return connManager.newRequest(handle);
    }

    /**
     * Returns <code>true</code> if the argument is an instance of
     * <code>KerberosEndpoint</code> with the same values for server
     * principal, server host, and port; and either both this instance
     * and the argument have <code>null</code> socket factories, or
     * the factories have the same actual class and are equal; and
     * returns <code>false</code> otherwise.
     */
    public boolean checkTrustEquivalence(Object obj) {
	return equals(obj);
    }

    /** Returns a hash code value for this object. */
    public int hashCode() {
	return getClass().getName().hashCode() ^
	    serverPrincipal.hashCode() ^ serverHost.hashCode() ^ serverPort ^
	    (csf != null ? csf.hashCode() : 0);
    }

    /**
     * Two instances of this class are equal if they contain the same
     * server principal, host, and port, and their socket factories
     * are both <code>null</code> or have the same actual class and
     * are equal.
     */
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof KerberosEndpoint)) {
	    return false;
	}
	KerberosEndpoint oep = (KerberosEndpoint) obj;
	return serverPrincipal.equals(oep.serverPrincipal) && 
	    serverHost.equals(oep.serverHost) &&
	    serverPort == oep.serverPort &&
	    Util.sameClassAndEquals(csf, oep.csf);
    }

    /** Returns a string representation of this endpoint. */
    public String toString() {
	return "KerberosEndpoint[serverHost=" + serverHost + 
	    " serverPort=" + serverPort + 
	    " serverPrincipal=" + serverPrincipal + 
	    (csf == null ? "" : "csf = " + csf.toString()) + "]";
    }

    //-----------------------------------
    //          private methods
    //-----------------------------------

    /** Resolves deserialized instance to equivalent canonical instance. */
    private Object readResolve() {
	return intern(this);
    }

    /**
     * Read in a serialized instance and check that the deserialized
     * instance has the right fields.
     */
    private void readObject(ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
	ois.defaultReadObject();
	
	if (serverHost == null) {
	    throw new InvalidObjectException("serverHost is null");
	} else if (serverPort <= 0 || serverPort > 0xFFFF) {
	    throw new InvalidObjectException(
		"server port number out of range 1-65535: " +
		"serverPort = : " + serverPort);
	} else if (serverPrincipal == null) {
	    throw new InvalidObjectException("serverPrincipal is null");
	}
    }

    /** Returns canonical instance equivalent to given instance. */
    private static KerberosEndpoint intern(KerberosEndpoint endpoint) {
	synchronized (internTable) {
	    Reference ref = (WeakReference) internTable.get(endpoint);
	    if (ref != null) {
		KerberosEndpoint canonical = (KerberosEndpoint) ref.get();
		if (canonical != null) {
		    return canonical;
		}
	    }

	    // construct these only if endpoint passes intern test
	    endpoint.softCache =
		new KerberosUtil.SoftCache(maxCacheSize);
	    endpoint.connectionEndpoint =
		endpoint.new ConnectionEndpointImpl();
	    endpoint.connManager = new BasicConnManagerFactory().create(
		endpoint.connectionEndpoint);

	    internTable.put(endpoint, new WeakReference(endpoint));
	    return endpoint;
	}
    }

    /** 
     * Make sure that the passed in endpoint instance equals the
     * enclosing endpoint instance.
     */
    private void checkEndpoint(KerberosEndpoint ep) {
	if (!this.equals(ep)) {
	    throw new IllegalArgumentException(
		"endpoint mismatch, this endpoint is: " + this +
		", passed in endpoint is: " + ep);
	}
    }

    /**
     * Make sure that the passed in request handle has the right type,
     * and was previously instantiated in this endpoint.
     */
    private RequestHandleImpl checkRequestHandleImpl(Object h) {
	if (h == null) {
	    throw new NullPointerException("Handle cannot be null");
	} else if (!(h instanceof RequestHandleImpl)) {
	    throw new IllegalArgumentException("Unexpected handle type: " + h);
	}
	RequestHandleImpl rh = (RequestHandleImpl) h;
	checkEndpoint(rh.getEndpoint());
	return rh;
    }

    /**
     * Make sure that the passed in context has the right type, and
     * was previously instantiated in this endpoint.
     */
    private ConnectionImpl checkConnection(Object c) {
	if (!(c instanceof ConnectionImpl)) {
	    throw new IllegalArgumentException(
		"Expected connection type is " + ConnectionImpl.class + 
		", while " + c + " is passed in.");
	}
	ConnectionImpl conn = (ConnectionImpl) c;
	checkEndpoint(conn.getEndpoint());
	return conn;
    }
    
    //-----------------------------------
    //       private inner classes
    //-----------------------------------

    /** Support EndpointInternals, for use by discovery providers */
    private static final class KerberosEndpointInternals
	implements EndpointInternals
    {
	/** Register back door. */
	static void registerDiscoveryBackDoor() {
	    final KerberosEndpointInternals backDoor =
		new KerberosEndpointInternals();
	    try {
		Security.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			KerberosEndpointInternalsAccess.set(backDoor);
			return null;
		    }
		});
	    } catch (Throwable t) {
		logger.log(Level.WARNING,
			   "Problem registering with discovery provider", t);
	    }
	}

	/* -- Implement EndpointInternals -- */

	public void disableSocketConnect(Endpoint endpoint) {
	    ((KerberosEndpoint) endpoint).disableSocketConnect = true;
	}

	public void setConnManagerFactory(Endpoint endpoint,
					  ConnManagerFactory factory)
	{
	    KerberosEndpoint kep = (KerberosEndpoint) endpoint;
	    kep.connManager = factory.create(kep.connectionEndpoint);
	}

	public void setServerConnManager(ServerEndpoint endpoint,
					 ServerConnManager manager)
	{
	    KerberosServerEndpoint ksep = (KerberosServerEndpoint) endpoint;
	    ksep.serverConnManager = manager;
	}

	public InvocationConstraints getUnfulfilledConstraints(
	    OutboundRequestHandle handle)
	{
	    return ((RequestHandleImpl) handle).unfulfilledConstraints;
	}
    }

    // error code constants
    private static final int NO_ERROR = -1;
    private static final int UNSUPPORTABLE_CONSTRAINT_REQUIRED = 0;
    private static final int NULL_SUBJECT = 1;
    private static final int NO_CLIENT_PRINCIPAL = 2;
    private static final int UNSATISFIABLE_CONSTRAINT_REQUIRED = 3;

    private static final String[] ERROR_STRINGS = {
	"UNSUPPORTABLE_CONSTRAINT_REQUIRED", "NULL_SUBJECT",
	"NO_CLIENT_PRINCIPAL", "UNSATISFIABLE_CONSTRAINT_REQUIRED"};

    /** <code>OutboundRequestHandle</code> implementation */
    private final class RequestHandleImpl implements OutboundRequestHandle {

	/** Subject from which private credentials will be extracted */
	private Subject clientSubject;

	/** Constraints of this request handle */
	private InvocationConstraints constraints;

	/** True if the subject is readonly when this handle is instantiated */
	private boolean subjectReadOnly;

	/**
	 * In case of subject is not readonly, snapshot its Kerberos
	 * principals
	 */
	private Set subjectClientPrincipals;

	/**
	 * The set of Kerberos principals allowed by the constraint
	 * requirements and found in the principal set of the subject.
	 */
	private Set clientPrincipals;

	/**
	 * Error code of this request handle.  A request will not
	 * succeed if its handle's errorCode != NO_ERROR.
	 */
	private int errorCode = NO_ERROR;

	/**
	 * The message explains the reason of the failure, later on an
	 * <code>UnsupportedConstraintException</code> will be
	 * instantiated using this message and conditionally thrown to
	 * the caller, depending on whether the caller has the
	 * "getSubject" <code>AuthPermission</code>.
	 */
	private String detailedExceptionMsg;

	/**
	 * Set of configurations that can satisfy the given set of
	 * constraints using principals in the current subject.  The
	 * set is composed without checking the private credentials of
	 * the subject and AuthenticationPermissions of the caller.
	 */
	private Config[] configs;

	/**
	 * Constraints that must be partially or fully implemented by
	 * higher layers for an outbound request using this handle.
	 */
	private InvocationConstraints unfulfilledConstraints;

	/** Absolute time by when the connection must be established */
        long connectionAbsoluteTime;

	/**
	 * Construct a <code>RequestHandleImpl</code>. <p>
	 *
	 * For each outgoing request, the computation took to
         * determine the {@link KerberosUtil.Config} for the {@link
         * ConnectionImpl Connection} carrying it is divided into two
         * phases.  The first phase includes steps that no
         * <code>AuthenticationPermission</code> checks are needed,
         * which are done once for each constraints and subject pair
         * in this constructor.  The second phase contains steps that
         * require <code>AuthenticationPermission</code> checks, which
         * are done for each request in {@link
         * RequestHandleImpl#getConfigs}.  All problems, even
         * encountered in the first phase, are reported in the second
         * phase in <code>getConfigs</code>. <p>
	 *
	 * The computation steps taken in phase one are listed as the
	 * following:
	 *
	 * <ul>
	 * <li> Check for unsupportable requirements
	 * <li> Determine client principal candidate set based on
	 *      client principal constraints in requirements and
	 *      current subject
	 * <li> Generate all possible <code>Configs</code> based on
	 *      the set of client principal candidates, and whether
	 *      encryption and delegation are mentioned/allowed by the
	 *      constraints
	 * <li> Pass each <code>Config</code> through the constraints
	 *      and filter out those that conflict with requirements
	 * <li> Reorder the remaining <code>Config</code> list by
	 *      preferences
	 * </ul> <p>
	 *
	 * @param clientSubject the client subject that contains
	 *        client principals and TGTs, can not be
	 *        <code>null</code>.
	 * @param constraints the security constraint set, can not be
	 *        <code>null</code>
	 */
	RequestHandleImpl(Subject clientSubject,
			  InvocationConstraints constraints)
	{
	    /* unsupportable constraint has to be checked before any
	       other security sensitive things are checked, so a
	       detailed exception regarding to it can always be thrown
	       regardless whether the caller has the getSubject
	       permission */
	    for (Iterator iter = constraints.requirements().iterator();
		 iter.hasNext(); )
	    {
		InvocationConstraint c = (InvocationConstraint) iter.next();
		if (!KerberosUtil.isSupportableConstraint(c)) {
		    errorCode = UNSUPPORTABLE_CONSTRAINT_REQUIRED;
		    detailedExceptionMsg = "A constraint unsupportable by " +
			"this endpoint has been required: " + c;
		    return;
		}
	    }

	    /* All Kerberos principals allowed by the constraints.  If
	       the resulting set is empty, it means no client min/max
	       principal constraints found in the constraints, instead
	       of no principals allowed. */
	    clientPrincipals = new HashSet();
	    for (Iterator iter = constraints.requirements().iterator();
		 iter.hasNext(); )
	    {
		if (!KerberosUtil.collectCpCandidates(
		    (InvocationConstraint) iter.next(),
		    clientPrincipals))
		{
		    errorCode = UNSUPPORTABLE_CONSTRAINT_REQUIRED;
		    detailedExceptionMsg = "Client principal constraint " +
			"related conflicts found in the given set of " +
			"constraints: " + constraints;
		    return;
		}
	    }

	    if (clientSubject == null) {
		errorCode = NULL_SUBJECT;
		detailedExceptionMsg = "JAAS login has not been done " +
		    "properly, the subject associated with the current " +
		    "AccessControlContext is null.";
		return;
	    }

	    this.clientSubject = clientSubject;
	    this.constraints = constraints;

	    subjectReadOnly = clientSubject.isReadOnly();
	    subjectClientPrincipals = getClientPrincipals(clientSubject);

	    if (subjectClientPrincipals.size() == 0) {
		errorCode = NO_CLIENT_PRINCIPAL;
		detailedExceptionMsg = "JAAS login has not been done " +
		    "properly, the subject associated with the current " +
		    "AccessControlContext contains no KerberosPrincipal.";
		return;
	    }

	    if (clientPrincipals.size() > 0) {
		clientPrincipals.retainAll(subjectClientPrincipals);
	    } else {
		clientPrincipals = subjectClientPrincipals;
	    }

	    boolean canDeleg = false;
	    if (KerberosUtil.containsConstraint(
		constraints.requirements(), Delegation.YES) ||
		KerberosUtil.containsConstraint(
		    constraints.preferences(), Delegation.YES))
	    {
		canDeleg = true;
	    }

	    // enumerate all possible configs and filter them by constraints
	    ArrayList configArr = new ArrayList();
	  outer:
	    for (ConfigIter citer = new ConfigIter(
		     clientPrincipals, serverPrincipal, canDeleg);
		 citer.hasNext(); )
	    {
		Config config = citer.next();
		for (Iterator jter = constraints.requirements().iterator();
		     jter.hasNext(); )
		{
		    InvocationConstraint c =
			(InvocationConstraint) jter.next();
		    if (!KerberosUtil.isSatisfiable(config, c))
			continue outer;
		}
		configArr.add(config);
	    }

	    if (configArr.size() == 0) {
		errorCode = UNSATISFIABLE_CONSTRAINT_REQUIRED;
		detailedExceptionMsg = "Constraints unsatisfiable by this " +
		    "endpoint with the current subject have been required: " +
		    constraints + ", while the KerberosPrincipal set of " +
		    "the subject is: " + subjectClientPrincipals;
		return;
	    }

	    configs = (Config[]) configArr.toArray(
		new Config[configArr.size()]);

	    // reorder configs by the num of preferences a config can satisfy
	    for (int i = 0; i < configs.length; i++) {
		for (Iterator iter = constraints.preferences().iterator();
		     iter.hasNext(); )
	        {
		    InvocationConstraint c =
			(InvocationConstraint) iter.next();
		    if (KerberosUtil.isSatisfiable(configs[i], c))
			configs[i].prefCount++;
		}
	    }
	    Arrays.sort(configs, new Comparator() {
		    public int compare(Object o1, Object o2) {
			Config config1 = (Config) o1;
			Config config2 = (Config) o2;
			// sort to descending order by prefCount
			return config2.prefCount - config1.prefCount;
		    }
		});

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
	    } else {
		unfulfilledConstraints = InvocationConstraints.EMPTY;
	    }

	    connectionAbsoluteTime = Math.min(
		computeConnectionTimeLimit(constraints.requirements()),
		computeConnectionTimeLimit(constraints.preferences()));
	}

	/** Returns a string representation of this request handle. */
	public String toString() {
	    StringBuffer b = new StringBuffer(
		"KerberosEndpoint.RequestHandleImpl[\n");
	    if (errorCode != NO_ERROR) {
		b.append("errorCode=" + ERROR_STRINGS[errorCode]);
		b.append(" errorExceptionMsg=" + detailedExceptionMsg);
	    } else {
		b.append("constraints=" + constraints);
		b.append("\nprincipalsInSubject=" + subjectClientPrincipals);
		b.append("\nallowedConfigs=[\n");
		if (configs.length > 0)
		    b.append(configs[0]);
		for (int i = 1; i < configs.length; i++) {
		    b.append(",\n" + configs[i]);
		}
		b.append("],");
		b.append("\nunfulfilledConstraints=" + unfulfilledConstraints);
		b.append("\nconnectionAbsoluteTime=");
		if (connectionAbsoluteTime == Long.MAX_VALUE) {
		    b.append("NO_LIMIT");
		} else {
		    b.append(new Date(connectionAbsoluteTime));
		}
	    }
	    b.append(']');
	    return b.toString();
	}

	/**
	 * Check whether this cached request handle can be used for
	 * the given subject. It is assumed that the caller has
	 * already checked <code>==</code> on both the security
	 * constraints and subject.
	 */
	boolean reusable(Subject subject) {
	    if (subject == null || subjectReadOnly)
		return true; // null subject means error, reuse handle
	    Set cps = getClientPrincipals(subject);
	    return cps.equals(subjectClientPrincipals);
	}

	/**
	 * Get a list of satisfiable configurations.  Elements of the
	 * list is from the configs array of this handle, with those
	 * who failed their corresponding AuthenticationPermission
	 * and private credential (TGT) checks filtered out.  The
	 * returned list is ordered by decreasing preference.
	 *
	 * @return a list of satisfiable configurations in decreasing
	 *         preference order.
	 * @throws UnsupportedConstraintException if the caller has
	 *         required unsupported constraints, or there are
	 *         conflicts or unsatisfiable constraint in the
	 *         requirements, or the JAAS login has not been done
	 *         (Subject.getSubject(AccessController.getContext())
	 *         returns <code>null</code>), or no appropriate
	 *         Kerberos principal and corresponding TGT allowed by
	 *         the requirements can be found in the current
	 *         subject.  If the caller has not been granted
	 *         <code>javax.security.auth.AuthPermission("getSubject")
	 *         </code> and cause is not unsupported constraints
	 *         being required, the exception message will be
	 *         generic and enumerate all these possible causes.
	 *         Otherwise, the message will spell out the reason
	 *         caused the exception.
	 * @throws SecurityException if there is a security manager
	 *         and the caller has
	 *         <code>javax.security.auth.AuthPermission("getSubject")
	 *         </code> but not any
	 *         <code>AuthenticationPermission</code> whose local
	 *         principal is a member of the client principal
	 *         candidate set.  The action of the
	 *         <code>AuthenticationPermission</code> is either
	 *         <code>connect</code> or <code>delegate</code>,
	 *         determined by the requirements of the constraints.
	 */
        List getConfigs() throws UnsupportedConstraintException {

	    if (errorCode != NO_ERROR) {
		throw new UnsupportedConstraintException(
		    detailedExceptionMsg);
	    }

	    KerberosTicket[] tickets =
		(KerberosTicket[]) AccessController.doPrivileged(
		    new PrivilegedAction() {
			    public Object run() {
				return getTickets();
			    }
			});

	    ArrayList configList = new ArrayList(configs.length);

	    /* This illustrates how a detailed failure msg is derived:
	     *
	     *               |<-- stepsFromSuccess -->|
	     *
	     *                               TGT.forwardable
	     *                      TGT.yes
	     *            perm.yes           TGT.unforwardable
	     *                      TGT.no
	     * deleg.yes 
	     *            perm.no
	     *
	     *-------------------------------------------------------
	     *
	     *                      TGT.yes
	     *            perm.yes
	     *                      TGT.no
	     * deleg.no 
	     *            perm.no
	     *
	     */
	    int delegYesStepsFromSuccess = 3;
	    KerberosPrincipal delegYesCp = null;

	    int delegNoStepsFromSuccess = 2;
	    KerberosPrincipal delegNoCp = null;

	    HashMap hasPermMap = new HashMap();
	    for (int i = 0; i < configs.length; i++) {
		AuthenticationPermission perm = getAuthenticationPermission(
		    configs[i].clientPrincipal, configs[i].deleg);
		Boolean hasPerm = (Boolean) hasPermMap.get(perm);
		if (hasPerm == null) {
		    try {
			KerberosUtil.checkAuthPermission(perm);
			hasPermMap.put(perm, Boolean.TRUE); // check succeed
		    } catch (SecurityException e) {
			hasPermMap.put(perm, Boolean.FALSE); // check failed
			continue;
		    }
		} else if (hasPerm == Boolean.FALSE) {
		    continue;
		} // else: permission check has been done and succeeded

		if (configs[i].deleg) {
		    if (delegYesStepsFromSuccess > 2) {
			delegYesStepsFromSuccess = 2; // record the 1st
			delegYesCp = configs[i].clientPrincipal;
		    }
		    KerberosTicket t = findTicket(
			tickets, configs[i].clientPrincipal);
		    if (t != null) {
			if (delegYesStepsFromSuccess > 1) {
			    delegYesStepsFromSuccess = 1; // record the 1st
			    delegYesCp = configs[i].clientPrincipal;
			}
			if (t.isForwardable())
			    configList.add(configs[i]);
		    }
		} else {
		    if (delegNoStepsFromSuccess > 1) {
			delegNoStepsFromSuccess = 1; // record the 1st
			delegNoCp = configs[i].clientPrincipal;
		    }
		    if (findTicket(tickets, configs[i].clientPrincipal) !=
			null)
		    {
			configList.add(configs[i]);
		    }
		}
	    }

	    if (configList.size() == 0) { // no valid config found
		if (delegNoStepsFromSuccess < delegYesStepsFromSuccess) {
		    switch (delegNoStepsFromSuccess) {
		    case 1:
			throw new UnsupportedConstraintException(
			    "JAAS login has not been done properly, the " +
			    "subject associated with the current " +
			    "AccessControlContext does not contain a valid " +
			    "TGT for " + delegNoCp.getName());
		    case 2:
			throw new SecurityException(
			    "Caller does not have any of the following " +
			    "acceptable permissions: " +
			    hasPermMap.keySet());
		    default:
			throw new AssertionError("should not reach here");
		    }
		} else {
		    switch (delegYesStepsFromSuccess) {
		    case 1:
			throw new UnsupportedConstraintException(
			    "JAAS login has not been done properly, the " +
			    "subject associated with the current " +
			    "AccessControlContext contains a valid TGT for " +
			    delegYesCp.getName() + ", but the TGT is not " +
			    "forwardable.");
		    case 2:
			throw new UnsupportedConstraintException(
			    "JAAS login has not been done properly, the " +
			    "subject associated with the current " +
			    "AccessControlContext does not contain a valid " +
			    "TGT for " + delegYesCp.getName());
		    default:
			throw new AssertionError("should not reach here");
		    }
		}
	    }

	    return configList;
	}	

	/** Get the enclosing endpoint instance */
	KerberosEndpoint getEndpoint() {
	    return KerberosEndpoint.this;
	}

	/**
	 * Return the set of Kerberos principals contained in the
	 * given subject.
	 *
	 * @param subj the subject whose principals will be extracted
	 * @return the set of Kerberos principals
	 */
	private Set getClientPrincipals(Subject subj) {
	    Set cpset = subj.getPrincipals();
	    synchronized (cpset) {
		HashSet set = new HashSet(cpset.size());
		for (Iterator iter = cpset.iterator(); iter.hasNext();) {
		    Object p = iter.next();
		    if (p instanceof KerberosPrincipal)
			set.add(p);
		}
		return set;
	    }
	}

	/**
	 * Compute the connection time limit basing on the specified
	 * set of constraints.
	 *
	 * @param constraints the set of constraints based on which
	 * the connection time limit will be computed
	 * @return the resulting connection time limit
	 */
	private long computeConnectionTimeLimit(Set constraints) {
	    long timeLimit = Long.MAX_VALUE;
 	outer:
 	    for (Iterator iter = constraints.iterator(); iter.hasNext(); ) {
 		Object c = iter.next();
 		long constraintTimeLimit = Long.MIN_VALUE;
 		if (c instanceof ConstraintAlternatives) {
 		    // homogeneous constraint alternatives is assumed
 		    Set alts = ((ConstraintAlternatives) c).elements();
 		    for (Iterator jter = alts.iterator(); jter.hasNext(); ) {
 			Object alt = jter.next();
 			if (alt instanceof ConnectionAbsoluteTime) {
 			    long t = ((ConnectionAbsoluteTime) alt).getTime();
 			    if (constraintTimeLimit < t)
 				constraintTimeLimit = t;
 			} else {
 			    continue outer;
 			}
 		    }
 		} else if (c instanceof ConnectionAbsoluteTime) {
 		    constraintTimeLimit = 
 			((ConnectionAbsoluteTime) c).getTime();
 		} else {
 		    continue;
 		}
 
 		if (constraintTimeLimit < timeLimit) {
 		    timeLimit = constraintTimeLimit;
 		}
 	    }

	    return timeLimit;
	}

	/**
	 * Return all valid Ticket Granting Tickets (TGTs) in the
	 * clientSubject as an array. The server name of a TGT starts
	 * with "krbtgt/".
	 * 
	 * @return an array of valid TGTs
	 */
	private KerberosTicket[] getTickets() {
	    ArrayList tlist = new ArrayList();
	    Set creds = clientSubject.getPrivateCredentials();
	    synchronized (creds) {
		for (Iterator iter = creds.iterator(); iter.hasNext(); ) {
		    Object cred = iter.next();
		    if (cred instanceof KerberosTicket) {
			KerberosTicket ticket = (KerberosTicket) cred;
			if (ticket.getServer().getName().startsWith(
			    "krbtgt/") && !ticket.isDestroyed() &&
			    ticket.isCurrent())
			{
			    tlist.add(ticket);
			}
		    }
		}
	    }
	    return (KerberosTicket[]) tlist.toArray(
                new KerberosTicket[tlist.size()]);
	}

	private KerberosTicket findTicket(
	    KerberosTicket[] tickets, KerberosPrincipal p)
	{
	    String crealm = p.getRealm();
	    String srealm = serverPrincipal.getRealm();
	    String tgtName = "krbtgt/" + srealm + "@" + crealm;
	    for (int i = 0; i < tickets.length; i++) {
		if (tickets[i].getClient().equals(p) && 
		    tickets[i].getServer().getName().equals(tgtName))
		{
		    return tickets[i];
		}
	    }
	    return null;
	}

	private AuthenticationPermission getAuthenticationPermission(
	    KerberosPrincipal client, boolean deleg)
	{
	    String act;
	    if (deleg) {
		act = "delegate";
	    } else {
		act = "connect";
	    }
	    Set locals = Collections.singleton(client);
	    Set peers = Collections.singleton(serverPrincipal);
	    return new AuthenticationPermission(locals, peers, act);
	}
    }

    /** ConnectionEndpoint implementation class for this end point */
    private final class ConnectionEndpointImpl implements ConnectionEndpoint {

	// javadoc is inherited from the ConnectionEndpoint interface
	public Connection connect(OutboundRequestHandle handle)
	    throws IOException
	{
	    RequestHandleImpl rh = checkRequestHandleImpl(handle);
	    Config config = null;
	    Exception exceptionCaught = null;
	    try {
		/* do permission and credential check, pick the config
		   to be used */
		List configs = rh.getConfigs();
		config = (Config) configs.get(0);
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "Passed in request handle " +
			       "is:\n{0},\nconfiguration list returned by " +
			       "getConfigs is:\n{1},\nin which the first " +
			       "one will be used.",
			       new Object[] {rh, configs});
		}
	    } catch (UnsupportedConstraintException e) {
		exceptionCaught = e;
	    } catch (SecurityException e) {
		exceptionCaught = e;
	    }

	    if (exceptionCaught != null) {
		if (logger.isLoggable(Levels.FAILED)) {
		    KerberosUtil.logThrow(
			logger, Levels.FAILED, this.getClass(), 
			"connect", "failed to find a supportable " +
			"connection configuration for the request", null, 
			exceptionCaught);
		}
		if (rh.errorCode == UNSUPPORTABLE_CONSTRAINT_REQUIRED)
		    throw (UnsupportedConstraintException) exceptionCaught;
		UnsupportedConstraintException genericException =
		    new UnsupportedConstraintException(
                        "Either there are conflicting or unsatisfiable " +
			"constraint requirements, " +
			"or the JAAS login has not been " +
			"done (Subject.getSubject(AccessController." +
			"getContext()) returns null), or no appropriate " +
                        "Kerberos principal and corresponding TGT " +
			"allowed by the requirements can be found in " +
			"the current subject. " + rh.constraints);
		KerberosUtil.secureThrow(exceptionCaught, genericException);
	    }

	    Socket sock;
	    if (!disableSocketConnect) {
		sock = connectToHost(rh);
	    } else {
		sock = newSocket();
	    }
	    
	    Connection c = new ConnectionImpl(sock, config);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "New connection established:\n{0}",
			   new Object[] {c});
	    }
	    return c;
	}

	private Socket connectToHost(RequestHandleImpl rh)
	    throws IOException
	{
	    InetAddress[] addresses;
	    try {
		addresses = InetAddress.getAllByName(serverHost);
	    } catch (UnknownHostException uhe) {
		try {
		    /*
		     * Creating the InetSocketAddress attempts to
		     * resolve the host again; in J2SE 5.0, there is a
		     * factory method for creating an unresolved
		     * InetSocketAddress directly.
		     */
		    return connectToSocketAddress(
			new InetSocketAddress(serverHost, serverPort), rh);
		} catch (IOException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    ConnectionEndpointImpl.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { serverHost + ":" + serverPort }, e);
		    }
		    throw e;
		} catch (SecurityException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    ConnectionEndpointImpl.class, "connectToHost",
			    "exception connecting to unresolved host {0}",
			    new Object[] { serverHost + ":" + serverPort }, e);
		    }
		    throw e;
		}
	    } catch (SecurityException e) {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			ConnectionEndpointImpl.class, "connectToHost",
			"exception resolving host {0}",
			new Object[] { serverHost }, e);
		}
		throw e;
	    }
	    IOException lastIOException = null;
	    SecurityException lastSecurityException = null;
	    for (int i = 0; i < addresses.length; i++) {
		SocketAddress socketAddress =
		    new InetSocketAddress(addresses[i], serverPort);
		try {
		    return connectToSocketAddress(socketAddress, rh);
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
			new Object[] { serverHost + ":" + serverPort },
			lastIOException);
		}
		throw lastIOException;
	    }
	    assert lastSecurityException != null;
	    if (logger.isLoggable(Levels.FAILED)) {
		LogUtil.logThrow(logger, Levels.FAILED,
		    ConnectionEndpointImpl.class, "connectToHost",
		    "exception connecting to {0}",
		    new Object[] { serverHost + ":" + serverPort },
		    lastSecurityException);
	    }
	    throw lastSecurityException;
	}

	/**
	 * Returns a socket connected to the specified address, with a
	 * timeout governed by the constraints in the request handle.
	 **/
	private Socket connectToSocketAddress(SocketAddress socketAddress,
					      RequestHandleImpl rh)
	    throws IOException
	{
	    long timeout = rh.connectionAbsoluteTime -
			   System.currentTimeMillis();
	    if (timeout <= 0) {
		throw new SocketTimeoutException(
		    "connection timeout passed before socket." +
		    "connect is called");
	    }

	    Socket sock = newSocket();
	    boolean ok = false;
	    try {
		if (timeout > Integer.MAX_VALUE) {
		    sock.connect(socketAddress);
		} else {
		    sock.connect(socketAddress, (int) timeout);
		}
		ok = true;
		return sock;
	    } finally {
		if (!ok) {
		    try {
			sock.close();
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
	    Socket sock;
	    if (csf != null) {
		try {
		    sock = csf.createSocket();
		} catch (IOException e) {
		    if (logger.isLoggable(Levels.FAILED)) {
			KerberosUtil.logThrow(
			    logger, Levels.FAILED, this.getClass(), 
			    "newSocket", "failed to create socket " +
			    "using the given SocketFactory {0}",
			    new Object[] {csf}, e);
		    }
		    throw e;
		}

		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "created socket {0} using " + 
			       "factory {1}", new Object[]{sock, csf});
		}
	    } else {
		sock = new Socket();
		logger.log(Level.FINE, "created socket {0}", sock);
	    }

	    setSocketOptions(sock);
	    return sock;
	}

	private void setSocketOptions(Socket sock) {
	    try {
		sock.setTcpNoDelay(true);
	    } catch (SocketException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    KerberosUtil.
			logThrow(logger, Levels.HANDLED, this.getClass(), 
				 "connect", "failed to setTcpNoDelay " +
				 "option for {0}", new Object[] {sock}, e);
		}
	    }

	    try {
		sock.setKeepAlive(true);
	    } catch (SocketException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    KerberosUtil.
			logThrow(logger, Levels.HANDLED, this.getClass(), 
				 "connect", "failed to setKeepAlive " +
				 "options for {0}", new Object[] {sock}, e);
		}
	    }
	}
	
	// javadoc is inherited from the ConnectionEndpoint interface
	public Connection connect(OutboundRequestHandle handle,
				  Collection active, Collection idle)
	{
	    RequestHandleImpl rh = checkRequestHandleImpl(handle);

	    if (active == null) {
		throw new NullPointerException(
		    "active collection cannot be null");
	    } else if (idle == null) {
		throw new NullPointerException(
		    "idle collection cannot be null");
	    }

	    // do permission and credential check, get all possible configs
	    List configList;
	    try {
		configList = rh.getConfigs();
	    } catch (Exception e) {
		return null; // the other connect will be called later
	    }

	    boolean checkedResolvePermission = false;
	    for (Iterator i = configList.iterator(); i.hasNext(); ) {
		Config config = (Config) i.next();
		for (Iterator j = active.iterator(); j.hasNext(); ) {
		    ConnectionImpl c = checkConnection(j.next());
		    if (c.satisfies(config)) {
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(Level.FINE, "found an active " +
				       "connection for reusing:\n{0}\n{1}",
				       new Object[] {c, config});
			}
			if (!checkedResolvePermission) {
			    try {
				checkResolvePermission();
			    } catch (SecurityException e) {
				if (logger.isLoggable(Levels.FAILED)) {
				    LogUtil.logThrow(logger, Levels.FAILED,
					ConnectionEndpointImpl.class, "connect",
					"exception resolving host {0}",
					new Object[] { serverHost }, e);
				}
				throw e;
			    }
			    checkedResolvePermission = true;
			}
			try {
			    c.checkConnectPermission();
			    return c;
			} catch (SecurityException e) {
			    if (logger.isLoggable(Levels.HANDLED)) {
				LogUtil.logThrow(logger, Levels.HANDLED,
				    ConnectionEndpointImpl.class, "connect",
				    "access to reuse connection {0} denied",
				    new Object[] { c.sock }, e);
			    }
			}
		    }
		}
	    }

	    for (Iterator i = configList.iterator(); i.hasNext(); ) {
		Config config = (Config) i.next();
		for (Iterator j = idle.iterator(); j.hasNext(); ) {
		    ConnectionImpl c = checkConnection(j.next());
		    if (c.switchTo(config)) {
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(Level.FINE, "found an idle " +
				       "connection for reusing:\n{0}\n{1}",
				       new Object[] {c, config});
			}
			if (!checkedResolvePermission) {
			    try {
				checkResolvePermission();
			    } catch (SecurityException e) {
				if (logger.isLoggable(Levels.FAILED)) {
				    LogUtil.logThrow(logger, Levels.FAILED,
					ConnectionEndpointImpl.class, "connect",
					"exception resolving host {0}",
					new Object[] { serverHost }, e);
				}
				throw e;
			    }
			    checkedResolvePermission = true;
			}
			try {
			    c.checkConnectPermission();
			    return c;
			} catch (SecurityException e) {
			    if (logger.isLoggable(Levels.HANDLED)) {
				LogUtil.logThrow(logger, Levels.HANDLED,
				    ConnectionEndpointImpl.class, "connect",
				    "access to reuse connection {0} denied",
				    new Object[] { c.sock }, e);
			    }
			}
		    }
		}
	    }

	    return null;
	}
	
	private void checkResolvePermission() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkConnect(serverHost, -1);
	    }
	}
    }

    /** Implementation class of the client side connection abstraction */
    private final class ConnectionImpl extends KerberosUtil.Connection
	implements Connection
    {
	/** Kerberos error code defined in RFC1510 (Request is a replay) */
	private static final int KRB_AP_ERR_REPEAT = 34;

	/** InputStream this connection will provide to the upper layer */
	private InputStream istream;

	/** OutputStream this connection will provide to the upper layer */
	private OutputStream ostream;

	/** 
	 * Establish a connection to server.
	 *
	 * @param sock socket over which the connection traffic will
	 *        be carried
	 * @param config a configuration object that specifies how the
	 *        connection should be setup
	 * @throws IOException if the connection establishment
	 *         handshake fails
	 */
	ConnectionImpl(Socket sock, Config config)
	    throws IOException
	{
	    super(sock);

	    clientPrincipal = config.clientPrincipal;
	    doEncryption = config.encry;
	    doDelegation = config.deleg;
	    connectionLogger = logger;

	    boolean done = false;
	    try {
		Security.doPrivileged(new PrivilegedExceptionAction() {
			public Object run() throws IOException, GSSException {
			    establishContext();
			    return null;
			}
		    });
		ostream = new KerberosUtil.ConnectionOutputStream(this);
		istream = new KerberosUtil.ConnectionInputStream(this);
		done = true;
	    } catch (PrivilegedActionException e) {
		Exception ex = e.getException();

		if (logger.isLoggable(Levels.FAILED)) {
		    KerberosUtil.logThrow(
			logger, Levels.FAILED, this.getClass(), "constructor",
			"failed to establish GSSContext for this connection " +
			"with {0}.", new Object[] {config}, ex);
		}

		if (ex instanceof GSSException) {
		    IOException ioe =
			new IOException("Failed to establish GSS " +
					"context for this connection.");
		    ioe.initCause(ex);
		    throw ioe;
		} else {
		    throw (IOException) ex;
		}
	    } finally {
		if (!done)
		    close();
	    }
	}

	// javadoc is inherited from the Connection interface
	public OutputStream getOutputStream() throws IOException {
	    return ostream;
	}

	// javadoc is inherited from the Connection interface
	public InputStream getInputStream() throws IOException {
	    return istream;
	}

	// javadoc is inherited from the Connection interface
	public SocketChannel getChannel() {
	    return null; // does not support channel for now
	}

	// javadoc is inherited from the Connection interface
	public void populateContext(
	    OutboundRequestHandle handle, Collection context)
	{
	    if (handle == null) {
		throw new NullPointerException("handle is null");
	    } else if (context == null) {
		throw new NullPointerException("context is null");
	    }
	}
	
	// javadoc is inherited from the Connection interface
	public InvocationConstraints getUnfulfilledConstraints(
	    OutboundRequestHandle handle)
	{
	    RequestHandleImpl rh = checkRequestHandleImpl(handle);
	    return rh.unfulfilledConstraints;
	}

	// javadoc is inherited from the Connection interface
	public void writeRequestData(OutboundRequestHandle handle,
				     OutputStream out)
	{
	    // got nothing to do here for this provider
	}

	// javadoc is inherited from the Connection interface
	public IOException readResponseData(OutboundRequestHandle handle,
				     InputStream in)
	{
	    // got nothing to do here for this provider
	    return null;
	}

	/** Returns a string representation of this connection. */
	public String toString() {
	    StringBuffer b = 
		new StringBuffer("KerberosEndpoint.ConnectionImpl");
	    b.append("[clientPrincipal=" + clientPrincipal);
	    b.append(" serverPrincipal=" + serverPrincipal);
	    b.append(" doEncryption=" + doEncryption);
	    b.append(" doDelegation=" + doDelegation);
	    b.append(" client=" + sock.getLocalAddress().getHostName());
	    b.append(":" + sock.getLocalPort());
	    b.append(" server=" + sock.getInetAddress().getHostName());
	    b.append(":" + sock.getPort());
	    b.append(']');
	    return b.toString();
	}
	
	/**
	 * Check whether this connection can satisfy the requirements
	 * specified by the given configuration.
	 */
	boolean satisfies(Config config) {
	    return gssContext.getLifetime() >= minGssContextLifetime &&
		clientPrincipal.equals(config.clientPrincipal) &&
		doEncryption == config.encry && doDelegation == config.deleg;
	}

	/**
	 * Check whether the caller has sufficient permissions to reuse the
	 * socket that this connection has connected with.
	 */
	void checkConnectPermission() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		InetSocketAddress address =
			(InetSocketAddress) sock.getRemoteSocketAddress();
		if (address.isUnresolved()) {
		    sm.checkConnect(address.getHostName(), sock.getPort());
		} else {
		    sm.checkConnect(address.getAddress().getHostAddress(),
				    sock.getPort());
		}
	    }
	}

	/**
	 * Switch an idle connection to satisfy the given
	 * configuration.
	 *
	 * @param config target configuration
	 * @return true if succeed, false otherwise.  If false is
	 *         returned, the connection is unchanged.
	 */
	boolean switchTo(Config config) {
	    if (gssContext.getLifetime() < minGssContextLifetime)
		return false;
	    if (clientPrincipal.equals(config.clientPrincipal) &&
		doDelegation == config.deleg)
	    {
		doEncryption = config.encry;
		return true;
	    }
	    return false;
	}

	/** Get the enclosing endpoint instance */
	KerberosEndpoint getEndpoint() {
	    return KerberosEndpoint.this;
	}

	/**
	 * Exchange handshake messages with server to establish the
	 * session or context of the connection.
	 */
	private void establishContext() throws IOException, GSSException {

	    synchronized (classLock) {
		if (gssManager == null) {
		    gssManager = GSSManager.getInstance();
		}
	    }

	    GSSName clientName = gssManager.createName(
		clientPrincipal.getName(), KerberosUtil.krb5NameType);

	    GSSCredential clientCred = gssManager.createCredential(
		clientName, GSSCredential.INDEFINITE_LIFETIME,
		KerberosUtil.krb5MechOid, GSSCredential.INITIATE_ONLY);

	    GSSName serverName = gssManager.createName(
		serverPrincipal.getName(), KerberosUtil.krb5NameType);

	    for (int i = maxGssContextRetries; i > 0; i--) {
		gssContext = gssManager.createContext(
		    serverName, KerberosUtil.krb5MechOid, clientCred,
		    GSSContext.DEFAULT_LIFETIME);
		/*
		 * Set the desired optional features on the
		 * context. The client chooses these options.
		 */
		gssContext.requestMutualAuth(true); // Mutual authentication
		gssContext.requestInteg(true);      // Will use integrity later
		gssContext.requestConf(true);       // always enable encryption
		gssContext.requestCredDeleg(doDelegation);
		gssContext.requestReplayDet(true);
		gssContext.requestSequenceDet(true);
		
		// Do the context establishment loop
		byte[] token = new byte[0];
		try {
		    while (true) {
			// token is ignored on the first call
			token = gssContext.initSecContext(
			    token, 0, token.length);
			/*
			 * Send a token to the server if one was generated by
			 * initSecContext
			 */
			if (token != null) {
			    dos.writeInt(token.length);
			    dos.write(token);
			    dos.flush();
			}
			
			if (gssContext.isEstablished()) {
			    break;
			} else {
			    token = new byte[dis.readInt()];
			    dis.readFully(token);
			}
		    }
		    break; // done gssContext establishment
		} catch (GSSException ge) {
		    if ((ge.getMessage().indexOf("34") >= 0 ||
			 ge.getMajor() == GSSException.DUPLICATE_TOKEN ||
			 ge.getMinor() == KRB_AP_ERR_REPEAT ||
			 ge.getMessage().indexOf("Request is a replay") >= 0)
			&& i != 1) // will throw ge if loop has terminated
		    {
			/* this could caused by our authenticator
                         * being time stamped to the same micro-second
                         * as those of other concurrent connections,
                         * retry for better luck */
			continue;
		    }
		    throw ge;
		}
	    }

	    if (!gssContext.getIntegState() ||
		(doEncryption && !gssContext.getConfState()) ||
		(doDelegation && !gssContext.getCredDelegState()) ||
		!gssContext.getTargName().toString().
		equals(serverPrincipal.getName()))
	    {
		throw new IOException("Failed to establish gss context " +
				      "for connection");
	    }
	    logger.log(Level.FINE, "GSSContext established for {0}", this);
	}
    }

    /**
     * The key used for the softcache of this endpoint. It
     * encapsulates the <code>clientSubject</code> and the
     * <code>InvocationConstraints</code> associated with a request.  To
     * compute a key's <code>hashcode</code>
     * <code>identityHashCode</code> of its contents are
     * <code>XOR</code>ed together.  For <code>equals</code>,
     * <code>==</code> are used.
     */
    private static final class CacheKey {

	private final Subject subject;
	private final InvocationConstraints constraints;

	/** Construct a Key object */
	CacheKey(Subject subject, InvocationConstraints constraints) {
	    this.subject = subject;
	    this.constraints = constraints;
	}

	public int hashCode() {
            // identityHashCode() should be faster
            return System.identityHashCode(subject) ^
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
	    return subject == okey.subject && constraints == okey.constraints;
	}
    }
}
