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
package org.apache.river.test.spec.export.exportpermission;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;

// davis packages
import net.jini.export.ExportPermission;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link java.security.BasicPermission#equals(Object)} method on
 *   {@link net.jini.export.ExportPermission} objects.
 *
 * Test Cases:
 *   This test creates ExportPermission objects with various target names:
 *     exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface
 *     exportRemoteInterface.org.apache.river.test.spec.export.util.*
 *     exportRemoteInterface.*
 *     *
 *
 * Infrastructure:
 *     - {@link BasicPermission_equals}
 *         performs actions
 *     - {@link org.apache.river.test.spec.export.exportpermission.ExportPermission_AbstractTest}
 *         abstract class for all tests for {@link net.jini.export.ExportPermission}
 *
 * Actions:
 *   Test performs the following steps in each test case:
 *     - create 2 ExportPermission objects;
 *     - invoke equals() method on one of the created ExportPermission object;
 *     - verify that the result of equals() method is equal to the expected one.
 *
 * </pre>
 */
public class BasicPermission_equals extends ExportPermission_AbstractTest {
    
    /**
     * Test Cases.
     */
    public TestCase t_cases[] = {
            new TestCase(new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         true),
            new TestCase(new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.*"),
                         new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.*"),
                         true),
            new TestCase(new ExportPermission ("exportRemoteInterface.*"),
                         new ExportPermission ("exportRemoteInterface.*"),
                         true),
            new TestCase(new ExportPermission ("*"),
                         new ExportPermission ("*"),
                         true),
            new TestCase(new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface1"),
                         false),
            new TestCase(new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.*"),
                         false),
            new TestCase(new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         new ExportPermission (
               "exportRemoteInterface.*"),
                         false),
            new TestCase(new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         new ExportPermission ( "*"),
                         false)
    };

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < t_cases.length; i++) {
            logger.log(Level.FINE, "+++++ Test Case #" + (i + (int)1));
            if (!checker(t_cases[i])) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }

    /**
     * This method checks that
     * {@link java.security.BasicPermission#equals(Object)} method runs
     * successfully on {@link net.jini.export.ExportPermission} objects.
     *
     * @param tc {@link org.apache.river.test.spec.export.exportpermission.ExportPermission_AbstractTest.TestCase}
     *           object that contains 2 {@link net.jini.export.ExportPermission}
     *           objects to be compared and the expected result of
     *           {@link java.security.BasicPermission#equals(Object)} method
     * @return true if the result of
     *         {@link java.security.BasicPermission#equals(Object)} is equal to
     *         the expected result or false otherwise
     */
    public boolean checker(TestCase tc) {
        ExportPermission p1 = tc.getPermission1();
        ExportPermission p2 = tc.getPermission2();
        boolean expected = tc.getExpected();
   
        logger.log(Level.FINE, "1-st ExportPermission object: " + p1);
        logger.log(Level.FINE, "2-nd ExportPermission object: " + p2);
        logger.log(Level.FINE, "\t+++ " + p1 + ".equals(" + p2 + ")");
        boolean returned = p1.equals(p2);
        logger.log(Level.FINE,
                "Expected result of equals() method: " + expected);
        logger.log(Level.FINE,
                "Returned result of equals() method: " + returned);
        return (returned == expected);
    }
}
