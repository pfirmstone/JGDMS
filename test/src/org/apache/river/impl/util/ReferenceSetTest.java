/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.impl.util;

import java.util.TreeSet;
import java.lang.ref.Reference;
import java.util.Set;
import java.util.HashSet;
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
public class ReferenceSetTest {
    
    public ReferenceSetTest() {
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

    @Test
    public void testEquals() {
        System.out.println("testEquals");
        Set<String> set1 = new HashSet<String>(3);
        Set<String> set2 = RC.set(new TreeSet<Referrer<String>>(), Ref.SOFT);
        String s1 = "1", s2 = "2", s3 = "3";
        set1.add(s1);
        set1.add(s2);
        set1.add(s3);
        set2.add(s1);
        set2.add(s2);
        set2.add(s3);
        assertTrue(set1.equals(set2));
        assertTrue(set2.equals(set1));
        assertTrue(set1.hashCode() == set2.hashCode());
    }
}
