/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.rmi.tls;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.security.Security;
import org.apache.river.action.GetPropertyAction;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;

/**
 * This class is made up of methods and fields copied directly 
 * from Utilities and SubjectCredentials classes in the net.jini.jeri.ssl package.
 */
class Utilities {
    
    /** Client logger */
    static final Logger CLIENT_LOGGER =
	Logger.getLogger("au.net.zeus.rmi.tls.client");

    /** Server logger */
    static final Logger SERVER_LOGGER =
	Logger.getLogger("au.net.zeus.rmi.tls.server");

    /** Initialization logger */
    static final Logger INIT_LOGGER =
	Logger.getLogger("au.net.zeus.rmi.tls.init");
    
    /* -- Fields -- */
    
    /** The secure socket protocol used with JSSE. */
    protected static final String SSL_PROTOCOL = (String) Security.doPrivileged(
	new GetPropertyAction("org.apache.river.jeri.ssl.sslProtocol", "TLSv1.2"));

    /** The JSSE Provider to use*/
    protected static final String JSSE_PROVIDER = (String) Security.doPrivileged(
	new GetPropertyAction("org.apache.river.jeri.ssl.jsseProvider", null));
    
    private static final String RAN_ALG = (String) Security.doPrivileged(
	new GetPropertyAction("org.apache.river.jeri.ssl.secureRandomAlgorithm", null));
    
    /** The providers and algorithms to use */
    private static final String JCE_PROVIDER = (String) Security.doPrivileged(
	new GetPropertyAction("org.apache.river.jeri.ssl.jceProvider", null));
    
    /**
     * The names of JSSE key exchange algorithms used for anonymous
     * communication.
     * 
     * Since anonymous clients risk MITM attacks, we need to look at
     * supporting Secure Remote Passwords, when it isn't practical to use
     * client certificates.  Also devices used for IOT, that are provided
     * with a key by the manufacture, mays need pre shared key support.
     * 
     * It is not known at this stage whether this functionality will be
     * provided in a separate Endpoint implementation.
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
        "ECDHE_ECDSA", //Only ephemeral DH safe from mitm attack.
//	"ECDHE_PSK", // Pre Shared Key
//	"DHE_PSK" // Pre Shared Key
    };

    /** The names of JSSE key exchange algorithms that use RSA keys. */
    private static final String[] RSA_KEY_EXCHANGE_ALGORITHMS = {
	"ECDHE_RSA", //Only ephemeral DH safe from mitm attack.
	"DHE_RSA", //Only ephemeral DH safe from mitm attack.
	"RSA",
//	"SRP_SHA_RSA" //RFC 5054 Secure Remote Password
    };

    /** The names of JSSE key exchange algorithms that use DSA keys. */
    private static final String[] DSA_KEY_EXCHANGE_ALGORITHMS = {
	"DHE_DSS", //Only ephemeral DH safe from mitm attack.
//	"SRP_SHA_DSS" //RFC 5054 Secure Remote Password
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
	"DH_anon",
//	"ECDHE_PSK", // Pre Shared Key
//	"DHE_PSK", // Pre Shared Key
//	"SRP_SHA_RSA", //RFC 5054 Secure Remote Password
//	"SRP_SHA_DSS" //RFC 5054 Secure Remote Password
    };	
    
    
    private static final ConcurrentMap<Subject,SSLContext> SERVER_TLS_CONTEXT_MAP = 
	    RC.concurrentMap(
		new ConcurrentHashMap<Referrer<Subject>,Referrer<SSLContext>>(),
		Ref.WEAK,
		Ref.SOFT,
		10000L,
		10000L
	    );
    
    private static final ConcurrentMap<Subject,SSLContext> CLIENT_TLS_CONTEXT_MAP = 
	    RC.concurrentMap(
		new ConcurrentHashMap<Referrer<Subject>,Referrer<SSLContext>>(),
		Ref.WEAK,
		Ref.SOFT,
		10000L,
		10000L
	    );
    
