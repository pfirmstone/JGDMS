/*
 * Copyright 2021 The Apache Software Foundation.
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
package net.jini.constraint;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
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
public class MethodKeyTest {
    
    public MethodKeyTest() {
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
     * Test of equals method, of class MethodKey.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        Object o = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"});;
        MethodKey instance = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"});;
        boolean expResult = true;
        boolean result = instance.equals(o);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

    /**
     * Test of hashCode method, of class MethodKey.
     */
    @Test
    public void testHashCode() {
        System.out.println("hashCode");
        MethodKey instance = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"});
        int expResult = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"}).hashCode();
        int result = instance.hashCode();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

    /**
     * Test of compareTo method, of class MethodKey.
     */
    @Test
    public void testCompareTo() {
        System.out.println("compareTo identical methods");
        MethodKey o = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"});
        MethodKey instance = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"}); 
        int expResult = 0;
        int result = instance.compareTo(o);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }
    
    /**
     * Test of compareTo method, of class MethodKey.
     */
    @Test
    public void testCompareTo2() {
        System.out.println("compareTo duplicate methods removed and correct natural order");
        String template = "org.apache.river.reggie.proxy.Template";
        String string = "java.lang.String";
        String integer  = "int";
        String lon = "long";
        String remoteEventListener = "net.jini.core.event.RemoteEventListener";
        String marshalledObject = "java.rmi.MarshalledObject";
        String item = "org.apache.river.proxy.Item";
        MethodKey [] methodKeys = new MethodKey []{
            new MethodKey(null, null),
            new MethodKey("getAdmin", null),
            new MethodKey("*kup", null),
            new MethodKey("register", new String[]{item, lon}),
            new MethodKey("lookup", null),
            new MethodKey("getEntryClasses", new String[]{template}),
            new MethodKey("*okup", new String[]{template}),
            new MethodKey("look*", new String[]{template}),
            new MethodKey("loo*", null),
            new MethodKey("look*", null),
            new MethodKey("*up", null),
            new MethodKey("getLocator", null),
            new MethodKey("getMemberGroups", null),
            new MethodKey("notify", new String[]{template, integer, remoteEventListener, marshalledObject, lon}),
            new MethodKey("getServiceTypes", new String[]{template, string}),
            new MethodKey("lookup", new String[]{template}),
            new MethodKey("lookup", new String[]{template, integer}),
            new MethodKey("lookup", new String[]{template}),
            new MethodKey("getFieldValues", new String[]{template, integer, integer})
        };
        MethodKey [] methodKeysNaturalOrder = new MethodKey []{
            new MethodKey("getAdmin", null),
            new MethodKey("getEntryClasses", new String[]{template}),
            new MethodKey("getFieldValues", new String[]{template, integer, integer}),
            new MethodKey("getLocator", null),
            new MethodKey("getMemberGroups", null),
            new MethodKey("getServiceTypes", new String[]{template, string}),
            new MethodKey("lookup", new String[]{template}),
            new MethodKey("lookup", new String[]{template, integer}),
            new MethodKey("lookup", null),
            new MethodKey("notify", new String[]{template, integer, remoteEventListener, marshalledObject, lon}),
            new MethodKey("register", new String[]{item, lon}),
            new MethodKey("*okup", new String[]{template}),
            new MethodKey("*kup", null),
            new MethodKey("look*", new String[]{template}),
            new MethodKey("look*", null),
            new MethodKey("loo*", null),
            new MethodKey("*up", null),
            new MethodKey(null, null)
        };
        Set<MethodKey> keySet = new TreeSet<MethodKey>(Arrays.asList(methodKeys));
        int expResult = methodKeys.length - 1;
        int result = keySet.size();
        assertEquals(expResult, result);
        MethodKey [] resultArry = keySet.toArray(new MethodKey [keySet.size()]);
        System.out.println(keySet);
        assertTrue("Array from TreeSet has expected natural order" , Arrays.equals(methodKeysNaturalOrder, resultArry));
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }
    
    /**
     * Test of compareTo method, of class MethodKey.
     */
    @Test
    public void testCompareTo3() {
        System.out.println("compareTo number of method parameters");
        MethodKey o = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template", "int"});
        MethodKey instance = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"}); 
        int expResult = -1;
        int result = instance.compareTo(o);
        assertEquals(expResult, result);
        result = o.compareTo(instance);
        expResult = 1;
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }
    /**
     * Test of compareTo method, of class MethodKey.
     */
    @Test
    public void testCompareTo4() {
        System.out.println("compareTo method parameter wild card");
        MethodKey o = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template", "int"});
        MethodKey paramWildCard = new MethodKey("lookup", null);
        MethodKey instance = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template"}); 
        int expResult = -1;
        int result = instance.compareTo(o);
        assertEquals(expResult, result);
        result = o.compareTo(instance);
        expResult = 1;
        assertEquals(expResult, result);
        expResult = -1;
        result = o.compareTo(paramWildCard);
        assertEquals(expResult, result);
        result = instance.compareTo(paramWildCard);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }
    
    /**
     * Test of compareTo method, of class MethodKey.
     */
    @Test
    public void testCompareTo5() {
        System.out.println("compareTo method parameter wild card");
        MethodKey o = new MethodKey("lookup", new String [] {"org.apache.reggie.proxy.Template", "int"});
        MethodKey paramWildCard = new MethodKey("lookup", null);
        MethodKey methodNamewildCard = new MethodKey("*up", null); 
        MethodKey methodNamewildCard2 = new MethodKey("*kup", null);
        MethodKey methodNamewildCard3 = new MethodKey("look*", new String [] {"org.apache.reggie.proxy.Template"}); 
        int expResult = 1;
        int result = methodNamewildCard.compareTo(o);
        assertEquals(expResult, result);
        result = methodNamewildCard2.compareTo(o);
        assertEquals(expResult, result);
        result = methodNamewildCard.compareTo(paramWildCard);
        assertEquals(expResult, result);
        result = methodNamewildCard3.compareTo(methodNamewildCard2);
        assertEquals(expResult, result);
        expResult = -1;
        result = o.compareTo(paramWildCard);
        assertEquals(expResult, result);
        result = o.compareTo(methodNamewildCard);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }
}
