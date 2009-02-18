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

package com.sun.jini.test.spec.config.configurationfile;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationNotFoundException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import com.sun.jini.test.spec.config.util.FakeClassLoader;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructor of
 *   CnfigurationFile class. There are four forms of constructor:
 *   1) public ConfigurationFile(String[] options)
 *   2) public ConfigurationFile(String[] options, ClassLoader cl)
 *   3) public ConfigurationFile(Reader reader, String[] options)
 *   4) public ConfigurationFile(Reader reader, String[] options,
 *                               ClassLoader cl)
 *
 * Test Cases:
 *   This test contains six test cases:
 *    a case with a constructor:
 *      public ConfigurationFile(String[] options)
 *    a case with a constructor:
 *      public ConfigurationFile(String[] options, ClassLoader cl)
 *    a case with a constructor:
 *      public ConfigurationFile(String[] options, null)
 *    a case with a constructor:
 *      public ConfigurationFile(Reader reader, String[] options)
 *    a case with a constructor:
 *      public ConfigurationFile(Reader reader, String[] options,
 *                               ClassLoader cl)
 *    a case with a constructor:
 *      public ConfigurationFile(Reader reader, String[] options,
 *                               null)
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeClassLoader
 *
 * Actions:
 *   Test checks normal and broken variants of options as a
 *   parameter for ConfigurationFile constructor. Then normal
 *   and broken variants of the reader are checked and at last
 *   normal variants of ClassLoader.
 *
 *   Test performs the following steps:
 *     a) valid option:
 *       1) construct a ConfigurationFile object
 *          passing null as a parameters for options;
 *          assert the object is constructed and no exceptions are thrown;
 *       2) construct a ConfigurationFile object
 *          passing empty string array;
 *          assert the object is constructed and no exceptions are thrown;
 *       3) construct a ConfigurationFile object
 *          passing options with the "-" string as a first element;
 *          assert the object is constructed and no exceptions are thrown;
 *       4) construct a ConfigurationFile object
 *          passing options with the valid URL with the "file" protocol
 *          as a first element;
 *          assert the object is constructed and no exceptions are thrown;
 *       5) construct a ConfigurationFile object
 *          passing options with the valid URL with the "http" protocol
 *          as a first element;
 *          assert the object is constructed and no exceptions are thrown;
 *       6) construct a ConfigurationFile object
 *          passing options with the valid URL with the "httpmd" protocol
 *          as a first element;
 *          assert the object is constructed and no exceptions are thrown;
 *       7) construct a ConfigurationFile object
 *          passing options with the valid file name as a first element;
 *          assert the object is constructed and no exceptions are thrown;
 *       8) construct a ConfigurationFile object
 *          passing options with the valid file name as a first element
 *          and one new valid override option;
 *          assert the object is constructed and no exceptions are thrown;
 *       9) construct a ConfigurationFile object
 *          passing options with the valid source file name as
 *          a first element and one override option the same as exists
 *          in the source file;
 *          assert the object is constructed and no exceptions are thrown;
 *     b) broken option:
 *       10) construct a ConfigurationFile object
 *           passing options with null as a first element;
 *           assert the ConfigurationException is thrown;
 *       11) construct a ConfigurationFile object
 *           passing options with the "-" string as a first
 *           element and null as a second element;
 *           assert the ConfigurationException is thrown;
 *       12) construct a ConfigurationFile object
 *           passing options with invalid URL name;
 *           assert the ConfigurationNotFoundException is thrown;
 *       13) construct a ConfigurationFile object
 *           passing options with valid URL format but name is not found;
 *           assert the ConfigurationNotFoundException is thrown;
 *       14) construct a ConfigurationFile object
 *           passing options with valid URL but caller does
 *           not have permission to access;
 *           assert the ConfigurationNotFoundException is thrown;
 *       15) construct a ConfigurationFile object
 *           passing options with invalid file name;
 *           assert the ConfigurationNotFoundException is thrown;
 *       16) construct a ConfigurationFile object
 *           passing options with valid file name but file is not found;
 *           assert the ConfigurationNotFoundException is thrown;
 *       17) construct a ConfigurationFile object
 *           passing options with valid file name but caller does
 *           not have permission to access;
 *           assert the ConfigurationNotFoundException is thrown;
 *       18) construct a ConfigurationFile object
 *           passing options with the valid file name as a first element
 *           with broken configuration in the file;
 *           assert the ConfigurationException is thrown;
 *       19) construct a ConfigurationFile object
 *           passing options with the valid URL as a first element
 *           with syntax error configuration in this location;
 *           assert the ConfigurationException is thrown;
 *       20) construct a ConfigurationFile object
 *           passing options with the valid file name as a first element
 *           and one override option with bad syntax;
 *           assert the ConfigurationException is thrown;
 *     c) valid reader (if exists):
 *       21) create reader with some valid entries and repeat steps
 *           from 1 to 9 passing this reader as a parameter;
 *     d) broken reader (if exists):
 *       22) construct a ConfigurationFile object
 *           passing null for reader
 *           assert the NullPointerException is thrown;
 *       23) construct a ConfigurationFile object
 *           passing reader with syntax error in it
 *           assert the ConfigurationException is thrown;
 *     c) valid classloader (if exists):
 *       24) repeat steps from 1 to 23 passing null for class loader as
 *           a parameter;
 *       25) create FakeClassLoader and repeat steps from 1 to 23 passing
 *           this loader as a parameter;
 *           assert the object is constructed and no exceptions are thrown;
 * </pre>
 */
