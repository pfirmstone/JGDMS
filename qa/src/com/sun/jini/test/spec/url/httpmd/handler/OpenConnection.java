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

// davis packages
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;
import java.net.URLConnection;
import java.net.NetPermission;

// java.io
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.IOException;

// java.security
import java.security.MessageDigest;
import java.security.DigestInputStream;

// java.util
import java.util.Vector;

// TestHandler extends url.httpmd.Handler class
import com.sun.jini.test.spec.url.httpmd.util.TestHandler;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of {@link Handler#openConnection(URL)}
 *   method.
 *   {@link Handler#openConnection(URL)} method should create a HTTPMD URL
 *   connection for an HTTPMD URL object or throw IOException if an I/O error
 *   occurs while opening the connection.
 *
 * Test Cases:
 *   This test tries to create a HTTPMD URL connection for an HTTPMD URL object.
 *   The cases:
 *     - OpenConnectionValidURL
 *       - URLConnection connect = openConnection(URL),
 *       - it's verified that URLConnection object is created;
 *       - InputStream in = connect.getInputStream(),
 *       - read data from InputStream and compare them with
 *         data retrived from the file corresponding to the HTTPMD URL object;
 *     - OpenConnectionNonExistFile
 *       - URLConnection connect = openConnection(URL),
 *         where URL references to non-existent file,
 *       - it's verified that URLConnection object is created;
 *       - InputStream in = connect.getInputStream(),
 *       - try to read data from InputStream;
 *	 - it's expected that IOException is thrown while reading data;
 *       Notes: At least on SPARC/Solaris 8:
 *          1. IOException isn't thrown while opening a HTTPMD URL connection,
 *          2. Instead IOException is thrown while trying to read from the open
 *             HTTPMD URL connection.
 *
 * Infrastructure:
 *     - TestHandler
 *         extends {@link Handler} class
 *     - OpenConnection
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD urls creating
 *       ({@link Handler} is used as HTTPMD Protocol handler),
 *     - launching HTTP Server.
 *   Test performs the following steps:
 *     - setting the ability to specify a TestHandler stream handler
 *       when constructing a HTTPMD URL object,
 *     - getting test parameters,
 *     - creating HTTPMD URL object,
 *     - creating TestHandler object,
 *     - implementing each case.
 *
 * </pre>
 */
public class OpenConnection extends QATestEnvironment implements Test {
    QAConfig config;
    final static int BUFSIZE = 8;

    /** HTTPMD protocol handler */
    protected TestHandler handler;

    /**
     * HTTP Server source directory.
     * The value is specified by testClassServer.dir test property.
     */
    protected String classServerSrcDir;

    /**
     * HTTP Server port number.
     * The value is specified by testClassServer.port test property.
     */
    protected int classServerPort;

    /**
     * HTTPMD URL object.
     * It's created from the String representation specified by
     * OpenConnection.Url test property.
     */
    protected URL httpmdURL;

    /** Contents of file specified by HTTPMD URL object */
    protected Vector realFileContents = new Vector();

    /** Expected contents of file specified by HTTPMD URL object */
    protected Vector expectedFileContents = new Vector();

    /**
     * Expected Exception as a String
     * The value is specified by OpenConnection.ExpResult test property.
     */
    protected String expClassStr;

    /** Expected result */
    protected Object expectedResult;

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     *  - setting the ability to specify a TestHandler stream handler
     *    when constructing a HTTPMD URL object,
     *  - getting test parameters,
     *  - creating HTTPMD URL object,
     *  - creating TestHandler object.
     * Test parameters:
     *    OpenConnection.Url       - String representation of HTTPMD URL
     *    OpenConnection.ExpResult - expected result
     *    testClassServer.dir        - HTTP Server source directory
     *    testClassServer.port       - HTTP Server port number
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /*
         * Setting the ability to specify a TestHandler stream handler
         * when constructing a HTTPMD URL object
         */
        NetPermission np = new NetPermission("specifyStreamHandler");

        /*
         * Checking the ability to specify a TestHandler stream handler
         * when constructing a HTTPMD URL object
         */
        (System.getSecurityManager()).checkPermission(np);

        /* Instantiating TestHandler object */
        handler = new TestHandler();

        /* Getting test parameters */
        String u = config.getStringConfigVal("OpenConnection.Url", "");
        expClassStr = config.getStringConfigVal("OpenConnection.ExpResult",
                null);

        /* Getting the source directory of HTTP Server */
        classServerSrcDir = config.getStringConfigVal("testClassServer.dir",
                null);

        /* Getting the port number of HTTP Server */
        classServerPort = config.getIntConfigVal("testClassServer.port", 0);

        /* Creating URL object according to the specified spec */
        httpmdURL = new URL(u);

        if (expClassStr != null) {

            /* Exception is expected as result of openConnection() method */

            /*
             * Creating Class object associated with the class with the
             * given string name
             */
            expectedResult = Class.forName(expClassStr);
        } else {

            /* No Exception is expected as result of openConnection() */

            /* Correcting HTTPMD URL object */
            httpmdURL = correctURL(httpmdURL);

            /* Absolute filename */
            String absfn = httpmdURL.getFile().substring(0,
                    httpmdURL.getFile().indexOf(";"));

            /* Obtaining the contents of the file into Vector of Bytes */
            getFileContents(classServerSrcDir + absfn);
            expectedResult = expectedFileContents;
        }
        return this;
    }

    /**
     * <pre>
     * Correcting the specified HTTPMD URL object.
     * The new HTTPMD URL object features:
     *   port is equal to HTTP Server port,
     *   the message digest is corrected.
     * </pre>
     * @param u HTTPMD URL object
     * @throws Exception if the specified HTTPMD URL object can't be corrected
     * @return HTTPMD URL object with the corrected message digest
     */
    protected URL correctURL(URL u) throws Exception {

        /*
         * Obtaining relative pathname of the file specified in the created
         * URL object
         */
        String relfileName = u.getFile().substring(0, u.getFile().indexOf(";"));

        /* Getting absolute pathname of the file */
        String absfileName = classServerSrcDir + relfileName;

        /* Obtaining the message digest algorithm from the created URL object */
        String urltail = u.getFile().substring(u.getFile().indexOf(";"),
                u.getFile().length());
        String alg = urltail.substring(urltail.indexOf(";") + 1,
                urltail.indexOf("="));

        /*
         * Computing message digest for the file specified in the created
         * URL object
         */
        String MD = computeFileMD(absfileName, alg);

        /* Obtaining comments from the created URL object */
        urltail = urltail.substring(urltail.indexOf("=") + 1, urltail.length());
        String comments = urltail.substring(urltail.indexOf(",") + 1,
                urltail.length());

        /*
         * Creating HTTPMD URL object with the corrected message digest and
         * HTTP Server port. TestHandler is used as protocol handler.
         */
        URL url = new URL(u.getProtocol(), u.getHost(), classServerPort,
                relfileName + ";" + alg + "=" + MD + "," + comments, handler);
        return url;
    }

    /**
     * Computing the message digest as a String in hexadecimal format
     * for the specified file and message digest algorithm.
     *
     * @param filename filename.
     * @param algorithm message digest algorithm.
     * @throws Exception if it's impossible to compute the message digest
     *                   for the specified file according to the specified
     *                   algorithm
     * @return the message digest as a String in hexadecimal format
     */
    public String computeFileMD(String filename, String algorithm)
            throws Exception {
        MessageDigest MDigest = MessageDigest.getInstance(algorithm);

        /* Computing the message digest */
        FileInputStream fin = new FileInputStream(filename);
        BufferedInputStream bin = new BufferedInputStream(fin, BUFSIZE);
        DigestInputStream in = new DigestInputStream(bin, MDigest);
        in.on(true);
        byte[] buf = new byte[BUFSIZE];

        while (true) {
            int n = in.read(buf, 0, buf.length);

            if (n < 0) {
                break;
            }
        }
        MDigest = in.getMessageDigest();
        in.close();
        byte[] digest = MDigest.digest();

        /* The message digest as a String in hexadecimal format */
        return digestString(digest);
    }

    /**
     * Converting a message digest to a String in hexadecimal format.
     *
     * @param digest a message digest as a byte[].
     * @return the message digest as a String in hexadecimal format.
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
     * Obtaining the contents of the file into Vector of Bytes.
     *
     * @param filename     file name
     * @throws IOException if it's impossible to get contents of
     *                     the specified file
     */
    public void getFileContents(String filename) throws IOException {
        byte[] buf = new byte[BUFSIZE];
        FileInputStream fin = new FileInputStream(filename);

        while (true) {
            int n = fin.read(buf);

            if (n < 0) {
                break;
            }

            for (int i = 0; i < n; i++) {
                expectedFileContents.add(new Byte(buf[i]));
            }
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "HTTPMD URL: " + httpmdURL);

        /* Checking that openConnection(url) returns URLConnection object */
        if (!(handler.openConnection(httpmdURL) instanceof URLConnection)) {
            throw new TestException(
                    ""
                    + " test failed: openConnection() returns"
                    + " non-instance of URLConnection class");
        }

        try {

            /* Creating URLConnection object */
            URLConnection connect = handler.openConnection(httpmdURL);

            /* Getting contents from URLConnection object */
            InputStream in = connect.getInputStream();
            byte[] buf = new byte[BUFSIZE];

            while (true) {
                int n = in.read(buf);

                if (n < 0) {
                    break;
                }

                for (int i = 0; i < n; i++) {
                    realFileContents.add(new Byte(buf[i]));
                }
            }

            /* Compare got contents with expected one */
            compareResults(realFileContents);
        } catch (Exception e) {

            /* Compare got Exception with expected one */
            compareResults(e);
        }
        
        return;
    }

    /**
     * Checking if the Vector represented by expectedResult variable is
     * the same as the Vector specified by the Vector parameter.
     *
     * @param realres an Vector.
     */
    public void compareResults(Vector realres) throws TestException {
        if (expClassStr != null) {
            throw new TestException(
                    "" + " test failed:"
                    + " Expected result: " + expClassStr
                    + " Returned result:  FILE CONTENTS");
        }
        logger.log(Level.FINE, "Comparing byte arrays ...");
        Vector expectedVector = (Vector) expectedResult;
        Vector realVector = (Vector) realres;
        String expectedVectorStr = expectedResult.toString();
        String realVectorStr = realres.toString();
        logger.log(Level.FINE, "Expected result: <FILE CONTENTS>");
        logger.log(Level.FINE, "Returned result: <FILE CONTENTS>");

        if (!realVector.equals(expectedVector)) {
            throw new TestException(
                    "" + " test failed:\n"
                    + " The data read from HTTPMD URL isn't equal to"
                    + " the data retrived from the file corresponding"
                    + " to the HTTPMD URL");
        }
        return;
    }

    /**
     * Checking if the class represented by expectedResult variable is
     * either the same as, or is a superclass of the class
     * whose instance is specified by the Exception parameter.
     *
     * @param realres an Exception.
     */
    public void compareResults(Exception realres) throws TestException {
        if (expClassStr == null) {
            throw new TestException(
                    "" + " test failed:"
                    + " Expected result: FILE CONTENTS" + " Returned result: "
                    + realres);
        }
        logger.log(Level.FINE, "Comparing exceptions ...");
        Class expectedClass = (Class) expectedResult;
        Class exceptionClass = (Class) realres.getClass();
        logger.log(Level.FINE, "Expected result: " + expectedClass);
        logger.log(Level.FINE, "Returned result: " + exceptionClass);

        if (!expectedClass.isAssignableFrom(exceptionClass)) {
            // if (!expectedClass.equals(exceptionClass)) {
	    realres.printStackTrace();
            throw new TestException(
                    "" + " test failed:\n"
                    + " Expected result: " + expectedClass + " Returned result: "
                    + exceptionClass);
        }
        return;
    }
}
