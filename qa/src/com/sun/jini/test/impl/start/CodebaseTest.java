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
package com.sun.jini.test.impl.start;

import java.util.logging.Level;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.ActivatableServiceStarterAdmin;
import com.sun.jini.qa.harness.QAConfig;

// java.rmi
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import net.jini.loader.ClassLoading;

/**
 * This test ensures that different service instances in the same VM properly
 * annotate their objects with their respective codebases.
 * <p>
 * Given two activated service instances, each with different multi-element
 * codebases, the test verifies that the stub returned for each instance has
 * the correct codebase annotation.  The test also verifies that an object
 * returned by a method call on each instance has the correct codebase
 * annotation.
 * <p>
 * This test expects com.sun.jini.test.impl.start.TestServiceDummyClass1
 * to be in the classpath of of service instances.
 */
public class CodebaseTest extends AbstractStartBaseTest {

    // javadoc inherited from super class
    public void run() throws Exception {
        // start test services
        String propertyKey = "com.sun.jini.test.impl.start.CodebaseTest";
        TestService service1 = null;
        TestService service2 = null;
        logger.log(Level.FINE, "activating test service 1");
	service1 = 
		(TestService) getManager().startService(propertyKey + "1");
        logger.log(Level.FINE, "activating test service 2");
	service2 = 
		(TestService) getManager().startService(propertyKey + "2");

	ActivatableServiceStarterAdmin admin1 = 
	    (ActivatableServiceStarterAdmin) getManager().getAdmin(service1);
	ActivatableServiceStarterAdmin admin2 = 
	    (ActivatableServiceStarterAdmin) getManager().getAdmin(service2);

        if (!admin1.getGroupID().equals(admin2.getGroupID())) {
            throw new TestException("Test services have different "
                + "ActivationGroupIDs which means that services are not "
                + "being run in a shared VM");
        }

        // check proxy codebases are as expected
        String expected_codebase1 = admin1.getCodebase();
        String expected_codebase2 = admin2.getCodebase();

        String proxy_codebase1 =
            ClassLoading.getClassAnnotation(service1.getClass());
        String proxy_codebase2 =
            ClassLoading.getClassAnnotation(service2.getClass());

        logger.log(Level.FINE, "expected codebase for test "
            + "service 1 proxy: " + expected_codebase1);
        logger.log(Level.FINE, "actual codebase for test "
            + "service 1 proxy: " + proxy_codebase1);

        logger.log(Level.FINE, "expected codebase for test "
            + "service 2 proxy: " + expected_codebase2);
        logger.log(Level.FINE, "actual codebase for test "
            + "service 2 proxy: " + proxy_codebase2);

        if (proxy_codebase1 == null ||
            !proxy_codebase1.equals(expected_codebase1))
        {
            throw new TestException("Actual codebase for test "
                + "service 1 proxy does not match expected codebase");
        }

        if (proxy_codebase2 == null ||
            !proxy_codebase2.equals(expected_codebase2))
        {
            throw new TestException("Actual codebase for test "
                + "service 2 proxy does not match expected codebase");
        }


        // load a class from each test service
        String cName =
                "com.sun.jini.test.impl.start.TestServiceDummyClass1";

        logger.log(Level.FINE, "attempting to load " + cName
                + " from test service 1");
        Object object1 = service1.loadClass(cName);

        logger.log(Level.FINE, "attempting to load " + cName
                + " from test service 2");
        Object object2 = service2.loadClass(cName);

        // check codebases of loaded classes
        String loadClass_codebase1 =
                ClassLoading.getClassAnnotation(object1.getClass());
        String loadClass_codebase2 =
                ClassLoading.getClassAnnotation(object2.getClass());

        logger.log(Level.FINE, "expected codebase for test "
                + "service 1 loaded class: " + expected_codebase1);
        logger.log(Level.FINE, "actual codebase for test "
                + "service 1 loaded class: " + loadClass_codebase1);

        logger.log(Level.FINE, "expected codebase for test "
                + "service 2 loaded class: " + expected_codebase2);
        logger.log(Level.FINE, "actual codebase for test "
                + "service 2 loaded class: " + loadClass_codebase2);

        if (loadClass_codebase1 == null ||
                !loadClass_codebase1.equals(expected_codebase1))
        {
            throw new TestException("Actual codebase for test "
                    + "service 1 loaded class does not match expected codebase");
        }

        if (loadClass_codebase2 == null ||
                !loadClass_codebase2.equals(expected_codebase2))
        {
            throw new TestException("Actual codebase for test "
                    + "service 2 loaded class does not match expected codebase");
        }
    }

}
