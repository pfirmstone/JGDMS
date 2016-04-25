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

import org.apache.river.logging.Levels;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.AccessControlContext;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.ClientHost;
import net.jini.io.context.ClientSubject;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.ServerConnectionManager;
import net.jini.jeri.ssl.SslServerEndpointImpl.SslListenEndpoint;
import net.jini.security.AuthenticationPermission;
import net.jini.security.SecurityContext;

/**
 * An implementation of {@link ServerEndpoint} that uses TLS/SSL to support
 * invocation constraints for direct communication over TCP sockets. <p>
 *
 * Instances of this class are intended to be created for use with the {@link
 * BasicJeriExporter} class. Calls to {@link #enumerateListenEndpoints
 * enumerateListenEndpoints} return instances of {@link SslEndpoint}. <p>
 *
 * This class supports at least the following constraints, possibly limited by
 * the available cipher suites: <p>
 *
 * <ul>
 * <li> {@link ClientAuthentication}
 * <li> {@link ClientMaxPrincipal}, when it contains an {@link X500Principal}
 * <li> {@link ClientMaxPrincipalType}, when it contains
 *	<code>X500Principal</code>
 * <li> {@link ClientMinPrincipal}, when it contains a single
 *	<code>X500Principal</code> only
 * <li> {@link ClientMinPrincipalType}, when it contains
 *	<code>X500Principal</code> only
 * <li> {@link Confidentiality}
 * <li> {@link ConfidentialityStrength}, a provider-specific constraint for
 *	specifying weak or strong confidentiality
 * <li> {@link ConnectionAbsoluteTime}, trivially, since this only takes effect
 *	on the client side
 * <li> {@link ConnectionRelativeTime}, trivially, since this only takes effect
 *	on the client side
 * <li> {@link ConstraintAlternatives}, if the elements all have the same
 *	actual class and at least one element is supported
 * <li> {@link Delegation#NO}
 * <li> {@link Delegation#YES}, trivially, for anonymous clients
 * <li> {@link DelegationAbsoluteTime}, trivially, when delegation is not
 *	supported
 * <li> {@link DelegationRelativeTime}, trivially, when delegation is not
 *	supported
 * <li> {@link Integrity#YES}
 * <li> {@link ServerAuthentication}
 * <li> {@link ServerMinPrincipal}, when it contains a single
 *	<code>X500Principal</code> only
 * </ul> <p>
 *
 * This class authenticates as a single {@link Principal} if the following
 * items are present in the server {@link Subject}: <p>
 *
 * <ul>
 * <li> One or more principals of type <code>X500Principal</code>
 * <li> For each principal, one or more certificate chains, stored as public
 *      credentials, and represented by instances of {@link CertPath}, whose
 *      <code>getType</code> method returns "X.509", and for which calling
 *      <code>getSubjectDN</code> on the certificate chain's first element
 *      returns that principal's name
 * <li> For each certificate chain, an instance of {@link
 *      X500PrivateCredential}, stored as a private credential, whose
 *      <code>getCertificate</code> method returns a value equal to the first
 *      element of the certificate chain, and whose <code>getPrivateKey</code>
 *      method returns the associated private key
 * </ul> <p>
 *
 * In addition, this class will only dispatch remote calls that authenticate as
 * a given principal if the caller of {@link
 * net.jini.jeri.ServerEndpoint.ListenEndpoint#listen listen} on the class's
 * {@link net.jini.jeri.ServerEndpoint.ListenEndpoint} has been granted {@link
 * AuthenticationPermission} with that principal as the local principal, the
 * principal representing the authenticated identity of the client for the call
 * (if any) as the peer principal, and the <code>accept</code> action. <p>
 *
 * This class supports remote connections between authenticated servers and
 * authenticated or anonymous clients, and between anonymous servers and
 * anonymous clients. Connections between anonymous servers and authenticated
 * clients are not supported. Because of the suites available in the TLS/SSL
 * protocol, support for {@link Confidentiality#NO} requires the server to
 * authenticate with an RSA public key. <p>
 *
 * If the server subject contains principals and credentials that would permit
 * authentication of more than one <code>X500Principal</code>, the endpoint
 * will make an arbitrary choice of the principal to use for authentication,
 * and will continue to make the same choice so long as subject contents,
 * validity of credentials, and security permissions do not change. <p>
 *
 * The host name specified when creating an <code>SslServerEndpoint</code>
 * instance controls the host name that will be contained in
 * <code>SslEndpoint</code> instances produced when {@link
 * #enumerateListenEndpoints enumerateListenEndpoints} is invoked to listen on
 * the server endpoint; the host name does not affect the behavior of the
 * listen operation itself, which listens on all of the local system's network
 * addresses. If the host name in the server endpoint is <code>null</code>,
 * then the host name in the <code>SslEndpoint</code> instances that it
 * produces will be the default server host name, which is the IP address
 * string of the {@link InetAddress} returned by {@link
 * InetAddress#getLocalHost InetAddress.getLocalHost} when
 * <code>enumerateListenEndpoints</code> is invoked. <p>
 *
 * This class permits specifying a {@link SocketFactory} for creating the
 * {@link Socket} instances that the associated <code>SslEndpoint</code>
 * instances use to make remote connections back to the server, and a {@link
 * ServerSocketFactory} for creating the {@link ServerSocket} instances that
 * the server endpoint uses to accept remote connections. These socket
 * factories and sockets should not implement the TLS/SSL protocol; it is the
 * responsibility of the implementation to establish TLS/SSL connections over
 * the sockets it obtains from the socket factories. In particular, instances
 * of {@link SSLSocketFactory} and {@link SSLServerSocketFactory} should not be
 * used, and the factories used should not return instances of {@link
 * SSLSocket} or {@link SSLServerSocket}. <p>
 *
 * A <code>SocketFactory</code> used with instances of this class should be
 * serializable, and must implement {@link Object#equals Object.equals} to obey
 * the guidelines that are specified for <code>equals</code> methods of {@link
 * Endpoint} instances. A <code>ServerSocketFactory</code> used with instances
 * of this class must implement <code>Object.equals</code> to obey the
 * guidelines that are specified for <code>equals</code> methods of {@link
 * net.jini.jeri.ServerEndpoint.ListenEndpoint ListenEndpoint} instances. <p>
 *
 * This class uses the <a href="../connection/doc-files/mux.html">Jini
 * extensible remote invocation (Jini ERI) multiplexing protocol</a> to map
 * outgoing requests to socket connections.
 *
 * 
 * @see SslEndpoint
 * @see ConfidentialityStrength
 * @since 2.0
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * This implementation uses the <a
 * href="http://java.sun.com/j2se/1.4/docs/guide/security/jsse/JSSERefGuide.html"
 * target="_top">Java(TM) Secure Socket Extension (JSSE)</a>. <p>
 *
 * This implementation uses the {@link ServerConnectionManager} class to manage
 * connections. <p>
 *
 * This implementation uses the following {@link Logger} instances in the
 * <code>net.jini.jeri.ssl</code> namespace: <p>
 *
 * <ul>
 * <li> <a href="#init_logger">init</a> - problems during initialization
 * <li> <a href="#server_logger">server</a> - information about
 *	server-side connections
 * </ul> <p>
 *
 * <a name="init_logger"></a>
 * <table border="1" cellpadding="5" summary="Describes logging to the init
 *	  logger performed by the SslServerEndpoint class at different
 *	  logging levels">
 *
 * <caption halign="center" valign="top"><b><code>
 *	    net.jini.jeri.ssl.init</code></b></caption>
 *     
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link Level#WARNING WARNING} <td> problems with initializing JSSE
 *
 * </table> <p>
 *
 * <a name="server_logger"></a>
 * <table border="1" cellpadding="5" summary="Describes logging to the server
 *	  logger performed by the SslServerEndpoint class at different
 *	  logging levels">
 *
 * <caption halign="center" valign="top"><b><code>
 *	    net.jini.jeri.ssl.server</code></b></caption>
 *     
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link Level#INFO INFO} <td> problems with accepting or handling
 * server connections, or with handling inbound requests
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> problems with checking
 * constraints or permissions, with enumerating listen endpoints, or with
 * security issues for inbound requests
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> exceptions caught involving
 * authentication
 * 
 * <tr> <td> {@link Level#FINE FINE} <td> creating server endpoints,
 * enumerating listen endpoints, creating or closing connections or listen
 * handles, or checking constraints for endpoints or inbound requests
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> low level operation tracing
 *
 * </table> <p>
 *
 * This implementation uses the following security providers: <p>
 *
 * <ul>
 * <li> {@link SSLContext}, with the protocol specified by the
 *	<code>org.apache.river.jeri.ssl.sslProtocol</code> system property, or
 *	<code>"TLS"</code> if that property is not defined, to provide the
 *	TLS/SSL implementation. The {@link SSLContext#init SSLContext.init}
 *	method is called with <code>null</code> for the <code>random</code>
 *	parameter to use the default {@link SecureRandom} implementation.
 * <li> {@link CertificateFactory}, with type <code>"X.509"</code>, to generate
 *	<code>CertPath</code> instances from X.509 certificate chains
 * <li> {@link TrustManagerFactory}, with the algorithm specified by the
 *	<code>org.apache.river.jeri.ssl.trustManagerFactoryAlgorithm</code> system
 *	property, or the default algorithm if that property is not defined, to
 *	implement trust management for the TLS/SSL implementation. The factory
 *	must return trust managers that implement {@link X509TrustManager}.
 * </ul> <p>
 * 
 * See the documentation on <a
 * href="http://java.sun.com/j2se/1.4/docs/guide/security/CryptoSpec.html#ProviderInstalling"
 * target="_top">installing security providers</a> and <a
 * href="http://java.sun.com/j2se/1.4/docs/guide/security/jsse/JSSERefGuide.html#ProviderCust"
 * target="_top">configuring JSSE</a> for information on configuring these
 * providers. <p>
 *
 * The <a
 * href="http://java.sun.com/j2se/1.4/docs/guide/security/jsse/JSSERefGuide.html#Customization"
 * target="_top">JSSE documentation</a> also describes the system properties
 * for configuring the location, type, and password of the truststore that this
 * implementation uses, through JSSE, to make decisions about what certificate
 * chains should be trusted. <p>
 * 
 * This implementation recognizes the following system properties: <p>
 * 
 * <ul>
 * <li> <code>org.apache.river.jeri.ssl.maxServerSessionDuration</code> - The
 *	maximum number of milliseconds a server-side TLS/SSL session should be
 *	used before expiring. The default is 24 hours. The value used should be
 *	larger than the maximum client session duration to allow the client to
 *	negotiate a new session before the server timeout occurs.
 * <li> <code>org.apache.river.jeri.ssl.sslProtocol</code> - The secure socket
 *	protocol used when obtaining {@link SSLContext} instances. The default
 *	is <code>"TLS"</code>.
 * <li> <code>org.apache.river.jeri.ssl.trustManagerFactoryAlgorithm</code> - The
 *	algorithm used when obtaining {@link TrustManagerFactory}
 *	instances. The default is the value returned by {@link
 *	TrustManagerFactory#getDefaultAlgorithm
 *	TrustManagerFactory.getDefaultAlgorithm}.
 * <li> <code>org.apache.river.jeri.ssl.cipherSuites</code> - The TLS/SSL cipher
 *	suites that should be used for communication. The default is the list
 *	of suites supported by the JSSE implementation. The value should
 *	specify the suite names, separated by commas. The value will be ignored
 *	if it contains no suites or specifies suites that are not supported by
 *	the JSSE implementation. Suites appearing earlier in the list will be
 *	preferred to ones appearing later for suites that support the same
 *	requirements and preferences.
 * </ul>
 */
