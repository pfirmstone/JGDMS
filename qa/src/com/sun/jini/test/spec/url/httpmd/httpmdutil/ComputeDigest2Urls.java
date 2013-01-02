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
package com.sun.jini.test.spec.url.httpmd.httpmdutil;

import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// davis packages
import net.jini.url.httpmd.HttpmdUtil;

// java.io
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;

// java.net
import java.net.URL;

// java.security
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link HttpmdUtil#computeDigest(URL,String)} method.
 *   {@link HttpmdUtil#computeDigest(URL,String)} method
 *   should compute the message digest of data specified by a URL or throw:
 *     IOException
 *       if an I/O exception occurs while reading data from the URL
 *     NoSuchAlgorithmException
 *       if no provider is found for the message digest algorithm
 *     NullPointerException
 *       if either argument is null
 *
 * Test Cases:
 *   This test tries to compute the message digests for data specified
 *   by URL objects according to the specified message digest algorithms.
 *   URL objects represent various protocols: file, http.
 *   The cases:
 *     - the message digest is calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)} method
 *       for the same .jar file twice; these message digests are compared;
 *       the same protocol is used;
 *       it's expected that these message digests are equal.
 *          computeDigest2UrlsSameFile
 *          computeDigest2UrlsSameHttp
 *     - the message digest is calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)} method
 *       for the same .jar file twice; these message digests are compared;
 *       different protocols are used;
 *       it's expected that these message digests are equal.
 *          computeDigest2UrlsSameFileHttp
 *     - the message digests are calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)} method
 *       for 2 different .jar files with the same contents; these message
 *       digests are compared;
 *       it's expected that these message digests are equal.
 *          computeDigest2UrlsSameContentsFile
 *          computeDigest2UrlsSameContentsFileHttp
 *          computeDigest2UrlsSameContentsHttp
 *     - the message digests are calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)} method
 *       for 2 different .jar files; these message digests are compared;
 *       it's expected that these message digests aren't equal.
 *          computeDigest2UrlsDiffFile
 *          computeDigest2UrlsDiffHttp
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     - ComputeDigest2Urls.TestItem
 *         auxiliary class that describes a Test Case
 *     - ComputeDigest2Urls
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - launching HTTP Server.
 *   Test performs the following steps:
 *     - construct a TestItem object
 *     - implementing Test Cases
 *
 * </pre>
 */
