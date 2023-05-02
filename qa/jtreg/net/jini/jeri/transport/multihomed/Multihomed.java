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
/* test removed @ does not run on Java 9 or later due to removal of sun.net.spi.nameservice.NameService
 * @bug 5050069
 * @bug 5087833
 * @summary TcpEndpoint.newRequest.next must try connecting to all
 * addresses for the endpoint's host name before failing, while
 * enforcing the specified security checks.  Also, failure of the
 * security check to reuse a connection to one address must not
 * prevent reuse or creation of a connection to another address for
 * which the caller does have permission.  Finally, connecting to (and
 * reuse of a connection to) an unresolvable host name must also be
 * supported, if supported by the underlying Socket.
 *
 * @build Multihomed
 * @build TestNameServiceDescriptor
 * @build TestNameService
 * @build AbstractSocketFactory
 * @run main/othervm/policy=security.policy -DendpointType=tcp
 *     -DtrustProxy=true -Dsun.net.spi.nameservice.provider.1=test,test
 *     Multihomed
 * @run main/othervm/policy=security.policy -DendpointType=http
 *     -DtrustProxy=true -Dsun.net.spi.nameservice.provider.1=test,test
 *     Multihomed
 * @run main/othervm/policy=security.policy -DendpointType=ssl
 *     -DtrustProxy=true -Dsun.net.spi.nameservice.provider.1=test,test
 *     Multihomed
 * @run main/othervm/policy=security.policy -DendpointType=https
 *     -DtrustProxy=true -Dsun.net.spi.nameservice.provider.1=test,test
 *     Multihomed
 */

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketPermission;
import java.net.UnknownHostException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.SubjectDomainCombiner;
import net.jini.export.Exporter;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.http.HttpServerEndpoint;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import net.jini.jeri.ssl.HttpsServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.AuthenticationPermission;

public class Multihomed {

    private static final String NAME = "foo";	// -> { 1.1.1.1, 2.2.2.2 }
    private static final String ADDR1 = "1.1.1.1";	// -> "foo"
    private static final String ADDR2 = "2.2.2.2";	// -> "foo"
    private static final String NAME_U1 = "bar";	// -> { }
    private static final String NAME_U2 = "baz";	// -> { }

    private static final int SUCCESS = 0;
    private static final int SECURITY_EXCEPTION = 1;
    private static final int IO_EXCEPTION = 2;

    static final String endpointType = System.getProperty("endpointType", "tcp");
    private static boolean isKerberos = false;
    private static Subject subject;
    private static SubjectDomainCombiner sdc;

    private static final Object failureCountLock = new Object();
    private static int failureCount = 0;

    public interface Ping extends Remote {
	void ping() throws RemoteException;
    }

    private static class PingImpl implements Ping {
	PingImpl() { }
	public void ping() { }
    }

