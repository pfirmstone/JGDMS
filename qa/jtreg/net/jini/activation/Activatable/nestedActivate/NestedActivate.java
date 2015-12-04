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
 * @bug 4138056
 *
 * @summary synopsis: Activating objects from an Activatable constructor causes deadlock
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build NestedActivate
 * @build NestedActivate_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default NestedActivate
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic NestedActivate
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp NestedActivate
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.jeri.*;

public class NestedActivate
	implements ActivateMe, Serializable
{

    private ActivationLibrary.ExportHelper helper;

    private static Exception exception = null;
    private static boolean done = false;
    private ActivateMe obj = null;

    /**
     * Activates/exports the object using 1.4 APIs if the
     * value in obj is "basic"; otherwise it uses pre-1.4 APIs
     * to export the object.
     */
    public NestedActivate(ActivationID id, MarshalledObject mobj)
	throws Exception
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
	System.err.println(
	    "NestedActivate<>: activating object, " + exportType);
	helper = new ActivationLibrary.ExportHelper(exportType, this, id);
	helper.export();

	obj = (ActivateMe) stuff[1];
	if (obj != null) {
	    System.err.println("NestedActivate<>: ping obj to activate");
	    obj.ping();
	    System.err.println("NestedActivate<>: ping completed");
	}
    }

    private Object writeReplace() {
	return helper.getStub();
    }

    public void ping()
    {}

    public void unregister() throws Exception {
	Activatable.unregister(helper.getActivationID());
    }
    
    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
	helper.deactivate();
	if (obj != null)
	    obj.shutdown();
    }

    public static void main(String[] args) {

 	System.err.println("\nRegression test for bug 4138056\n");
	
	TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

	final String exportType =
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
	    final Properties p = new Properties();
	    // this test must always set policies/managers in its
	    // activation groups
	    p.put("java.security.policy", 
		  TestParams.defaultGroupPolicy);
	    p.put("java.security.manager", 
		  TestParams.defaultSecurityManager);

	    Thread t = new Thread() {
		public void run () {
		    try {
			System.err.println("Creating group descriptor");
			ActivationGroupDesc groupDesc =
			    new ActivationGroupDesc(p, null);
			ActivationGroupID groupID =
			    ActivationGroup.getSystem().
			    registerGroup(groupDesc);
			
			System.err.println("Creating descriptor: object 1");
			Object[] stuff = new Object[] { exportType, null };
			ActivationDesc desc1 =
			    new ActivationDesc(groupID, "NestedActivate",
					       null,
					       new MarshalledObject(stuff));
	    
			System.err.println("Registering descriptor: obj1");
			ActivateMe obj1 = (ActivateMe)
			    ActivationLibrary.register(exportType, desc1);
			
			System.err.println("Creating descriptor: object 2");
			stuff[1] = obj1;
			ActivationDesc desc2 =
			    new ActivationDesc(groupID, "NestedActivate", null,
					       new MarshalledObject(stuff));

			System.err.println("Registering descriptor: obj2");
			ActivateMe obj2 = (ActivateMe)
			    ActivationLibrary.register(exportType, desc2);
	    
			System.err.println("Activating object 2");
			obj2.ping();

			System.err.println("Deactivating objects");
			obj2.shutdown();
		    } catch (Exception e) {
			exception = e;
		    }
		    done = true;
		}
	    };

	    t.start();
	    t.join(35000);

	    if (exception != null) {
		TestLibrary.bomb("test failed", exception);
	    } else if (!done) {
		TestLibrary.bomb("test failed: not completed before timeout", null);
	    } else {
		System.err.println("Test passed");
	    }
	    
	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
