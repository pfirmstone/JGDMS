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
 * @summary Test the SslServerEndpoint and HttpsServerEndpoint classes.
 * 
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @build TestUtilities
 * @run main/othervm/policy=policy TestServerEndpoint
 */

import net.jini.core.constraint.*;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.*;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.security.*;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketPermission;
import java.security.*;
import java.util.*;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.security.auth.PrivateCredentialPermission;
import javax.security.auth.Subject;
import javax.security.auth.x500.*;

public class TestServerEndpoint extends TestUtilities {

    static final boolean OK = true;
    static final boolean THROWS = false;

    /** All tests */
    public static Collection tests = new ArrayList();

    /** Run all tests */
    public static void main(String[] args) {
	test(tests);
    }

    static class DeviousPrincipal implements Principal {
	public String getName() { return "Devious"; }
	public boolean equals(Object other) {
	    throw new Test.FailedException(
		"Called equals on devious principal");
	}
    }

    /** Implements name() */
    static abstract class LocalTest extends TestUtilities implements Test {
	final Subject subject;
	private final String name;

	LocalTest(Subject subject, String name) {
	    this.subject = subject;
	    this.name = "subject=" + subjectString(subject) +
		(name != null ? ("\n  " + name) : "");
	}

	public String name() {
	    return name;
	}
    }

    /* 
     * Test getInstance, getPort, getHost, getPrincipals, getSocketFactory, and
     * getServerSocketFactory
     */

    static {
	tests.add(TestConstructor.localtests);
    }

    static class TestConstructor extends BasicTest {

	static LazyMethod getHost = new LazyMethod(
	    useHttps ? "HttpsServerEndpoint" : "SslServerEndpoint",
	    "getHost", new Class[] { });

	static LazyMethod getPort = new LazyMethod(
	    useHttps ? "HttpsServerEndpoint" : "SslServerEndpoint",
	    "getPort", new Class[] { });

	static LazyMethod getPrincipals = new LazyMethod(
	    useHttps ? "HttpsServerEndpoint" : "SslServerEndpoint",
	    "getPrincipals", new Class[] { });

	static LazyMethod getSocketFactory = new LazyMethod(
	    useHttps ? "HttpsServerEndpoint" : "SslServerEndpoint",
	    "getSocketFactory", new Class[] { });

	static LazyMethod getServerSocketFactory = new LazyMethod(
	    useHttps ? "HttpsServerEndpoint" : "SslServerEndpoint",
	    "getServerSocketFactory", new Class[] { });

	static Subject expired = new WithSubject() { {
	    addX500Principal("serverRSA2expired", this.subject);
	} }.subject();

