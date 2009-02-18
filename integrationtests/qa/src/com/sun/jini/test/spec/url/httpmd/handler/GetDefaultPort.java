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
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Hashtable;

// davis packages
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of {@link Handler#getDefaultPort()} method.
 *   {@link Handler#getDefaultPort()} method should return the default port for
 *   a HTTPMD URL parsed by {@link Handler} protocol handler.
 *   Default port for a HTTPMD URL parsed by {@link Handler} protocol handler
 *   should be 80.
 *
 * Test Cases:
 *     - getDefaultPort
 *       - create HTTPMD URL object with
 *           u = new URL(protocol, host, port, file) constructor
 *         where:
 *           protocol = "httpmd",
 *           host = "localhost",
 *           port = -1,
 *           file = "/file.jar;MD5=abcdefABCDEF0123456789" +
 *                          ",abcdefghijklmnopqrstuvwxyz" +
 *                          "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
 *                          "0123456789" +
 *                          "-_.~*'():@&=+$";
 *       - get the default port number of the protocol associated with this
 *         HTTPMD URL object using {@link URL#getDefaultPort()} (which
 *         invokes {@link Handler#getDefaultPort()});
 *       - check that URL#getDefaultPort() returns 80;
 *     - getDefaultPortAnotherPort
 *       - create HTTPMD URL object with
 *           u = new URL(protocol, host, port, file) constructor
 *         where:
 *           protocol = "httpmd",
 *           host = "localhost",
 *           port = port number,
 *           file = "/file.jar;MD5=abcdefABCDEF0123456789" +
 *                         ",abcdefghijklmnopqrstuvwxyz" +
 *	                   "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
 *                         "0123456789" +
 *	                   "-_.~*'():@&=+$";
 *       - get the default port number of the protocol associated with this
 *         HTTPMD URL object using {@link URL#getDefaultPort()} (which
 *         invokes {@link Handler#getDefaultPort()});
 *       - check that URL#getDefaultPort() returns 80;
 *
 * Infrastructure:
 *     - GetDefaultPort
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD urls creating
 *       ({@link Handler} is used as HTTPMD Protocol handler).
 *   Test performs the following steps:
 *     - implementing test cases.
 *
 * </pre>
 */
public class GetDefaultPort extends QATest {
    QAConfig config;

    /** HTTPMD URL object */
    protected URL httpmdURL;

    /**
     * Test Case Names.
     * The value is specified by GetDefaultPort.testCases test property.
     */
    protected String[] testCases;

    /**
     * Port number of HTTPMD URL object.
     * The value is specified by &lt;TestCaseName&gt;.Port test property.
     */
    protected Hashtable ports = new Hashtable();

    /** Expected result (default port number) */
    final protected int defPort = 80;

    /**
     * <pre>
     * This method performs all preparations.
     * Test parameters:
     *    GetDefaultPort.testCases  - Test Case Names
     *    &lt;TestCaseName&gt;.Port - port number of HTTPMD URL object
     * </pre>
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Getting test parameters */
        testCases = config.getStringConfigVal("GetDefaultPort.testCases",
                null).split(" ");

        for (int i = 0; i < testCases.length; i++) {
            ports.put(testCases[i],
                    new Integer(config.getIntConfigVal(testCases[i] + ".Port",
                    - 1)));
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        boolean returnedVal = true;

        for (int i = 0; i < testCases.length; i++) {
            boolean retVal = testCase(testCases[i]);

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
     * <pre>
     * Test Case actions.
     * Test Case actions:
     *   - creating HTTPMD URL object with the specified port number,
     *   - invoking getAndCompare() method
     * </pre>
     * @param tc_name Test Case Name
     * @return status of comparison (true (if the default port number is
     *         equal to the expected one) or false).
     */
    public boolean testCase(String tc_name) {
        logger.log(Level.FINE, "\n=============== Test Case name: " + tc_name);

        /* Protocol of HTTPMD URL object */
        final String protocol = "httpmd";

        /* Hostname of HTTPMD URL object */
        final String host = "localhost";

        /* Filename of HTTPMD URL object */
        final String file = "/file.jar;MD5=abcdefABCDEF0123456789"
                + ",abcdefghijklmnopqrstuvwxyz" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789" + "-_.~*'():@&=+$";

        /* Creating HTTPMD URL object with the specified port number */
        try {
            httpmdURL = new URL(protocol, host,
                    ((Integer) ports.get(tc_name)).intValue(), file);
            logger.log(Level.FINE, "HTTPMD URL: " + httpmdURL);
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception while creating URL: " + e);
            return false;
        }
        return getAndCompare(httpmdURL, tc_name);
    }

    /**
     * Getting the default port number of the specified HTTPMD URL object
     * and comparing the port with expected result (default port number).
     *
     * @param u    HTTPMD URL object
     * @param tc_n Test Case Name
     * @return status of comparison (true (if the default port number is
     *         equal to the expected one) or false).
     */
    public boolean getAndCompare(URL u, String tc_n) {

        /* Getting the default port number of the specified HTTPMD URL object */
        int portNum = u.getDefaultPort();
        logger.log(Level.FINE, "Expected default port number: " + defPort);
        logger.log(Level.FINE, "Returned default port number: " + portNum);

        /* Comparison */
        if (portNum != defPort) {
            logger.log(Level.FINE, tc_n + " test case failed");
            return false;
        }
        return true;
    }
}
