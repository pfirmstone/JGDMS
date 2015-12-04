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
import java.util.Enumeration;

// davis packages
import net.jini.export.ExportPermission;

// java.security
import java.security.PermissionCollection;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link java.security.BasicPermission#newPermissionCollection()} method on
 *   {@link net.jini.export.ExportPermission} objects.
 *
 * Infrastructure:
 *     - {@link BasicPermission_Collection}
 *         performs actions
 *     - {@link org.apache.river.test.spec.export.exportpermission.ExportPermission_AbstractTest}
 *         abstract class for all tests for {@link net.jini.export.ExportPermission}
 *
 * Actions:
 *   Test performs the following steps:
 *     - create array of ExportPermission objects with various target names;
 *     - invoke {@link java.security.BasicPermission#newPermissionCollection()}
 *       method on {@link net.jini.export.ExportPermission} object;
 *     - verify that valid {@link java.security.PermissionCollection} object
 *       is returned.
 *
 * </pre>
 */
public class BasicPermission_Collection extends ExportPermission_AbstractTest {

    /**
     * Array of {@link net.jini.export.ExportPermission} objects.
     */
    private ExportPermission[] perms = new ExportPermission[targetNames.length];

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < targetNames.length; i++) {
            perms[i] = new ExportPermission(targetNames[i]);
        }
        
        if (!checker(perms)) {
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }

    /**
     * This method checks that
     * {@link java.security.BasicPermission#newPermissionCollection()} method
     * run successfully on {@link net.jini.export.ExportPermission} objects.
     *
     * @param obj array of {@link net.jini.export.ExportPermission} objects
     * @return true if
     *         {@link java.security.BasicPermission#newPermissionCollection()}
     *         was successfully or false otherwise
     */
    public boolean checker(ExportPermission[] obj) {
        /*
         * Run ExportPermission.newPermissionCollection() method.
         */
        logger.log(Level.FINE,
                    "\n\t+++++ (" + obj[0] + ")).newPermissionCollection()");
        Object res = obj[0].newPermissionCollection();
        
        /*
         * Verify that an instance of PermissionCollection class has been
         * returned.
         */
        Class resClass = res.getClass();
        logger.log(Level.FINE, "newPermissionCollection() has returned an"
                + " instance of " + resClass);
        if (!(res instanceof PermissionCollection)) {
            logger.log(Level.FINE, "Expected that newPermissionCollection()"
                    + " returns an instance of PermissionCollection class,"
                    + " but really an instance of " + resClass
                    + " has been returned!");
            return false;
        }
        
        /*
         * Verify the returned PermissionCollection object.
         */
        PermissionCollection permColl = (PermissionCollection) res;
        
        /*
         * Verify PermissionCollection.add() method (that all ExportPermission
         * object has been added successfully).
         */
        for (int i = 0; i < obj.length; i++) {
            permColl.add(obj[i]);
        }
        logger.log(Level.FINE, "permColl:\n" + permColl.toString());
        Enumeration elements = permColl.elements();
        for (; elements.hasMoreElements() ;) {
            ExportPermission perm = (ExportPermission) elements.nextElement();
            for (int i = 0; i < obj.length; i++) {
                if (obj[i] == null) {
                    continue;
                }
                if (obj[i].equals(perm)) {
                    obj[i] = null;
                    continue;
                }
            }
        }
        for (int i = 0; i < obj.length; i++) {
            if (obj[i] != null) {
                logger.log(Level.FINE, obj[i] + " object hasn't been added"
                        + " to the PermissionCollection!");
                return false;
            }
        }
        return true;
    }
}