	static Object[] localtests = {

	    /* Port boundary cases */
	    new TestConstructor(null, -1, IllegalArgumentException.class),
	    new TestConstructor(null, -77, IllegalArgumentException.class),
	    new TestConstructor(null, 0, createServerEndpoint(0)),
	    new TestConstructor(null, 65535, createServerEndpoint(65535)),
	    new TestConstructor(null, 65536, IllegalArgumentException.class),
	    new TestConstructor(null, 100000, IllegalArgumentException.class),

	    /* Credentials and permissions */
	    new TestConstructor(new Subject(), 11, createServerEndpoint(11)),
	    new TestConstructor(
		new Subject(true,
			    Collections.singleton(x500(serverDSA)),
			    Collections.EMPTY_SET,
			    Collections.EMPTY_SET),
		21, createServerEndpoint(21)),
	    new TestConstructor(
		new Subject(
		    true,
		    Collections.singleton(x500(serverDSA)),
		    Collections.singleton(getCertificateChain("serverDSA")),
		    Collections.EMPTY_SET),
		41, createServerEndpoint(41)),
	    new TestConstructor(
		new Subject(
		    true,
		    Collections.singleton(x500(serverDSA)),
		    Collections.singleton(getCertificateChain("serverDSA")),
		    Collections.singleton(getPrivateKey("clientDSA"))),
		31, createServerEndpoint(31)),
	    new TestConstructor(
		new Subject(
		    true,
		    Collections.singleton(x500(clientDSA)),
		    Collections.singleton(getCertificateChain("clientDSA")),
		    Collections.singleton(getPrivateKey("clientDSA"))),
		51, createServerEndpoint(51)),
	    new TestConstructor(
		TestUtilities.serverSubject,
		61,
		createServerEndpoint(TestUtilities.serverSubject,
				     serverX500Principals, null, 61)),
	    new TestConstructor(
		expired,
		71,
		createServerEndpoint(
		    expired,
		    new X500Principal[] { x500("CN=serverRSA2") },
		    null, 71)),
	    new TestConstructor(TestUtilities.serverSubject, 81,
				createServerEndpoint(81))
	    {
		Object doAs(PrivilegedAction action) {
		    return Subject.doAsPrivileged(
			currentSubject,
			action,
			withPermissions(AuthenticationPermission.class,
					new AuthenticationPermission[0]));
		}
	    },
	    new TestConstructor(
		TestUtilities.serverSubject,
		91,
		createServerEndpoint(TestUtilities.serverSubject,
				     serverX500Principals, null, 91))
	    {
		Object doAs(PrivilegedAction action) {
		    return Subject.doAsPrivileged(
			currentSubject,
			action,
			withPermissions(PrivateCredentialPermission.class,
					new PrivateCredentialPermission[0]));
		}
	    },

	    /* Socket permission */
	    new TestConstructor(null, 151, createServerEndpoint(151)) {
		Object doAs(PrivilegedAction action) {
		    return Subject.doAsPrivileged(
			currentSubject,
			action,
			withPermissions(SocketPermission.class,
					new SocketPermission[0]));
		}
	    },

	    /* Server host */
	    new TestConstructor(null, null, 501,
				createServerEndpoint(null, 501)),
	    new TestConstructor(null, "foo", 511,
				createServerEndpoint("foo", 511)),
	    new TestConstructor(null, "127.0.0.1", 521,
				createServerEndpoint("127.0.0.1", 521)),

	    /* Socket factories */
	    new TestConstructor(null, null, 1001,
				SocketFactory.getDefault(),
				null,
				createServerEndpoint(
				    null, 1001,
				    SocketFactory.getDefault(),
				    null)),
	    new TestConstructor(null, null, 1011,
				null,
				ServerSocketFactory.getDefault(),
				createServerEndpoint(
				    null, 1011, null,
				    ServerSocketFactory.getDefault())),
	    new TestConstructor(null, null, 1021,
				SocketFactory.getDefault(),
				ServerSocketFactory.getDefault(),
				createServerEndpoint(
				    null, 1021,
				    SocketFactory.getDefault(),
				    ServerSocketFactory.getDefault())),

	    /* Subject and principals */
	    new TestConstructor(null, TestUtilities.serverSubject,
				null, null, 1501,
				createServerEndpoint(
				    TestUtilities.serverSubject,
				    serverX500Principals,
				    null, 1501)),
	    new TestConstructor(null, TestUtilities.serverSubject,
				serverX500Principals, null, 1511,
				createServerEndpoint(
				    TestUtilities.serverSubject,
				    serverX500Principals,
				    null, 1511)),
	    new TestConstructor(null, TestUtilities.serverSubject,
				new X500Principal[] { x500("CN=Foo") },
				null, 1521,
				createServerEndpoint(
				    TestUtilities.serverSubject,
				    new X500Principal[] { x500("CN=Foo") },
				    null, 1521)),
	    new TestConstructor(serverRSASubject, TestUtilities.serverSubject,
				null, null, 1531,
				createServerEndpoint(
				    TestUtilities.serverSubject,
				    serverX500Principals,
				    null, 1531))
	};

	final Subject currentSubject;
	private final Subject serverSubject;
	private final X500Principal[] serverPrincipals;
	private final String serverHost;
	private final int port;
	private final SocketFactory socketFactory;
	private final ServerSocketFactory serverSocketFactory;

	TestConstructor(Subject currentSubject, int port, Object shouldBe) {
	    this(currentSubject, null, null, null, port, null, null, shouldBe);
	}

	TestConstructor(Subject currentSubject,
			String serverHost,
			int port,
			Object shouldBe)
	{
	    this(currentSubject, null, null, serverHost, port, null, null,
		 shouldBe);
	}

	TestConstructor(Subject currentSubject,
			String serverHost,
			int port,
			SocketFactory socketFactory,
			ServerSocketFactory serverSocketFactory,
			Object shouldBe) {
	    this(currentSubject, null, null, serverHost, port, socketFactory,
		 serverSocketFactory, shouldBe);
	}

	TestConstructor(Subject currentSubject,
			Subject serverSubject,
			X500Principal[] serverPrincipals,
			String serverHost,
			int port,
			Object shouldBe) {
	    this(currentSubject, serverSubject, serverPrincipals, serverHost,
		 port, null, null, shouldBe);
	}

