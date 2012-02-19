/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.api.security;

import java.net.MalformedURLException;
import java.security.cert.Certificate;
import java.net.URL;
import java.security.Permission;
import java.security.ProtectionDomain;
import net.jini.security.GrantPermission;
import java.security.CodeSource;
import java.security.Principal;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class CodeSourceGrantTest {
    
        RuntimePermission rpD, rpA;
//        RuntimePermission rpD1 = new RuntimePermission("D1");
//        RuntimePermission rpC = new RuntimePermission("C");
//        RuntimePermission rpC1 = new RuntimePermission("C1");
        
        String rpDS;
        
        GrantPermission gpS;
        GrantPermission gpP;
        ProtectionDomain pd1;
        CodeSourceGrant instance;
        
    public CodeSourceGrantTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() throws MalformedURLException {
        rpD = new RuntimePermission("D", "");
        rpDS = "delim=' java.lang.RuntimePermission 'D'";
        gpS = new GrantPermission(rpDS);
        gpP = new GrantPermission(rpD);
        rpA = new RuntimePermission("A");
        Permission[] perms = { rpA, gpS };
        instance = new CodeSourceGrant(null, null, perms);
        CodeSource cs = new CodeSource(new URL("file://foo.bar"), (Certificate[]) null);
        pd1 = new ProtectionDomain(cs, null);
       // CodeSource cs = 
    }

    /**
     * Test of implies method, of class CodeSourceGrant.
     */
    @Test
    public void testImplies() {
        System.out.println("implies");
        boolean expResult = true;
        boolean result = instance.implies(pd1);
        assertEquals(expResult, result);
    }

//    /**
//     * Test of getBuilderTemplate method, of class CodeSourceGrant.
//     */
//    @Test
//    public void testGetBuilderTemplate() {
//        System.out.println("getBuilderTemplate");
//        CodeSourceGrant instance = null;
//        PermissionGrantBuilder expResult = null;
//        PermissionGrantBuilder result = instance.getBuilderTemplate();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
}
