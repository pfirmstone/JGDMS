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
 * @summary check that exception thrown by activatable object constructor
 *          gets back to phoenix without connection getting slammed shut by
 *          unexport of a JERI-exported activation group
 *
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build ActivateFailure
 * @run main/othervm/policy=security.policy/timeout=120 ActivateFailure
 */
import java.io.File;
import java.rmi.*;
import java.rmi.activation.*;
import java.util.Properties;

public class ActivateFailure implements Remote {

    public static class TestException extends Exception {
    }

    public ActivateFailure(ActivationID id, MarshalledObject obj)
	throws TestException
    {
	throw new TestException();
    }

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	RMID.removeLog();
	RMID rmid = RMID.createRMID();
	rmid.start();
	try {
	    ActivationSystem sys = ActivationGroup.getSystem();
	    Properties props = new Properties();
	    props.put("java.security.policy",
		      TestParams.testSrc + File.separator +
		      "group.security.policy");
	    props.put("java.rmi.server.codebase",
		      "file:" + TestParams.testClasses + File.separator);
	    ActivationGroupID gid =
		sys.registerGroup(new ActivationGroupDesc(props, null));
	    ActivationID aid = sys.registerObject(
		      new ActivationDesc(gid, "ActivateFailure", null, null));
	    try {
		aid.activate(false);
		throw new RuntimeException("activation succeeded");
	    } catch (ActivationException e) {
		if (!(e.detail instanceof TestException)) {
		    throw e;
		}
	    }
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