    private static final ConcurrentMap<Subject,ClientSubjectKeyManager> CLIENT_TLS_MANAGER_MAP = 
	    RC.concurrentMap(
		new ConcurrentHashMap<Referrer<Subject>,Referrer<ClientSubjectKeyManager>>(),
		Ref.WEAK,
		Ref.SOFT,
		10000L,
		10000L
	    );
    
    /**
     * Returns the offset of a string in an array of strings.  Returns -1 if
     * the string is not found.
     */
    static int position(String string, String[] list) {
	for (int i = list.length; --i >= 0; ) {
	    if (string != null && string.equals(list[i])) {
		return i;
	    }
	}
	return -1;
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

    /** Same as equals(), but allows either argument to be null */
    static boolean safeEquals(Object x, Object y) {
	return (x == null) ? y == null : x.equals(y);
    }

    /* -- checkValidity -- */
    /**
     * Checks if the X.509 certificates in the CertPath are currently valid.
     * If issuers is non-null, insures the path contains one of the issuers.
     */
    static void checkValidity(CertPath x509CertPath, X500Principal[] issuers) throws CertificateException {
	boolean issuerOK = issuers == null;
	List certs = x509CertPath.getCertificates();
	for (int i = certs.size(); --i >= 0;) {
	    X509Certificate cert = (X509Certificate) certs.get(i);
	    cert.checkValidity();
	    if (!issuerOK) {
		X500Principal certIssuer = cert.getIssuerX500Principal();
		for (int j = issuers.length; --j >= 0;) {
		    if (certIssuer.equals(issuers[j])) {
			issuerOK = true;
			break;
		    }
		}
	    }
	}
	if (!issuerOK) {
	    throw new CertificateException("No match for permitted issuers: " + toString(issuers) + "\nCertificate chain: " + x509CertPath);
	}
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
    
    /**
     * Returns a <code>RuntimeException</code> for a problem initializing JSSE.
     *
     * @param error an <code>Exception</code> that describes the problem
     * @param contextString describes where the problem occurred
     * @return a <code>RuntimeException</code> describing the problem
     */
    static RuntimeException initializationError(Exception error,
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
     * Returns the SSLContext to use for creating client
     * socket factories.  A new SSLContext is created each time.
     *
     * @param callContext the client call context
     * @return an SSLContextInfo containing an SSLContext and ClientAuthManager
     *	       to use for a connection described by the argument
     * @throws RuntimeException if an error occurs during initialization of
     *	       JSSE
     */
    static SSLContext getClientSSLContextInfo(Subject clientSubject) {
	SSLContext sslContext = null;
	ClientSubjectKeyManager authManager = CLIENT_TLS_MANAGER_MAP.get(clientSubject);
	if (authManager != null){
	    try {
		sslContext = CLIENT_TLS_CONTEXT_MAP.get(clientSubject);
		authManager.checkAuthentication();
	    } catch (GeneralSecurityException ex) {
		CLIENT_TLS_MANAGER_MAP.remove(clientSubject, authManager);
		CLIENT_TLS_CONTEXT_MAP.remove(clientSubject, sslContext);
		sslContext = null;
	    }
	}
	if (sslContext != null) {
	    if (CLIENT_LOGGER.isLoggable(Level.FINEST)) {
		CLIENT_LOGGER.log(Level.FINEST,
				 "get client SSL context for {0}\nreturns existing {1}",
				 new Object[] { clientSubject, sslContext });
	    }
	    return sslContext;
	}

	/* Create a new SSL context */
	try {
		if (JSSE_PROVIDER != null){
		    sslContext = SSLContext.getInstance(SSL_PROTOCOL,JSSE_PROVIDER);
		} else {
		    sslContext = SSLContext.getInstance(SSL_PROTOCOL);
		}
	} catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "finding SSL context");
	} catch (NoSuchProviderException e) {
	    throw initializationError(e, "finding SSL context");
	}

	try {
	    authManager = new ClientSubjectKeyManager(
		clientSubject
	    );
	    ClientSubjectKeyManager existed = CLIENT_TLS_MANAGER_MAP.putIfAbsent(clientSubject, authManager);
	    if (existed != null) authManager = existed;
	} 
	catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "creating key or trust manager");
	} 
	catch (NoSuchProviderException e) {
	    throw initializationError(e, "creating key manager");
	}
	
