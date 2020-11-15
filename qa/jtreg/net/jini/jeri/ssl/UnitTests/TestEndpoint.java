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
 * @summary Tests the SslEndpoint and HttpsEndpoint classes.
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test TestUtilities
 * @run main/othervm/policy=policy TestEndpoint
 */

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.net.ServerSocketFactory;
import javax.security.auth.AuthPermission;
import javax.security.auth.PrivateCredentialPermission;
import javax.security.auth.Subject;
import javax.security.auth.x500.*;
import net.jini.core.constraint.*;
import net.jini.io.*;
import net.jini.jeri.*;
import net.jini.jeri.ServerEndpoint.*;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.jeri.ssl.HttpsEndpoint;
import net.jini.jeri.ssl.SslEndpoint;

public class TestEndpoint extends TestUtilities {

    /** All tests */
    public static final Collection tests = new ArrayList();

    /** Runs all tests */
    public static void main(String[] args) {
	test(tests);
    }

    /* -- Test getInstance, getPort, and getHost -- */

    static {
	tests.add(TestGetInstance.localtests);
    }

    public static class TestGetInstance implements Test {
	static final Test[] localtests = {
	    new TestGetInstance(null, 1, NullPointerException.class),
	    new TestGetInstance("foo", -1, IllegalArgumentException.class),
	    new TestGetInstance("foo", 0, IllegalArgumentException.class),
	    new TestGetInstance("foo", Integer.MIN_VALUE,
				IllegalArgumentException.class),
	    new TestGetInstance("foo", 65536, IllegalArgumentException.class),
	    new TestGetInstance("foo", Integer.MAX_VALUE,
				IllegalArgumentException.class),
	    new TestGetInstance("foo", 1, null),
	    new TestGetInstance("foo.sun.com", 65535, null)
	};

	private static final LazyMethod getHost =
	    new LazyMethod(useHttps ? "HttpsEndpoint" : "SslEndpoint",
			   "getHost", new Class[0]);

	private static final LazyMethod getPort =
	    new LazyMethod(useHttps ? "HttpsEndpoint" : "SslEndpoint",
			   "getPort", new Class[0]);

	private final String serverHost;
	private final int port;
	private final Class exceptionClass;

	private TestGetInstance(String serverHost,
				int port,
				Class exceptionClass) {
	    this.serverHost = serverHost;
	    this.port = port;
	    this.exceptionClass = exceptionClass;
	}

	public String name() {
	    return "getInstance(" + serverHost + ", " + port + ")";
	}

	public Object run() {
	    try {
		return createEndpoint(serverHost, port);
	    } catch (Exception e) {
		return e;
	    }
	}

	public void check(Object object) {
	    if (exceptionClass != null) {
		if (object == null || object.getClass() != exceptionClass) {
		    throw new FailedException(
			"Should throw " + exceptionClass.getName());
		} else {
		    return;
		}
	    } else if (object instanceof Exception) {
		throw new FailedException("Should not throw");
	    } else {
		String resultHost = (String) getHost.invoke(
		    object, new Object[0]);
		int resultPort = ((Integer) getPort.invoke(
		    object, new Object[0])).intValue();
		if (!serverHost.equals(resultHost)) {
		    throw new FailedException(
			"Server host should be " + serverHost);
		} else if (port != resultPort) {
		    throw new FailedException("Port should be " + port);
		}
	    }
	}
    }

    /* -- Test equals and hashCode -- */

    static {
	tests.add(TestEquals.localtests);
    }

    public static class TestEquals extends BasicTest {
	private static Object[] objects = {
	    null,
	    new Integer(3),
	    new Endpoint() {
		public OutboundRequestIterator newRequest(
		    InvocationConstraints constraints)
		{
		    return null;
		}
	    },
	    createEndpoint("foo", 1),
	    createEndpoint("foo", 33),
	    createEndpoint("bar", 1),
	    createEndpoint("bar", 33),
	    (useHttps
	     ? (Endpoint) SslEndpoint.getInstance("foo", 1)
	     : HttpsEndpoint.getInstance("foo", 1))
	};

