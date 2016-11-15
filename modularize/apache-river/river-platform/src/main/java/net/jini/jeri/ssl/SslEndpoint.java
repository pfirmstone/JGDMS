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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
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
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.security.AuthenticationPermission;
import net.jini.security.Security;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.SslEndpointInternalsAccess;
import org.apache.river.jeri.internal.connection.ConnManagerFactory;
import org.apache.river.jeri.internal.connection.ServerConnManager;
import org.apache.river.logging.Levels;

/**
 * An implementation of {@link Endpoint} that uses TLS/SSL to support
 * invocation constraints for direct communication over TCP sockets. <p>
 *
 * Instances of this class are intended to be created by the {@link
 * BasicJeriExporter} class when it calls {@link
 * ServerEndpoint#enumerateListenEndpoints enumerateListenEndpoints} on
 * instances of {@link SslServerEndpoint}. <p>
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
 * This class uses the <a href="../connection/doc-files/mux.html">Jini
 * extensible remote invocation (Jini ERI) multiplexing protocol</a> to map
 * outgoing requests to socket connections.
 *
 * 
 * @see SslServerEndpoint
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
 *	  logger performed by the SslEndpoint class at different logging
 *	  levels">
 *
 * <caption halign="center" valign="top"><b><code>
 *	    net.jini.jeri.ssl.init</code></b></caption>
 *     
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link Level#WARNING WARNING} <td> problems with initializing JSSE
 * or with registering internal entry points with discovery providers
 *
 * </table> <p>
 *
 * <a name="client_logger"></a>
 * <table border="1" cellpadding="5" summary="Describes logging to the client
 *	  logger performed by the SslEndpoint class at different logging
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
 * </ul>
 */
