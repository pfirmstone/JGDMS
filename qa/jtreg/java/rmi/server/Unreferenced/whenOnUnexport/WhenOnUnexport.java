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
 * @bug 4849581
 * @summary When a remote object implements the Unreferenced interface,
 * the implementation should invoke its unreferenced() method when the number
 * of known remote client references through DGC-enabled exports transitions
 * from greater than zero to zero AND the remote object is still exported
 * by at least one DGC-enabled exporter-- but not if it's only exported
 * with non-DGC-enabled exporters.
 * 
 *
 * @library ../../../../../testlibrary
 * @build WhenOnUnexport
 * @run main/othervm/timeout=120 WhenOnUnexport
 */

import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.Unreferenced;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class WhenOnUnexport implements Remote, Unreferenced {

    private static final String BINDING = "WhenOnUnexport";
    private static final long TIMEOUT = 5000;

    private Object lock = new Object();
    private boolean unreferencedInvoked;

    public void unreferenced() {
	System.err.println("unreferenced() method invoked");
	synchronized (lock) {
	    unreferencedInvoked = true;
	    lock.notify();
	}
    }

    public static void main(String[] args) {

	System.err.println(
	    "\nTest to verify correction interaction of " +
	    "unexport and unreferenced\n");

	WhenOnUnexport obj = new WhenOnUnexport();

	try {
	    LocateRegistry.createRegistry(TestLibrary.REGISTRY_PORT);
	    System.err.println("created registry");

	    Exporter exporterA =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), true, false);
	    Remote stubA = exporterA.export(obj);
	    System.err.println("exported remote object once");

	    Exporter exporterB = 
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), true, false);
	    Remote stubB = exporterB.export(obj);
	    System.err.println("exported remote object twice");

	    Exporter exporterC =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), false, false);
	    Remote stubC = exporterC.export(obj);
	    System.err.println("exported remote object thrice");

	    Registry registry = LocateRegistry.getRegistry(
		"", TestLibrary.REGISTRY_PORT);

	    registry.bind(BINDING, stubA);
	    System.err.println("bound first stub in registry");

	    synchronized (obj.lock) {
		obj.unreferencedInvoked = false;

		exporterA.unexport(true);
		System.err.println("unexported first exporter");
		System.err.println("waiting for unreferenced() callback " +
				   "(SHOULD happen)...");
		obj.lock.wait(TIMEOUT);

		if (obj.unreferencedInvoked) {
		    System.err.println(
			"unreferenced() invoked with export remanining");
		} else {
		    throw new RuntimeException(
			"TEST FAILED: unrefereced() not invoked after " +
			((double) TIMEOUT / 1000.0) + " seconds");
		}
	    }

	    registry.rebind(BINDING, stubB);
	    System.err.println("bound second stub in registry");

	    synchronized (obj.lock) {
		obj.unreferencedInvoked = false;

		exporterB.unexport(true);
		System.err.println("unexported second exporter");
		System.err.println("waiting for unreferenced() callback " +
				   "(should NOT happen)...");
		obj.lock.wait(TIMEOUT);

		if (obj.unreferencedInvoked) {
		    throw new RuntimeException(
			"TEST FAILED: unreferenced() invoked " +
			"with no exports remaining");
		} else {
		    System.err.println(
			"unrefereced() not invoked after " +
			((double) TIMEOUT / 1000.0) + " seconds");
		}
	    }

	    System.err.println("TEST PASSED");

	} catch (Exception e) {
	    if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw new RuntimeException(
		    "TEST FAILED: unexpected exception: " + e.toString());
	    }
	} finally {
	}
    }
}