	TestConstructor(Subject currentSubject,
			Subject serverSubject,
			X500Principal[] serverPrincipals,
			String serverHost,
			int port,
			SocketFactory socketFactory,
			ServerSocketFactory serverSocketFactory,
			Object shouldBe)
	{
	    super("\n  currentSubject = " + currentSubject +
		  "\n  serverSubject = " + serverSubject +
		  "\n  serverPrincipals = " + toString(serverPrincipals) +
		  "\n  serverHost = " + serverHost +
		  "\n  port = " + port +
		  "\n  socketFactory = " + socketFactory +
		  "\n  serverSocketFactory = " + serverSocketFactory,
		  shouldBe);
	    this.currentSubject = currentSubject;
	    this.serverSubject = serverSubject;
	    this.serverPrincipals = serverPrincipals;
	    this.serverHost = serverHost;
	    this.port = port;
	    this.socketFactory = socketFactory;
	    this.serverSocketFactory = serverSocketFactory;
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() throws Exception {
	    try {
		return doAs(
		    new PrivilegedAction() {
			public Object run() {
			    return createServerEndpoint(
				serverSubject,
				serverPrincipals,
				serverHost,
				port,
				socketFactory,
				serverSocketFactory);
			}
		    });
	    } catch (Exception e) {
		return e.getClass();
	    }
	}

	private Object createPort() {
	    try {
		return doAs(
		    new PrivilegedAction() {
			public Object run() {
			    return createServerEndpoint(port);
			}
		    });
	    } catch (Exception e) {
		return e.getClass();
	    }
	}

	private Object createHostPort() {
	    try {
		return doAs(
		    new PrivilegedAction() {
			public Object run() {
			    return createServerEndpoint(serverHost, port);
			}
		    });
	    } catch (Exception e) {
		return e.getClass();
	    }
	}

	private Object createHostPortFactories() {
	    try {
		return doAs(
		    new PrivilegedAction() {
			public Object run() {
			    return createServerEndpoint(
				serverHost, port, socketFactory,
				serverSocketFactory);
			}
		    });
	    } catch (Exception e) {
		return e.getClass();
	    }
	}

	private Object createSubjectHostPort() {
	    try {
		return doAs(
		    new PrivilegedAction() {
			public Object run() {
			    return createServerEndpoint(
				serverSubject, serverPrincipals,
				serverHost, port);
			}
		    });
	    } catch (Exception e) {
		return e.getClass();
	    }
	}

	Object doAs(PrivilegedAction action) {
	    return Subject.doAs(currentSubject, action);
	}

	private String getHost(ServerEndpoint serverEndpoint) {
	    return (String) getHost.invoke(serverEndpoint, new Object[0]);
	}

	private int getPort(ServerEndpoint serverEndpoint) {
	    return ((Integer) getPort.invoke(
		serverEndpoint, new Object[0])).intValue();
	}

	private Set getPrincipals(ServerEndpoint serverEndpoint) {
	    return (Set) getPrincipals.invoke(serverEndpoint, new Object[0]);
	}

	private SocketFactory getSocketFactory(ServerEndpoint serverEndpoint) {
	    return (SocketFactory) getSocketFactory.invoke(
		serverEndpoint, new Object[0]);
	}

	private ServerSocketFactory getServerSocketFactory(
	    ServerEndpoint serverEndpoint)
	{
	    return (ServerSocketFactory) getServerSocketFactory.invoke(
		serverEndpoint, new Object[0]);
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    Object compareTo = getCompareTo();
	    if (serverSubject == null
		&& serverPrincipals == null
		&& serverHost == null
		&& socketFactory == null
		&& serverSocketFactory == null)
	    {
		super.check(createPort());
	    }
	    if (serverSubject == null
		&& serverPrincipals == null
		&& socketFactory == null
		&& serverSocketFactory == null)
	    {
		super.check(createHostPort());
	    }
	    if (serverSubject == null
		&& serverPrincipals == null)
	    {
		super.check(createHostPortFactories());
	    }
	    if (socketFactory == null
		&& serverSocketFactory == null)
	    {
		super.check(createSubjectHostPort());
	    }
	    if (result instanceof ServerEndpoint) {
		ServerEndpoint serverEndpoint = (ServerEndpoint) result;
		ServerEndpoint compareServerEndpoint =
		    (ServerEndpoint) compareTo;

		if (!safeEquals(getHost(serverEndpoint), serverHost))
		{
		    throw new FailedException("Wrong host");
		}
		if (port != getPort(serverEndpoint)) {
		    throw new FailedException("Wrong port");
		}
		if (!safeEquals(getPrincipals(serverEndpoint),
				getPrincipals(compareServerEndpoint)))
		{
		    throw new FailedException("Wrong principals");
		}
		if (socketFactory != getSocketFactory(serverEndpoint)) {
		    throw new FailedException("Wrong socket factory");
		}
		if (serverSocketFactory
		    != getServerSocketFactory(serverEndpoint))
		{
		    throw new FailedException("Wrong server socket factory");
		}
	    }
	}
    }

