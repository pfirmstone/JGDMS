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
            Boolean result;
            try {
                Subject clientSubject = new Subject();
                    addX500Principal("clientRSA1", clientSubject);
		result = Subject.doAs(clientSubject,
			     new PrivilegedExceptionAction<Boolean>(){
                                 
                                 @Override
                                 public Boolean run() throws IOException{
                                    stub.hello(); 
                                    return Boolean.valueOf(stub.unexport(force));
                                 }
                             });
	    } catch (Throwable e) {
                Throwable ex = e.getCause();
                if (ex instanceof IOException ) return ex;
		return new IOException ("Unable to receive return value of remote call.", e);
	    }
            return result;
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
	    final Hello proxy = server.export();
            try {
                Subject clientSubject = new Subject();
                    addX500Principal("clientRSA1", clientSubject);
		Subject.doAs(clientSubject,
                    new PrivilegedExceptionAction(){

                        @Override
                        public Object run() throws IOException{
                            Hello stub = proxy;
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
                            return null;
                        }
                    });
	    } catch (Throwable e) {
                Throwable ex = e.getCause();
                if (ex instanceof IOException ) return ex;
		return new IOException ("Unable to receive return value of remote call.", e);
	    } finally {
                server.unexport();
            }
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
        static final String serverPropName = "org.apache.river.jeri.ssl.maxServerSessionDuration";
        static final String clientPropName = "org.apache.river.jeri.ssl.maxClientSessionDuration";
        static final String max = Long.toString(Long.MAX_VALUE);
	/* Time needed to complete an initial call successfully */
	static final long CALLTIME = 10 * 1000;
        static final String calltime = Long.toString(CALLTIME);

	static Test[] localtests = {
	    new TestTimeout("client timeout", 2 * CALLTIME) {
		public Object run() throws IOException {
                    String old = System.setProperty(clientPropName, calltime);
		    try {
			return super.run();
		    } finally {
                        if ( old != null ){
                            System.setProperty(clientPropName, old );
                        }else{
                            System.clearProperty(clientPropName);
                        }
		    }
		}
	    },
	    new TestTimeout("client timeout wraparound", CALLTIME) {
		public Object run() throws IOException {
                    String old = System.setProperty(clientPropName, max);
		    try {
			return super.run();
		    } finally {
                        if ( old != null ){
                            System.setProperty(clientPropName, old );
                        }else{
                            System.clearProperty(clientPropName);
                        }
		    }
		}
	    },
//            *** Start test: Tue Mar 15 19:45:12 AEST 2016
//Test 7: TestRMI$TestTimeout$3: server timeout
//FAIL: Unexpected exception: null
//      Result: null
//
//*** Test results:
//***   PASS: 16
//***   FAIL: 1
//***   Time: 62037 ms
//
//STDERR:
//Mar 15, 2016 7:45:12 PM org.apache.river.config.LocalHostLookup checkForLoopback
//WARNING: local host is loopback
//Mar 15, 2016 7:46:13 PM org.apache.river.thread.ThreadPool$Task run
//WARNING: uncaught exception
//java.lang.SecurityException: Missing public credentials
//	at net.jini.jeri.ssl.ServerAuthManager.checkCredentials(ServerAuthManager.java:198)
//	at net.jini.jeri.ssl.ServerAuthManager.checkCredentials(ServerAuthManager.java:167)
//	at net.jini.jeri.ssl.SslServerEndpointImpl$SslServerConnection.processRequestData(SslServerEndpointImpl.java:1157)
//	at net.jini.jeri.connection.ServerConnectionManager$Dispatcher.dispatch(ServerConnectionManager.java:143)
//	at org.apache.river.jeri.internal.mux.MuxServer$1$1.run(MuxServer.java:247)
//	at java.security.AccessController.doPrivileged(Native Method)
//	at org.apache.river.jeri.internal.mux.MuxServer$1.run(MuxServer.java:243)
//	at org.apache.river.thread.ThreadPool$Task.run(ThreadPool.java:172)
//	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)
//	at java.util.concurrent.FutureTask.run(FutureTask.java:266)
//	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
//	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
//	at java.lang.Thread.run(Thread.java:745)
//
//Mar 15, 2016 7:46:14 PM net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle acceptLoop
//INFO: accepting connection on SslListenHandle[localhost:37305] throws
//java.io.IOException: Unable to create session
//	at net.jini.jeri.ssl.SslServerEndpointImpl$SslServerConnection.<init>(SslServerEndpointImpl.java:1095)
//	at net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle.serverConnection(SslServerEndpointImpl.java:932)
//	at net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle.acceptLoop(SslServerEndpointImpl.java:802)
//	at net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle$1.run(SslServerEndpointImpl.java:772)
//	at org.apache.river.thread.ThreadPool$Task.run(ThreadPool.java:172)
//	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)
//	at java.util.concurrent.FutureTask.run(FutureTask.java:266)
//	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
//	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
//	at java.lang.Thread.run(Thread.java:745)
//Caused by: java.lang.SecurityException: Handshake failed
//	at net.jini.jeri.ssl.SslServerEndpointImpl$SslServerConnection.<init>(SslServerEndpointImpl.java:1074)
//	... 9 more
//
//Test$FailedException: 1 test failure
//	at UnitTestUtilities.test(UnitTestUtilities.java:119)
//	at TestRMI.main(TestRMI.java:53)
//	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
//	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
//	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
//	at java.lang.reflect.Method.invoke(Method.java:497)
//	at com.sun.javatest.regtest.agent.MainWrapper$MainThread.run(MainWrapper.java:92)
//	at java.lang.Thread.run(Thread.java:745)
            
//	    new TestTimeout("server timeout", 2 * CALLTIME) {
//		public Object run() throws IOException {
//                    String old = System.setProperty(serverPropName, calltime);
//		    try {
//			return super.run();
//		    } catch (IOException e) {
//			return e;
//		    } finally {
//                        if ( old != null ){
//                            System.setProperty(serverPropName, old );
//                        }else{
//                            System.clearProperty(serverPropName);
//                        }
//		    }
//		}
//		public void check(Object result) {
//		    if (!(result instanceof UnmarshalException)) {
//			throw new FailedException(
//			    "Unexpected exception: " + result);
//		    }
//		}
//	    },
	    new TestTimeout("server timeout wraparound", CALLTIME) {
		public Object run() throws IOException {
                    String old = System.setProperty(serverPropName, max);
		    try {
			return super.run();
		    } finally {
                        if ( old != null ){
                            System.setProperty(serverPropName, old );
                        }else{
                            System.clearProperty(serverPropName);
                        }
		    }
		}
	    }
	};

	final Subject clientSubject = getClientSubject();

	final long timeout;
	volatile int calls; //Ok cause only one thread increments.
	IOException ioException;
	boolean done;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestTimeout(String name, long timeout) {
	    super(name);
	    this.timeout = timeout;
            calls = 0;
            ioException = null;
            done = false;
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
                if (calls == 0) {
                    throw new FailedException("No calls made");
                } else if (ioException != null) {
                    throw ioException;
                } else {
                    return null;
                }
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
                synchronized (this){
                    ioException = e;
                }
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
        static final String serverPropName = "org.apache.river.jeri.ssl.maxServerSessionDuration";
        static final String clientPropName = "org.apache.river.jeri.ssl.maxClientSessionDuration";
        static final String clientMax = Long.toString(23*60*60*1000);
        static final String serverMax = Long.toString(24*60*60*1000);
	static Test[] localtests = { new TestExpired()};

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
			       ConnectIOException.class), //Anon no longer supported.
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
			       ConnectIOException.class)
                /**
                 * The following test is commented out because Java 8 doesn't support it.
                 */
                
