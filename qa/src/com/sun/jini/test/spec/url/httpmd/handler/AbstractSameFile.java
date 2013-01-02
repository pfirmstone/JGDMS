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
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// davis packages
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;
import java.net.MalformedURLException;


/**
 * This is an abstract class that is extended by
 * {@link Equals} and {@link SameFile} tests.
 */
public abstract class AbstractSameFile extends QATestEnvironment implements Test {
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
     * Comparing two URL objects.
     *
     * @param u1 URL object to be compared
     * @param u2 URL object to be compared
     * @return result of comparison (true or false)
     */
    public abstract boolean compare(URL u1, URL u2);

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     *  - getting Test Cases names (specified by
     *    &lt;TestName&gt;.testCases test property),
     *  - creating TestItem object for each Test Case name.
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
        return this;
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
     * @param ti TestItem object that descibes the Test Case
     * @return result of the Test Case (true (if the returned
     *         value is equal to the expected one) or false)
     */
    public boolean testCase(TestItem ti) {

        /* Test Case name */
        String t_name = ti.getTestCaseName();
        logger.log(Level.FINE, "\n=============== Test Case name: " + t_name);

        /* URL objects to check */
        URL u1 = ti.getHttpmdURL();
        URL u2 = ti.getAnotherURL();

        /* Comparison */
        boolean ret = compare(u1, u2);

        /* Expected result */
        boolean exp = ti.getExpResult();
        logger.log(Level.FINE, "Expected result: " + exp);
        logger.log(Level.FINE, "Returned result: " + ret);

        if (ret != exp) {
            logger.log(Level.FINE,
                    t_name + " test case failed:" + "\n Expected result: " + exp
                    + "\n Returned result: " + ret);
            return false;
        }
        logger.log(Level.FINE, t_name + " test case passed");
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
         * HTTPMD URL object.
         * The value is specified by &lt;TestCaseName&gt;.httpmdURL test
         * property.
         */
        protected URL httpmdURL;

        /**
         * Another URL object to be compared with HTTPMD URL.
         * The value is specified by &lt;TestCaseName&gt;.anotherURL test
         * property.
         */
        protected URL anotherURL;

        /**
         * Expected result of URL objects comparison (true or false).
         * The value is specified by &lt;TestCaseName&gt;.expResult test
         * property.
         */
        protected boolean expResult;

        /**
         * Creating TestItem object (Constructor)
         *
         * @param tcname Test Case name
         * @throws MalformedURLException if URL object can't be created
         */
        public TestItem(String tcname) throws MalformedURLException {
            this(tcname, config.getStringConfigVal(tcname + ".httpmdURL", null),
                    config.getStringConfigVal(tcname + ".anotherURL", null),
                    config.getBooleanConfigVal(tcname + ".expResult", true));
        }

        /**
         * Creating TestItem object (Constructor)
         *
         * @param tcname Test Case name
         * @param url1   HTTPMD URL as a String
         * @param url2   Another URL as a String to be compared with HTTPMD URL
         * @param exp    Expected result of URL objects comparison
         * @throws MalformedURLException if URL object can't be created
         */
        public TestItem(String tcname, String url1, String url2, boolean exp)
                throws MalformedURLException {
            testCaseName = tcname;
            expResult = exp;

            /* Creating 2 URL objects from the String representations */
            httpmdURL = new URL(replacePound(url1));
            anotherURL = new URL(replacePound(url2));
        }

        /**
         * Replacing &lt;PoundSign&gt; with # sign.
         *
         * @param from String object
         * @return String object with &lt;PoundSign&gt; replaced with # sign
         */
        protected String replacePound(String from) {
            final String at = "<PoundSign>";
            final Character pound = new Character('#');

            if (from == null) {
                return from;
            }

            if (!(from.matches(".*" + at + ".*"))) {
                return from;
            }
            return from.replaceAll(at, pound.toString());
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
         * Getting HTTPMD URL object of this TestItem object.
         *
         * @return HTTPMD URL object of this TestItem object
         */
        public URL getHttpmdURL() {
            return httpmdURL;
        }

        /**
         * Getting URL object to be compared with HTTPMD URL object
         * of this TestItem object.
         *
         * @return URL object to be compared with HTTPMD URL object
         *         of this TestItem object
         */
        public URL getAnotherURL() {
            return anotherURL;
        }

        /**
         * Getting expected result of this TestItem object.
         *
         * @return expected result of this TestItem object
         */
        public boolean getExpResult() {
            return expResult;
        }
    }
}
