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
 * @summary Tests performance of RMI and JSSE.
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @build TestUtilities
 * @run main/othervm/policy=policy TestPerformance
 */

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.rmi.*;
import java.rmi.server.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.*;
import net.jini.core.constraint.*;
import net.jini.export.*;
import net.jini.jeri.*;
import net.jini.jeri.*;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.jeri.ssl.HttpsServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.*;
import net.jini.security.Security;

public class TestPerformance extends TestUtilities {

    static final Collection tests = new ArrayList();

    static final String[] quick = { "count", "1", "repeat", "1" };

    /** Default server endpoint class. */
    static final String defaultServerEndpoint =
	useHttps ? HttpsServerEndpoint.class.getName()
	    : SslServerEndpoint.class.getName();

    /** Provides methods for specifying properties with defaults. */
    abstract static class DefaultedPropertiesTest
	extends TestUtilities
	implements Test
    {
	private final Properties props;
	private final Properties defaultProps;

	DefaultedPropertiesTest(String[] props, String[] defaultProps) {
	    this.props = createProperties(props, System.getProperties());
	    this.defaultProps = createProperties(defaultProps, null);
	}

	public String name() {
	    return propertiesString();
	}

	/** Creates a property list from an array of strings. */
	private static Properties createProperties(String[] keysAndValues,
						   Properties defaults)
	{
	    Properties result = new Properties(defaults);
	    if (keysAndValues != null) {
		for (int i = 0; i < keysAndValues.length; i += 2) {
		    result.setProperty(keysAndValues[i], keysAndValues[i + 1]);
		}
	    }
	    return result;
	}

	String propertiesString() {
	    StringBuffer buf = new StringBuffer();
	    for (Iterator iter = defaultProps.keySet().iterator();
		 iter.hasNext(); )
	    {
		String key = (String) iter.next();
		String value = (String) getProperty(key);
		buf.append("\n  ").append(key).append(": ").append(value);
	    }
	    return buf.toString();
	}

	int getInt(String name) {
	    return Integer.parseInt(getProperty(name));
	}

	boolean getBoolean(String name) {
	    return "true".equalsIgnoreCase(getProperty(name));
	}

	String getProperty(String name) {
	    String val = props.getProperty(name);
	    if (val == null && defaultProps != null) {
		val = defaultProps.getProperty(name);
	    }
	    return val;
	}
    }

    /**
     * Run all tests, or just print results of a quick performance test if
     * printPerformance is true.
     */
    public static void main(String[] args) {
	test(tests);
    }

    /* -- Test RMI performance -- */

    static final String[] defaults = {
	"serverEndpoint", defaultServerEndpoint,
	"count", "1000",
	"repeat", "4",
	"encrypt", "true",
	"clientAuth", "true",
	"serverAuth", "true",
	"readOnlySubjects", "true",
	"numConstraints", "1",
	"strength", "weak",
	"multiplePrincipals", "false"
    };

    public static class RMI extends DefaultedPropertiesTest {

	private static final MethodConstraints[] allClientConstraints = {
	    new BasicMethodConstraints(
		requirements(ClientAuthentication.YES,
			     Confidentiality.YES,
			     ConfidentialityStrength.WEAK,
			     maxPrincipals(x500(clientRSA1)))),
	    new BasicMethodConstraints(
		requirements(ClientAuthentication.YES,
			     Confidentiality.YES,
			     ConfidentialityStrength.WEAK,
			     maxPrincipals(x500(clientRSA2)))),
	    new BasicMethodConstraints(
		requirements(ClientAuthentication.YES,
			     Confidentiality.YES,
			     ConfidentialityStrength.WEAK,
			     maxPrincipals(x500(clientDSA)))),
	    new BasicMethodConstraints(
		requirements(ClientAuthentication.YES,
			     Confidentiality.NO,
			     ConfidentialityStrength.WEAK,
			     maxPrincipals(x500(clientRSA1)))),
	    new BasicMethodConstraints(
		requirements(ClientAuthentication.YES,
			     Confidentiality.NO,
			     ConfidentialityStrength.WEAK,
			     maxPrincipals(x500(clientRSA2))))
	};

