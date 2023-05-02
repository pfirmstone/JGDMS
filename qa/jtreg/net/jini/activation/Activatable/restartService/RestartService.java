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
 * @bug 4095165

 * @summary synopsis: activator should restart daemon services
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build RestartService
 * @build RestartService_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default RestartService
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic RestartService
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp RestartService
 */

import java.io.*;
import java.rmi.*;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Properties;
import java.util.Vector;
import net.jini.jeri.*;

public class RestartService
	implements ActivateMe, Serializable
{

    private ActivationLibrary.ExportHelper helper;
    private Remote stub;
    private static Object lock = new Object();
    private Vector responders = new Vector();

    private static final String RESTARTABLE = "restartable";
    private static final String ACTIVATABLE = "activatable";


    public RestartService(ActivationID id, MarshalledObject mobj)
	throws ActivationException, RemoteException
    {
	Object[] stuff;

	try {
	    stuff = (Object[]) mobj.get();
	} catch (Exception e) {
	    System.err.println(
		"unable to obtain stub from marshalled object: " +
		e.getClass().getName());
	    e.printStackTrace();
	    return;
	}
	
	String exportType = (String) stuff[0];
	helper = new ActivationLibrary.ExportHelper(exportType, this, id);
	stub = helper.export();
	
	String responder = (String) stuff[1];
	System.err.println(responder + " service started");
	ActivateMe obj = (ActivateMe) stuff[2];
	obj.ping(responder);
    }

    private Object writeReplace() {
	return stub;
    }

    public RestartService() throws RemoteException {
	stub = UnicastRemoteObject.exportObject(this, 0);
    }

    public void ping(String responder) {
	System.err.println("RestartService: received ping from " + responder);
	synchronized (lock) {
	    responders.add(responder);
	    lock.notify();
	}
    }

    public boolean receivedPing(String responder) {
	return responders.contains(responder);
    }

    public static void main(String[] args) {

	System.out.println("\nRegression test for bug 4095165\n");
	
	TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");
	
	String exportType =
	    TestLibrary.getProperty("test.rmi.exportType","default");
	System.err.println("exportType: " + exportType);
	    
	RMID rmid = null;
	RestartService unicastObj = null;
	
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
	     * Create unicast object to be contacted when service is activated.
	     */
	    unicastObj = new RestartService();
	    
	    /*
	     * Create and register descriptors for a restartable and
	     * non-restartable service (respectively) in a group other than
	     * this VM's group.
	     */
	    System.err.println("Creating descriptors");
	    
	    Object[] stuff =
		new Object[] { exportType, RESTARTABLE, unicastObj };
	    MarshalledObject restartMobj = new MarshalledObject(stuff);
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(p, null);
	    
	    stuff[1] = ACTIVATABLE;
	    MarshalledObject activateMobj = new MarshalledObject(stuff);
	    ActivationGroupID groupID =
		ActivationGroup.getSystem().registerGroup(groupDesc);
	    ActivationDesc restartableDesc =
		new ActivationDesc(groupID, "RestartService", null,
				   restartMobj, true);
	    
	    ActivationDesc activatableDesc =
		new ActivationDesc(groupID, "RestartService", null,
				   activateMobj, false);
	    
	    System.err.println("Registering descriptors");
	    ActivateMe restartableObj = (ActivateMe)
		ActivationLibrary.register(exportType, restartableDesc);
	    ActivateMe activatableObj = (ActivateMe)
		ActivationLibrary.register(exportType, activatableDesc);

	    /*
	     * Restart rmid; it should start up the restartable service
	     */
	    rmid.restart();

	    /*
	     * Wait for service to be automatically restarted.
	     */
	    boolean gotPing = false;
	    for (int i = 0; i < 15; i++) {
		synchronized (lock) {
		    if (unicastObj.receivedPing(RESTARTABLE) != true) {
			lock.wait(5000);
			if (unicastObj.receivedPing(RESTARTABLE) == true) {
			    System.err.println("Test1 passed: rmid restarted" +
					       " service");
			    gotPing = true;
			    break;
			}
		    } else {
			gotPing = true;
			break;
		    }
		}
	    }

	    if (gotPing == false)
		TestLibrary.bomb("Test1 failed: service not restarted by timeout", null);

	    /*
	     * Make sure activatable services wasn't automatically restarted.
	     */
	    synchronized (lock) {
		if (unicastObj.receivedPing(ACTIVATABLE) != true) {
		    lock.wait(5000);
		    if (unicastObj.receivedPing(ACTIVATABLE) != true) {
			System.err.println("Test2 passed: rmid did not " +
					   "restart activatable service");
			return;
		    }
		}
		
		TestLibrary.bomb("Test2 failed: activatable service restarted!", null);
	    }


	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	    if (unicastObj != null) {
		TestLibrary.unexport(unicastObj);
	    }
	}
    }
}