public class Constructor_Test extends Template_Test {

    /**
     * Start test execution for one test case. Actions see in class description.
     */
    public void runCase(Object testCase) throws Exception {
        logger.log(Level.INFO, "=================" + testCase.toString());

        callConstructor(testCase, confFile, null);

        String[] emptyOptions = { };
        callConstructor(testCase, confFile, emptyOptions);

        String[] optionsWithDash = { "-" };
        callConstructor(testCase, confFile, optionsWithDash);

        URL confFileURL = confFile.toURL();
        logger.log(Level.INFO,
                "File URL=" + confFileURL.toString());
        String[] optionsWithFileURL = { confFileURL.toString() };
        callConstructor(testCase, confFileURL, optionsWithFileURL);

        confHttpURL = new URL("http", "localhost", port,
                "/" + confFileName);
        logger.log(Level.INFO,
                "Http URL=" + confHttpURL.toString());
        String[] optionsWithHttpURL = { confHttpURL.toString() };
        callConstructor(testCase, confHttpURL, optionsWithHttpURL);

        md.update(someValidConf.getBytes());
        String messageDigestString = digestString(md.digest());
        URL confHttpmdURL = new URL("httpmd", "localhost", port,
                "/" + confFileName + ";" + "MD5=" + messageDigestString);
        logger.log(Level.INFO,
                "Httpmd URL=" + confHttpmdURL.toString());
        String[] optionsWithHttpmdURL = { confHttpmdURL.toString() };
        callConstructor(testCase, confHttpmdURL, optionsWithHttpmdURL);

        logger.log(Level.INFO, "File=" + confFile.getPath());
        String[] optionsWithFile = { confFile.getPath() };
        callConstructor(testCase, confFile, optionsWithFile);

        String[] optionsWithFileAndOverrideNew = {
                confFile.getPath(),
                overrideNew };
        callConstructor(testCase, confFile, optionsWithFileAndOverrideNew);

        String[] optionsWithFileAndOverrideSame = {
                confFile.getPath(),
                overrideSame };
        callConstructor(testCase, confFile, optionsWithFileAndOverrideSame);

        String[] optionsWithNull = { null };
        try {
            callConstructor(testCase, confFile, optionsWithNull);
            throw new TestException(
                    "ConfigurationException should be thrown if first"
                    + " element of options is null");
        } catch (ConfigurationException ce) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of first"
                    + " element of options is null");
        }

        String[] optionsWithDashAndNull = { "-", null };
        try {
            callConstructor(testCase, confFile, optionsWithDashAndNull);
            throw new TestException(
                    "ConfigurationException should be thrown if second"
                    + " element of options is null");
        } catch (ConfigurationException ce) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of second"
                    + " element of options is null");
        }

        if (!withReader(testCase)) {
            String[] optionsWithInvalidURL = { "someprotocol:*/somefile" };
            try {
                callConstructor(testCase, confFile, optionsWithInvalidURL);
                    throw new TestException(
                            "ConfigurationException should be thrown if first"
                            + " element is invalid url");
            } catch (ConfigurationException ce) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of first"
                        + " element is invalid url");
            }

            unexistConfFileURL = unexistConfFile.toURL();
            String[] optionsWithUnexistFileURL = { 
                    unexistConfFileURL.toString() };
            try {
                callConstructor(testCase, unexistConfFileURL,
                        optionsWithUnexistFileURL);
                throw new TestException(
                        "ConfigurationException should be thrown if first"
                        + " element is url that points to unexist file");
            } catch (ConfigurationException ce) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of first"
                        + " element is url that points to unexist file");
            }

            // HTTPServer2 port does not have permission to access
            // according to policy file for this test
            confHttpURLNoAccess = new URL("http", "localhost", portNoAccess,
                   "/" + confFileName);
            logger.log(Level.INFO,
                    "Http URL with no access = "
                    + confHttpURLNoAccess.toString());
            String[] optionsWithHttpNoAccessURL = {
                    confHttpURLNoAccess.toString() };
            try {
                callConstructor(testCase, confFile, optionsWithHttpNoAccessURL);
                throw new TestException(
                        "ConfigurationException should be thrown if first"
                        + " element is url that points to server the caller"
                        + " does not have permission to access");
            } catch (ConfigurationException ce) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of first"
                        + " element is url that points to server the caller"
                        + " does not have permission to access");
            }

            String[] optionsWithInvalidFileName = { tmpDirName };
            try {
                callConstructor(testCase, confFile, optionsWithInvalidFileName);
                throw new TestException(
                        "ConfigurationNotFoundException should be thrown if"
                        + " first element is invalid file name");
            } catch (ConfigurationNotFoundException cnfe) {
                logger.log(Level.INFO,
                        "ConfigurationNotFoundException in case of first"
                        + " element is invalid file name");
            }

            String[] optionsWithUnexistFile = { confFile.getPath() + "1" };
            try {
                callConstructor(testCase, confFile, optionsWithUnexistFile);
                throw new TestException(
                        "ConfigurationNotFoundException should be thrown if"
                        + " first element is unexist file name");
            } catch (ConfigurationNotFoundException cnfe) {
                logger.log(Level.INFO,
                        "ConfigurationNotFoundException in case of first"
                        + " element is unexist file name");
            }

            logger.log(Level.INFO, "File=" + noAccessFile.toString());
            String[] optionsWithNoAccessFile = { noAccessFile.toString() };
            try {
                callConstructor(testCase, noAccessFile,
                        optionsWithNoAccessFile);
                throw new TestException(
                        "ConfigurationException should be thrown if"
                        + " first element is access denied file");
            } catch (ConfigurationException cnfe) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of first"
                        + " element is access denied file");
            }

            logger.log(Level.INFO, "brokenConfFile=" +
                    brokenConfFile.getPath());
            String[] optionsWithBrokenConfFile = { brokenConfFile.getPath() };
            try {
                callConstructor(testCase, brokenConfFile,
                        optionsWithBrokenConfFile);
                throw new TestException(
                        "ConfigurationException should be thrown if"
                        + " first element is file with broken configuration");
            } catch (ConfigurationException cnfe) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of first"
                        + " element is file with broken configuration");
            }

            brokenConfHttpURL = new URL("http", "localhost", port,
                    "/" + brokenConfFileName);
            logger.log(Level.INFO,
                    "Http URL=" + brokenConfHttpURL.toString());
            String[] optionsWithbrokenConfHttpURL = {
                    brokenConfHttpURL.toString() };
            try {
                callConstructor(testCase, brokenConfHttpURL,
                        optionsWithbrokenConfHttpURL);
                throw new TestException(
                        "ConfigurationException should be thrown if"
                        + " first element is URL with broken configuration");
            } catch (ConfigurationException cnfe) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of first"
                        + " element is URL with broken configuration");
            }
        }

        String[] optionsWithFileAndBrokenOverride = {
                confFile.getPath(), brokenOverride };
        try {
            callConstructor(testCase, confFile,
                    optionsWithFileAndBrokenOverride);
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " first element of options is valid configuration"
                    + " file and second is a broken override");
        } catch (ConfigurationException cnfe) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of"
                    + " first element of options is valid configuration"
                    + " file and second is a broken override");
        }

        if (withReader(testCase)) {
            try {
                callConstructor(testCase, null, emptyOptions);
                throw new TestException(
                        "NullPointerException should be thrown if"
                        + " reader is equal null");
            } catch (NullPointerException npe) {
                logger.log(Level.INFO,
                        "NullPointerException in case of"
                        + " reader is equal null");
            }
        }
    }

    /**
     * Prepare test for running.
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        QAConfig config = (QAConfig) sysConfig;
        port = config.getIntConfigVal("HTTPServer.port", -1);
        portNoAccess = config.getIntConfigVal("HTTPServer2.port", -1);
        manager.startService("HTTPServer");
        manager.startService("HTTPServer2");
        md = MessageDigest.getInstance("MD5");
        createFile(confFile, someValidConf);
        createFile(brokenConfFile, someBrokenConf);
        createFile(noAccessFile, someValidConf);
        fakeClassLoader = new FakeClassLoader();
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < testCases.length; ++i) {
            runCase(testCases[i]);
        }
    }

    /**
     * Clearning temporary resources.
     *
     */
    public void tearDown() {
        try {
            confFile.delete();
            brokenConfFile.delete();
            noAccessFile.delete();
        } catch (Throwable t) {
            logger.log(Level.INFO, "Some problems in tearDown()");
            t.printStackTrace();
        }
    }
}
