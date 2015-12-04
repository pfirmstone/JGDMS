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
/**
 * @test 
 * @bug 4143175
 * @summary Activation groups should be able to download and 
 * install a security manager.
 *
 * @author Laird Dornin; code borrowed from Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateMe
 * @build AlternateGroup
 * @build CustomRMISecurityManager
 * @build DownloadSecurityManager DownloadSecurityManager_Stub
 *
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default DownloadSecurityManager
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic DownloadSecurityManager
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp DownloadSecurityManager
 */

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.jeri.*;

/**
 * create an activatable object that creates a custom activation
 * group descriptor.  In the custom group descriptor, specify a 
 * custom ActivationGroupImpl for download.  The custom group
 * implementation will download and set a custom security manager.
 */

public class DownloadSecurityManager
	implements ActivateMe, Serializable
{

    private ActivationLibrary.ExportHelper helper;

    /**
     * Activates/exports the object using 1.4 APIs if the
     * value in obj is "basic"; otherwise it uses pre-1.4 APIs
     * to export the object.
     */
    public DownloadSecurityManager(ActivationID id, MarshalledObject mobj)
    	throws ActivationException, RemoteException
    {
	helper = new ActivationLibrary.ExportHelper(mobj, this, id);
	helper.export();
    }

    private Object writeReplace() {
	return helper.getStub();
    }

    public void ping(String responder) {
	System.err.println("DownloadSecurityManager: received ping " +
			   responder);
    }
    
    public static URL securityManagerCodebaseURL = null;

    public static void main(String[] args) {

	RMID rmid = null;

	System.out.println("\nRegression test for bug 4143175\n");
	
	try {
	    TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

	    String exportType =
		TestLibrary.getProperty("test.rmi.exportType","default");
	    System.err.println("exportType: " + exportType);
	    
	    // install relevant classes in appropriate codebases
	    System.err.println("Installing class files in codebases...");
	    securityManagerCodebaseURL = TestLibrary.installClassInCodebase(
                                   "CustomRMISecurityManager", 
				   "customSecurityManager");
	    TestLibrary.installClassInCodebase(
				   "CustomRMISecurityManager$CheckPackage", 
				   "customSecurityManager");
	    TestLibrary.installClassInCodebase("AlternateGroup", "group");
	    System.err.println("Class files installed.");

	    // start rmid
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
	    p.put("securityManagerCodebaseURL", 
		  securityManagerCodebaseURL.toString());

	    /* Create and register descriptors for a group and
	     * activatable object.  
	     */
	    System.err.println("Creating descriptors");
	    
	    String cb =
		new File(RMID.getDefaultGroupLocation()).toURI().toString();
	    Class cl = RMIClassLoader.loadClass(
				cb,
				"org.apache.river.phoenix.ActivationGroupData");
	    Constructor cons = cl.getConstructor(new Class[]{String[].class});
	    ActivationGroupDesc groupDesc1 = new ActivationGroupDesc(
		"org.apache.river.phoenix.ActivationGroupImpl",
		cb,
		new MarshalledObject(cons.newInstance(
			new Object[]{new String[]{TestParams.testSrc +
				     File.separator + "rmid.config"}})),
		p, null);

	    // download group impl, from the following location:
	    String location = "file:group/";

	    // need to privide own activation group descriptor so that
	    // we can test relevant functionality.
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc("AlternateGroup", location,
					new MarshalledObject(groupDesc1),
					p, null);
	    java.rmi.activation.ActivationGroupID groupID =
		ActivationGroup.getSystem().registerGroup(groupDesc);
	    ActivationDesc desc =
		new ActivationDesc(groupID, "DownloadSecurityManager", null,
				   new MarshalledObject(exportType), true);
	    
	    System.err.println("Registering descriptor");
	    
	    ActivateMe obj = (ActivateMe)
		ActivationLibrary.register(exportType, desc);

	    // wake the object up by calling a remote method.
	    System.err.println("Ping object");
	    obj.ping("hello");

	    Thread.sleep(7000);
	    System.err.println("test passed...");

	} catch (Exception e) {
	    TestLibrary.bomb("test failed", e);
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }

    public void shutdown() throws Exception {
	helper.deactivate();
    }

}