public final class SslServerEndpoint implements ServerEndpoint {

    /* -- Fields -- */

    /** Server logger */
    static final Logger logger = Utilities.SERVER_LOGGER;

    /** Implementation delegate. */
    final SslServerEndpointImpl impl;

    /* -- Methods -- */

    /**
     * Returns a TLS/SSL server endpoint for the specified port. Uses the
     * <code>null</code> server host (which requests that {@link
     * #enumerateListenEndpoints enumerateListenEndpoints} compute the default
     * server host), the subject associated with the current access control
     * context, the principals in the subject with appropriate public and
     * private credentials for which the caller has {@link
     * AuthenticationPermission} to listen, and <code>null</code> socket
     * factories to create default sockets. A <code>port</code> of
     * <code>0</code> requests listening on any free port.
     *
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @return an <code>SslServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     */
    public static SslServerEndpoint getInstance(int port) {
	SslServerEndpoint se = new SslServerEndpoint(null, null, null, check(port), null, null);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns a TLS/SSL server endpoint for the specified server host and
     * port. Uses the subject associated with the current access control
     * context, the principals in the subject with appropriate public and
     * private credentials for which the caller has {@link
     * AuthenticationPermission} to listen, and <code>null</code> socket
     * factories to create default sockets. A <code>serverHost</code> of
     * <code>null</code> requests that {@link #enumerateListenEndpoints
     * enumerateListenEndpoints} compute the default server host. A
     * <code>port</code> of <code>0</code> requests listening on any free port.
     *
     * @param serverHost the name that clients should use to connect to this
     *	      server, or <code>null</code> to use the default
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @return an <code>SslServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     */
    public static SslServerEndpoint getInstance(String serverHost, int port) {
	SslServerEndpoint se = new SslServerEndpoint(null, null, serverHost, check(port), null, null);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns a TLS/SSL server endpoint for the specified server host, port,
     * and socket factories. Uses the subject associated with the current
     * access control context, and the principals in the subject with
     * appropriate public and private credentials for which the caller has
     * {@link AuthenticationPermission} to listen. A <code>serverHost</code> of
     * <code>null</code> requests that {@link #enumerateListenEndpoints
     * enumerateListenEndpoints} compute the default server host. A
     * <code>port</code> of <code>0</code> requests listening on any free
     * port. A <code>socketFactory</code> of <code>null</code> uses default
     * sockets in the associated {@link SslEndpoint}. A
     * <code>serverSocketFactory</code> of <code>null</code> uses default
     * server sockets.
     *
     * @param serverHost the name that clients should use to connect to this
     *	      server, or <code>null</code> to use the default
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @param socketFactory the socket factory for use in the associated
     *	      <code>SslEndpoint</code> instances, or <code>null</code>
     * @param serverSocketFactory the server socket factory, or
     *	      <code>null</code>
     * @return an <code>SslServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     */
    public static SslServerEndpoint getInstance(
	String serverHost,
	int port,
	SocketFactory socketFactory,
	ServerSocketFactory serverSocketFactory)
    {
	SslServerEndpoint se = new SslServerEndpoint(null, null, serverHost, check(port),
				     socketFactory, serverSocketFactory);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns a TLS/SSL server endpoint for the specified server subject,
     * server principals, server host, and port. Uses <code>null</code> socket
     * factories to create default sockets. A <code>serverSubject</code> of
     * <code>null</code> uses the subject associated with the current access
     * control context. A <code>serverPrincipals</code> of <code>null</code>
     * uses the principals in the subject with appropriate public and private
     * credentials for which the caller has {@link AuthenticationPermission} to
     * listen; otherwise that argument specifies the principals to use, or that
     * the server should be anonymous if the argument has no elements.  If
     * non-<code>null</code>, the value of <code>serverPrincipals</code> is
     * neither retained nor modified; subsequent changes to that argument have
     * no effect on the instance created. A <code>serverHost</code> of
     * <code>null</code> requests that {@link #enumerateListenEndpoints
     * enumerateListenEndpoints} compute the default server host. A
     * <code>port</code> of <code>0</code> requests listening on any free port.
     *
     * @param serverSubject the <code>Subject</code> to use for authenticating
     *	      the server or <code>null</code> to use the current subject
     * @param serverPrincipals the principals to use for authenticating the
     *	      server, or <code>null</code> to use any available principals in
     *	      the subject
     * @param serverHost the name that clients should use to connect to this
     *	      server, or <code>null</code> to use the default
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @return an <code>SslServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     * @throws NullPointerException if <code>serverPrincipals</code> is not
     *	       <code>null</code> and any of its elements are <code>null</code>
     */
    public static SslServerEndpoint getInstance(
	Subject serverSubject,
	X500Principal[] serverPrincipals,
	String serverHost,
	int port)
    {
	SslServerEndpoint se = new SslServerEndpoint(serverSubject, serverPrincipals,
				     serverHost, check(port), null, null);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns a TLS/SSL server endpoint for the specified server subject,
     * server principals, server host, port, and socket factories. A
     * <code>serverSubject</code> of <code>null</code> uses the subject
     * associated with the current access control context. A
     * <code>serverPrincipals</code> of <code>null</code> uses the principals
     * in the subject with appropriate public and private credentials for which
     * the caller has {@link AuthenticationPermission} to listen; otherwise
     * that argument specifies the principals to use, or that the server should
     * be anonymous if the argument has no elements. If non-<code>null</code>,
     * the value of <code>serverPrincipals</code> is neither retained nor
     * modified; subsequent changes to that argument have no effect on the
     * instance created. A <code>serverHost</code> of <code>null</code>
     * requests that {@link #enumerateListenEndpoints enumerateListenEndpoints}
     * compute the default server host. A <code>port</code> of <code>0</code>
     * requests listening on any free port. A <code>socketFactory</code> of
     * <code>null</code> uses default sockets in the associated {@link
     * SslEndpoint}. A <code>serverSocketFactory</code> of <code>null</code>
     * uses default server sockets.
     *
     * @param serverSubject the <code>Subject</code> to use for authenticating
     *	      the server or <code>null</code> to use the current subject
     * @param serverPrincipals the principals to use for authenticating the
     *	      server, or <code>null</code> to use any available principals in
     *	      the subject
     * @param serverHost the name that clients should use to connect to this
     *	      server, or <code>null</code> to use the default
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @param socketFactory the socket factory for use in the associated
     *	      <code>SslEndpoint</code> instances, or <code>null</code>
     * @param serverSocketFactory the server socket factory, or
     *	      <code>null</code>
     * @return an <code>SslServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     * @throws NullPointerException if <code>serverPrincipals</code> is not
     *	       <code>null</code> and any of its elements are <code>null</code>
     */
    public static SslServerEndpoint getInstance(
	Subject serverSubject,
	X500Principal[] serverPrincipals,
	String serverHost,
	int port,
	SocketFactory socketFactory,
	ServerSocketFactory serverSocketFactory)
    {
	SslServerEndpoint se = new SslServerEndpoint(serverSubject, serverPrincipals,
				     serverHost, check(port), socketFactory,
				     serverSocketFactory);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }
    
    private static int check(int port){
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        return port;
    }

    /** Creates an instance of this class. */
    private SslServerEndpoint(Subject serverSubject,
			      X500Principal[] serverPrincipals,
			      String serverHost,
			      int port,
			      SocketFactory socketFactory,
			      ServerSocketFactory serverSocketFactory)
    {
	impl = new SslServerEndpointImpl(
	    this,  new SslListenEndpoint(serverSubject, serverPrincipals,
	    serverHost, port, socketFactory, serverSocketFactory));
    }

    /**
     * Returns the host name that will be used in {@link SslEndpoint} instances
     * created by listening on this object, or <code>null</code> if {@link
     * #enumerateListenEndpoints enumerateListenEndpoints} will use the default
     * server host.
     *
     * @return the host name to use in <code>SslEndpoint</code> instances
     *	       created by listening on this object, or <code>null</code> if
     *	       using the default
     */
    public String getHost() {
	return impl.getServerHost();
    }

    /**
     * Returns the TCP port on which this object listens for connections, or
     * <code>0</code> if it selects a free port.
     *
     * @return the TCP port on which this object listens for connections, or
     *	       <code>0</code> if it selects a free port
     */
    public int getPort() {
	return impl.getPort();
    }

    /**
     * Returns an immutable set of the principals that this instance uses for
     * authentication, or <code>null</code> if it is anonymous.
     *
     * @return an immutable set of the principals or <code>null</code>
     */
    public Set getPrincipals() {
	return impl.getServerPrincipals() == null
	    ? null : Collections.unmodifiableSet(impl.getServerPrincipals());
    }

    /**
     * Returns the socket factory that the associated {@link SslEndpoint}
     * instances created by listening on this server endpoint use to create
     * {@link Socket} instances, or <code>null</code> if they use default
     * sockets.
     *
     * @return the socket factory that associated endpoints use to create
     *	       sockets, or <code>null</code> if they use default sockets
     */
    public SocketFactory getSocketFactory() {
	return impl.getSocketFactory();
    }

    /**
     * Returns the server socket factory that this server endpoint uses to
     * create {@link ServerSocket} instances, or <code>null</code> if it uses
     * default server sockets.
     *
     * @return the server socket factory that this server endpoint uses to
     *	       create server sockets, or <code>null</code> if it uses default
     *	       server sockets
     */
    public ServerSocketFactory getServerSocketFactory() {
	return impl.getServerSocketFactory();
    }

    /** Returns a string representation of this object. */
    public String toString() {
	return "SslServerEndpoint" + impl.fieldsToString();
    }

    /* -- Implement ServerCapabilities -- */

    /**
     * Checks that it is possible to receive requests that either
     * fully or partially satisfy the specified requirements, and
     * returns any constraints that must be fully or partially
     * implemented by higher layers in order to fully satisfy all of
     * the specified requirements. <p>
     *
     * This implementation only returns {@link Integrity#YES} constraints.
     *
     * @throws SecurityException if the current security context does not have
     *	       the permissions necessary to perform this operation
     * @throws NullPointerException if <code>constraints</code> is
     *	       <code>null</code>
     */
    public InvocationConstraints checkConstraints(
	InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	return impl.checkConstraints(constraints);
    }

    /* -- Implement ServerEndpoint -- */

    /** Returns a hash code value for this object. */
    public int hashCode() {
	return impl.hashCode();
    }

    /**
     * Two instances of this class are equal if they have server subjects that
     * compare equal using <code>==</code>; have server principals that are
     * either both <code>null</code> or are equal when compared as the elements
     * of a {@link Set}; have the same values for server host and port; have
     * socket factories that are either both <code>null</code>, or have the
     * same actual class and are equal; and have server socket factories
     * that are either both <code>null</code>, or have the same actual class
     * and are equal.
     */
    public boolean equals(Object object) {
	if (this == object) {
	    return true;
	} else {
	    return object instanceof SslServerEndpoint &&
		impl.equals(((SslServerEndpoint) object).impl);
	}
    }

    /**
     * Passes the {@link net.jini.jeri.ServerEndpoint.ListenEndpoint
     * ListenEndpoint} for this <code>SslServerEndpoint</code> to
     * <code>listenContext</code>, which will ensure an active listen
     * operation on the endpoint, and returns an <code>SslEndpoint</code>
     * instance corresponding to the listen operation chosen by
     * <code>listenContext</code>. <p>
     *
     * If this server endpoint's server host is <code>null</code>, then the
     * endpoint returned will contain the default server host. This method
     * computes the default by invoking {@link InetAddress#getLocalHost
     * InetAddress.getLocalHost} to obtain an <code>InetAddress</code> for the
     * local host. If <code>InetAddress.getLocalHost</code> throws an
     * exception, this method throws that exception. The default host name will
     * be the string returned by invoking {@link InetAddress#getHostAddress
     * getHostAddress} on that <code>InetAddress</code>. If there is a security
     * manager, its {@link SecurityManager#checkConnect(String,int)
     * checkConnect} method will be invoked with the string returned by
     * invoking {@link InetAddress#getHostName getHostName} on that same
     * <code>InetAddress</code> as the host argument and <code>-1</code> as the
     * port argument; this could result in a
     * <code>SecurityException</code>. <p>
     *
     * This method invokes <code>addListenEndpoint</code> on
     * <code>listenContext</code> once, passing a <code>ListenEndpoint</code>
     * as described below.  If <code>addListenEndpoint</code> throws an
     * exception, then this method throws that exception.  Otherwise, this
     * method returns an <code>SslEndpoint</code> instance with the host name
     * described above, the TCP port number bound by the listen operation
     * represented by the {@link net.jini.jeri.ServerEndpoint.ListenHandle
     * ListenHandle} returned by <code>addListenEndpoint</code>, and the same
     * <code>SocketFactory</code> as this <code>SslServerEndpoint</code>. <p>
     *
     * The <code>ListenEndpoint</code> passed to
     * <code>addListenEndpoint</code> represents the server subject, server
     * principals, TCP port number, and <code>ServerSocketFactory</code> of
     * this <code>SslServerEndpoint</code>.  Its methods behave as follows: <p>
     *
     * {@link net.jini.jeri.ServerEndpoint.ListenEndpoint#listen ListenHandle
     * listen(RequestDispatcher)}:
     *
     * <blockquote>
     *
     * Listens for requests received on this endpoint's TCP port, dispatching
     * them to the supplied <code>RequestDispatcher</code> in the form of
     * {@link InboundRequest} instances. <p>
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
     * could result in a <code>SecurityException</code>. In addition, for each
     * server principal in this endpoint, the security manager's {@link
     * SecurityManager#checkPermission checkPermission} method will be invoked
     * with an {@link AuthenticationPermission} containing the server
     * principal and the <code>listen</code> action; this could also result in
     * a <code>SecurityException</code>.  Furthermore, before a given
     * <code>InboundRequest</code> gets dispatched to the supplied request
     * dispatcher, the security manager's {@link SecurityManager#checkAccept
     * checkAccept} method must have been successfully invoked in the security
     * context of this <code>listen</code> invocation with the remote IP
     * address and port of the <code>Socket</code> used to receive the
     * request, and if the server authenticated itself to the client, the
     * security manager's <code>checkPermission</code> method must have been
     * successfully invoked in the same context with an
     * <code>AuthenticationPermission</code> containing that authenticated
     * server principal as local principal, the client's authenticated
     * principal (if any) as peer principal, and the <code>accept</code>
     * action. The <code>checkPermissions</code> method of the dispatched
     * <code>InboundRequest</code> also performs these latter security checks.
     * (Note that in some cases, the implementation may carry out some of
     * these security checks indirectly, such as through invocations of
     * <code>ServerSocket</code>'s constructors or <code>accept</code>
     * method.) <p>
     *
     * Requests will be dispatched in a {@link PrivilegedAction} wrapped by a
     * {@link SecurityContext} obtained when this method was invoked, with the
     * {@link AccessControlContext} of that <code>SecurityContext</code> in
     * effect. <p>
     *
     * Dispatched requests will implement {@link
     * InboundRequest#populateContext populateContext} to populate the given
     * collection with an element that implements the {@link ClientHost}
     * interface, and an element that implements the {@link ClientSubject}
     * interface. The <code>ClientHost</code> element implements {@link
     * ClientHost#getClientHost getClientHost} to return the IP address of the
     * <code>Socket</code> that the request was received over (see {@link
     * Socket#getInetAddress}). <p>
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
     * <code>SecurityException</code>. In addition, for each server principal
     * in this endpoint, the security manager's {@link
     * SecurityManager#checkPermission checkPermission} method will be invoked
     * with an {@link AuthenticationPermission} containing the server
     * principal and the <code>listen</code> action; this could also result in
     * a <code>SecurityException</code>. <p>
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
     * object is also a <code>ListenEndpoint</code> produced by an
     * <code>SslServerEndpoint</code>, and the two listen endpoints both have
     * server subjects that compare equal using <code>==</code>; have server
     * principals that are either both <code>null</code> or are equal when
     * compared as the elements of a {@link Set}; have the same values for TCP
     * port; and have server socket factories that are either both
     * <code>null</code>, or have the same actual class and are equal.
     *
     * </blockquote>
     *
     * @throws SecurityException if there is a security manager, and either its
     *	       {@link SecurityManager#checkListen checkListen} method fails,
     *	       or <code>serverHost</code> is <code>null</code> and the security
     *	       manager's {@link SecurityManager#checkConnect checkConnect}
     *	       method fails; or if the calling thread does not have permission
     *	       to authenticate as each of the endpoint's server principals when
     *	       listening for connections
     * @throws IllegalArgumentException if an invocation of the
     *	       <code>addListenEndpoint</code> method on the supplied
     *	       <code>ListenContext</code> returns a <code>ListenCookie</code>
     *	       that does not correspond to the <code>ListenEndpoint</code> that
     *	       was passed to it
     * @throws NullPointerException if <code>listenContext</code> is
     *	       <code>null</code>
     * @throws UnknownHostException if this instance's server host 
     *	       is <code>null</code> and <code>InetAddress.getLocalHost</code>
     *	       throws an <code>UnknownHostException</code>
     * @throws UnsupportedConstraintException if the server subject is missing
     *	       any of the endpoint's server principals or the associated
     *	       credentials
     */
    public Endpoint enumerateListenEndpoints(ListenContext listenContext)
	throws IOException
    {
	return impl.enumerateListenEndpoints(listenContext);
    }
}
