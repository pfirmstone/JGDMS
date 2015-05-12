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

import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.context.ClientHost;
import net.jini.io.context.ClientSubject;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.Endpoint;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.jeri.kerberos.KerberosEndpoint;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import net.jini.security.AuthenticationPermission;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketPermission;
import java.net.SocketTimeoutException;
import java.security.AccessController;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.net.SocketFactory;
import javax.net.ServerSocketFactory;
import javax.security.auth.AuthPermission;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.ServicePermission;
import javax.security.auth.Subject;

public class TestEndpoints extends TestUtilities {

    /** 
     * All tests for this endpoint test instance, except listen
     * endpoint equals tests.
     */
    public static Test[] tests;

    private static String clientLoginEntry = "testClient";
    private static Subject clientSubject;
    private static KerberosEndpoint clientEndpoint;

    private static String onePrincipalServerLoginEntry = "onePrincipalServer";
    private static String twoPrincipalServerLoginEntry = "twoPrincipalServer";
    private static String serverLoginEntry = "testServer";

    /* following two server subjects are used by constructor tests */
    private static Subject onePrincipalServerSubject;
    private static Subject twoPrincipalServerSubject;

    private static Subject serverSubject;
    private static KerberosPrincipal defaultServerPrincipal;
    private static KerberosServerEndpoint serverEndpoint;
    private static KerberosServerEndpoint serverEndpointNoCred;
    private static ServerEndpoint.ListenHandle serverListenHandle;

    private static Map checkerMap;

    private static Permission[] defaultPermsNoSocketPerm;
    private static Permission[] defaultPermsWithSocketPerm;
    private static Random rand;
    private static int nextID;   // used to generate unique id

    /**
     * Maximum number of milliseconds the server side will wait before
     * the incoming request.  Default to 0.
     */
    private static int maxServerSleepTime = Integer.getInteger(
	"maxServerSleepTime", 0).intValue();

    static KerberosPrincipal[] cps = new KerberosPrincipal[] {
	new KerberosPrincipal("testClient1"),
	new KerberosPrincipal("testClient2"),
	new KerberosPrincipal("testClient3"),
	new KerberosPrincipal("testClient4")
    };

    private static KerberosPrincipal[] sps = new KerberosPrincipal[] {
	new KerberosPrincipal("testServer1"),
	new KerberosPrincipal("testServer2"),
	new KerberosPrincipal("testServer3"),
	new KerberosPrincipal("testServer4")
    };

    static {
	init();
    }

    public static void cleanup() {
	if (serverListenHandle != null) {
	    debugPrint(30, "Closing listener: " + serverListenHandle);
	    serverListenHandle.close();
	}
    }

    /** Runs all Endpoint tests */
    public static void main(String[] args) {
	try {

	    ArrayList allTests = new ArrayList(Arrays.asList(tests));
	    allTests.add(TestListenEndpointEquals.localtests);
	    test(allTests);
	} finally {
	    cleanup();
	}
    }

    //-----------------------------------
    //          private methods
    //-----------------------------------

    private static void init() {

	try {
	    // disable connection reuse across tests
	    System.setProperty("org.apache.river.jeri.connectionTimeout", "1");

	    clientSubject = getLoginSubject(clientLoginEntry);

	    onePrincipalServerSubject =
		getLoginSubject(onePrincipalServerLoginEntry);
	    twoPrincipalServerSubject =
		getLoginSubject(twoPrincipalServerLoginEntry);
	    serverSubject = getLoginSubject(serverLoginEntry);

	    defaultServerPrincipal = sps[1];
	    serverEndpoint = KerberosServerEndpoint.getInstance(
                serverSubject, defaultServerPrincipal, null, 0, null, null);
	    serverEndpointNoCred = KerberosServerEndpoint.getInstance(
                new Subject(), defaultServerPrincipal, null, 0, null, null);
	    clientEndpoint =
		(KerberosEndpoint) serverEndpoint.enumerateListenEndpoints(
		    new ServerEndpoint.ListenContext() {
			    public ServerEndpoint.ListenCookie
			    addListenEndpoint(
				ServerEndpoint.ListenEndpoint listenEndpoint)
				throws IOException
			    {
				listenEndpoint.checkPermissions();
				serverListenHandle = listenEndpoint.listen(
				    new RequestDispatcher() {
					    public void dispatch(
						InboundRequest request)
					    {
						try {
						    processReq(request);
						} catch (IOException e) {
						    throw new RuntimeException(
							"IOException thrown " +
							"while handling " +
							"inbound request", e);
						}
					    }
					});
				return serverListenHandle.getCookie();
			    }
			});
	    checkerMap = Collections.synchronizedMap(new HashMap());
	} catch (Exception e) {
	    throw new RuntimeException("endpoints init failure", e);
	}

	String name = TestPrincipal.class.getName() + " \"local\" peer " +
	    TestPrincipal.class.getName() + " \"peer\"";
	String actions = "listen, accept, delegate";
	defaultPermsNoSocketPerm = new Permission[] {
	    new TestPermission("testPermission"),
	    new AuthenticationPermission(name, actions),
	    new AuthPermission("doAs"), // needed for newRequest()
	    // so provider will throw detailed exception instead of generic one
	    new AuthPermission("getSubject")
	};
	defaultPermsWithSocketPerm = new Permission[] {
	    new TestPermission("testPermission"),
	    new AuthenticationPermission(name, actions),
	    new AuthPermission("doAs"), // needed for newRequest()
	    // so provider will throw detailed exception instead of generic one
	    new AuthPermission("getSubject"),
	    new SocketPermission("*:1024-", "accept,connect,listen")
	};
	rand = new Random();
	nextID = 1; // id starts from 1

	initTests();
    }

    private static void processReq(InboundRequest inbound)
	throws IOException
    {
	InputStream is = inbound.getRequestInputStream();
	DataInputStream dis = new DataInputStream(is);
	String clientMsg = dis.readUTF();
	ServerSideChecker checker =
	    (ServerSideChecker) checkerMap.get(clientMsg);
	if (checker == null)
	    return; // no checker, abort here
	if (maxServerSleepTime > 0) {
	    try {
		// wait some random time for concurrent testing
		long time = rand.nextInt() % maxServerSleepTime;
		if (time < 0)
		    time = -time;
		Thread.sleep(time);
	    } catch (InterruptedException e) {}
	}
	String failMsg = checker.checkServer(inbound);
	OutputStream os = inbound.getResponseOutputStream();
	DataOutputStream dos = new DataOutputStream(os);
	if (failMsg == null) {
	    dos.writeUTF(checker.getReplyMsg());
	} else {
	    dos.writeUTF(failMsg);
	}
	writeData(dos, checker.getReplyData());
	dos.close();
	checker.checkDone();
    }

    private static void writeData(DataOutputStream dos, Object data)
	throws IOException
    {
	if (data instanceof byte[]) {
	    byte[] bytes = (byte[]) data;
	    dos.writeInt(bytes.length);
	    dos.write(bytes);
	} else {
	    byte[][] arr = (byte[][]) data;
	    int len = 0;
	    for (int i = 0; i < arr.length; i++) {
		len += arr[i].length;
	    }
	    int nextFlush = 5;
	    int count = 0;
	    dos.writeInt(len);
	    for (int i = 0; i < arr.length; i++, nextFlush--) {
		dos.write(arr[i]);
		if (++count == nextFlush) {
		    dos.flush();
		    if (--nextFlush == 0)
			nextFlush = 5;
		    count = 0;
		}
	    }
	}
    }

    private static boolean dataEquals(Object data, byte[] bytes) {
	if (data instanceof byte[]) {
	    return Arrays.equals((byte[]) data, bytes);
	} else {
	    byte[][] arr = (byte[][]) data;
	    int idx = 0;
	    byte[] dataBytes = new byte[bytes.length];
	    for (int i = 0; i < arr.length; i++) {
		if (idx + arr[i].length > bytes.length)
		    return false;
		System.arraycopy(arr[i], 0, dataBytes, idx, arr[i].length);
		idx += arr[i].length;
	    }
	    if (idx != bytes.length)
		return false;
	    return Arrays.equals(dataBytes, bytes);
	}
    }

    private static byte[] getBytes(int len) {
	byte[] data = new byte[len];
	for (int i = 0; i < len; i++) {
	    data[i] = (byte) i;
	}
	return data;
    }

    private static byte[][] getBytes(int narrs, int len) {
	byte[][] data = new byte[narrs][];
	for (int i = 0; i < narrs; i++) {
	    data[i] = getBytes(len);
	}
	return data;
    }

    private static synchronized int nextID() {
	return nextID++;
    }

    private static Object runWithPerms(
	final Action act, final Permission[] perms) throws Exception
    {
	try {
	    return AccessController.doPrivileged(
		new PrivilegedExceptionAction() {
			public Object run() throws Exception {
			    return act.run();
			}
		    }, getContext(perms));
	} catch (PrivilegedActionException pae) {
	    throw pae.getException();
	}
    }