	private Exporter exporter;
	private Service server;

	final MethodConstraints[] clientConstraints;
	final MethodConstraints serverConstraints;

	interface Service extends Remote {
	    void call() throws RemoteException;
	}

	public static void main(String[] args) throws Exception {
	    test(new RMI(null));
	}

	RMI(String[] properties) {
	    super(properties, defaults);

	    String p = getProperty("encrypt");
	    InvocationConstraint encrypt =
		"true".equals(p) ? Confidentiality.YES :
		"false".equals(p) ? Confidentiality.NO :
		null;
	    p = getProperty("strength");
	    InvocationConstraint strength =
		"strong".equals(p) ? ConfidentialityStrength.STRONG :
		"weak".equals(p) ? ConfidentialityStrength.WEAK :
		null;
	    InvocationConstraint clientAuth = getBoolean("clientAuth")
		? ClientAuthentication.YES : ClientAuthentication.NO;
	    InvocationConstraint serverAuth = getBoolean("serverAuth")
		? ServerAuthentication.YES : ServerAuthentication.NO;

	    int numConstraints = getInt("numConstraints");
	    if (numConstraints == 1) {
		Collection requirements = new ArrayList();
		if (encrypt != null) {
		    requirements.add(encrypt);
		}
		requirements.add(serverAuth);
		if (strength != null) {
		    requirements.add(strength);
		}
		clientConstraints = new MethodConstraints[] {
		    new BasicMethodConstraints(
			new InvocationConstraints(requirements, null))
		};
	    } else {
		clientConstraints = new MethodConstraints[numConstraints];
		System.arraycopy(allClientConstraints, 0,
				 clientConstraints, 0, numConstraints);
	    }

	    Collection serverRequirements = new ArrayList();
	    if (encrypt != null) {
		serverRequirements.add(encrypt);
	    }
	    serverRequirements.add(clientAuth);
	    serverConstraints =
		new BasicMethodConstraints(
		    new InvocationConstraints(serverRequirements, null));
	}

	public Object run() throws Exception {
	    return client(server());
	}

	public void check(Object result) { }

	private Service server() {
	    final Subject subject = new WithSubject() { {
		addX500Principal("serverRSA", this.subject);
		if (getBoolean("multiplePrincipals")) {
		    addX500Principal("serverDSA", this.subject);
		}
	    } }.subject(getBoolean("readOnlySubjects"));
	    return (Service) Subject.doAs(
		subject,
		new PrivilegedAction() {
		    public Object run() {
			try {
			    exporter = new BasicJeriExporter(
				getEndpoint(subject),
				new BasicILFactory(serverConstraints, null),
				false, false, null);
			    class ServiceImpl implements Service {
				public void call() { }
			    }
			    server = new ServiceImpl();
			    return exporter.export(server);
			} catch (Exception e) {
			    throw unexpectedException(e);
			}
		    }
		});
	}

