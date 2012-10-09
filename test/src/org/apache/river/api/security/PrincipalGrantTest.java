/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.api.security;

import java.security.cert.CertificateException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import tests.support.MyPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * 
 */
public class PrincipalGrantTest {
    
    public PrincipalGrantTest() {
    }
    
    Principal pal1;
    Principal pal2;
    Principal[] pals;
    Permission perm1;
    Permission perm2;
    Permission[] perms;
    PrincipalGrant instance;
    CertificateFactory cf;
    Certificate[] certs1, certs2;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        try {
	    cf = CertificateFactory.getInstance("X.509");
	} catch ( CertificateException e) {
	    cf = null;
	}
        pal1 = new MyPrincipal("Test Principal 1");
        pal2 = new MyPrincipal("Test Principal 2");
        pals = new Principal[2];
        pals[0]= pal1;
        pals[1]= pal2;
        perm1 = new RuntimePermission("getProtationDomain");
        perm2 = new RuntimePermission("getClassLoader");
        perms = new Permission[2];
        perms[0] = perm1;
        perms[1] = perm2;
        instance = new PrincipalGrant(pals,perms);
    }

    /**
     * Test of equals method, of class PrincipalGrant.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        PermissionGrant o = PermissionGrantBuilder.newBuilder()
            .principals(pals)
            .permissions(perms)
            .context(PermissionGrantBuilder.PRINCIPAL)
            .build();
        Object o2 = new Object();
        boolean result = instance.equals(o);
        assertEquals(true, result);
        result = instance.equals(o2);
        assertEquals(false, result);
    }


    /**
     * Test of implies method, of class PrincipalGrant.
     */
    @Test
    public void testImplies_PrincipalArr() {
        System.out.println("implies");
        Principal[] prs = new Principal[0];
        boolean expResult = false;
        boolean result = instance.implies(prs);
        assertEquals(expResult, result);
    }

    /**
     * Test of getBuilderTemplate method, of class PrincipalGrant.
     */
    @Test
    public void testGetBuilderTemplate() {
        System.out.println("getBuilderTemplate");
        PermissionGrantBuilder pgb = instance.getBuilderTemplate();
        PermissionGrant pg = pgb.build();
        assertFalse(pg == instance); // we might change this if we create an object pool
        assertEquals(instance, pg);
    }

    /**
     * Test of getPermissions method, of class PrincipalGrant.
     */
    @Test
    public void testGetPermissions() {
        System.out.println("getPermissions");
        Collection expResult = Arrays.asList(perms);
        Collection result = instance.getPermissions();
        assertTrue(result.containsAll(expResult));
    }

    /**
     * Test of readResolve method, of class PrincipalGrant.
     */
    @Test
    public void testSerialization() {
        System.out.println("Serialization test");
        PrincipalGrant result = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            out = new ObjectOutputStream(baos);
            out.writeObject(instance);
            // Unmarshall it
            in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
            result = (PrincipalGrant) in.readObject();
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
        } catch (ClassNotFoundException ex){
            ex.printStackTrace(System.out);
        }
        assertEquals(instance, result);
    }
}