    /* Add in all endpoint tests */
    private static void initTests() {

	Subject testSubj = new Subject();
	testSubj.getPrincipals().add(new TestPrincipal("testPrincipal"));
	testSubj.setReadOnly();

	byte[] oneShortArray1 = getBytes(100);
	byte[] oneShortArray2 = getBytes(100);
	byte[][] multiShortArray1 = getBytes(30, 100);
	byte[][] multiShortArray2 = getBytes(20, 100);
	// just < KerberosUtil.ConnectionOutputStream.bufsize + jeri header
	byte[] oneLongArray1 = getBytes(8176);
	// just > KerberosUtil.ConnectionOutputStream.bufsize + jeri header
	byte[] oneLongArray2 = getBytes(8192);
	byte[] oneLongArray3 = getBytes(30000);
	byte[] oneLongArray4 = getBytes(20000);
	byte[][] multiLongArray1 = getBytes(65, 1000);
	byte[][] multiLongArray2 = getBytes(90, 1000);

	Permission[] permsNoAuth = new Permission[] {
	    new AuthPermission("doAs"),
	    new AuthPermission("getSubject"),
	    new SocketPermission("*:1024-", "accept,connect,listen"),
	};

	Permission[] sockPermOnly = new Permission[] {
	    new SocketPermission("*:1024-", "accept,connect,listen"),
	};

	Permission[] authPermOnly = new Permission[] {
	    new AuthenticationPermission(
		Collections.singleton(defaultServerPrincipal), null, "listen")
	};

	/*
	Permission[] withTestClient3AuthPerms = new Permission[] {
	    new AuthPermission("doAs"), // needed for newRequest()
	    // so provider will throw detailed exception instead of generic one
	    new AuthPermission("getSubject"),
	    new SocketPermission("*:1024-", "accept,connect,listen"),
	    new AuthenticationPermission("testClient3", "connect")};
	*/

	InvocationConstraints emptyConstraints =
	    InvocationConstraints.EMPTY;

	InvocationConstraints integrityRequiredConstraints =
	    new InvocationConstraints(Integrity.YES, null);

	InvocationConstraints integrityPreferredConstraints =
	    new InvocationConstraints(null, Integrity.YES);

	tests = new Test[] {
	    /* -- preference tests -- */
	    new EndpointTest( // test preference, delegation necessary
		"PreferenceConstraintTestDelegationPreferred",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]})},
		    new InvocationConstraint[] {
			Integrity.NO, // will be ignored
			new ClientMinPrincipal(cps[1]),
			Delegation.YES}),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, true),
	    new EndpointTest( // test preference, delegation not necessary
		"PreferenceConstraintTestDelegationNotPreferred",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]})},
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[2]), // not necessary
			Integrity.YES,
			Confidentiality.YES}), // preferred, but not necessary
		integrityPreferredConstraints, /* clientUnfulfilledCs */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, true, false),
	    /* -- permission tests -- */
	    new EndpointTest(
		"ClientPermissionNoSocketPermTest",
		new Permission[0],   /* clientPerms = default + auth */
		false,
		null,         /* serverPerms, default to the right set */
		true,
		defaultPermsNoSocketPerm),
	    new EndpointTest(
		"ServerPermissionNoSocketPermTest",
		null,         /* clientPerms, default to the right set */
		true,
		new Permission[0],   /* serverPerms = default + auth */
		false,
		defaultPermsNoSocketPerm),
	    new EndpointTest(
		"ClientPermissionNoAuthPermTest",
		defaultPermsWithSocketPerm, /* clientPerms */
		false,
		null,         /* serverPerms, default to the right set */
		true,
		null),
	    new EndpointTest(
		"ServerPermissionNoAuthPermtest",
		null,         /* clientPerms, default to the right set */
		true,
		defaultPermsWithSocketPerm, /* serverPerms */
		false,
		null),
	    /* -- connection time constraint tests -- */
	    new EndpointTest(
		"ConnectionAbsoluteTimeConstraintClientPassTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionAbsoluteTime(
			    System.currentTimeMillis() + 1000 * 1800)},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionAbsoluteTimeConstraintAlternativesClientPassTest",
		new InvocationConstraints(
		    new ConstraintAlternatives(
			new InvocationConstraint[] {
			    new ConnectionAbsoluteTime(
				System.currentTimeMillis() + 1000 * 1800),
			    new ConnectionAbsoluteTime(3)}),
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionAbsoluteTimeConstraintClientFailTest1",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionAbsoluteTime(3)},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionAbsoluteTimeConstraintClientFailTest2",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionAbsoluteTime(
			    System.currentTimeMillis() + 1000 * 1800),
			new ConnectionAbsoluteTime(3)},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionAbsoluteTimeConstraintServerPassTest1",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionAbsoluteTime(3)},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionAbsoluteTimeConstraintServerPassTest2",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionAbsoluteTime(
			    System.currentTimeMillis() + 1000 * 1800)},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionRelativeTimeConstraintClientPassTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionRelativeTime(1000 * 1800)},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ConnectionRelativeTimeConstraintServerFailTest",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConnectionRelativeTime(1000 * 1800)},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null, null, false, false),
	    /* -- authentication tests -- */
	    new EndpointTest(
		"AuthenticatonYESTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {ClientAuthentication.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledCs */
		new InvocationConstraints(
		    new InvocationConstraint[] {ServerAuthentication.YES},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"AuthenticationNOTest1",
		new InvocationConstraints(
		    new InvocationConstraint[] {ClientAuthentication.NO},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"AuthenticationNOTest2",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {ServerAuthentication.NO},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		null, null, false, false),
	    /* -- unfulfilled constraints tests -- */
	    new EndpointTest(
		"UnfulfilledConstraintClientIntegrityRequiredTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			ClientAuthentication.YES, Integrity.YES},
		    null),
		integrityRequiredConstraints, /* clientUnfulfilledCs */
		new InvocationConstraints(
		    new InvocationConstraint[] {ServerAuthentication.YES},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"UnfulfilledConstraintClientIntegrityPreferredTest",
		new InvocationConstraints(
		    null, new InvocationConstraint[] {Integrity.YES}),
		integrityPreferredConstraints, /* clientUnfulfilledCs */
		new InvocationConstraints(
		    new InvocationConstraint[] {ServerAuthentication.YES},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"UnfulfilledConstraintServerIntegrityRequiredTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {ClientAuthentication.YES},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {Integrity.YES},
		    null),
		integrityRequiredConstraints, /* clientUnfulfilledCs */
		null, null, false, false),
	    new EndpointTest(
		"UnfulfilledConstraintServerIntegrityPreferredTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {ClientAuthentication.YES},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		new InvocationConstraints(
		    null, new InvocationConstraint[] {Integrity.YES}),
		integrityPreferredConstraints, /* clientUnfulfilledCs */
		null, null, false, false),
	    /* -- principal constraint tests -- */
	    new EndpointTest(
		"ClientPrincipalConstraintValidMinMaxTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[1], cps[2], cps[3]})},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, false),
	    new EndpointTest(
		"ClientPrincipalConstraintInvalidMinMaxTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[2]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[1], cps[3]})},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, false),
	    new EndpointTest(
		"ClientPrincipalConstraintNonKerberosPrincipalTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(
			    new TestPrincipal("CPCT3testPrincipal"))},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, false),
	    new EndpointTest(
		"ServerPrincipalConstraintValidServerMinTest",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ServerPrincipalConstraintInvalidServerMinTest",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ServerMinPrincipal(sps[0])},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		null, null, false, false),
	    new EndpointTest(
		"ServerPrincipalConstraintNonKerberosServerMinTest",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(
			    new TestPrincipal("SPCT3testPrincipal"))},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		null, null, false, false),
	    new EndpointTest(
		"ClientMaxPrincipalConstraintClientSideTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMaxPrincipal(
			    new Principal[] {cps[1], cps[2], cps[3]}),
			new ClientMaxPrincipal(
			    new Principal[] {cps[1], cps[2]}),
			new ClientMaxPrincipal(cps[3])},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		null, null, false, false),
	    new EndpointTest(
		"ClientMaxPrincipalConstraintServerSideTest",
		null,
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMaxPrincipal(
			    new Principal[] {cps[1], cps[2], cps[3]}),
			new ClientMaxPrincipal(
			    new Principal[] {cps[1], cps[3]}),
			new ClientMaxPrincipal(cps[2])},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		null, null, false, false),
	    /* -- integrity tests -- */
	    new EndpointTest(
		"IntegrityYESRequiredTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]}),
			Integrity.YES},
		    null),
		integrityRequiredConstraints, /* clientUnfulfilledConstrain */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Integrity.YES},
		    null),
		integrityRequiredConstraints, /* serverUnfulfilledConstrain */
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		false, false),
	    new EndpointTest(
		"IntegrityYESPreferredTest",
		new InvocationConstraints(
		    null,
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]}),
			Integrity.YES}),
		integrityPreferredConstraints, /* clientUnfulfilledConstrain */
		new InvocationConstraints(
		    null,
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Integrity.YES}),
		integrityPreferredConstraints, /* serverUnfulfilledConstrain */
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		false, false),
	    new EndpointTest(
		"ClientIntegrityNOTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]}),
			Integrity.NO},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, false),
	    new EndpointTest(
		"ServerIntegrityNOTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1])}, null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Integrity.NO},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		false, false),
	    /* -- constraint alternatives tests -- */
	    new EndpointTest(
		"ClientConstraintAlternativesPassTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[2]),
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ServerMinPrincipal(sps[3]),
				new ClientMaxPrincipal(
				    new Principal[] {cps[1], cps[2]}),
				new ClientMaxPrincipal(cps[3])})},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, false),
	    new EndpointTest(
		"ClientConstraintAlternativesFailTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[0]),
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ServerMinPrincipal(sps[3]),
				new ClientMaxPrincipal(
				    new Principal[] {cps[1], cps[2]}),
				new ClientMaxPrincipal(cps[3])})},
		    null),
		null, // clientUnfulfilledConstraints, null indicates failure
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, false),
	    new EndpointTest(
		"ServerConstraintAlternativesPassTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[2])},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ServerMinPrincipal(sps[3]),
				new ClientMinPrincipal(
				    new Principal[] {cps[1], cps[2]}),
				new ClientMaxPrincipal(
				    new Principal[] {cps[2], cps[3]})}),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, false),
	    new EndpointTest(
		"ServerConstraintAlternativesFailTest1",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[0])},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ServerMinPrincipal(sps[3]),
				new ClientMinPrincipal(
				    new Principal[] {cps[1], cps[2]}),
				new ClientMaxPrincipal(
				    new Principal[] {cps[2], cps[3]})}),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		new KerberosPrincipal[] {cps[0]}, null, false, false),
	    new EndpointTest(
		"ServerConstraintAlternativesFailTest2",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[0])},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ClientMinPrincipal(
				    new Principal[] {cps[0], cps[2]}),
				new ClientMinPrincipal(
				    new Principal[] {cps[0], cps[3]})}),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		new KerberosPrincipal[] {cps[0]}, null, false, false),
	    /* -- confidentiality tests -- */
	    new EndpointTest(
		"ConfidentialityYESTest1", // only client requests
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]}),
			Confidentiality.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Integrity.YES},
		    null),
		integrityRequiredConstraints, /* serverUnfulfilledConstrain */
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		true, false),
	    new EndpointTest(
		"ConfidentialityYESTest2", // only server requests
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]})},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Confidentiality.YES},
		    null),
		null, // serverUnfulfilledConstraints, null indicates failure
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		false, false),
	    new EndpointTest(
		"ConfidentialityYESTest3", // both client and server request
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]}),
			Confidentiality.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Confidentiality.YES},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		true, false),
	    new EndpointTest(
		"ClientConfidentialityNOTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1], cps[2]}),
			Confidentiality.NO},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, false),
	    new EndpointTest(
		"ServerConfidentialityNOTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1])}, null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[0], cps[1]}),
			new ServerMinPrincipal(defaultServerPrincipal),
			Confidentiality.NO},
		    null),
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]},
		new KerberosPrincipal[] {defaultServerPrincipal},
		false, false),
	    /* -- delegation tests -- */
	    new EndpointTest(
		"DelegationYESTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[2]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[1], cps[2], cps[3]}),
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, true),
	    new EndpointTest(
		"DelegationNOTest",
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[2]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[1], cps[2], cps[3]}),
			Delegation.NO},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[2]}, null, false, false),
	    /* -- data tests -- */
	    new EndpointTest(
		"OneWriteShortDataNoEncryptionTest",
		oneShortArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		oneShortArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, true, null),
	    new EndpointTest(
		"MultiWriteShortDataNoEncryptionTest",
		multiShortArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		multiShortArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, true, null),
	    new EndpointTest(
		"OneWriteLongDataNoEncryptionTest1",
		oneLongArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		oneLongArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, true, null),
	    new EndpointTest(
		"OneWriteLongDataNoEncryptionTest2",
		oneLongArray3, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		oneLongArray4, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, true, null),
	    new EndpointTest(
		"MultiWriteLongDataNoEncryptionTest",
		multiLongArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		multiLongArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, false, true, null),
	    new EndpointTest(
		"OneWriteShortDataWithEncryptionTest",
		oneShortArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Confidentiality.YES,
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		oneShortArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, true, true, null),
	    new EndpointTest(
		"MultiWriteShortDataWithEncryptionTest",
		multiShortArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Confidentiality.YES,
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		multiShortArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, true, true, null),
	    new EndpointTest(
		"OneWriteLongDataWithEncryptionTest1",
		oneLongArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Confidentiality.YES,
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		oneLongArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, true, true, null),
	    new EndpointTest(
		"OneWriteLongDataWithEncryptionTest2",
		oneLongArray3, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Confidentiality.YES,
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		oneLongArray4, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, true, true, null),
	    new EndpointTest(
		"MultiWriteLongDataWithEncryptionTest",
		multiLongArray1, null, true,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			Confidentiality.YES,
			Delegation.YES},
		    null),
		emptyConstraints, /* clientUnfulfilledConstraints */
		multiLongArray2, null, true,
		null,
		emptyConstraints, /* serverUnfulfilledConstraints */
		new KerberosPrincipal[] {cps[1]}, null, true, true, null),
	    /* -- server capability tests -- */
	    new ServerCapabilityTest(
		"ServerCapabilityNullConstraintsTest",
		serverEndpoint, null,
		null,             /* constraints */
		emptyConstraints, /* unfulfilledConstraints */
		"null constraints, should not return", /* returnErrMsg */
		null,             /* nullPointerExceptionErrMsg */
		"unsupportedConstraintException thrown",
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityNoAuthenticationPermissionTest",
		serverEndpoint, defaultPermsNoSocketPerm,
		null,             /* constraints */
		emptyConstraints, /* unfulfilledConstraints */
		"null constraints, should not return", /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		"unsupportedConstraintException thrown",
		null),            /* securityExceptionErrMsg */
	    new ServerCapabilityTest(
		"ServerCapabilitySubjectNoCredentialPermissionTest",
		serverEndpointNoCred, null,
		null,             /* constraints */
		emptyConstraints, /* unfulfilledConstraints */
		"null constraints, should not return", /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		null,             /* unsupportedConstraintExceptionErrMsg */
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityValidPrincipalConstraintsTest",
		serverEndpoint, null,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[1], cps[2], cps[3]}),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		emptyConstraints, /* unfulfilledConstraints */
		null,             /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		"unsupportedConstraintException thrown",
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityConstraintAlternativesPassTest",
		serverEndpoint, null,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ServerMinPrincipal(sps[3]),
				new ClientMinPrincipal(
				    new Principal[] {cps[1], cps[2]}),
				new ClientMaxPrincipal(cps[3])}),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		emptyConstraints, /* unfulfilledConstraints */
		null,             /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		"unsupportedConstraintException thrown",
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityConstraintAlternativesFailTest",
		serverEndpoint, null,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ConstraintAlternatives(
			    new InvocationConstraint[] {
				new ServerMinPrincipal(sps[3]),
				new ClientMinPrincipal(
				    new Principal[] {cps[1], cps[2]}),
				new ClientMinPrincipal(
				    new Principal[] {cps[2], cps[3]})}),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		null,             /* unfulfilledConstraints */
		"invalid constraints, should not return", /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		null,             /* unsupportedConstraintExceptionErrMsg */
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityInvalidPrincipalConstraintsTest1",
		serverEndpoint, null,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(
			    new Principal[] {cps[1], cps[2]}),
			new ClientMaxPrincipal(cps[3]),
			new ServerMinPrincipal(defaultServerPrincipal)},
		    null),
		emptyConstraints, /* unfulfilledConstraints */
		"conflict constraints, should not return", /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		null,             /* unsupportedConstraintExceptionErrMsg */
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityInvalidPrincipalConstraintsTest2",
		serverEndpoint, null,
		new InvocationConstraints(
		    new InvocationConstraint[] {
			new ClientMinPrincipal(cps[1]),
			new ClientMaxPrincipal(
                            new Principal[] {cps[1], cps[2], cps[3]}),
			new ServerMinPrincipal(sps[2])},
		    null),
		emptyConstraints, /* unfulfilledConstraints */
		"conflict constraints, should not return", /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		null,             /* unsupportedConstraintExceptionErrMsg */
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityIntegrityRequiredTest",
		serverEndpoint, null,
		integrityRequiredConstraints, /* constraints */
		integrityRequiredConstraints, /* unfulfilledConstraints */
		null,             /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		"unsupportedConstraintException thrown",
		"SecurityException thrown when perms granted"),
	    new ServerCapabilityTest(
		"ServerCapabilityIntegrityPreferredTest",
		serverEndpoint, null,
		integrityPreferredConstraints, /* constraints */
		integrityPreferredConstraints, /* unfulfilledConstraints */
		null,             /* returnErrMsg */
		"NullPointerException thrown", /* nullPointerExceptionErrMsg */
		"unsupportedConstraintException thrown",
		"SecurityException thrown when perms granted"),
	    /* -- server endpoint constructor tests -- */
	    new ServerEndpointConstructorTest(
		"ServerConstructorNullSubjectNoContextSubjTest", // name
		null,                             // subject
		null,                             // serverPrincipal
		null, 0,                          // serverHost and port
		null, null,                       // csf and ssf
		null,                             // doAsSubject
	        null,                             // permsToRunWith
		"no context subject, should fail",// returnErrMsg
		null,                             // UnsupportedConsErrMsg
		"IllegalArgumentException thrown",// illArgErrMsg
		"SecurityException thrown when perms granted"), //secErrMsg
	    new ServerEndpointConstructorTest(
		"ServerConstructorContextSubjNoPrincipalTest", // name
		null,                             // subject
		null,                             // serverPrincipal
		null, 0,                          // serverHost and port
		null, null,                       // csf and ssf
		new Subject(),                    // doAsSubject
	        null,                             // permsToRunWith
		"context subject no principal, should fail", // returnErrMsg
		null,                             // UnsupportedConsEMsg
		"IllegalArgumentException thrown",// illArgErrMsg
		"SecurityException thrown when perms granted"), //secErrMsg
	    new ServerEndpointConstructorTest(
		"ServerConstructorContextSubjOnePrincipalTest", // name
		null,                             // subject
		null,                             // serverPrincipal
		null, 0,                          // serverHost and port
		null, null,                       // csf and ssf
		onePrincipalServerSubject,        // doAsSubject
	        null,                             // permsToRunWith
		null,                             // returnErrMsg
		"UnsupportedConstraintException thrown",// UnsupportedConsEMsg
		"IllegalArgumentException thrown",// illArgErrMsg
		"SecurityException thrown when perms granted"), //secErrMsg
	    new ServerEndpointConstructorTest(
		"ServerConstructorContextSubjMultiPrincipalTest", // name
		null,                             // subject
		null,                             // serverPrincipal
		null, 0,                          // serverHost and port
		null, null,                       // csf and ssf
		serverSubject,                    // doAsSubject
	        null,                             // permsToRunWith
		null,                             // returnErrMsg
		"UnsupportedConstraintException thrown",// UnsupportedConsEMsg
		"IllegalArgumentException thrown",// illArgErrMsg
		"SecurityException thrown when perms granted"), //secErrMsg
	    new ServerEndpointConstructorTest(
		"ServerConstructorContextSubjTwoPrincipalOneKeyTest", // name
		null,                             // subject
		null,                             // serverPrincipal
		null, 0,                          // serverHost and port
		null, null,                       // csf and ssf
		twoPrincipalServerSubject,        // doAsSubject
	        null,                             // permsToRunWith
		null,                             // returnErrMsg
		"UnsupportedConstraintException thrown",// UnsupportedConsEMsg
		"IllegalArgumentException thrown", // illArgErrMsg
		"SecurityException thrown when perms granted"), //secErrMsg
	    new ServerEndpointConstructorTest(
		"ServerConstructorNoAuthPermTest",// name
		serverSubject,                    // subject
		null,                             // serverPrincipal
		null, 0,                          // serverHost and port
		null, null,                       // csf and ssf
		null,                             // doAsSubject
	        permsNoAuth,                      // permsToRunWith
		"constructor returned when auth listen not granted",// retEMsg
		"UnsupportedConstraintException thrown",// UnsupportedConsEMsg
		"IllegalArgumentException thrown",// illArgErrMsg
		null),                            //secErrMsg
	    new ServerEndpointConstructorTest(
		"ServerConstructorPortErrTest",   // name
		serverSubject,                    // subject
		defaultServerPrincipal,           // serverPrincipal
		null, -1,                         // serverHost and port
		null, null,                       // csf and ssf
		null,                             // doAsSubject
	        null,                             // permsToRunWith
		"constructor returned when auth listen not granted",// retEMsg
		"UnsupportedConstraintException thrown",// UnsupportedConsEMsg
		null,                             // illArgErrMsg
		"SecurityException thrown when perms granted"), //secErrMsg
	    /* -- server endpoint listen tests -- */
	    new ServerEndpointListenTest(
		"ListenNoSockPermTest",           // name
		serverEndpoint,                   // serverEndpoint
		authPermOnly,                     // permsToRunWith
		"listen returned when sock perm not granted", // returnErrMsg
		"IllegalArgumentException thrown",// illArgErrMsg,
		null),                            // secErrMsg)
	    new ServerEndpointListenTest(
		"ListenNoAuthPermTest",           // name
		serverEndpoint,                   // serverEndpoint
		sockPermOnly,                     // permsToRunWith
		"listen returned when auth listen not granted", // returnErrMsg
		"IllegalArgumentException thrown",// illArgErrMsg,
		null),                            // secErrMsg)
	    new ServerEndpointListenTest(
		"ListenWithSockAndAuthPermsTest", // name
		serverEndpoint,                   // serverEndpoint
		null,                             // permsToRunWith
		null,                             // returnErrMsg
		"IllegalArgumentException thrown",// illArgErrMsg,
		"SecurityException thrown when should succeed") // secErrMsg)
	};
    };

    //-----------------------------------
    //          nested classes
    //-----------------------------------

    private interface ServerSideChecker {
	String checkServer(InboundRequest inbound);
	String getReplyMsg();
	Object getReplyData();
	void checkDone();
    }
    
    private interface Action {
	public Object run() throws Exception;
    }

    private static class TestPrincipal implements Principal {
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

    private static class TestConstraint implements InvocationConstraint {
	public InvocationConstraint reduceBy(InvocationConstraint c) {
	    return this;
	}
    }	

    private static class TestPermission extends Permission {

	public TestPermission(String name) {
	    super(name);
	}

	public boolean equals(Object obj) {
	    if (!obj.getClass().equals(TestPermission.class))
		return false;
	    return getName().equals(((TestPermission) obj).getName());
	}

	public String getActions() {
	    return null;
	}

	public int hashCode() {
	    return getName().hashCode();
	}

	public boolean implies(Permission permission) {
	    return equals(permission);
	}
    }

    public static class EndpointTest extends BasicTest
	implements ServerSideChecker
    {
	/* -- provided data on the client side -- */
	Object clientData;  // byte[] or array of byte[]s
	Permission[] clientPerms;
	boolean clientPermsOk;
	InvocationConstraints clientConstraints;
	InvocationConstraints clientUnfulfilledConstraints;

	/* -- provided data on the server side -- */
	Object serverData;  // byte[] or array of byte[]s
	Permission[] serverPerms;
	boolean serverPermsOk;
	InvocationConstraints serverConstraints;
	InvocationConstraints serverUnfulfilledConstraints;

	/* -- connection properties to be verified -- */
	Set allowedCps; // allowable client principals
	Set allowedSps; // allowable server principals
	boolean doEncryption;
	boolean doDelegation;

	/* -- perms + authPerm form the default permission set -- */
	Permission[] defaultPerms;

	int testID;
	String requestMsg;
	String replyMsg;

	boolean serverSideCheckFailed;
	boolean serverCheckDone;

	/* declared as instance variable because it need to be used in
           anonymous class but cannot be declared final */
	InboundRequestHandle rh; // returned by processRequestData(...)
	    
	EndpointTest(String name,
		     Permission[] clientPerms,
		     boolean clientPermsOk,
		     Permission[] serverPerms,
		     boolean serverPermsOk,
		     Permission[] defaultPerms)
	{
	    this(name, null, clientPerms, clientPermsOk, null, null,
		 null, serverPerms, serverPermsOk, null, null, null, null,
		 false, false, defaultPerms);
	}
	    
	EndpointTest(String name,
		     InvocationConstraints clientConstraints,
		     InvocationConstraints clientUnfulfilledConstraints,
		     InvocationConstraints serverConstraints,
		     InvocationConstraints serverUnfulfilledConstraints,
		     /* connection parameter */
		     KerberosPrincipal[] clientPrincipals,
		     KerberosPrincipal[] serverPrincipals,
		     boolean doEncryption,
		     boolean doDelegation)
	{
	    this(name, null, null, true, clientConstraints,
		 clientUnfulfilledConstraints, null, null, true,
		 serverConstraints, serverUnfulfilledConstraints,
		 clientPrincipals, serverPrincipals,
		 doEncryption, doDelegation, null);
	}
	    
	/* If clientConstraints is non-null and
           clientUnfulfilledConstraints is null, then the
           corresponding requestIterator.next() should throw an
           UnsupportedConstraintException.  If serverConstraints is
           non-null and serverUnfulfilledConstraints is null, then the
           corresponding ServerConnection.checkConstraints() should
           throw and UnsupportedConstraintException. */
	EndpointTest(String name,
		     /* client parameters */
		     Object clientData,
		     Permission[] clientPerms,
		     boolean clientPermsOk,
		     InvocationConstraints clientConstraints,
		     InvocationConstraints clientUnfulfilledConstraints,
		     /* server parameters */
		     Object serverData,
		     Permission[] serverPerms,
		     boolean serverPermsOk,
		     InvocationConstraints serverConstraints,
		     InvocationConstraints serverUnfulfilledConstraints,
		     /* connection parameter */
		     KerberosPrincipal[] clientPrincipals,
		     KerberosPrincipal[] serverPrincipals,
		     boolean doEncryption,
		     boolean doDelegation,
		     /* perms + authPerm form the default permission set */
		     Permission[] defaultPerms)
	{
	    super(name);

	    testID = nextID();

	    if (clientData == null) {
		this.clientData = new byte[0];
	    } else {
		this.clientData = clientData;
	    }
	    if (clientConstraints == null) {
		this.clientConstraints = InvocationConstraints.EMPTY;
		this.clientUnfulfilledConstraints =
		    InvocationConstraints.EMPTY;
	    } else {
		this.clientConstraints = clientConstraints;
		this.clientUnfulfilledConstraints =
		    clientUnfulfilledConstraints;
	    }

	    if (serverData == null) {
		/*
		 * keep the testID in serverData in case of replyMsg
		 * is "failure"
		 */
		this.serverData = new Integer(testID).toString().getBytes();
	    } else {
		this.serverData = serverData;
	    }
	    if (serverConstraints == null) {
		this.serverConstraints = InvocationConstraints.EMPTY;
		this.serverUnfulfilledConstraints = InvocationConstraints.EMPTY;
	    } else {
		this.serverConstraints = serverConstraints;
		this.serverUnfulfilledConstraints =
		    serverUnfulfilledConstraints;
	    }
	    
	    allowedCps = clientPrincipals == null ?
		new HashSet(Arrays.asList(cps)) :
		new HashSet(Arrays.asList(clientPrincipals));
	    allowedSps = serverPrincipals == null ?
		new HashSet(Arrays.asList(sps)) :
		new HashSet(Arrays.asList(serverPrincipals));

	    if (clientConstraints == null && serverConstraints == null) {
		this.doEncryption = false;
		this.doDelegation = false;
	    } else {
		this.doEncryption = doEncryption;
		this.doDelegation = doDelegation;
	    }

	    if (defaultPerms == null) {
		this.defaultPerms = defaultPermsWithSocketPerm;
	    } else {
		this.defaultPerms = defaultPerms;
	    }

	    // getClientPerms() has to be called after doDelegation,
	    // allowedCps, and defaultPerms being setup
	    this.clientPerms = getClientPerms(clientPerms);
	    this.clientPermsOk = clientPermsOk;

	    // getServerPerms() has to be called after allowedSps and
	    // defaultPerms being setup
	    this.serverPerms = getServerPerms(serverPerms);
	    this.serverPermsOk = serverPermsOk;

	    requestMsg = "TestEndpoint:" + testID + " request";
	    replyMsg = "TestEndpoint:" + testID + " reply";
	}

	public Object run() throws Exception {
	    //return doTest();
	    Object result = doTest();
	    try {
		Thread.sleep(10); // wait till current connection expires
	    } catch (InterruptedException e) {}
	    return result;
	}

	public void check(Object result) throws Exception {
	    if (result != null)
		throw new FailedException(name() + " failed!");
	}

	/* return a failMsg, or null if succeed */
	public String checkServer(InboundRequest inbound) {

	    /* -- check the underlying connection -- */
	    final ServerRequestInfo info = 
		getServerRequestInfo(serverEndpoint, inbound);
	    if (!(allowedSps.contains(info.serverPrincipal) &&
		  allowedCps.contains(info.clientPrincipal) &&
		  info.doEncryption == doEncryption &&
		  info.doDelegation == doDelegation &&
		  info.gssContext.getCredDelegState() == doDelegation))
	    {
		return "(testID=" + testID + ") " +
		    "the connection used for inbound request " +
		    "does not have the right parameters:\n" + 
		    info + "\tExpected ServerRequestInfo[" +
		    "\n\t\tallowedCps = " + allowedCps +
		    "\n\t\tallowedSps = " + allowedSps +
		    "\n\t\tdoEncryption = " + doEncryption +
		    "\n\t\tdoDelegation = " + doDelegation + "]\n\n";
	    }

	    /* test connection.processRequestData(...) */
	    try {
		rh = (InboundRequestHandle) runWithPerms(
		    new Action() {
			    public Object run() throws Exception {
				ServerConnection conn = info.serverConnection;
				InputStream in = conn.getInputStream();
				OutputStream out = conn.getOutputStream();
				return conn.processRequestData(in, out);
			    }
			},
		    defaultPermsNoSocketPerm);
	    } catch (IOException e) {
		e.printStackTrace(); // for debug
		return "(testID=" + testID + ") " +
		    "IOException encountered when testing processRequestData" +
		    "(...): " + e;
	    } catch (SecurityException e) {
		e.printStackTrace(); // for debug
		return "(testID=" + testID + ") " +
		    "processRequestData(...) should not check any auth " +
		    "permissions, hence no SecurityException should be " +
		    "thrown here: " + e;
	    } catch (Exception e) {
		e.printStackTrace(); // for debug
		return "unknown exception thrown in " +
		    "processRequestData(...): " + e;
	    }

	    /* test connection.checkPermission() */
	    try {
		runWithPerms(new Action() {
			public Object run() {
			    info.serverConnection.checkPermissions(rh);
			    return null;
			}
		    }, serverPerms);
		if (!serverPermsOk) {
		    return "(testID=" + testID + ") " +
			"no SecurityException thrown in " +
			"serverConnection.checkPermissions(clientSubject) " +
			"when passed in serverPermsOk = false";
		}
	    } catch (SecurityException e) {
		if (serverPermsOk) {
		    e.printStackTrace(); // for debug
		    return "(testID=" + testID + ") " +
			"SecurityException thrown in " +
			"serverConnection.checkPermissions(clientSubject) " +
			"when passed in serverPermsOk = true: " + e;
		} else {
		    return null;
		}
	    } catch (Exception e) {
		e.printStackTrace(); // for debug
		return "unknown exception thrown in " +
		    "serverConnection.checkPermissions(rh): " + e;
	    }

	    /* test connection.checkConstraints(...) */
	    try {
		InvocationConstraints unfulfilledConstraints =
		    (InvocationConstraints) runWithPerms(new Action() {
			    public Object run() throws Exception {
				return info.serverConnection.checkConstraints(
				    rh, serverConstraints);
			    }
			}, defaultPermsNoSocketPerm);
		if (serverUnfulfilledConstraints == null) {
		    return "(testID=" + testID + ") " +
			"serverConnection.checkConstraints() returns " +
			unfulfilledConstraints + " when " +
			"passed in serverUnfulfilledConstraints = " + null;
		} else if (!serverUnfulfilledConstraints.equals(
		    unfulfilledConstraints))
		{
		    return "(testID=" + testID + ") " +
			"serverConnection.checkConstraints() returns " +
			unfulfilledConstraints + " when " +
			"passed in serverUnfulfilledConstraints = " + 
			serverUnfulfilledConstraints;
		}
	    } catch (UnsupportedConstraintException e) {
		if (serverUnfulfilledConstraints != null) {
		    e.printStackTrace(); // for debug
		    return "(testID=" + testID + ") " +
			"serverConnection.checkConstraints() throws " + e +
			" when passed in serverUnfulfilledConstraints is " +
			serverUnfulfilledConstraints;
		}
	    } catch (SecurityException e) {
		e.printStackTrace(); // for debug
		return "(testID=" + testID + ") " +
		    "serverConnection.checkConstraints() should not " +
		    "check any auth permissions, hence no " +
		    "SecurityException should be thrown here: " + e;
	    } catch (Exception e) {
		e.printStackTrace(); // for debug
		return "unknown exception thrown in " +
		    "serverConnection.checkConstraints() " + e;
	    }
	    
	    /* test connection.populateContext(...) */
	    try {
		Collection context = (Collection) runWithPerms(
		    new Action() {
			    public Object run() throws Exception {
				Collection context = new ArrayList(2);
				info.serverConnection.populateContext(
				    rh, context);
				return context;
			    }
			},
		    defaultPermsNoSocketPerm);
		if (context.size() != 2)
		    return "context size is not 2: " + context;
		for (Iterator iter = context.iterator(); iter.hasNext(); ) {
		    Object ctx = iter.next();
		    if (ctx instanceof ClientHost) {
			InetAddress addr = ((ClientHost) ctx).getClientHost();
			if (!InetAddress.getLocalHost().equals(addr)) {
			    return "client host info in server request " +
				"context incorrect: " + addr;
			}
		    } else if (ctx instanceof ClientSubject) {

			Subject csubj =
			    ((ClientSubject) ctx).getClientSubject();

			Set ps = csubj.getPrincipals();
			if (ps.size() != 1 ||
			    (!allowedCps.contains(ps.iterator().next())))
			{
			    return "client subject of the request does not " +
				"have the right set of principals: " + csubj;
			}
			
			if (info.doDelegation) {
			    KerberosTicket[] tickets = getTickets(csubj);
			    if (tickets.length != 1 ||
				(!allowedCps.contains(tickets[0].getClient())))
			    {
				return "client subject of the request does " +
				    "not have the right forwarded ticket: " +
				    csubj;
			    }
			}
		    } else {
			return "unknown object found in context returned " +
			    "by serverConnection.populateContext(...): " + ctx;
		    }
		}
	    } catch (Exception e) {
		e.printStackTrace(); // for debug
		return "unknown exception thrown in " +
		    "during serverConnection.populateContext(...): " + e;
	    }

	    /* test received client data */
	    InputStream is = inbound.getRequestInputStream();
	    DataInputStream dis = new DataInputStream(is);
	    /*
	     * requestMsg have been checked when calling
	     * checkerMap.get(clientMsg), only clientData need to be
	     * checked here.
	     */
	    try {
		byte[] requestData = new byte[dis.readInt()];
		dis.readFully(requestData);
		if (dataEquals(clientData, requestData)) {
		    return null;
		} else {
		    return "received client data does not match clientData";
		}
	    } catch (Exception e) {
		e.printStackTrace(); // for debug
		return "failure while receiving client data in server: " + e;
	    }
	}
	
	public String getReplyMsg() {
	    if (serverSideCheckFailed) {
		// cannot modify replyMsg, it is used to indicate "succeed"
		return "failed";
	    } else {
		return replyMsg;
	    }
	}

	public Object getReplyData() {
	    return serverData;
	}

	public void checkDone() {
	    serverCheckDone = true;
	}

	/* perms == null: Return an set of acceptable client permissions */
	Permission[] getClientPerms(Permission[] clientPerms) {
	    Permission[] basePerms;
	    if (clientPerms == null) {
		basePerms = defaultPermsWithSocketPerm;
	    } else if (clientPerms.length == 0) {
		basePerms = defaultPerms;
	    } else {
		return clientPerms; // client perms has been given
	    }
	    
	    clientPerms = new Permission[basePerms.length + allowedSps.size()];
	    System.arraycopy(basePerms, 0, clientPerms, 0, basePerms.length);

	    String action;
	    if (doDelegation) {
		action = "delegate";
	    } else {
		action = "connect";
	    }

	    // add in the "connect/delegate" auth permissions
	    int i = basePerms.length;
	    for (Iterator iter = allowedSps.iterator(); iter.hasNext();) {
		Set spset = Collections.singleton(iter.next());
		clientPerms[i++] = new AuthenticationPermission(
		    allowedCps, spset, action);
	    }
	    return clientPerms;
	}

	// return an set of acceptable server permissions
	Permission[] getServerPerms(Permission[] serverPerms) {
	    Permission[] basePerms;
	    if (serverPerms == null) {
		basePerms = defaultPermsWithSocketPerm;
	    } else if (serverPerms.length == 0) {
		basePerms = defaultPerms;
	    } else {
		return serverPerms; // server perms has been given
	    }

	    serverPerms = new Permission[basePerms.length + 1 +
					 allowedCps.size()];
	    System.arraycopy(basePerms, 0, serverPerms, 0, basePerms.length);

	    // add in the "listen" auth permission
	    serverPerms[basePerms.length] = 
	        new AuthenticationPermission(allowedSps, null, "listen");

	    // add in the "accept" auth permissions
	    int i = basePerms.length + 1;
	    for (Iterator iter = allowedCps.iterator(); iter.hasNext();) {
		Set cpset = Collections.singleton(iter.next());
		serverPerms[i++] = new AuthenticationPermission(
		    allowedSps, cpset, "accept");
	    }

	    return serverPerms;
	}

	private KerberosTicket[] getTickets(final Subject subj) {
	    return (KerberosTicket[]) AccessController.doPrivileged(
		new PrivilegedAction() {
			public Object run() {
			    ArrayList tlist = new ArrayList();
			    Set creds = subj.getPrivateCredentials();
			    for (Iterator iter = creds.iterator();
				 iter.hasNext();)
			    {
				Object cred = iter.next();
				if (cred instanceof KerberosTicket) {
				    KerberosTicket ticket =
					(KerberosTicket) cred;
				    KerberosPrincipal srv = ticket.getServer();
				    String rlm = srv.getRealm();
				    String tgtname = "krbtgt/" + rlm + "@" +
					rlm;
				    if (srv.getName().equals(tgtname) &&
					!ticket.isDestroyed() && ticket.
					isCurrent())
				    {
					tlist.add(ticket);
				    }
				}
			    }
			    return tlist.toArray(
				new KerberosTicket[tlist.size()]);
			}
		    });
	}

	private String checkClientRequest(OutboundRequest outbound) {
	    ClientRequestInfo info = 
		getClientRequestInfo(clientEndpoint, outbound);

	    if (!((allowedSps.contains(info.serverPrincipal)) &&
		  (allowedCps.contains(info.clientPrincipal)) &&
		  info.doEncryption == doEncryption &&
		  info.doDelegation == doDelegation &&
		  info.gssContext.getCredDelegState() == doDelegation))
	    {
		return "the connection used for outbound request " +
		    "does not have the right parameters:\n" +
		    info + "\tExpectedClientRequestInfo[" +
		    "\n\t\tallowedCps = " + allowedCps +
		    "\n\t\tallowedSps = " + allowedSps +
		    "\n\t\tdoEncryption = " + doEncryption +
		    "\n\t\tdoDelegation = " + doDelegation + "]\n\n";
	    }
	    return null;
	}

	private Object doTest() throws IOException {
	    OutboundRequest outbound;
	    checkerMap.put(requestMsg, this);

	    /* test endpoint.newRequest(...) */
	    final PrivilegedExceptionAction pact =
		new PrivilegedExceptionAction() {
			public Object run() throws IOException {
			    OutboundRequestIterator riter = 
				clientEndpoint.newRequest(clientConstraints);
			    if (!riter.hasNext()) {
				return "no request in the requestIter: " +
				    riter;
			    }

			    OutboundRequest req = riter.next();
			    if (riter.hasNext()) {
				return "more than one request in " +
				    "requestIter: " + riter;
			    }

			    return req;
			}
		    };

	    try {
		Object result = runWithPerms(
		    new Action() {
			    public Object run() throws Exception {
				try {
				    return Subject.doAs(clientSubject, pact);
				} catch (PrivilegedActionException pe) {
				    throw pe.getException();
				}
			    }
			}, clientPerms);

		if (result instanceof String) {
		    return result; // return error msg
		} else {
		    outbound = (OutboundRequest) result;
		}

		if (!clientPermsOk) {
		    return "no SecurityException thrown in " +
			"outboundRequestIter.next() " +
			"when passed in clientPermsOk = false";
		}

		if (clientUnfulfilledConstraints == null) {
		    return "SocketTimeoutException did not thrown in " +
			"outboundRequestIter.next() when passed in " +
			"clientUnfulfilledConstraints = " + 
			clientUnfulfilledConstraints + ", indicates " +
			"connect should not succeed.";
		}
	    } catch (UnsupportedConstraintException e) {
		if (clientUnfulfilledConstraints == null) {
		    return null;    // test succeeded
		} else {
		    e.printStackTrace(); // for debug
		    return "UnsupportedConstraintException thrown in " +
			"outboundRequestIter.next() when passed in " +
			"clientUnfulfilledConstraints = " + 
			clientUnfulfilledConstraints + ", e = " + e;
		}
	    } catch (SocketTimeoutException e) {
		if (clientUnfulfilledConstraints == null) {
		    return null;    // test succeeded
		} else {
		    e.printStackTrace(); // for debug
		    return "SocketTimeoutException thrown in " +
			"outboundRequestIter.next() when passed in " +
			"clientUnfulfilledConstraints = " + 
			clientUnfulfilledConstraints + ", e = " + e;
		}
	    } catch (IOException e) {
		e.printStackTrace(); // for debug
		return "IOException thrown in outboundRequestIter.next(): " +
		    e;
	    } catch (SecurityException e) {
		if (clientPermsOk) {
		    e.printStackTrace(); // for debug
		    return "SecurityException thrown in " +
			"outboundRequestIter.next() " +
			"when passed in clientPermsOk = true: " + e;
		} else {
		    return null;     // test succeeded
		}
	    } catch (Exception e) {
		e.printStackTrace(); // for debug
		return "unknown exception thrown in " +
		    "outboundRequestIter.next(): " + e;
	    }

	    if (outbound == null) {
		return "outboundRequestIter.next() returns null, which " +
		    "should never happen!";
	    }

	    String errMsg = checkClientRequest(outbound);
	    if (errMsg != null) {
		outbound.abort();
		return errMsg;
	    }

	    if (!outbound.getUnfulfilledConstraints().equals(
		clientUnfulfilledConstraints))
	    {
		return "outbound.getUnfulfilledConstraints() returns: " +
		    outbound.getUnfulfilledConstraints() + ", while the " +
		    "given clientUnfulfilledConstraints is: " +
		    clientUnfulfilledConstraints;
	    }
	    
	    OutputStream os = outbound.getRequestOutputStream();
	    DataOutputStream dos = new DataOutputStream(os);
	    dos.writeUTF(requestMsg);
	    writeData(dos, clientData);
	    dos.close();
	    InputStream is = outbound.getResponseInputStream();
	    DataInputStream dis = new DataInputStream(is);
	    String reply = dis.readUTF();
	    if (!reply.equals(replyMsg)) {
		outbound.abort();
		return reply;
	    }
	    byte[] replyData = new byte[dis.readInt()];
	    int curLen = 0;
	    long start = System.currentTimeMillis();
	    while (true) {
		try {
		    Thread.sleep(100);
		} catch (InterruptedException e) {}
		int len = dis.available();
		if ((curLen + len) > replyData.length) {
		    outbound.abort();
		    return "incoming data longer than expected";
		}
		dis.read(replyData, curLen, len);
		curLen += len;
		if (curLen == replyData.length)
		    break;
		if ((System.currentTimeMillis() - start) > 15 * 60 * 1000) {
		    outbound.abort();
		    return "timed out on server check reply, " +
			"serverCheckDone = " + serverCheckDone;
		}
	    }
	    dis.close();
	    if (dataEquals(serverData, replyData)) {
		return null;
	    } else {
		return "received server reply data does not match serverData";
	    }
	}
    }

    private static class ServerCapabilityTest extends BasicTest {

	KerberosServerEndpoint serverEndpoint;
	Permission[] permsToRunWith;
	InvocationConstraints constraints;
	InvocationConstraints unsupportedConstraints;
	String returnErrMsg;
	String nullPointerExceptionErrMsg;
	String unsupportedConstraintExceptionErrMsg;
	String securityExceptionErrMsg;

	ServerCapabilityTest(
	    String name, KerberosServerEndpoint serverEndpoint,
	    Permission[] permsToRunWith,
	    InvocationConstraints constraints,
	    InvocationConstraints unsupportedConstraints,
	    String returnErrMsg,
	    String nullPointerExceptionErrMsg,
	    String unsupportedConstraintExceptionErrMsg,
	    String SecurityExceptionErrMsg)
	{
	    super(name);
	    this.serverEndpoint = serverEndpoint;

	    if (permsToRunWith != null) {
		this.permsToRunWith = permsToRunWith;
	    } else {
		AuthenticationPermission authPerm =
		    new AuthenticationPermission(
			Collections.singleton(serverEndpoint.getPrincipal()),
			null, "listen");
		this.permsToRunWith = new Permission[] {
		    new AuthPermission("getSubject"),
		    new SocketPermission("*:1024-", "accept,connect,listen"),
		    authPerm};
	    }

	    if (constraints != null) {
		this.constraints = constraints;
	    } else {
		this.constraints = InvocationConstraints.EMPTY;
	    }

	    this.unsupportedConstraints = unsupportedConstraints;
	    this.returnErrMsg = returnErrMsg;
	    this.unsupportedConstraintExceptionErrMsg = 
		unsupportedConstraintExceptionErrMsg;
	    this.nullPointerExceptionErrMsg = nullPointerExceptionErrMsg;
	    this.securityExceptionErrMsg = securityExceptionErrMsg;
	}

	public Object run() throws Exception {
	    final Action act = new Action() {
		    public Object run() throws Exception {
			try {
			    InvocationConstraints unsupportedcs = 
				serverEndpoint.checkConstraints(constraints);
			    if (!unsupportedcs.equals(unsupportedConstraints))
				return returnErrMsg;
			    return null;
			} catch (NullPointerException e) {
			    if (nullPointerExceptionErrMsg != null)
				e.printStackTrace(); // for debug
			    return nullPointerExceptionErrMsg;
			} catch (UnsupportedConstraintException e) {
			    if (unsupportedConstraintExceptionErrMsg != null)
				e.printStackTrace(); // for debug
			    return unsupportedConstraintExceptionErrMsg;
			} catch (SecurityException e) {
			    if (securityExceptionErrMsg != null)
				e.printStackTrace(); // for debug
			    return securityExceptionErrMsg;
			}
		    }
		};

	    return runWithPerms(act, permsToRunWith);
	}

	public void check(Object result) throws Exception {
	    if (result != null)
		throw new FailedException(name() + " failed!");
	}
    }

    private static class ServerEndpointConstructorTest extends BasicTest {

	Subject subject; // subject pass to the server endpoint constructor
	KerberosPrincipal serverPrincipal;
	String serverHost;
	int port;
	SocketFactory csf;
	ServerSocketFactory ssf;
	Subject doAsSubject;
	Permission[] permsToRunWith;
	String returnErrMsg;
	String UnsupportedConsErrMsg;
	String illArgErrMsg;
	String secErrMsg;

	ServerEndpointConstructorTest(String name,
				      Subject subject,
				      KerberosPrincipal serverPrincipal,
				      String serverHost, int port,
				      SocketFactory csf,
				      ServerSocketFactory ssf,
				      Subject doAsSubject,
				      Permission[] permsToRunWith,
				      String returnErrMsg,
				      String UnsupportedConsErrMsg,
				      String illArgErrMsg,
				      String secErrMsg)
	{
	    super(name);
	    this.subject = subject;
	    this.serverPrincipal = serverPrincipal;
	    this.serverHost = serverHost;
	    this.port = port;
	    this.csf = csf;
	    this.ssf = ssf;
	    this.doAsSubject = doAsSubject != null ? doAsSubject :
		Subject.getSubject(AccessController.getContext());
	    if (permsToRunWith != null) {
		this.permsToRunWith = permsToRunWith;
	    } else {
		AuthenticationPermission authPerm;
		if (serverPrincipal != null) {
		    authPerm = new AuthenticationPermission(
			Collections.singleton(serverPrincipal), null,
			"listen");
		} else {
		    authPerm = new AuthenticationPermission(
			"javax.security.auth.kerberos.KerberosPrincipal \"*\"",
			"listen");
		}
		this.permsToRunWith = new Permission[] {
		    new AuthPermission("doAs"),
		    new AuthPermission("getSubject"),
		    new SocketPermission("*:1024-", "accept,connect,listen"),
		    authPerm};
	    }
	    this.returnErrMsg = returnErrMsg;
	    this.UnsupportedConsErrMsg = UnsupportedConsErrMsg;
	    this.illArgErrMsg = illArgErrMsg;
	    this.secErrMsg = secErrMsg;
	}

	public Object run() throws Exception {
	    final Action act = new Action() {
		    public Object run() throws Exception {
			try {
			    return Subject.doAs(
				doAsSubject, new PrivilegedExceptionAction() {
					public Object run() throws Exception {
					    return createSep();
					}
				    });
			} catch (PrivilegedActionException pae) {
			    throw pae.getException();
			}
		    }
		};

	    return runWithPerms(act, permsToRunWith);
	}

	public void check(Object result) throws Exception {
	    if (result != null)
		throw new FailedException(name() + " failed!");
	}

	private Object createSep() throws Exception {
	    try {
	        KerberosServerEndpoint sep = 
		    KerberosServerEndpoint.getInstance(
			subject, serverPrincipal, serverHost, port, csf, ssf);
		sep.enumerateListenEndpoints(new ListenContext() {
			public ListenCookie addListenEndpoint(
			    ListenEndpoint listenEndpoint) throws IOException
			{
			    ListenHandle handle = listenEndpoint.listen(
				new RequestDispatcher() {
					public void dispatch(
					    InboundRequest request)
					{
					    return;
					}
				    });
			    return handle.getCookie();
			}
		    });
		return returnErrMsg;
	    } catch (UnsupportedConstraintException e) {
		if (UnsupportedConsErrMsg != null)
		    e.printStackTrace(); // for debug
		return UnsupportedConsErrMsg;
	    } catch (IllegalArgumentException e) {
		if (illArgErrMsg != null)
		    e.printStackTrace(); // for debug
		return illArgErrMsg;
	    } catch (SecurityException e) {
		if (secErrMsg != null)
		    e.printStackTrace(); // for debug
		return secErrMsg;
	    }
	}
    }

    private static class ServerEndpointListenTest extends BasicTest {

	KerberosServerEndpoint serverEp;
	Permission[] permsToRunWith;
	String returnErrMsg;
	String illArgErrMsg;
	String secErrMsg;

	ServerEndpointListenTest(String name,
				 KerberosServerEndpoint serverEp,
				 Permission[] permsToRunWith,
				 String returnErrMsg,
				 String illArgErrMsg,
				 String secErrMsg)
	{
	    super(name);
	    this.serverEp = serverEp;

	    if (permsToRunWith != null) {
		this.permsToRunWith = permsToRunWith;
	    } else {
		Principal serverPrincipal = serverEp.getPrincipal();
		AuthenticationPermission authPerm =
		    new AuthenticationPermission(
			Collections.singleton(serverPrincipal), null,
			"listen");
		this.permsToRunWith = new Permission[] {
		    new SocketPermission("*:1024-", "accept,connect,listen"),
		    authPerm,
		    new AuthPermission("doAs"),
		    new RuntimePermission("modifyThread"),
		    new RuntimePermission("modifyThreadGroup"),
		    new RuntimePermission("setContextClassLoader"),
		    new ServicePermission(serverPrincipal.getName(), "accept")
		};
	    }

	    this.returnErrMsg = returnErrMsg;
	    this.illArgErrMsg = illArgErrMsg;
	    this.secErrMsg = secErrMsg;
	}
	
	public Object run() throws Exception {
	    Action act = new Action() {
		    public Object run() throws Exception {
			serverEp.enumerateListenEndpoints(
			    new ServerEndpoint.ListenContext() {
				    public ServerEndpoint.ListenCookie
				    addListenEndpoint(
					ServerEndpoint.ListenEndpoint
					listenEndpoint)
					throws IOException
				    {
					listenEndpoint.checkPermissions();
					ServerEndpoint.ListenHandle handle =
					    listenEndpoint.listen(
						new RequestDispatcher() {
							public void dispatch(
							    InboundRequest
							    request)
							{ // do nothing
							}
						});
					ServerEndpoint.ListenCookie cookie =
					    handle.getCookie();
					handle.close();
					return cookie;
				    }
				});
			return returnErrMsg;
		    }
		};

	    try {
		return runWithPerms(act, permsToRunWith);
	    } catch (IllegalArgumentException e) {
		if (illArgErrMsg != null)
		    e.printStackTrace();
		return illArgErrMsg;
	    } catch (SecurityException e) {
		if (secErrMsg != null)
		    e.printStackTrace();
		return secErrMsg;
	    }
	}

	public void check(Object result) throws Exception {
	    if (result != null)
		throw new FailedException(name() + " failed!");
	}
    }

    /* -- Test ListenEndpoint.equals -- */

    public static class TestListenEndpointEquals extends BasicTest {

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

	static class DeviousPrincipal implements Principal {
	    boolean canCallEquals = true;
	    public String getName() { return "Devious"; }
	    public boolean equals(Object other) {
		if (!canCallEquals) {
		    throw new Test.FailedException(
			"Called equals on devious principal");
		}
		return this == other;
	    }
	}

	static class I {
	    int i;
	    Object item;
	    I(int i, Object item) { this.i = i; this.item = item; }
	}


	/*
	 * Create multiple subject instance with the same content.
         * Note that the listen endpoint equals only check subject "==".
	 */
	private static Subject subject1;
	private static Subject subject2;
	private static Subject subject3;
	private static Subject subject4;
	private static Subject defaultSubject = onePrincipalServerSubject;
	static {
	    try {
		subject1 = getLoginSubject(serverLoginEntry);
		subject2 = getLoginSubject(serverLoginEntry);
		subject3 = getLoginSubject(serverLoginEntry);
		subject4 = getLoginSubject(serverLoginEntry);
	    } catch (Exception e) {
		throw new RuntimeException("getLoginSubject failure", e);
	    }
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
	    new I(1, createListenEndpoint(defaultSubject, null, "foo", 0)),
	    new I(1, createListenEndpoint(defaultSubject, null, "bar", 0)),
	    // 2 tests for equality with same subject instance
	    new I(2,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject = subject1;
		      } }.subject,
		      null, "baz", 0)),
	    new I(2,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject = subject1;
			  DeviousPrincipal dp = new DeviousPrincipal();
			  this.subject.getPrincipals().add(dp);
			  dp.canCallEquals = false;
		      } }.subject,
		      null, "baz", 0)),
	    // 3, 4 test for unequality with different subject instances,
	    // though they have the same content
	    new I(3,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject = subject2;
		      } }.subject,
		      null, "baz", 0)),
	    new I(4,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject = subject3;
		      } }.subject,
		      null, "baz", 0)),
	    // 5, 6 test for unequality with different principals,
	    // though the same subject instance is used
	    new I(5,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject = subject4;
		      } }.subject,
		      new KerberosPrincipal("testServer2"), "baz", 0)),
	    new I(6,
		  createListenEndpoint(
		      new WithSubject() { {
			  this.subject = subject4;
		      } }.subject,
		      new KerberosPrincipal("testServer3"), "baz", 0)),
	    new I(7, createListenEndpoint(defaultSubject, null, "foo", 1)),
	    new I(8, createListenEndpoint(defaultSubject, null, "foo", 33)),
	    new I(8, createListenEndpoint(defaultSubject, null, "bar", 33)),
	    new I(8, createListenEndpoint(
		      defaultSubject, null, "foo", 33,
		      createSocketFactory("a"), null)),
	    new I(8, createListenEndpoint(
		      defaultSubject, null, "foo", 33,
		      createSocketFactory("b"), null)),
	    new I(9, createListenEndpoint(
		      defaultSubject, null, "foo", 33,
		      null, createServerSocketFactory(44))),
	    new I(10, createListenEndpoint(
		      defaultSubject, null, "foo", 33,
		      createSocketFactory("c"),
		      createServerSocketFactory(55))),
	    new I(11, createListenEndpoint(
		      defaultSubject, null, "foo", 33,
		      createSocketFactory("c"),
		      createServerSocketFactory(56)))
	};

	static Collection localtests = new ArrayList();

	static {
	    for (int i = items.length; --i >= 0; ) {
		for (int j = items.length; --j >= 0; ) {
		    Object x = items[i].item;
		    Object y = items[j].item;
		    String name = "\n  x(" + items[i].i + ") = " + x +
			"\n  y(" + items[j].i + ") = " + y;
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
	    Subject serverSubject, KerberosPrincipal serverPrincipal,
	    String serverHost, int port)
	{
	    return createListenEndpoint(
		serverSubject, serverPrincipal, serverHost, port, null, null);
	}

	static ListenEndpointData createListenEndpoint(
	    Subject serverSubject, KerberosPrincipal serverPrincipal,
	    String serverHost, int port,
	    SocketFactory csf, ServerSocketFactory ssf)
	{
	    return new ListenEndpointData(
		serverSubject, serverPrincipal, serverHost, port, csf, ssf);
	}

	static class ListenEndpointData {
	    private final Subject serverSubject;
	    private final KerberosPrincipal serverPrincipal;
	    private final String serverHost;
	    private final int port;
	    private final SocketFactory csf;
	    private final ServerSocketFactory ssf;

	    ListenEndpointData(Subject serverSubject,
			       KerberosPrincipal serverPrincipal,
			       String serverHost, int port,
			       SocketFactory csf, ServerSocketFactory ssf)

	    {
		this.serverSubject = serverSubject;
		this.serverPrincipal = serverPrincipal;
		this.serverHost = serverHost;
		this.port = port;
		this.csf = csf;
		this.ssf = ssf;
	    }		

	    ListenEndpoint create() {
		ServerEndpoint serverEndpoint;
		try {
		    serverEndpoint = KerberosServerEndpoint.getInstance(
			serverSubject, serverPrincipal, serverHost, port,
			csf, ssf);
		} catch (Exception e) {
		    throw new RuntimeException(
			"failed to get server endpoint instance", e);
		}
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
		    "serverSubject = " + serverSubject +
		    ", serverPrincipal = " + serverPrincipal +
		    ", serverHost = " + serverHost +
		    ", port = " + port +
		    ", csf = " + csf +
		    ", ssf = " + 
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
}
