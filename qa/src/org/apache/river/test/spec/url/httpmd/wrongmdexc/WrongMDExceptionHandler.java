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
package org.apache.river.test.spec.url.httpmd.wrongmdexc;

import java.util.logging.Level;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig; // base class for QAConfig
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// davis packages
import net.jini.url.httpmd.WrongMessageDigestException;
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.NetPermission;
import java.net.MalformedURLException;
import java.net.UnknownServiceException;

// java.io
import java.io.InputStream;
import java.io.IOException;

// TestHandler extends url.httpmd.Handler class
import org.apache.river.test.spec.url.httpmd.util.TestHandler;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies that
 *   {@link WrongMessageDigestException} is thrown
 *   when the message digest for data retrieved from an HTTPMD URL does not
 *   match the value specified in the HTTPMD URL.
 *
 * Test Cases:
 *   This test tries to create HTTPMD URL object with wrong value of message
 *   digest (TestHandler object is specified as HTTPMD protocol handler) and
 *   then obtain contents of the file specified by the created
 *   HTTPMD URL (using TestHandler.openConnection() method).
 *   It's expected that
 *   {@link WrongMessageDigestException} is thrown.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     - TestHandler
 *           extends Handler class (including Handler.openConnection() method)
 *     - WrongMDExceptionHandler
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - launching HTTP Server.
 *   Test performs the following steps:
 *     - setting and then checking the ability to specify a TestHandler
 *       stream handler when constructing a HTTPMD URL,
 *     - creating TestHandler object,
 *     - implement test case.
 *
 * </pre>
 */
public class WrongMDExceptionHandler extends WrongMDException {
    QAConfig config;

    /** HTTPMD protocol handler */
    protected TestHandler handler;

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     * - invoking super.construct() method,
     * - setting and then checking the ability to specify a TestHandler
     *   stream handler when constructing a HTTPMD URL,
     * - creating TestHandler object.
     * Test parameters:
     *    WrongMDException.File - filename to be specified in HTTPMD URL
     *    testClassServer.port    - HTTP Server port number
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config); // getting test parameters
        this.config = (QAConfig) config; // or this.config = getConfig();

        /*
         * Setting the ability to specify a TestHandler stream handler
         * when constructing a HTTPMD URL
         */
        NetPermission np = new NetPermission("specifyStreamHandler");

        /*
         * Checking the ability to specify a TestHandler stream handler
         * when constructing a HTTPMD URL
         */
        (System.getSecurityManager()).checkPermission(np);

        /* Creating TestHandler object */
        handler = new TestHandler();
        return this;
    }

    /**
     * Create HTTPMD URL object for the specified file.
     *
     * @param fname filename as a String
     * @throws MalformedURLException if HTTPMD URL can't be created
     * @throws SecurityException if HTTPMD URL can't be created
     * @return URL object
     */
    public URL createHTTPMDURL(String fname)
            throws MalformedURLException, SecurityException {
        final String md = ";MD5=abcedf0123456789,comments";
        return new URL("httpmd", "localhost", classServerPort, "/" + fname + md,
                handler);
    }

    /**
     * Obtaining the contents of the file specified by HTTPMD URL object.
     *
     * @param u HTTPMD URL.
     * @throws IOException             if an error occurs while getting
     *                                 contents of the file
     * @throws UnknownServiceException if an error occurs while getting
     *                                 contents of the file
     * @return the contents of the file specified by HTTPMD URL
     */
    public String getFileContents(URL u)
            throws IOException, UnknownServiceException {
        final int BUFSIZE = 8;
        byte[] buf = new byte[BUFSIZE];
        Vector v = new Vector();
        URLConnection uconn = handler.openConnection(u);
        uconn.connect();
        InputStream in = uconn.getInputStream();

        while (true) {
            int n = in.read(buf);

            if (n < 0) {
                break;
            }

            for (int i = 0; i < n; i++) {
                v.add(new Byte(buf[i]));
            }
        }
        return v.toString();
    }
}
