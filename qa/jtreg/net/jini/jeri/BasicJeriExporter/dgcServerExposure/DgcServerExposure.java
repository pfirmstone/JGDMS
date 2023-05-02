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
 * @bug 5099956
 * @summary The server-side DGC implementation remote object should
 * only be exported at a given endpoint while there are DGC-enabled
 * remote objects also exported at that same endpoint, in order to
 * minimize exposure to certain unmarshalling denial-of-service
 * attacks (such as a "large object" attack), given that the
 * server-side DGC implementation does not support access control.
 * Also, when the server-side DGC implementation remote object is
 * exported, it should ignore codebase annotations when unmarshalling
 * arguments, in order to minimize exposure to unmarshallling
 * denial-of-service attacks that involve downloading untrusted code.
 *
 * @library ../../../../../testlibrary
 * @build DgcServerExposure ForeignUuid
 * @build TestLibrary
 * @run main/othervm/policy=security.policy DgcServerExposure
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.AtomicInvocationHandler;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class DgcServerExposure {

    private static final Uuid DGC_ID =
	UuidFactory.create("d32cd1bc-273c-11b2-8841-080020c9e4a1");

    private static final String foreignUuidClassName = "ForeignUuid";

    private static final int PORT_A = 2019;
    private static final int PORT_B = 2020;

    private static final InvocationLayerFactory ilf =
	new BasicILFactory(null, null,
			   DgcServerExposure.class.getClassLoader());

    private static final Uuid clientID = UuidFactory.generate();
    private static long nextSequenceNum = Long.MIN_VALUE;

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 5099956\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	/*
	 * Obtain a Uuid instance that will not be locally loadable
	 * by the DGC server implementation.
	 */
	URL codebase =
	    TestLibrary.installClassInCodebase(foreignUuidClassName,
					       "codebase");
	ClassLoader foreignUuidLoader =
	    URLClassLoader.newInstance(
		new URL[] { codebase },
		DgcServerExposure.class.getClassLoader());
	Class foreignUuidClass = Class.forName(foreignUuidClassName, false,
					       foreignUuidLoader);
	Uuid foreignUuid = (Uuid) foreignUuidClass.newInstance();

	/*
	 * Establish two server endpoints and proxies for calling the
	 * DGC servers at each of those endpoints.
	 */
	ServerEndpoint seA = TcpServerEndpoint.getInstance(PORT_A);
	ServerEndpoint seB = TcpServerEndpoint.getInstance(PORT_B);

	DgcServer dgcA = makeDgcProxy(TcpEndpoint.getInstance("", PORT_A));
	DgcServer dgcB = makeDgcProxy(TcpEndpoint.getInstance("", PORT_B));

	/*
	 * Export non-DGC-enabled remote object (A0) on endpoint A.
	 *
	 * - DGC call to A should fail.
	 */
	Remote implA0 = new Remote() { };
	Exporter expA0 = new BasicJeriExporter(seA, ilf, false, false);
	expA0.export(implA0);
	System.err.println("\t{A0}");

	System.err.print("DGC call to A ");
	verifyFailure(dgcA);

	/*
	 * Export DGC-enabled remote object (B1) on endpoint B.
	 *
	 * - DGC call to A should still fail.
	 * - DGC call to B should succeed.
	 * - DGC call to B with foreign UUID class should fail.
	 */
	Remote implB1 = new Remote() { };
	Exporter expB1 = new BasicJeriExporter(seB, ilf, true, false);
	expB1.export(implB1);
	System.err.println("\t{A0}\t{B1}");

	System.err.print("DGC call to A ");
	verifyFailure(dgcA);
	System.err.print("DGC call to B ");
	verifySuccess(dgcB);
	System.err.print("DGC call to B with foreign UUID class ");
	verifyFailureWithUuid(dgcB, foreignUuid);

	/*
	 * Export DGC-enabled remote object (A1) on endpoint A.
	 *
	 * - DGC call to A should now succeed.
	 */
	Remote implA1 = new Remote() { };
	Exporter expA1 = new BasicJeriExporter(seA, ilf, true, false);
	expA1.export(implA1);
	System.err.println("\t{A0,A1}\t{B1}");

	System.err.print("DGC call to A ");
	verifySuccess(dgcA);

	/*
	 * Unexport A1.
	 *
	 * - DGC call to A should fail again.
	 * - DGC call to B should still succeed.
	 */
	expA1.unexport(true);
	System.err.println("\t{A0}\t{B1}");

	System.err.print("DGC call to A ");
	verifyFailure(dgcA);
	System.err.print("DGC call to B ");
	verifySuccess(dgcB);

	/*
	 * Export non-DGC-enabled remote object (B0) on endpoint B, to
	 * maintain listening on B.  Then clear reference to B1,
	 * letting it become garbage collected.
	 *
	 * - DGC call to B should now fail.
	 */

	Remote implB0 = new Remote() { };
	Exporter expB0 = new BasicJeriExporter(seB, ilf, false, false);
	expB0.export(implB0);
	System.err.println("\t{A0}\t{B0,B1}");

	implB1 = null;
	System.gc();
	Thread.sleep(3000);
	System.err.println("\t{A0}\t{B0}");

	System.err.print("DGC call to B ");
	verifyFailure(dgcB);

	System.err.println("TEST PASSED");
    }

    private static void verifySuccess(DgcServer dgc) {
	try {
	    dgc.dirty(clientID, nextSequenceNum++, new Uuid[] { });
	    System.err.println("succeeds as expected");
	} catch (RemoteException e) {
	    throw new RuntimeException("TEST FAILED: unexpected failure", e);
	}
    }

    private static void verifyFailure(DgcServer dgc) {
	try {
	    dgc.dirty(clientID, nextSequenceNum++, new Uuid[] { });
	    throw new RuntimeException("TEST FAILED: unexpected success");
	} catch (NoSuchObjectException e) {
	    System.err.println(
		"throws NoSuchObjectException as expected");
	} catch (RemoteException e) {
	    throw new RuntimeException("TEST FAILED: unexpected failure", e);
	}
    }

    private static void verifyFailureWithUuid(DgcServer dgc, Uuid uuid) {
	try {
	    dgc.dirty(clientID, nextSequenceNum++, new Uuid[] { uuid });
	    throw new RuntimeException("TEST FAILED: unexpected success");
	} catch (ServerException e) {
	    Throwable cause = e.getCause();
	    if (cause instanceof UnmarshalException &&
		cause.getCause() instanceof ClassNotFoundException)
	    {
		System.err.println("throws ServerException as expected");
	    } else {
		throw new RuntimeException("TEST FAILED: unexpected failure", 
					   e);
	    }
	} catch (RemoteException e) {
	    throw new RuntimeException("TEST FAILED: unexpected failure", e);
	}
    }

    /**
     * JGDMS uses AtomicMarshalInputStream and AtomicMarshalOutputStream
     * for all DGC endpoints, annotations are not written into the stream.
     * @param e
     * @return 
     */
    private static DgcServer makeDgcProxy(Endpoint e) {
	ObjectEndpoint oe = new BasicObjectEndpoint(e, DGC_ID, false);
        // The appended test output shows what happens when BasicInvocationHandler
        // is used.
	InvocationHandler ih = new AtomicInvocationHandler(oe, null, false);
	return (DgcServer)
	    Proxy.newProxyInstance(DgcServer.class.getClassLoader(),
				   new Class[] { DgcServer.class }, ih);
    }

    private interface DgcServer extends Remote {
	long dirty(Uuid clientID, long sequenceNum, Uuid[] ids)
	    throws RemoteException;
	void clean(Uuid clientID, long sequenceNum, Uuid[] ids, boolean strong)
	    throws RemoteException;
    }
}

