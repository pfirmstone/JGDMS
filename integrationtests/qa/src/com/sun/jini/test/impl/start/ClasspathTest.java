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
 * This test ensures that different service instances in the same VM properly
 * load classes from their respective classpaths and the classpath of a
 * common class loader.
 * <p>
 * Given two activated service instances, each with different classpaths,
 * each instance is asked to:
 * <ul>
 *   <li>load a class that is only in it's classpath (should pass)
 *   <li>load a class that is only in the other instance's classpath (should
 *       fail)
 *   <li>load a class that is in the classpath of each instance as well 
 *       as in the classpath of a common class loader to both instances
 *       (should pass)
 *   <li>get and set a public static variable
 * </ul>
 * <p>
 * The results are returned to the test.
 * <p>
 * This test expects:
 * <ul>
 *   <li>com.sun.jini.test.impl.start.TestServiceDummyClass0
 *       to be defined in a common class loader of both service instances
 *   <li>com.sun.jini.test.impl.start.TestServiceDummyClass2
 *       to be defined only in the class loader of one service instance
 *   <li>com.sun.jini.test.impl.start.TestServiceDummyClass3
 *       to be defined only in the class loader of the other service instance
 * </ul>
 */
public class ClasspathTest extends AbstractStartBaseTest {

    // javadoc inherited from super class
    public void run() throws Exception {
        // start test services
        String propertyKey = "com.sun.jini.test.impl.start.ClasspathTest";
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

        // load a class exclusive to the test service; should pass
        loadClass("2",service1,"1"); //service 1: load dummy class 2
        loadClass("3",service2,"2"); //service 2: load dummy class 3

        // load a class exclusive to the *other* test service; should fail
        try {
            loadClass("3",service1,"1"); //service 1: load dummy class 3
            throw new TestException("test service 1 was able "
                    + "to load a class from the classpath of test service 2");
        } catch (ClassNotFoundException ignore) {
            // should occur
        }
        try {
            loadClass("2",service2,"2"); //service 2: load dummy class 2
            throw new TestException("test service 2 was able "
                    + "to load a class from the classpath of test service 1");
        } catch (ClassNotFoundException ignore) {
            // should occur
        }

        // load a class common to test services and a common class loader
        loadClass("0",service1,"1"); //service 1: load dummy class 0
        loadClass("0",service2,"2"); //service 2: load dummy class 0

        // set and get static variable of common class
        int setValue = -1;
        logger.log(Level.FINE, "setting static variable "
                + "common to both test services to: " + setValue);
        service1.setCommonStaticVariable(setValue);
        int getValue = service2.getCommonStaticVariable();
        if (getValue != setValue) {
            throw new TestException("test service 1 set the "
                    + "common static variable to " + setValue
                    + " but test service 2 got the common static variable "
                    + getValue + "; these values should match");
        }
        setValue = 3000;
        logger.log(Level.FINE, "setting static variable "
                + "common to both test services to: " + setValue);
        service2.setCommonStaticVariable(setValue);
        getValue = service1.getCommonStaticVariable();
        if (getValue != setValue) {
            throw new TestException("test service 2 set the "
                    + "common static variable to " + setValue
                    + " but test service 1 got the common static variable "
                    + getValue + "; these values should match");
        }

        // set and get static variable local to each test service
        setValue = 100;
        logger.log(Level.FINE, "setting static variable "
                + "local to test service 1: " + setValue);
        service1.setLocalStaticVariable(setValue);
        getValue = service2.getLocalStaticVariable();
        if (getValue == setValue) {
            throw new TestException("test service 1 set its "
                    + "local static variable to " + setValue
                    + " and test service 2 got its local static variable "
                    + getValue + "; these values should *not* match");
        }
        setValue = -40;
        logger.log(Level.FINE, "setting static variable "
                + "local to test service 2: " + setValue);
        service2.setLocalStaticVariable(setValue);
        getValue = service1.getLocalStaticVariable();
        if (getValue == setValue) {
            throw new TestException("test service 2 set its "
                    + "local static variable to " + setValue
                    + " and test service 1 got its local static variable "
                    + getValue + "; these values should *not* match");
        }
        return;
    }

    private Object loadClass(String dummyClass, // dummy class to load
                           TestService service, // service to load dummy class
                          String serviceNumber) // service name for doc purpose
        throws Exception
    {
        String classToLoad =
            "com.sun.jini.test.impl.start.TestServiceDummyClass"
            + dummyClass;
        logger.log(Level.FINE, "attempting to load "
            + classToLoad + " from test service " + serviceNumber);
        return service.loadClass(classToLoad);
    }
}
