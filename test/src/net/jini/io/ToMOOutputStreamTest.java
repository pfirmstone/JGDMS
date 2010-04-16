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
import java.io.OutputStream;
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
public class ToMOOutputStreamTest {
    public ToMOOutputStreamTest(){}
    String strObject;
    MarshalledInstance mi;
    Convert convert;
    net.jini.io.MarshalledObject jinmo;
    @org.junit.BeforeClass
    public static void setUpClass() throws Exception {
    }

    @org.junit.AfterClass
    public static void tearDownClass() throws Exception {
    }

    @org.junit.Before
    public void setUp() throws Exception {
        strObject = "Test String";
        mi = new MarshalledInstance(strObject);
        convert = Convert.getInstance();
        jinmo = convert.toJiniMarshalledObject(mi);
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ToMOOutputStream(baos);
            oos.writeObject(jinmo);
            oos.flush();
            byte[] bytes = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            java.rmi.MarshalledObject mo = (java.rmi.MarshalledObject) ois.readObject();
            if (mo == null) {
                fail("MarshalledObject returned was null");
            }
            String result = (String) mo.get();
            System.out.println(result);
            assertEquals(strObject, result);
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("The test threw an exception: " + ex.getMessage());
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            fail("The test threw an exception: " + ex.getMessage());
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            fail("The test threw an exception: " + ex.getMessage());
        }
    }
}
