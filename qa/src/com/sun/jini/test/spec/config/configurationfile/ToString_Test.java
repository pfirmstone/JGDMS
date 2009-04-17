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
 *   This test verifies the behavior of the toString() method of
 *   {@link CofigurationFile} class. There are four forms of constructor:
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
 *   Test constructs various variants ConfigurationFile object instances.
 *   and verifies that call to their toString method returns non empty
 *   string.
 *
 *   Test performs the following steps:
 *       1) construct a ConfigurationFile object
 *          passing null as a parameters for options;
 *          assert the toString method returns non empty string;
 *       2) construct a ConfigurationFile object
 *          passing empty string array;
 *          assert the toString method returns non empty string;
 *       3) construct a ConfigurationFile object
 *          passing options with the "-" string as a first element;
 *          assert the toString method returns non empty string;
 *       4) construct a ConfigurationFile object
 *          passing options with the valid URL with the "file" protocol
 *          as a first element;
 *          assert the toString method returns non empty string;
 *       5) construct a ConfigurationFile object
 *          passing options with the valid URL with the "http" protocol
 *          as a first element;
 *          assert the toString method returns non empty string;
 *       6) construct a ConfigurationFile object
 *          passing options with the valid URL with the "httpmd" protocol
 *          as a first element;
 *          assert the toString method returns non empty string;
 *       7) construct a ConfigurationFile object
 *          passing options with the valid file name as a first element;
 *          assert the toString method returns non empty string;
 *       8) construct a ConfigurationFile object
 *          passing options with the valid file name as a first element
 *          and one new valid override option;
 *          assert the toString method returns non empty string;
 *       9) construct a ConfigurationFile object
 *          passing options with the valid source file name as
 *          a first element and one override option the same as exists
 *          in the source file;
 *          assert the toString method returns non empty string;
 * </pre>
 */
