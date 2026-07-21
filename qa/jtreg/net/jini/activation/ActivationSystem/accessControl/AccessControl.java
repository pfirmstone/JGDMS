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
 * @bug 4302502
 * 
 * @summary test basic permission-based access control for a secure rmid
 * @author Bob Scheifler
 * 
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID
 * @build AccessControl
 * @run main/othervm/timeout=240/policy=security.policy
 * -Djava.protocol.handler.pkgs=net.jini.url AccessControl
 */
import java.io.File;
import java.rmi.ConnectIOException;
import java.rmi.MarshalledObject;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationDesc;
import net.jini.activation.arg.ActivationGroupDesc.CommandEnvironment;
import net.jini.activation.arg.ActivationGroupDesc;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.ActivationDescImpl;
import net.jini.activation.ActivationGroupDescImpl;
import net.jini.activation.ActivationGroupDescImpl.CommandEnvironmentImpl;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.arg.ActivationSystem;
import java.rmi.server.UID;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.x500.X500Principal;
import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.constraint.*;
import net.jini.core.constraint.*;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.*;

public class AccessControl {
    /** ActivationSystem stub */
    static ActivationSystem sys;
    /** id of created group */
    static ActivationGroupID gid;
    /** Server subject */
    static Subject server;
    static String testsrc = TestParams.testSrc + File.separator;

    static Collection trustCtx = Collections.singleton(
		new BasicMethodConstraints(
		    new InvocationConstraints(
			new InvocationConstraint[]{
			    Integrity.YES,
			    ServerAuthentication.YES,
			    new ServerMinPrincipal(
				new X500Principal("CN=server"))},
			null)));

    public interface Service extends Remote {
	UID inactivate() throws RemoteException;
    }

    public static class Impl implements Service, ProxyAccessor {
	private static ActivationID myAid;
	private static Exporter exp;
	/**
	 * Static so we can tell if the object gets reactivated in the
	 * same VM or in a new VM.
	 */
	private static UID uid = new UID();
	
	private final Remote proxy;

