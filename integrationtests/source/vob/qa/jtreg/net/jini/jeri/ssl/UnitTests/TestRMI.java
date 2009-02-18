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
 * @summary Performs user-level tests defined at the RMI level.
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @build TestUtilities
 * @run main/othervm/policy=policy TestRMI
 */

import java.io.*;
import java.net.*;
import java.rmi.*;
import java.rmi.server.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.*;
import net.jini.core.constraint.*;
import net.jini.export.*;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientSubject;
import net.jini.jeri.*;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.security.*;

public class TestRMI extends TestUtilities {

    /** All RMI tests */
    static final Collection tests = new ArrayList();

    /** Main test entrypoint */
    public static void main(String[] args) {
	test(tests);
    }
	
    public interface Hello extends Remote {
	String hello() throws RemoteException;
    }

    public static class HelloImpl implements Hello {
	final Exporter exporter;

	HelloImpl(Subject serverSubject) throws IOException {
	    exporter = new BasicJeriExporter(
		createServerEndpoint(serverSubject),
		new BasicILFactory(
		    new BasicMethodConstraints(InvocationConstraints.EMPTY),
		    null),
		false, false, null);
	}
	    
	public String hello() {
	    return "Hi";
	}
	
	Hello export() throws ExportException {
	    return (Hello) exporter.export(this);
	}

	void unexport() {
	    exporter.unexport(true);
	}
    }

    /* -- TestClientSubjectAfterSwitchConstraints -- */

    static {
	tests.add(new TestClientSubjectAfterSwitchConstraints());
    }

    public static class TestClientSubjectAfterSwitchConstraints
	extends BasicTest
    {
	/**
	 * A remote interface for determining the principal used to
	 * authenticate the caller.
	 */
	interface GetClientPrincipal extends Remote {
	    String getClientPrincipal() throws RemoteException;
	}

	/** Implements GetClientPrincipal. */
	static class GetClientPrincipalImpl implements GetClientPrincipal {
	    public String getClientPrincipal() throws RemoteException {
		try {
		    ClientSubject cs = (ClientSubject)
			ServerContext.getServerContextElement(
			    ClientSubject.class);
		    Subject subject =
			(cs != null) ? cs.getClientSubject() : null;
		    if (subject == null) {
			return null;
		    } else {
			Set principals = subject.getPrincipals();
			Principal p = (Principal) principals.iterator().next();
			return p.getName();
		    }
		} catch (ServerNotActiveException e) {
		    throw new RemoteException(e.toString());
		}
	    }
	}

	/** An action for calling getClientPrincipal */
	static class GetClientPrincipalAction
	    implements PrivilegedExceptionAction
	{
	    private final GetClientPrincipal gcp;

	    GetClientPrincipalAction(GetClientPrincipal gcp) {
		this.gcp = gcp;
	    }

	    public Object run() throws RemoteException {
		return gcp.getClientPrincipal();
	    }
	}

	TestClientSubjectAfterSwitchConstraints() {
	    super("", getAliasPrincipalName("clientDSA"));
	}

	private static String getAliasPrincipalName(String alias) {
	    List certs = getCertificateChain(alias).getCertificates();
	    return ((X509Certificate) certs.get(0)).getSubjectDN().getName();
	}

	public Object run() throws IOException {
	    GetClientPrincipal server = new GetClientPrincipalImpl();
	    Exporter exporter = null;
	    try {
		exporter = new BasicJeriExporter(
		    createServerEndpoint(serverSubject),
		    new BasicILFactory(
			new BasicMethodConstraints(
			    requirements(ClientAuthentication.YES)),
			null),
		    false, false, null);
		GetClientPrincipal stub = (GetClientPrincipal)
		    exporter.export(server);

		stub = (GetClientPrincipal)
		    ((RemoteMethodControl) stub).setConstraints(
			new BasicMethodConstraints(
			    preferences(Confidentiality.YES,
					ConfidentialityStrength.WEAK)));

		Subject clientSubject = new Subject();
		addX500Principal("clientRSA1", clientSubject);
		Subject.doAs(clientSubject,
			     new GetClientPrincipalAction(stub));
	    
		clientSubject.getPrincipals().clear();
		addX500Principal("clientDSA", clientSubject);
		return Subject.doAs(clientSubject,
				    new GetClientPrincipalAction(stub));

	    } catch (PrivilegedActionException e) {
		throw (IOException) e.getException();
	    } finally {
		if (exporter != null) {
		    exporter.unexport(true);
		}
	    }
	}
    }

