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
/* @test 1.3 98/07/14
 * @bug 4149366
 * @summary The class loader used to load classes for parameter types sent in
 * an RMI call to an activatable object should delegate to the class loader
 * that loaded the class of the activatable object itself, to maximize the
 * likelihood of type compatibility between downloaded parameter types and
 * supertypes shared with the activatable object.
 * @author Peter Jones (much code taken from Ann Wollrath's activation tests)
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build DownloadParameterClass
 * @build Foo
 * @build FooReceiverImpl
 * @build FooReceiverImpl_Stub
 * @build Bar
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default DownloadParameterClass
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic DownloadParameterClass
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp DownloadParameterClass
 */

import java.io.*;
import java.net.*;
import java.rmi.*;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.jeri.*;

public class DownloadParameterClass {

    public interface FooReceiver extends Remote {

	/*
	 * The interface can't actually declare that the method takes a
	 * Foo, because then Foo would have to be in the test's CLASSPATH,
	 * which might get propagated to the group VM's CLASSPATH, which
	 * would nullify the test (the Foo supertype must be loaded in the
	 * group VM only through the class loader that loaded the
	 * activatable object).
	 */
	public void receiveFoo(Object obj) throws RemoteException;
    }

    public static void main(String[] args) {

	System.err.println("\nRegression test for bug 4149366\n");

	/*
	 * Install classes to be seen by the activatable object's class
	 * loader in the "codebase1" subdirectory of working directory, and
	 * install the subtype to be downloaded into the activatable object
	 * into the "codebase2" subdirectory.
	 */
	URL codebase1 = null;	
	URL codebase2 = null;
	try {
	    codebase1 = TestLibrary.installClassInCodebase("FooReceiverImpl", "codebase1");
	    TestLibrary.installClassInCodebase("FooReceiverImpl_Stub", "codebase1");
	    TestLibrary.installClassInCodebase("Foo", "codebase1");
	    codebase2 = TestLibrary.installClassInCodebase("Bar", "codebase2");
	} catch (MalformedURLException e) {
	    TestLibrary.bomb("failed to install test classes", e);
	}
	
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
	     * Create and register descriptors for activatable object in a
	     * group other than this VM's group, so that another VM will be
	     * spawned with the object is activated.
	     */
	    System.err.println("Creating descriptors");
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(p, null);
	    ActivationGroupID groupID =
		ActivationGroup.getSystem().registerGroup(groupDesc);
	    ActivationDesc objDesc =
		new ActivationDesc(groupID, "FooReceiverImpl",
				   codebase1.toString(),
				   new MarshalledObject(exportType), false);

	    System.err.println("Registering descriptors");
	    FooReceiver obj = (FooReceiver)
		ActivationLibrary.register(exportType, objDesc);

	    /*
	     * Create an instance of the subtype to be downloaded by the
	     * activatable object.  The codebase must be a path including
	     * "codebase1" as well as "codebase2" because the supertype
	     * must be visible here as well; the supertype cannot be
	     * installed in both codebases (like it would be in a typical
	     * setup) because of the trivial installation mechanism used
	     * below, and see the comment above for why it can't be in
	     * the test's CLASSPATH.
	     */
	    Class subtype = RMIClassLoader.loadClass(
		codebase2 + " " + codebase1, "Bar");
	    Object subtypeInstance = subtype.newInstance();

	    obj.receiveFoo(subtypeInstance);

	    System.err.println("\nTEST PASSED\n");

	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
