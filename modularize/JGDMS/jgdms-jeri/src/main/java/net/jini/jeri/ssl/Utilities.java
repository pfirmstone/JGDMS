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

import org.apache.river.action.GetPropertyAction;
import org.apache.river.collection.WeakSoftTable;
import java.lang.ref.ReferenceQueue;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.security.auth.AuthPermission;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.security.Security;

/**
 * Provides miscellaneous utilities for the classes in this package.
 *
 * 
 */
abstract class Utilities {

    /* -- Fields -- */

    /**
     * The names of JSSE key exchange algorithms used for anonymous
     * communication.
     */
    private static final String[] ANONYMOUS_KEY_EXCHANGE_ALGORITHMS = {
        //These are not safe from mitm attack, but are here for constraint 
        // functionality purposes, all are disabled in Java 8.
	"ECDH_anon", 
	"DH_anon",
        "DH_anon_EXPORT"
    };
    
    private static final String[] EPHEMERAL_KEY_EXCHANGE_ALGORITHMS = {
        "ECDHE_RSA", //Only ephemeral DH safe from mitm attack.
	"DHE_RSA", //Only ephemeral DH safe from mitm attack.
        "DHE_DSS", //Only ephemeral DH safe from mitm attack.
        "ECDHE_ECDSA" //Only ephemeral DH safe from mitm attack.
    };

    /** The names of JSSE key exchange algorithms that use RSA keys. */
    private static final String[] RSA_KEY_EXCHANGE_ALGORITHMS = {
	"ECDHE_RSA", //Only ephemeral DH safe from mitm attack.
	"DHE_RSA", //Only ephemeral DH safe from mitm attack.
	"RSA",
    };

    /** The names of JSSE key exchange algorithms that use DSA keys. */
    private static final String[] DSA_KEY_EXCHANGE_ALGORITHMS = {
	"DHE_DSS" //Only ephemeral DH safe from mitm attack.
    };

    /** The names of JSSE key exchange algorithms that use DSA keys. */
    private static final String[] ECDSA_KEY_EXCHANGE_ALGORITHMS = {
	"ECDHE_ECDSA" //Only ephemeral DH safe from mitm attack.
    };
    
    /**
     * The names of all the JSSE key exchange algorithms supported by this
     * provider.
     */
    private static final String[] SUPPORTED_KEY_EXCHANGE_ALGORITHMS = {
	"ECDHE_ECDSA",
	"DHE_DSS",
	"ECDHE_RSA",
	"DHE_RSA",
	"RSA",
	"ECDH_anon",
	"DH_anon"
    };	

    /**
     * The name of the JSSE message integrity code algorithm that does not
     * insure integrity.
     */
    private static final String NO_INTEGRITY_MIC_ALGORITHM = "NULL";

    /** The name of the JSSE cipher algorithm that provides no encryption */
    private static final String NO_ENCRYPTION_CIPHER_ALGORITHM = "NULL";

    /** The names of cipher algorithms that do strong encryption */
    private static final String[] STRONG_ENCRYPTION_CIPHERS = {
	"AES_256_GCM",
	"AES_128_GCM"
    };

    /** The names of all cipher algorithms supported by this provider. */
    private static final String[] SUPPORTED_ENCRYPTION_CIPHERS = {
	"AES_256_CBC",
	"AES_128_CBC",
	"AES_256_GCM",
	"AES_128_GCM",
	"RC4_128",
	"3DES_EDE_CBC"
    };

    /** Client logger */
    static final Logger CLIENT_LOGGER =
	Logger.getLogger("net.jini.jeri.ssl.client");

    /** Server logger */
    static final Logger SERVER_LOGGER =
	Logger.getLogger("net.jini.jeri.ssl.server");

    /** Initialization logger */
    static final Logger INIT_LOGGER =
	Logger.getLogger("net.jini.jeri.ssl.init");

    /**
     * Returned by getPermittedKeyAlgorithms when any key algorithm is
     * permitted.
     */
    static final int ANY_KEY_ALGORITHM = 0xffffffff;

    /**
     * Or'ed into the value returned by getPermittedKeyAlgorithms when DSA keys
     * are permitted.
     */
    static final int DSA_KEY_ALGORITHM = 1;

    /**
     * Or'ed into the value returned by getPermittedKeyAlgorithms when RSA keys
     * are permitted.
     */
    static final int RSA_KEY_ALGORITHM = 1 << 1;
    
