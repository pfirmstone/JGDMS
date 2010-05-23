/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.security.policy;

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
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class DynamicPolicyProviderTest {

    public DynamicPolicyProviderTest() {
    }
    
    DynamicPolicyProvider instance;

    @org.junit.Before
    public void setUp() throws Exception {
        instance = new DynamicPolicyProvider();
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
        Permission[] permissions = null;
        instance.grant(cl, principals, permissions);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
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