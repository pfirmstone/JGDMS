/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
public class PackageVersionTest {

    public PackageVersionTest() {
    }
    Package[] pkg;
    
    @org.junit.BeforeClass
    public static void setUpClass() throws Exception {
    }

    @org.junit.AfterClass
    public static void tearDownClass() throws Exception {
    }

    @org.junit.Before
    public void setUp() throws Exception {
        pkg = Package.getPackages();
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    /**
     * Test of getInstance method, of class PackageVersion.
     */
    @org.junit.Test
    public void getInstance() {
        System.out.println("getInstance");
        String pkgName = pkg[0].getName();
        String impVendor = pkg[0].getImplementationVendor();
        String impVersion = pkg[0].getImplementationVersion();
        System.out.println(pkgName + "|" + impVendor + "|" +impVersion);
        PackageVersion expResult = PackageVersion.getInstance(pkg[0]);
        PackageVersion result = PackageVersion.getInstance(pkgName, impVendor, impVersion);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        // fail("The test case is a prototype.");
    }

    /**
     * Test of equals method, of class PackageVersion.
     */
    @org.junit.Test
    public void equalHashCode() {
        System.out.println("equalsHashCode");
        Object obj = PackageVersion.getInstance(pkg[1]);
        String pkgName = pkg[1].getName();
        String impVendor = pkg[1].getImplementationVendor();
        String impVersion = pkg[1].getImplementationVersion();
        System.out.println(pkgName + "|" + impVendor + "|" +impVersion);
        PackageVersion instance = PackageVersion.getInstance(pkgName, impVendor,
                impVersion);
        boolean expResult = true;
        boolean result = instance.equals(obj);
        assertEquals(expResult, result);
        assertEquals(obj.hashCode(), instance.hashCode());
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of equalsPackage method, of class PackageVersion.
     */
    @org.junit.Test
    public void equalsPackage() {
        System.out.println("equalsPackage");
        String pkgName = pkg[1].getName();
        String impVendor = pkg[1].getImplementationVendor();
        String impVersion = pkg[1].getImplementationVersion();
        System.out.println(pkgName + "|" + impVendor + "|" +impVersion);
        PackageVersion instance = PackageVersion.getInstance(pkg[1]);
        boolean expResult = true;
        boolean result = instance.equalsPackage(pkg[1]);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
    
   /**
     * Test of equalsPackage method, of class PackageVersion.
     */
    @org.junit.Test
    public void equalsPackage2() {
        System.out.println("equalsPackage2");
        String pkgName = pkg[2].getName();
        String impVendor = pkg[2].getImplementationVendor();
        String impVersion = pkg[2].getImplementationVersion();
        System.out.println(pkgName + "|" + impVendor + "|" +impVersion);
        PackageVersion instance = PackageVersion.getInstance(pkg[2]);
        boolean expResult = false;
        boolean result = instance.equalsPackage(pkg[3]);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
    
   /**
     * Test of equalsPackage method, of class PackageVersion.
     */
    @org.junit.Test
    public void marshalUnmarshal() {
        System.out.println("marshalUnmarshal");
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            PackageVersion instance = PackageVersion.getInstance(pkg[2]);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out = new ObjectOutputStream(baos);
            out.writeObject(instance);
            // Unmarshall it
            in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
            PackageVersion result = (PackageVersion) in.readObject();
            assertEquals(instance, result);
            // TODO review the generated test code and remove the default call to fail.
            //fail("The test case is a prototype.");
        } catch (IOException ex) {
            System.out.println( ex.getMessage());
            ex.printStackTrace(System.out);
            fail("The test case threw an exception.");
        } catch (ClassNotFoundException ex) {
            System.out.println( ex.getMessage());
            ex.printStackTrace(System.out);
            fail("The test case threw an exception.");
        } finally {
            try {
                out.close();
            } catch (IOException ex) {
                System.out.println( ex.getMessage());
                ex.printStackTrace(System.out);
                fail("The test case threw an exception.");
            }
        }
    }
}