	private ServerEndpoint getEndpoint(Subject subject) {
	    try {
		Class serverEndpointClass = 
		    Class.forName(getProperty("serverEndpoint"));
		Method method = serverEndpointClass.getMethod(
		    "getInstance",
		    new Class[] {
			Subject.class, X500Principal[].class, String.class,
			int.class
		    });
		return (ServerEndpoint) method.invoke(
			null,
			new Object[] {
			    subject, null, "localhost", new Integer(0)
			});
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	private Long client(final Service server) {
	    return (Long) Subject.doAs(
		new WithSubject() { {
		    addX500Principal("clientRSA1", subject);
		    if (getBoolean("multiplePrincipals")) {
			addX500Principal("clientRSA2", subject);
			addX500Principal("clientDSA", subject);
		    }
		} }.subject(getBoolean("readOnlySubjects")),
		new PrivilegedAction() {
		    public Object run() {
			try {
			    Service[] allServers =
				new Service[clientConstraints.length];
			    RemoteMethodControl rserver =
				(RemoteMethodControl) server;
			    for (int i = 0; i < clientConstraints.length; i++)
			    {
				Security.verifyObjectTrust(
				    server, null,
				    Collections.singleton(
					clientConstraints[i]));
				allServers[i] = (Service)
				    rserver.setConstraints(
					clientConstraints[i]);
			    }
			    long result = Long.MAX_VALUE;
			    int repeat = getInt("repeat");
			    int count = getInt("count");
			    for (int i = 1; i <= repeat; i++) {
				long time = call(count, allServers);
				debugPrint(30, "  Time " + i + ": " + time);
				if (time < result) {
				    result = time;
				}
			    }
			    return new Long(result);
			} catch (Exception e) {
			    throw unexpectedException(e);
			} finally {
			    exporter.unexport(true);
			}
		    }
		});
	}

	private long call(int count, Service[] allServers)
	    throws RemoteException
	{
	    long start = System.currentTimeMillis();
	    int serverIndex = 0;
	    for (int i = count; --i >= 0; ) {
		allServers[serverIndex].call();
		serverIndex = (serverIndex + 1) % allServers.length;
	    }
	    long stop = System.currentTimeMillis();
	    return stop - start;
	}
    }

    /* --- Quick test that prints out the performance of a JSSE call -- */

    static {
	tests.add(new TestQuickRMIPerformance());
    }

    static class TestQuickRMIPerformance extends RMI {
	TestQuickRMIPerformance() {
	    super(null);
	}

	public void check(Object result) {
	    super.check(result);
	    long total = ((Long) result).longValue();
	    double time = (double) total / getInt("count");
	    System.out.println(
		"\n*** Time for RMI call using " +
		getProperty("serverEndpoint") + ": " + time + " ms");
	}
    }

    /** -- Test all RMI over JSSE performance -- */

    public static class RMIJSSE {

	static final String[][] propsList = {
	    { "encrypt", "false",
	      "clientAuth", "false" },
	    { "clientAuth", "false",
	      "serverAuth", "false" },
	    { "clientAuth", "false" },
	    { },
	    { "readOnlySubjects", "false" },
	    { "numConstraints", "2",
	      "multiplePrincipals", "true" },
	    { "numConstraints", "3",
	      "multiplePrincipals", "true" },
	    { "numConstraints", "3",
	      "multiplePrincipals", "true",
	      "readOnlySubjects", "false" },
	    { "numConstraints", "5",
	      "encrypt", "sometimes",
	      "multiplePrincipals", "true" },
	    { "numConstraints", "5",
	      "encrypt", "sometimes",
	      "multiplePrincipals", "true",
	      "readOnlySubjects", "false" }
	};

	static final Test[] localTests = new Test[propsList.length];
	static {
	    for (int i = propsList.length; --i >= 0; ) {
		localTests[i] = new RMI(propsList[i]);
	    }
	};

	public static void main(String[] args) {
	    test(localTests);
	}
    }

    /** -- Try each of the RMI over JSSE performance tests once -- */
    static {
	for (int i = RMIJSSE.propsList.length; --i >= 0; ) {
	    String[] props = RMIJSSE.propsList[i];
	    String[] quickProps = new String[props.length + 4];
	    System.arraycopy(quick, 0, quickProps, 0, 4);
	    System.arraycopy(props, 0, quickProps, 4, props.length);
	    tests.add(new RMI(quickProps));
	}
    }

    /** -- Test non-secure RMI performance -- */

    static {
	// XXX
	// tests.add(new NonSecureRMI(quick));
    }

    public static class NonSecureRMI extends DefaultedPropertiesTest {

	private static final String[] defaults = {
	    "count", "1000",
	    "repeat", "4",
	};

	private Exporter exporter;
	private Service server;

	interface Service extends Remote {
	    void call() throws RemoteException;
	}

	public static void main(String[] args) throws Exception {
	    test(new NonSecureRMI(null));
	}

	NonSecureRMI(String[] properties) {
	    super(properties, defaults);
	}

	public Object run() throws Exception {
	    return client(server());
	}

	public void check(Object result) { }

	private Service server() {
	    try {
		exporter = new BasicJeriExporter(
		    // XXX
		    // TcpServerEndpoint.getInstance(0),
		    null,
		    new BasicILFactory(), true, true, null);
		class ServiceImpl implements Service {
		    public void call() { }
		};
		server = new ServiceImpl();
		return (Service) exporter.export(server);
	    } catch (ExportException e) {
		throw unexpectedException(e);
	    }
	}

	private Long client(Service server) {
	    try {
		long result = Long.MAX_VALUE;
		int repeat = getInt("repeat");
		int count = getInt("count");
		for (int i = 1; i <= repeat; i++) {
		    long time = call(count, server);
		    debugPrint(30, "  Time " + i + ": " + time);
		    if (time < result) {
			result = time;
		    }
		}
		return new Long(result);
	    } catch (RemoteException e) {
		throw unexpectedException(e);
	    } finally {
		exporter.unexport(true);
	    }
	}

	private long call(int count, Service server) throws RemoteException {
	    long start = System.currentTimeMillis();
	    for (int i = count; --i >= 0; ) {
		server.call();
	    }
	    long stop = System.currentTimeMillis();
	    return stop - start;
	}
    }

    /** -- Test JSSE performance -- */

    static {
	tests.add(new JSSE(quick));
    }

    public static class JSSE extends DefaultedPropertiesTest {

	private static final String[] defaults = {
	    "requestSize", "20",
	    "replySize", "20",
	    "count", "1000",
	    "repeat", "4",
	    "suite", "SSL_RSA_WITH_RC4_128_MD5",
	    "clientAuth", "true"
	};
	private static final String keyStoreProp = "keyStore";
	private static final String keyStorePassword = "keypass";

	private final byte[] request;
	private final byte[] requestBuffer;
	private final byte[] reply;
	private final byte[] replyBuffer;
	private SSLServerSocket serverSocket;

	public static void main(String[] args) {
	    test(new JSSE(null));
	}

	JSSE(String[] properties) {
	    super(properties, defaults);
	    int requestSize = getInt("requestSize");
	    request = new byte[requestSize];
	    for (int i = 0; i < requestSize; i++) {
		request[i] = (byte) i;
	    }
	    requestBuffer = new byte[requestSize];
	    int replySize = getInt("replySize");
	    reply = new byte[replySize];
	    for (int i = replySize; --i >= 0; ) {
		reply[i] = (byte) i;
	    }
	    replyBuffer = new byte[replySize];
	}

	public Object run() throws Exception {
	    return client(server());
	}

	public void check(Object result) { }

	private int server() {
	    try {
		SSLServerSocketFactory factory =
		    getSSLContext().getServerSocketFactory();
		serverSocket = (SSLServerSocket) factory.createServerSocket(0);
		int port = serverSocket.getLocalPort();
		serverSocket.setNeedClientAuth(getBoolean("clientAuth"));
		String suite = getProperty("suite");
		serverSocket.setEnabledCipherSuites(new String[] { suite });
		new Thread() {
		    public void run() {
			try {
			    debugPrint(40, "Server accepting");
			    Socket socket = serverSocket.accept();
			    debugPrint(40, "Server accepted connection");
			    DataInputStream in =
				new DataInputStream(socket.getInputStream());
			    OutputStream out = socket.getOutputStream();
			    for (int i = 0; true; i++) {
				in.readFully(requestBuffer);
				out.write(reply);
				out.flush();
			    }
			} catch (EOFException e) {
			    debugPrint(40, "Server received EOF");
			} catch (Throwable t) {
			    throw unexpectedException(t);
			}
		    }
		}.start();
		return port;
	    } catch (Exception t) {
		throw unexpectedException(t);
	    }
	}

	private SSLContext getSSLContext() throws Exception {
	    KeyStore keyStore =
		KeyStore.getInstance(KeyStore.getDefaultType());
	    InputStream in = new FileInputStream(
		System.getProperty(keyStoreProp));
	    char[] password = keyStorePassword.toCharArray();
	    keyStore.load(in, password);
	    in.close();

	    KeyManagerFactory keyManagerFactory =
		KeyManagerFactory.getInstance("SunX509");
	    keyManagerFactory.init(keyStore, password);

	    TrustManagerFactory trustManagerFactory =
		TrustManagerFactory.getInstance("SunX509");
	    trustManagerFactory.init(keyStore);
	    SSLContext sslContext = SSLContext.getInstance("SSL");
	    sslContext.init(keyManagerFactory.getKeyManagers(),
			    trustManagerFactory.getTrustManagers(),
			    SecureRandom.getInstance("SHA1PRNG"));
	    return sslContext;
	}

	private Long client(int port) {
	    SSLSocket socket = null;
	    try {
		SSLSocketFactory factory = getSSLContext().getSocketFactory();
		socket = (SSLSocket) factory.createSocket("localhost", port);
		debugPrint(40, "Client connected");
		String suite = getProperty("suite");
		socket.setEnabledCipherSuites(new String[] { suite });
		int repeat = getInt("repeat");
		int count = getInt("count");
		long result = Long.MAX_VALUE;
		for (int i = 1; i <= repeat; i++) {
		    long time = call(count, socket);
		    debugPrint(30, "  Time " + i + ": " + time);
		    if (time < result) {
			result = time;
		    }
		}
		return new Long(result);
	    } catch (Exception e) {
		throw unexpectedException(e);
	    } finally {
		if (serverSocket != null) {
		    try {
			socket.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}

	private long call(int count, Socket socket) throws IOException {
	    DataInputStream in = new DataInputStream(socket.getInputStream());
	    DataOutputStream out =
		new DataOutputStream(socket.getOutputStream());
	    long start = System.currentTimeMillis();
	    for (int i = count; --i >= 0; ) {
		out.write(request);
		out.flush();
		in.readFully(replyBuffer);
	    }
	    long stop = System.currentTimeMillis();
	    return stop - start;
	}
    }

    /* -- Test performance of the SubjectCredentials class -- */

    static {
	tests.add(new TestSubjectCredentials(quick));
    }

    public static class TestSubjectCredentials
	extends DefaultedPropertiesTest
    {
	private static final String[] defaults = {
	    "count", "10000",
	    "repeat", "4"
	};

	private static final LazyMethod getCertificateChain =
	    new LazyMethod("SubjectCredentials", "getCertificateChain",
			   new Class[] {
			       Subject.class, X509Certificate.class });
	private static final LazyMethod getPrincipal =
	    new LazyMethod("SubjectCredentials", "getPrincipal",
			   new Class[] {
			       Subject.class, X509Certificate.class });
	private static final LazyMethod getPrivateCredential =
	    new LazyMethod("SubjectCredentials", "getPrivateCredential",
			   new Class[] {
			       Subject.class, X509Certificate.class });

	private final Subject subject;
	private final X509Certificate cert;

	static final Test[] localTests = {
	    new TestSubjectCredentials(null)
	};

	public static void main(String[] args) {
	    test(localTests);
	}

	TestSubjectCredentials(String[] properties) {
	    this(properties,
		 clientRSASubject,
		 (X509Certificate)
		 getCertificateChain("clientRSA2").getCertificates().get(0));
	}

	TestSubjectCredentials(String[] properties,
			       Subject subject,
			       X509Certificate cert)
	{
	    super(properties, defaults);
	    this.subject = subject;
	    this.cert = cert;
	}

	public Object run() {
	    int repeat = getInt("repeat");
	    int count = getInt("count");
	    long result = Long.MAX_VALUE;
	    for (int i = 1; i <= repeat; i++) {
		long time = checkCredentials(count);
		debugPrint(30, "  Time " + i + ": " + time);
		if (time < result) {
		    result = time;
		}		    
	    }
	    return new Long(result);
	}

	/**
	 * Performs the checks needed to insure that the Subject has the
	 * credentials for the certificate.
	 */
	private long checkCredentials(int count) {
	    Object[] args = new Object[] { subject, cert };
	    long start = System.currentTimeMillis();
	    while (--count >= 0) {
		getCertificateChain.invokeStatic(args);
		getPrincipal.invokeStatic(args);
		getPrivateCredential.invokeStatic(args);
	    }
	    long stop = System.currentTimeMillis();
	    return stop - start;
	}

	public void check(Object result) { }
    }	
}
