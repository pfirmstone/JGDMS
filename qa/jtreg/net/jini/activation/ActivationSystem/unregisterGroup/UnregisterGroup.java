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
 * @bug 4134233
 * @bug 4213186
 *
 * @summary synopsis: ActivationSystem.unregisterGroup should unregister objects in group
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID JavaVM StreamPipe
 * @build ActivateMe CallbackInterface
 * @build UnregisterGroup
 * @build UnregisterGroup_Stub
 * @build Callback_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=120 UnregisterGroup
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Properties;

class Callback extends UnicastRemoteObject implements CallbackInterface {

    private int num_deactivated = 0;

    public Callback() throws RemoteException {
    }

    public synchronized void inc() throws RemoteException {
	num_deactivated++;
	notifyAll();
    }

    public synchronized int getNumDeactivated() throws RemoteException {
	return(num_deactivated);
    }
}

public class UnregisterGroup
	extends Activatable
	implements ActivateMe, Runnable
{
    private static final int NUM_OBJECTS = 10;
    private static int PORT = 2006;

    public UnregisterGroup(ActivationID id, MarshalledObject mobj)
	throws Exception
    {
	super(id, 0);
    }

    public void ping() {
    }

    public void unregister() throws Exception {
	super.unregister(super.getID());
    }
    
    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception {
	Thread t = new Thread(this,"UnregisterGroup");
	t.setDaemon(false);
	t.start();
    }

    /**
     * To support exiting of group VM as a last resort
     */
    public void justGoAway() {
	System.exit(0);
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run() {

	ActivationLibrary.deactivate(this, getID());

	try {
	    CallbackInterface cobj =
		(CallbackInterface)Naming.lookup("//:" + PORT + "/Callback");
	    cobj.inc();
	} catch (Exception e) {
	    System.err.println("cobj.inc exception");
	    e.printStackTrace();
	}

    }

    public static void main(String[] args) throws Exception {

 	System.err.println("\nRegression test for bug 4134233\n");
	
	TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

	Registry registry = LocateRegistry.createRegistry(PORT);
	// create reg and export callback object
	Callback robj = new Callback();
	registry.bind("Callback", robj);

	ActivateMe lastResortExitObj = null;
	RMID.removeLog();
	RMID rmid = RMID.createRMID();
	rmid.start();

	try {

	    /* Cause activation groups to have a security policy that will
	     * allow security managers to be downloaded and installed
	     */
	    final Properties p = new Properties();
	    // this test must always set policies/managers in its
	    // activation groups
	    p.put("java.security.policy", 
		  TestParams.defaultGroupPolicy);
	    p.put("java.security.manager", 
		  TestParams.defaultSecurityManager);

	    System.err.println("Creating group descriptor");
	    ActivationGroupDesc groupDesc = new ActivationGroupDesc(p, null);
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);

	    ActivateMe[] obj = new ActivateMe[NUM_OBJECTS];

	    for (int i = 0; i < NUM_OBJECTS; i++) {
		System.err.println("Creating descriptor: " + i);
		ActivationDesc desc =
		    new ActivationDesc(groupID, "UnregisterGroup", null, null);
		System.err.println("Registering descriptor: " + i);
		obj[i] = (ActivateMe) Activatable.register(desc);
		lastResortExitObj = obj[0];
		System.err.println("Activating object: " + i);
		obj[i].ping();
	    }

	    System.err.println("Unregistering group");
	    system.unregisterGroup(groupID);

	    try {
		System.err.println("Get the group descriptor");
		system.getActivationGroupDesc(groupID);
		TestLibrary.bomb("test failed: group still registered");
	    } catch (UnknownGroupException e) {
		System.err.println("group unregistered");
	    }

	    /*
	     * Deactivate objects so group VM will exit.
	     */
	    for (int i = 0; i < NUM_OBJECTS; i++) {
		System.err.println("Deactivating object: " + i);
		obj[i].shutdown();
		obj[i] = null;
	    }

	    // Wait for the object deactivation to take place first
	    System.err.println("waiting for objects");
	    synchronized (robj) {
		while (robj.getNumDeactivated() < NUM_OBJECTS) {
		    robj.wait();
		}
	    }
	    lastResortExitObj = null;
	    UnicastRemoteObject.unexportObject(robj, true);
	    
	} finally {
	    if (lastResortExitObj != null) {
		try {
		    lastResortExitObj.justGoAway();
		} catch (Exception munch) {
		}
	    }

	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