@AtomicSerial
public final class SslEndpoint
    implements Endpoint, Serializable, TrustEquivalence
{
    /* -- Fields -- */

    private static final long serialVersionUID = 5311538504705775156L;

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

    /* Register a back door interface for use by discovery providers. */
    static {
	SslEndpointInternals.registerDiscoveryBackDoor();
    }

    /** Implementation delegate. */
    transient SslEndpointImpl impl;

    /* -- Methods -- */

    /**
     * Returns a TLS/SSL endpoint for the specified server host and port. Uses
     * a <code>null</code> socket factory to create default sockets.
     *
     * @param serverHost the name of the server host
     * @param port the server port
     * @return an <code>SslEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is less than or
     *	       equal to <code>0</code>, or greater than <code>65535</code>
     * @throws NullPointerException if <code>serverHost</code> is
     *	       <code>null</code>
     */
    public static SslEndpoint getInstance(String serverHost, int port) {
	try {
	    return new SslEndpoint(false, serverHost, port, null);
	} catch (InvalidObjectException ex) {
	    throw new Error("unreachable code");
	}
    }

    /**
     * Returns a TLS/SSL endpoint for the specified server host, port, and
     * socket factory. A <code>socketFactory</code> of <code>null</code> uses
     * default sockets.
     *
     * @param serverHost the name of the server host
     * @param port the server port
     * @param socketFactory the socket factory, or <code>null</code>
     * @return an <code>SslEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is less than or
     *	       equal to <code>0</code>, or greater than <code>65535</code>
     * @throws NullPointerException if <code>serverHost</code> is
     *	       <code>null</code>
     */
    public static SslEndpoint getInstance(
	String serverHost, int port, SocketFactory socketFactory)
    {
	try {
	    return new SslEndpoint(false, serverHost, port, socketFactory);
	} catch (InvalidObjectException ex) {
	    throw new Error("unreachable code");
	}
    }
    
    private SslEndpoint(boolean atomicSerial, 
			String serverHost, 
			int port, 
			SocketFactory socketFactory) throws InvalidObjectException
    {
	this(atomicSerial ? 
		validate(serverHost, port):
		check(serverHost, port),
	     port, 
	     socketFactory
	);
    }
    
    private static String check(String serverHost, int port){
	if (serverHost == null) {
	    throw new NullPointerException("serverHost cannot be null");
	} else if  (port <= 0 || port > 0xFFFF) {
	    throw new IllegalArgumentException("Invalid port: " + port);
	}
	return serverHost;
    }

    /** Creates an instance of this class. */
    private SslEndpoint(
	String serverHost, int port, SocketFactory socketFactory)
    {
	impl = new SslEndpointImpl(this, serverHost, port, socketFactory);
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
	return "SslEndpoint" + impl.fieldsToString();
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
	    return object instanceof SslEndpoint &&
		impl.equals(((SslEndpoint) object).impl);
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
     * <code>SocketFactory</code> of this <code>SslEndpoint</code>
     * (which produced this iterator) if non-<code>null</code>, or it
     * will create a <code>Socket</code> directly otherwise.
     *
     * <p>When the implementation needs to connect a
     * <code>Socket</code>, if the host name to connect to 
     * resolves to multiple addresses (according to {@link
     * InetAddress#getAllByName InetAddress.getAllByName}), it
     * attempts to connect to the first resolved address; if that
     * attempt fails with an <code>IOException</code> or a
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
     * 
     * <p>If there is a security manager:
     *
     * <ul>
     *
     * <li>If a new connection is to be created, the security
     * manager's {@link SecurityManager#checkConnect(String,int)
     * checkConnect} method is invoked with this
     * <code>SslEndpoint</code>'s host and <code>-1</code> for the
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
     * this <code>SslEndpoint</code>'s host and <code>-1</code> for
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
     * <code>SslEndpoint</code> with the same values for server host and port;
     * and either both this instance and the argument have <code>null</code>
     * socket factories, or the factories have the same actual class and are
     * equal; and returns <code>false</code> otherwise.
     */
    public boolean checkTrustEquivalence(Object obj) {
	if (this == obj) {
	    return true;
	} else {
	    return obj instanceof SslEndpoint &&
		impl.equals(((SslEndpoint) obj).impl);
	}
    }

    /** Support EndpointInternals, for use by discovery providers */
    private static final class SslEndpointInternals extends Utilities
	implements EndpointInternals
    {
	/** Register back door. */
	static void registerDiscoveryBackDoor() {
	    final SslEndpointInternals backDoor = new SslEndpointInternals();
	    try {
		Security.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			SslEndpointInternalsAccess.set(backDoor);
			return null;
		    }
		});
	    } catch (RuntimeException e) {
		INIT_LOGGER.log(Level.WARNING,
			       "Problem registering with discovery provider",
			       e);
	    } catch (Error e) {
		INIT_LOGGER.log(Level.WARNING,
			       "Problem registering with discovery provider",
			       e);
	    }
	}

	/* -- Implement EndpointInternals -- */

	public void disableSocketConnect(Endpoint endpoint) {
	    coerce(endpoint).disableSocketConnect = true;
	}

	private static SslEndpointImpl coerce(Endpoint endpoint) {
	    if (!(endpoint instanceof SslEndpoint)) {
		throw new IllegalArgumentException(
		    "Endpoint must be an SslEndpoint: " + endpoint);
	    }
	    return ((SslEndpoint) endpoint).impl;
	}

	private static SslServerEndpointImpl coerce(ServerEndpoint endpoint) {
	    if (!(endpoint instanceof SslServerEndpoint)) {
		throw new IllegalArgumentException(
		    "Server endpoint must be an SslServerEndpoint: " +
		    endpoint);
	    }
	    return ((SslServerEndpoint) endpoint).impl;
	}

        @Override
	public void setConnManagerFactory(Endpoint endpoint,
					  ConnManagerFactory factory)
	{
	    SslEndpointImpl impl = coerce(endpoint);
	    impl.setConnectionManager(factory.create(impl));
	}

        @Override
	public void setServerConnManager(ServerEndpoint endpoint,
					 ServerConnManager manager)
	{
	    coerce(endpoint).setServerConnectionManager(manager);
	}

        @Override
	public InvocationConstraints getUnfulfilledConstraints(
	    OutboundRequestHandle handle)
	{
	    return coerce(handle).getUnfulfilledConstraints();
	}

	private static CallContext coerce(OutboundRequestHandle handle) {
	    if (!(handle instanceof CallContext)) {
		throw new IllegalArgumentException(
		    "Handle must be a CallContext: " + handle);
	    }
	    return (CallContext) handle;
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
	validate(serverHost, port);
	impl = new SslEndpointImpl(this, serverHost, port, socketFactory);
    }
    
    private static String validate(String serverHost, int port) throws InvalidObjectException{
	if (serverHost == null) {
	    throw new InvalidObjectException("serverHost cannot be null");
	} else if  (port <= 0 || port > 0xFFFF) {
	    throw new InvalidObjectException("Invalid port: " + port);
	}
	return serverHost;
    }
    
    public SslEndpoint(AtomicSerial.GetArg arg) throws IOException{
	this(true,
	     arg.get("serverHost", null, String.class),
	     arg.get("port", 0),
	     arg.get("socketFactory", null, SocketFactory.class));
    }
}
