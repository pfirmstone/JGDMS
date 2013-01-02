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
package com.sun.jini.test.spec.url.httpmd.wrongmdexc;

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
import net.jini.url.httpmd.WrongMessageDigestException;
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;
import java.net.MalformedURLException;

// java.io
import java.io.InputStream;
import java.io.IOException;


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
 *   digest and then obtain contents of the file specified by the created
 *   HTTPMD URL.
 *   It's expected that
 *   {@link WrongMessageDigestException} is thrown.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     - WrongMDException
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD urls creating
 *       ({@link Handler} is used as HTTPMD Protocol handler),
 *     - launching HTTP Server.
 *   Test performs the following steps:
 *     - implementing test case.
 *
 * </pre>
 */
public class WrongMDException extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * HTTP Server port number.
     * The value is specified by testClassServer.port test property.
     */
    protected int classServerPort;

    /**
     * Filename to be specified in HTTPMD URL. The value is specified
     * by WrongMDException.File test property.
     */
    protected String filename;

    /**
     * <pre>
     * This method performs all preparations.
     * Test parameters:
     *    WrongMDException.File - filename to be specified in HTTPMD URL
     *    testClassServer.port    - HTTP Server port number
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Getting test parameters */
        filename = config.getStringConfigVal("WrongMDException.File", null);
        classServerPort = config.getIntConfigVal("testClassServer.port", 0);
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        try {

            /* Trying to create HTTPMD URL */
            URL httpmdUrl = createHTTPMDURL(filename);
            logger.log(Level.FINE, "httpmdUrl: " + httpmdUrl);

            /* Trying to get contents of the file specified by HTTPMD URL */
            String URLContents = getFileContents(httpmdUrl);
            throw new TestException(
                    "" + " test: Failed\n"
                    + "Expected result:\nWrongMessageDigestException\n"
                    + "Returned result:\n"
                    + "data from HTTPMD URL has been obtained successfully:\n"
                    + "  data:\n" + URLContents);
        } catch (WrongMessageDigestException e) {
            logger.log(Level.FINE, "Returned result:\n" + e);
        } catch (Exception e) {
            throw new TestException(
                    "" + " test: Failed\n"
                    + "Expected result:\nWrongMessageDigestException\n"
                    + "Returned result:\n" + e);
        }

        return;
    }

    /**
     * Creating HTTPMD URL object for the specified file.
     *
     * @param fname filename as a String
     * @throws MalformedURLException if HTTPMD URL can't be created
     * @return URL object
     */
    public URL createHTTPMDURL(String fname) throws MalformedURLException {
        final String md = ";MD5=abcedf0123456789,comments";
        return new URL("httpmd", "localhost", classServerPort,
                "/" + fname + md);
    }

    /**
     * Obtaining the contents of the file specified by HTTPMD URL object.
     *
     * @param u HTTPMD URL
     * @throws IOException if it's impossible to get contents of the file
     * @return the contents of the file specified by HTTPMD URL as a String
     */
    public String getFileContents(URL u) throws IOException {
        final int BUFSIZE = 8;
        byte[] buf = new byte[BUFSIZE];
        Vector v = new Vector();
        InputStream in = u.openStream();

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