public class ToString_Test extends QATest {
    /**
     * An object to point to constructor:
     *   public ConfigurationFile(String[] options)
     */
    Object OPT_TEST_CASE = new Object() {
        public String toString() {
            return "Constructor_Test.OPT_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(String[] options, ClassLoader cl)
     */
    Object OPT_CL_TEST_CASE = new Object() {
        public String toString() {
            return "Constructor_Test.OPT_CL_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(String[] options, null)
     */
    Object OPT_NULL_TEST_CASE = new Object() {
        public String toString() {
            return "Constructor_Test.OPT_NULL_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(Reader reader, String[] options)
     */
    Object RDR_OPT_TEST_CASE = new Object() {
        public String toString() {
            return "Constructor_Test.RDR_OPT_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(Reader reader, String[] options,
     *                            ClassLoader cl)
     */
    Object RDR_OPT_CL_TEST_CASE = new Object() {
        public String toString() {
            return "Constructor_Test.RDR_OPT_CL_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(Reader reader, String[] options,
     *                            null)
     */
    Object RDR_OPT_NULL_TEST_CASE = new Object() {
        public String toString() {
            return "Constructor_Test.RDR_OPT_NULL_TEST_CASE";
        }
    };

    Object[] testCases = new Object[] {
        OPT_TEST_CASE,
        OPT_CL_TEST_CASE,
        OPT_NULL_TEST_CASE,
        RDR_OPT_TEST_CASE,
        RDR_OPT_CL_TEST_CASE,
        RDR_OPT_NULL_TEST_CASE
    };

    /**
     * Check if reader exists in test case.
     */
    private boolean withReader(Object testCase) {
        return ((testCase == RDR_OPT_TEST_CASE)
             || (testCase == RDR_OPT_CL_TEST_CASE)
             || (testCase == RDR_OPT_NULL_TEST_CASE));
    }

    /**
     * Some valid configuration.
     */
    final private String someValidConf =
            "import net.jini.security.BasicProxyPreparer;\n"
            + "com.sun.jini.start {\n"
            + "    activationSystemPreparer = new BasicProxyPreparer();\n"
            + "}\n";

    /**
     * Some override new entry.
     */
    final private String overrideNew =
            "net.jini.lookup.JoinManager.registrarPreparer = "
            + " new BasicProxyPreparer()";
    
    /**
     * Override entry same as exists in someValidConf.
     */
    final private String overrideSame =
            "com.sun.jini.start.activationSystemPreparer = "
            + " new SomeProxyPreparer()";

    /**
     * File name for valid configuration.
     */
    final private String confFileName = "valid.prop";

    /**
     * Name of temporary directory from system java.io.tmpdir property.
     */
    final private String tmpDirName = System.getProperty("java.io.tmpdir");
    
    /**
     * File for valid configuration.
     */
    final private File confFile = new File(tmpDirName, confFileName);

    /**
     * Port number for valid http server.
     */
    private int port;

    /**
     * File URL for valid configuration.
     */
    private URL confFileURL;

    /**
     * Http URL for valid configuration.
     */
    private URL confHttpURL;

    /**
     * Message digestfor httpmd testing.
     */
    private MessageDigest md;

    /**
     * Fake class loader.
     */
    private FakeClassLoader fakeClassLoader;

    /**
     * Write information to the file.
     *
     * @param file in this file content will be written
     * @param content desired content of configuration file
     */
    private void createFile(File file, String content)
            throws FileNotFoundException, UnsupportedEncodingException,
            IOException {
        final OutputStream cfos = new FileOutputStream(file);
        final OutputStreamWriter cfosw = new OutputStreamWriter(cfos, "UTF-8");
        final Writer cfw = new BufferedWriter(cfosw);
        cfw.write(content);
        cfw.close();
        cfosw.close();
        cfos.close();
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
     * Run ConfigurationFile constructor for valid test case.
     *
     * @param testCase test case from 1 to 4 according to
     * list in  class description
     * @param forReader File, URL or null for constructing reader if needed
     * @param options is used as constructor parameter
     * @param cl is used as constructor parameter
     */
    protected void checkVariant(
            Object testCase,
            Object forReader,
            String[] options)
        throws ConfigurationException,
            ConfigurationNotFoundException,
            FileNotFoundException,
            UnsupportedEncodingException,
            IOException,
            TestException
    {
        InputStream cfis = null;
        Reader reader = null;
        if (withReader(testCase)) {
            if (forReader instanceof File) {
                cfis = new FileInputStream((File)forReader);
                reader = new InputStreamReader(cfis, "UTF-8");
            } else if (forReader instanceof URL) {
                cfis = ((URL)forReader).openStream();
                reader = new InputStreamReader(cfis, "UTF-8");
            }
        }
        ConfigurationFile cf = null;
        if (       testCase == OPT_TEST_CASE) {
            cf = new ConfigurationFile(options);
        } else if (testCase == OPT_CL_TEST_CASE) {
            cf = new ConfigurationFile(options, fakeClassLoader);
        } else if (testCase == OPT_NULL_TEST_CASE) {
            cf = new ConfigurationFile(options, null);
        } else if (testCase == RDR_OPT_TEST_CASE) {
            cf = new ConfigurationFile(reader, options);
        } else if (testCase == RDR_OPT_CL_TEST_CASE) {
            cf = new ConfigurationFile(reader, options, fakeClassLoader);
        } else if (testCase == RDR_OPT_NULL_TEST_CASE) {
            cf = new ConfigurationFile(reader, options, null);
        }
        String s = cf.toString();
        logger.log(Level.INFO, "toString()=" + s);
        assertion(s != null, "toString method returns null");
        assertion(s.length() != 0, "toString method returns empty string");
        if (withReader(testCase)) {
            if (reader != null) reader.close();
            if (cfis != null) cfis.close();
        }
    }

    /**
     * Start test case execution.
     */
    public void runCase(Object testCase) throws Exception {
        logger.log(Level.INFO, "=================" + testCase.toString());

        checkVariant(testCase, confFile, null);

        String[] emptyOptions = { };
        checkVariant(testCase, confFile, emptyOptions);

        String[] optionsWithDash = { "-" };
        checkVariant(testCase, confFile, optionsWithDash);

        URL confFileURL = confFile.toURL();
        logger.log(Level.INFO,
                "File URL=" + confFileURL.toString());
        String[] optionsWithFileURL = { confFileURL.toString() };
        checkVariant(testCase, confFileURL, optionsWithFileURL);

        confHttpURL = new URL("http", "localhost", port,
                "/" + confFileName);
        logger.log(Level.INFO,
                "Http URL=" + confHttpURL.toString());
        String[] optionsWithHttpURL = { confHttpURL.toString() };
        checkVariant(testCase, confHttpURL, optionsWithHttpURL);

        md.update(someValidConf.getBytes());
        String messageDigestString = digestString(md.digest());
        URL confHttpmdURL = new URL("httpmd", "localhost", port,
                "/" + confFileName + ";" + "MD5=" + messageDigestString);
        logger.log(Level.INFO,
                "Httpmd URL=" + confHttpmdURL.toString());
        String[] optionsWithHttpmdURL = { confHttpmdURL.toString() };
        checkVariant(testCase, confHttpmdURL, optionsWithHttpmdURL);

        logger.log(Level.INFO, "File=" + confFile.getPath());
        String[] optionsWithFile = { confFile.getPath() };
        checkVariant(testCase, confFile, optionsWithFile);

        String[] optionsWithFileAndOverrideNew = {
                confFile.getPath(),
                overrideNew };
        checkVariant(testCase, confFile, optionsWithFileAndOverrideNew);

        String[] optionsWithFileAndOverrideSame = {
                confFile.getPath(),
                overrideSame };
        checkVariant(testCase, confFile, optionsWithFileAndOverrideSame);
    }

    /**
     * Prepare test for running.
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        QAConfig config = (QAConfig) sysConfig;
        port = config.getIntConfigVal("HTTPServer.port", -1);
        manager.startService("HTTPServer");
        md = MessageDigest.getInstance("MD5");
        createFile(confFile, someValidConf);
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
     */
    public void tearDown() {
        try {
            confFile.delete();
        } catch (Throwable t) {
            logger.log(Level.INFO, "Unexpected exception in tearDown()");
            t.printStackTrace();
        }
    }
}
