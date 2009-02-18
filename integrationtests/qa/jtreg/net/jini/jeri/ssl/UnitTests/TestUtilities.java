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
/* @test 
 * @summary Tests the Utilities class
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm/policy=policy TestUtilities
 */

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Destroyable;
import javax.security.auth.Subject;
import javax.security.auth.x500.*;
import net.jini.core.constraint.*;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.*;
import net.jini.jeri.connection.*;
import net.jini.jeri.ssl.HttpsEndpoint;
import net.jini.jeri.ssl.HttpsServerEndpoint;
import net.jini.jeri.ssl.SslEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.security.*;

/** Provides common utilities for tests and tests provider utilities. */
public class TestUtilities extends UnitTestUtilities {

    /* -- Make sure system properties and security manager are set -- */

    static {
	Properties props = System.getProperties();
	String src = props.getProperty("test.src", ".") + File.separator;
	if (props.getProperty("keyStore") == null) {
	    props.setProperty("keyStore", src + "keystore");
	}
	if (props.getProperty("javax.net.ssl.trustStore") == null) {
	    props.setProperty("javax.net.ssl.trustStore", src + "truststore");
	}
	if (props.getProperty("java.security.policy") == null) {
	    props.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    /* -- Running tests -- */


    /* Reflection */

    /** The name of the package containing the classes */
    static String PACKAGE = "net.jini.jeri.ssl";

    static final LazyMethod impliesConstraintConstraint = new LazyMethod(
	"Utilities", "implies",
	new Class[] { InvocationConstraint.class, InvocationConstraint.class });
    static final LazyMethod impliesCollectionCollection = new LazyMethod(
	"Utilities", "implies",
	new Class[] { Collection.class, Collection.class });
    static final LazyMethod impliesCollectionConstraint = new LazyMethod(
	"Utilities", "implies",
	new Class[] { Collection.class, InvocationConstraint.class });
    private static final LazyMethod getConnectionEndpoint = new LazyMethod(
	"SslEndpoint", "getConnectionEndpoint", new Class[0]);
    private static final LazyField standardInboundRequestConnection =
	new LazyField("net.jini.jeri.connection", "ConnectionManager$Inbound",
		      "c");
    private static final LazyField httpsInboundRequestConnection =
	new LazyField("HttpsServerEndpoint$HttpsInboundRequest", "connection");
    private static final LazyField standardOutboundRequestConnection =
	new LazyField("net.jini.jeri.connection", "ConnectionManager$Outbound",
		      "c");
    private static final LazyField httpsOutboundRequestConnection =
	new LazyField("HttpsEndpoint$HttpsOutboundRequest", "connection");
    private static final LazyField maxClientSessionDuration =
	new LazyField("SslConnection", "maxClientSessionDuration");
    private static final LazyField maxServerSessionDuration =
	new LazyField("SslServerEndpointImpl", "maxServerSessionDuration");

    /* Running tests */

    /** Whether to use HTTPS. */
    public static final boolean useHttps = Boolean.getBoolean("useHttps");
    static {
	if (useHttps) {
	    System.out.println("Testing using HTTPS");
	}
    }

    /* Suites */

    /** All cipher suites */
    static final String[] allSuites =
	((SSLSocketFactory)
	 SSLSocketFactory.getDefault()).getSupportedCipherSuites();

    /** Cipher suites that do no authentication -- all do encryption */
    static final String[] anonymousSuites =
	containing(allSuites, new String[] { "anon" });

    /** Cipher suites that do no authentication and weak encryption */
    static final String[] weakAnonymousSuites =
	containing(anonymousSuites, new String[] { "_DES", "RC4_40" });

    /** Cipher suites that do no authentication and strong encryption */
    static final String[] strongAnonymousSuites =
	containing(anonymousSuites, new String[] { "RC4_128", "3DES" });

    /** Cipher suites that do not maintain integrity */
    static final String[] noIntegritySuites = null;

    /** Cipher suites that do authentication but not encryption */
    static final String[] nonEncryptingSuites =
	containing(allSuites, new String[] { "NULL" });

    /** Cipher suites that do authentication */
    static final String[] authenticatingSuites =
	containing(allSuites, new String[] { "DHE", "RSA" });

    /** Cipher suites that do authentication and use RSA keys */
    static final String[] authenticatingRSASuites =
	containing(authenticatingSuites, new String[] { "RSA" });

    /** Cipher suites that do authentication and weak encryption */
    static final String[] weakAuthenticatingSuites =
	containing(authenticatingSuites, new String[] { "_DES", "RC4_40" });

    /**
     * Cipher suites that do authentication, weak encryption, and use RSA keys
     */
    static final String[] weakAuthenticatingRSASuites = 
	containing(weakAuthenticatingSuites, new String[] { "RSA" });

    /** Cipher suites that do strong encryption */
    static final String[] strongAuthenticatingSuites =
	containing(authenticatingSuites, new String[] { "RC4_128", "3DES" });

    /**
     * Cipher suites that do authentication, strong encryption, and use RSA
     * keys
     */
    static final String[] strongAuthenticatingRSASuites =
	containing(strongAuthenticatingSuites, new String[] { "RSA" });

    /** Cipher suites that do authentication and encryption */
    static final String[] encryptingSuites =
	containing(authenticatingSuites, new String[] { "DES", "RC4" });

    /** Cipher suites that do authentication and encryption and use RSA keys */
    static final String[] encryptingRSASuites =
	containing(encryptingSuites, new String[] { "RSA" });

    /* Credentials */

    private static KeyStore keyStore;

    private static CertificateFactory certFactory;

    static char[] keyStorePassword = "keypass".toCharArray();

    static final String clientDSA = "CN=clientDSA";
    static final String clientRSA1 = "CN=clientRSA1, C=US";
    static final String clientRSA2 = "CN=clientRSA2";

    static final Subject clientSubject = new WithSubject() { {
	addX500Principal("clientDSA", subject);
	addX500Principal("clientRSA1", subject);
	subject.getPrincipals().add(new TestPrincipal("testClient"));
    } }.subject();

    static final Principal[] clientX500Principals = {
	x500(clientRSA1), x500(clientDSA)
    };

    static final InvocationConstraint clientMinPrincipals =
        alternatives(minPrincipals(x500(clientRSA1)),
		     minPrincipals(x500(clientDSA)));

    static final Subject clientRSASubject = new WithSubject() { {
	addX500Principal("clientRSA1", subject);
    } }.subject();

    static final String serverDSA = "CN=serverDSA, C=US";
    static final String serverRSA = "CN=serverRSA";

    static final Subject serverSubject = new WithSubject() { {
	addX500Principal("serverDSA", subject);
	addX500Principal("serverRSA", subject);
	subject.getPrincipals().add(new TestPrincipal("testServer"));
    } }.subject();

    static final X500Principal[] serverX500Principals = {
	x500(serverDSA), x500(serverRSA)
    };

    static final InvocationConstraint serverMinPrincipals =
        alternatives(serverPrincipals(x500(serverDSA)),
		     serverPrincipals(x500(serverRSA)));

    static final Subject serverRSASubject = new WithSubject() { {
	    addX500Principal("serverRSA", subject);	    
    } }.subject();

    /* -- Misc -- */

    /**
     * Prints the specified debugging message if the test level is at least
     * the value specified.
     */
    public static void debugPrint(int forLevel, String message) {
	if (testLevel >= forLevel) {
	    System.out.println(message);
	}
    }

    /**
     * Returns a server endpoint for the specified server subject and value for
     * client authentication.  The useHttps system property controls whether to
     * use direct or HTTPS connections.
     */
    static ServerEndpoint createServerEndpoint(Subject serverSubject) {
	return createServerEndpoint(serverSubject, null, "localhost", 0);
    }

    static ServerEndpoint createServerEndpoint(int port) {
	if (useHttps) {
	    return HttpsServerEndpoint.getInstance(port);
	} else {
	    return SslServerEndpoint.getInstance(port);
	}
    }

    static ServerEndpoint createServerEndpoint(String serverHost, int port) {
	if (useHttps) {
	    return HttpsServerEndpoint.getInstance(serverHost, port);
	} else {
	    return SslServerEndpoint.getInstance(serverHost, port);
	}
    }

    static ServerEndpoint createServerEndpoint(
	String serverHost,
	int port,
	SocketFactory socketFactory,
	ServerSocketFactory serverSocketFactory)
    {
	if (useHttps) {
	    return HttpsServerEndpoint.getInstance(
		serverHost, port, socketFactory, serverSocketFactory);
	} else {
	    return SslServerEndpoint.getInstance(
		serverHost, port, socketFactory, serverSocketFactory);
	}
    }

    /**
     * Returns a server endpoint for the specified server subject, value for
     * client authentication, server host and port.  The useHttps system
     * property controls whether to use direct or HTTPS connections.
     */
    static ServerEndpoint createServerEndpoint(Subject serverSubject,
					       X500Principal[] serverPrincipals,
					       String serverHost,
					       int port)
    {
	if (useHttps) {
	    return HttpsServerEndpoint.getInstance(
		serverSubject, serverPrincipals, serverHost, port);
	} else {
	    return SslServerEndpoint.getInstance(
		serverSubject, serverPrincipals, serverHost, port);
	}
    }

    static ServerEndpoint createServerEndpoint(
	Subject serverSubject,
	X500Principal[] serverPrincipals,
	String serverHost,
	int port,
	SocketFactory socketFactory,
	ServerSocketFactory serverSocketFactory)

    {
	if (useHttps) {
	    return HttpsServerEndpoint.getInstance(
		serverSubject, serverPrincipals, serverHost, port,
		socketFactory, serverSocketFactory);
	} else {
	    return SslServerEndpoint.getInstance(
		serverSubject, serverPrincipals, serverHost, port,
		socketFactory, serverSocketFactory);
	}
    }


    /**
     * Returns an endpoint for the specified server host and port.  The
     * useHttps system property controls whether to use direct or HTTPS
     * connections.
     */
    static Endpoint createEndpoint(String serverHost, int port) {
	if (useHttps) {
	    return HttpsEndpoint.getInstance(serverHost, port);
	} else {
	    return SslEndpoint.getInstance(serverHost, port);
	}
    }

    /**
     * Returns an array containing the elements of strings that contain one or
     * more of the strings in keywords as substrings.
     */
    static String[] containing(String[] strings, String[] keywords) {
	List l = new ArrayList(strings.length);
	for (int i = 0; i < strings.length; i++) {
	    String s = strings[i];
	    for (int j = keywords.length; --j >= 0; ) {
		if (s.indexOf(keywords[j]) >= 0) {
		    l.add(s);
		    break;
		}
	    }
	}
	return (String[]) l.toArray(new String[l.size()]);
    }

    /**
     * Sets the value of SslConnection.maxClientSessionDuration and returns
     * the old value.
     */
    static long setMaxClientSessionDuration(long l) {
	long old = ((Long) maxClientSessionDuration.getStatic()).longValue();
	maxClientSessionDuration.setStatic(new Long(l));
	return old;
    }

    /**
     * Sets the value of SslServerEndpointImpl.maxServerSessionDuration and
     * returns the old value.
     */
    static long setMaxServerSessionDuration(long l) {
	long old = ((Long) maxServerSessionDuration.getStatic()).longValue();
	maxServerSessionDuration.setStatic(new Long(l));
	return old;
    }

    /* -- withAuthenticationPermissions -- */

    /**
     * Returns an AccessControlContext similar to the current one, but with
     * only the specified AuthenticationPermissions.  A null argument removes
     * all AuthenticationPermissions.
     */
    static AccessControlContext withAuthenticationPermissions(
	AuthenticationPermission[] authPerms)
    {
	return withPermissions(AuthenticationPermission.class, authPerms);
    }

    /* -- Reflection utilities -- */

    /** Returns the provider class with the specified name */
    static Class getClass(String className) {
	return getClass(PACKAGE, className);
    }

    /** Returns the class in the specified package with the specified name. */
    static Class getClass(String packageName, String className) {
	try {
	    return Class.forName(packageName + "." + className);
	} catch (ClassNotFoundException e) {
	    throw unexpectedException(e);
	}
    }

    /** Like Method, but resolves method when first invoked */
    static class LazyMethod {
	private String className;
	private String methodName;
	private Class[] argumentTypes;
	private Method method;

	LazyMethod(String className,
		   String methodName,
		   Class[] argumentTypes)
	{
	    this.className = className;
	    this.methodName = methodName;
	    this.argumentTypes = argumentTypes;
	}

	/**
	 * Invokes a static method, throwing a RuntimeException if any
	 * exception is thrown.
	 */
	Object invokeStatic(Object[] arguments) {
	    return invoke(null, arguments);
	}

	/**
	 * Invokes a method on an object, throwing a RuntimeException if any
	 * exception is thrown.
	 */
	Object invoke(Object object, Object[] arguments) {
	    try {
		return getMethod().invoke(object, arguments);
	    } catch (InvocationTargetException e) {
		throw unexpectedException(e.getTargetException());
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	/**
	 * Invokes a method on an object, throwing InvocationTargetException if
	 * an exception occurs during the method invocation.
	 */
	Object invokeWithThrows(Object object, Object[] arguments)
	    throws InvocationTargetException
	{
	    try {
		return getMethod().invoke(object, arguments);
	    } catch (InvocationTargetException e) {
		throw e;
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	/** Returns the requested provider method */
	private Method getMethod() {
	    if (method == null) {
		try {
		    Class type = TestUtilities.getClass(className);
		    method = type.getDeclaredMethod(methodName, argumentTypes);
		    method.setAccessible(true);
		} catch (NoSuchMethodException e) {
		    throw unexpectedException(e);
		}
	    }
	    return method;
	}
    }

    /** Like Constructor, but resolves constructor when first used */
    static class LazyConstructor {
	private String className;
	private Class[] argumentTypes;
	private Constructor constructor;

	LazyConstructor(String className, Class[] argumentTypes) {
	    this.className = className;
	    this.argumentTypes = argumentTypes;
	}

	/**
	 * Creates a new instance, throwing a RuntimeException if any exception
	 * is thrown
	 */
	Object newInstance(Object[] arguments) {
	    try {
		return getConstructor().newInstance(arguments);
	    } catch (InvocationTargetException e) {
		throw unexpectedException(e.getTargetException());
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	/**
	 * Creates a new instance, throwing InvocationTargetException if an
	 * exception is thrown while invoking the constructor.
	 */
	Object newInstanceWithThrows(Object[] arguments)
	    throws InvocationTargetException
	{
	    try {
		return getConstructor().newInstance(arguments);
	    } catch (InvocationTargetException e) {
		throw e;
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	/** Returns the requested provider constructor */
	private Constructor getConstructor() {
	    if (constructor == null) {
		try {
		    Class type = TestUtilities.getClass(className);
		    constructor = type.getDeclaredConstructor(argumentTypes);
		    constructor.setAccessible(true);
		} catch (NoSuchMethodException e) {
		    throw unexpectedException(e);
		}
	    }
	    return constructor;
	}
    }

    /** Like Field, but resolves field when first used */
    static class LazyField {
	private String packageName;
	private String className;
	private String fieldName;
	private Field field;

	LazyField(String className, String fieldName) {
	    this(PACKAGE, className, fieldName);
	}

	LazyField(String packageName, String className, String fieldName) {
	    this.packageName = packageName;
	    this.className = className;
	    this.fieldName = fieldName;
	}

	/** Gets a static field */
	Object getStatic() {
	    return get(null);
	}

	/** Gets a field */
	Object get(Object object) {
	    try {
		return getField().get(object);
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	/** Returns the requested provider field */
	private Field getField() {
	    if (field == null) {
		try {
		    Class type =
			TestUtilities.getClass(packageName, className);
		    field = type.getDeclaredField(fieldName);
		    field.setAccessible(true);
		} catch (NoSuchFieldException e) {
		    throw unexpectedException(e);
		}
	    }
	    return field;
	}

	/** Sets a static field */
	void setStatic(Object value) {
	    set(null, value);
	}

	/** Sets a field */
	void set(Object object, Object value) {
	    try {
		getField().set(object, value);
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}
    }

    /* -- Methods for accessing connections -- */

    static ServerConnection getInboundRequestConnection(InboundRequest r) {
	if (useHttps) {
	    return (ServerConnection)
		httpsInboundRequestConnection.get(r);
	} else {
	    return (ServerConnection)
		standardInboundRequestConnection.get(r);
	}
    }

    static Connection getOutboundRequestConnection(OutboundRequest r) {
	if (useHttps) {
	    return (Connection) httpsOutboundRequestConnection.get(r);
	} else {
	    return (Connection) standardOutboundRequestConnection.get(r);
	}
    }

    /**
     * Returns the ConnectionEndpoint associated with a Endpoint.
     */
    static ConnectionEndpoint getConnectionEndpoint(Endpoint endpoint) {
	return (ConnectionEndpoint) getConnectionEndpoint.invoke(
	    endpoint, new Object[0]);
    }

    /* -- Constraint utilities -- */

    /**
     * Returns true if the requirements in the first constraints imply the
     * ones in the second.
     */
    static boolean implies(InvocationConstraints c1, InvocationConstraints c2) {
	return implies(c1.requirements(), c2.requirements());
    }

    /** Returns true if one collection of constraints implies another */
    static boolean implies(Collection c1, Collection c2) {
	Object result = impliesCollectionCollection.invokeStatic(
	    new Object[] { c1, c2 });
	return ((Boolean) result).booleanValue();
    }

    /**
     * Returns true if the requirements in the constraints imply the
     * constraint.
     */
    static boolean implies(InvocationConstraints constraints,
			   InvocationConstraint constraint)
    {
	return implies(constraints.requirements(), constraint);
    }

    /** Returns true if a collection of constraint simplies a constraint */
    static boolean implies(Collection constraints,
			   InvocationConstraint constraint)
    {
	Object result = impliesCollectionConstraint.invokeStatic(
	    new Object[] { constraints, constraint });
	return ((Boolean) result).booleanValue();
    }

    /** Returns true if one constraint implies another */
    static boolean implies(InvocationConstraint c1, InvocationConstraint c2) {
	Object result = impliesConstraintConstraint.invokeStatic(
	    new Object[] { c1, c2 });
	return ((Boolean) result).booleanValue();
    }

    /* -- Credentials -- */

    static class TestPrincipal implements Principal {
	String name;
	TestPrincipal(String name) { this.name = name; }
	public String getName() { return name; }
	public String toString() { return "TestPrincipal{" + name + "}"; }
	public int hashCode() { return name.hashCode(); }
	public boolean equals(Object other) {
	    if (other instanceof TestPrincipal) {
		return name.equals(((TestPrincipal) other).name);
	    } else {
		return false;
	    }
	}
    }

    static KeyStore getKeyStore() {
	if (keyStore == null) {
	    String filename = System.getProperty("keyStore");
	    if (filename == null) {
		return null;
	    }
	    try {
		InputStream in =
		    new BufferedInputStream(new FileInputStream(filename));
		keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(in, null);
		in.close();
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    } catch (KeyStoreException e) {
		throw new RuntimeException(e.toString());
	    } catch (GeneralSecurityException e) {
		throw new RuntimeException(e.toString());
	    }
	}
	return keyStore;
    }

    static CertPath getCertificateChain(String alias) {
	try {
	    Certificate[] chain = getKeyStore().getCertificateChain(alias);
	    if (chain == null) {
		throw new RuntimeException(
		    "Certificate chain not found for alias " + alias);
	    }
	    return getCertFactory().generateCertPath(Arrays.asList(chain));
	} catch (KeyStoreException e) {
	    throw new RuntimeException(e.toString());
	} catch (GeneralSecurityException e) {
	    throw new RuntimeException(e.toString());
	}
    }

    static CertificateFactory getCertFactory() {
	if (certFactory == null) {
	    try {
		certFactory = CertificateFactory.getInstance("X.509");
	    } catch (CertificateException e) {
		throw new RuntimeException(e.toString());
	    }
	}
	return certFactory;
    }

    static PrivateKey getPrivateKey(String alias) {
	try {
	    PrivateKey result =
		(PrivateKey) getKeyStore().getKey(alias, keyStorePassword);
	    if (result == null) {
		throw new RuntimeException(
		    "Private key not found for alias " + alias);
	    }
	    return result;
	} catch (KeyStoreException e) {
	    throw new RuntimeException(e.toString());
	} catch (GeneralSecurityException e) {
	    throw new RuntimeException(e.toString());
	}
    }

    static void addX500Principal(String alias, Subject subject) {
	addX500Principal(alias, subject, true);
    }

    static void addX500Principal(String alias,
				 Subject subject,
				 boolean includePrivateKey)
    {
	try {
	    KeyStore keyStore = getKeyStore();
	    if (keyStore == null) {
		return;
	    }
	    CertPath certificateChain = getCertificateChain(alias);
 	    subject.getPublicCredentials().add(certificateChain);
	    X509Certificate certificate =
		(X509Certificate) certificateChain.getCertificates().get(0);
	    subject.getPrincipals().add(
		x500(certificate.getSubjectDN().getName()));
	    if (includePrivateKey) {
		PublicKey publicKey = certificate.getPublicKey();
		PrivateKey privateKey =
		    (PrivateKey) keyStore.getKey(alias, keyStorePassword);
		subject.getPrivateCredentials().add(
		    new X500PrivateCredential(certificate, privateKey));
	    }
	} catch (KeyStoreException e) {
	    throw new RuntimeException(e.toString());
	} catch (GeneralSecurityException e) {
	    throw new RuntimeException(e.toString());
	}
    }

    static void addPrivateKey(String keyAlias,
			      String certificateAlias,
			      Subject subject)
    {
	try {
	    KeyStore keyStore = getKeyStore();
	    if (keyStore == null) {
		return;
	    }
	    CertPath certificateChain = getCertificateChain(certificateAlias);
	    X509Certificate certificate =
		(X509Certificate) certificateChain.getCertificates().get(0);
	    PublicKey publicKey = certificate.getPublicKey();
	    PrivateKey privateKey =
		(PrivateKey) keyStore.getKey(keyAlias, keyStorePassword);
	    subject.getPrivateCredentials().add(
		new X500PrivateCredential(certificate, privateKey));
	} catch (KeyStoreException e) {
	    throw new RuntimeException(e.toString());
	} catch (GeneralSecurityException e) {
	    throw new RuntimeException(e.toString());
	}
    }

    static void destroyPrivateCredentials(Subject subject) {
	for (Iterator i = subject.getPrivateCredentials().iterator();
	     i.hasNext(); )
	{
		Object cred = i.next();
		if (cred instanceof Destroyable) {
		    try {
			((Destroyable) cred).destroy();
		    } catch (Exception e) {
			throw unexpectedException(e);
		    }
		}
	}
    }

    static public class WithSubject {
	protected Subject subject = new Subject();
	public Subject subject() {
	    return subject(true);
	}
	public Subject subject(boolean setReadOnly) {
	    if (setReadOnly) {
		subject.setReadOnly();
	    }
	    return subject;
	}
    }

    /**
     * Returns true if two subjects should be considered equal from the point
     * of view of the JSSE RMI transport provider.
     */
     static boolean subjectsEqual(Subject s1, Subject s2) {
	 if (s1 == null || s2 == null) {
	     return s1 == s2;
	 }
	 return
	     s1.getPrincipals().equals(s2.getPrincipals())
	     && publicCredentialsEqual(s1.getPublicCredentials(),
				       s2.getPublicCredentials())
	     && s1.getPrivateCredentials().equals(
		 s2.getPrivateCredentials());
     }

    /**
     * Returns true if two sets of public credentials should be considered
     * equal from the point of view of the JSSE RMI transport provider.
     */
    static boolean publicCredentialsEqual(Set p1, Set p2) {
	for (Iterator i1 = p1.iterator(); i1.hasNext(); ) {
	    Object cred1 = i1.next();
	    if (cred1 instanceof CertPath && !p2.contains(cred1)) {
		return false;
	    }
	}
	for (Iterator i2 = p2.iterator(); i2.hasNext(); ) {
	    Object cred2 = i2.next();
	    if (cred2 instanceof CertPath && !p1.contains(cred2)) {
		return false;
	    }
	}
	return true;
    }

    /** Returns a String that describes the subject. */
    static String subjectString(Subject subject) {
	if (testLevel >= 30) {
	    return String.valueOf(subject);
	} else {
	    return (subject == null)
		? "[]" : subject.getPrincipals().toString();
	}
    }

    /** Returns true if two arrays contain equal objects. */
    static boolean arraysEqual(Object[] array1, Object[] array2) {
	if (array1.length != array2.length) {
	    return false;
	}
	for (int i = 0; i < array1.length; i++) {
	    if (array1[i] == null
		? array2[i] != null
		: !array1[i].equals(array2[i]))
	    {
		return false;
	    }
	}
	return true;
    }

    static Set asSet(Object[] array) {
	if (array == null) {
	    return null;
	}
	Set result = new HashSet(array.length);
	for (int i = 0; i < array.length; i++) {
	    result.add(array[i]);
	}
	return result;
    }

    static Set asSet(Collection collection) {
	if (collection == null) {
	    return null;
	} else {
	    return new HashSet(collection);
	}
    }

    static Collection asList(Object[] array) {
	Collection result = new ArrayList(array.length);
	for (int i = 0; i < array.length; i++) {
	    result.add(array[i]);
	}
	return result;
    }

    static Iterator asIterator(final Object[] array) {
	return new Iterator() {
	    int next;
	    public boolean hasNext() { return next < array.length; }
	    public Object next() {
		if (hasNext()) {
		    return array[next++];
		} else {
		    throw new NoSuchElementException();
		}
	    }
	    public void remove() {
		throw new UnsupportedOperationException();
	    }
	};
    }

    /* -- Check if streams are closed -- */

    /** Checks if the input stream is closed. */
    static void checkClosed(final InputStream in, final String name) {
	class Checker extends Thread {
	    Throwable exception;
	    boolean eof;
	    boolean done;
	    public void run() {
		try {
		    eof = in.read() == -1;
		    if (eof) {
			debugPrint(30, "Got EOF for reading " + name);
		    }
		    done = true;
		} catch (IOException e) {
		    debugPrint(
			30,
			"Got exception for reading " + name + ": " + e);
		    eof = true;
		    done = true;
		} catch (Throwable t) {
		    exception = t;
		    done = true;
		}
		synchronized (this) {
		    notify();
		}
	    }
	}
	Checker checker = new Checker();
	try {
	    synchronized (checker) {
		checker.setDaemon(true);
		checker.start();
		checker.wait(100 * 1000);
	    }
	} catch (InterruptedException e) {
	    throw new Test.FailedException(
	      "Unexpected exception for " + name + ": " + e);
	}
	synchronized (checker) {
	    if (checker.exception != null) {
		throw new Test.FailedException(
		    "Unexpected exception for " + name + ": " +
		    toString(checker.exception));
	    } else if (!checker.done) {
		throw new Test.FailedException(
		    "Read did not complete for " + name);
	    } else if (!checker.eof) {
		throw new Test.FailedException(
		    "Stream not closed for " + name);
	    }
	}
    }

    /** Checks if the output stream is closed. */
    static void checkClosed(final OutputStream out, String name) {
	class Checker extends Thread {
	    Throwable exception;
	    boolean done;
	    public void run() {
		try {
		    /*
		     * Write enough bytes that the socket sends the data in two
		     * batches -- sockets only detect that the other side has
		     * failed after the first write attempt fails to go
		     * through.  -tjb[8.Feb.2002]
		     */
		    out.write(new byte[2048]);
		    out.flush();
		    out.close();
		    done = true;
		} catch (Throwable t) {
		    exception = t;
		    done = true;
		}
		synchronized (this) {
		    notify();
		}
	    }
	}
	Checker checker = new Checker();
	try {
	    synchronized (checker) {
		checker.start();
		checker.wait(100 * 1000);
	    }
	} catch (InterruptedException e) {
	    throw new Test.FailedException(
		"Unexpected exception for " + name + ": " + e);
	}
	synchronized (checker) {
	    if (!checker.done) {
		throw new Test.FailedException(
		    "Write did not complete for " + name);
	    } else if (!(checker.exception instanceof IOException)) {
		throw new Test.FailedException(
		    "Stream not closed for " + name + ": " +
		    (checker.exception == null
		     ? "No exception thrown"
		     : "Wrong exception thrown: " + checker.exception));
	    } else {
		debugPrint(30,
			   "Got exception for writing " + name +
			   ": " + checker.exception);
	    }
	}
    }

    /* -- TestConstraint -- */

    public static class TestConstraint implements InvocationConstraint { }

    /* -- Constraints -- */

    public static InvocationConstraints requirements(InvocationConstraint c) {
	return new InvocationConstraints(c, null);
    }

    public static InvocationConstraints requirements(InvocationConstraint c1,
						     InvocationConstraint c2)
    {
	return new InvocationConstraints(array(c1, c2), null);
    }

    public static InvocationConstraints requirements(InvocationConstraint c1,
						     InvocationConstraint c2,
						     InvocationConstraint c3)
    {
	return new InvocationConstraints(array(c1, c2, c3), null);
    }

    public static InvocationConstraints requirements(InvocationConstraint c1,
						     InvocationConstraint c2,
						     InvocationConstraint c3,
						     InvocationConstraint c4)
    {
	return new InvocationConstraints(array(c1, c2, c3, c4), null);
    }

    public static InvocationConstraints requirements(InvocationConstraint c1,
						     InvocationConstraint c2,
						     InvocationConstraint c3,
						     InvocationConstraint c4,
						     InvocationConstraint c5)
    {
	return new InvocationConstraints(array(c1, c2, c3, c4, c5), null);
    }


    public static InvocationConstraints preferences(InvocationConstraint c) {
	return new InvocationConstraints(null, c);
    }

    public static InvocationConstraints preferences(InvocationConstraint c1,
						    InvocationConstraint c2)
    {
	return new InvocationConstraints(null, array(c1, c2));
    }

    public static InvocationConstraints constraints(InvocationConstraint r,
						    InvocationConstraint p)
    {
	return new InvocationConstraints(r, p);
    }

    public static InvocationConstraints constraints(InvocationConstraint[] r,
						    InvocationConstraint[] p)
    {
	return new InvocationConstraints(r, p);
    }

    public static InvocationConstraint[] array(InvocationConstraint c)
    {
	return new InvocationConstraint[] { c };
    }

    public static InvocationConstraint[] array(InvocationConstraint c1,
					       InvocationConstraint c2)
    {
	return new InvocationConstraint[] { c1, c2 };
    }

    public static InvocationConstraint[] array(InvocationConstraint c1,
					       InvocationConstraint c2,
					       InvocationConstraint c3)
    {
	return new InvocationConstraint[] { c1, c2, c3 };
    }

    public static InvocationConstraint[] array(InvocationConstraint c1,
					       InvocationConstraint c2,
					       InvocationConstraint c3,
					       InvocationConstraint c4)
    {
	return new InvocationConstraint[] { c1, c2, c3, c4 };
    }

    public static InvocationConstraint[] array(InvocationConstraint c1,
					       InvocationConstraint c2,
					       InvocationConstraint c3,
					       InvocationConstraint c4,
					       InvocationConstraint c5)
    {
	return new InvocationConstraint[] { c1, c2, c3, c4, c5 };
    }

    public static ClientMinPrincipal minPrincipals(Principal principal) {
	return new ClientMinPrincipal(principal);
    }

    public static ClientMinPrincipal minPrincipals(Principal p1, Principal p2)
    {
	return new ClientMinPrincipal(array(p1, p2));
    }

    public static ClientMinPrincipal minPrincipals(Principal p1,
						   Principal p2,
						   Principal p3)
    {
	return new ClientMinPrincipal(array(p1, p2, p3));
    }

    public static ClientMinPrincipal minPrincipals(Principal[] principals) {
	return new ClientMinPrincipal(principals);
    }

    public static ClientMaxPrincipal maxPrincipals(Principal principal) {
	return new ClientMaxPrincipal(principal);
    }

    public static ClientMaxPrincipal maxPrincipals(Principal p1, Principal p2)
    {
	return new ClientMaxPrincipal(array(p1, p2));
    }

    public static ClientMaxPrincipal maxPrincipals(Principal p1,
						   Principal p2,
						   Principal p3)
    {
	return new ClientMaxPrincipal(array(p1, p2, p3));
    }

    public static ClientMaxPrincipal maxPrincipals(Principal[] principals) {
	return new ClientMaxPrincipal(principals);
    }

    public static ServerMinPrincipal serverPrincipals(Principal p1) {
	return new ServerMinPrincipal(p1);
    }

    public static ServerMinPrincipal serverPrincipals(Principal p1,
						      Principal p2)
    {
	return new ServerMinPrincipal(array(p1, p2));
    }

    public static ServerMinPrincipal serverPrincipals(Principal[] principals) {
	return new ServerMinPrincipal(principals);
    }

    public static X500Principal x500(String name) {
	return new X500Principal(name);
    }

    public static Principal[] array(Principal p1) {
	return new Principal[] { p1 };
    }

    public static Principal[] array(Principal p1, Principal p2) {
	return new Principal[] { p1, p2 };
    }

    public static Principal[] array(Principal p1, Principal p2, Principal p3) {
	return new Principal[] { p1, p2, p3 };
    }

    public static InvocationConstraint alternatives(InvocationConstraint c1,
						    InvocationConstraint c2)
    {
	return ConstraintAlternatives.create(array(c1, c2));
    }

    /* -- Tests -- */

    static final Collection tests = new ArrayList();

    public static void main(String[] args) throws Exception {
	test(tests);
    }

    /* -- SuiteTest base class -- */

    static abstract class SuiteTest extends BasicTest {
	final String suite;

	SuiteTest(String suite, Object result) {
	    super(suite, result);
	    this.suite = suite;
	}

	SuiteTest(String suite, String name, Object result) {
	    super(suite + ", " + name, result);
	    this.suite = suite;
	}
    }

    /* -- Test Utilities.getCipherAlgorithm -- */

    static {
	tests.add(TestGetCipherAlgorithm.localtests);
    }

    static class TestGetCipherAlgorithm extends SuiteTest {
	private static final LazyMethod getCipherAlgorithm = new LazyMethod(
	    "Utilities", "getCipherAlgorithm",
	    new Class[] { String.class });

	static final Test[] localtests = {
	    new TestGetCipherAlgorithm(
		"SSL_DH_anon_WITH_DES_CBC_SHA", "DES_CBC"),
	    new TestGetCipherAlgorithm(
		"SSL_RSA_EXPORT_WITH_RC4_40_MD5", "RC4_40"),
	    new TestGetCipherAlgorithm("SSL_RSA_WITH_NULL_SHA", "NULL"),
	    new TestGetCipherAlgorithm("SSL_NULL_WITH_NULL_NULL", "NULL"),
	    new TestGetCipherAlgorithm("", "NULL"),
	    new TestGetCipherAlgorithm("foo bar baz", "NULL"),
	    new TestGetCipherAlgorithm("_WITH_", "NULL"),	    
	};

	TestGetCipherAlgorithm(String suite, String result) {
	    super(suite, result);
	}

	public Object run() {
	    return getCipherAlgorithm.invokeStatic(new Object[] { suite });
	}
    }

    /* -- Test Utilities.getKeyExchangeAlgorithm -- */

    static {
	tests.add(TestGetKeyExchangeAlgorithm.localtests);
    }

    static class TestGetKeyExchangeAlgorithm extends SuiteTest {
	private static final LazyMethod getKeyExchangeAlgorithm =
	    new LazyMethod("Utilities", "getKeyExchangeAlgorithm",
			   new Class[] { String.class });

	static final Test[] localtests = {
	    test("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", "DH_anon_EXPORT"),
	    test("SSL_DH_anon_WITH_RC4_128_MD5", "DH_anon"),
	    test("SSL_NULL_WITH_NULL_NULL", "NULL"),
	    test("SSL_RSA_WITH_NULL_MD5", "RSA"),
	    test("SSL_FORTEZZA_DMS_WITH_NULL_SHA", "FORTEZZA_DMS"),
	    test("SSL_RSA_EXPORT_WITH_RC4_40_MD5", "RSA_EXPORT"),
	    test("SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA", "DH_DSS_EXPORT"),
	    test("SSL_DH_DSS_WITH_DES_CBC_SHA", "DH_DSS"),
	    test("SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA", "DH_RSA_EXPORT"),
	    test("SSL_DH_RSA_WITH_DES_CBC_SHA", "DH_RSA"),
	    test("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", "DHE_DSS_EXPORT"),
	    test("SSL_DHE_DSS_WITH_DES_CBC_SHA", "DHE_DSS"),
	    test("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "DHE_RSA_EXPORT"),
	    test("SSL_DHE_RSA_WITH_DES_CBC_SHA", "DHE_RSA"),
	    test("SSL_NULL_WITH_NULL_NULL", "NULL"),
	    test("", "NULL"),
	    test("foo bar baz", "NULL"),
	    test("_WITH_", "NULL")
	};

	private static Test test(String suite, String result) {
	    return new TestGetKeyExchangeAlgorithm(suite, result);
	}

	private TestGetKeyExchangeAlgorithm(String suite, String result) {
	    super(suite, result);
	}

	public Object run() {
	    return getKeyExchangeAlgorithm.invokeStatic(
		new Object[] { suite });
	}
    }

    /* -- Test Utilities.getPermittedKeyAlgorithms -- */

    static {
	tests.add(TestGetKeyExchangeAlgorithm.localtests);
    }

    static class TestGetPermittedKeyAlgorithms extends SuiteTest {
	private static final LazyMethod getPermittedKeyAlgorithms =
	    new LazyMethod("Utilities", "getPermittedKeyAlgorithms",
			   new Class[] { String.class, boolean.class });
	private static final int NULL = 0;
	private static final int DSA =
	    ((Integer) new LazyField(
		"Utilities", "DSA_KEY_ALGORITHM").getStatic()).intValue();
	private static final int RSA =
	    ((Integer) new LazyField(
		"Utilities", "RSA_KEY_ALGORITHM").getStatic()).intValue();
	private static final int FORTEZZA =
	    ((Integer) new LazyField(
		"Utilities",
		"FORTEZZA_KEY_ALGORITHM").getStatic()).intValue();
	private static final boolean CLIENT = true;
	private static final boolean SERVER = false;

	static final Test[] localtests = {
	    test("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", SERVER, NULL),
	    test("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", CLIENT, NULL),
	    test("SSL_DH_anon_WITH_RC4_128_MD5", SERVER, NULL),
	    test("SSL_NULL_WITH_NULL_NULL", SERVER, NULL),
	    test("SSL_NULL_WITH_NULL_NULL", CLIENT, NULL),
	    test("SSL_RSA_WITH_NULL_MD5", SERVER, RSA),
	    test("SSL_RSA_WITH_NULL_MD5", CLIENT, DSA | RSA),
	    test("SSL_FORTEZZA_DMS_WITH_NULL_SHA", SERVER, FORTEZZA),
	    test("SSL_RSA_EXPORT_WITH_RC4_40_MD5", SERVER, RSA),
	    test("SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA", SERVER, DSA),
	    test("SSL_DH_DSS_WITH_DES_CBC_SHA", SERVER, DSA),
	    test("SSL_DH_DSS_WITH_DES_CBC_SHA", CLIENT, DSA | RSA),
	    test("SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA", SERVER, RSA),
	    test("SSL_DH_RSA_WITH_DES_CBC_SHA", SERVER, RSA),
	    test("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", SERVER, DSA),
	    test("SSL_DHE_DSS_WITH_DES_CBC_SHA", SERVER, DSA),
	    test("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", SERVER, RSA),
	    test("SSL_DHE_RSA_WITH_DES_CBC_SHA", SERVER, RSA)
	};

	private final boolean client;

	private static Test test(String suite, boolean client, int result) {
	    return new TestGetPermittedKeyAlgorithms(suite, client, result);
	}

	private TestGetPermittedKeyAlgorithms(String suite,
					      boolean client,
					      int result)
	{
	    super(suite, "client=" + client + ")", new Integer(result));
	    this.client = client;
	}

	public Object run() {
	    return getPermittedKeyAlgorithms.invokeStatic(
		new Object[] { suite, new Boolean(client) });
	}
    }

    /* -- Test Utilities.doesServerAuthentication -- */

    static {
	tests.add(TestDoesServerAuthentication.localtests);
    }

    static class TestDoesServerAuthentication extends SuiteTest {
	private static final LazyMethod doesServerAuthentication =
	    new LazyMethod("Utilities", "doesServerAuthentication",
			   new Class[] { String.class });

	static final Test[] localtests = {
	    new TestDoesServerAuthentication(
		"SSL_DH_anon_WITH_DES_CBC_SHA", false),
	    new TestDoesServerAuthentication(
		"SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", false),
	    new TestDoesServerAuthentication(
		"SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA", true)
	};

	private TestDoesServerAuthentication(String suite, boolean result) {
	    super(suite, Boolean.valueOf(result));
	}

	public Object run() {
	    return doesServerAuthentication.invokeStatic(
		new Object[] { suite });
	}
    }

    /* -- Test Utilities.doesEncryption -- */

    static {
	tests.add(TestDoesEncryption.localtests);
    }

    static class TestDoesEncryption extends SuiteTest {
	private static final LazyMethod doesEncryption = new LazyMethod(
	    "Utilities", "doesEncryption", new Class[] { String.class });

	static final Test[] localtests = {
	    new TestDoesEncryption("SSL_DH_anon_WITH_DES_CBC_SHA", true),
	    new TestDoesEncryption("SSL_RSA_WITH_NULL_MD5", false),
	    new TestDoesEncryption("SSL_RSA_EXPORT_WITH_RC4_40_MD5", true)
	};

	private TestDoesEncryption(String suite, boolean result) {
	    super(suite, Boolean.valueOf(result));
	}

	public Object run() {
	    return doesEncryption.invokeStatic(new Object[] { suite });
	}
    }

    /* -- Test Utilities.getSupportedCipherSuites -- */

    static {
	tests.add(TestGetSupportedCipherSuites.localtests);
	tests.add(TestRequestCipherSuites.localtests);
    }

    static class TestGetSupportedCipherSuites extends BasicTest {
	private static final LazyMethod getSupportedCipherSuites =
	    new LazyMethod("Utilities", "getSupportedCipherSuites",
			   new Class[] { String[].class });

	static final Test[] localtests = {
	    new TestGetSupportedCipherSuites(new String[0]),
	    new TestGetSupportedCipherSuites(
		new String[] {
		    "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
		    "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
		    "SSL_DHE_DSS_WITH_AES_128_CBC_SHA",
		    "SSL_DHE_DSS_WITH_AES_256_CBC_SHA",
		    "SSL_DHE_DSS_WITH_DES_CBC_SHA",
		    "SSL_DHE_RSA_WITH_AES_128_CBC_SHA",
		    "SSL_DHE_RSA_WITH_AES_256_CBC_SHA",
		    "SSL_DH_DSS_WITH_AES_128_CBC_SHA",
		    "SSL_DH_DSS_WITH_AES_256_CBC_SHA",
		    "SSL_DH_RSA_WITH_AES_128_CBC_SHA",
		    "SSL_DH_RSA_WITH_AES_256_CBC_SHA",
		    "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
		    "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
		    "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
		    "SSL_DH_anon_WITH_AES_128_CBC_SHA",
		    "SSL_DH_anon_WITH_AES_256_CBC_SHA",
		    "SSL_DH_anon_WITH_DES_CBC_SHA",
		    "SSL_DH_anon_WITH_RC4_128_MD5",
		    "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
		    "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
		    "SSL_RSA_WITH_AES_128_CBC_SHA",
		    "SSL_RSA_WITH_AES_256_CBC_SHA",
		    "SSL_RSA_WITH_DES_CBC_SHA",
		    "SSL_RSA_WITH_IDEA_CBC_SHA",
		    "SSL_RSA_WITH_NULL_MD5",
		    "SSL_RSA_WITH_NULL_SHA",
		    "SSL_RSA_WITH_RC4_128_MD5",
		    "SSL_RSA_WITH_RC4_128_SHA"
		}),
	    new TestGetSupportedCipherSuites(
		new String[] {
		    "SSL_RSA_WITH_RC4_128_MD5",
		    "SSL_RSA_WITH_UNKNOWN_CIPHER_SHA",
		    "SSL_UNKNOWN_KEY_EXCHANGE_WITH_RC4_128_SHA",
		    "SSL_RSA_WITH_DES_CBC_SHA"
		},
		new String[] {
		    "SSL_RSA_WITH_RC4_128_MD5",
		    "SSL_RSA_WITH_DES_CBC_SHA"
		}),
	    new TestGetSupportedCipherSuites(
		new String[] {
		    "SSL_RSA_WITH_RC4_128_MD5",
		    "SSL_RSA_WITH_UNKNOWN_CIPHER_SHA",
		    "SSL_RSA_WITH_AES_999_CBC_SHA",
		    "SSL_RSA_WITH_DES_CBC_SHA",
		    "SSL_UNKNOWN_KEY_EXCHANGE_WITH_RC4_128_SHA"
		},
		new String[] {
		    "SSL_RSA_WITH_RC4_128_MD5",
		    "SSL_RSA_WITH_DES_CBC_SHA"
		}),
	    new TestGetSupportedCipherSuites(
		new String[] {
		    "SSL_RSA_WITH_UNKNOWN_CIPHER_SHA",
		    "SSL_UNKNOWN_KEY_EXCHANGE_WITH_RC4_128_SHA"
		},
		new String[0])
	    };

	private final String[] suites;

	TestGetSupportedCipherSuites(String[] suites) {
	    this(suites, suites);
	}

	TestGetSupportedCipherSuites(String[] suites, String[] result) {
	    super(toString(suites), toString(result));
	    this.suites = suites;
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() {
	    return toString(
		(String[]) getSupportedCipherSuites.invoke(
		    null, new Object[] { suites }));
	}
    }

    static class TestRequestCipherSuites extends BasicTest {
	private static final LazyMethod getSupportedCipherSuites =
	    new LazyMethod("Utilities", "getSupportedCipherSuites",
			   new Class[] { });
	private static final LazyField supportedCipherSuitesInternal =
	    new LazyField("Utilities", "supportedCipherSuitesInternal");
	private static final LazyField requestedCipherSuites =
	    new LazyField("Utilities", "requestedCipherSuites");

	static final Test[] localtests = {
	    new TestRequestCipherSuites(new String[0], getDefaultSuites()),
	    new TestRequestCipherSuites(
		new String[] { "SSL_DHE_DSS_WITH_DES_CBC_SHA", "foo" },
		getDefaultSuites()),
	    new TestRequestCipherSuites(
		new String[] { "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
			       "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA" },
		new String[] { "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
			       "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA" })
	};

	private final String[] suites;

	TestRequestCipherSuites(String[] suites, String[] result) {
	    super(toString(suites), toString(result));
	    this.suites = suites;
	}

	private static String[] getDefaultSuites() {
	    String[] restore = (String[]) requestedCipherSuites.get(null);
	    try {
		supportedCipherSuitesInternal.set(null, null);
		requestedCipherSuites.set(null, null);
		return getSuites();
	    } finally {
		supportedCipherSuitesInternal.set(null, null);
		requestedCipherSuites.set(null, restore);
	    }
	}

	private static String[] getSuites() {
	    return (String[]) getSupportedCipherSuites.invoke(
		null, new Object[0]);
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() {
	    String[] restore = (String[]) requestedCipherSuites.get(null);
	    try {
		supportedCipherSuitesInternal.set(null, null);
		requestedCipherSuites.set(null, suites);
		return toString(getSuites());
	    } finally {
		supportedCipherSuitesInternal.set(null, null);
		requestedCipherSuites.set(null, restore);
	    }
	}
    }
}
