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
 * @bug 4116082
 * 
 * @summary synopsis: rmid should not destroy group when it reports
 * inactiveGroup
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build InactiveGroup
 * @build InactiveGroup_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default InactiveGroup
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic InactiveGroup
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp InactiveGroup
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.jeri.*;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class InactiveGroup
	implements ActivateMe, Serializable
{

    private ActivationLibrary.ExportHelper helper;
    private Remote stub;

    /**
     * Activates/exports the object using 1.4 APIs if the
     * value in obj is "basic"; otherwise it uses pre-1.4 APIs
     * to export the object.
     */
    public InactiveGroup(ActivationID id, MarshalledObject obj)
	throws ActivationException, RemoteException
    {
	helper = new ActivationLibrary.ExportHelper(obj, this, id);
	stub = helper.export();
    }

    public InactiveGroup(String exportType) throws RemoteException {
	// stub = UnicastRemoteObject.exportObject(this, 0);
	if (exportType.equals("basic")) {
	    BasicJeriExporter basicExporter =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), true, true);
	    stub = basicExporter.export(this);
	} else {
	    stub = UnicastRemoteObject.exportObject(this, 0);
	}
    }

    private Object writeReplace() {
	return stub;
    }

    public void ping()
    {}

    public ActivateMe getUnicastVersion(String exportType)
	throws RemoteException
    {
	return new InactiveGroup(exportType);
    }

    public void shutdown() throws Exception {
	helper.deactivate();
    }

    public static void main(String[] args) {

	System.out.println("\nRegression test for bug 4116082\n");
	
	TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

	String exportType =
	    TestLibrary.getProperty("test.rmi.exportType","default");
	System.err.println("exportType: " + exportType);

	RMID rmid = null;
	
	try {
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.start();

	    /* Cause activation groups to have a security policy that will
	     * allow security managers to be downloaded and installed
	     */
	    Properties p = new Properties();
	    // this test must always set policies/managers in its
	    // activation groups
	    p.put("java.security.policy", 
		  TestParams.defaultGroupPolicy);
	    p.put("java.security.manager", 
		  TestParams.defaultSecurityManager);

	    /*
	     * Create descriptor and activate object in a separate VM.
	     */
	    System.err.println("Creating descriptor");
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(p, null);
	    ActivationGroupID groupID =
		ActivationGroup.getSystem().registerGroup(groupDesc);
	    ActivationDesc desc =
		new ActivationDesc(groupID, "InactiveGroup", null,
				   new MarshalledObject(exportType));
	    
	    System.err.println("Registering descriptor");
	    ActivateMe activatableObj = (ActivateMe)
		ActivationLibrary.register(exportType, desc);
	    
	    System.err.println("Activate object via method call");
	    activatableObj.ping();

	    /*
	     * Create a unicast object in the activatable object's VM.
	     */
	    System.err.println("Obtain unicast object");
	    ActivateMe unicastObj =
		activatableObj.getUnicastVersion(exportType);

	    /*
	     * Make activatable object (and therefore group) inactive.
	     */
	    System.err.println("Make activatable object inactive");
	    activatableObj.shutdown();

	    /*
	     * Ping the unicast object a few times to make sure that the
	     * activation group's process hasn't gone away.
	     */
	    System.err.print("Ping unicast object for existence");
	    for (int i = 0; i < 10; i++) {
		System.err.print(".");
		unicastObj.ping();
		Thread.sleep(500);
	    }
	    System.err.println("");

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
	    } catch (Exception thisShouldFail) {
		System.err.println("Test passed: couldn't reach unicast obj: " +
				   thisShouldFail.getMessage());
		return;
	    }

	    TestLibrary.bomb("Test failed: unicast obj accessible after group reactivates",
		 null);
	    
	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
