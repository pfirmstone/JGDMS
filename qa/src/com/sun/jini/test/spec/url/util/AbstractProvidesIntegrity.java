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
package com.sun.jini.test.spec.url.util;

import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// java.net
import java.net.URL;


/**
 * <pre>
 * This is an abstract class that is extended by the following tests:
 *   - {@link com.sun.jini.test.spec.url.file.integrity.ProvidesIntegrity url.file.integrity.ProvidesIntegrity}
 *   - {@link com.sun.jini.test.spec.url.httpmd.integrity.ProvidesIntegrity url.httpmd.integrity.ProvidesIntegrity}
 *   - {@link com.sun.jini.test.spec.url.https.integrity.ProvidesIntegrity url.https.integrity.ProvidesIntegrity}
 * </pre>
 */

public abstract class AbstractProvidesIntegrity extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * All Test Cases (each element describes a Test Case).
     */
    protected Vector items = new Vector();

    /**
     * This method performs all preparations.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();
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
                // commented due to 'fast-fail' approach
                // returnedVal = retVal;
                break;
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
     * @param ti {@link AbstractProvidesIntegrity.TestItem}
     *           object that descibes a Test Case
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
     *
     * @param ti {@link AbstractProvidesIntegrity.TestItem}
     *           object that descibes a Test Case
     * @return true (if the returned result is equal to
     *         the expected one) or false otherwise
     */
    public abstract boolean checker(TestItem ti);


    /**
     * Auxiliary class that describes a Test Case.
     */
    protected class TestItem {

        /**
         * The Test Case name.
         */
        protected String testCaseName;

        /** {@link java.net.URL URL} object */
        protected URL testURL;

        /**
         * Expected boolean value.
         */
        protected boolean expectedBoolean;

        /**
         * Expected Exception (Class object).
         */
        protected Class expectedException;

        /**
         * Creating {@link AbstractProvidesIntegrity.TestItem}
         * object (Constructor).
         *
         * @param tcname Test Case name
         * @param url    {@link java.net.URL URL} object
         * @param exp    expected result
         * @throws Exception if any exception occured while
         *                   {@link AbstractProvidesIntegrity.TestItem}
         *                   object creation
         */
        public TestItem(String tcname, String url, String exp)
                throws Exception {
            testCaseName = tcname;

            if (url == null) {
                testURL = null;
            } else {
                testURL = new URL(url);
            }

            if (exp.endsWith(".class")) {
                expectedException = Class.forName(exp.substring(0,
                        exp.lastIndexOf(".class")));
            } else {
                if (exp.compareTo("true") == 0) {
                    expectedBoolean = true;
                } else if (exp.compareTo("false") == 0) {
                    expectedBoolean = false;
                } else {
                    throw new Exception(exp
                            + ": Bad value for expected result");
                }
            }
        }

        /**
         * Creating {@link AbstractProvidesIntegrity.TestItem}
         * object (Constructor).
         *
         * @param tcname Test Case name
         * @param proto  protocol of {@link java.net.URL URL} object
         * @param host   host of {@link java.net.URL URL} object
         * @param file   file of {@link java.net.URL URL} object
         * @param exp    expected result
         * @throws Exception if any exception occured while
         *                   {@link AbstractProvidesIntegrity.TestItem}
         *                   object creation
         */
        public TestItem(String tcname, String proto, String host, String file,
                String exp) throws Exception {
            testCaseName = tcname;

            if (proto == null) {
                testURL = null;
            } else {
                testURL = new URL(proto, host, file);
            }

            if (exp.endsWith(".class")) {
                expectedException = Class.forName(exp.substring(0,
                        exp.lastIndexOf(".class")));
            } else {
                if (exp.compareTo("true") == 0) {
                    expectedBoolean = true;
                } else if (exp.compareTo("false") == 0) {
                    expectedBoolean = false;
                } else {
                    throw new Exception(exp
                            + ": Bad value for expected result");
                }
            }
        }

        /**
         * Getting Test Case name of this
         * {@link AbstractProvidesIntegrity.TestItem}
         * object.
         *
         * @return Test Case name of this
         *         {@link AbstractProvidesIntegrity.TestItem}
         *         object
         */
        public String getTestCaseName() {
            return testCaseName;
        }

        /**
         * This method returns {@link java.net.URL URL} object to be tested.
         *
         * @return url
         */
        public URL getURL() {
            return testURL;
        }

        /**
         * Comparing 2 boolean values.
         *
         * @param test the result returned by method to be verified
         * @return result of comparison (true or false)
         */
        public boolean compare(boolean test) {
            logger.log(Level.FINE, "Expected Result: " + expectedBoolean);
            logger.log(Level.FINE, "Returned Result: " + test);

            if (test == expectedBoolean) {
                return true;
            }
            return false;
        }

        /**
         * Comparing 2 Exceptions.
         *
         * @param test the Exception occurred while invoking method to be
         *             verified
         * @return result of comparison (true or false)
         */
        public boolean compare(Exception test) {
            logger.log(Level.FINE,
                    "Expected Result: " + expectedException.getName());
            logger.log(Level.FINE, "Returned Result: " + test);
            return expectedException.isInstance(test);
        }
    }
}
