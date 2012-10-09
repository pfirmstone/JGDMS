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
/* @test 1.1 00/12/06
 * @summary When an object is retrieved from a MarshalledObject, callbacks
 * that were registered by objects in the graph for execution when the
 * unmarshalling is done should get executed.  This is verified by way of
 * a BasicJeriExporter-exported stub getting unmarshalled, and then garbage
 * collected, in which case the impl's unreferenced() method should get
 * invoked.
 * 
 *
 * @library ../../../../../testlibrary
 * @build MarshalledObjectGet
 * @run main/othervm/timeout=120 MarshalledObjectGet
 */

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.server.Unreferenced;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class MarshalledObjectGet implements Remote, Unreferenced {

    private static final String BINDING = "MarshalledObjectGet";
    private static final long GC_INTERVAL = 6000;
    private static final long TIMEOUT = 50000;

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
	    "MarshalledObject.get and DGC registration\n");

	/*
	 * Set the interval that RMI will request for GC latency (before RMI
	 * gets initialized and this property is read) to an unrealistically
	 * small value, so that this test shouldn't have to wait too long.
	 */
	System.setProperty("sun.rmi.dgc.client.gcInterval",
	    String.valueOf(GC_INTERVAL));

	MarshalledObjectGet obj = new MarshalledObjectGet();
	Exporter exporter = null;

	try {
	    exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					     new BasicILFactory(), true, true);
	    Remote stub = exporter.export(obj);
	    System.err.println("exported remote object");

	    MarshalledObject mobj = new MarshalledObject(stub);
	    Remote unmarshalledStub = (Remote) mobj.get();
	    System.err.println("unmarshalled stub from marshalled object");

	    synchronized (obj.lock) {
		obj.unreferencedInvoked = false;

		unmarshalledStub = null;
		System.gc();
		System.err.println("cleared unmarshalled stub");
		System.err.println("waiting for unreferenced() callback " +
				   "(SHOULD happen)...");
		obj.lock.wait(TIMEOUT);

		if (obj.unreferencedInvoked) {
		    // TEST PASSED
		} else {
		    throw new RuntimeException(
			"TEST FAILED: unrefereced() not invoked after " +
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
	    if (exporter != null) {
		exporter.unexport(true);
	    }
	}
    }
}