	Impl(ActivationID aid, MarshalledObject mo)
	    throws Exception
	{
	    myAid = aid;
	    Exporter basicExporter =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), false, true);
	    exp = new ActivationExporter(aid, basicExporter);
	    proxy = exp.export(this);
	}

	public Object getProxy() {
	    return proxy;
	}

	private static class Inactivate extends Thread {
	    public void run() {
		try {
		    for (int i = 5; --i >= 0; ) {
			if (ActivationGroup.inactive(myAid, exp)) {
			    return;
			}
			Thread.sleep(500);
		    }
		} catch (Exception e) {
		    throw new RuntimeException("failed to go inactive", e);
		}
		throw new RuntimeException("failed to go inactive");
	    }
	}

	public UID inactivate() {
	    new Inactivate().start();
	    return uid;
	}
    }

    /**
     * Log in with the given login configuration name and return the subject
     * set read only.
     */
    static Subject getSubject(String name) throws Exception {
	LoginContext ctx = new LoginContext(name);
	ctx.login();
	Subject s = ctx.getSubject();
	s.setReadOnly();
	return s;
    }

    /**
     * Check Client1 permissions, and indirectly check Server permissions
     * for calls between the activation group and rmid.
     */
    public static class AsClient1 implements PrivilegedExceptionAction {
	public Object run() throws Exception {
	    // Security.verifyObjectTrust(obj, loader, ctx): loader==null
	    // resolves to Thread.currentThread().getContextClassLoader(),
	    // used both to find TrustVerifier services and (in
	    // BasicJeriTrustVerifier.hasTrustedClassLoader) to require that
	    // the *caller's* loader descend from (or equal) the *proxy's*
	    // defining loader. sys/aid are obtained over a real out-of-
	    // process RMI codebase download (rmid is a genuinely separate
	    // JVM here, unlike most single-process qa/jtreg trust tests),
	    // so their proxy class is defined by a codebase loader that is
	    // a *child* of this test's own (system/app) loader -- the
	    // reverse of what covers() requires when loader==null, however
	    // the context class loader is set (setContextClassLoader in
	    // main() alone did not resolve it; confirmed via
	    // net.jini.jeri.BasicJeriTrustVerifier FINEST logging: "Sub
	    // ClassLoader was not a child ClassLoader of sup"). Passing the
	    // proxy's own loader explicitly is a documented, supported
	    // usage of the API (see its javadoc) and matches how a caller
	    // that received the proxy via RMI would naturally know its
	    // loader, without altering the trust-verification logic itself.
	    Security.verifyObjectTrust(sys, sys.getClass().getClassLoader(), trustCtx);
	    Properties props = new Properties();
	    props.put("javax.net.ssl.trustStore", testsrc + "keystore");
	    // au.net.zeus.rmi.tls.FilterX509TrustManager.getTrustManager()
	    // loads javax.net.ssl.trustStore as a KeyStore using
	    // javax.net.ssl.trustStorePassword (empty char[] if unset). The
	    // keystore keystore.sh generates is PKCS12 (modern keytool
	    // default), which -- unlike the old JKS format -- cryptographically
	    // requires the correct password just to read it; with the wrong
	    // password KeyStore.load() fails and is silently swallowed
	    // (logged at CONFIG only), leaving an empty KeyStore and hence
	    // zero trust anchors ("the trustAnchors parameter must be
	    // non-empty"). Must match keystore.sh's -storepass/-keypass.
	    props.put("javax.net.ssl.trustStorePassword", "keypass");
	    props.put("java.security.policy",
		      testsrc + "group.security.policy");
	    props.put("java.security.auth.login.config", testsrc + "login");
	    // can call registerGroup
	    gid = sys.registerGroup(new ActivationGroupDescImpl(props, null));
	    // can call getActivationGroupDesc
	    ActivationGroupDesc gdesc = sys.getActivationGroupDesc(gid);
	    // cannot call setActivationGroupDesc
	    try {
		sys.setActivationGroupDesc(gid, gdesc);
		throw new RuntimeException(
				   "client1 able to setActivationGroupDesc");
	    } catch (SecurityException e) {
	    }
	    // can call registerObject
	    ActivationID aid =
		sys.registerObject(new ActivationDescImpl(gid,
						      Impl.class.getName(),
						      null, null));
	    // cannot call getProxyVerifier
	    try {
		Security.verifyObjectTrust(aid, aid.getClass().getClassLoader(), trustCtx);
		throw new RuntimeException(
				   "client1 able to verifyObjectTrust");
	    } catch (SecurityException e) {
	    }
	    // can call getActivationDesc
	    ActivationDesc adesc = sys.getActivationDesc(aid);
	    // cannot call setActivationDesc
	    try {
		sys.setActivationDesc(aid, adesc);
		throw new RuntimeException(
				   "client1 able to setActivationDesc");
	    } catch (SecurityException e) {
	    }
	    // can call activate
	    Service svc = (Service) aid.activate(false);
	    // make the service (and hence the group) go inactive
	    UID uid1 = svc.inactivate();
	    // give the service and group time to go inactive
	    Thread.sleep(15000);
	    // check indirectly that group actually went away the first time
	    UID uid2 = svc.inactivate();
	    if (uid1.equals(uid2)) {
		throw new RuntimeException("group did not go inactive");
	    }
	    // can call unregisterObject
	    sys.unregisterObject(aid);
	    // cannot call activeGroup
	    try {
		sys.activeGroup(gid, null, 0);
		throw new RuntimeException("client1 able to activeGroup");
	    } catch (SecurityException e) {
	    }
	    // cannot call unregisterGroup
	    try {
		sys.unregisterGroup(gid);
		throw new RuntimeException("client1 able to unregisterGroup");
	    } catch (SecurityException e) {
	    }
	    // cannot call shutdown
	    try {
		sys.shutdown();
		throw new RuntimeException("client1 able to shutdown");
	    } catch (SecurityException e) {
	    }
	    return null;
	}
    }

    /**
     * Check Client2 permissions.
     */
    public static class AsClient2 implements PrivilegedExceptionAction {
	public Object run() throws Exception {
	    Security.verifyObjectTrust(sys, sys.getClass().getClassLoader(), trustCtx);
	    // cannot call registerGroup
	    try {
		sys.registerGroup(new ActivationGroupDescImpl(null, null));
		throw new RuntimeException("client2 able to registerGroup");
	    } catch (SecurityException e) {
	    }
	    // cannot call getActivationGroupDesc
	    try {
		sys.getActivationGroupDesc(gid);
		throw new RuntimeException(
				   "client2 able to getActivationGroupDesc");
	    } catch (SecurityException e) {
	    }
	    CommandEnvironment env =
		new CommandEnvironmentImpl("foobar", new String[]{"-baz"});
	    // can set java program to "foobar" and option "-baz"
	    sys.setActivationGroupDesc(gid,
				       new ActivationGroupDescImpl(null, env));
	    env = new CommandEnvironmentImpl("baz", null);
	    // cannot set java program to "baz"
	    try {
		sys.setActivationGroupDesc(gid,
					   new ActivationGroupDescImpl(null,
								   env));
		throw new RuntimeException(
		   "client2 able to setActivationGroupDesc with program baz");
	    } catch (SecurityException e) {
	    }
	    // cannot set java program to "foobar" and option "-foobar"
	    env = new CommandEnvironmentImpl("foobar", new String[]{"-foobar"});
	    try {
		sys.setActivationGroupDesc(gid,
					   new ActivationGroupDescImpl(null, env));
		throw new RuntimeException(
		 "client2 able to setActivationGroupDesc with option -foobar");
	    } catch (SecurityException e) {
	    }
	    // can call setActivationGroupDesc to set to default
	    sys.setActivationGroupDesc(gid,
				       new ActivationGroupDescImpl(null, null));
	    // can call registerObject
	    ActivationID aid =
		sys.registerObject(new ActivationDescImpl(gid, "Foo", null, null));
	    // cannot call activate
	    try {
		aid.activate(false);
		throw new RuntimeException("client2 able to activate");
	    } catch (SecurityException e) {
	    }
	    // can call getProxyVerifier
	    Security.verifyObjectTrust(aid, aid.getClass().getClassLoader(), trustCtx);
	    // can call getActivationDesc
	    ActivationDesc adesc = sys.getActivationDesc(aid);
	    // can call setActivationDesc
	    sys.setActivationDesc(aid, adesc);
	    // can call unregisterObject
	    sys.unregisterObject(aid);
	    // cannot call activeGroup
	    try {
		sys.activeGroup(gid, null, 0);
		throw new RuntimeException("client2 able to activeGroup");
	    } catch (SecurityException e) {
	    }
	    // cannot call unregisterGroup
	    try {
		sys.unregisterGroup(gid);
		throw new RuntimeException("client2 able to unregisterGroup");
	    } catch (SecurityException e) {
	    }
	    // cannot call shutdown
	    try {
		sys.shutdown();
		throw new RuntimeException("client2 able to shutdown");
	    } catch (SecurityException e) {
	    }
	    return null;
	}
    }

    /**
     * Check that anonymous cannot call anything.
     */
    public static class AsNobody implements PrivilegedExceptionAction {
	public Object run() throws Exception {
	    try {
		Security.verifyObjectTrust(sys, sys.getClass().getClassLoader(), trustCtx);
		throw new RuntimeException("nobody able to verify trust");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.registerGroup(new ActivationGroupDescImpl(null, null));
		throw new RuntimeException("nobody able to registerGroup");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.getActivationGroupDesc(gid);
		throw new RuntimeException(
				   "nobody able to getActivationGroupDesc");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.setActivationGroupDesc(gid, new ActivationGroupDescImpl(null,
									null));
		throw new RuntimeException(
				   "nobody able to setActivationGroupDesc");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.registerObject(new ActivationDescImpl(gid, "Foo", null, null));
		throw new RuntimeException("nobody able to registerObject");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.getActivationDesc(null);
		throw new RuntimeException(
				   "nobody able to getActivationDesc");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.unregisterObject(null);
		throw new RuntimeException(
				   "nobody able to unregisterObject");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.activeGroup(gid, null, 0);
		throw new RuntimeException("nobody able to activeGroup");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.unregisterGroup(gid);
		throw new RuntimeException("nobody able to unregisterGroup");
	    } catch (ConnectIOException e) {
	    }
	    try {
		sys.shutdown();
		throw new RuntimeException("nobody able to shutdown");
	    } catch (ConnectIOException e) {
	    }
	    return null;
	}
    }

    /**
     * Check that Server cannot call anything.
     */
    public static class AsServer implements PrivilegedExceptionAction {
	public Object run() throws Exception {
	    Security.verifyObjectTrust(sys, sys.getClass().getClassLoader(), trustCtx);
	    try {
		sys.registerGroup(new ActivationGroupDescImpl(null, null));
		throw new RuntimeException("server able to registerGroup");
	    } catch (SecurityException e) {
	    }
	    try {
		sys.getActivationGroupDesc(gid);
		throw new RuntimeException(
				   "server able to getActivationGroupDesc");
	    } catch (SecurityException e) {
	    }
	    try {
		sys.setActivationGroupDesc(gid, new ActivationGroupDescImpl(null,
									null));
		throw new RuntimeException(
				   "server able to setActivationGroupDesc");
	    } catch (SecurityException e) {
	    }
	    try {
		sys.registerObject(new ActivationDescImpl(gid, "Foo", null, null));
		throw new RuntimeException("server able to registerObject");
	    } catch (SecurityException e) {
	    }
	    try {
		sys.getActivationDesc(null);
		throw new RuntimeException(
				   "server able to getActivationDesc");
	    } catch (SecurityException e) {
	    }
	    try {
		sys.unregisterObject(null);
		throw new RuntimeException(
				   "server able to unregisterObject");
	    } catch (SecurityException e) {
	    }
	    try {
		sys.unregisterGroup(gid);
		throw new RuntimeException("server able to unregisterGroup");
	    } catch (SecurityException e) {
	    }
	    return null;
	}
    }

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	// Security.verifyObjectTrust(obj, null, ctx) resolves a null loader
	// to Thread.currentThread().getContextClassLoader() (see
	// net.jini.jeri.BasicJeriTrustVerifier.hasTrustedClassLoader /
	// covers()); jtreg's MainWrapper doesn't set it to anything in
	// particular. Set it explicitly to this test's own loader, per the
	// documented convention (see qa/jtreg/net/jini/security/Security/
	// contextClassLoader/Test.java, which does the same before its own
	// null-loader verifyObjectTrust call).
	Thread.currentThread().setContextClassLoader(
	    AccessControl.class.getClassLoader());
	System.setProperty("java.security.auth.login.config",
			   testsrc + "login");
	System.setProperty("javax.net.ssl.trustStore",
			   testsrc + "keystore");
	// Must match keystore.sh's -storepass/-keypass -- see the longer
	// comment on the same property in AsClient1.run() above.
	System.setProperty("javax.net.ssl.trustStorePassword", "keypass");
	server = getSubject("server");
	Subject client1 = getSubject("client1");
	Subject client2 = getSubject("client2");
	RMID.removeLog();
	final RMID rmid = RMID.createRMID(true);
	rmid.addOptions(new String[]{"-Djava.security.auth.login.config=" +
				     testsrc + "login",
				     "-Djavax.net.ssl.trustStorePassword=keypass",
				     "-Djavax.net.ssl.trustStore=" +
				     testsrc + "keystore"});
	rmid.slowStart();
	try {
	    // ActivationGroup.getSystem() uses au.net.zeus.rmi.tls.
	    // TlsRMIClientSocketFactory to contact the (secure) Registry,
	    // whose Subject resolution (Utilities.getTlsSubject()) checks
	    // Subject.current() (a ScopedValue, populated by Subject.callAs)
	    // before falling back to the legacy ACC-derived Subject via
	    // Subject.getSubject(AccessController.getContext()) -- and on
	    // this JDK, Subject.doAsPrivileged() alone does not populate
	    // Subject.current(), so plain doAsPrivileged still resolves to
	    // "no TLS-capable Subject" here. Mirror the pattern
	    // org.apache.river.phoenix.Activation.main(String[],boolean,
	    // PhoenixStarter) already uses for the same reason (see its
	    // comment above the Subject.callAs(...) call): nest
	    // doAsPrivileged inside callAs so both the ScopedValue and the
	    // legacy ACC mechanisms see the Subject. (The later
	    // Subject.doAsPrivileged(client1/client2/null/server, ...)
	    // calls below go through JERI's net.jini.jeri.ssl SslEndpoint,
	    // a separate, ACC-based mechanism unaffected by this; only this
	    // au.net.zeus.rmi.tls-based Registry lookup needs it.)
	    sys = (ActivationSystem) Subject.callAs(server,
		new java.util.concurrent.Callable<Object>() {
		    public Object call() throws Exception {
			return Subject.doAsPrivileged(server,
			    new PrivilegedExceptionAction() {
				public Object run() throws Exception {
				    return ActivationGroup.getSystem();
				}
			    }, null);
		    }
		});
	    Subject.doAsPrivileged(client1, new AsClient1(), null);
	    Subject.doAsPrivileged(client2, new AsClient2(), null);
	    Subject.doAsPrivileged(null, new AsNobody(), null);
	    Subject.doAsPrivileged(server, new AsServer(), null);
	} finally {
	    Subject.doAsPrivileged(server, new PrivilegedAction() {
		public Object run() {
		    ActivationLibrary.rmidCleanup(rmid);
		    return null;
		}
	    }, null);
	}
    }
}
