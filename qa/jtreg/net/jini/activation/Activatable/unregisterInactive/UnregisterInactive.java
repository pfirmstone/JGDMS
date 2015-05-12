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
 * @bug 4115331

 * @summary synopsis: activatable object fails to go inactive after
 * unregister/inactive sequence.
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build UnregisterInactive
 * @build UnregisterInactive_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default UnregisterInactive
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic UnregisterInactive
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp UnregisterInactive
 */

import java.io.*;
import java.lang.reflect.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import net.jini.activation.ActivationGroup;
import net.jini.jeri.*;

public class UnregisterInactive
	implements ActivateMe, Serializable
{
 
    private ActivationLibrary.ExportHelper helper;

    private UnregisterInactive(ActivationID id, MarshalledObject obj)
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
    
    public void shutdown() throws Exception {
	helper.deactivate();
    }

    public static void main(String[] args) {

	System.out.println("\nRegression test for bug 4115331\n");
	
        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");
	
	RMID rmid = null;
	
	try {	
	    String exportType =
		TestLibrary.getProperty("test.rmi.exportType","default");
	    System.err.println("exportType: " + exportType);
	    
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.start();
	    System.err.println("Creating descriptor");

	    ActivationGroupDesc groupDesc =
 		new ActivationGroupDesc(null, null);
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);
	    String cb =
		new File(RMID.getDefaultGroupLocation()).toURI().toString();
	    Class cl = RMIClassLoader.loadClass(
				cb,
				"org.apache.river.phoenix.ActivationGroupData");
	    Constructor cons = cl.getConstructor(new Class[]{String[].class});
	    groupDesc = new ActivationGroupDesc(
		"org.apache.river.phoenix.ActivationGroupImpl",
		 cb,
		 new MarshalledObject(cons.newInstance(
			new Object[]{new String[]{TestParams.testSrc +
				     File.separator + "rmid.config"}})),
		null, null);
	    cl = RMIClassLoader.loadClass(
				 cb,
				 "org.apache.river.phoenix.ActivationGroupImpl");
	    Method m = cl.getMethod("createGroup",
				    new Class[]{ActivationGroupID.class,
						ActivationGroupDesc.class,
						long.class});
	    m.invoke(null, new Object[]{groupID, groupDesc, new Long(0)});
	    
	    ActivationDesc desc =
		new ActivationDesc(groupID, "UnregisterInactive", null,
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
	    
	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
