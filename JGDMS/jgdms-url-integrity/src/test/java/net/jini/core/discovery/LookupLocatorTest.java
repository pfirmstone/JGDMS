/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.jini.core.discovery;

import java.net.MalformedURLException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class LookupLocatorTest {
    
    public LookupLocatorTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of getHost method, of class LookupLocator.
     */
    @Test
    public void testGetHost() throws MalformedURLException {
        System.out.println("getHost");
        LookupLocator instance = new LookupLocator("jini://new-1:4171");
        String expResult = "new-1";
        String result = instance.getHost();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of getPort method, of class LookupLocator.
     */
    @Test
    public void testGetPort() throws MalformedURLException {
        System.out.println("getPort");
        LookupLocator instance = new LookupLocator("jini://new-1:4171");;
        int expResult = 4171;
        int result = instance.getPort();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

   
}

