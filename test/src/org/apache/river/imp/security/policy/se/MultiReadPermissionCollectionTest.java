/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.se;


import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Enumeration;
import net.jini.security.AccessPermission;
import net.jini.security.AuthenticationPermission;
import org.apache.river.imp.security.policy.se.RevokeablePermissionCollection;
import org.apache.river.imp.security.policy.se.MultiReadPermissionCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class MultiReadPermissionCollectionTest {

    public MultiReadPermissionCollectionTest() {
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
     * Test of isReadOnly method, of class MultiReadPermissionCollection.
     */
    @org.junit.Test
    public void isReadOnly() {
        System.out.println("isReadOnly");
        Permission permission0 = new RuntimePermission("getClassLoader");
        MultiReadPermissionCollection instance = new MultiReadPermissionCollection(permission0);
        instance.setReadOnly();
        SecurityException exp = new SecurityException("attempt to add a Permission to a readonly Permissions object");
        String result = null;
        Permission permission1 = new AuthenticationPermission("javax.security.auth.x500.X500Principal \"CN=serverRSA\"", "listen");
        try {
            instance.add(permission1);
        }catch (SecurityException e) {
            result = e.toString();
            System.out.println(e.toString());
        }
        String expResult = exp.toString();
        assertEquals(expResult, result);
    }

    /**
     * Test of implies method, of class MultiReadPermissionCollection.
     */
    @org.junit.Test
    public void implies() {
        System.out.println("add");
        Permission permission = new RuntimePermission("getClassLoader");
        PermissionCollection instance = new MultiReadPermissionCollection(permission);
        instance.add(permission);
        boolean result = instance.implies(permission);
        assertEquals(true, result);
    }

    /**
     * Test of elements method, of class MultiReadPermissionCollection.
     */
    @org.junit.Test
    public void elements() {
        System.out.println("elements");
        Permission permission0 = new AccessPermission("org.some.class");
        Permission permission1 = new AccessPermission("org.some.other.class");
        Permission permission2 = new AccessPermission("org.another.class");
        PermissionCollection instance = new MultiReadPermissionCollection(permission0);
        instance.add(permission0);
        instance.add(permission1);
        instance.add(permission2);
        ArrayList<Permission> expResult = new ArrayList<Permission>();
        expResult.add(permission0);
        expResult.add(permission1);
        expResult.add(permission2);
        Enumeration<Permission> elem = instance.elements();
        ArrayList<Permission> result = new ArrayList<Permission>();
        while (elem.hasMoreElements()){
            result.add(elem.nextElement());
        }
        int expRes = expResult.size();
        int res = result.size();
        assertEquals(expRes, res);
    }



}