    /* -- TestUnexportInServerImpl -- */

    static {
	tests.add(TestUnexportInServerImpl.localTests);
    }

    public static class TestUnexportInServerImpl extends BasicTest {

	static final Test[] localTests = {
	    new TestUnexportInServerImpl(true),
	    new TestUnexportInServerImpl(false),
	};

	private final boolean force;

	interface I extends Remote {
	    String hello() throws RemoteException;
	    boolean unexport(boolean force) throws RemoteException;
	}

	static class Impl implements I {
	    final Exporter exporter;

	    Impl() throws IOException {
		exporter = new BasicJeriExporter(
		    createServerEndpoint(serverSubject),
		    new BasicILFactory(
			new BasicMethodConstraints(InvocationConstraints.EMPTY),
			null),
		    false, false, null);
	    }

	    public String hello() throws RemoteException {
		return "Hi";
	    }

	    public boolean unexport(boolean force) throws RemoteException {
		return exporter.unexport(force);
	    }
	}

	TestUnexportInServerImpl(boolean force) {
	    super("force=" + force);
	    this.force = force;
	}

	public static void main(String[] args) {
	    test(localTests);
	}

	public Object run() throws IOException {
	    Impl server = new Impl();
	    I stub = (I) server.exporter.export(server);
	    stub.hello();
	    try {
		return new Boolean(stub.unexport(force));
	    } catch (IOException e) {
		return e;
	    }
	}

	public void check(Object object) {
	    if (!force) {
		if (!Boolean.FALSE.equals(object)) {
		    throw new FailedException("Expected Boolean.FALSE");
		}
	    } else if (!(object instanceof IOException)) {
		throw new FailedException("Expected an IOException");
	    }
	}
    }

    /* -- TestSimple1 -- */

    static {
	tests.add(new TestSimple1());
    }

    /**
     * Tests making a simple RMI request with two different sets of
     * preferences.
     */
    public static class TestSimple1 extends BasicTest {
	TestSimple1() {
	    super("");
	}

	public static void main(String[] args) {
	    test(new TestSimple1());
	}

