/*
 * Copyright 2019 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.core.discovery;

import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.lookup.ServiceRegistrar;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter
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
    public void testGetHost() {
        System.out.println("getHost");
        LookupLocator instance = new LookupLocator("fe80::1d72:62a4:a256:2b09%11", 4160);
        String expResult = "[fe80::1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    /**
     * Test of getHost method, of class LookupLocator.
     */
    @Test
    public void testGetHost2() {
        System.out.println("getHost2");
        LookupLocator instance = new LookupLocator("fe80:0:0:0:1d72:62a4:a256:2b09", 4160);
        String expResult = "[fe80::1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    /**
     * Test of getHost method, of class LookupLocator.
     */
    @Test
    public void testGetHost3() {
        System.out.println("getHost3");
        LookupLocator instance = new LookupLocator("[fe80:0:0:0:1d72:62a4:a256:2b09]", 4160);
        String expResult = "[fe80::1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost4() {
        System.out.println("getHost4");
        LookupLocator instance = new LookupLocator("[fe80:0:0:0:1d72:62a4:a256:2b09%2511]", 4160);
        String expResult = "[fe80::1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost5() {
        System.out.println("getHost5");
        LookupLocator instance = new LookupLocator("fe80:0000:0000:0000:1d72:62a4:a256:2b09%2511", 4160);
        String expResult = "[fe80::1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost6() {
        System.out.println("getHost6");
        LookupLocator instance = new LookupLocator("fe80:0000:0000:0000:0d72:62a4:a256:2b09%2511", 4160);
        String expResult = "[fe80::d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost7() {
        System.out.println("getHost7");
        LookupLocator instance = new LookupLocator("fe80:0000:0000:62a4:0d72:0000:0:0%2511", 4160);
        String expResult = "[fe80:0:0:62a4:d72::]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost8() {
        System.out.println("getHost8");
        LookupLocator instance = new LookupLocator("fe80:0000:1:b:1d72:62a4:a256:2b09%2511", 4160);
        String expResult = "[fe80:0:1:b:1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost9() {
        System.out.println("getHost9");
        LookupLocator instance = new LookupLocator("0000:0000:0000:0000:1d72:62a4:a256:2b09%2511", 4160);
        String expResult = "[::1d72:62a4:a256:2b09]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost10() {
        System.out.println("getHost10");
        LookupLocator instance = new LookupLocator("0000:0000:0000:0000:0000:FFFF:192.168.7.1", 4160);
        String expResult = "[::ffff:192.168.7.1]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    @Test
    public void testGetHost11() {
        System.out.println("getHost11");
        LookupLocator instance = new LookupLocator("::FFFF:192.168.7.1", 4160);
        String expResult = "[::ffff:192.168.7.1]";
        String result = instance.getHost();
        assertEquals(expResult, result);
    }
    
//    /**
//     * Test of toString method, of class LookupLocator.
//     */
//    @Test
//    public void testToString() {
//        System.out.println("toString");
//        LookupLocator instance = null;
//        String expResult = "";
//        String result = instance.toString();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of equals method, of class LookupLocator.
//     */
//    @Test
//    public void testEquals() {
//        System.out.println("equals");
//        Object o = null;
//        LookupLocator instance = null;
//        boolean expResult = false;
//        boolean result = instance.equals(o);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of hashCode method, of class LookupLocator.
//     */
//    @Test
//    public void testHashCode() {
//        System.out.println("hashCode");
//        LookupLocator instance = null;
//        int expResult = 0;
//        int result = instance.hashCode();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
    
}