public class ComputeDigest2Urls extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * All Test Cases (each element describes a Test Case).
     */
    protected Vector items = new Vector();

    /**
     * Getting Test Class name.
     *
     * @return Test Class name
     */
    public String getTestClassName() {
        return this.getClass().getName();
    }

    /**
     * Getting Test Cases names.
     *
     * @return Test Case names
     */
    public String[] getTestCaseNames() {
        String cName =
                getTestClassName().substring(getTestClassName().lastIndexOf(".")
                + 1);
        String tests = config.getStringConfigVal(cName + ".testCases", null);
        return tests.split(" ");
    }

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     * - getting test parameters
     * Test parameters:
     *    ComputeDigest2Urls.testCases   - Test Cases names
     *    ComputeDigest2Urls.Port        - HTTP Server port number
     *    ComputeDigest2Urls.SrcDir      - HTTP Server source directory
     *                                     (directory where the files exist)
     *    &lt;TestCaseName&gt;.Protocol1 - Protocol of URL1
     *    &lt;TestCaseName&gt;.Protocol2 - Protocol of URL2
     *    &lt;TestCaseName&gt;.FileName1 - Filename of URL1
     *    &lt;TestCaseName&gt;.FileName2 - Filename of URL2
     *    &lt;TestCaseName&gt;.Algorithm - Message Digest Algorithm
     *    &lt;TestCaseName&gt;.Expected  - Expected result
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Getting test parameters and creating TestItem objects */
        String[] tc_names = getTestCaseNames();

        for (int i = 0; i < tc_names.length; i++) {
            items.add(i, new TestItem(tc_names[i]));
        }
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        boolean returnedVal = true;

        for (int i = 0; i < items.size(); i++) {
            boolean retVal = testCase((TestItem) items.get(i));

            if (retVal != true) {
                returnedVal = retVal;
            }
        }

        if (returnedVal != true) {
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }

    /**
     * Test Case actions.
     *
     * @param ti TestItem object that descibes a Test Case
     * @return result of the Test Case (true (if the returned
     *         value is equal to the expected one) or false)
     */
    public boolean testCase(TestItem ti) {

        /* Test Case name */
        String t_name = ti.getTestCaseName();
        logger.log(Level.FINE, "\n=============== Test Case name: " + t_name);

        /* Testing */
        boolean ret = checker(ti);

        if (ret != true) {
            logger.log(Level.FINE, t_name + " test case failed");
            return false;
        }
        logger.log(Level.FINE, t_name + " test case passed");
        return true;
    }

    /**
     * Checking test assertion.
     * Computing Message Digest with
     * {@link HttpmdUtil#computeDigest(URL,String)} method
     * for the both URL objects, and comparing the computed Message Digests.
     *
     * @param ti TestItem object that descibes a Test Case
     * @return true (if the computed Message Digests comport with the expected
     *         result) or false otherwise
     */
    public boolean checker(TestItem ti) {
        String mdUrl1, mdUrl2;
        String al = ti.getAlg();
        String exp = ti.getExpected();
        URL u1 = ti.getURL1();
        URL u2 = ti.getURL2();

        try {

            /* Computing the message digest for the URL1 */
            logger.log(Level.FINE,
                    "HttpmdUtil.computeDigest(" + u1 + ", " + al + ")");
            mdUrl1 = HttpmdUtil.computeDigest(u1, al);
            logger.log(Level.FINE, "Message digest: " + mdUrl1);
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while computing message digest: " + e);
            return false;
        }

        try {

            /* Computing the message digest for the URL2 */
            logger.log(Level.FINE,
                    "HttpmdUtil.computeDigest(" + u2 + ", " + al + ")");
            mdUrl2 = HttpmdUtil.computeDigest(u2, al);
            logger.log(Level.FINE, "Message digest: " + mdUrl2);
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while computing message digest: " + e);
            return false;
        }

        /* Message Digests Comparison */
        if (exp.equals("equal")) {
            logger.log(Level.FINE, "Message digests should be equal");

            if (!mdUrl1.equals(mdUrl2)) {
                logger.log(Level.FINE,
                        "Test: Failed\n" + "It's expected that the message digest are equal "
                        + "but really the message digest are different:\n"
                        + "message digest for " + u1 + ": " + mdUrl1 + "\n"
                        + "message digest for " + u2 + ": " + mdUrl2);
                return false;
            }
        } else if (exp.equals("non-equal")) {
            logger.log(Level.FINE, "Message digests shouldn't be equal");

            if (mdUrl1.equals(mdUrl2)) {
                logger.log(Level.FINE,
                        "Test: Failed\n" + "It's expected that the message digest aren't equal "
                        + "but really the message digest are equal:\n"
                        + "message digest for " + u1 + ": " + mdUrl1 + "\n"
                        + "message digest for " + u2 + ": " + mdUrl2);
                return false;
            }
        }
        return true;
    }


    /**
     * Auxiliary class that describes a Test Case.
     */
    protected class TestItem {

        /**
         * The Test Case name.
         */
        protected String testCaseName;

        /**
         * HTTP Server source directory (directory where the files exist).
         * The value is specified by ComputeDigest2Urls.SrcDir test property.
         */
        protected String srcDir;

        /**
         * HTTP Server port number.
         * The value is specified by ComputeDigest2Urls.Port test property.
         */
        protected int port;

        /**
         * Protocol of URL1.
         * The value is specified by &lt;TestCaseName&gt;.Protocol1 test
         * property.
         */
        protected String proto1;

        /**
         * Protocol of URL2.
         * The value is specified by &lt;TestCaseName&gt;.Protocol2 test
         * property.
         */
        protected String proto2;

        /**
         * Filename of URL1.
         * The value is specified by &lt;TestCaseName&gt;.FileName1 test
         * property.
         */
        protected String fname1;

        /**
         * Filename of URL2.
         * The value is specified by &lt;TestCaseName&gt;.FileName2 test
         * property.
         */
        protected String fname2;

        /**
         * Message Digest Algorithm.
         * The value is specified by &lt;TestCaseName&gt;.Algorithm test
         * property.
         */
        protected String algorithm;

        /**
         * Expected result.
         * The value is specified by &lt;TestCaseName&gt;.Expected test
         * property.
         */
        protected String expected;

        /**
         * URL objects.
         */
        protected URL url1, url2;

        /**
         * Creating TestItem object (Constructor)
         * Test parameters:
         *    ComputeDigest2Urls.testCases   - Test Cases names
         *    ComputeDigest2Urls.Port        - HTTP Server port number
         *    ComputeDigest2Urls.SrcDir      - HTTP Server source directory
         *                                    (directory where the files exist)
         *    &lt;TestCaseName&gt;.Protocol1 - Protocol of URL1
         *    &lt;TestCaseName&gt;.Protocol2 - Protocol of URL2
         *    &lt;TestCaseName&gt;.FileName1 - Filename of URL1
         *    &lt;TestCaseName&gt;.FileName2 - Filename of URL2
         *    &lt;TestCaseName&gt;.Algorithm - Message Digest Algorithm
         *    &lt;TestCaseName&gt;.Expected  - Expected result
         *
         * @param tcname Test Case name
         * @throws Exception if any exception occured while TestItem object
         *                   creation
         */
        public TestItem(String tcname) throws Exception {
            this(tcname, config.getStringConfigVal(tcname + ".Protocol1", null),
                    config.getStringConfigVal(tcname + ".Protocol2", null),
                    config.getIntConfigVal("ComputeDigest2Urls.Port", 0),
                    config.getStringConfigVal("ComputeDigest2Urls.SrcDir",
                    null), config.getStringConfigVal(tcname + ".FileName1",
                    null), config.getStringConfigVal(tcname + ".FileName2",
                    null), config.getStringConfigVal(tcname + ".Algorithm",
                    "MD5"), config.getStringConfigVal(tcname + ".Expected",
                    "equal"));
        }

        /**
         * Creating TestItem object (Constructor)
         *
         * @param tcname  Test Case name
         * @param pr1     protocol of 1-st URL object
         * @param pr2     protocol of 2-nd URL object
         * @param port    port of the URL objects
         * @param src     source directory of the URL objects
         * @param fn1     filename of 1-st URL object
         * @param fn2     filename of 2-nd URL object
         * @param alg     message digest algorithm
         * @param exp     expected result ("equal" or "non-equal")
         * @throws Exception if any exception occured while TestItem object
         *                   creation
         */
        public TestItem(String tcname, String pr1, String pr2, int port,
                String src, String fn1, String fn2, String alg, String exp)
                throws Exception {
            testCaseName = tcname;
            proto1 = pr1;
            proto2 = pr2;
            this.port = port;
            srcDir = src;
            fname1 = fn1;
            fname2 = fn2;
            algorithm = alg;
            expected = exp;

            /* Creating URL1 */
            if (proto1.equals("file")) {
                url1 = new URL("file:" + srcDir + "/" + fname1);
            } else {
                url1 = new URL(proto1, "localhost", port, "/" + fname1);
            }

            /* Creating URL2 */
            if (proto2.equals("file")) {
                url2 = new URL("file:" + srcDir + "/" + fname2);
            } else {
                url2 = new URL(proto2, "localhost", port, "/" + fname2);
            }
        }

        /**
         * Getting Test Case name of this TestItem object.
         *
         * @return Test Case name of this TestItem object
         */
        public String getTestCaseName() {
            return testCaseName;
        }

        /**
         * This method returns message digest algorithm.
         *
         * @return the message digest algorithm
         */
        public String getAlg() {
            return algorithm;
        }

        /**
         * This method returns expected result.
         *
         * @return the expected result
         */
        public String getExpected() {
            return expected;
        }

        /**
         * This method returns the 1-st URL object.
         *
         * @return the 1-st URL object
         */
        public URL getURL1() {
            return url1;
        }

        /**
         * This method returns the 2-nd URL object.
         *
         * @return the 2-nd URL object
         */
        public URL getURL2() {
            return url2;
        }
    }
}