	/* Initialize SSL context */
	try {
	    SecureRandom random = null;
	    if (RAN_ALG != null){
		if ( JCE_PROVIDER != null){
		    random = SecureRandom.getInstance(RAN_ALG, JCE_PROVIDER);
		} else {
		    random = SecureRandom.getInstance(RAN_ALG);
		}
	    }
	    sslContext.init(
		    new KeyManager[]  { authManager },
		    new TrustManager[] { authManager },
		    random
	    );
	} 
	catch (KeyManagementException e) {
	    throw initializationError(e, "initializing SSL context");
	} 
	catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "initializing SSL context");
	} 
	catch (NoSuchProviderException e) {
	    throw initializationError(e, "initializing SSL context");
	}

	if (CLIENT_LOGGER.isLoggable(Level.FINEST)) {
	    CLIENT_LOGGER.log(Level.FINEST,
			     "get client SSL context for {0}\nreturns new {1}",
			     new Object[] { clientSubject, sslContext });
	}
	SSLContext existed = CLIENT_TLS_CONTEXT_MAP.putIfAbsent(clientSubject, sslContext);
	if (existed != null) {
	    if (CLIENT_LOGGER.isLoggable(Level.FINEST)) {
		CLIENT_LOGGER.log(Level.FINEST,
				 "get client SSL context for {0}\nreturns new {1},\n but it already existed {2}",
				 new Object[] { clientSubject, sslContext, existed });
	    }
	    // REMIND: Should we return existed?
	}
	return sslContext;
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
    static SSLContext getServerSSLContextInfo(Subject serverSubject)
    {
	SSLContext sslContext = SERVER_TLS_CONTEXT_MAP.get(serverSubject);
	if (sslContext != null) {
	    if (SERVER_LOGGER.isLoggable(Level.FINEST)) {
		SERVER_LOGGER.log(
		    Level.FINEST,
		    "get server SSL context for {0}\n" +
		    "returns existing {1}",
		    new Object[] { subjectString(serverSubject),
				   sslContext });
	    }
	    return sslContext;
	}

	try {
	    if (JSSE_PROVIDER != null){
		sslContext = SSLContext.getInstance(SSL_PROTOCOL,JSSE_PROVIDER);
	    } else {
		sslContext = SSLContext.getInstance(SSL_PROTOCOL);
	    }
	} 
	catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "finding SSL context");
	} 
	catch (NoSuchProviderException e) {
	    throw initializationError(e, "finding SSL context");
	}

	ServerSubjectKeyManager authManager;
	try {
	    authManager = new ServerSubjectKeyManager(
		serverSubject,
		sslContext.getServerSessionContext());
	} 
	catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "creating key or trust manager");
	} 
	catch (NoSuchProviderException e) {
	    throw initializationError(e, "creating key manager");
	} 

	/* Initialize SSL context */
	try {
	    SecureRandom random = null;
	    if (RAN_ALG != null){
		if ( JCE_PROVIDER != null){
		    random = SecureRandom.getInstance(RAN_ALG, JCE_PROVIDER);
		} else {
		    random = SecureRandom.getInstance(RAN_ALG);
		}
	    }
	    sslContext.init(
		    new KeyManager[]  { authManager },
		    new TrustManager[] { authManager },
		    random
	    );
	} 
	catch (KeyManagementException e) {
	    throw initializationError(e, "initializing SSL context");
	} 
	catch (NoSuchAlgorithmException e) {
	    throw initializationError(e, "initializing SSL context");
	} 
	catch (NoSuchProviderException e) {
	    throw initializationError(e, "initializing SSL context");
	}

	SSLContext existed = SERVER_TLS_CONTEXT_MAP.putIfAbsent(serverSubject, sslContext);
	if (existed != null) {
	    if (SERVER_LOGGER.isLoggable(Level.FINEST)) {
		SERVER_LOGGER.log(
		    Level.FINEST,
		    "get server SSL context for {0}\n" +
		    "returns existing {1}",
		    new Object[] { subjectString(serverSubject),
				   sslContext });
	    }
	    return existed;
	} else {
	    if (SERVER_LOGGER.isLoggable(Level.FINEST)) {
		SERVER_LOGGER.log(
		    Level.FINEST,
		    "get server SSL context for {0}\n" +
		    "returns new {1}",
		    new Object[] { subjectString(serverSubject),
				   sslContext });
	    }
	    return sslContext;
	}
    }
    
    