    /**
     * Or'ed into the value returned by getPermittedKeyAlgorithms when ECDSA keys
     * are permitted.
     */
    static final int ECDSA_KEY_ALGORITHM = 1 << 2;

    /** Stores SSL contexts and auth managers. */
    private static final WeakSoftTable SSL_CONTEXT_MAP = new WeakSoftTable();

    /**
     * The cipher suites supported by the JSSE implementation, or null if not
     * set yet.
     */
    private static String[] supportedCipherSuitesInternal = null;

    /**
     * The cipher suites specified by the user, or null if not specified.  The
     * field is not final to aid testing.
     */
    private static String[] requestedCipherSuites;
    static {
	String value = (String) Security.doPrivileged(
	    new GetPropertyAction("org.apache.river.jeri.ssl.cipherSuites"));
	if (value == null) {
	    requestedCipherSuites = null;
	} else {
	    StringTokenizer tokens = new StringTokenizer(value, ",");
	    final int count = tokens.countTokens();
	    requestedCipherSuites = new String[count];
	    for (int i = 0; i < count; i++) {
		requestedCipherSuites[i] = tokens.nextToken();
	    }
	}
    }

    /** An X.509 certificate factory for creating CertPaths. */
    private static CertificateFactory certFactory;

    /**
     * Represents a principal whose name is not known.  Used, for example, to
     * represent the server principal when the server subject is not available.
     */
    static final Principal UNKNOWN_PRINCIPAL = new Principal() {
        @Override
	public String toString() { return "UNKNOWN_PRINCIPAL"; }
        @Override
	public String getName() { return toString(); }
    };

    /** Constraints that require Integrity.YES. */
    static final InvocationConstraints INTEGRITY_REQUIRED =
	new InvocationConstraints(Integrity.YES, null);

    /** Constraints that prefer Integrity.YES. */
    static final InvocationConstraints INTEGRITY_PREFERRED =
	new InvocationConstraints(null, Integrity.YES);

    /** The secure socket protocol used with JSSE. */
    private static final String SSL_PROTOCOL = (String) Security.doPrivileged(
	new GetPropertyAction("org.apache.river.jeri.ssl.sslProtocol", "TLSv1.2"));

    /** Permission needed to access the current subject. */
    static final AuthPermission GET_SUBJECT_PERMISSION =
	new AuthPermission("getSubject");

    /* -- Methods -- */

    /* -- getSupportedCipherSuites -- */

    /**
     * Returns all the cipher suites supported by the JSSE implementation and
     * this provider.
     */
    static String[] getSupportedCipherSuites() {
	synchronized (SSL_CONTEXT_MAP) {
	    if (supportedCipherSuitesInternal == null) {
		SSLContextInfo info = getServerSSLContextInfo(null, null);
		SSLSocketFactory factory = info.sslContext.getSocketFactory();
		supportedCipherSuitesInternal =
		    getSupportedCipherSuites(factory);
	    }
	    return supportedCipherSuitesInternal;
	}
    }

    /**
     * Returns all the cipher suites supported by the socket factory and this
     * provider.  Uses the requested cipher suites, if any.
     */
    private static String[] getSupportedCipherSuites(
	SSLSocketFactory factory)
    {
	String[] suites;
	if (requestedCipherSuites == null) {
	    suites = factory.getSupportedCipherSuites();
	} else if (requestedCipherSuites.length == 0) {
	    INIT_LOGGER.log(Level.WARNING,
			   "Problem with requested cipher suites: " +
			   "No suites specified -- " +
			   "using default suites");
	    suites = factory.getSupportedCipherSuites();
	} else {
	    /*
	     * Set the enabled suites on a socket to make sure they are
	     * supported by JSSE.
	     */
	    try {
		SSLSocket socket = (SSLSocket) factory.createSocket();
		socket.setEnabledCipherSuites(requestedCipherSuites);
		suites = requestedCipherSuites;
	    } catch (Exception e) {
		INIT_LOGGER.log(Level.WARNING,
			       "Problem with requested cipher suites -- " +
			       "using default suites",
			       e);
		suites = factory.getSupportedCipherSuites();
	    }
	}
	return getSupportedCipherSuites(suites);
    }

    /**
     * Filters out unsupported suites, modifying the argument and maintaining
     * the original order.
     */
    private static String[] getSupportedCipherSuites(String[] suites) {
	int max = suites.length;
	for (int i = suites.length; --i >= 0; ) {
	    if (!supportedCipherSuite(suites[i])) {
		if (i < max - 1) {
		    System.arraycopy(suites, i + 1, suites, i, max - i - 1);
		}
		max--;
	    }
	}
	if (max == suites.length) {
	    return suites;
	} else {
	    String[] copy = new String[max];
	    System.arraycopy(suites, 0, copy, 0, max);
	    return copy;
	}
    }

