/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.io;

import java.io.IOException;
import java.rmi.MarshalledObject;
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
public class ConvertTest {

    public ConvertTest() {
    }
    String strObject;
    
    @org.junit.BeforeClass
    public static void setUpClass() throws Exception {
    }

    @org.junit.AfterClass
    public static void tearDownClass() throws Exception {
    }

    @org.junit.Before
    public void setUp() throws Exception {
        strObject = "Test String";
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    /**
     * Test of toRmiMarshalledObject method, of class Convert.
     */
    @org.junit.Test
    public void toRmiMarshalledObject() {
        try {
            System.out.println("toRmiMarshalledObject");
            CDCMarshalledObject<String> instance_2 = new CDCMarshalledObject<String>(strObject);
            Convert<String> convert = new Convert<String>();
            MarshalledObject<String> expResult = new MarshalledObject<String>(strObject);
            MarshalledObject<String> result = convert.toRmiMarshalledObject(instance_2);
            assertEquals(expResult, result);         
            //fail("The test case is a prototype.");
        } catch (Exception ex) {
            fail("The test threw an exception: " + ex.getMessage());
        }
    }

    /**
     * Test of toMarshalledInstance method, of class Convert.
     */
    @org.junit.Test
    public void toMarshalledInstance() {
        try {
            System.out.println("toMarshalledInstance");
            MarshalledObject<String> instance_2 = new MarshalledObject<String>(strObject);
            Convert<String> convert = new Convert<String>();
            MarshalledInstance<String> expResult = new MarshalledInstance<String>(strObject);
            MarshalledInstance<String> result = convert.toMarshalledInstance(instance_2);
            assertEquals(expResult, result);
            
            //fail("The test case is a prototype.");
        } catch (Exception ex) {
            ex.printStackTrace();    
            fail("The test threw an exception: " + ex.getMessage());
        }
    }

    /**
     * Test of toCDCMarshalledObject method, of class Convert.
     */
    @org.junit.Test
    public void toCDCMarshalledObject() {
        try {
            System.out.println("toCDCMarshalledObject");
            MarshalledObject<String> instance_2 = new MarshalledObject<String>(strObject);
            Convert<String> instance = new Convert<String>();
            CDCMarshalledObject<String> expResult = new CDCMarshalledObject<String>(strObject);
            CDCMarshalledObject<String> result = instance.toCDCMarshalledObject(instance_2);
            assertEquals(expResult, result);
            // TODO review the generated test code and remove the default call to fail.
            //fail("The test case is a prototype.");
        } catch (Exception ex) {
            fail("The test threw an exception: " + ex.getMessage());
        }
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

}