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
/* @test %W% %E%
 * @bug 4917993
 * @summary ActivatableInvocationHandler wrapping of ConnectException;
 *	    this test checks possible exceptional conditions occurring when
 *	    ActivatableInvocationHandler attempts to activate an object
 * @author Ann Wollrath
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary ActivationLibrary RMID
 * @build ActivateMe
 * @build ActivateExceptionTest
 * @run main/othervm/policy=security.policy/timeout=240 ActivateExceptionTest
 */

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import java.io.*;
import java.util.Properties;

public class ActivateExceptionTest
	implements ActivateMe, Serializable
{
    
    private ActivationLibrary.ExportHelper helper;
    
    public ActivateExceptionTest(ActivationID id, MarshalledObject mobj)
	throws ActivationException, RemoteException
    {
	boolean refuseToActivate = false;
	try {
	    refuseToActivate = ((Boolean) mobj.get()).booleanValue();
	} catch (Exception impossible) {
	}
	
	if (refuseToActivate) {
	    throw new RemoteException("object refuses to activate");
	} else {
	    helper = new ActivationLibrary.ExportHelper("basic", this, id);
	    helper.export();
	}
    }

    private Object writeReplace() {
	return helper.getStub();
    }

    public void ping() {}

    public void shutdown() throws Exception {
	helper.deactivate();
    }

    public static void main(String[] args) 
    {
	RMID rmid = null;
	ActivateMe obj1 = null;
	ActivateMe obj2 = null;
	boolean rmidShutdown = false;
	
	System.err.println("\nRegression test for bug 4917993\n");
	try {
	    TestLibrary.suggestSecurityManager("java.lang.SecurityManager"); 

	    /*
	     * First run "rmid" and wait for it to start up.
	     */
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.start();

	    /*
	     * Set up activation group props.
	     */
	    Properties p = new Properties();
	    p.put("java.security.policy", 
		  TestParams.defaultGroupPolicy);
	    p.put("java.security.manager", 
		  TestParams.defaultSecurityManager);

	    /*
	     * Create and register descriptors.
	     */
	    System.err.println("Register group descriptor");
	    ActivationGroupDesc groupDesc =
		new ActivationGroupDesc(p, null);
	    ActivationSystem system = ActivationGroup.getSystem();
	    ActivationGroupID groupID = system.registerGroup(groupDesc);
	    
	    ActivationDesc desc1 =
		new ActivationDesc(groupID, "ActivateExceptionTest",
				   null,
				   new MarshalledObject(new Boolean(true)));
	    
	    ActivationDesc desc2 = 
		new ActivationDesc(groupID, "ActivateExceptionTest",
				   null,
				   new MarshalledObject(new Boolean(false)));
	    
	    System.err.println("Register activation descriptors");
	    obj1 = (ActivateMe) ActivationLibrary.register("basic", desc1);
	    ActivationID id = system.registerObject(desc2);
	    obj2 = (ActivateMe) ActivationLibrary.createSecureProxy(id, desc2);

	    /*
	     * Test for receipt of ActivateFailedException.
	     */
	    System.err.println("\nTest for ActivateFailedException");
	    System.err.println("Ping obj1");
	    try {
		obj1.ping();
		System.err.println("Test1a FAILED: no ActivateFailedExeption");
		throw new RuntimeException(
		    "Test1a FAILED: no ActivateFailedExeption");
	    
	    } catch (ActivateFailedException e) {

		System.err.println("Test1a PASSED: ActivateFailedException " +
				   "Generated");
	    }

	    System.err.println("Ping obj2");
	    try {
		obj2.ping();
		System.err.println("Test1b PASSED: no ActivateFailedException");
	    } catch (Exception e) {
		System.err.println("Test1b FAILED: unexpected exception");
		throw e;
	    }

	    /*
	     * Test for receipt of NoSuchObjectException.
	     */
	    System.err.println("Shutdown obj2");
	    obj2.shutdown();

	    // wait for shutdown to work 
	    Thread.sleep(2000);
	    
	    System.err.println("\nTest for NoSuchObjectException");
	    System.err.println("Unregister obj2");
	    system.unregisterObject(id);

	    try {
		System.err.println("Ping obj2");
		obj2.ping();
		System.err.println("Test2 FAILED: no NoSuchObjectException");
		throw new RuntimeException("Test2 FAILED: no NoSuchObjectException");
	    } catch (NoSuchObjectException e) {
		System.err.println("Test2 PASSED: NoSuchObjectException generated");
	    }
	    
	    /*
	     * Test for receipt of ConnectException.
	     */
	    ActivationLibrary.rmidCleanup(rmid);
	    System.err.println("\nTest for ConnectException");
	    rmidShutdown = true;
	    try {
		System.err.println("Ping obj2");
		obj2.ping();
	    } catch (ConnectException e) {
		System.err.println("Test3 PASSED: ConnectException generated");
	    } catch (Exception e) {
		System.err.println("Test3 FAILED: no ConnectException");
		throw new RuntimeException(e);
	    }
	    
	} catch (Exception e) {
	    /*
	     * Test failed; unexpected exception generated.
	     */
	    TestLibrary.bomb("\nfailure: unexpected exception " +
			       e.getClass().getName() + ": " + e.getMessage(), e);
	    
	} finally {
	    if (!rmidShutdown) {
		ActivationLibrary.rmidCleanup(rmid);
	    }
	    obj1 = obj2 = null;
	}
    }
}