	static Collection localtests = new ArrayList();
	static {
	    for (int i = objects.length; --i >= 0; ) {
		for (int j = objects.length; --j >= 0; ) {
		    Object x = objects[i];
		    Object y = objects[j];
		    boolean result = i == j;
		    localtests.add(new TestEquals("", x, y, result));
		    if (x instanceof SslEndpoint ||
			x instanceof HttpsEndpoint)
		    {
			try {
			    x = serialized(x);
			} catch (IOException e) {
			    throw unexpectedException(e);
			}
			localtests.add(
			    new TestEquals("Serialized ", x, y, result));
		    }
		}
	    }
	}

	private final Object x;
	private final Object y;

	private TestEquals(String name, Object x, Object y, boolean result) {
	    super(name + x + ", " + y, Boolean.valueOf(result));
	    this.x = x;
	    this.y = y;
	}

	public Object run() {
	    return Boolean.valueOf(x == null ? y == null : x.equals(y));
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    super.check(Boolean.valueOf(
		y == null ? x == null : y.equals(x)));
	    if (Boolean.TRUE.equals(result)) {
		int h1 = x == null ? 0 : x.hashCode();
		int h2 = y == null ? 0 : y.hashCode();
		if (h1 != h2) {
		    throw new FailedException("Hash codes differ");
		}
	    }
	}
    }

    /* -- Test newRequest -- */

    static {
	tests.add(TestNewRequest.localtests);
    }

    public static class TestNewRequest implements Test {
	static final Class OK = OutboundRequest.class;

	static final Class UNSUPPORTED = UnsupportedConstraintException.class;

	static final Subject clientAllRSASubject = new WithSubject() { {
	    addX500Principal("clientRSA1", subject);
	    addX500Principal("clientRSA2", subject);
	} }.subject();

