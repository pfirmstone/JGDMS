/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.se;

import java.io.FilePermission;
import net.jini.security.policy.*;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tests.support.MyPrincipal;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class DynamicConcurrentPolicyProviderTest {

    public DynamicConcurrentPolicyProviderTest() {
    }
    
    DynamicConcurrentPolicyProvider instance;
    Principal[] pra = null;
    Permission[] pma = null;    
    Principal pr1 = new MyPrincipal("1");
    Principal pr2 = new MyPrincipal("1");
    Principal pr3 = new MyPrincipal("2");
    Permission pm1 = new FilePermission("1", "read");
    Permission pm2 = new FilePermission("1", "read");
    Permission pm3 = new FilePermission("2", "read");
    Permission pm4 = new FilePermission("4", "read");
    Permission[] pmGranted = new Permission[] { pm1, pm2, pm3 };

    @org.junit.Before
    public void setUp() throws Exception {
        Policy basePolicy = new PolicyFileProvider();
        instance = new DynamicConcurrentPolicyProvider();
        instance.basePolicy(basePolicy);
        instance.initialize();
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    /**
     * Test of revoke method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void revoke() {
        System.out.println("revoke");
        Class cl = null;
        Principal[] principals = null;
        Permission[] permissions = null;
        instance.revoke(cl, principals, permissions);
        fail("The test case is a prototype.");
    }

    /**
     * Test of revokeSupported method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void revokeSupported() {
        System.out.println("revokeSupported");
        boolean expResult = false;
        boolean result = instance.revokeSupported();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getProvider method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void getProvider() {
        System.out.println("getProvider");
        Provider expResult = null;
        Provider result = instance.getProvider();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getType method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void getType() {
        System.out.println("getType");
        String expResult = "";
        String result = instance.getType();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getPermissions method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void getPermissions() {
        System.out.println("getPermissions");
        CodeSource codesource = null;
        PermissionCollection expResult = null;
        PermissionCollection result = instance.getPermissions(codesource);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of implies method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void implies() {
        System.out.println("implies");
        ProtectionDomain domain = null;
        Permission permission = null;
        boolean expResult = false;
        boolean result = instance.implies(domain, permission);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of refresh method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void refresh() {
        System.out.println("refresh");
        instance.refresh();
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of grantSupported method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void grantSupported() {
        System.out.println("grantSupported");
        boolean expResult = false;
        boolean result = instance.grantSupported();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of grant method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void grant() {
        System.out.println("grant");
        Class cl = null;
        Principal[] principals = null;
        instance.grant(cl, principals, pmGranted);
        // TODO review the generated test code and remove the default call to fail.
        assertTrue(instance.implies(null, pm1));
        assertTrue(instance.implies(null, pm2));
        assertTrue(instance.implies(null, pm3));
        //fail("The test case is a prototype.");
    }

    /**
     * Test of getGrants method, of class DynamicPolicyProvider.
     */
    @org.junit.Test
    public void getGrants() {
        System.out.println("getGrants");
        Class cl = null;
        Principal[] principals = null;
        Permission[] expResult = null;
        Permission[] result = instance.getGrants(cl, principals);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}