/*
Regression test for bug 5099956
    [jtreg] 
    [jtreg] TEST_LIBRARY: C:\Users\peter\Documents\NetBeansProjects\JGDMS\qa\jtreg\JTwork\classes\net\jini\jeri\BasicJeriExporter\dgcServerExposure\ForeignUuid.class
    [jtreg] TEST_LIBRARY: C:\Users\peter\Documents\NetBeansProjects\JGDMS\qa\jtreg\JTwork\scratch\codebase\ForeignUuid.class
    [jtreg] TEST_LIBRARY: Installed class "ForeignUuid" in codebase file:C:/Users/peter/Documents/NetBeansProjects/JGDMS/qa/jtreg/JTwork/scratch/codebase/
    [jtreg] 	{A0}
    [jtreg] DGC call to A throws NoSuchObjectException as expected
    [jtreg] 	{A0}	{B1}
    [jtreg] DGC call to A throws NoSuchObjectException as expected
    [jtreg] DGC call to B succeeds as expected
    [jtreg] DGC call to B with foreign UUID class WARNING: An illegal reflective access operation has occurred
    [jtreg] WARNING: Illegal reflective access by org.apache.river.api.io.ThrowableSerializer$1 (file:/C:/Users/peter/Documents/NetBeansProjects/JGDMS/qa/jtreg/JTlib-tmp/jgdms-platform-3.1.1-SNAPSHOT.jar) to field java.lang.Throwable.detailMessage
    [jtreg] WARNING: Please consider reporting this to the maintainers of org.apache.river.api.io.ThrowableSerializer$1
    [jtreg] WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
    [jtreg] WARNING: All illegal access operations will be denied in a future release
    [jtreg] java.lang.RuntimeException: TEST FAILED: unexpected failure
    [jtreg] 	at DgcServerExposure.verifyFailureWithUuid(DgcServerExposure.java:228)
    [jtreg] 	at DgcServerExposure.main(DgcServerExposure.java:139)
    [jtreg] 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    [jtreg] 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    [jtreg] 	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    [jtreg] 	at java.base/java.lang.reflect.Method.invoke(Method.java:567)
    [jtreg] 	at com.sun.javatest.regtest.agent.MainWrapper$MainThread.run(MainWrapper.java:127)
    [jtreg] 	at java.base/java.lang.Thread.run(Thread.java:830)
    [jtreg] Caused by: java.rmi.UnmarshalException: exception unmarshalling response; nested exception is: 
    [jtreg] 	java.io.OptionalDataException
    [jtreg] 	at net.jini.jeri.BasicInvocationHandler.invokeRemoteMethodOnce(BasicInvocationHandler.java:908)
    [jtreg] 	at net.jini.jeri.BasicInvocationHandler.invokeRemoteMethod(BasicInvocationHandler.java:707)
    [jtreg] 	at net.jini.jeri.BasicInvocationHandler.invoke(BasicInvocationHandler.java:576)
    [jtreg] 	at $Proxy0.dirty(Unknown Source)
    [jtreg] 	at DgcServerExposure.verifyFailureWithUuid(DgcServerExposure.java:215)
    [jtreg] 	... 7 more
    [jtreg] Caused by: java.io.OptionalDataException
    [jtreg] 	at java.base/java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1669)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readObject(ObjectInputStream.java:464)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readObject(ObjectInputStream.java:422)
    [jtreg] 	at net.jini.io.MarshalInputStream.readAnnotation(MarshalInputStream.java:464)
    [jtreg] 	at net.jini.io.MarshalInputStream.resolveClass(MarshalInputStream.java:349)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readNonProxyDesc(ObjectInputStream.java:1941)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readClassDesc(ObjectInputStream.java:1827)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:2115)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1646)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readObject(ObjectInputStream.java:464)
    [jtreg] 	at java.base/java.io.ObjectInputStream.readObject(ObjectInputStream.java:422)
    [jtreg] 	at net.jini.jeri.BasicInvocationHandler.unmarshalThrow(BasicInvocationHandler.java:1411)
    [jtreg] 	at net.jini.jeri.BasicInvocationHandler.invokeRemoteMethodOnce(BasicInvocationHandler.java:893)
    [jtreg] 	... 11 more
*/