     /* -- Test ServerEndpoint.equals() -- */

     static {
 	tests.add(TestEquals.localtests);
     }

     static class TestEquals extends LocalTest {
 	static final boolean EQUALS = true;
 	static final boolean NOT_EQUALS = false;

 	static Test[] localtests = {

 	    /* Compare client subject with null */
 	    new TestEquals(serverSubject, null, "server subject, with null",
 			   NOT_EQUALS),

 	    /* Compare with non-endpoint */
 	    new TestEquals(serverSubject, new Integer(33),
 			   "server subject with Integer", NOT_EQUALS),

 	    /* Compare null subjects */
 	    new TestEquals(null,
 			   createServerEndpoint(null),
 			   "null subjects", EQUALS),

 	    /* Different subjects */
 	    new TestEquals(serverRSASubject,
 			   createServerEndpoint(serverSubject),
 			   "different subjects", NOT_EQUALS),

 	    /* Different server hosts */
 	    new TestEquals(serverSubject, "localhost", 0,
 			   createServerEndpoint(
 			       serverSubject, null, "0.0.0.0", 0),
 			   "different server hosts", NOT_EQUALS),
	      
 	    /* Different ports */
 	    new TestEquals(serverSubject, "localhost", 0,
 			   createServerEndpoint(
 			       serverSubject, null, "localhost", 1234),
 			   "different ports", NOT_EQUALS),

 	    /* Make sure equals() doesn't call Subject.equals() */
	    new TestEquals(
		new WithSubject() { {
 		    this.subject.getPrincipals().add(new DeviousPrincipal());
 		    addX500Principal("serverRSA", this.subject);
 		} }.subject(),
		createServerEndpoint(
		    new WithSubject() { {
			this.subject.getPrincipals().add(
			    new DeviousPrincipal());
			addX500Principal("serverRSA", this.subject);
		    } }.subject()),
		"devious subject", NOT_EQUALS)
 	};

 	Object other;
 	boolean compareTo;
 	String serverHost;
 	int port;
 	ServerEndpoint thisEndpoint;

 	TestEquals(Subject subject,
 		   Object other,
 		   String desc,
 		   boolean compareTo)
 	{
 	    this(subject, "localhost", 0, other, desc, compareTo);
 	}

 	TestEquals(Subject subject,
 		   String serverHost,
 		   int port,
 		   Object other,
 		   String desc,
 		   boolean compareTo)
 	{
 	    super(subject, desc);
 	    this.serverHost = serverHost;
 	    this.port = port;
 	    this.other = other;
 	    this.compareTo = compareTo;
 	}

 	public Object run() throws UnsupportedConstraintException {
 	    thisEndpoint = createServerEndpoint(
 		subject, null, serverHost, port);
 	    return new Boolean(thisEndpoint.equals(other));
 	}

 	public void check(Object result) {
 	    if (compareTo != ((Boolean) result).booleanValue()) {
 		throw new FailedException("Result should be " + compareTo);
 	    }
 	    int thisHash = thisEndpoint.hashCode();
 	    int otherHash = (other == null) ? 0 : other.hashCode();
 	    if (compareTo && (thisHash != otherHash)) {
 		throw new FailedException(
 		    "Equals() returned true but hash codes were different: " +
 		    "thisEndpoint.hashCode() = " + thisHash +
 		    ", other.hashCode = " + otherHash);
 	    }
 	}
     }

     /* -- Test ServerEndpoint.checkConstraints() -- */

     static {
 	tests.add(TestSupportsConstraints.localtests);
     }