/** Returns a String that includes relevant information about a Subject */
    static String subjectString(Subject subject) {
	if (subject == null) {
	    return "null subject";
	} else {
	    return "Subject@" +
		Integer.toHexString(System.identityHashCode(subject)) +
		"{\n" + credentialsString(subject) + "}";
	}
    }
    
    /**
     * Returns a String that describes the credentials in the subject.
     *
     * @param subject the Subject containing the credentials
     * @return a String describing the credentials
     * @throws NullPointerException if the subject is null
     */
    static String credentialsString(Subject subject) {
	List certPaths = getCertificateChains(subject);
	if (certPaths == null) {
	    return "";
	}
	StringBuffer buf = new StringBuffer();
	for (int i = certPaths.size(); --i >= 0; ) {
	    CertPath chain = (CertPath) certPaths.get(i);
	    X509Certificate cert = firstX509Cert(chain);
	    X500Principal principal = getPrincipal(subject, cert);
	    if (principal != null) {
		buf.append("  Principal: ").append(principal).append('\n');
		buf.append("    Public key: ");
		appendKeyString(cert.getPublicKey(), buf);
		buf.append('\n');
		buf.append("    Private key: ");
		try {
		    X500PrivateCredential cred =
			(X500PrivateCredential) Security.doPrivileged(
			    new GetPrivateCredentialAction(subject, cert));
		    PrivateKey privateKey =
			cred != null ? cred.getPrivateKey() : null;
		    if (privateKey == null) {
			buf.append("Not found");
		    } else {
			appendKeyString(privateKey, buf);
		    }
		} catch (SecurityException e) {
		    buf.append("No permission");
		}
	    }
	}
	return buf.toString();
    }
    
    /** Appends information about a key to a StringBuffer. */
    private static void appendKeyString(Key key, StringBuffer buf) {
	String className = key.getClass().getName();
	buf.append(className.substring(className.lastIndexOf('.') + 1));
	buf.append('@');
	buf.append(Integer.toHexString(System.identityHashCode(key)));
    }
    

    /* -- firstX509Cert -- */
    /**
     * Returns the first X509Certificate from a CertPath known to contain them.
     */
    static X509Certificate firstX509Cert(CertPath certPath) {
	return (X509Certificate) certPath.getCertificates().get(0);
    }

    /**
     * Returns the latest time for which all of the X.509 certificates in the
     * certificate chain are valid.
     */
    static long certificatesValidUntil(CertPath chain) {
	long result = Long.MAX_VALUE;
	List<? extends Certificate> certs = chain.getCertificates();
	for (int i = certs.size(); --i >= 0;) {
	    X509Certificate cert = (X509Certificate) certs.get(i);
	    long until = cert.getNotAfter().getTime();
	    if (until < result) {
		result = until;
	    }
	}
	return result;
    }

    /**
     * Returns the latest time for which all of the X.509 certificates in the
     * certificate chain are valid.
     */
    static long certificatesValidUntil(X509Certificate[] chain) {
	long result = Long.MAX_VALUE;
	for (int i = chain.length; --i >= 0;) {
	    X509Certificate cert = chain[i];
	    long until = cert.getNotAfter().getTime();
	    if (until < result) {
		result = until;
	    }
	}
	return result;
    }

    /**
     * Retrieves the X.509 CertPath for a credential name.  Returns null if the
     * chain associated with the credential name is not found.  Does not check
     * if either principal or private key associated with the chain are
     * present.
     *
     * @param subject the Subject containing the credentials or null
     * @param name the name of the credentials
     * @return the certificate chain or null
     */
    static CertPath getCertificateChain(Subject subject, String name) {
	if (subject == null) {
	    return null;
	}
	SubjectKeyManager.CertificateMatcher matcher = SubjectKeyManager.CertificateMatcher.create(name);
	if (matcher != null) {
	    Set publicCreds = subject.getPublicCredentials();
	    synchronized (publicCreds) {
		for (Iterator it = publicCreds.iterator(); it.hasNext();) {
		    Object cred = it.next();
		    if (isX509CertificateChain(cred)) {
			CertPath chain = (CertPath) cred;
			if (matcher.matches(firstX509Cert(chain))) {
			    return chain;
			}
		    }
		}
	    }
	}
	return null;
    }

    /**
     * Checks if the subject's public credentials contain a certificate chain
     * that starts with a certificate with the same subject and public key, and
     * returns the certificate chain if it does.  Does not check the validity
     * of the certificate chain, or for associated private credentials or
     * principal.
     *
     * @param cert the certificate
     * @return the certificate chain starting with an equivalent certificate,
     *	       if present, otherwise null
     */
    static CertPath getCertificateChain(Subject subject, X509Certificate cert) {
	if (subject != null) {
	    Principal subjectDN = null;
	    PublicKey key = null;
	    Set publicCreds = subject.getPublicCredentials();
	    synchronized (publicCreds) {
		for (Iterator it = publicCreds.iterator(); it.hasNext();) {
		    Object cred = it.next();
		    if (!isX509CertificateChain(cred)) {
			continue;
		    }
		    CertPath chain = (CertPath) cred;
		    X509Certificate start = firstX509Cert(chain);
		    if (cert.equals(start)) {
			return chain;
		    }
		    if (subjectDN == null) {
			subjectDN = cert.getSubjectDN();
			key = cert.getPublicKey();
		    }
		    if (subjectDN.equals(start.getSubjectDN()) && key.equals(start.getPublicKey())) {
			return chain;
		    }
		}
	    }
	}
	return null;
    }

    /**
     * Returns the X.509 CertPaths stored in the public credentials of the
     * subject.  Does not check if the associated principals or private keys
     * are present.  Returns null if none are found.
     *
     * @param subject the subject containing the X.509 CertPaths or null
     * @return List of the X.509 CertPaths in the subject
     */
    static List getCertificateChains(Subject subject) {
	List result = null;
	if (subject != null) {
	    Set publicCreds = subject.getPublicCredentials();
	    synchronized (publicCreds) {
		for (Iterator it = publicCreds.iterator(); it.hasNext();) {
		    Object cred = it.next();
		    if (isX509CertificateChain(cred)) {
			if (result == null) {
			    result = new ArrayList(publicCreds.size());
			}
			result.add(cred);
		    }
		}
	    }
	}
	return result;
    }

    /**
     * Returns the credential name for an X.509 certificate.
     *
     * @param cert the certificate
     * @return the credential name
     */
    static String getCertificateName(X509Certificate cert) {
	return SubjectKeyManager.CertificateMatcher.getName(cert);
    }

    /**
     * Returns the subject principal matching the X.509 certificate.  Returns
     * null if the principal is not found.  Does not check if the associated
     * private key is present.  Assumes that the subject is non-null.
     *
     * @param subject the Subject containing the credentials
     * @param cert the X.509 certificate
     * @return the X.500 principal or null
     */
    static X500Principal getPrincipal(Subject subject, X509Certificate cert) {
	X500Principal x500 = cert.getSubjectX500Principal();
	String name = x500.getName(X500Principal.CANONICAL);
	Set principals = subject.getPrincipals();
	synchronized (principals) {
	    for (Iterator i = principals.iterator(); i.hasNext();) {
		Object next = i.next();
		if (!(next instanceof X500Principal)) {
		    continue;
		}
		X500Principal principal = (X500Principal) next;
		if (principal.getName(X500Principal.CANONICAL).equals(name)) {
		    return principal;
		}
	    }
	}
	return null;
    }

    /**
     * Returns the X500PrivateCredential for an X.509 certificate.  Returns
     * null if the associated private credential is missing from the subject.
     * Does not check if the public credential or principal are present.
     * Assumes that the subject is non-null.  The caller should check for
     * AuthenticationPermission and then call this method from within
     * AccessController.doPrivileged to give it private credential permissions.
     *
     * @param subject the Subject containing the credentials
     * @param cert the X.509 certificate
     * @return the X500PrivateCredential or null
     */
    static X500PrivateCredential getPrivateCredential(Subject subject, X509Certificate cert) {
	X500PrivateCredential result = null;
	Set privateCreds = subject.getPrivateCredentials();
	synchronized (privateCreds) {
	    /*
	     * XXX: Include this synchronization to work around BugID 4892913,
	     * Subject.getPrivateCredentials not thread-safe against changes to
	     * principals.  -tjb[18.Jul.2003]
	     *
	     * synchronized (subject.getPrincipals()) {
	     */
	    for (Iterator it = privateCreds.iterator(); it.hasNext();) {
		Object cred = it.next();
		if (cred instanceof X500PrivateCredential) {
		    X500PrivateCredential xpc = (X500PrivateCredential) cred;
		    if (cert.equals(xpc.getCertificate())) {
			result = xpc;
			break;
		    }
		}
	    }
	}
	return result;
    }

    /**
     * Determines if the argument is an X.509 certificate CertPath.  Returns
     * true if the argument is a non-null CertPath, has at least one
     * certificate, and has type X.509.
     */
    protected static boolean isX509CertificateChain(Object credential) {
	if (!(credential instanceof CertPath)) {
	    return false;
	}
	CertPath certPath = (CertPath) credential;
	if (certPath.getCertificates().isEmpty()) {
	    return false;
	} else if (!certPath.getType().equals("X.509")) {
	    return false;
	}
	return true;
    }
    
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
    
    /**
     * Provides utilities for converting between X.509 certificates and unique
     * certificate names.
     */
    static class CertificateMatcher {
	private final BigInteger serialNumber;
	private final String issuerName;

	/**
	 * Creates an object that can be compared with an X.509 certificate.
	 * Returns null if the argument is not a valid certificate name.
	 */
	static CertificateMatcher create(String certificateName) {
	    if (certificateName == null) {
		return null;
	    }
	    int atSignPosition = certificateName.indexOf('@');
	    if (atSignPosition < 0) {
		return null;
	    }
	    BigInteger serialNumber;
	    try {
		serialNumber = new BigInteger(
		    certificateName.substring(0, atSignPosition),
		    16);
	    } catch (NumberFormatException e) {
		return null;
	    }
	    String issuerName = certificateName.substring(atSignPosition + 1);
	    return new CertificateMatcher(serialNumber, issuerName);
	}

	private CertificateMatcher(BigInteger serialNumber, String issuerName)
	{
	    this.serialNumber = serialNumber;
	    this.issuerName = issuerName;
	}

	/** Returns the unique certificate name for an X.509 certificate */
	static String getName(X509Certificate certificate) {
	    /*
	     * Use the certificate serial number, which is unique for all
	     * certificates from a given issuer, plus the issuer name, to get a
	     * unique name for the certificate.
	     */
	    return certificate.getSerialNumber().toString(16) + "@" +
		getIssuerName(certificate);
	}

	/**
	 * Returns true if an X.509 certificate matches the certificate name
	 * specified in the constructor.
	 */
	boolean matches(X509Certificate certificate) {
	    return certificate.getSerialNumber().equals(serialNumber)
		&& getIssuerName(certificate).equals(issuerName);
	}

	/** Returns the canonical issuer name for an X.509 certificate. */
	private static String getIssuerName(X509Certificate certificate) {
	    return certificate.getIssuerX500Principal().getName(
		X500Principal.CANONICAL);
	}
    }
    
    /**
     * A privileged action that gets the private credentials for an X.509
     * certificate.
     */
    static class GetPrivateCredentialAction implements PrivilegedAction {
	private final Subject subject;
	private final X509Certificate cert;

	GetPrivateCredentialAction(Subject subject, X509Certificate cert) {
	    this.subject = subject;
	    this.cert = cert;
	}

	public Object run() {
	    return getPrivateCredential(subject, cert);
	}
    }
    
}
