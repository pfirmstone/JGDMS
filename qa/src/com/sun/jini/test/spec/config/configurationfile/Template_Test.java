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
 *   This class contains common parts of some ConfigurationFile tests.
 * </pre>
 */
public abstract class Template_Test extends QATest {
    /**
     * An object to point to constructor:
     *   public ConfigurationFile(String[] options)
     */
    Object OPT_TEST_CASE = new Object() {
        public String toString() {
            return "OPT_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(String[] options, ClassLoader cl)
     */
    Object OPT_CL_TEST_CASE = new Object() {
        public String toString() {
            return "OPT_CL_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(String[] options, null)
     */
    Object OPT_NULL_TEST_CASE = new Object() {
        public String toString() {
            return "OPT_NULL_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(Reader reader, String[] options)
     */
    Object RDR_OPT_TEST_CASE = new Object() {
        public String toString() {
            return "RDR_OPT_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(Reader reader, String[] options,
     *                            ClassLoader cl)
     */
    Object RDR_OPT_CL_TEST_CASE = new Object() {
        public String toString() {
            return "RDR_OPT_CL_TEST_CASE";
        }
    };

    /**
     * An object to point to constructor:
     *   public ConfigurationFile(Reader reader, String[] options,
     *                            null)
     */
    Object RDR_OPT_NULL_TEST_CASE = new Object() {
        public String toString() {
            return "RDR_OPT_NULL_TEST_CASE";
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
     * An object to point to test subcase when tested entry
     * (if exists) is placed in file
     */
    Object OPT_FILE_SUBCASE = new Object() {
        public String toString() {
            return "OPT_FILE_SUBCASE";
        }
    };

    /**
     * An object to point to test subcase when tested entry
     * (if exists) is placed in url
     */
    Object OPT_URL_SUBCASE = new Object(){
        public String toString() {
            return "OPT_URL_SUBCASE";
        }
    };

    /**
     * An object to point to test subcase when tested entry
     * (if exists) is placed in overriding remaining options
     */
    Object OPT_OVERRIDE_SUBCASE = new Object(){
        public String toString() {
            return "OPT_OVERRIDE_SUBCASE";
        }
    };

    Object[] testSubCases = new Object[] {
        OPT_FILE_SUBCASE,
        OPT_URL_SUBCASE,
        OPT_OVERRIDE_SUBCASE
    };

    /**
     * Check if reader exists in test case.
     */
    protected boolean withReader(Object testCase) {
        return ((testCase == RDR_OPT_TEST_CASE)
             || (testCase == RDR_OPT_CL_TEST_CASE)
             || (testCase == RDR_OPT_NULL_TEST_CASE));
    }

    /**
     * Some valid configuration.
     */
    final protected String someValidConf =
            "import net.jini.security.BasicProxyPreparer;\n"
            + "com.sun.jini.start {\n"
            + "    activationSystemPreparer = new BasicProxyPreparer();\n"
            + "}\n";

    /**
     * Some broken configuration.
     */
    final protected String someBrokenConf = "<some broken configuration>";
    
    /**
     * Some override new entry.
     */
    final protected String overrideNew =
            "net.jini.lookup.JoinManager.registrarPreparer = "
            + " new BasicProxyPreparer()";
    
    /**
     * Override entry same as exists in someValidConf.
     */
    final protected String overrideSame =
            "com.sun.jini.start.activationSystemPreparer = "
            + " new SomeProxyPreparer()";

    /**
     * Broken override entry.
     */
    final protected String brokenOverride = "<broken override>";

    /**
     * File name for valid configuration.
     */
    final protected String confFileName = "valid.prop";

    /**
     * File name for broken configuration.
     */
    final protected String brokenConfFileName = "broken.prop";

    /**
     * File name for unexist file.
     */
    final protected String unexistFileName = "unexist.prop";
    
    /**
     * File name for file with no "read" permission.
     */
    final protected String noAccessFileName = "noaccess.prop";
    
    /**
     * Name of temporary directory from system java.io.tmpdir property.
     */
    final protected String tmpDirName = System.getProperty("java.io.tmpdir");
    
    /**
     * File for valid configuration.
     */
    final protected File confFile = new File(tmpDirName, confFileName);

    /**
     * File for broken configuration.
     */
    final protected File brokenConfFile = new File(tmpDirName,
            brokenConfFileName);

    /**
     * File object for unexist file.
     */
    final protected File unexistConfFile = new File(tmpDirName,
            unexistFileName);

    /**
     * File for access denied configuration
     */
    final protected File noAccessFile = new File(tmpDirName, noAccessFileName);

    /**
     * Port number for valid http server.
     */
    protected int port;

    /**
     * Port number for http server with access denied.
     */
    protected int portNoAccess;

    /**
     * File URL for valid configuration.
     */
    protected URL confFileURL;

    /**
     * Http URL for valid configuration.
     */
    protected URL confHttpURL;

    /**
     * URL for broken configuration.
     */
    protected URL brokenConfHttpURL;

    /**
     * URL for unexist file.
     */
    protected URL unexistConfFileURL;

    /**
     * URL for file with access denied.
     */
    protected URL confHttpURLNoAccess;

    /**
     * Message digestfor httpmd testing.
     */
    protected MessageDigest md;

    /**
     * Fake class loader.
     */
    protected FakeClassLoader fakeClassLoader;

    /**
     * Write information to the file.
     *
     * @param file in this file content will be written
     * @param content desired content of configuration file
     */
    protected void createFile(File file, String content)
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
     * @param testCase test case from according to testCases
     * @param forReader File, URL or null for constructing reader if needed
     * @param options is used as constructor parameter
     * @return instance of created ConfigurationFile object
     */
    protected ConfigurationFile callConstructor(
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
        ConfigurationFile result = null;
        if (withReader(testCase)) {
            if (forReader instanceof File) {
                cfis = new FileInputStream((File)forReader);
                reader = new InputStreamReader(cfis, "UTF-8");
            } else if (forReader instanceof URL) {
                cfis = ((URL)forReader).openStream();
                reader = new InputStreamReader(cfis, "UTF-8");
            }
        }
        if (       testCase == OPT_TEST_CASE) {
            result = new FakeConfigurationFile(options);
        } else if (testCase == OPT_CL_TEST_CASE) {
            result = new FakeConfigurationFile(options, fakeClassLoader);
        } else if (testCase == OPT_NULL_TEST_CASE) {
            result = new FakeConfigurationFile(options, null);
        } else if (testCase == RDR_OPT_TEST_CASE) {
            result = new FakeConfigurationFile(reader, options);
        } else if (testCase == RDR_OPT_CL_TEST_CASE) {
            result = new FakeConfigurationFile(reader,
                    options, fakeClassLoader);
        } else if (testCase == RDR_OPT_NULL_TEST_CASE) {
            result = new FakeConfigurationFile(reader, options, null);
        }
        if (withReader(testCase)) {
            if (reader != null) reader.close();
            if (cfis != null) cfis.close();
        }
        return result;
    }
}
