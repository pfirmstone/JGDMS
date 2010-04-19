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
public class MoToMiInputStreamTest {
    public MoToMiInputStreamTest(){}
    String strObject;
    MarshalledInstance mi;
    MarshalledObject mo;
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
        mo = new MarshalledObject(strObject);
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    /**
     * Test of toRmiMarshalledObject method, of class Convert.
     */
    @org.junit.Test
    public void toMarshalledInstance() {
        System.out.println("toMarshalledInstance");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(mo);
            oos.flush();
            
            byte[] bytes = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new MoToMiInputStream(bais);
            MarshalledInstance mi = (MarshalledInstance) ois.readObject();
            if (mi == null) {
                fail("MarshalledObject returned was null");
            }
            String result = (String) mi.get(false);
            System.out.println(result);
            assertEquals(mi, this.mi);
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
