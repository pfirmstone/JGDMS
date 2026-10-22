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

import org.apache.river.jeri.internal.ListenBacklog;
import org.apache.river.jeri.internal.http.ConnectionTimer;
import org.apache.river.jeri.internal.http.HttpServerConnection;
import org.apache.river.jeri.internal.http.HttpServerManager;
import org.apache.river.jeri.internal.http.HttpSettings;
import org.apache.river.logging.Levels;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Collection;
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
import net.jini.io.context.AcknowledgmentSource;
import net.jini.io.context.ClientHost;
import net.jini.io.context.ClientSubject;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.jeri.connection.ServerConnectionManager;
import net.jini.security.AuthenticationPermission;
import net.jini.security.SecurityContext;

/**
 * An implementation of {@link ServerEndpoint} that uses HTTPS (HTTP over
 * TLS/SSL) to support invocation constraints for communication through
 * firewalls. <p>
 *
 * Instances of this class are intended to be created for use with the {@link
 * BasicJeriExporter} class. Calls to {@link #enumerateListenEndpoints
 * enumerateListenEndpoints} return instances of {@link HttpsEndpoint}. <p>
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
 * The host name specified when creating an <code>HttpsServerEndpoint</code>
 * instance controls the host name that will be contained in
 * <code>HttpsEndpoint</code> instances produced when {@link
 * #enumerateListenEndpoints enumerateListenEndpoints} is invoked to listen on
 * the server endpoint; the host name does not affect the behavior of the
 * listen operation itself, which listens on all of the local system's network
 * addresses. If the host name in the server endpoint is <code>null</code>,
 * then the host name in the <code>HttpsEndpoint</code> instances that it
 * produces will be the default server host name, which is the IP address
 * string of the {@link InetAddress} returned by {@link
 * InetAddress#getLocalHost InetAddress.getLocalHost} when
 * <code>enumerateListenEndpoints</code> is invoked. <p>
 *
 * This class permits specifying a {@link SocketFactory} for creating the
 * {@link Socket} instances that the associated <code>HttpsEndpoint</code>
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
 * net.jini.jeri.ServerEndpoint.ListenEndpoint ListenEndpoint} instances.
 *
 * 
 * @see HttpsEndpoint
 * @see ConfidentialityStrength
 * @since 2.0
 *
 *  <!-- Implementation Specifics -->
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
 *	  logger performed by the HttpsServerEndpoint class at different
 *	  logging levels">
 *
 * <caption><b><code>
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
 *	  logger performed by the HttpsServerEndpoint class at different
 *	  logging levels">
 *
 * <caption><b><code>
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
 *	used before expiring. The default is 32 minutes. The value used should be
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
 * <li> <code>org.apache.river.jeri.https.idleServerConnectionTimeout</code> - The
 *	number of milliseconds to retain idle server-side HTTPS connections
 *	before closing them. The default is the idle client-side connection
 *	timeout (as specified by the
 *	<code>org.apache.river.jeri.https.idleConnectionTimeout</code> system
 *	property) plus <code>30000</code>.
 * <li> <code>org.apache.river.jeri.https.responseAckTimeout</code> - The number of
 *	milliseconds to wait for acknowledgments from {@link
 *	AcknowledgmentSource} instances. The default is <code>15000</code>.
 * <li> <code>org.apache.river.jeri.listenBacklog</code> - The accept backlog
 *	requested when the server endpoint binds its listening socket, which is
 *	the number of connections the operating system queues for a listener
 *	that has not yet accepted them. The default is
 *	<code>Integer.MAX_VALUE</code>, which each operating system clamps to
 *	its own maximum (on Linux, <code>net.core.somaxconn</code>); the JDK
 *	default of 50 would drop or refuse connections from more than about 50
 *	simultaneous connectors. A value below 1 selects the JDK default. A
 *	large backlog lets a remote peer fill kernel buffers, up to the
 *	operating system limit, with connections that are not yet accepted;
 *	operators can lower the value to bound that. A custom server socket
 *	factory receives the value as the <code>backlog</code> argument.
 * <li> <code>org.apache.river.jeri.ssl.handshakeSummaryInterval</code> -
 *	The number of milliseconds between the summary lines, written at
 *	INFO, of the TLS handshakes that failed on a server endpoint.
 *	Each failed handshake is logged at FINE only, because a peer
 *	that is not authenticated can cause as many as it likes; the
 *	summary gives the number of connections that failed in each way,
 *	and is written when a handshake fails and the interval has
 *	passed since the last summary. The default is 30000. A value of
 *	0 or less turns the summary off.
 * <li> <code>org.apache.river.jeri.ssl.serverHandshakeTimeout</code> -
 *	The number of milliseconds that a server has to complete the TLS
 *	handshake with a connection, counted from the moment the
 *	connection is accepted. When it has passed, the server closes the
 *	connection. The limit covers every wait of the handshake, reads
 *	and writes, and the time that a connection spends waiting for its
 *	turn and for its first record (see maxServerHandshakes), and does
 *	not grow when the peer sends a byte at a time. A connection is
 *	given its turn to work only if at least serverHandshakeMinBudget
 *	of the time is left, and is closed when that much is no longer
 *	left while it waits. It ends when the handshake finishes; it does
 *	not limit what the peer does afterwards. The default is 10000. A
 *	value of 0 or less removes the limit.
 * <li> <code>org.apache.river.jeri.ssl.serverHandshakeMinBudget</code> -
 *	The number of milliseconds of the serverHandshakeTimeout that must
 *	be left for a server to give a connection its turn to work on a
 *	handshake. A connection that waits in the queue (see
 *	maxServerHandshakes) is closed when the time that is left falls to
 *	this, because a handshake that was begun then would be cut off by
 *	the timeout after it had done its work and its client would have
 *	reconnected for nothing; the closing costs the server nothing, and
 *	counts as a connection that waited too long, not as a handshake
 *	that timed out. The value is at most half of
 *	serverHandshakeTimeout, so that a short timeout is still long
 *	enough to work in. The default is 2000. A value of 0 or less gives
 *	a connection its turn however little time is left.
 * <li> <code>org.apache.river.jeri.ssl.handshakeTimeout</code> - The
 *	number of milliseconds that a client allows for the TLS
 *	handshake with a server, counted from its start. The default is
 *	60000. It applies to client connections only; a server uses
 *	org.apache.river.jeri.ssl.serverHandshakeTimeout.
 * <li> <code>org.apache.river.jeri.ssl.maxServerHandshakes</code> -
 *	The largest number of TLS handshakes that the server endpoints of
 *	the Java virtual machine work on at once, all together. Until a
 *	handshake is finished the peer has not been authenticated, and a
 *	handshake that works costs a file descriptor and about 60 KiB of
 *	memory. A connection that is accepted waits for its turn to work,
 *	and a connection that uses an SSLEngine takes a turn only when its
 *	first TLS record, the one that carries the start of a ClientHello,
 *	has arrived, which the server reads while the connection waits. A
 *	peer that sends nothing, or a part of the record only, therefore
 *	holds a place in the queue (see maxServerHandshakeQueue) and never
 *	one of these; one that sends something that is not the start of a
 *	TLS ClientHello is closed as soon as that is seen; and one that
 *	closes the connection is removed at once. A connection that waits
 *	holds its socket, a virtual thread and the bytes of its first
 *	record that have arrived, at most 16389. The Java runtime reads
 *	for itself the handshakes done with an SSLSocket, which are those
 *	of HTTPS and of endpoints with a server socket factory, so there a
 *	silent peer takes one of these places until its handshake times
 *	out. The limit is shared by all the server endpoints of the
 *	virtual machine, HTTPS ones included, because the file descriptors
 *	and the memory are the process's. The default is the smaller of
 *	512 and an eighth of the number of file descriptors that the
 *	process may open (see maxFileDescriptors). A value below 1 selects
 *	the default.
 * <li> <code>org.apache.river.jeri.ssl.maxServerHandshakeQueue</code> -
 *	The largest number of accepted connections that wait for one of
 *	the maxServerHandshakes handshakes to be free, all server
 *	endpoints of the Java virtual machine together. A connection that
 *	waits holds a place in this queue from its accept until its
 *	handshake ends, whether it has yet sent its first record or not
 *	(see maxServerHandshakes). When that many connections are
 *	admitted, working or waiting, each endpoint stops accepting
 *	connections until a handshake ends, and the operating system
 *	queues the connections that arrive, up to the listen backlog (see
 *	listenBacklog); they are neither refused nor closed. Each
 *	endpoint's accept loop also holds one of the places while it waits
 *	in accept, so the places left for connections are fewer by the
 *	number of endpoints. The waiting endpoints are served in the order
 *	in which they began to wait, so none is passed over for ever. The
 *	default is the smaller of 4096 and a quarter of the number of file
 *	descriptors that the process may open, less maxServerHandshakes,
 *	and at least 0; the sockets that the process admits are therefore
 *	at most a quarter of its descriptors however many endpoints it
 *	has. A value below 0 selects the default.
 * <li> <code>org.apache.river.jeri.ssl.maxFileDescriptors</code> - The
 *	number of file descriptors that the process may open, from which
 *	the defaults of maxServerHandshakes and maxServerHandshakeQueue
 *	are computed. The default is the number that the Java virtual
 *	machine reports, where it can (on Linux and macOS), and 16384
 *	where it cannot (on Windows).
 * <li> <code>org.apache.river.jeri.ssl.serverNamedGroups</code> -
 *	The key exchange groups that a server negotiates, in order of
 *	preference, separated by commas. A client chooses among the groups
 *	that a server enables, and the groups that the Java virtual
 *	machine enables by default cost a server very different amounts of
 *	processor time before it has authenticated the client: about 2 ms
 *	for x25519, 10 ms for secp521r1 and 27 ms for ffdhe8192. A client
 *	that offers none of the groups is refused. The default is
 *	X25519MLKEM768,x25519,secp256r1, which refuses a peer that offers
 *	only secp384r1, as peers that follow the CNSA or FIPS profiles
 *	may; set the property to include secp384r1 for them. The names are
 *	those of SSLParameters.setNamedGroups, and are checked when the
 *	server starts to listen: a name that the Java virtual machine does
 *	not support is not offered and is logged at WARNING, and if none
 *	of the names is supported, so that no client could agree with the
 *	server on a group, the groups are not restricted and that is
 *	logged at WARNING. An empty value does not restrict the groups.
 *	The groups apply to the SSLEngine and the SSLSocket paths alike.
 * <li> <code>org.apache.river.jeri.ssl.serverHandshakeCpuPermits</code> -
 *	The number of TLS handshakes, in the whole Java virtual machine,
 *	that may be computing the first flight of the server, its key
 *	exchange and signatures, at one time. All the server endpoints
 *	share the permits, so a virtual machine with several endpoints
 *	lets a peer that floods them with replayed ClientHellos use no
 *	more processors than one with a single endpoint. A handshake done
 *	with an SSLEngine holds a permit from the moment the server has
 *	read a ClientHello until its first flight has been computed,
 *	before it is written, so a peer that replays a ClientHello, reads
 *	the flight and closes costs the server no more than the permits
 *	allow, although each of its connections lasts a few milliseconds,
 *	and a peer that stops reading holds no permit. A handshake done with
 *	an SSLSocket, which is those of HTTPS and of endpoints with a server
 *	socket factory, takes a permit only when the peer has sent the first
 *	record of a ClientHello, so a peer that sends nothing, or something
 *	that is not TLS, takes none. The Java runtime does its reads and
 *	writes inside one call that cannot be split, so the permit is given
 *	back when the server has written its first flight and next reads from
 *	the peer, which is seen on the socket that the SSLSocket is layered
 *	over; a peer that replays a ClientHello and then stalls holds a permit
 *	for the work of the first flight, and one that does not read the
 *	flight, so that the write blocks, until the handshake times out. The
 *	work that follows a HelloRetryRequest is not within a permit on this
 *	path. There are as many permits for SSLSockets, kept apart so that
 *	peers that block the write of the flight cannot hold up the endpoints
 *	that use SSLEngines; among the endpoints that use SSLSockets, this
 *	many such peers stop the others from starting a handshake until they
 *	time out. A handshake waits
 *	for a permit within its serverHandshakeTimeout. The default is the
 *	larger of 2 and a quarter of the number of processors. A value
 *	below 1 selects the default.
 * </ul>
 */