     static class TestSupportsConstraints extends LocalTest {
 	static final InvocationConstraints NOT_SUPPORTED = null;
 	static Test[] localtests = {
 	    new TestSupportsConstraints(
 		null, requirements(Integrity.YES),
		requirements(Integrity.YES)),
 	    new TestSupportsConstraints(
 		null, requirements(ClientAuthentication.YES),
		NOT_SUPPORTED),
 	    new TestSupportsConstraints(
 		serverSubject, requirements(ClientAuthentication.YES),
 		InvocationConstraints.EMPTY),
 	    new TestSupportsConstraints(
 		serverSubject, requirements(Integrity.NO), NOT_SUPPORTED),
 	    new TestSupportsConstraints(
 		serverSubject,
 		requirements(ClientAuthentication.NO, Integrity.NO),
 		NOT_SUPPORTED),
 	    new TestSupportsConstraints(
 		serverSubject,
 		constraints(ClientAuthentication.NO, Integrity.NO),
 		InvocationConstraints.EMPTY),
 	    new TestSupportsConstraints(serverSubject, null, NOT_SUPPORTED) {
 		public Object run() throws UnsupportedConstraintException {
 		    try {
 			return super.run();
 		    } catch (NullPointerException e) {
 			return e;
 		    }
 		}
 		public void check(Object result) {
 		    if (!(result instanceof NullPointerException)) {
 			throw new FailedException(
 			    "Should have thrown NullPointerException");
 		    }
 		}
 	    },

 	    /* Don't support multiple server principals */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("clientRSA1", this.subject);
 		    addX500Principal("clientRSA2", this.subject);
 		} }.subject(),
 		requirements(ServerAuthentication.YES,
 			     serverPrincipals(x500(clientRSA1)),
 			     serverPrincipals(x500(clientRSA2))),
 		NOT_SUPPORTED),

 	    /* Don't support multiple client principals */
 	    new TestSupportsConstraints(
 		serverSubject,
 		requirements(
 		    ClientAuthentication.YES,
 		    minPrincipals(x500(clientRSA1), x500(clientRSA2))),
 		NOT_SUPPORTED),

