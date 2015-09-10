/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
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

package org.apache.river.concurrent;

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
        Set<String> set2 = RC.set(new TreeSet<Referrer<String>>(), Ref.SOFT, 10000L);
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
