/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.api.security;

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
     * Test of escapeIllegalCharacters method, of class UriString.
     */
    @Test
    public void testEscapeIllegalCharacters() {
        System.out.println("escapeIllegalCharacters");
        String url = " ";
        String expResult = "%20";
        String result = UriString.escapeIllegalCharacters(url);
        assertEquals(expResult, result);
    }
}