    public static void main(String[] args) throws Exception {
	println("\nRegression test for RFE 5050069\n");
	if (endpointType.equals("kerberos")) {
	    isKerberos = true;
	    // attempt login
	    LoginContext loginContext = new LoginContext("onePrincipalServer");
	    loginContext.login();
	    subject = loginContext.getSubject();
	    sdc = new SubjectDomainCombiner(subject);
	}

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	AccessControlContext accA = createAccWith(new Permission[] {
	    new SocketPermission(NAME, "connect"),
	    new SocketPermission(NAME_U1, "connect"),
	});
	AccessControlContext accB = createAccWith(new Permission[] {
	    new SocketPermission(ADDR1, "connect"),
	    new SocketPermission(NAME_U2, "connect"),
	});
	AccessControlContext accC = createAccWith(new Permission[] {
	    new SocketPermission(ADDR2, "connect"),
	});
	AccessControlContext accD = createAccWith(new Permission[] {
	    new SocketPermission(NAME, "resolve"),
	    new SocketPermission(ADDR2, "connect"),
	});

	int port = 2019;		// use separate ports to control reuse
	Ping impl = new PingImpl();	// hold on to impl

	println("===== trying host \"" + NAME + "\": =====");
	println();

	Ping proxy = export(NAME, port++, impl);
	println("== A tries first:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== A tries after A:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== B tries after A:");
	tryWithAcc(accB, proxy, SUCCESS);
	println("== C tries after A:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println("== D tries after A:");
	tryWithAcc(accD, proxy, SUCCESS);
	println();

	proxy = export(NAME, port++, impl);
	println("== B tries first:");
	tryWithAcc(accB, proxy, SUCCESS);
	println("== A tries after B:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== B tries after B:");
	tryWithAcc(accB, proxy, SUCCESS);
	println("== C tries after B:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println("== D tries after B:");
	tryWithAcc(accD, proxy, SUCCESS);
	println();

	proxy = export(NAME, port++, impl);
	println("== C tries first:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println();

	proxy = export(NAME, port++, impl);
	println("== D tries first:");
	tryWithAcc(accD, proxy, SUCCESS);
	println("== A tries after D:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== B tries after D:");
	tryWithAcc(accB, proxy, SUCCESS);
	println("== C tries after D:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println("== D tries after D:");
	tryWithAcc(accD, proxy, SUCCESS);
	println();

	println("=== (disabling first address) ===");
	SF.firstAddressDisabled = true;
	println();

	proxy = export(NAME, port++, impl);
	println("== A tries first:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== A tries after A:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== B tries after A:");
	tryWithAcc(accB, proxy, IO_EXCEPTION);
	println("== C tries after A:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println("== D tries after A:");
	tryWithAcc(accD, proxy, SUCCESS);
	println();

	proxy = export(NAME, port++, impl);
	println("== B tries first:");
	tryWithAcc(accB, proxy, IO_EXCEPTION);
	println();

	proxy = export(NAME, port++, impl);
	println("== C tries first:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println();

	proxy = export(NAME, port++, impl);
	println("== D tries first:");
	tryWithAcc(accD, proxy, SUCCESS);
	println("== A tries after D:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== B tries after D:");
	tryWithAcc(accB, proxy, IO_EXCEPTION);
	println("== C tries after D:");
	tryWithAcc(accC, proxy, SECURITY_EXCEPTION);
	println("== D tries after D:");
	tryWithAcc(accD, proxy, SUCCESS);
	println();

	println("===== trying host \"" + NAME_U1 + "\": =====");
	println();

	proxy = export(NAME_U1, port++, impl);
	println("== A tries first:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== A tries after A:");
	tryWithAcc(accA, proxy, SUCCESS);
	println("== B tries after A:");
	tryWithAcc(accB, proxy, SECURITY_EXCEPTION);
	println();

	proxy = export(NAME_U1, port++, impl);
	println("== B tries first:");
	tryWithAcc(accB, proxy, SECURITY_EXCEPTION);
	println();

	println("===== trying host \"" + NAME_U2 + "\": =====");
	println();

	proxy = export(NAME_U2, port++, impl);
	println("== A tries first:");
	tryWithAcc(accA, proxy, SECURITY_EXCEPTION);
	println();

	proxy = export(NAME_U2, port++, impl);
	println("== B tries first:");
	tryWithAcc(accB, proxy, IO_EXCEPTION);
	println();

	int failures;
	synchronized (failureCountLock) {
	    failures = failureCount;
	}
	println(failures + " failure" + (failures == 1 ? "" : "s"));
	if (failures > 0) {
	    throw new RuntimeException("TEST FAILED");
	}
	println("TEST PASSED");
    }

    private static void print(String s) { System.err.print(s); }
    private static void println() { System.err.println(); }
    private static void println(String s) { System.err.println(s); }

    /**
     * Creates an AccessControlContext with a ProtectionDomain that
     * has only the specified permissions.
     **/
    private static AccessControlContext createAccWith(Permission[] perms) {
	PermissionCollection permissions = new Permissions();
	for (int i = 0; i < perms.length; i++) {
	    permissions.add(perms[i]);
	}
	if (isKerberos) {
	    permissions.add(new AuthenticationPermission("* \"*\"", "connect"));
	}
	AccessControlContext acc = new AccessControlContext(
				    new ProtectionDomain[] {
				    new ProtectionDomain(null, permissions),
	});
	if (isKerberos) {
	    // kerberos needs the current subject
	    return new AccessControlContext(acc, sdc);
	} else {
	    return acc;
	}
    }

    private static Ping export(String host, int port, Ping impl)
	throws ExportException, UnsupportedConstraintException
    {
	ServerEndpoint se = getServerEndpoint(host, port, new SF(), null);
	InvocationLayerFactory ilf =
	    new BasicILFactory(null, null, PingImpl.class.getClassLoader());
	Exporter exporter = new BasicJeriExporter(se, ilf, false, false);
	return (Ping) exporter.export(impl);
    }

    private static void tryWithAcc(AccessControlContext acc,
				   final Ping proxy,
				   int expectation)
    {
	try {
	    AccessController.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws RemoteException {
		    proxy.ping();
		    return null;
		}
	    }, acc);
	    if (expectation == SUCCESS) {
		println("-- success as expected");
	    } else {
		incrementFailureCount();
		println("XX unexpected success");
	    }
	} catch (PrivilegedActionException pae) {
	    RemoteException e = (RemoteException) pae.getCause();
	    if (e instanceof java.rmi.ConnectException ||
		e instanceof java.rmi.ConnectIOException)
	    {
		if (expectation == IO_EXCEPTION) {
		    print("-- I/O exception as expected: ");
		} else {
		    incrementFailureCount();
		    print("XX unexpected I/O exception: ");
		}
	    } else {
		incrementFailureCount();
		print("XX unexpected RemoteException: ");
	    }
	    e.printStackTrace();
	} catch (SecurityException e) {
	    if (expectation == SECURITY_EXCEPTION) {
		print("-- security exception as expected: ");
	    } else {
		incrementFailureCount();
		print("XX unexpected security exception: ");
	    }
	    e.printStackTrace();
	} catch (Exception e) {
	    incrementFailureCount();
	    print("XX unexpected exception: ");
	    e.printStackTrace();
	}
    }

    private static void incrementFailureCount() {
	synchronized (failureCountLock) {
	    failureCount++;
	}
    }

    /**
     * Socket factory that produces sockets that (only) support
     * connecting to the following addresses/hosts:
     *
     * - an address of ADDR1, if firstAddressDisabled is false
     * - an address of ADDR2, always
     * - an unresolved address, if the host name is NAME_U1
     *
     * If an attempt is made to ADDR1 and firstAddressDisabled is
     * true, or an attempt is made to connect to any resolved address
     * other than ADDR1 or ADDR2, then a ConnectException is thrown.
     *
     * If an attempt is made to connect to an unresolved address with
     * host name NAME_U2, then a ConnectException is thrown; if an
     * attempt is made to connect to an unresolved address with a hoat
     * name other than NAME_U1 or NAME_U2, then an
     * UnknownHostException is thrown.
     *
     * Connections are actually made to the loopback address at the
     * specified port, but the security check and the values returned
     * by the getInetAddress and getRemoteSocketAddress methods behave
     * as if the connection is to the caller-specified address/host.
     **/
    private static class SF extends AbstractSocketFactory {

	static volatile boolean firstAddressDisabled = false;

	SF() { };

	public Socket createSocket() {
	    return new S();
	}

	private class S extends Socket {

	    S() { super(); };

	    private InetSocketAddress connectedSocketAddress = null;

	    public void connect(SocketAddress sa) throws IOException {
		connect(sa, 0);
	    }

	    public void connect(SocketAddress sa, int timeout)
		throws IOException
	    {
		InetSocketAddress isa = (InetSocketAddress) sa;
		checkConnect(isa);
		InetAddress addr = isa.getAddress();
		if (addr == null) {
		    String host = isa.getHostName();
		    if (host.equalsIgnoreCase(NAME_U1)) {
			superConnect(isa, timeout);
		    } else if (host.equals(NAME_U2)) {
			try { close(); } catch (IOException e) { }
			throw new ConnectException("can't connect");
		    } else {
			throw new UnknownHostException(host);
		    }
		} else if (addr.getHostAddress().equalsIgnoreCase(ADDR1)) {
		    if (firstAddressDisabled) {
			try { close(); } catch (IOException e) { }
			throw new ConnectException("first address disabled");
		    } else {
			superConnect(isa, timeout);
		    }
		} else if (addr.getHostAddress().equalsIgnoreCase(ADDR2)) {
		    superConnect(isa, timeout);
		} else {
		    try { close(); } catch (IOException e) { }
		    throw new ConnectException("unrecognized address: " +
					       addr);
		}
	    }

	    private void checkConnect(InetSocketAddress isa) {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
		    if (isa.isUnresolved()) {
			sm.checkConnect(isa.getHostName(), isa.getPort());
		    } else {
			sm.checkConnect(isa.getAddress().getHostAddress(),
					isa.getPort());
		    }
		} else {
		    throw new AssertionError();
		}
	    }

	    private void superConnect(final InetSocketAddress isa,
				      final int timeout)
		throws IOException
	    {
		try {
		    AccessController.doPrivileged(
			new PrivilegedExceptionAction() {
			    public Object run() throws IOException {
				S.super.connect(
				    new InetSocketAddress("",	// localhost
							  isa.getPort()),
				    timeout);
				return null;
			    }
			});
		} catch (PrivilegedActionException e) {
		    throw (IOException) e.getCause();
		}
		connectedSocketAddress = isa;
	    }

	    public SocketAddress getRemoteSocketAddress() {
		if (!isConnected()) {
		    return null;
		} else {
		    return connectedSocketAddress;
		}
	    }

	    public InetAddress getInetAddress() {
		if (!isConnected()) {
		    return null;
		} else {
		    return connectedSocketAddress.getAddress();
		}
	    }

	    public String toString() {
		if (!isConnected()) {
		    return "Socket[unconnected]";
		} else {
		    return
			"Socket[addr=" + getRemoteSocketAddress() +
			",localport=" + getLocalPort() + "]";
		}
	    }
	}
    }

    private static ServerEndpoint getServerEndpoint(String host,
						    int port,
						    SocketFactory sf,
						    ServerSocketFactory ssf)
	throws UnsupportedConstraintException
    {
	System.err.println("Endpoint type: " + endpointType);
	if (endpointType.equals("tcp")) {
	    return TcpServerEndpoint.getInstance(host, port, sf, ssf);
	} else if (endpointType.equals("http")) {
	    return HttpServerEndpoint.getInstance(host, port, sf, ssf);
	} else if (endpointType.equals("ssl")) {
	    return SslServerEndpoint.getInstance(host, port, sf, ssf);
	} else if (endpointType.equals("https")) {
	    return HttpsServerEndpoint.getInstance(host, port, sf, ssf);
	} else if (endpointType.equals("kerberos")) {
	    return KerberosServerEndpoint.getInstance(subject, null,
							  host, port, sf, ssf);
	} else {
	    throw new RuntimeException(
		"TEST FAILED: unsupported endpoint type: " + endpointType);
	}
    }
}