	static Test[] localtests = {
	    new TestNewRequest(
		"Null constraints",
		null,
		null,
		null,
		NullPointerException.class),
	    new TestNewRequest(
		"Wrong server principal",
		clientRSASubject, // null client not allowed.
		requirements(ServerAuthentication.YES,
			     serverPrincipals(x500("CN=Wrong"))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Right server principal",
		clientRSASubject, // null client not allowed.
		requirements(ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Server should be anonymous",
		clientRSASubject, // null client not allowed.
		requirements(ServerAuthentication.NO),
		null,
		UNSUPPORTED),
	    new TestNewRequest(
		"Multiple server principals required",
		clientRSASubject, // null client not allowed.
		requirements(ServerAuthentication.YES,
			     serverPrincipals(
				 x500(clientRSA1),
				 x500(clientRSA2))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Server Principal alternatives required",
		clientRSASubject, // null client not allowed.
		requirements(ServerAuthentication.YES,
			     alternatives(
				 serverPrincipals(x500(serverRSA)),
				 serverPrincipals(x500(serverDSA)))),
		TestUtilities.serverSubject,
		OK),
	    new TestNewRequest(
		"Wrong client principal",
		clientRSASubject,
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500("CN=Wrong"))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Right client principal",
		clientRSASubject,
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Client should be anonymous",
		clientRSASubject,
		requirements(ClientAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Conflicting client principal constraints",
		clientAllRSASubject,
		requirements(ClientAuthentication.YES,
			     maxPrincipals(x500(clientRSA1)),
			     minPrincipals(x500(clientRSA2))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"min client principal constraint, multiple principals",
		clientAllRSASubject,
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"max client principal constraint, multiple principals",
		clientAllRSASubject,
		requirements(ClientAuthentication.YES,
			     maxPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"min and max client principal constraint, multiple principals",
		clientAllRSASubject,
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500(clientRSA1)),
			     maxPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Alternative client principal constraints",
		clientAllRSASubject,
		requirements(ClientAuthentication.YES,
			     alternatives(minPrincipals(x500(clientRSA1)),
					  minPrincipals(x500(clientRSA2)))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Trusted, unsupported constraint",
		clientRSASubject,
		requirements(new TestConstraint()),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Conflicting supported preferences",
		clientRSASubject,
		constraints(array(Confidentiality.YES),
			    array(ConfidentialityStrength.WEAK,
				  ConfidentialityStrength.STRONG)),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Conflicting unsupported alternatives",
		clientAllRSASubject,
		requirements(
		    ClientAuthentication.YES,
		    alternatives(maxPrincipals(x500(clientRSA1)),
				 maxPrincipals(x500("CN=Tim"))),
		    alternatives(maxPrincipals(x500(clientRSA2)),
				 maxPrincipals(x500("CN=Tim")))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Unsupported, unnecessary constraints",
		clientAllRSASubject,
		requirements(
		    serverPrincipals(x500(clientRSA1), x500(clientRSA2))),
		serverRSASubject,
		UNSUPPORTED), // Unsupported of course!
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Multiple principal preference", 
		clientRSASubject,
		constraints(ServerAuthentication.YES,
			    serverPrincipals(x500(serverRSA), x500(serverDSA))),
		TestUtilities.serverSubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Conflicting server principal preferences", 
		clientRSASubject,
		constraints(array(ServerAuthentication.YES),
			    array(serverPrincipals(x500(serverRSA)),
				  serverPrincipals(x500(serverDSA)))),
		TestUtilities.serverSubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"One of two client principal preferences",
		clientAllRSASubject,
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500(clientRSA2))),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Min client principal preference for only principal",
		clientRSASubject,
		constraints(ClientAuthentication.YES,
			    minPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Max client principal preference for all principals",
		clientAllRSASubject,
		constraints(ClientAuthentication.YES,
			    maxPrincipals(x500(clientRSA1), x500(clientRSA2))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Notice removed principals",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		serverRSASubject,
		OK)
	    {
		void secondRequest(Class result) throws IOException {
		    clientSubject.getPrincipals().clear();
		    super.secondRequest(UNSUPPORTED);
		}
	    },
	    new TestNewRequest(
		"Notice removed public credentials",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		serverRSASubject,
		OK)
	    {
		void secondRequest(Class result) throws IOException {
		    clientSubject.getPublicCredentials().clear();
		    super.secondRequest(UNSUPPORTED);
		}
	    },
	    new TestNewRequest(
		"Notice removed private credentials",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		OK)
	    {
		void secondRequest(Class result) throws IOException {
		    clientSubject.getPrivateCredentials().clear();
		    super.secondRequest(UNSUPPORTED);
		}
	    },
	    new TestNewRequest(
		"Notice removed private credentials with no server " +
		"principal constraint",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		serverRSASubject,
		OK)
	    {
		void secondRequest(Class result) throws IOException {
		    clientSubject.getPrivateCredentials().clear();
		    super.secondRequest(UNSUPPORTED);
		}
	    },
	    new TestNewRequest(
		"No principals specified",
		clientRSASubject,
		requirements(ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"No principals specified " +
		"with no private credential permissions",
		clientRSASubject,
		requirements(ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		OK)
	    {
		AccessControlContext getContext() {
		    return withPermissions(
			PrivateCredentialPermission.class, null);
		}
		/*
		 * This is a hack to check that the listen method doesn't
		 * require PrivateCredentialPermission.  -tjb[11.Apr.2003]
		 */
		public Object run() throws IOException {
		    try {
			return AccessController.doPrivileged(
			    new PrivilegedExceptionAction() {
				public Object run() throws IOException {
				    return superRun();
				}
			    },
			    getContext());
		    } catch (PrivilegedActionException e) {
			throw (IOException) e.getCause();
		    }
		}
		Object superRun() throws IOException {
		    return super.run();
		}
	    },
	    new TestNewRequest(
		"No principals specified with no access to subject",
		clientRSASubject,
		requirements(ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		OK)
	    {
		AccessControlContext getContext() {
		    return withPermissions(AuthPermission.class, null);
		}
	    },
            // Fails SSLException readHandshakeRecord why?
            // Caused by: java.net.SocketException: An established connection was aborted by the software in your host machine
//	    new TestNewRequest(
//		"No authentication permission for client principal, " +
//		"no server principal constraints",
//		new WithSubject() { {
//		    addX500Principal("noPerm", subject);
//		} }.subject(),
//		requirements(ClientAuthentication.YES,
//			     ServerAuthentication.YES),
//		serverRSASubject,
//		AccessControlException.class),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with server principal constraints",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		AccessControlException.class),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with server principal constraints and no access to subject",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		UNSUPPORTED)
	    {
		AccessControlContext getContext() {
		    return withPermissions(AuthPermission.class, null);
		}
	    },
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with full client and server principal constraints",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500("CN=noPerm")),
			     ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		AccessControlException.class),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with multiple full client and server principal constraints",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500("CN=noPerm")),
			     ServerAuthentication.YES,
			     alternatives(serverPrincipals(x500(serverRSA)),
					  serverPrincipals(x500(serverDSA)))),
		serverRSASubject,
		SecurityException.class),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with multiple full client and server principal constraints " +
		"and no subject",
		new Subject(),
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500("CN=noPerm")),
			     ServerAuthentication.YES,
			     alternatives(serverPrincipals(x500(serverRSA)),
					  serverPrincipals(x500(serverDSA)))),
		serverRSASubject,
		SecurityException.class),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with client authentication required and preference for " +
		"that principal",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		constraints(
		    array(ClientAuthentication.YES),
		    array(minPrincipals(x500("CN=noPerm")),
			  ServerAuthentication.YES,
			  serverPrincipals(x500(serverRSA)))),
		serverRSASubject,
		AccessControlException.class),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with client authentication required and preference for " +
		"that principal and no subject",
		null,
		constraints(
		    array(ClientAuthentication.YES),
		    array(minPrincipals(x500("CN=noPerm")),
			  ServerAuthentication.YES,
			  serverPrincipals(x500(serverRSA)))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"No authentication permission for one client principal, " +
		"with client authentication required and preference for " +
		"principal with no permission",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		    addX500Principal("clientRSA1", subject);
		} }.subject(),
		constraints(
		    array(ClientAuthentication.YES),
		    array(minPrincipals(x500("CN=noPerm")),
			  ServerAuthentication.YES,
			  serverPrincipals(x500(serverRSA)))),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with extra client and server principal constraints",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     maxPrincipals(x500("CN=noPerm"),
					   x500(clientRSA1)),
			     ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"No authentication permission for client principal, " +
		"with extra client and server principal constraints " +
		"and no subject",
		null,
		requirements(ClientAuthentication.YES,
			     maxPrincipals(x500("CN=noPerm"),
					   x500(clientRSA1)),
			     ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"Notice null Subject with getSubject permission but no " +
		"authentication permission",
		null,
		requirements(ClientAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED)
	    {
		ServerSocketFactory getServerSocketFactory() {
		    return new AbstractServerSocketFactory() {
			public ServerSocket createServerSocket()
			    throws IOException
			{
			    return new ServerSocket() {
				public Socket accept() throws IOException {
				    Socket s = super.accept();
				    throw new FailedException("Accepted call");
				}
			    };
			}
		    };
		}
	    },
	    new TestNewRequest(
		"With destroyed credentials",
		new WithSubject() { {
		    addX500Principal("clientRSA1", subject);
		    destroyPrivateCredentials(subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		"With credentials destroyed later",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		OK)
	    {
		void secondRequest(Class result) throws IOException {
		    destroyPrivateCredentials(clientSubject);
		    super.secondRequest(UNSUPPORTED);
		}
	    },
	    new TestNewRequest(
		"With expired credentials -- checks validity",
		new WithSubject() { {
		    addX500Principal("clientDSA2expired", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),

	    /* TestEndpointInternal */

	    new TestNewRequest(
		"Unsupported requirement",
		null, requirements(Integrity.NO), serverRSASubject, UNSUPPORTED),
	    new TestNewRequest(
		"Unsupported preference",
		clientRSASubject, preferences(Integrity.NO), serverRSASubject, OK),
	    new TestNewRequest(
		"Alternative requirements, only one supported",
		clientRSASubject,
		requirements(alternatives(Integrity.YES, Integrity.NO)),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Alternative requirements, both supported",
		clientRSASubject,
		requirements(
		    alternatives(
			ServerAuthentication.YES,
			ServerAuthentication.NO)),
		serverRSASubject,
		OK),
	    new TestNewRequest(
		"Unnecessary requirement",
		clientRSASubject,
		requirements(
		    new ClientMinPrincipalType(X500Principal.class)),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Unnecessary preference",
		clientRSASubject,
		preferences(new ClientMinPrincipalType(X500Principal.class)),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Requirement necessary because of preference",
		clientRSASubject,
		constraints(
		    new ClientMinPrincipalType(X500Principal.class),
		    ClientAuthentication.YES),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Subject principal supports preference for some suites",
		clientRSASubject,
		constraints(
		    array(ClientAuthentication.YES,
			  new ClientMinPrincipalType(X500Principal.class)),
		    array(minPrincipals(x500(clientRSA1)))),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Don't include preferred, wrong principal of right type",
		clientRSASubject,
		constraints(ClientAuthentication.YES,
			    minPrincipals(x500("CN=foo"))),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Don't include max principal preference if subject contains " +
		"multiple principals",
		clientAllRSASubject,
		constraints(ClientAuthentication.YES,
			    maxPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Max principal type implied by subject principals",
		clientAllRSASubject,
		constraints(ClientAuthentication.YES,
			    new ClientMaxPrincipalType(X500Principal.class)),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Alternative preferences, both supported",
		clientRSASubject,
		preferences(Confidentiality.YES,
			    Confidentiality.NO),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Alternative preferences, only one supported",
		clientRSASubject,
		constraints(
		    array(ServerAuthentication.NO),
		    array(Confidentiality.YES,
			  Confidentiality.NO)),
		serverRSASubject,
		UNSUPPORTED),

	    /* Combinations of constraints */
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.NO,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.NO,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.NO,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.NO,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.YES,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.YES,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.NO,
			     ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.NO,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.NO,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.NO,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.NO,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.YES,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.YES,
			     ServerAuthentication.NO),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		null,
		requirements(Confidentiality.YES,
			     ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		UNSUPPORTED),
	    new TestNewRequest(
		clientRSASubject,
		requirements(Confidentiality.YES,
			     ClientAuthentication.YES,
			     ServerAuthentication.YES),
		serverRSASubject,
		OK),

	    /* Others */

	    new TestNewRequest(
		"Server auth with no server subject",
		null, requirements(ServerAuthentication.YES), null,
		UNSUPPORTED),
	    new TestNewRequest(
		"No server auth with no server subject",
		null, requirements(ServerAuthentication.NO), null,
		UNSUPPORTED), // Anon no longer supported
	    new TestNewRequest(
		"Non-encrypting with RSA server credentials",
		clientRSASubject,
		requirements(Confidentiality.NO, ServerAuthentication.YES),
		new WithSubject() { {
		    addX500Principal("serverRSA", subject);
		} }.subject(),
		UNSUPPORTED),
	    new TestNewRequest(
		"Non-encrypting with DSA server credentials",
		new WithSubject() { {
		    addX500Principal("clientDSA", subject);
		} }.subject(),
		requirements(Confidentiality.NO, ServerAuthentication.YES),
		new WithSubject() { {
		    addX500Principal("serverDSA", subject);
		} }.subject(),
		UNSUPPORTED),
	    new TestNewRequest(
		"Client DSA credentials and server RSA credentials",
		new WithSubject() { {
		    addX500Principal("clientDSA", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500(clientDSA)),
			     ServerAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		serverRSASubject,
		OK),
	    /* XXX: Check preferences */
	    new TestNewRequest(
		"Client RSA credentials and multiple client constraints",
		TestUtilities.clientSubject,
		constraints(ClientAuthentication.YES,
			    minPrincipals(x500(clientRSA1))),
		serverRSASubject,
		OK)
	};

	private final String name;
	final Subject clientSubject;
	private final InvocationConstraints constraints;
	private final Subject serverSubject;
	private final Class result;
	private ListenHandle listenHandle;
	private Endpoint endpoint;
	private OutboundRequestIterator iter;

	private TestNewRequest(Subject clientSubject,
			       InvocationConstraints constraints,
			       Subject serverSubject,
			       Class result)
	{
	    this("", clientSubject, constraints, serverSubject, result);
	}

	private TestNewRequest(String name,
			       Subject clientSubject,
			       InvocationConstraints constraints,
			       Subject serverSubject,
			       Class result)
	{
	    this.name = name +
		"\n  clientSubject: " + clientSubject +
		"\n  constraints: " + constraints +
		"\n  serverSubject: " + serverSubject;
	    this.clientSubject = clientSubject;
	    this.constraints = constraints;
	    this.serverSubject = serverSubject;
	    this.result = result;
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public String name() {
	    return name;
	}

	/**
	 * Creates an endpoint and listen handle.  Then calls newRequest on the
	 * endpoint, returning any NullPointerException it throws, otherwise
	 * calls next on the resulting OutboundRequestIterator, returning the
	 * result, or any SecurityException or UnsupportedConstraintException
	 * it throws.
	 */
	public Object run() throws IOException {
	    ServerEndpoint serverEndpoint =
		createServerEndpoint(serverSubject, null, "localhost", 0,
				     null, getServerSocketFactory());
	    endpoint = serverEndpoint.enumerateListenEndpoints(
		new ListenContext() {
		    public ListenCookie addListenEndpoint(
			ListenEndpoint listenEndpoint)
			throws IOException
		    {
			listenHandle = listenEndpoint.listen(
			    new RequestDispatcher() {
				public void dispatch(InboundRequest r) { }
			    });
			return listenHandle.getCookie();
		    }
		});
	    try {
		 newRequest();
	    } catch (NullPointerException e) {
		return e;
	    }
	    return nextRequest();
	}

	/**
	 * Calls newRequest on the endpoint, using the specified client
	 * subject, and storing the result.
	 */
	void newRequest() {
	    Subject.doAsPrivileged(
		clientSubject,
		new PrivilegedAction() {
		    public Object run() {
			iter = endpoint.newRequest(constraints);
			return null;
		    }
		},
		getContext());
	}

	ServerSocketFactory getServerSocketFactory() { return null; }

	/** Returns the access control context for calling newRequest */
	AccessControlContext getContext() { return null; }

	/**
	 * Calls next on the OutboundRequestIterator, returning the result or
	 * any SecurityException or UnsupportedConstraintException it throws.
	 */
	Object nextRequest() throws IOException {
	    try {
		return iter.next();
	    } catch (SecurityException e) {
		return e;
	    } catch (UnsupportedConstraintException e) {
		return e;
	    }
	}

	public void check(Object object) throws IOException {
	    try {
		checkInternal(result, object);
		if (iter != null) {
		    /* There should be only one OutboundRequest */
		    if (iter.hasNext()) {
			throw new FailedException("Multiple requests");
		    }
		    try {
			iter.next();
			throw new FailedException("Multiple requests");
		    } catch (NoSuchElementException e) {
		    }
		    if (object instanceof OutboundRequest) {
			/* Try a second request */
			secondRequest(result);
		    }
		}
	    } finally {
		if (listenHandle != null) {
		    listenHandle.close();
		}
	    }
	}

	/**
	 * Checks that object matches the specified result class, either
	 * exactly for exceptions, or by inheritence.
	 */
	private void checkInternal(Class result, Object object) {
	    if (!result.isInstance(object)) {
		throw new FailedException("Should be a " + result.getName());
	    } else if (Exception.class.isAssignableFrom(result) &&
		       (object == null || object.getClass() != result))
	    {
		throw new FailedException("Should be " + result.getName());
	    }
	}

	/**
	 * Performs a second sequence of newRequest and next calls, and checks
	 * that the result matches the specified result class.
	 */
	void secondRequest(Class result) throws IOException {
	    newRequest();
	    Object second = nextRequest();
	    try {
		checkInternal(result, second);
	    } catch (FailedException e) {
		throw new FailedException(
		    "Second request returned " + second +
		    "\n" + e.getMessage());
	    }
	}

	static Subject newClientRSA1Subject() {
	    Subject subject = new Subject();
	    addX500Principal("clientRSA1", subject);
	    return subject;
	}

	static abstract class AbstractServerSocketFactory
	    extends ServerSocketFactory
	{
	    public abstract ServerSocket createServerSocket()
		throws IOException;
	    public ServerSocket createServerSocket(int port)
		throws IOException
	    {
		ServerSocket ss = createServerSocket();
		ss.bind(new InetSocketAddress(port));
		return ss;
	    }
	    public ServerSocket createServerSocket(int port, int backlog)
		throws IOException
	    {
		ServerSocket ss = createServerSocket();
		ss.bind(new InetSocketAddress(port), backlog);
		return ss;
	    }
	    public ServerSocket createServerSocket(int port,
						   int backlog,
						   InetAddress bindAddr)
		throws IOException
	    {
		ServerSocket ss = createServerSocket();
		ss.bind(new InetSocketAddress(bindAddr, port));
		return ss;
	    }
	}
    }
}
