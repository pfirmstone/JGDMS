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
package com.sun.jini.test.spec.export.exportpermission;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;

// davis packages
import net.jini.export.ExportPermission;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link java.security.Permission#getName()} method on
 *   {@link net.jini.export.ExportPermission} objects.
 *
 * Test Cases:
 *   This test creates ExportPermission objects with various target names:
 *     exportRemoteInterface.com.sun.jini.test.spec.export.util.FakeInterface
 *     exportRemoteInterface.com.sun.jini.test.spec.export.util.*
 *     exportRemoteInterface.*
 *     *
 *
 * Infrastructure:
 *     - {@link BasicPermission_getActions}
 *         performs actions
 *     - {@link com.sun.jini.test.spec.export.exportpermission.ExportPermission_AbstractTest}
 *         abstract class for all tests for {@link net.jini.export.ExportPermission}
 *
 * Actions:
 *   Test performs the following steps in each test case:
 *     - create ExportPermission object;
 *     - invoke getName() method on the created ExportPermission object;
 *     - verify that the value returned by getName() method is equal to the
 *       expected target name of the ExportPermission object.
 *
 * </pre>
 */
public class Permission_getName extends ExportPermission_AbstractTest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < targetNames.length; i++) {
            if (!checker(new ExportPermission(targetNames[i]), targetNames[i])) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }

    /**
     * This method checks that {@link java.security.Permission#getName()} method
     * run successfully on {@link net.jini.export.ExportPermission} objects
     * (returns the target name of this permission).
     *
     * @param obj     {@link net.jini.export.ExportPermission} object to check
     * @param expName the expected target name of this permission
     * @return true if the value returned by
     *         {@link java.security.Permission#getName()} method is
     *         equal to the expected one or false otherwise
     */
    public boolean checker(ExportPermission obj, String expName) {
        logger.log(Level.FINE,
                    "\n\t+++++ (" + obj + ").getName()");
        String exportPermName = obj.getName();
        logger.log(Level.FINE, "Expected target name: " + expName);
        logger.log(Level.FINE, "Returned target name: " + exportPermName);

        if (!exportPermName.equals(expName)) {
            return false;
        }
        return true;
    }
}
