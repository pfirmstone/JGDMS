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

import org.apache.river.jeri.internal.http.HttpClientConnection;
import org.apache.river.jeri.internal.http.HttpClientManager;
import org.apache.river.jeri.internal.http.HttpClientSocketFactory;
import org.apache.river.jeri.internal.http.HttpSettings;
import org.apache.river.logging.Levels;
import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
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
import net.jini.io.context.AcknowledgmentSource;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.security.AuthenticationPermission;
import net.jini.security.Security;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * An implementation of {@link Endpoint} that uses HTTPS (HTTP over TLS/SSL) to
 * support invocation constraints for communication through firewalls. <p>
 *
 * Instances of this class are intended to be created by the {@link
 * BasicJeriExporter} class when it calls {@link
 * ServerEndpoint#enumerateListenEndpoints enumerateListenEndpoints} on
 * instances of {@link HttpsServerEndpoint}. <p>
 *
 * The {@link SslTrustVerifier} trust verifier may be used for establishing
 * trust in remote proxies that use instances of this class. <p>
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
 * <li> {@link ConnectionAbsoluteTime}
 * <li> {@link ConstraintAlternatives}, if the elements all have the same
 *	actual class and at least one element is supported
 * <li> {@link Delegation#NO}
 * <li> {@link Delegation#YES}, trivially, for anonymous clients
 * <li> {@link DelegationAbsoluteTime}, trivially, when delegation is not
 *	supported
 * <li> {@link Integrity#YES}
 * <li> {@link ServerAuthentication}
 * <li> {@link ServerMinPrincipal}, when it contains a single
 *	<code>X500Principal</code> only
 * </ul> <p>
 *
 * Note that {@link ConnectionRelativeTime} and {@link DelegationRelativeTime}
 * constraints may be used at higher levels, but should be converted to the
 * associated absolute time constraints for use by this class. <p>
 *
 * This class authenticates as a single {@link Principal} if the following
 * items are present in the current {@link Subject}: <p>
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
 * In addition, this class's {@link #newRequest newRequest} method will only
 * authenticate as a given principal if the caller has been granted {@link
 * AuthenticationPermission} with that principal as the local principal, the
 * principal representing the authenticated identity of the server as the peer
 * principal, and the <code>connect</code> action. <p>
 *
 * This class supports remote connections between authenticated servers and
 * authenticated or anonymous clients, and between anonymous servers and
 * anonymous clients. Connections between anonymous servers and authenticated
 * clients are not supported. Because of the suites available in the TLS/SSL
 * protocol, support for {@link Confidentiality#NO} requires the server to
 * authenticate with an RSA public key. <p>
 *
 * This class permits specifying a {@link SocketFactory} for creating the
 * {@link Socket} instances that it uses to make remote connections. These
 * socket factories should not be instances of {@link SSLSocketFactory} or
 * return instances {@link SSLSocket}; it is the responsibility of the
 * implementation to establish a TLS/SSL connection over the socket it obtains
 * from the socket factory. <p>
 *
 * A <code>SocketFactory</code> used with instances of this class should be
 * serializable, and must implement {@link Object#equals Object.equals} to
 * obey the guidelines that are specified for <code>equals</code> methods of
 * {@link Endpoint} instances. <p>
 *
 * This class recognizes the following system properties: <p>
 * 
 * <ul>
 * <li> https.proxyHost - The host name for the secure proxy server. The
 *	default is to use no proxy server.
 * <li> https.proxyPort - The port for the secure proxy server. The default is
 *	443.
 * <li> http.nonProxyHosts - The names of hosts for which direct connections
 *	should be made rather than using the proxy server. Each host name may
 *	contain '<code>*</code>' wildcard characters in any position to match
 *	zero or more of any characters within the name. Multiple host names may
 *	be specified by separating the names with '<code>|</code>'
 *	characters. The default is for all connections to use the proxy server
 *	if one is specified.
 * </ul>
 *
 * 
 * @see HttpsServerEndpoint
 * @see ConfidentialityStrength
 * @see SslTrustVerifier
 * @since 2.0
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * This implementation uses the <a
 * href="http://java.sun.com/j2se/1.4/docs/guide/security/jsse/JSSERefGuide.html"
 * target="_top">Java(TM) Secure Socket Extension (JSSE)</a>. <p>
 *
 * This implementation uses the {@link ConnectionManager} class to manage
 * connections. <p>
 *
 * This implementation uses the following {@link Logger} instances in the
 * <code>net.jini.jeri.ssl</code> namespace: <p>
 *
 * <ul>
 * <li> <a href="#init_logger">init</a> - problems during initialization
 * <li> <a href="#client_logger">client</a> - information about
 *	client-side connections
 * </ul> <p>
 *
 * <a name="init_logger"></a>
 * <table border="1" cellpadding="5" summary="Describes logging to the init
 *	  logger performed by the HttpsEndpoint class at different logging
 *	  levels">
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
 * <a name="client_logger"></a>
 * <table border="1" cellpadding="5" summary="Describes logging to the client
 *	  logger performed by the HttpsEndpoint class at different logging
 *	  levels">
 *
 * <caption halign="center" valign="top"><b><code>
 *	    net.jini.jeri.ssl.client</code></b></caption>
 *     
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> problems with outbound requests
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> exceptions caught involving
 * authentication
 *
 * <tr> <td> {@link Level#FINE FINE} <td> authentication decisions; creating,
 * choosing, expiring, or closing connections; or handling outbound requests
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
 * <li> <code>org.apache.river.jeri.ssl.maxClientSessionDuration</code> - The
 *	maximum number of milliseconds a client-side TLS/SSL session should be
 *	used. The default is 23.5 hours. The value should be smaller than the
 *	maximum server session duration to allow the client to negotiate a new
 *	session before the server timeout occurs.
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
 * <li> <code>org.apache.river.jeri.https.idleConnectionTimeout</code> - The number
 *	of milliseconds to retain idle client-side HTTPS connections before
 *	closing them. The default is <code>15000</code>.
 * <li> <code>org.apache.river.jeri.https.responseAckTimeout</code> - The number of
 *	milliseconds to keep track of acknowledgments that have not yet been
 *	sent for {@link AcknowledgmentSource} instances. The default is
 *	<code>15000</code>.
 * <li> <code>org.apache.river.jeri.https.pingProxyConnections</code> - If
 *      the value is case-insensitive equal to <code>true</code>, then if an
 *      HTTP proxy is being used, ping the server endpoint to verify whether
 *      it is alive and reachable. The ping occurs before the first request
 *      and before each subsequent request which follows the expiration of
 *      the ping proxy timeout period (below) following the previous ping.
 *      When using an HTTP proxy it is often impossible to distinguish
 *      between inability to reach the server endpoint (such as because the
 *      server process refused a connection by the HTTP proxy) and the lack
 *      of response from a delivered request (which might result in an
 *      UnmarshalException). The ping increases the likelihood that the
 *      inability to reach the server endpoint can be explicitly identified.
 *	The default value is <code>false</code>, and no pings are done.
 * <li> <code>org.apache.river.jeri.https.pingProxyConnectionTimeout</code> - The
 *      number of milliseconds from the time a server endpoint was last
 *      pinged before a ping will precede the next request. The default is
 *      <code>Long.MAX_VALUE</code> (essentially meaning, ping only before
 *      the first request).
 * </ul>
 */
public final class HttpsEndpoint
    implements Endpoint, Serializable, TrustEquivalence
{
    /* -- Fields -- */

    private static final long serialVersionUID = -3438786823613900804L;

    /**
     * @serialField serverHost String
     *		    The name of the server host.
     * @serialField port int
     *		    The server port.
     * @serialField socketFactory SocketFactory
     *		    The socket factory for creating sockets, or
     *		    <code>null</code> to use default sockets.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
	new ObjectStreamField("serverHost", String.class),
	new ObjectStreamField("port", int.class),
	new ObjectStreamField("socketFactory", SocketFactory.class)
    };

    /**
     * Maps HttpsEndpointImpls to EndpointInfo instances, each of which
     * contains a representative endpoint equal to the key and a list of idle
     * connections.
     */
    static final Map endpointMap = new HashMap();

    /** How long to leave idle connections around before closing them. */
    static final long IDLE_TIMEOUT;

    /** Executes a Runnable in a system thread. */
    static final Executor systemExecutor = (Executor)
	AccessController.doPrivileged(new GetThreadPoolAction(false));

    /** Manager for HTTP client connections. */
    static final HttpClientManager httpClientManager;

    static {
	HttpSettings hs = getHttpSettings();
	IDLE_TIMEOUT = hs.getConnectionTimeout();
	httpClientManager = new HttpClientManager(hs.getResponseAckTimeout());
    }

    /** Implementation delegate. */
    private transient HttpsEndpointImpl impl;

    /* -- Methods -- */

    /**
     * Returns an HTTPS endpoint for the specified server host and port. Uses a
     * <code>null</code> socket factory to create default sockets.
     *
     * @param serverHost the name of the server host
     * @param port the server port
     * @return an <code>HttpsEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is less than or
     *	       equal to <code>0</code>, or greater than <code>65535</code>
     * @throws NullPointerException if <code>serverHost</code> is
     *	       <code>null</code>
     */
    public static HttpsEndpoint getInstance(String serverHost, int port) {
	return new HttpsEndpoint(serverHost, port, null);
    }

    /**
     * Returns an HTTPS endpoint for the specified server host, port, and
     * socket factory. A <code>socketFactory</code> of <code>null</code> uses
     * default sockets.
     *
     * @param serverHost the name of the server host
     * @param port the server port
     * @param socketFactory the socket factory, or <code>null</code>
     * @return an <code>HttpsEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is less than or
     *	       equal to <code>0</code>, or greater than <code>65535</code>
     * @throws NullPointerException if <code>serverHost</code> is
     *	       <code>null</code>
     */
    public static HttpsEndpoint getInstance(
	String serverHost, int port, SocketFactory socketFactory)
    {
	return new HttpsEndpoint(serverHost, port, socketFactory);
    }

    /** Creates an instance of this class. */
    private HttpsEndpoint(
	String serverHost, int port, SocketFactory socketFactory)
    {
	impl = new HttpsEndpointImpl(this, serverHost, port, socketFactory);
    }

    /**
     * Returns the server host that this endpoint connects to.
     *
     * @return the server host that this endpoint connects to
     */
    public String getHost() {
	return impl.serverHost;
    }

    /**
     * Returns the TCP port that this endpoint connects to.
     *
     * @return the TCP port that this endpoint connects to
     */
    public int getPort() {
	return impl.port;
    }

    /**
     * Returns the socket factory that this endpoint uses to create {@link
     * Socket} instances, or <code>null</code> if it uses default sockets.
     *
     * @return the socket factory that this endpoint uses to create sockets, or
     *	       <code>null</code> if it uses default sockets
     */
    public SocketFactory getSocketFactory() {
	return impl.socketFactory;
    }

    /** Returns a string representation of this object. */
    public String toString() {
	return "HttpsEndpoint" + impl.fieldsToString();
    }

    /* -- Implement Endpoint -- */

    /** Returns a hash code value for this object. */
    public int hashCode() {
	return impl.hashCode();
    }

    /**
     * Two instances of this class are equal if they have the same values for
     * server host and port; and have socket factories that are either both
     * <code>null</code>, or have the same actual class and are equal.
     */
    public boolean equals(Object object) {
	if (this == object) {
	    return true;
	} else {
	    return object instanceof HttpsEndpoint &&
		impl.equals(((HttpsEndpoint) object).impl);
	}
    }

    /**
     * {@inheritDoc} <p>
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
     * <code>SocketFactory</code> of this <code>HttpsEndpoint</code>
     * (which produced this iterator) if non-<code>null</code>, or it
     * will create a <code>Socket</code> directly otherwise.
     *
     * <p>When the implementation needs to connect a
     * <code>Socket</code>, if the host name to connect to (if an HTTP
     * proxy is to be used for the communication, the proxy's host
     * name; otherwise, this <code>HttpsEndpoint</code>'s host name)
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
     * is invoked with this <code>HttpsEndpoint</code>'s host and port;
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
     * <code>HttpsEndpoint</code>'s host and <code>-1</code> for the
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
     * this <code>HttpsEndpoint</code>'s host and <code>-1</code> for
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
     * timed out or was refused or there are unsupported or conflicting
     * constraints.
     *
     * <p>Throws {@link SecurityException} if there is a security
     * manager and an invocation of its <code>checkConnect</code>
     * method fails, or if the caller does not have the appropriate
     * <code>AuthenticationPermission</code>.
     *
     * </blockquote>
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public OutboundRequestIterator newRequest(
	InvocationConstraints constraints)
    {
	return impl.newRequest(constraints);
    }

    /* -- Implement TrustEquivalence -- */

    /**
     * Returns <code>true</code> if the argument is an instance of
     * <code>HttpsEndpoint</code> with the same values for server host and
     * port; and either both this instance and the argument have
     * <code>null</code> socket factories, or the factories have the same
     * actual class and are equal; and returns <code>false</code> otherwise.
     */
    public boolean checkTrustEquivalence(Object obj) {
	if (this == obj) {
	    return true;
	} else {
	    return obj instanceof HttpsEndpoint &&
		impl.equals(((HttpsEndpoint) obj).impl);
	}
    }

    /* -- Serialization -- */

    /**
     * Writes the <code>serverHost</code>, <code>port</code>, and
     * <code>socketFactory</code> fields.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField fields = out.putFields();
	fields.put("serverHost", impl.serverHost);
	fields.put("port", impl.port);
	fields.put("socketFactory", impl.socketFactory);
	out.writeFields();
    }

    /**
     * Reads the <code>serverHost</code>, <code>port</code>, and
     * <code>socketFactory</code> fields, checks that <code>serverHost</code>
     * is not <code>null</code> and <code>port</code> is in range, and sets
     * transient fields.
     *
     * @throws InvalidObjectException if <code>serverHost</code> is
     * <code>null</code> or <code>port</code> is out of range
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	ObjectInputStream.GetField fields = in.readFields();
	String serverHost = (String) fields.get("serverHost", null);
	int port = fields.get("port", 0);
	SocketFactory socketFactory =
	    (SocketFactory) fields.get("socketFactory", null);
	if (serverHost == null) {
	    throw new InvalidObjectException("serverHost cannot be null");
	} else if  (port <= 0 || port > 0xFFFF) {
	    throw new InvalidObjectException("Invalid port: " + port);
	}
	impl = new HttpsEndpointImpl(this, serverHost, port, socketFactory);
    }

    /** Returns current HTTP system property settings. */
    static HttpSettings getHttpSettings() {
	return (HttpSettings) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return HttpSettings.getHttpSettings(true);
		}
	    });
    }

    /* -- Classes -- */

    /** Implementation delegate */
    private static final class HttpsEndpointImpl extends SslEndpointImpl {

	/* -- Fields -- */

	/** Time at which the server endpoint was last pinged. */
	private long timeLastVerified;

	/* -- Constructors -- */

	/** Creates an instance of this class. */
	HttpsEndpointImpl(Endpoint endpoint,
			  String serverHost,
			  int port,
			  SocketFactory socketFactory)
	{
	    super(endpoint, serverHost, port, socketFactory);
	}

	/* -- Methods -- */

	/**
	 * Implements Endpoint.newRequest when the constraints are supported.
	 */
	OutboundRequestIterator newRequest(final CallContext callContext) {
	    return new OutboundRequestIterator() {
		private boolean done;
		public synchronized boolean hasNext() {
		    return !done;
		}
		public synchronized OutboundRequest next() throws IOException {
		    if (!hasNext()) {
			throw new NoSuchElementException();
		    }
		    done = true;
		    return getOutboundRequest(callContext);
		}
	    };
	}

	/** Returns an outbound request for the specified call context. */
	OutboundRequest getOutboundRequest(CallContext callContext)
	    throws IOException
	{
	    HttpsConnection connection = null;
	    EndpointInfo info;
	    synchronized (endpointMap) {
		info = (EndpointInfo) endpointMap.get(this);
		if (info == null) {
		    info = new EndpointInfo(this);
		}
	    }
	    connection = info.connect(callContext);
	    boolean ok = false;
	    try {

		// If using a connection through a proxy, and if the
		// connectionTimeout has passed, then ping the server
		// endpoint to verify the connection.  This reduces the
		// likelihood that a connection dropped by the server
		// won't be noticed until after some or all of the data
		// destined for the server has already been sent to the
		// proxy.

		if (connection.usesHttpProxy()) {
		    HttpSettings settings = getHttpSettings();
		    if (settings.getPingProxyConnections()) {
			long timeout = settings.getPingProxyConnectionTimeout();
			long now = System.currentTimeMillis();
			if (now - timeLastVerified > timeout) {
			    pingEndpoint(connection);
			    // The ping succeeded, but the connection
			    // cannot be reused as it was marked idle.
			    connection = info.connect(callContext);
			    timeLastVerified = System.currentTimeMillis();
			}
		    }
		}
		OutboundRequest request = connection.newRequest(callContext);
		ok = true;
		logger.log(Level.FINE, "using {0}", connection);
		return request;
	    } finally {
		if (!ok) {
		    info.noteClosed(connection);
		}
	    }
	}

	/**
	 * Ping the server endpoint to test the connection. Throw (or
	 * pass) an IOException if the server endpoint doesn't
	 * respond. If the ping succeeds, the connection will have been
	 * returned to the idle pool.
	 */
	private void pingEndpoint(HttpsConnection connection)
	    throws IOException
	{
	    logger.log(Level.FINEST, "HTTP pinging {0}", connection);
	    try{
		if (!connection.ping()) {
		    throw new IOException("HTTP ping via proxy failed.");
		}
	    } catch (IOException e) {
		logger.log(Level.FINEST, "pinging HTTP endpoint failed.");
		throw e;
	    }
	}


	public Connection connect(OutboundRequestHandle handle) {
	    throw new AssertionError("wrong connect method called");
	}
    }

    /* Implements Connection and HttpClientSocketFactory. */
    private static final class HttpsConnection extends SslConnection
	implements HttpClientSocketFactory
    {
	/* -- Fields -- */

	/** The associated endpoint. */
	private final HttpsEndpointImpl endpoint;

	/**
	 * The proxy host, or empty string if using a direct connection.
	 */
	final String proxyHost;

	/** The proxy port, ignored if using a direct connection. */
	final int proxyPort;

	/** The current HTTP client connection. */
	private HttpClientConnection httpClient;

	/**
	 * The time this connection was found to be idle by the Reaper thread.
	 * Set to zero each time a new request is initiated.
	 */
	private long idleTime = 0;

	/* -- Methods -- */

	/** Creates a connection. */
	HttpsConnection(HttpsEndpointImpl endpoint,
			CallContext context,
			String serverHost,
			int port,
			String proxyHost,
			int proxyPort,
			SocketFactory socketFactory)
	    throws IOException
	{
	    super(context, serverHost, port, socketFactory);
	    this.endpoint = endpoint;
	    this.proxyHost = proxyHost;
	    this.proxyPort = proxyPort;
	}

	/**
	 * Attempts to create a new socket for the specified call context and
	 * cipher suites.
	 *
	 * @throws SSLException if the requested suites cannot be supported
	 * @throws IOException if an I/O failure occurs
	 */
	void establishNewSocket() throws IOException {
	    if (proxyHost.length() == 0) {
		httpClient = new HttpClient(serverHost, port, this);
	    } else {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
		    sm.checkConnect(serverHost, port);
		}
		try {
		    httpClient = (HttpClient) Security.doPrivileged(
			new PrivilegedExceptionAction() {
			    public Object run() throws IOException {
				return new HttpClient(serverHost, port,
						      proxyHost, proxyPort,
						      HttpsConnection.this);
			    }
			});
		} catch (PrivilegedActionException e) {
		    throw (IOException) e.getCause();
		}
	    }
	}

 	/**
	 * Uses the HTTPClientConnection to create an OutboundRequest object
	 * with the specified call context, and sets the idle time to 0.
	 */
	OutboundRequest newRequest(CallContext callContext)
	    throws IOException
	{
	    OutboundRequest request = new HttpsOutboundRequest(
		httpClient.newRequest(), this, callContext);
	    idleTime = 0;
	    return request;
	}

	/* inherit javadoc */
	public void close() throws IOException {
	    synchronized (this) {
		if (closed) {
		    return;
		}
	    }
	    super.close();
	    /* Close the associated HTTP connection. */
	    if (httpClient != null) {
		httpClient.shutdown(true);
		httpClient = null;
	    }
	}

	/**
	 * Returns true if the recorded idle time is more than IDLE_TIMEOUT
	 * milliseconds before now.  If the recorded idle time is zero, sets
	 * the recorded idle time to now.
	 */
	boolean checkIdle(long now) {
	    if (idleTime == 0) {
		idleTime = now;
		return false;
	    } else {
		return now - idleTime > IDLE_TIMEOUT;
	    }
	}

	/**
	 * Adds this connection to the set of idle connections recorded for the
	 * connection's endpoint.
	 */
	void noteIdle() {
	    synchronized (endpointMap) {
		EndpointInfo info =
		    ((EndpointInfo) endpointMap.get(endpoint));
		if (info != null) {
		    info.noteIdle(this);
		}
		idleTime = 0;
	    }
	}

	/* -- Implement HttpClientSocketFactory -- */

	/**
	 * Creates a plain socket to use to talk to the proxy host, else
	 * creates an SSL socket for a direct connection to the server.
	 */
	public Socket createSocket(String host, int port) throws IOException {
	    Socket s = createPlainSocket(host, port);
	    if (proxyHost.length() == 0) {
		s = setSSLSocket(
		    (SSLSocket) sslSocketFactory.createSocket(
			s, host, port, true));
	    }
	    return s;
	}

	/**
	 * Creates an SSL socket on top of the one the HTTP code used to
	 * connect through the proxy.
	 */
	public Socket createTunnelSocket(Socket s) throws IOException {
	    return setSSLSocket(
		(SSLSocket) sslSocketFactory.createSocket(
		    s, s.getInetAddress().getHostName(), s.getPort(),
		    true /* autoClose */));
	}

	/**
	 * Stores the new socket in the sslSocket field, does a handshake on
	 * it, and returns it.
	 */
	private Socket setSSLSocket(SSLSocket newSocket) throws IOException {
	    sslSocket = newSocket;
	    establishSuites();
	    return sslSocket;
	}


	/**
	 * Return true if this connection is using an HTTP proxy.
	 */
	boolean usesHttpProxy() {
	    return proxyHost.length() != 0;
	}


	/**
	 * Forward a ping request to the underlying HttpClientConnection.
	 */
	private boolean ping() throws IOException {
	    return httpClient.ping();
	}

	/**
	 * Return the proxy host name.
	 */
	protected String getProxyHost() {
	    return proxyHost;
	}
    }

    /**
     * Subclass of HttpClientConnection that closes the associated connection
     * when it shuts down and moves it to the idle list when it becomes idle.
     */
    private static final class HttpClient extends HttpClientConnection {

	/** The associated secure connection. */
	private final HttpsConnection connection;

	HttpClient(String host, int port, HttpsConnection connection)
	    throws IOException
	{
	    super(host, port, connection, httpClientManager);
	    this.connection = connection;
	}

	HttpClient(String targetHost,
		   int targetPort,
		   String proxyHost,
		   int proxyPort,
		   HttpsConnection connection)
	    throws IOException
	{
	    super(targetHost, targetPort, proxyHost, proxyPort,
		  true /* tunnel */, true, connection, httpClientManager);
	    this.connection = connection;
	}

	/** Tells the connection that it is idle. */
	protected void idle() {
	    connection.noteIdle();
	}

	/** Closes the associated secure connection. */
	public boolean shutdown(boolean force) {
	    boolean result = super.shutdown(force);
	    if (result) {
		try {
		    connection.close();
		} catch (IOException e) {
		}
	    }
	    return result;
	}
    }

    /**
     * Manages the open connections associated with endpoints equal to a
     * representative endpoint.
     */
    private static final class EndpointInfo {

	/**
	 * A representative endpoint that equals the endpoint for all the
	 * connections.
	 */
	private final HttpsEndpointImpl endpoint;

	/** The proxy host, or empty string if using a direct connection. */
	private String proxyHost = "";

	/** The proxy port, ignored if using a direct connection. */
	private int proxyPort = 0;

	/** The idle connections for the endpoint. */
	private final List idle = new ArrayList(1);

	/** The connections that are in use for the endpoint. */
	private final List inUse = new ArrayList(1);

	EndpointInfo(HttpsEndpointImpl endpoint) {
	    this.endpoint = endpoint;
	}

	/**
	 * Chooses and returns an idle connection that satisfies the
	 * constraints, removing the connection from the list of idle
	 * connections and adding it to the list of ones in use, else returns
	 * null.
	 */
	HttpsConnection connect(CallContext context)
	    throws IOException
	{
	    HttpSettings settings = getHttpSettings();
	    String phost = settings.getProxyHost(endpoint.serverHost);
	    int pport = (phost.length() == 0) ? 0 : settings.getProxyPort();
	    HttpsConnection result;
	    synchronized (this) {
		List reap = new ArrayList(0);
		if (!(proxyHost.equals(phost) && proxyPort == pport)) {
		    proxyHost = phost;
		    proxyPort = pport;
		    reap.addAll(idle);
		    idle.clear();
		} else {
		    checkIdle(System.currentTimeMillis(), reap);
		}
		for (int i = reap.size(); --i >= 0; ) {
		    try {
			((HttpsConnection) reap.get(i)).close();
		    } catch (IOException e) {
		    }
		}
		result = (HttpsConnection) endpoint.connect(
		    context, Collections.EMPTY_SET, idle);
		if (result != null) {
		    idle.remove(result);
		} else {
		    result = new HttpsConnection(endpoint, context,
						 endpoint.serverHost,
						 endpoint.port, phost, pport,
						 endpoint.socketFactory);
		    result.establishCallContext();
		}
	    }
	    synchronized (endpointMap) {
		EndpointInfo info = (EndpointInfo) endpointMap.get(endpoint);
		if (info == null) {
		    if (endpointMap.isEmpty()) {
			systemExecutor.execute(
			    new Reaper(), "HttpsEndpointReaper");
		    }
		    endpointMap.put(endpoint, this);
		    info = this;
		}
		info.noteInUse(result);
	    }
	    return result;
	}

	/** Adds a connection to the set of connections in use. */
	synchronized void noteInUse(HttpsConnection connection) {
	    inUse.add(connection);
	}

	/** Removes a connection from the set of connections in use. */
	synchronized void noteClosed(HttpsConnection connection) {
	    inUse.remove(connection);
	}

	/**
	 * Removes a connection from the set of connections in use and adds it
	 * to the set of idle connections.
	 */
	synchronized void noteIdle(HttpsConnection connection) {
	    inUse.remove(connection);
	    if (proxyHost.equals(connection.proxyHost) &&
		proxyPort == connection.proxyPort)
	    {
		idle.add(connection);
	    } else {
		try {
		    connection.close();
		} catch (IOException e) {
		}
	    }
	}

	/**
	 * For each connection, calls checkIdle on the connection and, if that
	 * returns true, removes the connection and adds it to the reap list.
	 * Returns true if no in-use or idle connections remain.
	 */
	synchronized boolean checkIdle(long now, List reap) {
	    for (int i = idle.size(); --i >= 0; ) {
		HttpsConnection connection = (HttpsConnection) idle.get(i);
		if (connection.checkIdle(now)) {
		    reap.add(connection);
		    idle.remove(connection);
		}
	    }
	    return idle.isEmpty() && inUse.isEmpty();
	}
    }

    /**
     * Implements OutboundRequest using the specified OutboundRequest,
     * HttpsConnection, and OutboundRequestHandle.
     */
    private static final class HttpsOutboundRequest implements OutboundRequest {
	private final OutboundRequest request;
	private final HttpsConnection connection;
	private final OutboundRequestHandle handle;

	HttpsOutboundRequest(OutboundRequest request,
			     HttpsConnection connection,
			     OutboundRequestHandle handle)
	{
	    this.connection = connection;
	    this.request = request;
	    this.handle = handle;
	}

	/* Delegate to connection */
	public void populateContext(Collection context) {
	    connection.populateContext(handle, context);
	}

	/* Delegate to connection */
	public InvocationConstraints getUnfulfilledConstraints() {
	    return connection.getUnfulfilledConstraints(handle);
	}

	/* Delegate to underlying request */
	public OutputStream getRequestOutputStream() {
	    return request.getRequestOutputStream();
	}

	/* Delegate to underlying request */
	public InputStream getResponseInputStream() {
	    return request.getResponseInputStream();
	}

	/* Delegate to underlying request */
	public boolean getDeliveryStatus() {
	    return request.getDeliveryStatus();
	}

	/* Delegate to underlying request */
	public void abort() {
	    request.abort();
	}
    }

    /**
     * Records idle times in connections and shuts them down if they have been
     * idle for at least IDLE_TIMEOUT milliseconds.
     */
    private static final class Reaper implements Runnable {

	/** Non-private constructor to avoid accessor methods. */
	Reaper() { }

	/**
	 * Sleep for IDLE_TIMEOUT milliseconds.  Then call checkIdle on each
	 * connection, and, if that returns true, remove the connection from
	 * the list of idle connections.  Then shutdown all of the idle
	 * connections that have been collected.  Terminate if no connections
	 * remain, else wait for the next timeout.
	 */
	public void run() {
	    List reap = new ArrayList(1);
	    boolean done;
	    do {
		try {
		    Thread.sleep(IDLE_TIMEOUT);
		} catch (InterruptedException e) {
		    return;
		}
		long now = System.currentTimeMillis();
		synchronized (endpointMap) {
		    for (Iterator iter = endpointMap.values().iterator();
			 iter.hasNext(); )
		    {
			EndpointInfo info = (EndpointInfo) iter.next();
			if (info.checkIdle(now, reap)) {
			    iter.remove();
			}
		    }
		    done = endpointMap.isEmpty();
		}
		for (int i = reap.size(); --i >= 0; ) {
		    try {
			((HttpsConnection) reap.get(i)).close();
		    } catch (IOException e) {
		    }
		}
		reap.clear();
	    } while (!done);
	}
    }
}
