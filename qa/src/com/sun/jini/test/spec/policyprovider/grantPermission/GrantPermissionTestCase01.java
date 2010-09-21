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
package com.sun.jini.test.spec.policyprovider.grantPermission;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.security
import java.security.Permission;
import java.security.PermissionCollection;

// java.io
import java.io.FilePermission;

// davis packages
import net.jini.security.GrantPermission;

// test base class
import com.sun.jini.test.spec.policyprovider.AbstractTestBase;

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Util;

/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>GrantPermission</code> class
 * properly implements aggregate permission implication.
 * <br>
 * For example, suppose we have 3 permissions A, B, and C (none
 * of which is a GrantPermission), such that A does not imply C by itself, and B
 * does not imply C by itself, but a PermissionCollection containing both A and
 * B implies C.  Then, we need to verify that a GrantPermission containing both
 * A and B implies a GrantPermission containing C.
 * <br>
 * Also this test verifies that implies() works properly for
 * PermissionCollections returned from GrantPermission.newPermissionCollection
 * and these PermissionCollections properly implements aggregate permission
 * implication.
 *
 * <b>Test Description</b><br><br>
 *
 *  This test emulates aggregate permission using FilePermission with
 * "read", "write" and "read, write" actions.
 *
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ul><lh>This test requires the following infrastructure:</lh>
 *  <li> infrastructure is not required</li>
 * </ul>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li>
 *        Create three FilePermissions with "read", "write" and 
 *        "read,write" actions for the same file.
 *    </li>
 *    <li>
 *        Create three GrantPermissions passing created FilePermissions
 *        with "read", "write" and "read,write" actions.
 *    </li>
 *    <li>
 *        Create GrantPermission passing array of two created
 *        GrantPermissions for "read" and "write" actions.
 *    </li>
 *    <li>
 *        Verify that last created GrantPermission implies
 *        GrantPermission that was created
 *        for "read,write" action.
 *    </li>
 *    <li>
 *        Get new PermissionCollection from GrantPermission for 
 *        "read" action and add GrantPermission for "read" action and
 *        GrantPermission for "write" action to this PermissionCollection.
 *    </li>
 *    <li>
 *        Verify that PermissionCollection does not imply
 *        FilePermissions with "read,write" action.
 *    </li>
 *    <li>
 *        Verify that PermissionCollection implies
 *        GrantPermission for "read,write" action.
 *    </li>
 * </ol>
 *
 */
public class GrantPermissionTestCase01 extends AbstractTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        PermissionCollection pc = null;

        /*
         * Create three FilePermissions with "read", "write" and 
         * "read,write" actions for the same file.
         */
        FilePermission fp01 = new FilePermission("foo", "read");
        FilePermission fp02 = new FilePermission("foo", "write");
        FilePermission fp03 = new FilePermission("foo", "read,write");

        /*
         * Create three GrantPermissions passing created FilePermissions
         * with "read", "write" and "read,write" actions.
         */
        GrantPermission gp01 = new GrantPermission(fp01);
        GrantPermission gp02 = new GrantPermission(fp02);
        GrantPermission gp03 = new GrantPermission(fp03);

        /*
         * Create GrantPermission passing array of two created
         * GrantPermissions for "read" and "write" actions.
         */
        Permission[] pa = { fp01, fp02 };
        GrantPermission gppa = new GrantPermission(pa);

        /*
         * Verify that last created GrantPermission implies
         * GrantPermission that was created
         * for "read,write" action.
         */
        checkImplies(gppa, gp03, true);

        /*
         * Get new PermissionCollection from GrantPermission for 
         * "read" action and add GrantPermission for "read" action and
         * GrantPermission for "write" action to this PermissionCollection.
         */
        pc = gp01.newPermissionCollection();
        pc.add(gp01);
        pc.add(gp02);

        /*
         * Verify that PermissionCollection does not imply
         * FilePermissions with "read,write" action.
         */
        checkImplies(pc, fp03, false, "(Grant)PermissionCollection");

        /*
         * Verify that PermissionCollection implies
         * GrantPermission for "read,write" action.
         */
        checkImplies(pc, gp03, true, "(Grant)PermissionCollection");
    }

    /**
     * Verify that passing PermissionCollection are implied/not implied
     * passing Permission.
     *
     * @param pc PermissionCollection to be verified.
     * @param p  Permission to be verified.
     * @param exp if true then PermissionCollection should imply
     *        passing Permission, otherwise should not imply.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void checkImplies(PermissionCollection pc, Permission p,
            boolean exp, String msg) throws TestException {
        boolean ret = pc.implies(p);
        msg += ".implies(" + str(p) + ")";

        if (ret != exp) {
            throw new TestException(Util.fail(msg, "" + ret, "" + exp));
        } else {
            logger.log(Level.FINE, Util.pass(msg, "" + ret));
        }
    }

    /**
     * Verify that passing Permission are implied/not implied
     * another passing Permission.
     *
     * @param p Permission to be verified.
     * @param pAnother another Permission to be verified.
     * @param exp if true then Permission should imply
     *        another passing Permission, otherwise should not imply.
     *
     * @throws TestException if failed
     *
     */
    protected void checkImplies(Permission p, Permission pAnother,
            boolean exp) throws TestException {
        boolean ret = p.implies(pAnother);
        msg = str(p) + ".implies(" + str(pAnother) + ")";

        if (ret != exp) {
            throw new TestException(Util.fail(msg, "" + ret, "" + exp));
        } else {
            logger.log(Level.FINE, Util.pass(msg, "" + ret));
        }
    }

    private String str(Permission p) {
        String className = p.getClass().getName();
        int lastIndex = className.lastIndexOf(".");

        if (lastIndex > 0) {
            className = className.substring(lastIndex + 1);
        }
        return className + "(" + p.getName() + ")";
    }
}
