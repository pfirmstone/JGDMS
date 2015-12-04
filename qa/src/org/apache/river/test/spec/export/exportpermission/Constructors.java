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
 *   This test verifies the behavior of the
 *   {@link net.jini.export.ExportPermission#ExportPermission(String)} and
 *   {@link net.jini.export.ExportPermission#ExportPermission(String,String)}
 *   constructors.
 *   Both constructors create an instance of
 *   {@link net.jini.export.ExportPermission} with the specified name.
 *   The actions parameter is ignored.
 *   Parameters:
 *     name - the target name
 *     actions - ignored
 *
 * Test Cases:
 *   This test invokes both constructors with different target names to create
 *   ExportPermission objects.
 *   TestCase 1:
 *     ExportPermission(name) is invoked with the following target name:
 *       exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface
 *   TestCase 2:
 *     ExportPermission(name,null) is invoked with the following target name:
 *       exportRemoteInterface.org.apache.river.test.spec.export.util.FakeInterface
 *   TestCase 3:
 *     ExportPermission(name) is invoked with the following target name:
 *       exportRemoteInterface.org.apache.river.test.spec.export.util.*
 *   TestCase 4:
 *     ExportPermission(name,null) is invoked with the following target name:
 *       exportRemoteInterface.org.apache.river.test.spec.export.util.*
 *   TestCase 5:
 *     ExportPermission(name) is invoked with the following target name:
 *       exportRemoteInterface.*
 *   TestCase 6:
 *     ExportPermission(name,null) is invoked with the following target name:
 *       exportRemoteInterface.*
 *   TestCase 7:
 *     ExportPermission(name) is invoked with the following target name:
 *       *
 *   TestCase 8:
 *     ExportPermission(name) is invoked with the following target name:
 *       *
 *
 * Infrastructure:
 *     - {@link Constructors}
 *         performs actions
 *
 * Actions:
 *   Test performs the following steps in each test case:
 *     - creating ExportPermission object with the
 *       specified target name using the specified constructor;
 *     - invoking {@link java.security.Permission#getName()} method;
 *     - comparing obtained target name with the specified one.
 *
 * </pre>
 */
public class Constructors extends ExportPermission_AbstractTest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < targetNames.length; i++) {

            /* Checking ExportPermission(String) constructor */
            logger.log(Level.FINE,
                    "\n\t+++++ invoking ExportPermission(" + targetNames[i]
                    + ")");
            if (!checker(new ExportPermission(targetNames[i]),
                    targetNames[i])) {
                throw new TestException(
                        "" + " test failed");
            }

            /* Checking ExportPermission(String,String) constructor */
            logger.log(Level.FINE,
                    "\n\t+++++ new ExportPermission(" + targetNames[i]
                    + ", null)");
            if (!checker(new ExportPermission(targetNames[i], null),
                    targetNames[i])) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }

    /**
     * This method checks that {@link net.jini.export.ExportPermission} object
     * has been created successfully. I.e. the target name of the specified
     * {@link net.jini.export.ExportPermission} object is equals to the expected
     * one.
     *
     * @param obj {@link net.jini.export.ExportPermission} object to check
     * @param target expected target name of
     *               {@link net.jini.export.ExportPermission} object
     * @return true ({@link net.jini.export.ExportPermission} object
     *         has been created successfully) or false otherwise
     */
    public boolean checker(ExportPermission obj, String target) {
        String exportPermName = obj.getName();
        logger.log(Level.FINE,
                "getName() on created ExportPermission object should return: "
                + target);
        logger.log(Level.FINE,
                "getName() on created ExportPermission object returns:       "
                + exportPermName);

        if (!exportPermName.equals(target)) {
            return false;
        }
        return true;
    }
}