public final class HttpsServerEndpoint implements ServerEndpoint {

    /* -- Fields -- */

    /** Server logger */
    static final Logger logger = Utilities.SERVER_LOGGER;

    /** Manager for HTTP server connections. */
    static final HttpServerManager httpServerManager;

    /** Times out idle connections. */
    static final ConnectionTimer connectionTimer;

    static {
	HttpSettings hs = HttpsEndpoint.getHttpSettings();
	httpServerManager = new HttpServerManager(hs.getResponseAckTimeout());
	connectionTimer = new ConnectionTimer(hs.getServerConnectionTimeout());
    }

    /** Implementation delegate. */
    private final HttpsServerEndpointImpl impl;

    /* -- Methods -- */

    /**
     * Returns an HTTPS server endpoint for the specified port. Uses the
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
     * @return an <code>HttpsServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     */
    public static HttpsServerEndpoint getInstance(int port) {
	HttpsServerEndpoint se = new HttpsServerEndpoint(null, null, null, check(port), null, null, false);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }
    
    /**
     * Returns an HTTPS server endpoint for the specified server host and port.
     * Uses the subject associated with the current access control context, the
     * principals in the subject with appropriate public and private
     * credentials for which the caller has {@link AuthenticationPermission} to
     * listen, and <code>null</code> socket factories to create default
     * sockets. A <code>serverHost</code> of <code>null</code> requests that
     * {@link #enumerateListenEndpoints enumerateListenEndpoints} compute the
     * default server host. A <code>port</code> of <code>0</code> requests
     * listening on any free port.
     *
     * @param serverHost the name that clients should use to connect to this
     *	      server, or <code>null</code> to use the default
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @return an <code>HttpsServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     */
    public static HttpsServerEndpoint getInstance(String serverHost, int port) {
	HttpsServerEndpoint se = new HttpsServerEndpoint(
	    null, null, serverHost, check(port), null, null, false);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns an HTTPS server endpoint for the specified server host, port,
     * and socket factories. Uses the subject associated with the current
     * access control context, and the principals in the subject with
     * appropriate public and private credentials for which the caller has
     * {@link AuthenticationPermission} to listen. A <code>serverHost</code> of
     * <code>null</code> requests that {@link #enumerateListenEndpoints
     * enumerateListenEndpoints} compute the default server host. A
     * <code>port</code> of <code>0</code> requests listening on any free
     * port. A <code>socketFactory</code> of <code>null</code> uses default
     * sockets in the associated {@link HttpsEndpoint}. A
     * <code>serverSocketFactory</code> of <code>null</code> uses default
     * server sockets.
     *
     * @param serverHost the name that clients should use to connect to this
     *	      server, or <code>null</code> to use the default
     * @param port the port on which to listen for connections, or
     *	      <code>0</code> for any free port
     * @param socketFactory the socket factory for use in the associated
     *	      <code>HttpsEndpoint</code> instances, or <code>null</code>
     * @param serverSocketFactory the server socket factory, or
     *	      <code>null</code>
     * @return an <code>HttpsServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     */
    public static HttpsServerEndpoint getInstance(
	String serverHost,
	int port,
	SocketFactory socketFactory,
	ServerSocketFactory serverSocketFactory)
    {
	HttpsServerEndpoint se = new HttpsServerEndpoint(null, null, serverHost, check(port),
				       socketFactory, serverSocketFactory, false);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns an HTTPS server endpoint for the specified server subject,
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
     * @return an <code>HttpsServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     * @throws NullPointerException if <code>serverPrincipals</code> is not
     *	       <code>null</code> and any of its elements are <code>null</code>
     */
    public static HttpsServerEndpoint getInstance(
	Subject serverSubject,
	X500Principal[] serverPrincipals,
	String serverHost,
	int port)
    {
	HttpsServerEndpoint se = new HttpsServerEndpoint(serverSubject, serverPrincipals,
				       serverHost, check(port), null, null, false);
        logger.log(Level.FINE, "created {0}", se);
        return se;
    }

    /**
     * Returns an HTTPS server endpoint for the specified server subject,
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
     * HttpsEndpoint}. A <code>serverSocketFactory</code> of <code>null</code>
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
     *	      <code>HttpsEndpoint</code> instances, or <code>null</code>
     * @param serverSocketFactory the server socket factory, or
     *	      <code>null</code>
     * @return an <code>HttpsServerEndpoint</code> instance
     * @throws IllegalArgumentException if <code>port</code> is negative or
     *	       greater than <code>65535</code>
     * @throws NullPointerException if <code>serverPrincipals</code> is not
     *	       <code>null</code> and any of its elements are <code>null</code>
     */
    public static HttpsServerEndpoint getInstance(
	Subject serverSubject,
	X500Principal[] serverPrincipals,
	String serverHost,
	int port,
	SocketFactory socketFactory,
	ServerSocketFactory serverSocketFactory)
    {
	HttpsServerEndpoint se = new HttpsServerEndpoint(serverSubject, serverPrincipals,
				       serverHost, check(port), socketFactory,
				       serverSocketFactory, false);
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
    private HttpsServerEndpoint(Subject serverSubject, X500Principal[] serverPrincipals, String serverHost, int port, SocketFactory socketFactory, ServerSocketFactory serverSocketFactory, boolean compression)
    {
	impl = new HttpsServerEndpointImpl(
	    this, serverSubject, serverPrincipals,
	    serverHost, port, socketFactory, serverSocketFactory);
    }

    /**
     * Returns the host name that will be used in {@link HttpsEndpoint}
     * instances created by listening on this object, or <code>null</code> if
     * {@link #enumerateListenEndpoints enumerateListenEndpoints} will
     * use the default server host.
     *
     * @return the host name to use in <code>HttpsEndpoint</code> instances
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
     * Returns the socket factory that the associated {@link HttpsEndpoint}
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
	return "HttpsServerEndpoint" + impl.fieldsToString();
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
     * same actual class and are equal; and have server socket factories that
     * are either both <code>null</code>, or have the same actual class and are
     * equal.
     */
    public boolean equals(Object object) {
	if (this == object) {
	    return true;
	} else {
	    return object instanceof HttpsServerEndpoint &&
		impl.equals(((HttpsServerEndpoint) object).impl);
	}
    }

    /**
     * Passes the {@link net.jini.jeri.ServerEndpoint.ListenEndpoint
     * ListenEndpoint} for this <code>HttpsServerEndpoint</code> to
     * <code>listenContext</code>, which will ensure an active listen
     * operation on the endpoint, and returns an <code>HttpsEndpoint</code>
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
     * method returns an <code>HttpsEndpoint</code> instance with the host name
     * described above, the TCP port number bound by the listen operation
     * represented by the {@link net.jini.jeri.ServerEndpoint.ListenHandle
     * ListenHandle} returned by <code>addListenEndpoint</code>, and the same
     * <code>SocketFactory</code> as this <code>HttpsServerEndpoint</code>. <p>
     *
     * The <code>ListenEndpoint</code> passed to
     * <code>addListenEndpoint</code> represents the server subject, server
     * principals, TCP port number, and <code>ServerSocketFactory</code> of
     * this <code>HttpsServerEndpoint</code>.  Its methods behave as follows:
     * <p>
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
     * <code>HttpsServerEndpoint</code>, and the two listen endpoints both have
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

    /** Implementation delegate. */
    private static final class HttpsServerEndpointImpl
	extends SslServerEndpointImpl
    {
	HttpsServerEndpointImpl(ServerEndpoint serverEndpoint, Subject serverSubject, X500Principal[] serverPrincipals, String serverHost, int port, SocketFactory socketFactory, ServerSocketFactory serverSocketFactory)
	{
	    super(serverEndpoint, new HttpsListenEndpoint(serverSubject, serverPrincipals,
		  serverHost, port, socketFactory, serverSocketFactory));
	}

	@Override
	Endpoint createEndpoint(String serverHost, SslListenCookie cookie) {
	    return HttpsEndpoint.getInstance(serverHost, cookie.getPort(), getSocketFactory());
	}

	/** Implements ListenEndpoint */
	private static final class HttpsListenEndpoint extends SslListenEndpoint {
            
            HttpsListenEndpoint(Subject serverSubject,
                                X500Principal[] serverPrincipals,
                                String serverHost,
                                int port,
                                SocketFactory socketFactory,
                                ServerSocketFactory serverSocketFactory)
            {
                super(serverSubject, serverPrincipals, serverHost,
                        port, socketFactory, serverSocketFactory);
            }

            /**
             * The HTTPS transport drives TLS over an {@code SSLSocket} layered
             * on an HTTP-proxy tunnel, so it must NOT use the
             * {@code ServerSocketChannel}-backed socket (which would route
             * accepted connections down the SSLEngine keystone path).  Bind a
             * plain {@code ServerSocket} (channel-less accepted sockets keep the
             * SSLSocket path in {@code SslServerConnection}).
             */
            @Override
            java.net.ServerSocket createServerSocket() throws IOException {
                int backlog = ListenBacklog.get();
                return serverSocketFactory != null
                    ? serverSocketFactory.createServerSocket(port, backlog)
                    : new java.net.ServerSocket(port, backlog);
            }

	    @Override
	    SslListenHandle createListenHandle(RequestDispatcher requestDispatcher,
					    ServerSocket serverSocket)
		throws IOException
	    {
		return new HttpsListenHandle(requestDispatcher, serverSocket, this);
	    }
	}

	/** Implements ListenHandle */
	private static final class HttpsListenHandle extends SslListenHandle {

	    HttpsListenHandle(RequestDispatcher requestDispatcher,
                                ServerSocket serverSocket, 
                                HttpsListenEndpoint listenEndpoint)
		throws IOException
	    {
		super(requestDispatcher, serverSocket, listenEndpoint);
	    }

            @Override
	    SslServerConnection serverConnection(Socket socket, Handshake handshake)
		throws IOException
	    {
		return new HttpsServerConnection(this, socket, handshake);
	    }

            @Override
	    void handleConnection(SslServerConnection connection,
				  RequestDispatcher requestDispatcher)
	    {
		try {
		    HttpServer server = new HttpServer(
			connection.sslSocket,
			new HttpsRequestDispatcher(
			    requestDispatcher,
			    (HttpsServerConnection) connection),
			(HttpsServerConnection) connection);
                    server.start();
		} catch (IOException e) {
		    if (logger.isLoggable(Level.INFO)) {
			logThrow(logger, Level.INFO,
				 HttpsListenHandle.class, "handleConnection",
				 "{0} throws", new Object[] { connection },
				 e);
		    }
		    try {
			connection.close();
		    } catch (IOException e2) {
		    }
		}
	    }
	}

	/**
	 * An HTTP server connection that gets its client host from the HTTPS
	 * connection and closes the HTTPS connection when it shuts down.
	 */
	private static final class HttpServer extends HttpServerConnection {

	    private final HttpsServerConnection connection;

	    HttpServer(Socket socket,
		       RequestDispatcher requestDispatcher,
		       HttpsServerConnection connection)
		throws IOException
	    {
		super(socket, requestDispatcher, httpServerManager);
		this.connection = connection;
	    }
            
            @Override
            public void start(){
                connection.setHttpServer(this, null);
                super.start();
            }

	    /** Not called -- handled by inbound request */
	    protected void checkPermissions() {
		throw new AssertionError();
	    }

	    /** No additional context -- supplied by inbound request */
	    protected void populateContext(Collection context) { }

	    /** Not called -- handled by inbound request */
	    protected InvocationConstraints checkConstraints(
		InvocationConstraints constraints)
	    {
		throw new AssertionError();
	    }

	    /** Schedules the connection to close after an idle timeout. */
	    protected void idle() {
		connectionTimer.scheduleTimeout(this, false);
	    }

	    /** Cancels the idle timeout. */
	    protected void busy() {
		connectionTimer.cancelTimeout(this);
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
		connectionTimer.cancelTimeout(this);
		return result;
	    }
	}

	/**
	 * Implements RequestDispatcher using the specified RequestDispatcher
	 * and ServerConnection.
	 */
	private static final class HttpsRequestDispatcher extends Utilities
	    implements RequestDispatcher
	{
	    private final RequestDispatcher requestDispatcher;
	    private final HttpsServerConnection connection;

	    HttpsRequestDispatcher(RequestDispatcher requestDispatcher,
				   HttpsServerConnection connection)
	    {
		this.requestDispatcher = requestDispatcher;
		this.connection = connection;
	    }

            @Override
	    public void dispatch(InboundRequest request) {
		try {
		    InboundRequest wrappedRequest =
			new HttpsInboundRequest(
			    request, connection,
			    connection.processRequestData(
				request.getRequestInputStream(),
				request.getResponseOutputStream()));
		    wrappedRequest.checkPermissions();
		    requestDispatcher.dispatch(wrappedRequest);
		} catch (SecurityException e) {
		    if (logger.isLoggable(Level.INFO)) {
			logThrow(
			    logger, Level.INFO,
			    HttpsRequestDispatcher.class, "dispatch",
			    "{0} throws", new Object[] { connection }, e);
		    }
		}
	    }
	}

	/** Implements InboundRequest */
	private static final class HttpsInboundRequest
	    implements InboundRequest
	{
	    private final InboundRequest request;
	    private final ServerConnection connection;
	    private final InboundRequestHandle handle;

	    HttpsInboundRequest(InboundRequest request,
				ServerConnection connection,
				InboundRequestHandle handle)
	    {
		this.request = request;
		this.connection = connection;
		this.handle = handle;
	    }

	    /* Delegate to connection */
	    public void checkPermissions() {
		connection.checkPermissions(handle);
	    }

	    /* Delegate to connection */
	    public InvocationConstraints checkConstraints(
		InvocationConstraints constraints)
		throws UnsupportedConstraintException
	    {
		return connection.checkConstraints(handle, constraints);
	    }

	    /* Delegate to both the underlying request and the connection. */
	    public void populateContext(Collection context) {
		request.populateContext(context);
		connection.populateContext(handle, context);
	    }

	    /* Delegate to underlying request */
	    public InputStream getRequestInputStream() {
		return request.getRequestInputStream();
	    }

	    /* Delegate to underlying request */
	    public OutputStream getResponseOutputStream() {
		return request.getResponseOutputStream();
	    }

	    /* Delegate to underlying request */
	    public void abort() {
		request.abort();
	    }
	}

	/** Implements ServerConnection. */
	private static final class HttpsServerConnection extends SslServerConnection {

	    /** The HTTP server connection */
	    private HttpServer httpServer;

	    /** Creates a server connection. */
	    HttpsServerConnection(HttpsListenHandle listenHandle, Socket socket,
				  SslListenHandle.Handshake handshake)
		throws IOException
	    {
		super(listenHandle, socket, handshake);
	    }

	    void closeInternal(boolean removeFromListener) throws IOException {
		synchronized (this) {
		    if (closed) {
			return;
		    }
		}
		super.closeInternal(removeFromListener);
		/* Close the associated HTTP connection */
                HttpServer httpServ = getHttpServer();
		if (httpServ != null) {
		    httpServ.shutdown(true);
		    setHttpServer(null, httpServ);
		}
	    }

            /**
             * @return the httpServer
             */
            synchronized HttpServer getHttpServer() {
                return httpServer;
            }
 
            /**
             * Atomic, only set's server if it's reference is equal to the expected 
             * currentServer.
             * 
             * @param newServer the httpServer to set
             * @return the HttpServer.
             * 
             */
            synchronized HttpServer setHttpServer(HttpServer newServer, HttpServer currentServer) {
                if (this.httpServer == currentServer){
                    this.httpServer = newServer;
                }
                return this.httpServer;
            }
	}
    }
}