 	    /* Don't support conflicting min and max principals */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("clientRSA1", this.subject);
 		    addX500Principal("clientRSA2", this.subject);
 		} }.subject(),
 		requirements(ClientAuthentication.YES,
 			     minPrincipals(x500(clientRSA1)),
 			     maxPrincipals(x500(clientRSA2))),
 		NOT_SUPPORTED),

 	    /* Don't support conflicting STRONG and WEAK constraints */
 	    new TestSupportsConstraints(
 		serverSubject,
 		requirements(Confidentiality.YES,
 			     ConfidentialityStrength.STRONG,
 			     ConfidentialityStrength.WEAK),
 		NOT_SUPPORTED),

 	    /*
 	     * Don't support conflicting STRONG and WEAK constraints made
 	     * necessary by suite constraints.  Note that Confidentiality.YES
 	     * is implied by requiring server authentication and having only
 	     * DSA principals, since DSA suites all perform encryption.
 	     */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("serverDSA", this.subject);
 		} }.subject(),
 		requirements(
 		    ServerAuthentication.YES,
 		    ConfidentialityStrength.STRONG,
 		    ConfidentialityStrength.WEAK),
 		NOT_SUPPORTED),

 	    /* Support single server principal */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("clientRSA1", this.subject);
 		} }.subject(),
 		requirements(ServerAuthentication.YES,
 			     serverPrincipals(x500(clientRSA1))),
 		InvocationConstraints.EMPTY),

 	    /* Support server principal alternatives */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("clientRSA1", this.subject);
 		    addX500Principal("clientRSA2", this.subject);
 		} }.subject(),
 		requirements(ServerAuthentication.YES,
 			     alternatives(
 				 serverPrincipals(x500(clientRSA1)),
 				 serverPrincipals(x500(clientRSA2)))),
 		InvocationConstraints.EMPTY),

 	    /*
 	     * Support server principal even if server subject contains more
 	     * than one
 	     */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("clientRSA1", this.subject);
 		    addX500Principal("clientRSA2", this.subject);
 		} }.subject(),
 		requirements(ServerAuthentication.YES,
 			     serverPrincipals(x500(clientRSA1))),
 		InvocationConstraints.EMPTY),

 	    /* Don't support destroyed server credentials */
 	    new TestSupportsConstraints(
 		new WithSubject() { {
 		    addX500Principal("serverRSA", this.subject);
 		    destroyPrivateCredentials(this.subject);
 		} }.subject(),
 		requirements(ServerAuthentication.YES),
 		NOT_SUPPORTED)
 	};

 	InvocationConstraints constraints;
 	InvocationConstraints compareTo;

 	public static void main(String[] args) {
 	    test(localtests);
 	}

 	TestSupportsConstraints(Subject subject,
 				InvocationConstraints constraints,
 				InvocationConstraints compareTo)
 	{
 	    super(subject, "constraints=" + constraints);
 	    this.constraints = constraints;
 	    this.compareTo = compareTo;
 	}

 	public Object run() throws UnsupportedConstraintException {
 	    ServerEndpoint endpoint = createServerEndpoint(subject);
	    try {
		return endpoint.checkConstraints(constraints);
	    } catch (Exception e) {
		return e;
	    }
 	}

 	public void check(Object result) {
	    if (compareTo == null) {
		if (result == null ||
		    result.getClass() != UnsupportedConstraintException.class)
		{
		    throw new FailedException(
			"Should be an UnsupportedConstraintException");
		}
	    } else {
		if (!compareTo.equals(result)) {
		    throw new FailedException("Should be " + compareTo);
		}
	    }
	}
     }

     /* -- Test ServerEndpoint.enumerateListenEndpoints() -- */

     static {
 	tests.add(TestEnumerateListenEndpoints.localtests);
     }

     static class TestEnumerateListenEndpoints extends LocalTest {
 	static Test[] localtests = {
 	    new TestEnumerateListenEndpoints(null, OK),
 	    new TestEnumerateListenEndpoints(
		null, new AuthenticationPermission[] { }, OK),
 	    new TestEnumerateListenEndpoints(serverSubject, OK),
 	    new TestEnumerateListenEndpoints(
 		serverSubject,
 		new AuthenticationPermission[] {
 		    new AuthenticationPermission(
 			Collections.singleton(x500(serverDSA)), null,
 			"accept") },
 		THROWS)
 	};

 	private final boolean ok;
 	private final AuthenticationPermission[] permissions;

 	public static void main(String[] args) {
 	    test(localtests);
 	}

 	TestEnumerateListenEndpoints(Subject subject, boolean ok) {
 	    this(subject, null, ok);
 	}

 	TestEnumerateListenEndpoints(Subject subject,
				     AuthenticationPermission[] permissions,
				     boolean ok)
	 {
 	    super(subject,
 		  (permissions == null ? ""
 		   : "permissions=" + TestUtilities.toString(permissions)));
 	    this.ok = ok;
 	    this.permissions = permissions;
 	}

 	public Object run() throws IOException {
	    final ServerEndpoint serverEndpoint =
		createServerEndpoint(subject);

 	    if (permissions == null) {
 		return doEnumerate(serverEndpoint);
 	    }
 	    try {
 		return AccessController.doPrivileged(
 		    new PrivilegedExceptionAction() {
 			public Object run() throws IOException {
 			    return doEnumerate(serverEndpoint);
 			}
 		    },
 		    TestUtilities.withAuthenticationPermissions(permissions));
 	    } catch (PrivilegedActionException e) {
 		Throwable t = e.getCause();
 		if (t instanceof IOException) {
 		    throw (IOException) t;
 		} else {
 		    throw unexpectedException(t);
 		}
 	    }
 	}

 	protected Object doEnumerate(ServerEndpoint serverEndpoint)
	    throws IOException
	 {
 	    try {
 		return serverEndpoint.enumerateListenEndpoints(
 		    new ListenContext() {
			public ListenCookie addListenEndpoint(
			    ListenEndpoint listenEndpoint)
			    throws IOException
			{
			    ListenHandle handle = listenEndpoint.listen(
				new RequestDispatcher() {
				    public void dispatch(InboundRequest ir) {
				    }
				});
			    ListenCookie cookie = handle.getCookie();
			    handle.close();
			    return cookie;
			}
		    });
 	    } catch (Exception e) {
 		return e;
 	    }
 	}

 	public void check(Object result) {
	    Class shouldBe = ok ? Endpoint.class : SecurityException.class;
	    if (!shouldBe.isInstance(result)) {
		throw new FailedException(
		    "Should be instance of " + shouldBe.getName());
	    }
 	}
     }

    /* -- Test ListenEndpoint.equals -- */

    static {
	 tests.add(TestListenEndpointEquals.localtests);
    }

    public static class TestListenEndpointEquals extends BasicTest {
	static class I {
	    int i;
	    Object item;
	    I(int i, Object item) { this.i = i; this.item = item; }
	}

	private static I[] items = {
	    new I(-1, null),
	    new I(-1, new Integer(3)),
	    new I(-1,
		  new ListenEndpoint() {
		      public void checkPermissions() { }
		      public ListenHandle listen(RequestDispatcher rd) {
			  return null;
		      }
		  }),
	    new I(1, createListenEndpoint(null, null, "foo", 0)),
	    new I(1, createListenEndpoint(null, null, "bar", 0)),
	    new I(1,
		  createListenEndpoint(
		      new Subject(true,
				  Collections.EMPTY_SET,
				  Collections.EMPTY_SET,
				  Collections.EMPTY_SET),
		      null, "baz", 0)),
	    new I(2,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject.getPrincipals().add(
			      new DeviousPrincipal());
			  addX500Principal("serverRSA", this.subject);
		      } }.subject,
		      null, "baz", 0)),
	    new I(3,
		  createListenEndpoint(
		      serverSubject, new X500Principal[] { x500(serverRSA) },
		      "baz", 0)),
	    new I(4, createListenEndpoint(null, null, "foo", 1)),
	    new I(5, createListenEndpoint(null, null, "foo", 33)),
	    new I(5, createListenEndpoint(null, null, "bar", 33)),
	    new I(5, createListenEndpoint(
		      null, null, "foo", 33,
		      createSocketFactory("a"), null)),
	    new I(5, createListenEndpoint(
		      null, null, "foo", 33,
		      createSocketFactory("b"), null)),
	    new I(6, createListenEndpoint(
		      null, null, "foo", 33,
		      null, createServerSocketFactory(44))),
	    new I(7, createListenEndpoint(
		      null, null, "foo", 33,
		      createSocketFactory("c"),
		      createServerSocketFactory(55)))
	};

	static Collection localtests = new ArrayList();

	static {
	    for (int i = items.length; --i >= 0; ) {
		for (int j = items.length; --j >= 0; ) {
		    Object x = items[i].item;
		    Object y = items[j].item;
		    String name = "\n  x = " + x + "\n  y = " + y;
		    if (x instanceof ListenEndpointData) {
			x = ((ListenEndpointData) x).create();
		    }
		    if (y instanceof ListenEndpointData) {
			y = ((ListenEndpointData) y).create();
		    }
		    boolean result = 
			i == j || items[i].i > 0 && items[i].i == items[j].i;
		    localtests.add(
			new TestListenEndpointEquals(name, x, y, result));
		    if (x instanceof Serializable) {
			try {
			    x = serialized(x);
			} catch (IOException e) {
			    throw unexpectedException(e);
			}
			localtests.add(
			    new TestListenEndpointEquals(
				"Serialized " + name, x, y, result));
		    }
		}
	    }
	}

	private final Object x;
	private final Object y;

	TestListenEndpointEquals(String name,
				 Object x,
				 Object y,
				 boolean result)
	{
	    super(name, Boolean.valueOf(result));
	    this.x = x;
	    this.y = y;
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() throws Exception {
	    return Boolean.valueOf(x == null ? y == null : x.equals(y));
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    super.check(
		Boolean.valueOf(y == null ? x == null : y.equals(x)));
	    if (Boolean.TRUE.equals(result)) {
		int h1 = x == null ? 0 : x.hashCode();
		int h2 = y == null ? 0 : y.hashCode();
		if (h1 != h2) {
		    throw new FailedException("Hash codes differ");
		}
	    }
	}

	static ListenEndpointData createListenEndpoint(
	    Subject subject, X500Principal[] principals, String host, int port)
	{
	    return createListenEndpoint(
		subject, principals, host, port, null, null);
	}

	static ListenEndpointData createListenEndpoint(
	    Subject subject,
	    X500Principal[] principals,
	    String host,
	    int port,
	    SocketFactory socketFactory,
	    ServerSocketFactory serverSocketFactory)
	{
	    return new ListenEndpointData(
		subject, principals, host, port, socketFactory,
		serverSocketFactory);
	}

	static class ListenEndpointData {
	    private final Subject subject;
	    private final X500Principal[] principals;
	    private final String host;
	    private final int port;
	    private final SocketFactory socketFactory;
	    private final ServerSocketFactory serverSocketFactory;

	    ListenEndpointData(Subject subject,
			       X500Principal[] principals,
			       String host,
			       int port,
			       SocketFactory socketFactory,
			       ServerSocketFactory serverSocketFactory)
	    {
		this.subject = subject;
		this.principals = principals;
		this.host = host;
		this.port = port;
		this.socketFactory = socketFactory;
		this.serverSocketFactory = serverSocketFactory;
	    }		

	    ListenEndpoint create() {
		ServerEndpoint serverEndpoint =
		    TestUtilities.createServerEndpoint(
			subject, principals, host, port, socketFactory,
			serverSocketFactory);
		class LC implements ListenContext {
		    ListenEndpoint listenEndpoint;
		    public ListenCookie addListenEndpoint(
			ListenEndpoint listenEndpoint)
			throws IOException
		    {
			this.listenEndpoint = listenEndpoint;
			throw new IOException();
		    }
		}
		LC lc = new LC();
		try {
		    serverEndpoint.enumerateListenEndpoints(lc);
		} catch (IOException e) {
		}
		return lc.listenEndpoint;
	    }

	    public String toString() {
		return
		    "ListenEndpoint[" +
		    "subject = " + subject +
		    ", principals = " + TestUtilities.toString(principals) +
		    ", host = " + host +
		    ", port = " + port +
		    ", socketFactory = " + socketFactory +
		    ", serverSocketFactory = " + serverSocketFactory +
		    "]";
	    }
	}

	static SocketFactory createSocketFactory(String host) {
	    return new SF(host);
	}

	static class SF extends SocketFactory {
	    private final String host;
	    SF(String host) {
		this.host = host;
	    }
	    public Socket createSocket(String host, int port) {
		return null;
	    }
	    public Socket createSocket(InetAddress address, int port) {
		return null;
	    }
	    public Socket createSocket(String host,
				       int port1,
				       InetAddress address,
				       int port2)
	    {
		return null;
	    }
	    public Socket createSocket(InetAddress address1,
				       int port1,
				       InetAddress address2,
				       int port2)
	    {
		return null;
	    }
	    public String toString() {
		return "SF[" + host + "]";
	    }
	    public boolean equals(Object o) {
		return o instanceof SF && host.equals(((SF) o).host);
	    }
	    public int hashCode() {
		return host.hashCode();
	    }
	}

	static ServerSocketFactory createServerSocketFactory(int port) {
	    return new SSF(port);
	}

	static class SSF extends ServerSocketFactory {
	    private final int port;
	    SSF(int port) {
		this.port = port;
	    }
	    public ServerSocket createServerSocket() {
		return null;
	    }
	    public ServerSocket createServerSocket(int port) {
		return null;
	    }
	    public ServerSocket createServerSocket(int port1, int port2) {
		return null;
	    }
	    public ServerSocket createServerSocket(int port1,
						   int port2,
						   InetAddress address)
	    {
		return null;
	    }
	    public String toString() {
		return "SSF[" + port + "]";
	    }
	    public boolean equals(Object o) {
		return o instanceof SSF && port == ((SSF) o).port;
	    }
	    public int hashCode() {
		return port;
	    }
	}
    }

