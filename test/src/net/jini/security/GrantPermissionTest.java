/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
     * Test of getActions method, of class GrantPermission.
     */
//    @org.junit.Test
//    public void getActions() {
//        System.out.println("getActions");
//        GrantPermission instance = null;
//        String expResult = "";
//        String result = instance.getActions();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of newPermissionCollection method, of class GrantPermission.
     */
//    @org.junit.Test
//    public void newPermissionCollection() {
//        System.out.println("newPermissionCollection");
//        GrantPermission instance = null;
//        PermissionCollection expResult = null;
//        PermissionCollection result = instance.newPermissionCollection();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

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