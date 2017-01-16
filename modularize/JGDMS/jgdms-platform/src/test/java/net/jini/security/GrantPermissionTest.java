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

package net.jini.security;

import java.io.FilePermission;
import java.security.Permission;
import java.security.PermissionCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class GrantPermissionTest {

    public GrantPermissionTest() {
    }

    @org.junit.BeforeClass
    public static void setUpClass() throws Exception {
    }

    @org.junit.AfterClass
    public static void tearDownClass() throws Exception {
    }

    @org.junit.Before
    public void setUp() throws Exception {       
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

   /**
     * Test of string construction, of class GrantPermission.
     */
    @org.junit.Test
    public void construct() {
        System.out.println("String constructor");
        RuntimePermission rpD = new RuntimePermission("D", "");
//        RuntimePermission rpD1 = new RuntimePermission("D1");
//        RuntimePermission rpC = new RuntimePermission("C");
//        RuntimePermission rpC1 = new RuntimePermission("C1");
        
        String rpDS = "delim=' java.lang.RuntimePermission 'D'";
        
        GrantPermission gpS = new GrantPermission(rpDS);
        GrantPermission gpP = new GrantPermission(rpD);
        System.out.print(gpS.toString());
        System.out.print(gpP.toString());
        boolean result = gpS.implies(gpP);
        boolean expResult = true;
        assertEquals(expResult, result);
        result = gpP.implies(gpS);
        assertEquals(expResult, result);
    }

    /**
     * Test of implies method, of class GrantPermission.
     */
    @org.junit.Test
    public void implies() {
        System.out.println("implies");
        /*
         * Create three FilePermissions with "read", "write" and 
         * "read,write" actions for the same file.
         */
        FilePermission fp01 = new FilePermission("foo", "read");
        FilePermission fp02 = new FilePermission("foo", "write");
        FilePermission fp03 = new FilePermission("foo", "read,write");

        /*
         * Create GrantPermission passing created FilePermissions
         * with "read,write" actions.
         */

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
        boolean result = gppa.implies(gp03);
        String msg = str(gppa) + ".implies(" + str(gp03) + ")";       
        System.out.println(msg);
        boolean expResult = true;
        assertEquals(expResult, result);

    }
    
    private String str(Permission p) {
        String className = p.getClass().getName();
        int lastIndex = className.lastIndexOf(".");

        if (lastIndex > 0) {
            className = className.substring(lastIndex + 1);
        }
        return className + "(" + p.getName() + ")";
    }    

    @org.junit.Test
    public void doesNotImplyFilePermissionsRWTest(){
        System.out.println("Verify that PermissionCollection does not imply FilePermissions with \"read,write\" action.");
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
        boolean result = checkImplies(pc, fp03, false, "(Grant)PermissionCollection");
        assertEquals( result , true );
    }    
    
    @org.junit.Test
    public void verifyDoesImplyGrantPermissionTest(){
        System.out.println("Verify that PermissionCollection implies GrantPermission for \"read,write\" action.");
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
         * Get new PermissionCollection from GrantPermission for 
         * "read" action and add GrantPermission for "read" action and
         * GrantPermission for "write" action to this PermissionCollection.
         */
        pc = gp01.newPermissionCollection();
        pc.add(gp01);
        pc.add(gp02);

        /*
         * Verify that PermissionCollection implies
         * GrantPermission for "read,write" action.
         */
        boolean result = checkImplies(pc, gp03, true, "(Grant)PermissionCollection");
        assertEquals( result , true );
    }
    
    protected boolean checkImplies(PermissionCollection pc, Permission p,
            boolean exp, String msg){
        boolean ret = pc.implies(p);
        msg += ".implies(" + str(p) + ")";
        System.out.println(msg);
        return ret == exp;
    }

    /**
     * Test of equals method, of class GrantPermission.
     */
//    @org.junit.Test
//    public void testequals() {
//        System.out.println("equals");
//        Object obj = null;
//        GrantPermission instance = null;
//        boolean expResult = false;
//        boolean result = instance.equals(obj);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of hashCode method, of class GrantPermission.
     */
//    @org.junit.Test
//    public void testhashCode() {
//        System.out.println("hashCode");
//        GrantPermission instance = null;
//        int expResult = 0;
//        int result = instance.hashCode();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

}