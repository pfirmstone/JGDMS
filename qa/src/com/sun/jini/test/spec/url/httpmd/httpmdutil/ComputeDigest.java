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
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// davis packages
import net.jini.url.httpmd.HttpmdUtil;
import net.jini.url.httpmd.Handler;

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
 *   This test verifies the behavior of the
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
 *   URL objects represent various protocols: file, http, httpmd.
 *   The cases:
 *     - the message digest is calculated for non-empty text file and compared
 *       with the message digest calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)}
 *       method; various protocols (file, http, httpmd) and various
 *       algorithms (MD5, SHA) are used; it's expected that these message
 *       digests are equal.
 *          computeDigestNonEmptyMD5file
 *          computeDigestNonEmptyMD5http
 *          computeDigestNonEmptyMD5httpmd
 *          computeDigestNonEmptySHAfile
 *          computeDigestNonEmptySHAhttp
 *          computeDigestNonEmptySHAhttpmd
 *     - the message digest is calculated for non-empty .jar file and compared
 *       with the message digest calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)}
 *       method; various protocols (file, http, httpmd) and various
 *       algorithms (MD5, SHA) are used; it's expected that these message
 *       digests are equal.
 *          computeDigestJarMD5file
 *          computeDigestJarMD5http
 *          computeDigestJarMD5httpmd
 *          computeDigestJarSHAfile
 *          computeDigestJarSHAhttp
 *          computeDigestJarSHAhttpmd
 *     - the message digest is calculated for an empty file and compared with
 *       the message digest calculated with
 *       {@link HttpmdUtil#computeDigest(URL,String)}
 *       method; various protocols (file, http, httpmd) and various
 *       algorithms (MD5, SHA) are used; it's expected that these message
 *       digests are equal.
 *          computeDigestEmptyMD5file
 *          computeDigestEmptyMD5http
 *          computeDigestEmptyMD5httpmd
 *          computeDigestEmptySHAfile
 *          computeDigestEmptySHAhttp
 *          computeDigestEmptySHAhttpmd
 *     - URL is incorrect (URL represents non-existent file)
 *       IOException is expected.
 *          computeDigestNonExistMD5file
 *          computeDigestNonExistMD5http
 *          computeDigestNonExistMD5httpmd
 *     - URL is incorrect (URL with a syntax error: Message Digest is empty)
 *       IOException is expected.
 *          computeDigestBadUrl
 *     - URL is incorrect (URL with a syntax error: Message Digest Algorithm
 *       in URL is invalid)
 *       IOException is expected.
 *          computeDigestBadAlgorithmInUrl
 *     - algorithm is incorrect ("BADALG");various protocols
 *       (file, http, httpmd) are used
 *       NoSuchAlgorithmException is expected.
 *          computeDigestBadAlgorithmfile
 *          computeDigestBadAlgorithmhttp
 *          computeDigestBadAlgorithmhttpmd
 *     - one or both arguments are null
 *       NullPointerException is expected.
 *          computeDigestNullAlgorithm
 *          computeDigestNullUrl
 *          computeDigestNull
 *
 * Infrastructure:
 *     - ComputeDigest.TestItem
 *         auxiliary class that describes a Test Case
 *     - ComputeDigest
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD urls creating
 *       ({@link Handler} is used as HTTPMD Protocol handler),
 *     - launching HTTP Server.
 *   Test performs the following steps:
 *     - creating an empty and non-empty text files
 *     - in each Test Case the test does the following:
 *       1) constructing a TestItem object,
 *       2) calculating a message digest with
 *          {@link HttpmdUtil#computeDigest(URL,String)}
 *          method for the specified URL object,
 *       3) comparing
 *          {@link HttpmdUtil#computeDigest(URL,String)}
 *          result (the calculated message digest or exception)
 *          with expected results from the TestItem object.
 *
 * </pre>
 */
public class ComputeDigest extends QATest {
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
     * @return Test Cases names
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
     * - creating an empty and non-empty text files,
     * - getting test parameters.
     * Test parameters:
     *    ComputeDigest.testCases             - Test Cases names
     *    &lt;TestCaseName&gt;.Protocol       - protocol of URL object
     *    &lt;TestCaseName&gt;.FileName       - filename of URL object
     *    &lt;TestCaseName&gt;.SrcDir         - directory where the file
     *                                          specified by the URL
     *                                          object exist
     *    &lt;TestCaseName&gt;.Port           - port number of URL object
     *    &lt;TestCaseName&gt;.Algorithm      - message digest algorithm
     *    &lt;TestCaseName&gt;.Expected       - expected result
     *    testClassServer.dir                   - HTTP Server source directory
     *    testClassServer.port                  - HTTP Server port number
     *    ComputeDigest.SecondHTTPServer.port - 2-nd HTTP Server port number
     *    ComputeDigest.SecondHTTPServer.dir  - 2-nd HTTP Server source
     *                                          directory
     * </pre>
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Creating an empty file (will be deleted when VM exits) */
        File empty_file = new File(System.getProperty("java.io.tmpdir")
                + System.getProperty("file.separator") + "empty_file");
        empty_file.createNewFile();
        empty_file.deleteOnExit();

        /* Creating a non-empty file (will be deleted when VM exits) */
        File nonempty_file = new File(System.getProperty("java.io.tmpdir")
                + System.getProperty("file.separator") + "nonempty_file");
        FileOutputStream fin = new FileOutputStream(nonempty_file);
        byte[] nonempty_filecontents = "xxx".getBytes();

        try {
            fin.write(nonempty_filecontents);
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception while writing to file: " + e);
            throw e;
        }
        fin.close();
        nonempty_file.deleteOnExit();

        /* Launching 2-nd HTTP Server */
        manager.startService("ComputeDigest.SecondHTTPServer");

        /* Getting test parameters and creating TestItem objects */
        String[] tc_names = getTestCaseNames();

        for (int i = 0; i < tc_names.length; i++) {
            items.add(i, new TestItem(tc_names[i]));
        }
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
     * {@link HttpmdUtil#computeDigest(URL,String)}, and
     * comparing the computed Message Digest (or thrown Exception) with
     * the expected result.
     *
     * @param ti TestItem object that descibes a Test Case
     * @return true (if the computed Message Digest (or Exception) is equal to
     *         the expected one) or false otherwise
     */
    public boolean checker(TestItem ti) {
        String expectedRes = ti.getExpRes();

        try {
            logger.log(Level.FINE,
                    "HttpmdUtil.computeDigest(" + ti.getURL() + ", "
                    + ti.getalg() + ")");
            String realRes = HttpmdUtil.computeDigest(ti.getURL(), ti.getalg());

            /* Comparing 2 String objects - Message Digest values */
            return ti.compare(realRes);
        } catch (Exception e) {

            /* Comparing 2 Exception objects */
            return ti.compare(e);
        }
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
         * Protocol of URL object.
         * The value is specified by &lt;TestCaseName&gt;.Protocol test
         * property.
         */
        protected String proto;

        /**
         * Source directory of URL object (directory where the file specified
         * by the URL object exist).
         * The value is specified by &lt;TestCaseName&gt;.SrcDir test property.
         */
        protected String src;

        /**
         * Filename of URL object.
         * The value is specified by &lt;TestCaseName&gt;.FileName test
         * property.
         */
        protected String fname;

        /**
         * Port of URL object.
         * The value is specified by &lt;TestCaseName&gt;.Port test property.
         */
        protected int port;

        /**
         * Message Digest Algorithm.
         * The value is specified by &lt;TestCaseName&gt;.Algorithm test
         * property.
         */
        protected String algorithm;

        /**
         * Expected result (Message Digest as a String in hexadecimal format
         * or Exception).
         * The value is specified by &lt;TestCaseName&gt;.Expected test
         * property.
         */
        protected String expected;

        /** Message Digest as a String in hexadecimal format */
        protected String mesDigest;

        /** URL object */
        protected URL testURL;

        /**
         * Creating TestItem object (Constructor).
         * Test parameters:
         *    ComputeDigest.testCases             - Test Cases names
         *    &lt;TestCaseName&gt;.Protocol       - protocol of URL object
         *    &lt;TestCaseName&gt;.FileName       - filename of URL object
         *    &lt;TestCaseName&gt;.SrcDir         - directory where the file
         *                                          specified by the URL
         *                                          object exist
         *    &lt;TestCaseName&gt;.Port           - port number of URL object
         *    &lt;TestCaseName&gt;.Algorithm      - message digest algorithm
         *    &lt;TestCaseName&gt;.Expected       - expected result
         *    testClassServer.dir                   - HTTP Server source directory
         *    testClassServer.port                  - HTTP Server port number
         *    ComputeDigest.SecondHTTPServer.port - 2-nd HTTP Server port number
         *    ComputeDigest.SecondHTTPServer.dir  - 2-nd HTTP Server source
         *                                          directory
         *
         * @param tcname Test Case name
         * @throws Exception if any exception occured while TestItem object
         *                   creation
         */
        public TestItem(String tcname) throws Exception {
            this(tcname, config.getStringConfigVal(tcname + ".Protocol", null),
                    config.getIntConfigVal(tcname + ".Port", 0),
                    config.getStringConfigVal(tcname + ".SrcDir", null),
                    config.getStringConfigVal(tcname + ".FileName", null),
                    config.getStringConfigVal(tcname + ".Algorithm", null),
                    config.getStringConfigVal(tcname + ".Expected", null));
        }

        /**
         * Creating TestItem object (Constructor).
         *
         * @param tcname Test Case name
         * @param proto  protocol
         * @param port   port
         * @param src    source directory
         * @param fname  filename
         * @param alg    message digest algorithm
         * @param exp    expected result (Exception or null)
         * @throws Exception if any exception occured while TestItem object
         *                   creation
         */
        public TestItem(String tcname, String proto, int port, String src,
                String fname, String alg, String exp) throws Exception {
            this.testCaseName = tcname;
            this.proto = proto;
            this.port = port;
            this.src = src;
            this.fname = fname;
            this.algorithm = alg;
            this.expected = exp;

            /* For testing null URL, Exception is expected */
            if (fname == null) {
                testURL = null;
                return;
            }

            /*
             * Creating URL object and computing message digest for the file
             * (if needed)
             */
            createURL();

            /* If correct message digest is expected (no Exception) */
            if (exp == null) {
                expected = mesDigest;
            }
        }

        /**
         * Creating URL object with computed message digest for the file
         * specified by filename portion of URL object (if needed).
         *
         * @throws Exception if it's impossible to create URL object
         */
        protected void createURL() throws Exception {

            /* Computing message digest for the file */
            mesDigest = computeMessageDigest(src, fname, algorithm);

            /* Creating URL object */
            if (proto.equals("file")) {
                testURL = new URL("file:" + src + "/" + fname);
            } else {
                testURL = new URL(proto, "localhost", port, "/" + fname);
            }

            if (!proto.equals("httpmd")) {
                return;
            }

            /* Correcting HTTPMD URL object (Message Digest value) */
            String fnorg = testURL.getFile();
            int indSemicolon = fnorg.indexOf(";");
            int indEqual = fnorg.indexOf("=", indSemicolon);
            int indComma = fnorg.indexOf(",", indEqual);
            String algorithm = fnorg.substring(indSemicolon + 1, indEqual);
            String mdinUrl = fnorg.substring(indEqual + 1, indComma);

            /*
             * If message digest is empty in the original URL object, then
             * message digest isn't corrected and Exception is expected.
             */
            String mdvalue;

            if (mdinUrl.equals("")) {
                mdvalue = mdinUrl;
            } else {
                mdvalue = mesDigest;
            }
            StringBuffer fnameStr = new StringBuffer(fnorg.substring(0,
                    indSemicolon) + ";" + algorithm + "=" + mdvalue);

            /* If comma sign exists in the url (comments exist) */
            if (indComma != -1) {
                fnameStr.append(fnorg.substring(indComma));
            }

            /* Correcting message digest in the HTTPMD URL object */
            testURL = new URL(testURL.getProtocol(), testURL.getHost(),
                    testURL.getPort(), fnameStr.toString());
            return;
        }

        /**
         * Computing message digest for the file.
         *
         * @param  src       source directory
         * @param  fn        file name
         * @param  alg       message digest algorithm
         * @throws Exception if the message digest for the specified file
         *                   can't be computed
         * @return message digest in hexadecimal format or null if file
         *         doesn't exist
         */
        protected String computeMessageDigest(String src, String fn, String alg)
                throws Exception {
            final int BUFSIZE = 2048;
            final String ALG = "MD5";
            MessageDigest md;

            /* Portioning filename - removing message digest (if exist) */
            int indSemicolon = fn.indexOf(";");
            StringBuffer fname = new StringBuffer(src
                    + System.getProperty("file.separator"));

            if (indSemicolon != -1) {

                /* There is message digest in the filename */
                fname.append(fn.substring(0, indSemicolon));
            } else {
                fname.append(fn);
            }

            /* Checking existence of the file */
            File fileObj = new File(fname.toString());

            if (!(fileObj.exists())) {
                return null;
            }

            /* Computing message digest for the file */
            try {
                md = MessageDigest.getInstance(alg);
            } catch (Exception e) {
                md = MessageDigest.getInstance(ALG);
            }
            FileInputStream fin = new FileInputStream(fileObj);
            BufferedInputStream bin = new BufferedInputStream(fin, BUFSIZE);
            DigestInputStream in = new DigestInputStream(bin, md);
            in.on(true);
            byte[] buf = new byte[BUFSIZE];
            byte[] mdbyte = new byte[BUFSIZE];

            while (true) {
                int n = in.read(buf, 0, buf.length);

                if (n < 0) {
                    break;
                }
            }
            md = in.getMessageDigest();
            mdbyte = md.digest();
            return digestString(mdbyte);
        }

        /**
         * Converts a message digest to a String in hexadecimal format.
         *
         * @param digest message digest
         * @return message digest in hexadecimal format
         */
        protected String digestString(byte[] digest) {
            StringBuffer sb = new StringBuffer(digest.length * 2);

            for (int i = 0; i < digest.length; i++) {
                byte b = digest[i];
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
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
         * This method returns URL object to be tested.
         *
         * @return url
         */
        public URL getURL() {
            return testURL;
        }

        /**
         * This method returns message digest algorithm.
         *
         * @return the message digest algorithm
         */
        public String getalg() {
            return algorithm;
        }

        /**
         * This method returns expected result.
         *
         * @return the expected result
         */
        public String getExpRes() {
            return expected;
        }

        /**
         * Comparing 2 Message Digests.
         * Comparing the Message Digest computed with
         * {@link HttpmdUtil#computeDigest(URL,String)}
         * method with the expected one.
         *
         * @param test the Message Digest value computed with
         *             {@link HttpmdUtil#computeDigest(URL,String)}
         *             method
         * @return result of comparison (true or false)
         */
        public boolean compare(String test) {
            logger.log(Level.FINE, "Expected Result: " + expected);
            logger.log(Level.FINE, "Returned Result: " + test);

            if (expected.equals(test)) {
                return true;
            }
            return false;
        }

        /**
         * Comparing 2 Exceptions.
         * Comparing the Exception occurred while computing Message Digest with
         * {@link HttpmdUtil#computeDigest(URL,String)} method
         * with the expected one.
         *
         * @param test the Exception occurred while computing Message Digest with
         *             {@link HttpmdUtil#computeDigest(URL,String)}
         *             method
         * @return result of comparison (true or false)
         */
        public boolean compare(Exception test) {
            Class expectedClass, exceptionClass;
            boolean res;
            logger.log(Level.FINE, "Expected Result: " + expected);
            logger.log(Level.FINE, "Returned Result: " + test);

            try {
                exceptionClass = test.getClass();
                expectedClass = Class.forName(expected);
                res = expectedClass.isAssignableFrom(exceptionClass);
            } catch (Exception e) {
                logger.log(Level.FINE, "Exception during comparison: " + e);
                return false;
            }

            if (res) {
                return true;
            }
            return false;
        }
    }
}
