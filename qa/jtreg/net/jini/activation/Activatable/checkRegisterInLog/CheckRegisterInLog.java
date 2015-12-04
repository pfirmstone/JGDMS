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
 * @bug 4110548
 * @summary activate fails if rmid is restarted
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build ActivationLibrary TestLibrary RMID
 * @build ActivateMe CheckRegisterInLog CheckRegisterInLog_Stub
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=basic CheckRegisterInLog
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=default CheckRegisterInLog
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 -Dtest.rmi.exportType=jrmp CheckRegisterInLog
 */

import java.io.*;
import java.lang.reflect.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.util.Properties;
import net.jini.jeri.*;

public class CheckRegisterInLog
	implements ActivateMe, Serializable
{
    
    private ActivationLibrary.ExportHelper helper;
    
    public CheckRegisterInLog(ActivationID id, MarshalledObject mobj)
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

    public static void main(String[] args)  {
	RMID rmid = null;
	ActivateMe obj;
	
	System.out.println("\nRegression test for bug 4110548\n");

	CheckRegisterInLog server;
	
	try {
            TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

	    String exportType =
		TestLibrary.getProperty("test.rmi.exportType","default");
	    System.err.println("exportType: " + exportType);
	    
	    /*
	     * Start up activation system daemon "rmid".
	     */
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
	     * Register an activation group and an object
	     * in that group.
	     */
	    System.err.println("Creating group descriptor");
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(p, null);
	    System.err.println("Registering group");
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);
	    
	    System.err.println("Creating descriptor");
	    ActivationDesc desc =
		new ActivationDesc(groupID, "CheckRegisterInLog",
				   null, new MarshalledObject(exportType));
	    System.err.println("Registering descriptor");

	    obj = (ActivateMe) ActivationLibrary.register(exportType, desc);

	    /*
	     * Restart rmid to force it to read the log file
	     */
	    rmid.restart();

	    
	    /* 
	     * 4212096: Give rmid time to go away - we want to make
	     * sure that an attempt to activate the test object is not made
	     * on the ActivationSystem that is about to be shutdown.
	     */
	    try {
		Thread.sleep(10000);
	    } catch (InterruptedException ie) {
	    }

	    /*
	     * Activate the object via a method call.
	     */
	    System.err.println("Activate the object via method call");
	    obj.ping();
	    
	    /*
	     * Clean up object too.
	     */
	    System.err.println("Deactivate object via method call");
	    obj.shutdown();

	    System.err.println("\nsuccess: CheckRegisterInLog test passed ");
	    
	} catch (Exception e) {
	    System.err.println("\nfailure: unexpected exception " +
			       e.getClass().getName() + ": " + e.getMessage());
	    e.printStackTrace(System.err);
	    throw new RuntimeException("CheckRegisterInLog got exception " +
				       e.getMessage());
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}


