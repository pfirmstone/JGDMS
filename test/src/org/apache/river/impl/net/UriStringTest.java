/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.impl.net;

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
public class UriStringTest {
    
    public UriStringTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of parse method, of class UriString.
     */
    @Test
    public void testEscapeIllegalCharacters() {
        System.out.println("escapeIllegalCharacters");
        String url = " ";
        String expResult = "%20";
        String result = UriString.parse(url);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testWindowsDrives() {
        System.out.println("windows drive letters");
        String url = "file:c:/Program Files/java";
        String expResult = "file:///C:/Program%20Files/java";
        String result = UriString.parse(url);
        assertEquals(expResult, result);
        url = "file:/c:/Program Files/java lib";
        expResult = "file:///C:/Program%20Files/java%20lib";
        result = UriString.parse(url);
        assertEquals(expResult, result);
        url = "file:///c:/Program Files/java";
        expResult = "file:///C:/Program%20Files/java";
        result = UriString.parse(url);
        assertEquals(expResult, result);
    }
}
