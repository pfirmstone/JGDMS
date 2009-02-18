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
 * @author Laird Dornin
 * @bug 4164971
 * @summary allow non-public activatable class and/or constructor
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID
 * @build ActivateMe
 * @build CreatePrivateActivatable$PrivateActivatable_Stub
 * @build CreatePrivateActivatable
 * @run main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default CreatePrivateActivatable
 * @run main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic CreatePrivateActivatable
 * @run main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp CreatePrivateActivatable
 */

import java.io.*;
import java.lang.reflect.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.jeri.*;

/**
 * Test creates a private inner class Activatable object with a
 * private constructor and makes sure that the object can be
 * activated.
 */
public class CreatePrivateActivatable
{
    private static class PrivateActivatable
	implements ActivateMe, Serializable
    {
	private ActivationLibrary.ExportHelper helper;
	
	private PrivateActivatable(ActivationID id, MarshalledObject mobj)
	    throws ActivationException, RemoteException
	{
	    helper = new ActivationLibrary.ExportHelper(mobj, this, id);
	    helper.export();
	}

	private Object writeReplace() {
	    return helper.getStub();
	}

	public void ping()
	{}

	public void shutdown() throws Exception {
	    helper.deactivate();
	}
    }
    
    public static void main(String[] args)  {
	RMID rmid = null;
	ActivateMe obj;

	System.err.println("\nRegression test for bug 4164971\n");
	System.err.println("java.security.policy = " +
			   System.getProperty("java.security.policy", "no policy"));

	CreatePrivateActivatable server;
	try {
	    TestLibrary.suggestSecurityManager(TestParams.defaultSecurityManager);

	    String exportType =
		TestLibrary.getProperty("test.rmi.exportType","default");
	    System.err.println("exportType: " + exportType);
	    
	    // start an rmid.
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
	     * Activate an object by registering its object
	     * descriptor and invoking a method on the
	     * stub returned from the register call.
	     */
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(p, null);
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);

	    System.err.println("Creating descriptor");
	    ActivationDesc desc =
		new ActivationDesc(groupID,
		    "CreatePrivateActivatable$PrivateActivatable", null,
		    new MarshalledObject(exportType));
	    
	    System.err.println("Registering descriptor");
	    obj = (ActivateMe) ActivationLibrary.register(exportType, desc);
	    
	    /*
	     * Loop a bunch of times to force activator to
	     * spawn VMs (groups)
	     */
	    System.err.println("Activate object via method call");
	    obj.ping();
		
	    /*
	     * Clean up object too.
	     */
	    System.err.println("Deactivate object via method call");
	    obj.shutdown();
	    
	    System.err.println("\nsuccess: CreatePrivateActivatable test passed ");
	
	} catch (Exception e) {
	    if (e instanceof java.security.PrivilegedActionException) {
		e = ((java.security.PrivilegedActionException)e).getException();
	    }
	    TestLibrary.bomb("\nfailure: unexpected exception " +
			     e.getClass().getName(), e);
	    
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	    obj = null;
	}
    }
}