    /* -- principal constraints methods -- */

    /**
     * Returns all client principals referred to by the constraints or null if
     * no client principal constraints are specified.  Returns a new set if the
     * result is non-null.
     */
    static Set getClientPrincipals(InvocationConstraints constraints) {
	return getPrincipals(constraints, true);
    }

    /**
     * Returns all client principals referred to by the constraints or null if
     * no client principal constraints are specified.  Returns a new set if the
     * result is non-null.
     */
    static Set getClientPrincipals(Set constraints) {
	return getPrincipals(constraints, true);
    }

    /**
     * Returns all server principals referred to by the constraints or null if
     * no server principal constraints are specified.  Returns a new set if the
     * result is non-null.
     */
    static Set getServerPrincipals(InvocationConstraints constraints) {
	return getPrincipals(constraints, false);
    }

    /** Implements getClientPrincipals or getServerPrincipals. */
    private static Set getPrincipals(InvocationConstraints constraints,
				     boolean client)
    {
	Set requirements = getPrincipals(constraints.requirements(), client);
	Set preferences = getPrincipals(constraints.preferences(), client);
	if (requirements == null) {
	    return preferences;
	} else if (preferences == null) {
	    return requirements;
	} else {
	    requirements.addAll(preferences);
	    return requirements;
	}
    }

    /**
     * Returns the client or server principals referred to by a set of
     * constraints.
     */
    private static Set getPrincipals(Set constraints, boolean client) {
	Set result = null;
	for (Iterator i = constraints.iterator(); i.hasNext(); ) {
	    Set eltResult =
		getPrincipals((InvocationConstraint) i.next(), client);
	    if (eltResult != null) {
		if (result == null) {
		    result = new HashSet();
		}
		result.addAll(eltResult);
	    }
	}
	return result;
    }

    /**
     * Returns the principals specified by a ClientMinPrincipal,
     * ClientMaxPrincipal, or ServerMinPrincipal constraint, or an alternatives
     * of one of those types.
     */
    private static Set getPrincipals(InvocationConstraint constraint,
				     boolean client)
    {
	Set principals;
	if (constraint instanceof ConstraintAlternatives) {
	    Set alts = ((ConstraintAlternatives) constraint).elements();
	    return getPrincipals(alts, client);
	} else if (constraint instanceof ServerMinPrincipal) {
	    if (client) {
		return null;
	    }
	    principals = ((ServerMinPrincipal) constraint).elements();
	} else if (!client) {
	    return null;
	} else if (constraint instanceof ClientMinPrincipal) {
	    principals = ((ClientMinPrincipal) constraint).elements();
	} else if (constraint instanceof ClientMaxPrincipal) {
	    principals = ((ClientMaxPrincipal) constraint).elements();
	} else {
	    return null;
	}
	Set result = new HashSet(principals.size());
	for (Iterator i = principals.iterator(); i.hasNext(); ) {
	    Object elt = i.next();
	    if (elt instanceof X500Principal) {
		result.add(elt);
	    }
	}
	return result;
    }

    /* -- get*SSLContextInfo -- */

    /** Used to pass an SSLContext and AuthManager pair. */
    static class SSLContextInfo {
	final SSLContext sslContext;
	final AuthManager authManager;

	SSLContextInfo(SSLContext sslContext, AuthManager authManager) {
	    this.sslContext = sslContext;
	    this.authManager = authManager;
	}
    }

    /**
     * WeakKey for looking up a server SSLContext.  Stores a weak reference to
     * the subject, plus the permitted principals.
     */
    private static class ServerKey extends WeakSoftTable.WeakKey {
	final Set permittedLocalPrincipals;

	/** Creates a key for the specified subject and local principals */
	ServerKey(Subject subject, Set permittedLocalPrincipals) {
	    super(subject);
	    this.permittedLocalPrincipals = permittedLocalPrincipals;
	}

	/** Copies the key to the queue */
	ServerKey(ServerKey serverKey, ReferenceQueue queue) {
	    super(serverKey, queue);
	    permittedLocalPrincipals = serverKey.permittedLocalPrincipals;
	}

