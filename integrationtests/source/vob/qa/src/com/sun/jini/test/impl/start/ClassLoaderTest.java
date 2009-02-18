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

/**
 * This test ensures that different instances of the same service in the same
 * VM are loaded by the appropriate class loader in the appropriate class 
 * loader hierarchy.  A passing run of this test should be sufficient to infer
 * proper namespace separation of service instances.
 * <p>
 * Given two activated service instances, each instance creates a
 * representation of its class loader hierarchy and returns it to the
 * test for comparison.  To pass, the class hierarchies must be identical
 * except for their last (leaf) element which must be different.
 */
public class ClassLoaderTest extends AbstractStartBaseTest {

    // javadoc inherited from super class
    public void run() throws Exception {
        // start test services
        String propertyKey = "com.sun.jini.test.impl.start.ClassLoaderTest";
        TestService service1 = null;
        TestService service2 = null;

        logger.log(Level.FINE, "activating test service 1");
	service1 = 
		(TestService) manager.startService(propertyKey + "1");
        logger.log(Level.FINE, "activating test service 2");
        service2 = 
		(TestService) manager.startService(propertyKey + "2");

	ActivatableServiceStarterAdmin admin1 = 
	    (ActivatableServiceStarterAdmin) manager.getAdmin(service1);
	ActivatableServiceStarterAdmin admin2 = 
	    (ActivatableServiceStarterAdmin) manager.getAdmin(service2);
        if (!admin1.getGroupID().equals(admin2.getGroupID())) {
            throw new TestException("Test services have different "
                + "ActivationGroupIDs which means that services are not "
                + "being run in a shared VM");
        }

        // compare ClassLoader hierarchies for each test service
        logger.log(Level.FINE, "Comparing class loader hierarchies");
        if (!service1.compareSiblingClassLoaderHierarchy(service2.getUuid()) ||
            !service2.compareSiblingClassLoaderHierarchy(service1.getUuid())) {
            throw new TestException(
                "Test services have unexpected class loader hierarchies");
        }


	// Run a negative test just for insurance
        logger.log(Level.FINE, "Comparing same class loader hierarchies");
        if (service1.compareSiblingClassLoaderHierarchy(service1.getUuid()) ||
            service2.compareSiblingClassLoaderHierarchy(service2.getUuid())) {
            throw new TestException(
                "Same services have different class loader hierarchies");
        }


        return;
    }
}
