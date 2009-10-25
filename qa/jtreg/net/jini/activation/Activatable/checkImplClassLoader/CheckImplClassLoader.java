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
 * @bug 4289544
 * @summary ActivationGroupImpl.newInstance does not set context classloader for impl
 *
 * @author Laird Dornin; code borrowed from Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID JavaVM StreamPipe
 * @build MyRMI
 * @build CheckImplClassLoader ActivatableImpl
 * @build ActivatableImpl ActivatableImpl_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=150 -Dtest.rmi.exportType=default CheckImplClassLoader
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=150 -Dtest.rmi.exportType=basic CheckImplClassLoader
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=150 -Dtest.rmi.exportType=jrmp CheckImplClassLoader
 */

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.activation.ActivationGroup;
import net.jini.jeri.*;

/**
 * ActivationInstantiator.newInstance() needs to set the
 * context class loader when it constructs the implementation class of
 * an Activatable object.  It needs to set the ccl to be the class
 * loader of the implementation class.
 *
 * Test creates an Activatable object whose impl is loaded outside of
 * CLASSPATH.  The impls constructor checks to make sure that the
 * correct context class loader has been set when the constructor is
 * invoked.
 */
public class CheckImplClassLoader {

    private static Object dummy = new Object();
    private static MyRMI myRMI = null;
    
    public static void main(String args[]) {  
	/*
	 * The following line is required with the JDK 1.2 VM because
	 * of gc hocus pocus that may no longer be needed with an
	 * exact vm (hotspot).
	 */
	Object dummy1 = new Object();
	RMID rmid = null;
	
	System.err.println("\nRegression test for bug/rfe 4289544\n");
	    
	try {

	    URL implcb = TestLibrary.installClassInCodebase("ActivatableImpl",
							    "implcb");
	    TestLibrary.installClassInCodebase("ActivatableImpl_Stub",
					       "implcb");
	    TestLibrary.suggestSecurityManager(
	        TestParams.defaultSecurityManager);

	    String exportType =
		TestLibrary.getProperty("test.rmi.exportType","default");
	    System.err.println("exportType: " + exportType);
	    
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.start();

	    System.err.println("Create activation group in this VM");
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(null, null);
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);
	    String cb =
		new File(RMID.getDefaultGroupLocation()).toURI().toString();
	    Class cl = RMIClassLoader.loadClass(
				cb,
				"com.sun.jini.phoenix.ActivationGroupData");
	    Constructor cons = cl.getConstructor(new Class[]{String[].class});
	    groupDesc = new ActivationGroupDesc(
		 "com.sun.jini.phoenix.ActivationGroupImpl",
		 cb,
		 new MarshalledObject(cons.newInstance(
			new Object[]{new String[]{TestParams.testSrc +
				     File.separator + "rmid.config"}})),
		 null, null);
	    cl = RMIClassLoader.loadClass(
				 cb,
				 "com.sun.jini.phoenix.ActivationGroupImpl");
	    Method m = cl.getMethod("createGroup",
				    new Class[]{ActivationGroupID.class,
						ActivationGroupDesc.class,
						long.class});
	    m.invoke(null, new Object[]{groupID, groupDesc, new Long(0)});
	    
	    ActivationDesc desc =
		new ActivationDesc(groupID,
				   "ActivatableImpl",
				   implcb.toString(),
				   new MarshalledObject(exportType));

	    myRMI = (MyRMI) ActivationLibrary.register(exportType, desc);

	    System.err.println("Checking that impl has correct " +
			       "context class loader");
	    if (!myRMI.classLoaderOk()) {
		TestLibrary.bomb("incorrect context class loader for " +
				 "activation constructor");
	    } 
	    
	    System.err.println("Deactivate object via method call");
	    myRMI.shutdown();

	    System.err.println("\nsuccess: CheckImplClassLoader test passed ");
		
	} catch (Exception e) {
	    TestLibrary.bomb("\nfailure: unexpected exception ", e);
	} finally {
	    try {
		Thread.sleep(4000);
	    } catch (InterruptedException e) {
	    }

	    myRMI = null;
	    System.err.println("rmid shut down");
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
