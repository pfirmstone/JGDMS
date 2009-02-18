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
 * @bug 4173960
 * @summary synopsis: Activatable objects cannot be restarted.
 * @author Laird Dornin
 *
 * @library ../../../../../testlibrary
 * @build ActivationLibrary TestLibrary RMID
 * @build ActivateMe
 * @build ForceLogSnapshot
 * @build ForceLogSnapshot_Stub
 * @run main/othervm/policy=security.policy/timeout=700 -Dtest.rmi.exportType=default ForceLogSnapshot
 * @run main/othervm/policy=security.policy/timeout=700 -Dtest.rmi.exportType=basic ForceLogSnapshot
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.activation.ActivationExporter;
import net.jini.export.Exporter;
import net.jini.jeri.*;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class ForceLogSnapshot
    implements ActivateMe, Serializable
{
    /** how many activatable remote objects to create to test rmid */
    final public static int HOW_MANY = 50;
    final public static int NUM_GROUPS = 4;

    private Exporter exporter;
    private Remote stub;
    private ActivationID id;
    private Vector responders = new Vector();

    private static final String RESTARTABLE = "restartable";
    private static final String ACTIVATABLE = "activatable";

    private static Object lock = new Object();
    private static boolean[] restartedObjects = new boolean[HOW_MANY];
    private static boolean[] activatedObjects = new boolean[HOW_MANY];

    /**
     * Activates/exports the object using 1.4 APIs if the
     * value in obj is "basic"; otherwise it uses pre-1.4 APIs
     * to export the object.
     */
    public ForceLogSnapshot(ActivationID id, MarshalledObject mobj)
    	throws ActivationException, RemoteException
    {
	this.id = id;
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
	if (exportType.equals("basic")) {
	    BasicJeriExporter basicExp =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), true, true);
	    exporter = new ActivationExporter(id, basicExp);
	    stub =  exporter.export(this);
	} else {
	    stub = Activatable.exportObject(this, id, 0);
	}

	int intId = ((Integer) stuff[1]).intValue();
	String responder = (String) stuff[2];
	ActivateMe obj = (ActivateMe) stuff[3];

	System.err.println(responder + " service started");
	obj.ping(intId, responder);
    }

    public ForceLogSnapshot(String exportType) throws RemoteException {
	if (exportType.equals("basic")) {
	    exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					     new BasicILFactory(),
					     true, true);
	    stub = exporter.export(this);
	} else {
	    stub = UnicastRemoteObject.exportObject(this, 0);
	}
    }

    private Object writeReplace() {
	return stub;
    }

    private Exporter getExporter() {
	return exporter;
    }

    public void ping(int intId, String responder) {
	System.err.println("ForceLogSnapshot: received ping from " +
			   responder);
	if (responder.equals(RESTARTABLE)) {
	    synchronized (lock) {
		restartedObjects[intId] = true;
	    }
	} else if (responder.equals(ACTIVATABLE)) {
	    synchronized (lock) {
		activatedObjects[intId] = true;
	    }
	}
    }

    public void crash() {
	System.exit(0);
    }

    public ActivationID getID() {
	return id;
    }

    public static void main(String[] args) {

	System.out.println("\nRegression test for bug 4173960\n");

	TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

	String exportType =
	    TestLibrary.getProperty("test.rmi.exportType","default");
	System.err.println("exportType: " + exportType);

	RMID rmid = null;
	ForceLogSnapshot[] unicastObjs = new ForceLogSnapshot[HOW_MANY];

	try {
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.slowStart();

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

	    Object[][] stuff = new Object[HOW_MANY][];
	    MarshalledObject restartMobj = null;
	    ActivationGroupDesc groupDesc = null;
	    MarshalledObject activateMobj = null;
	    ActivationGroupID[] groupIDs = new ActivationGroupID[NUM_GROUPS];
	    ActivationDesc restartableDesc = null;
	    ActivationDesc activatableDesc = null;
	    ActivateMe[] restartableObj = new ActivateMe[HOW_MANY];
	    ActivateMe[] activatableObj = new ActivateMe[HOW_MANY];

	    /*
	     * Create unicast object to be contacted when service is activated.
	     */
	    int group = 0;
	    int groupNo = 0;
	    for (int i = 0 ; i < HOW_MANY ; i ++ ) {

		System.err.println("Creating descriptors and remote objects");

		unicastObjs[i] = new ForceLogSnapshot(exportType);

		/*
		 * Create and register descriptors for a restartable and
		 * non-restartable service (respectively) in a group other than
		 * this VM's group.
		 */
		stuff[i] = new Object[] { exportType, new Integer(i),
					      RESTARTABLE, unicastObjs[i] };
		restartMobj = new MarshalledObject(stuff[i]);

		stuff[i][2] = ACTIVATABLE;
		activateMobj = new MarshalledObject(stuff[i]);

		groupDesc =
		    new ActivationGroupDesc(p, null);

		if (i < NUM_GROUPS) {
		    groupNo = i;
		    groupIDs[groupNo] =
			ActivationGroup.getSystem().
			registerGroup(groupDesc);
		} else {
		    groupNo = (group++)%NUM_GROUPS;
		}

		System.err.println("Objects group number: " + groupNo);

		restartableDesc =
		    new ActivationDesc(groupIDs[groupNo], "ForceLogSnapshot",
				       null,
				       restartMobj, true);

		activatableDesc =
		    new ActivationDesc(groupIDs[groupNo], "ForceLogSnapshot",
				       null, activateMobj, false);

		System.err.println("Registering descriptors");
		restartableObj[i] = (ActivateMe)
		    ActivationLibrary.register(exportType, restartableDesc);
		activatableObj[i] = (ActivateMe)
		    ActivationLibrary.register(exportType, activatableDesc);
		System.err.println("registered activatable #: " + i);

		// start reusing groups if we need to do so.
	    }

	    int repeatOnce = 1;
	    do {

		/*
		 * Restart rmid; it should start up the restartable service
		 */
		rmid.destroy();
		rmid.slowStart();

		if (howManyRestarted(restartedObjects, 10) < HOW_MANY) {
		    TestLibrary.bomb("Test1 failed: a service would not " +
				     "restart");
		}
		System.err.println("Test1 passed: all service(s) restarted");

		/*
		 * Make sure no activatable services were automatically
		 * restarted.
		 */
		if (howManyRestarted(activatedObjects, 2) != 0) {
		    TestLibrary.bomb(
			"Test2 failed: activatable service restarted!",
			null);
		}
		System.err.println("Test2 passed: rmid did not " +
				   "restart activatable service(s)");

		if (repeatOnce > 0) {
		    try {
			System.err.println("\nCrash restartable object");
			for (int i = 0 ; i < HOW_MANY ; i ++) {
			    restartableObj[i].crash();
			}
		    } catch (Exception e) {
		    }
		}

	    } while (repeatOnce-- > 0);


	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	    for (int i = 0 ; i < HOW_MANY ; i ++) {
		if (unicastObjs[i] != null) {
		    TestLibrary.unexport(unicastObjs[i],
					 unicastObjs[i].getExporter());
		}
	    }
	}
    }

    /**
     * Check to see how many services have been automatically
     * restarted.
     */
    private static int howManyRestarted(boolean[] startedObjects, int retries)
    {
	int succeeded = 0;
	int restarted = 0;
	int atry = 0;

	while ((restarted < HOW_MANY) && (atry < retries)) {
	    restarted = 0;
	    for (int j = 0 ; j < HOW_MANY ; j ++ ) {
		synchronized(lock) {
		    if (startedObjects[j]) {
			restarted ++;
		    }
		}
	    }
	    System.err.println("not all objects restarted, retrying...");
	    try {
		Thread.sleep(10000);
	    } catch (InterruptedException ie) {
	    }
	    atry ++;
	}
	return restarted;
    }
}