	public Object run() throws IOException {
	    HelloImpl server = new HelloImpl(serverSubject);
	    Hello stub = server.export();
	    stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
		new BasicMethodConstraints(
		    new InvocationConstraints(
			Confidentiality.YES,
			ConfidentialityStrength.STRONG)));
	    stub.hello();
	    stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
		new BasicMethodConstraints(
		    new InvocationConstraints(
			Confidentiality.YES,
			ConfidentialityStrength.WEAK)));
	    stub.hello();	    
	    server.unexport();
	    return null;
	}

	public void check(Object object) { }
    }

    /* -- TestTimeout -- */

    static {
	tests.add(TestTimeout.localtests);
    }

    /** Test timing out client and server SSL sessions. */
    public static class TestTimeout extends BasicTest {
	/* Time needed to complete an initial call successfully */
	static final long CALLTIME = 10 * 1000;

	static Test[] localtests = {
	    new TestTimeout("client timeout", 2 * CALLTIME) {
		public Object run() throws IOException {
		    long old = setMaxClientSessionDuration(CALLTIME);
		    try {
			return super.run();
		    } finally {
			setMaxClientSessionDuration(old);
		    }
		}
	    },
	    new TestTimeout("client timeout wraparound", CALLTIME) {
		public Object run() throws IOException {
		    long old = setMaxClientSessionDuration(Long.MAX_VALUE);
		    try {
			return super.run();
		    } finally {
			setMaxClientSessionDuration(old);
		    }
		}
	    },
	    new TestTimeout("server timeout", 2 * CALLTIME) {
		public Object run() throws IOException {
		    long old = setMaxServerSessionDuration(CALLTIME);
		    try {
			return super.run();
		    } catch (IOException e) {
			return e;
		    } finally {
			setMaxServerSessionDuration(old);
		    }
		}
		public void check(Object result) {
		    if (!(result instanceof UnmarshalException)) {
			throw new FailedException(
			    "Unexpected exception: " + result);
		    }
		}
	    },
	    new TestTimeout("server timeout wraparound", CALLTIME) {
		public Object run() throws IOException {
		    long old = setMaxServerSessionDuration(Long.MAX_VALUE);
		    try {
			return super.run();
		    } finally {
			setMaxServerSessionDuration(old);
		    }
		}
	    }
	};

	Subject clientSubject = getClientSubject();

	long timeout;
	int calls;
	IOException ioException;
	boolean done;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestTimeout(String name, long timeout) {
	    super(name);
	    this.timeout = timeout;
	}

	Subject getClientSubject() {
	    return new WithSubject() { {
		addX500Principal("clientDSA", subject);
		addX500Principal("clientRSA1", subject);
		addX500Principal("clientRSA2", subject);
	    } }.subject();
	}

	public Object run() throws IOException {
	    try {
		return Subject.doAs(
		    clientSubject,
		    new PrivilegedExceptionAction() {
			public Object run() throws IOException {
			    return runInternal();
			}
		    });
	    } catch (PrivilegedActionException e) {
		throw (IOException) e.getException();
	    }
	}

	public void check(Object result) { }

	Object runInternal() throws IOException {
	    Thread t = new Thread() {
		public void run() {
		    runInThread();
		}
	    };
	    t.start();
	    synchronized (this) {
		long stop = System.currentTimeMillis() + 2 * timeout;
		do {
		    try {
			this.wait(stop - System.currentTimeMillis());
		    } catch (InterruptedException e) {
		    }
		} while (!done && System.currentTimeMillis() < stop);
	    }
	    if (calls == 0) {
 		throw new FailedException("No calls made");
	    } else if (ioException != null) {
		throw ioException;
	    } else {
		return null;
	    }
	}

	void runInThread() {
	    try {
		HelloImpl server = new HelloImpl(serverSubject);
		Hello stub = server.export();
		Hello[] stubs = {
		    (Hello) ((RemoteMethodControl) stub).setConstraints(
			new BasicMethodConstraints(
			    requirements(Confidentiality.YES,
					 ClientAuthentication.YES,
					 minPrincipals(x500(clientDSA))))),
		    (Hello) ((RemoteMethodControl) stub).setConstraints(
			new BasicMethodConstraints(
			    requirements(Confidentiality.YES,
					 ClientAuthentication.YES,
					 minPrincipals(x500(clientRSA1))))),
		    (Hello) ((RemoteMethodControl) stub).setConstraints(
			new BasicMethodConstraints(
			    requirements(Confidentiality.YES,
					 ClientAuthentication.YES,
					 minPrincipals(x500(clientRSA2)))))
		};

		long stop = System.currentTimeMillis() + timeout;
		for ( ; System.currentTimeMillis() < stop; calls++) {
		    stubs[calls % stubs.length].hello();
		    try {
			Thread.sleep(10);
		    } catch (InterruptedException e) {
		    }
		}
		server.unexport();
	    } catch (IOException e) {
		ioException = e;
	    } finally {
		synchronized (TestTimeout.this) {
		    done = true;
		    TestTimeout.this.notify();
		}
	    }
	}
    }

    /* -- TestExpired -- */

    static {
	tests.add(TestExpired.localtests);
    }

    /** Test with expired certificates. */
    public static class TestExpired extends BasicTest {
	static Test[] localtests = { new TestExpired() };

	Subject clientSubject = new WithSubject() { {
	    addX500Principal("clientDSA2", subject);
	} }.subject(false /* readOnly */);

	Subject serverSubject = new WithSubject() { {
	    addX500Principal("serverRSA2", subject);
	} }.subject(false /* readOnly */);

	public static void main(String[] args) {
	    test(localtests);
	}

	TestExpired() {
	    super("Expired client and server credentials");
	}

	public Object run() throws IOException {
	    try {
		return Subject.doAs(
		    clientSubject,
		    new PrivilegedExceptionAction() {
			public Object run() throws IOException {
			    return runInternal();
			}
		    });
	    } catch (PrivilegedActionException e) {
		throw (IOException) e.getException();
	    }
	}

	public void check(Object result) { }

	Object runInternal() throws IOException {
	    HelloImpl server = new HelloImpl(serverSubject);
	    try {
		Hello stub = server.export();
		stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
		    new BasicMethodConstraints(
			requirements(Confidentiality.YES,
				     ClientAuthentication.YES,
				     ServerAuthentication.YES)));

		debugPrint(30, "Good credentials");
		stub.hello();

		debugPrint(30, "Expired client credentials");
		clientSubject.getPublicCredentials().clear();
		addX500Principal("clientDSA2expired", clientSubject);
		try {
		    stub.hello();
		    throw new FailedException(
			"No exception thrown for expired client credentials");
		} catch (IOException e) {
		    /*
		     * Note that JERI throws a ConnectIOException if an
		     * IOException gets thrown while establishing a connection
		     * for a call, as in this case when the server rejects the
		     * client's credentials.  -tjb[8.Oct.2001]
		     */
		    debugPrint(30, "Expected exception: " + e);
		}

		debugPrint(30, "Good client credentials");
		clientSubject.getPublicCredentials().clear();
		addX500Principal("clientDSA2", clientSubject);
		stub.hello();

		debugPrint(30, "Expired server credentials");
		serverSubject.getPublicCredentials().clear();
		addX500Principal("serverRSA2expired", serverSubject);
		try {
		    stub.hello();
		    throw new FailedException(
			"No exception thrown for expired server credentials");
		} catch (IOException e) {
		    /*
		     * Note that the MUX code throws IOException if the remote
		     * side throws an unchecked exception, like the
		     * SecurityException thrown when the server's credentials
		     * have expired.  -tjb[8.Oct.2001]
		     */
		    debugPrint(30, "Expected exception: " + e);
		}

		debugPrint(30, "Good server credentials");
		serverSubject.getPublicCredentials().clear();
		addX500Principal("serverRSA2", serverSubject);
		stub.hello();

		return null;
	    } finally {
		server.unexport();
	    }
	}
    }

    /* -- TestNotTrusted -- */

    static {
	tests.add(TestNotTrusted.localtests);
    }

    /**
     * Test accessing a server with an untrusted certificate.  Make sure that
     * the provider handles the fact that JSSE picks an authenticating suite if
     * it is enabled even if the client rejects the server's credentials but
     * would allow an anonymous connection.
     */
    public static class TestNotTrusted extends BasicTest {
	static Subject notTrustedServerSubject = new WithSubject() { {
	    addX500Principal("notTrusted", subject);
	} }.subject();

	static Test[] localtests = {
	    new TestNotTrusted("Anonymous only",
			       requirements(ServerAuthentication.NO),
			       notTrustedServerSubject,
			       null),
	    new TestNotTrusted("Anonymous or authenticated",
			       null,
			       notTrustedServerSubject,
			       ConnectIOException.class),
	    new TestNotTrusted("Anonymous or authenticated, wrong server",
			       requirements(
				   serverPrincipals(x500(serverDSA))),
			       serverRSASubject,
			       ConnectIOException.class),
	    new TestNotTrusted("Prefer authenticated, wrong server",
			       constraints(
				   serverPrincipals(x500(serverDSA)),
				   ServerAuthentication.YES),
			       serverRSASubject,
			       ConnectIOException.class),
	    new TestNotTrusted("Prefer anonymous, wrong server",
			       constraints(
				   serverPrincipals(x500(serverDSA)),
				   ServerAuthentication.NO),
			       serverRSASubject,
			       null)
	};

	Subject serverSubject;
	InvocationConstraints constraints;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestNotTrusted(String name,
		       InvocationConstraints constraints,
		       Subject serverSubject,
		       Object result)
	{
	    super(name, result);
	    this.constraints = constraints;
	    this.serverSubject = serverSubject;
	}

	public Object run() throws IOException {
	    HelloImpl server = new HelloImpl(serverSubject);
	    Hello stub = server.export();
	    stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
		new BasicMethodConstraints(constraints));
	    ConnectIOException exception = null;
	    try {
		stub.hello();
	    } catch (ConnectIOException e) {
		exception = e;
	    }
	    server.unexport();
	    return exception;
	}

	public void check(Object result) throws Exception {
	    super.check(result == null ? null : result.getClass());
	}
    }	

    /* -- TestMissingPermissions -- */

    static {
	tests.add(TestMissingPermissions.localtests);
    }

    /** Run tests with missing authentication permissions. */
    public static class TestMissingPermissions extends BasicTest {
	static Test[] localtests = {
	    new TestMissingPermissions(
		"One previous encrypting suite",
		new InvocationConstraints[] {
		    requirements(ClientAuthentication.YES,
				 Confidentiality.YES)
		},
		array(x500(clientRSA1)),
		requirements(ClientAuthentication.YES,
			     Confidentiality.YES),
		array(x500(clientDSA))),
	    new TestMissingPermissions(
		"One previous non-encrypting suite",
		new InvocationConstraints[] {
		    requirements(ClientAuthentication.YES,
				 Confidentiality.NO)
		},
		array(x500(clientRSA1)),
		requirements(ClientAuthentication.YES,
			     Confidentiality.NO),
		array(x500(clientDSA))),
	    new TestMissingPermissions(
		"Two previous suites",
		new InvocationConstraints[] {
		    requirements(ClientAuthentication.YES,
				 Confidentiality.NO),
		    requirements(ClientAuthentication.YES,
				 Confidentiality.YES)
		},
		array(x500(clientRSA1)),
		requirements(ClientAuthentication.YES),
		array(x500(clientDSA)))
	};

	InvocationConstraints[] initialConstraints;
	Set initialPrincipals;
	InvocationConstraints testConstraints;
	Set testPrincipals;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestMissingPermissions(String name,
			       InvocationConstraints[] initialConstraints,
			       Principal[] initialPrincipals,
			       InvocationConstraints testConstraints,
			       Principal[] testPrincipals)
	{
	    super(name);
	    this.initialConstraints = initialConstraints;
	    this.initialPrincipals =
		new HashSet(Arrays.asList(initialPrincipals));
	    this.testConstraints = testConstraints;
	    this.testPrincipals = new HashSet(Arrays.asList(testPrincipals));
	}

	public Object run() throws IOException {
	    HelloImpl server = new HelloImpl(serverSubject);
	    final Hello stub = server.export();
	    class Action implements PrivilegedExceptionAction {
		InvocationConstraints c;
		Action(InvocationConstraints c) { this.c = c; }
		public Object run() throws IOException {
		    ((Hello) ((RemoteMethodControl) stub).setConstraints(
			new BasicMethodConstraints(c))).hello();
		    return null;
		}
	    }
	    try {
		for (int i = 0; i < initialConstraints.length; i++) {
		    Subject.doAsPrivileged(
			clientSubject, new Action(initialConstraints[i]),
			withAuthenticationPermissions(
			    new AuthenticationPermission[] {
				new AuthenticationPermission(
				    initialPrincipals, null, "connect") }));
		}
		Subject.doAsPrivileged(
		    clientSubject, new Action(testConstraints),
		    withAuthenticationPermissions(
			new AuthenticationPermission[] {
			    new AuthenticationPermission(
				testPrincipals, null, "connect") }));
	    } catch (PrivilegedActionException e) {
		throw (IOException) e.getException();
	    }
	    server.unexport();
	    return null;
	}

	public void check(Object result) { }
    }

    /* -- TestServerCredential -- */

    static {
	tests.add(TestServerCredential.localtests);
    }

    /**
     * Run tests with specific server credential -- checks that server
     * credentials with spaces in the name work properly.
     */
    public static class TestServerCredential extends BasicTest {
	static Test[] localtests = {
	    new TestServerCredential()
	};

	TestServerCredential() {
	    super("");
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() throws IOException {
	    Exporter exporter = new BasicJeriExporter(
		createServerEndpoint(
		    new WithSubject() { {
			addX500Principal("serverDSA", subject);
		    } }.subject()),
		new BasicILFactory(
		    new BasicMethodConstraints(InvocationConstraints.EMPTY),
		    null),
		false, false, null);
	    HelloImpl server = new HelloImpl(serverSubject);
	    Hello stub = (Hello) exporter.export(server);
	    stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
		new BasicMethodConstraints(
		    requirements(ServerAuthentication.YES,
				 serverPrincipals(x500(serverDSA)))));
	    stub.hello();
	    exporter.unexport(true);
	    return null;
	}

	public void check(Object result) { }
    }

    /* -- TestSubjectModification -- */

    static {
	/*
	 * XXX: Disable this test by default, due to BugID 4892913,
	 * Subject.getPrivateCredentials not thread-safe against changes to
	 * principals.  -tjb[22.Jul.2003]
	 *
	 * tests.add(TestSubjectModification.localtests);
	 */
    }

    /**
     * Tests that modifications to the subject don't cause iterators over
     * subject principals and credentials to get
     * ConcurrentModificationExceptions.
     */
    public static class TestSubjectModification extends BasicTest {
	static Test[] localtests = { new TestSubjectModification() };

	Subject clientSubject = new WithSubject() { {
	    addX500Principal("clientDSA", subject);
	    addX500Principal("clientRSA1", subject);
	    addX500Principal("clientRSA2", subject);
	} }.subject(false);

	Subject serverSubject = new WithSubject() { {
	    addX500Principal("serverDSA", subject);
	    addX500Principal("serverRSA", subject);
	} }.subject(false);

	TestSubjectModification() {
	    super("");
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() {
	    ModifySubject ms = new ModifySubject();
	    try {
		return Subject.doAs(clientSubject,
				    new PrivilegedAction() {
					public Object run() {
					    makeCalls();
					    return null;
					}
				    });
	    } finally {
		ms.done();
	    }
	}

	void makeCalls() {
	    Exporter exporter = null;
	    for (int i = 0; i < 10; i++) {
		try {
		    debugPrint(30, "Export " + i);
		    exporter = new BasicJeriExporter(
			createServerEndpoint(serverSubject),
			new BasicILFactory(
			    new BasicMethodConstraints(
				InvocationConstraints.EMPTY), null),
			false, false, null);
		    HelloImpl server = new HelloImpl(serverSubject);
		    Hello stub = (Hello) exporter.export(server);
		    stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
			new BasicMethodConstraints(
			    requirements(ClientAuthentication.YES,
					 ServerAuthentication.YES)));
		    for (int j = 0; j < 100; j++) {
			stub.hello();
		    }
		} catch (Exception e) {
		    throw unexpectedException(e);
		} finally {
		    if (exporter != null) {
			try {
			    exporter.unexport(true);
			} catch (IllegalStateException e) {
			}
			exporter = null;
		    }
		}
	    }
	}

	class ModifySubject extends Thread {
	    private boolean done;

	    ModifySubject() {
		start();
	    }

	    synchronized void done() {
		done = true;
	    }

	    public void run() {
		Set[] sets = {
		    clientSubject.getPrincipals(),
		    clientSubject.getPublicCredentials(),
		    clientSubject.getPrivateCredentials(),
		    serverSubject.getPrincipals(),
		    serverSubject.getPublicCredentials(),
		    serverSubject.getPrivateCredentials() };
		Principal p = new X500Principal("CN=dummy");
		for (int i = 0; true; i++) {
		    synchronized (this) {
			if (done) {
			    break;
			}
		    }
		    if (i % 1000 == 0) {
			debugPrint(30, "ModifySubject: " + i);
		    }
		    sets[i % sets.length].add(p);
		    sets[i % sets.length].remove(p);
		}
	    }
	}

	public void check(Object result) { }
    }
}
