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
package org.apache.river.test.spec.url.https.integrity;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig; // base class for QAConfig
import org.apache.river.qa.harness.Test;
// java.util
import java.util.logging.Level;

// davis packages
import net.jini.url.https.HttpsIntegrityVerifier;

// Abstract ProvidesIntegrity Test
import org.apache.river.test.spec.url.util.AbstractProvidesIntegrity;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method.
 *   {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *   should return:
 *     true   if the specified URL uses the "https" protocol;
 *     false  otherwise.
 *   The method throws:
 *     {@link NullPointerException} - if the URL is null
 *
 * Test Cases:
 *   This test invokes
 *   {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *   with different URL objects.
 *   The cases:
 *     - testcase1: (HTTPS URL object)
 *         URL: {@link #tc1_url}
 *       It's expected that
 *       {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc1_expected}.
 *     - testcase2: (HTTPMD URL object)
 *         URL: {@link #tc2_url}
 *       It's expected that
 *       {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc2_expected}.
 *     - testcase3: (HTTP URL object)
 *         URL: {@link #tc3_url}
 *       It's expected that
 *       {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc3_expected}.
 *     - testcase4: (FILE URL object)
 *         URL: {@link #tc4_url}
 *       It's expected that
 *       {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *       returns {@link #tc4_expected}.
 *     - testcase5: (URL object = null)
 *         URL: {@link #tc5_url}
 *       It's expected that
 *       {@link HttpsIntegrityVerifier#providesIntegrity(URL)} method
 *       throws {@link #tc5_expected}.
 *
 * Infrastructure:
 *     - {@link AbstractProvidesIntegrity}
 *         abstract class that is extended by {@link ProvidesIntegrity} class
 *     - {@link org.apache.river.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
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
 *     - creating {@link HttpsIntegrityVerifier} object,
 *     - in each Test Case the test does the following:
 *       1) constructing a
 *          {@link org.apache.river.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
 *          object,
 *       2) invoking
 *          {@link HttpsIntegrityVerifier#providesIntegrity(URL)}
 *          method for the specified URL object,
 *       3) comparing
 *          {@link HttpsIntegrityVerifier#providesIntegrity(URL)}
 *          result (true, false or exception) with the expected result from
 *          the
 *          {@link org.apache.river.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
 *          object.
 *
 * </pre>
 */
public class ProvidesIntegrity extends AbstractProvidesIntegrity {

    /**
     * {@link java.net.URL URL} object for test case 1.
     */
    protected static final String tc1_url = "https://localhost/file.jar";

    /**
     * {@link java.net.URL URL} object for test case 2.
     */
    protected static final String tc2_url =
            "httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789";

    /**
     * {@link java.net.URL URL} object for test case 3.
     */
    protected static final String tc3_url = "http://localhost/file.jar";

    /**
     * {@link java.net.URL URL} object for test case 4.
     */
    protected static final String tc4_url = "file:/file.jar";

    /**
     * {@link java.net.URL URL} object for test case 5.
     */
    protected static final String tc5_url = null;

    /**
     * Test Cases {@link java.net.URL URL} objects.
     */
    protected static final String[] tc_url = {
                                               tc1_url,
                                               tc2_url,
                                               tc3_url,
                                               tc4_url,
                                               tc5_url
                                             };

    /**
     * Expected result for test case 1.
     */
    protected static final String tc1_expected = "true";

    /**
     * Expected result for test case 2.
     */
    protected static final String tc2_expected = "false";

    /**
     * Expected result for test case 3.
     */
    protected static final String tc3_expected = "false";

    /**
     * Expected result for test case 4.
     */
    protected static final String tc4_expected = "false";

    /**
     * Expected result for test case 5.
     */
    protected static final String tc5_expected =
            "java.lang.NullPointerException.class";

    /**
     * Test Cases expected results.
     */
    protected static final String[] tc_expected = {
                                                    tc1_expected,
                                                    tc2_expected,
                                                    tc3_expected,
                                                    tc4_expected,
                                                    tc5_expected
                                                  };

    /**
     * {@link HttpsIntegrityVerifier HttpsIntegrityVerifier} object.
     */
    protected static final HttpsIntegrityVerifier verifier = new
            HttpsIntegrityVerifier();

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     *   - creating
     *    {@link org.apache.river.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
     *     object for each test case.
     * Test parameters:
     *    - {@link java.net.URL URL} object,
     *    - expected result (boolean value or Exception class).
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        /* Creating TestItem objects */
        for (int i = 0; i < tc_url.length; i++) {
            items.add(i, new TestItem("TestCase" + (i + 1),
                                      tc_url[i],
                                      tc_expected[i]));
        }
        return this;
    }

    /**
     * Checking test assertion.
     * Invoking
     * {@link HttpsIntegrityVerifier#providesIntegrity(URL)},
     * and comparing the returned boolean value (or thrown Exception) with
     * the expected result.
     *
     * @param ti {@link org.apache.river.test.spec.url.util.AbstractProvidesIntegrity.TestItem}
     *           object that descibes a Test Case
     * @return true (if the returned result is equal to
     *         the expected one) or false otherwise
     */
    public boolean checker(TestItem ti) {
        try {
            logger.log(Level.FINE, "providesIntegrity(" + ti.getURL() + ")");
            boolean realRes = verifier.providesIntegrity(ti.getURL());

            /* Comparing boolean values */
            return ti.compare(realRes);
        } catch (Exception e) {

            /* Comparing 2 Exception objects */
            return ti.compare(e);
        }
    }
}