        @Override
	public WeakSoftTable.RemovableReference copy(ReferenceQueue queue) {
	    return new ServerKey(this, queue);
	}

        @Override
	public int hashCode() {
	    return super.hashCode()
		^ (permittedLocalPrincipals == null ? 1
		   : permittedLocalPrincipals.hashCode());
	}

        @Override
	public boolean equals(Object other) {
	    return super.equals(other)
		&& safeEquals(permittedLocalPrincipals,
			      ((ServerKey) other).permittedLocalPrincipals);
	}
    }

    /**
     * WeakKey for looking up a client SSLContext.  Stores a weak reference to
     * the subject, plus the permitted client and server principals, the
     * endpoint, and whether client authentication is required.
     */
    private final static class ClientKey extends ServerKey {
	final Set permittedRemotePrincipals;
	final Endpoint endpoint;
	final boolean clientAuthRequired;
	final String[] cipherSuites;

	/** Creates a key for the specified client call context. */
	ClientKey(CallContext callContext) {
	    super(callContext.clientSubject, callContext.clientPrincipals);
	    permittedRemotePrincipals = callContext.serverPrincipals;
	    endpoint = callContext.endpoint;
	    clientAuthRequired = callContext.clientAuthRequired;
	    cipherSuites = callContext.cipherSuites;
	    assert cipherSuites != null
		&& cipherSuites.length > 0
		&& cipherSuites[0] != null;
	}

	/** Copies the key to the queue. */
	private ClientKey(ClientKey clientKey, ReferenceQueue queue) {
	    super(clientKey, queue);
	    permittedRemotePrincipals = clientKey.permittedRemotePrincipals;
	    endpoint = clientKey.endpoint;
	    clientAuthRequired = clientKey.clientAuthRequired;
	    cipherSuites = clientKey.cipherSuites;
	}

        @Override
	public WeakSoftTable.RemovableReference copy(ReferenceQueue queue) {
	    return new ClientKey(this, queue);
	}

        @Override
	public int hashCode() {
	    return super.hashCode()
		^ (permittedRemotePrincipals == null ? 2
		   : permittedRemotePrincipals.hashCode())
		^ endpoint.hashCode()
		^ (clientAuthRequired ? 4 : 0)
		^ cipherSuites[0].hashCode();
	}

        @Override
	public boolean equals(Object other) {
	    if (!super.equals(other)) {
		return false;
	    }
	    ClientKey clientKey = (ClientKey) other;
	    return safeEquals(permittedRemotePrincipals,
			      clientKey.permittedRemotePrincipals)
		&& endpoint.equals(clientKey.endpoint)
		&& clientAuthRequired == clientKey.clientAuthRequired
		&& Arrays.equals(cipherSuites, clientKey.cipherSuites);
	}
    }

    /**
     * Used to store a soft reference to a SSLContext and the associated
     * AuthManager in the SSL context map.
     */
    private final static class Value extends WeakSoftTable.SoftValue {
	final AuthManager authManager;

	/**
	 * Creates a value for the associated key containing the specified SSL
	 * context and auth manager.
	 */
	Value(ServerKey key, SSLContext sslContext, AuthManager authManager) {
	    super(key, sslContext);
	    this.authManager = authManager;
	}

	/** Copies the value to the queue. */
	private Value(Value value, ReferenceQueue queue) {
	    super(value, queue);
	    this.authManager = value.authManager;
	}

        @Override
	public WeakSoftTable.RemovableReference copy(ReferenceQueue queue) {
	    return new Value(this, queue);
	}

	/** Returns the SSL context. */
	SSLContext getSSLContext() {
	    return (SSLContext) get();
	}
    }