//      /* -- Test ServerEndpoint.checkPermissions() -- */

//      static {
//  	tests.add(TestCheckPermissions.localtests);
//      }

//      static class TestCheckPermissions extends LocalTest {

//  	static Test[] localtests = {
//  	    new TestCheckPermissions(null, null, THROWS),
//  	    new TestCheckPermissions(
//  		null, 
//  		new SocketPermission[] {
//  		    new SocketPermission("localhost", "listen")
//  		},
//  		OK)
//  	};

//  	private final SocketPermission[] permissions;
//  	private final boolean shouldBe;

//  	TestCheckPermissions(Subject subject,
//  			     SocketPermission[] permissions,
//  			     boolean shouldBe)
//  	{
//  	    super(subject, toString(permissions));
//  	    this.permissions = permissions;
//  	    this.shouldBe = shouldBe;
//  	}

//  	public Object run() throws UnsupportedConstraintException {
//  	    final ServerEndpoint endpoint =
//  		createServerEndpoint(false, subject);
//  	    try {
//  		return AccessController.doPrivileged(
//  		    new PrivilegedAction() {
//  			public Object run() {
//  			    endpoint.checkPermissions();
//  			    return null;
//  			}
//  		    },
//  		    withPermissions(SocketPermission.class, permissions));
//  	    } catch (SecurityException e) {
//  		return e;
//  	    }
//  	}

//  	public void check(Object result) {
//  	    if (shouldBe == OK) {
//  		if (result != null) {
//  		    throw new FailedException("Expected no exception");
//  		}
//  	    } else {
//  		if (result == null) {
//  		    throw new FailedException("Expected exception");
//  		}
//  	    }
//  	}
//      }
}
