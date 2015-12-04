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

// java.util
import java.util.logging.Level;

// davis packages
import net.jini.export.ExportPermission;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link java.security.BasicPermission#implies(Permission)} method on
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
 *     - {@link BasicPermission_implies}
 *         performs actions
 *
 * Actions:
 *   Test performs the following steps in each test case:
 *     - create 2 ExportPermission objects;
 *     - invoke implies() method on one of the created ExportPermission object;
 *     - verify that the result of implies() method is equal to the expected
 *       one.
 *
 * </pre>
 */
public class BasicPermission_implies extends BasicPermission_equals {
    
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
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         true),
            new TestCase(new ExportPermission ("exportRemoteInterface.*"),
                         new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
                         true),
            new TestCase(new ExportPermission ("*"),
                         new ExportPermission (
               "exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface"),
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
     * This method checks that
     * {@link java.security.BasicPermission#implies(Permission)} method runs
     * successfully on {@link net.jini.export.ExportPermission} objects.
     *
     * @param tc {@link org.apache.river.test.spec.export.exportpermission.ExportPermission_AbstractTest.TestCase}
     *           object that contains 2 {@link net.jini.export.ExportPermission}
     *           objects to be checked with
     *           {@link java.security.BasicPermission#implies(Permission)}
     *           method and the expected result of implies() method
     * @return true if the result of
     *         {@link java.security.BasicPermission#implies(Permission)} is
     *         equal to the expected result or false otherwise
     */
    public boolean checker(TestCase tc) {
        ExportPermission p1 = tc.getPermission1();
        ExportPermission p2 = tc.getPermission2();
        boolean expected = tc.getExpected();
   
        logger.log(Level.FINE, "1-st ExportPermission object: " + p1);
        logger.log(Level.FINE, "2-nd ExportPermission object: " + p2);
        logger.log(Level.FINE, "\t+++ " + p1 + ".implies(" + p2 + ")");
        boolean returned = p1.implies(p2);
        logger.log(Level.FINE,
                "Expected result of implies() method: " + expected);
        logger.log(Level.FINE,
                "Returned result of implies() method: " + returned);
        return (returned == expected);
    }
}
