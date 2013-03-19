/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.impl.net;

import java.net.URI;
import java.net.URISyntaxException;
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
    public void testEscapeIllegalCharacters() throws URISyntaxException {
        System.out.println("escapeIllegalCharacters");
        String url = " ";
        String expResult = "%20";
        String result = UriString.escapeIllegalCharacters(url);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testEscape() throws URISyntaxException {
        System.out.println("test escape");
        String url = "file:/c:/Program Files/java";
        String expResult = "file:/c:/Program%20Files/java";
        String result = UriString.escapeIllegalCharacters(url);
        assertEquals(expResult, result);
        url = "file:/c:/Program Files/java<|>lib";
        expResult = "file:/c:/Program%20Files/java%3C%7C%3Elib";
        result = UriString.escapeIllegalCharacters(url);
        assertEquals(expResult, result);
    }
    
//    @Test
//    public void testNormalise() throws URISyntaxException{
//        System.out.println("normalise test");
//        URI uri = new URI("FILE:/c:/Program%20Files/java");
//        String expResult = "file:/C:/PROGRAM%20FILES/JAVA";
//        String result = UriString.normalise(uri).toString();
//        assertEquals(expResult, result);
//    }
            
    
    @Test
    public void testNormalisation() throws URISyntaxException {
        System.out.println("URI Normalisation Test");
        URI url = new URI("HTTP://river.apache.ORG/foo%7ebar/file%3clib");
        URI expResult = new URI("http://river.apache.org/foo~bar/file%3Clib");
        URI result = UriString.normalisation(url);
        assertEquals(expResult, result);
        assertEquals(result.toString(), "http://river.apache.org/foo~bar/file%3Clib");
    }
    
    @Test
    public void testNormalisation2() throws URISyntaxException {
        System.out.println("URI Normalisation Test 2");
        URI url = new URI("http://Bryan-Thompson-MacBook-Air.local:9082/qa1-start-testservice1-dl.jar");
        URI expResult = new URI("http://bryan-thompson-macbook-air.local:9082/qa1-start-testservice1-dl.jar");
        URI result = UriString.normalisation(url);
        assertEquals(expResult.toString(), result.toString());
    }
    
    @Test
    public void testNormalisation3() throws URISyntaxException {
        System.out.println("URI Normalisation Test 3");
        URI url = new URI("http://Bryan-Thompson-MacBook-Air.local:9082/qa1-start-testservice1-dl.jar");
        String host = url.getHost();
        String expHost = "Bryan-Thompson-MacBook-Air.local";
        assertEquals(expHost, host);
        url = UriString.normalisation(url);
        host = url.getHost();
        expHost = "bryan-thompson-macbook-air.local";
        assertEquals(expHost, host);
    }
    
//    @Test
//    public void testFixWindowsURI() {
//        System.out.println("Test fix Windows file URI string");
//        String uri = "file:C:\\home\\user";
//        String expResult = "file:/C:/home/user";
//        String result = UriString.fixWindowsURI(uri);
//        assertEquals(expResult, result);
//    }
}
