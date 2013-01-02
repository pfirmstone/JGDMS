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
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;
import java.net.NetPermission;
import java.net.MalformedURLException;

// TestHandler extends url.httpmd.Handler class
import com.sun.jini.test.spec.url.httpmd.util.TestHandler;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of {@link Handler#hashCode(URL)} method.
 *   The method computes a hash code for the specified HTTPMD URL object.
 *   {@link Handler#hashCode(URL)} method should ignore:
 *      - the comment portion of the message digest parameter
 *      - the case of characters in the message digest
 *      - the case of characters in the algorithm
 *
 * Test Cases:
 *   This test tries to compute hash codes for different HTTPMD URL objects.
 *   The cases:
 *     - hashCodeSameURL
 *       it's verified that the 2 hash codes computed with
 *       {@link Handler#hashCode(URL)} method for the same HTTPMD URL object
 *       are equal;
 *     - hashCodeComments
 *       it's verified that the hash code for HTTPMD URL object with comment
 *       portion of the message digest parameter is equal to the hash code for
 *       HTTPMD URL object without comment portion;
 *     - hashCodeCapitalLetters
 *       it's verified that the hash code for HTTPMD URL object with uppercase
 *       characters in the message digest is equal to the hash code for HTTPMD
 *       URL object with lowercase characters in the message digest;
 *     - hashCodeAlgorithm
 *       it's verified that the hash code for HTTPMD URL object with uppercase
 *       characters in the algorithm is equal to the hash code for HTTPMD URL
 *       object with lowercase characters in the algorithm;
 *
 * Infrastructure:
 *     - TestHandler
 *         extends {@link Handler} class
 *         including {@link Handler#hashCode(URL)} method
 *     - HashCode.TestItem
 *         auxiliary class that describes a Test Case
 *     - HashCode
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD URL objects creating
 *       ({@link Handler} is used as HTTPMD Protocol handler).
 *   Test performs the following steps:
 *     - creating TestHandler object
 *   Test performs the following steps in each Test Case:
 *     - creating 2 URL objects from the String representation,
 *     - comparing hash codes for these 2 URL objects.
 *
 * </pre>
 */
public class HashCode extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * All Test Cases (each element describes a Test Case).
     */
    protected Vector items = new Vector();

    /** TestHandler object */
    protected TestHandler handler;

    /**
     * Getting Test Class name.
     *
     * @return Test Class name
     */
    public String getTestClassName() {
        return this.getClass().getName();
    }

    /**
     * Getting Test Case names.
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
     *  - getting test parameters,
     *  - creating TestHandler object,
     *  - creating TestItem object for each Test Case name.
     * Test parameters:
     *    HashCode.testCases        - Test Cases names
     *    &lt;TestCaseName&gt;.Url1 - String representation of 1-st HTTPMD URL object
     *    &lt;TestCaseName&gt;.Url2 - String representation of 2-nd HTTPMD URL object
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /*
         * Getting Test Cases names and creating the corresponding
         * TestItem objects.
         */
        String[] tc_names = getTestCaseNames();

        for (int i = 0; i < tc_names.length; i++) {
            items.add(i, new TestItem(tc_names[i]));
        }

        /* Creating TestHandler object */
        handler = new TestHandler();
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

        /* URL objects to check */
        URL u1 = ti.getURL1();
        URL u2 = ti.getURL2();

        /* Comparison */
        boolean ret = hashCodeCompare(u1, u2);

        if (ret != true) {
            logger.log(Level.FINE,
                    t_name + " test case failed: hash codes aren't equal");
            return false;
        }
        logger.log(Level.FINE, t_name + " test case passed");
        return true;
    }

    /**
     * Comparing hash codes for the specified URL objects.
     *
     * @param u1  HTTPMD URL object for comparison
     * @param u2  HTTPMD URL object for comparison
     * @return result of comparison (true (if hash codes are equal) or false)
     */
    public boolean hashCodeCompare(URL u1, URL u2) {

        /* Calculating hash codes for the specified URL objects */
        int hashcode1 = handler.hashCode(u1);
        logger.log(Level.FINE, "URL1: " + u1);
        logger.log(Level.FINE, "\thashcode1: " + hashcode1);
        int hashcode2 = handler.hashCode(u2);
        logger.log(Level.FINE, "URL2: " + u2);
        logger.log(Level.FINE, "\thashcode2: " + hashcode2);

        /* Comparing hash codes for the specified URL objects */
        if (hashcode1 != hashcode2) {
            return false;
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
         * 1-st HTTPMD URL object.
         * The value is specified by &lt;TestCaseName&gt;.Url1 test property.
         */
        protected URL url1;

        /**
         * 2-nd HTTPMD URL object.
         * The value is specified by &lt;TestCaseName&gt;.Url2 test property.
         */
        protected URL url2;

        /**
         * Creating TestItem object (Constructor)
         *
         * @param tcname Test Case name
         * @throws MalformedURLException if URL object can't be created from
         *                               the String representation
         */
        public TestItem(String tcname) throws MalformedURLException {
            this(tcname, config.getStringConfigVal(tcname + ".Url1", null),
                    config.getStringConfigVal(tcname + ".Url2", null));
        }

        /**
         * Creating TestItem object (Constructor)
         *
         * @param tcname Test Case name
         * @param u1     1-st HTTPMD URL object as a String
         * @param u2     2-nd HTTPMD URL object as a String
         * @throws MalformedURLException if URL object can't be created from
         *                               the String representation
         */
        public TestItem(String tcname, String u1, String u2)
                throws MalformedURLException {
            testCaseName = tcname;

            /* Creating 2 URL objects from the String representations */
            url1 = new URL(u1);
            url2 = new URL(u2);
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
         * Getting 1-st HTTPMD URL object of this TestItem object.
         *
         * @return 1-st HTTPMD URL object of this TestItem object
         */
        public URL getURL1() {
            return url1;
        }

        /**
         * Getting 2-nd HTTPMD URL object of this TestItem object.
         *
         * @return 2-nd HTTPMD URL object of this TestItem object
         */
        public URL getURL2() {
            return url2;
        }
    }
}
