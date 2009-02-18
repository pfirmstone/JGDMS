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

    private static DgcServer makeDgcProxy(Endpoint e) {
	ObjectEndpoint oe = new BasicObjectEndpoint(e, DGC_ID, false);
	InvocationHandler ih = new BasicInvocationHandler(oe, null);
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
