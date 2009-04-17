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
 * @bug 4115296
 *
 * @summary synopsis: NoSuchObjectException not thrown for non-existent
 * activatable objects
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build NonExistentActivatable
 * @build NonExistentActivatable_Stub
 * @run main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default NonExistentActivatable
 * @run main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic NonExistentActivatable
 * @run main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp NonExistentActivatable
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.jeri.*;

public class NonExistentActivatable
	implements ActivateMe, Serializable
{

    private ActivationLibrary.ExportHelper helper;

    public NonExistentActivatable(ActivationID id, MarshalledObject obj)
	throws ActivationException, RemoteException
    {
	helper = new ActivationLibrary.ExportHelper(obj, this, id);
	helper.export();
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
    }
    
    public static void main(String[] args) {

	System.out.println("\nRegression test for bug 4115331\n");
	
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

	    System.err.println("Create activation group");
	    ActivationGroupDesc groupDesc = new ActivationGroupDesc(p, null);
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);
	    
	    System.err.println("Creating descriptor");
	    ActivationDesc desc =
		new ActivationDesc(groupID, "NonExistentActivatable", null,
				   new MarshalledObject(exportType));
	    
	    System.err.println("Registering descriptor");
	    ActivateMe obj = (ActivateMe)
		ActivationLibrary.register(exportType, desc);
	    
	    System.err.println("Activate object via method call");
	    obj.ping();

	    System.err.println("Unregister object");
	    obj.unregister();

	    System.err.println("Make object inactive");
	    obj.shutdown();
	    // give server side a chance to actually go inactive
	    Thread.sleep(6000);

	    System.err.println("Reactivate object");
	    try {
		obj.ping();
		TestLibrary.bomb("ping succeeded");
	    } catch (NoSuchObjectException e) {
		System.err.println("Test succeeded: " +
				   "NoSuchObjectException caught");
		return;
	    } catch (Exception e) {
		TestLibrary.bomb("Test failed: exception other than NoSuchObjectException",
		     e);
	    }

	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
