/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.jini.test.spec.url.httpmd.handler;

// java.util
import java.util.logging.Level;

// java.net
import java.net.URL;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of {@link URL#equals(Object)} method
 *   for HTTPMD URL objects.
 *   {@link Handler#sameFile(URL,URL)} method is
 *   used to see if these HTTPMD URL objects refer to
 *   the same file using the following rules:
 *     - case-insensitive comparison of the protocols and of the
 *       message digest parameters (ignoring the comment)
 *     - calls hostsEqual to compare the hosts
 *     - compares the ports, with -1 matching the default HTTP port (80)
 *     - case-sensitive comparison on the remaining portions
 *
 * Test Cases:
 *   This test tries to compare two HTTPMD URL objects using
 *   {@link URL#equals(Object)} method. {@link URL#equals(Object)} method
 *   invokes {@link Handler#equals(URL,URL) method inherited
 *   from {@link URLStreamHandler} class.
 *   {@link URLStreamHandler#equals(URL,URL)} method uses
 *   {@link Handler#sameFile(URL,URL)} method to compare
 *   HTTPMD URL objects.
 *   The cases:
 *     - equalsTheSame
 *       url1 = url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsAlgorithmCase
 *       url1 = httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=ABCDEFabcdef0123456789
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsDigestCase
 *       url1 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=ABCDEFabcdef0123456789
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsCommentsIgnore
 *       url1 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsCommentsIgnore2
 *       url1 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms1
 *       url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms2
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsPort
 *       url1 = httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsDefaultPort
 *       url1 = httpmd://localhost:80/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsDefaultDiffPort
 *       url1 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost:8001/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsRef
 *       url1 = httpmd://localhost/file.jar;MD5=abc#Ref
 *       url2 = httpmd://localhost/file.jar;MD5=abc#Ref
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsQuery
 *       url1 = httpmd://localhost/file.jar;MD5=abc?query
 *       url2 = httpmd://localhost/file.jar;MD5=abc?query
 *       it's expected that url1.equals(url2) returns true;
 *     - equalsHTTP
 *       url1 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = http://localhost/file.jar
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsFILE
 *       url1 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = file:/file.jar
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffHost
 *       url1 = httpmd://remotehost/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffPort
 *       url1 = httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost:8001/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffAlgorithm
 *       url1 = httpmd://localhost/file.jar;SHA=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffAlgorithm2
 *       url1 = httpmd://localhost/file.jar;SHA=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffAlgorithm3
 *       url1 = httpmd://localhost/file.jar;sha=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffAlgorithm4
 *       url1 = httpmd://localhost/file.jar;sha=abcdefABCDEF0123456789
 *       url2 = httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffDigest
 *       url1 = httpmd://localhost/file.jar;MD5=abcdef
 *       url2 = httpmd://localhost/file.jar;MD5=0123456789
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffRef
 *       url1 = httpmd://localhost/file.jar;MD5=abc#Ref1
 *       url2 = httpmd://localhost/file.jar;MD5=abc#Ref2
 *       it's expected that url1.equals(url2) returns false;
 *     - equalsDiffQuery
 *       url1 = httpmd://localhost/file.jar;MD5=abc?query1
 *       url2 = httpmd://localhost/file.jar;MD5=abc?query2
 *       it's expected that url1.equals(url2) returns false;
 *
 * Infrastructure:
 *     - AbstractSameFile
 *         abstract class that performs all actions
 *     - AbstractSameFile.TestItem
 *         auxiliary class that describes a Test Case
 *     - Equals extends AbstractSameFile
 *         overrides AbstractSameFile.compare() method
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD URL objects creating
 *       ({@link Handler} is used as HTTPMD
 *       Protocol handler).
 *   Test performs the following steps in each Test Case:
 *     - creating 2 URL objects from the String representation,
 *     - comparing these 2 URL objects using {@link URL#equals(Object)} method.
 *
 * </pre>
 */
public class Equals extends AbstractSameFile {

    /**
     * Comparing two URL objects using {@link URL#equals(Object)} method.
     *
     * @param u1 URL object to be compared
     * @param u2 URL object to be compared
     * @return result of comparison (true or false)
     */
    public boolean compare(URL u1, URL u2) {
        logger.log(Level.FINE, "(" + u1 + ").equals(" + u2 + ")");
        return u1.equals(u2);
    }
}
