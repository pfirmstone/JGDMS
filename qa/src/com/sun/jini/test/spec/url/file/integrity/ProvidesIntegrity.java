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
package com.sun.jini.test.spec.url.file.integrity;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.Test;
// java.util
import java.util.logging.Level;

// davis packages
import net.jini.url.file.FileIntegrityVerifier;

// Abstract ProvidesIntegrity Test
import com.sun.jini.test.spec.url.util.AbstractProvidesIntegrity;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link FileIntegrityVerifier#providesIntegrity(URL)} method.
 *   {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *   should return:
 *     true   if the specified URL uses the "file" protocol and
 *            the host is null, empty, "~", or "localhost";
 *     false  otherwise.
 *   The method throws:
 *     {@link NullPointerException} - if the URL is null
 *
 * Test Cases:
 *   This test invokes
 *   {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *   with different URL objects.
 *   The cases:
 *     - testcase1: (FILE URL object)
 *         protocol of URL: {@link #tc1_prot}
 *         host     of URL: {@link #tc1_host}
 *         file     of URL: {@link #tc1_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc1_expected}.
 *     - testcase2: (FILE URL object)
 *         protocol of URL: {@link #tc2_prot}
 *         host     of URL: {@link #tc2_host}
 *         file     of URL: {@link #tc2_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc2_expected}.
 *     - testcase3: (FILE URL object)
 *         protocol of URL: {@link #tc3_prot}
 *         host     of URL: {@link #tc3_host}
 *         file     of URL: {@link #tc3_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc3_expected}.
 *     - testcase4: (FILE URL object)
 *         protocol of URL: {@link #tc4_prot}
 *         host     of URL: {@link #tc4_host}
 *         file     of URL: {@link #tc4_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc4_expected}.
 *     - testcase5: (FILE URL object)
 *         protocol of URL: {@link #tc5_prot}
 *         host     of URL: {@link #tc5_host}
 *         file     of URL: {@link #tc5_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc5_expected}.
 *     - testcase6: (HTTPMD URL object)
 *         protocol of URL: {@link #tc6_prot}
 *         host     of URL: {@link #tc6_host}
 *         file     of URL: {@link #tc6_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc6_expected}.
 *     - testcase7: (HTTPS URL object)
 *         protocol of URL: {@link #tc7_prot}
 *         host     of URL: {@link #tc7_host}
 *         file     of URL: {@link #tc7_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc7_expected}.
 *     - testcase8: (HTTP URL object)
 *         protocol of URL: {@link #tc8_prot}
 *         host     of URL: {@link #tc8_host}
 *         file     of URL: {@link #tc8_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc8_expected}.
 *     - testcase9: (URL object = null)
 *         protocol of URL: {@link #tc9_prot}
 *         host     of URL: {@link #tc9_host}
 *         file     of URL: {@link #tc9_file}
 *       It's expected that
 *       {@link FileIntegrityVerifier#providesIntegrity(URL)} method
 *       throws {@link #tc9_expected}.
 *
 * Infrastructure:
 *     - {@link AbstractProvidesIntegrity}
 *         abstract class that is extended by {@link ProvidesIntegrity} class
 *     - {@link com.sun.jini.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
 *         auxiliary class that describes a Test Case
 *     - {@link ProvidesIntegrity}
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD urls creating
 *       ({@link net.jini.url.httpmd.Handler} is used as HTTPMD Protocol
 *       handler).
 *   Test performs the following steps:
 *     - creating {@link FileIntegrityVerifier} object,
 *     - in each Test Case the test does the following:
 *       1) constructing a
 *          {@link com.sun.jini.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
 *          object,
 *       2) invoking
 *          {@link FileIntegrityVerifier#providesIntegrity(URL)}
 *          method for the specified URL object,
 *       3) comparing
 *          {@link FileIntegrityVerifier#providesIntegrity(URL)}
 *          result (true, false or exception) with the expected result from
 *          the
 *          {@link com.sun.jini.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
 *          object.
 *
 * </pre>
 */
public class ProvidesIntegrity extends AbstractProvidesIntegrity {

    /**
     * Protocol for test case 1.
     */
    protected static final String tc1_prot = "file";

    /**
     * Host for test case 1.
     */
    protected static final String tc1_host = null;

    /**
     * File for test case 1.
     */
    protected static final String tc1_file = "/file.jar";

    /**
     * Expected result for test case 1.
     */
    protected static final String tc1_expected = "true";

    /**
     * Protocol for test case 2.
     */
    protected static final String tc2_prot = "file";

    /**
     * Host for test case 2.
     */
    protected static final String tc2_host = "";

    /**
     * File for test case 2.
     */
    protected static final String tc2_file = "/file.jar";

    /**
     * Expected result for test case 2.
     */
    protected static final String tc2_expected = "true";

    /**
     * Protocol for test case 3.
     */
    protected static final String tc3_prot = "file";

    /**
     * Host for test case 3.
     */
    protected static final String tc3_host = "~";

    /**
     * File for test case 3.
     */
    protected static final String tc3_file = "/file.jar";

    /**
     * Expected result for test case 3.
     */
    protected static final String tc3_expected = "true";

    /**
     * Protocol for test case 4.
     */
    protected static final String tc4_prot = "file";

    /**
     * Host for test case 4.
     */
    protected static final String tc4_host = "localhost";

    /**
     * File for test case 4.
     */
    protected static final String tc4_file = "/file.jar";

    /**
     * Expected result for test case 4.
     */
    protected static final String tc4_expected = "true";

    /**
     * Protocol for test case 5.
     */
    protected static final String tc5_prot = "file";

    /**
     * Host for test case 5.
     */
    protected static final String tc5_host = "remotehost";

    /**
     * File for test case 5.
     */
    protected static final String tc5_file = "/file.jar";

    /**
     * Expected result for test case 5.
     */
    protected static final String tc5_expected = "false";

    /**
     * Protocol for test case 6.
     */
    protected static final String tc6_prot = "httpmd";

    /**
     * Host for test case 6.
     */
    protected static final String tc6_host = "remotehost";

    /**
     * File for test case 6.
     */
    protected static final String tc6_file =
            "/file.jar;md5=abcdefABCDEF0123456789";

    /**
     * Expected result for test case 6.
     */
    protected static final String tc6_expected = "false";

    /**
     * Protocol for test case 7.
     */
    protected static final String tc7_prot = "https";

    /**
     * Host for test case 7.
     */
    protected static final String tc7_host = "remotehost";

    /**
     * File for test case 7.
     */
    protected static final String tc7_file = "/file.jar";

    /**
     * Expected result for test case 7.
     */
    protected static final String tc7_expected = "false";

    /**
     * Protocol for test case 8.
     */
    protected static final String tc8_prot = "http";

    /**
     * Host for test case 8.
     */
    protected static final String tc8_host = "remotehost";

    /**
     * File for test case 8.
     */
    protected static final String tc8_file = "/file.jar";

    /**
     * Expected result for test case 8.
     */
    protected static final String tc8_expected = "false";

    /**
     * Protocol for test case 9.
     */
    protected static final String tc9_prot = null;

    /**
     * Host for test case 9.
     */
    protected static final String tc9_host = null;

    /**
     * File for test case 9.
     */
    protected static final String tc9_file = null;

    /**
     * Expected result for test case 9.
     */
    protected static final String tc9_expected =
            "java.lang.NullPointerException.class";

    /**
     * Test Cases Protocols.
     */
    protected static final String[] tc_prot = {
                                               tc1_prot,
                                               tc2_prot,
                                               tc3_prot,
                                               tc4_prot,
                                               tc5_prot,
                                               tc6_prot,
                                               tc7_prot,
                                               tc8_prot,
                                               tc9_prot
                                             };

    /**
     * Test Cases Hosts.
     */
    protected static final String[] tc_host = {
                                               tc1_host,
                                               tc2_host,
                                               tc3_host,
                                               tc4_host,
                                               tc5_host,
                                               tc6_host,
                                               tc7_host,
                                               tc8_host,
                                               tc9_host
                                             };

    /**
     * Test Cases Files.
     */
    protected static final String[] tc_file = {
                                               tc1_file,
                                               tc2_file,
                                               tc3_file,
                                               tc4_file,
                                               tc5_file,
                                               tc6_file,
                                               tc7_file,
                                               tc8_file,
                                               tc9_file
                                             };

    /**
     * Test Cases expected results.
     */
    protected static final String[] tc_expected = {
                                                    tc1_expected,
                                                    tc2_expected,
                                                    tc3_expected,
                                                    tc4_expected,
                                                    tc5_expected,
                                                    tc6_expected,
                                                    tc7_expected,
                                                    tc8_expected,
                                                    tc9_expected
                                                  };

    /**
     * {@link FileIntegrityVerifier FileIntegrityVerifier} object.
     */
    protected static final FileIntegrityVerifier verifier = new
            FileIntegrityVerifier();

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     *   - creating
     *     {@link com.sun.jini.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
     *     object for each test case.
     * Test parameters:
     *    - protocol of {@link java.net.URL URL} object,
     *    - host of {@link java.net.URL URL} object,
     *    - file of {@link java.net.URL URL} object,
     *    - expected result (boolean value or Exception class).
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        /* Creating TestItem objects */
        for (int i = 0; i < tc_prot.length; i++) {
            items.add(i, new TestItem("TestCase" + (i + 1),
                                      tc_prot[i],
                                      tc_host[i],
                                      tc_file[i],
                                      tc_expected[i]));
        }
        return this;
    }

    /**
     * Checking test assertion.
     * Invoking
     * {@link FileIntegrityVerifier#providesIntegrity(URL)},
     * and comparing the returned boolean value (or thrown Exception) with
     * the expected result.
     *
     * @param ti {@link com.sun.jini.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
     *           object that descibes a Test Case
     * @return true (if the returned result is equal to
     *         the expected one) or false otherwise
     */
    public boolean checker(TestItem ti) {
        try {
            logger.log(Level.FINE, "providesIntegrity(" + ti.getURL() + ")");
            logger.log(Level.FINE,
                    "Protocol of URL: " + ti.getURL().getProtocol());
            logger.log(Level.FINE, "Host     of URL: " + ti.getURL().getHost());
            boolean realRes = verifier.providesIntegrity(ti.getURL());

            /* Comparing boolean values */
            return ti.compare(realRes);
        } catch (Exception e) {

            /* Comparing 2 Exception objects */
            return ti.compare(e);
        }
    }
}