    /**
     * Returns the SSLContext and ClientAuthManager to use for creating client
     * socket factories.  Each client connection has exclusive access to an
     * SSLContext while the opening an SSL connection and should return the
     * SSLContextInfo by calling releaseClientSSLContextInfo when the
     * connection handshake is done, unless the handshake fails.
     *
     * @param callContext the client call context
     * @return an SSLContextInfo containing an SSLContext and ClientAuthManager
     *	       to use for a connection described by the argument
     * @throws RuntimeException if an error occurs during initialization of
     *	       JSSE
     */
    static SSLContextInfo getClientSSLContextInfo(CallContext callContext) {
	ClientKey key = new ClientKey(callContext);
	synchronized (SSL_CONTEXT_MAP) {
	    for (int i = 0; true; i++) {
		Value value = (Value) SSL_CONTEXT_MAP.get(key, i);
		if (value == null) {
		    break;
		}
		SSLContext sslContext = value.getSSLContext();
		if (sslContext != null) {
		    ClientAuthManager authManager =
			(ClientAuthManager) value.authManager;
		    try {
			authManager.checkAuthentication();
		    } catch (SecurityException e) {
			continue;
		    } catch (UnsupportedConstraintException e) {
			continue;
		    }
		    SSL_CONTEXT_MAP.remove(key, i);
		    if (CLIENT_LOGGER.isLoggable(Level.FINEST)) {
			CLIENT_LOGGER.log(
			    Level.FINEST,
			    "get client SSL context for {0}\n" +
			    "returns existing {1}",
			    new Object[] { callContext, sslContext });
		    }
		    return new SSLContextInfo(sslContext, authManager);
		}
	    }
	}
	/* Create a new SSL context */
	SSLContext sslContext;
	try {
	    sslContext = SSLContext.getInstance(SSL_PROTOCOL);
	} catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "finding SSL context");
	}

	ClientAuthManager authManager;
	try {
	    authManager = new ClientAuthManager(
		callContext.clientSubject,
		callContext.clientPrincipals,
		callContext.serverPrincipals);
	} catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "creating key or trust manager");
	}

	/* Initialize SSL context */
	try {
	    sslContext.init(new KeyManager[] { authManager },
			    new TrustManager[] { authManager },
			    null);
	} catch (KeyManagementException e) {
	    throw initializationError(e, "initializing SSL context");
	}

	if (CLIENT_LOGGER.isLoggable(Level.FINEST)) {
	    CLIENT_LOGGER.log(Level.FINEST,
			     "get client SSL context for {0}\nreturns new {1}",
			     new Object[] { callContext, sslContext });
	}
	return new SSLContextInfo(sslContext, authManager);
    }

    /**
     * Returns the SSLContext and ServerAuthManager to use for creating server
     * socket factories.  Server connections with the same subject and
     * principals share the same SSLContext.
     *
     * @param serverSubject the subject, or null
     * @param serverPrincipals the permitted principals, or null
     * @return an SSLContextInfo containing an SSLContext and ServerAuthManager
     *	       to use for a connection described by the arguments
     * @throws RuntimeException if an error occurs during initialization of
     *	       JSSE
     */
    static SSLContextInfo getServerSSLContextInfo(Subject serverSubject,
						  Set serverPrincipals)
    {
	ServerKey key = new ServerKey(serverSubject, serverPrincipals);
	synchronized (SSL_CONTEXT_MAP) {
	    Value value = (Value) SSL_CONTEXT_MAP.get(key, 0);
	    if (value != null) {
		SSLContext sslContext = value.getSSLContext();
		if (sslContext != null) {
		    if (SERVER_LOGGER.isLoggable(Level.FINEST)) {
			SERVER_LOGGER.log(
			    Level.FINEST,
			    "get server SSL context for {0}\n" +
			    "and principals {1}\nreturns existing {2}",
			    new Object[] { subjectString(serverSubject),
					   serverPrincipals, sslContext });
		    }
		    return new SSLContextInfo(sslContext, value.authManager);
		}
	    }

	    SSLContext sslContext;
	    try {
		sslContext = SSLContext.getInstance(SSL_PROTOCOL);
	    } catch (NoSuchAlgorithmException e) {
		throw initializationError(e, "finding SSL context");
	    }

	    ServerAuthManager authManager;
	    try {
		authManager = new ServerAuthManager(
		    serverSubject, serverPrincipals,
		    sslContext.getServerSessionContext());
	    } catch (NoSuchAlgorithmException e) {
		throw initializationError(e, "creating key or trust manager");
	    }

	    /* Initialize SSL context */
	    try {
		sslContext.init(new KeyManager[] { authManager },
				new TrustManager[] { authManager },
				null);
	    } catch (KeyManagementException e) {
		throw initializationError(e, "initializing SSL context");
	    }

	    SSL_CONTEXT_MAP.add(key, new Value(key, sslContext, authManager));

	    if (SERVER_LOGGER.isLoggable(Level.FINEST)) {
		SERVER_LOGGER.log(
		    Level.FINEST,
		    "get server SSL context for {0}\nand principals {1}\n" +
		    "returns new {2}",
		    new Object[] { subjectString(serverSubject),
				   serverPrincipals, sslContext });
	    }
	    return new SSLContextInfo(sslContext, authManager);
	}
    }

    /**
     * Returns the client's SSLContext and ClientAuthManager to the
     * SSLContextMap for use by another connection.
     */
    static void releaseClientSSLContextInfo(CallContext callContext,
					    SSLContext sslContext,
					    ClientAuthManager authManager)
    {
	ClientKey key = new ClientKey(callContext);
	synchronized (SSL_CONTEXT_MAP) {
	    SSL_CONTEXT_MAP.add(key, new Value(key, sslContext, authManager));
	}
    }

    /**
     * Returns a <code>RuntimeException</code> for a problem initializing JSSE.
     *
     * @param error an <code>Exception</code> that describes the problem
     * @param contextString describes where the problem occurred
     * @return a <code>RuntimeException</code> describing the problem
     */
    private static RuntimeException initializationError(Exception error,
							String contextString)
    {
	RuntimeException e = new RuntimeException(
	    "Error during initialization of SSL or HTTPS provider, " +
	    "while " + contextString + ": " + error.getMessage(),
	    error);
	INIT_LOGGER.log(Level.WARNING, "Initialization error", e);
	return e;
    }

    /**
     * Returns a CertificateFactory for generating a CertPath for X.509
     * certificates.
     */
    static CertificateFactory getCertFactory() {
	synchronized (SSL_CONTEXT_MAP) {
	    if (certFactory == null) {
		try {
		    certFactory = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
		    throw initializationError(
			e, "getting certificate factory");
		}
	    }
	    return certFactory;
	}
    }

    /* -- firstX509Cert -- */

    /**
     * Returns the first X509Certificate from a CertPath known to contain them.
     */
    static X509Certificate firstX509Cert(CertPath certPath) {
 	return (X509Certificate) certPath.getCertificates().get(0);
    }

    /* -- checkValidity -- */

    /**
     * Checks if the X.509 certificates in the CertPath are currently valid.
     * If issuers is non-null, insures the path contains one of the issuers.
     */
    static void checkValidity(CertPath x509CertPath, X500Principal[] issuers)
	throws CertificateException
    {
	boolean issuerOK = issuers == null;
	List certs = x509CertPath.getCertificates();
	for (int i = certs.size(); --i >= 0; ) {
	    X509Certificate cert = (X509Certificate) certs.get(i);
	    cert.checkValidity();
	    if (!issuerOK) {
		X500Principal certIssuer = cert.getIssuerX500Principal();
		for (int j = issuers.length; --j >= 0; ) {
		    if (certIssuer.equals(issuers[j])) {
			issuerOK = true;
			break;
		    }
		}
	    }
	}
	if (!issuerOK) {
	    throw new CertificateException(
		"No match for permitted issuers: " + toString(issuers) +
		"\nCertificate chain: " + x509CertPath);
	}
    }

    /* -- Cipher suite info -- */

    /** Determines if the cipher suite authenticates the server */
    static boolean doesServerAuthentication(String cipherSuite) {
	String alg = getKeyExchangeAlgorithm(cipherSuite);
	return position(alg, ANONYMOUS_KEY_EXCHANGE_ALGORITHMS) == -1;
    }

    /** Determines if the cipher suite does encryption */
    static boolean doesEncryption(String cipherSuite) {
	return !getCipherAlgorithm(cipherSuite).equals(
	    NO_ENCRYPTION_CIPHER_ALGORITHM);
    }

    /** Determines if the cipher suite maintains integrity */
    static boolean maintainsIntegrity(String cipherSuite) {
	return !getMICAlgorithm(cipherSuite).equals(
	    NO_INTEGRITY_MIC_ALGORITHM);
    }

    /**
     * Returns the key exchange algorithm for the specified cipher suite. <p>
     *
     * The key exchange algorithm is found following the first underscore and
     * up to the first occurrence of "_WITH_".
     */
    static String getKeyExchangeAlgorithm(String cipherSuite) {
	int start = cipherSuite.indexOf('_') + 1;
	if (start >= 1) {
	    int end = cipherSuite.indexOf("_WITH_", start);
	    if (end >= start) {
		return cipherSuite.substring(start, end);
	    }
	}
	return "NULL";
    }

    /**
     * Returns the key algorithm for the specified cipher suite, one of "RSA",
     * "DSA", "ECDSA" or "NULL".  Throws an IllegalArgumentException if the algorithm
     * is not recognized. <p>
     *
     * The key algorithm is specified by the key exchange algorithm.
     */
    static String getKeyAlgorithm(String cipherSuite) {
	String alg = getKeyExchangeAlgorithm(cipherSuite);
	if (position(alg, RSA_KEY_EXCHANGE_ALGORITHMS) != -1) {
	    return "RSA";
	} else if (position(alg, DSA_KEY_EXCHANGE_ALGORITHMS) != -1) {
	    return "DSA";
	} else if (position(alg, ECDSA_KEY_EXCHANGE_ALGORITHMS) != -1){
	    return "ECDSA";
	} else if (position(alg, ANONYMOUS_KEY_EXCHANGE_ALGORITHMS) != -1) {
	    return "NULL";
	} else {
	    throw new IllegalArgumentException(
		"Unrecognized key exchange algorithm: " + alg);
	}
    }    

    /**
     * Returns the algorithms permitted for keys used with this cipher suite.
     * Note that the result can be different for client and server sides.
     *
     * @param cipherSuite the cipher suite
     * @param client true to get results for the client side, false for the
     *	      server side
     * @return the permitted key algorithms, an OR of some set of the values
     *	       DSA_KEY_ALGORITHM and RSA_KEY_ALGORITHM
     * @throws IllegalArgumentException if the key exchange algorithm is not
     *	       recognized
     */
    static int getPermittedKeyAlgorithms(String cipherSuite, boolean client) {
	String keyAlgorithm = getKeyAlgorithm(cipherSuite);
	if ("RSA".equals(keyAlgorithm))
	    /*
	    * For these suites, the server must use an RSA key, but the client
	    * may use either an RSA or DSA key.
	    */
	    return (client)
		    ? DSA_KEY_ALGORITHM | RSA_KEY_ALGORITHM
		    : RSA_KEY_ALGORITHM;
	if ("DSA".equals(keyAlgorithm))
	    /* Same here, but server must use a DSA key */
	    return (client)
		    ? DSA_KEY_ALGORITHM | RSA_KEY_ALGORITHM
		    : DSA_KEY_ALGORITHM;
	if("ECDSA".equals(keyAlgorithm))
	    /* For this suit the server must use a ECDSA key , but the client
	    * may use either an RSA, DSA or ECDSA key. */
	    return (client)
		    ? DSA_KEY_ALGORITHM | RSA_KEY_ALGORITHM | ECDSA_KEY_ALGORITHM
		    : ECDSA_KEY_ALGORITHM;
	if("NULL".equals(keyAlgorithm))
	    return 0;
	// else
	throw new AssertionError(
		    "Unrecognized key algorithm: " + keyAlgorithm);
    }

    /**
     * Returns true if the algorithm is one of the permitted algorithms,
     * otherwise false.
     */
    static boolean permittedKeyAlgorithm(String keyAlgorithm,
					 int permittedKeyAlgorithms)
    {
	if (null != keyAlgorithm){ 
            // Don't permit any, as that subjects endpoints to mitm attacks on weaker
            // key excange protocols.
            //	if (permittedKeyAlgorithms == ANY_KEY_ALGORITHM) {
            //	    return true;
	    if ("DSA".equals(keyAlgorithm))
		return (permittedKeyAlgorithms & DSA_KEY_ALGORITHM) != 0;
	    if ("ECDSA".equals(keyAlgorithm))
		return (permittedKeyAlgorithms & ECDSA_KEY_ALGORITHM) != 0;
	    if ("RSA".equals(keyAlgorithm))
		return (permittedKeyAlgorithms & RSA_KEY_ALGORITHM) != 0;
        }
        return false;
    }

    /**
     * Returns the cipher algorithm for the specified cipher suite. <p>
     *
     * The cipher algorithm is found following the first occurrence of "_WITH_"
     * and up to the last underscore.
     */
    static String getCipherAlgorithm(String cipherSuite) {
	int start = cipherSuite.indexOf("_WITH_") + 6;
	if (start >= 6) {
	    int end = cipherSuite.lastIndexOf('_');
	    if (end >= start) {
		return cipherSuite.substring(start, end);
	    }
	}
	return "NULL";
    }

    /**
     * Returns true if the cipher algorithm for the specified cipher suite is
     * considered a strong cipher and the key exchange has perfect forward
     * secrecy (ephemeral), otherwise false.
     */
    static boolean hasStrongKeyCipherAlgorithms(String cipherSuite) {
	String cipher = getCipherAlgorithm(cipherSuite);
        String key = getKeyExchangeAlgorithm(cipherSuite);
        return ( position(key, EPHEMERAL_KEY_EXCHANGE_ALGORITHMS) != -1 &&
                 position(cipher, STRONG_ENCRYPTION_CIPHERS) != -1);
    }

    /**
     * Returns the message integrity code algorithm for the specified cipher
     * suite. <p>
     *
     * The message integrity algorithm is found after the last underscore.
     */
    private static String getMICAlgorithm(String cipherSuite) {
	return cipherSuite.substring(cipherSuite.lastIndexOf('_'));
    }

    /**
     * Checks if the suite is supported by this provider.  The suite can only
     * be supported if its security characteristics can be determined, meaning
     * its key exchange and cipher algorithms must be known.
     */
    private static boolean supportedCipherSuite(String cipherSuite) {
	return position(getKeyExchangeAlgorithm(cipherSuite),
			SUPPORTED_KEY_EXCHANGE_ALGORITHMS) != -1 &&
	    position(getCipherAlgorithm(cipherSuite),
		     SUPPORTED_ENCRYPTION_CIPHERS) != -1;
    }

    /* -- subjectString -- */

    /** Returns a String that includes relevant information about a Subject */
    static String subjectString(Subject subject) {
	if (subject == null) {
	    return "null subject";
	} else {
	    return "Subject@" +
		Integer.toHexString(System.identityHashCode(subject)) +
		"{\n" + SubjectCredentials.credentialsString(subject) + "}";
	}
    }

    /* -- safeEquals -- */

    /** Same as equals(), but allows either argument to be null */
    static boolean safeEquals(Object x, Object y) {
	return (x == null) ? y == null : x.equals(y);
    }

    /* -- contains -- */

    /**
     * Returns true if the array contains an equal element, which may be null.
     */
    static boolean contains(Object[] array, Object element) {
	for (int i = array.length; --i >= 0; ) {
	    if (safeEquals(array[i], element)) {
		return true;
	    }
	}
	return false;
    }

    /* -- toString -- */

    /** Converts the contents of an Object array to a String. */
    static String toString(Object[] array) {
	if (array == null) {
	    return "null";
	}
	StringBuilder buf = new StringBuilder("[");
	for (int i = 0; i < array.length; i++) {
	    if (i != 0) {
		buf.append(", ");
	    }
	    buf.append(array[i]);
	}
	buf.append("]");
	return buf.toString();
    }

    /* -- equals -- */

    /**
     * Checks if the elements of two arrays are equal.
     *
     * @param x the first array
     * @param y the second array
     * @return true if both arguments are null or both are non-null, are the
     *	       same length, and, for each array index, the elements of each
     *	       array either both null or both non-null and equal
     */
    static boolean equals(Object[] x, Object[] y) {
	if (x == null) {
	    return y == null;
	} else if (y == null) {
	    return false;
	} else if (x.length != y.length) {
	    return false;
	} else {
	    for (int i = x.length; --i >= 0; ) {
		if (!safeEquals(x[i], y[i])) {
		    return false;
		}
	    }
	    return true;
	}
    }

    /* -- getClassName -- */

    /**
     * Returns the class name of an object, without the package or enclosing
     * class prefix.
     */
    static String getClassName(Object object) {
	String className = object.getClass().getName();
	className = className.substring(className.lastIndexOf('.') + 1);
	className = className.substring(className.lastIndexOf('$') + 1);
	return className;
    }

    /* -- position -- */

    /**
     * Returns the offset of a string in an array of strings.  Returns -1 if
     * the string is not found.
     */
    static int position(String string, String[] list) {
	for (int i = list.length; --i >= 0; ) {
	    if (string.equals(list[i])) {
		return i;
	    }
	}
	return -1;
    }

    /* -- Logging -- */

    /**
     * Logs a throw. Use this method to log a throw when the log message needs
     * parameters.
     *
     * @param logger logger to log to
     * @param level the log level
     * @param sourceClass class where throw occurred
     * @param sourceMethod name of the method where throw occurred
     * @param msg log message
     * @param params log message parameters
     * @param e exception thrown
     */
    static void logThrow(Logger logger,
			 Level level,
			 Class sourceClass,
			 String sourceMethod,
			 String msg,
			 Object[] params,
			 Throwable e)
    {
	LogRecord r = new LogRecord(level, msg);
	r.setLoggerName(logger.getName());
	r.setSourceClassName(sourceClass.getName());
	r.setSourceMethodName(sourceMethod);
	r.setParameters(params);
	r.setThrown(e);
	logger.log(r);
    }
}