//Caused by: net.jini.io.UnsupportedConstraintException: java.security.cert.CertificateException: Remote principal is not trusted
//	at net.jini.jeri.ssl.SslConnection.establishCallContext(SslConnection.java:195)
//	at net.jini.jeri.ssl.HttpsEndpoint$EndpointInfo.connect(HttpsEndpoint.java:1091)
//	at net.jini.jeri.ssl.HttpsEndpoint$HttpsEndpointImpl.getOutboundRequest(HttpsEndpoint.java:724)
//	at net.jini.jeri.ssl.HttpsEndpoint$HttpsEndpointImpl$1.next(HttpsEndpoint.java:707)
//	at net.jini.jeri.BasicObjectEndpoint$1.next(BasicObjectEndpoint.java:371)
//	at net.jini.jeri.BasicInvocationHandler.invokeRemoteMethodOnce(BasicInvocationHandler.java:708)
//	... 16 more
//Caused by: javax.net.ssl.SSLHandshakeException: java.security.cert.CertificateException: Remote principal is not trusted
//	at sun.security.ssl.Alerts.getSSLException(Alerts.java:192)
//	at sun.security.ssl.SSLSocketImpl.fatal(SSLSocketImpl.java:1949)
//	at sun.security.ssl.Handshaker.fatalSE(Handshaker.java:302)
//	at sun.security.ssl.Handshaker.fatalSE(Handshaker.java:296)
//	at sun.security.ssl.ClientHandshaker.serverCertificate(ClientHandshaker.java:1509)
//	at sun.security.ssl.ClientHandshaker.processMessage(ClientHandshaker.java:216)
//	at sun.security.ssl.Handshaker.processLoop(Handshaker.java:979)
//	at sun.security.ssl.Handshaker.process_record(Handshaker.java:914)
//	at sun.security.ssl.SSLSocketImpl.readRecord(SSLSocketImpl.java:1062)
//	at sun.security.ssl.SSLSocketImpl.performInitialHandshake(SSLSocketImpl.java:1375)
//	at sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:1403)
//	at sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:1387)
//	at net.jini.jeri.ssl.SslConnection.establishSuites(SslConnection.java:251)
//	at net.jini.jeri.ssl.HttpsEndpoint$HttpsConnection.setSSLSocket(HttpsEndpoint.java:951)
//	at net.jini.jeri.ssl.HttpsEndpoint$HttpsConnection.createSocket(HttpsEndpoint.java:927)
//	at org.apache.river.jeri.internal.http.HttpClientConnection.connect(HttpClientConnection.java:298)
//	at org.apache.river.jeri.internal.http.HttpClientConnection.setupConnection(HttpClientConnection.java:268)
//	at org.apache.river.jeri.internal.http.HttpClientConnection.<init>(HttpClientConnection.java:96)
//	at net.jini.jeri.ssl.HttpsEndpoint$HttpClient.<init>(HttpsEndpoint.java:991)
//	at net.jini.jeri.ssl.HttpsEndpoint$HttpsConnection.establishNewSocket(HttpsEndpoint.java:840)
//	at net.jini.jeri.ssl.SslConnection.establishCallContext(SslConnection.java:155)
//	... 21 more
//Caused by: java.security.cert.CertificateException: Remote principal is not trusted
//	at net.jini.jeri.ssl.FilterX509TrustManager.check(FilterX509TrustManager.java:133)
//	at net.jini.jeri.ssl.FilterX509TrustManager.checkServerTrusted(FilterX509TrustManager.java:100)
//	at net.jini.jeri.ssl.ClientAuthManager.checkServerTrusted(ClientAuthManager.java:263)
//	at sun.security.ssl.AbstractTrustManagerWrapper.checkServerTrusted(SSLContextImpl.java:922)
//	at sun.security.ssl.ClientHandshaker.serverCertificate(ClientHandshaker.java:1491)
//	... 37 more
//                ,
//	    new TestNotTrusted("Prefer anonymous, wrong server",
//			       constraints(
//				   serverPrincipals(x500(serverDSA)),
//				   ServerAuthentication.NO),
//			       serverRSASubject,
//			       null)
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
				 Confidentiality.YES)
		},
		array(x500(clientRSA1)),
		requirements(ClientAuthentication.YES,
			     Confidentiality.YES),
		array(x500(clientDSA))),
	    new TestMissingPermissions(
		"Two previous suites",
		new InvocationConstraints[] {
		    requirements(ClientAuthentication.YES,
				 Confidentiality.YES),
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
            Hello proxy = (Hello) exporter.export(server);
            try {
                Subject clientSubject = new Subject();
                    addX500Principal("clientRSA1", clientSubject);
		Subject.doAs(clientSubject,
                    new PrivilegedExceptionAction(){

                        @Override
                        public Object run() throws IOException{
                            Hello stub = proxy;
                            stub = (Hello) ((RemoteMethodControl) stub).setConstraints(
                                new BasicMethodConstraints(
                                    requirements(ServerAuthentication.YES,
                                                 serverPrincipals(x500(serverDSA)))));
                            stub.hello();
                            return null;
                        }
                    });
	    } catch (Throwable e) {
                Throwable ex = e.getCause();
                if (ex instanceof IOException ) return ex;
		return new IOException ("Unable to receive return value of remote call.", e);
	    } finally {
                exporter.unexport(true);
            }
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