/*

The original MarshalInputStream did not utilise codebase annotations, however
the implementation of MarshalInputStream still reads an annotation from the 
stream, while AtomicMarshalInputStream does not unless codebase annotations are 
enabled.   If we enabled annotations in AtomicMarshalInputStream, by passing
true for useCodebaseAnnotation in the constructor, then it would be compatible,
but not secure.

If we want backward compatibility we could create additional constructors
for AtomicMarshalInputStream and AtomicMarshalOutputStream with the desired
behaviour.  But full compatibility would only be achived for basic TCP endpoints
in any case.

This means that JGDMS DGC is not fully backward compatible with River, however
services will still be compatible when DGC is turned off.  In any case River / Jini
JERI DGC should not be used as it is a security vulnerability.  Secure endpoints
with DGC enabled would provide an entry point for an attacker to unmarshall
a gadget attack as the original DGC implementation required anonymous
clients, which is no longer supported for SSLEndpoint's for JGDMS, while for
River / Jini it requres enabling insecure SSL TLS Ciphers.



98  DgcRequestDispatcher(Unreferenced unrefCallback, ObjectTable table ) {
99 	        this.unrefCallback = unrefCallback;
100 	        this.table = table;
101 	        try {
102 	            dgcDispatcher =
103 	                new BasicInvocationDispatcher(
104 	                    dgcDispatcherMethods, dgcServerCapabilities,
105 	                    null, null, this.getClass().getClassLoader())
106 	                {
107 	                    protected ObjectInputStream createMarshalInputStream(
108 	                        Object impl,
109 	                        InboundRequest request,
110 	                        boolean integrity,
111 	                        Collection context)
112 	                        throws IOException
113 	                    {
114 	                        ClassLoader loader = getClassLoader();
115 	                        return new MarshalInputStream(
116 	                            request.getRequestInputStream(),
117 	                            loader, integrity, loader,
118 	                            Collections.unmodifiableCollection(context));
119 	                        // useStreamCodebases() not invoked
120 	                    }
121 	                };
122 	        } catch (ExportException e) {
123 	            throw new AssertionError();
124 	        }
125 	        this.dgcServer = table.getDgcServer(this);
126 	    }

*/