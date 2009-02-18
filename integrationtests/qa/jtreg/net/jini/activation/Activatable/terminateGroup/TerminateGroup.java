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
 * @bug 4454736
 * 
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build TerminateGroup
 * @run main/othervm/policy=security.policy/timeout=240 TerminateGroup
 */

import java.io.*;
import java.lang.reflect.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.activation.ActivationExporter;
import net.jini.export.Exporter;
import net.jini.jeri.*;
import net.jini.jeri.tcp.*;

public class TerminateGroup implements ActivateMe, Serializable {

    private Exporter exporter;
    private Remote stub;
    private ActivationID id;

    private static TerminateGroup impl;

    public TerminateGroup(ActivationID id, MarshalledObject obj)
	throws IOException, ClassNotFoundException
    {
	this.id = id;
	int port = 0;
	if (obj != null) {
	    port = ((Integer) obj.get()).intValue();
	}
	Exporter basicExporter =
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(port),
				  new BasicILFactory(), false, true);
	exporter = new ActivationExporter(id, basicExporter);
	stub = exporter.export(this);
    }

    public TerminateGroup() throws ExportException {
	exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					 new BasicILFactory(), true, true);
	stub = exporter.export(this);
    }

    private Object writeReplace() {
	return stub;
    }

    public void ping() {
    }

    public ActivateMe getUnicastVersion() throws Exception {
	impl = new TerminateGroup();
	ActivateMe stub = (ActivateMe) impl.stub;
	// get the port it was exported on
	BasicInvocationHandler h =
	    (BasicInvocationHandler) Proxy.getInvocationHandler(stub);
	Endpoint e =
	    ((BasicObjectEndpoint) h.getObjectEndpoint()).getEndpoint();
	int port = ((TcpEndpoint) e).getPort();
	ActivationSystem sys = ActivationGroup.getSystem();
	// set it as the port to use when reactivating
	ActivationDesc desc = sys.getActivationDesc(id);
	desc = new ActivationDesc(desc.getGroupID(),
				  desc.getClassName(),
				  desc.getLocation(),
				  new MarshalledObject(new Integer(port)),
				  desc.getRestartMode());
	sys.setActivationDesc(id, desc);
	// add a shutdown hook to slow down group process termination
	Runtime.getRuntime().addShutdownHook(
	    new Thread(new Runnable() {
		public void run() {
		    try {
			System.err.println("shutdown hook sleeping");
			Thread.sleep(20000);
			System.err.println("shutdown hook exiting");
		    } catch (InterruptedException e) {
		    }
		}
	    }, "Shutdown Wait"));
	return stub;
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() {
	new Thread(new Runnable() {
	    public void run() {
		ActivationLibrary.deactivate(TerminateGroup.this,
					     id, exporter);
	    }
	}, "TerminateGroup").start();
    }

    public static void main(String[] args) {

	TestLibrary.suggestSecurityManager(null);

	RMID rmid = null;
	
	try {
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.start();

	    /*
	     * Create descriptor and activate object in a separate VM.
	     */
	    System.err.println("Creating descriptor");
	    Properties p = new Properties();
	    p.put("java.security.policy", 
		  TestParams.defaultGroupPolicy);
	    p.put("java.security.manager", 
		  TestParams.defaultSecurityManager);
	    ActivationGroupDesc groupDesc = new ActivationGroupDesc(p, null);
	    ActivationGroupID groupID =
		ActivationGroup.getSystem().registerGroup(groupDesc);
	    ActivationDesc desc =
		new ActivationDesc(groupID, "TerminateGroup", null, null);
	    
	    System.err.println("Registering descriptor");
	    ActivateMe activatableObj =
		(ActivateMe) ActivationLibrary.register("basic", desc);
	    
	    System.err.println("Activate object via method call");
	    activatableObj.ping();

	    /*
	     * Create a unicast object in the activatable object's VM.
	     */
	    System.err.println("Obtain unicast object");
	    ActivateMe unicastObj = activatableObj.getUnicastVersion();

	    /*
	     * Make activatable object (and therefore group) inactive.
	     */
	    System.err.println("Make activatable object inactive");
	    activatableObj.shutdown();

	    /*
	     * Ping the unicast object a few times to make sure that the
	     * activation group's process hasn't gone away.
	     */
	    System.err.println("Ping unicast object for existence");
	    for (int i = 0; i < 10; i++) {
		unicastObj.ping();
		Thread.sleep(500);
	    }

	    /*
	     * Now, reactivate the activatable object; the unicast object
	     * should no longer be accessible, since reactivating the
	     * activatable object should kill the previous group's VM
	     * and the unicast object along with it.
	     */
	    System.err.println("Reactivate activatable obj");
	    activatableObj.ping();

	    try {
		System.err.println("Ping unicast object again");
		unicastObj.ping();
	    } catch (Exception e) {
		System.err.println("Test passed: unicast obj gone: " +
				   e.getMessage());
		return;
	    }
	    TestLibrary.bomb("Test failed: unicast obj still accessible",
			     null);
	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
