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
package com.sun.jini.test.spec.url.httpmd.httpmdutil;

import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// davis packages
import net.jini.url.httpmd.HttpmdUtil;
import net.jini.url.httpmd.Handler;

// java.io
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.BufferedInputStream;

// java.security
import java.security.MessageDigest;
import java.security.DigestInputStream;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link HttpmdUtil#computeDigestCodebase(String,String)}
 *   method.
 *   {@link HttpmdUtil#computeDigestCodebase(String,String)}
 *   method should compute the message digests for a codebase with HTTPMD
 *   URLs (the digest values specified in the URLs are ignored) or throw:
 *     IllegalArgumentException
 *       if any of the URLs in codebase fail to specify the HTTPMD protocol
 *     IOException
 *       if an I/O exception occurs while reading data from the source files
 *     MalformedURLException
 *       if any of the URLs in codebase have incorrect syntax
 *     NullPointerException
 *       if either argument is null
 *
 * Test Cases:
 *   This test tries to update the specified codebases representing
 *   various protocols (file, http, httpmd). In each test case
 *   the codebase updated to include computed digests or the Exception
 *   thrown by
 *   {@link HttpmdUtil#computeDigestCodebase(String,String)}
 *   is compared with expected result (the codebase or Exception class).
 *
 *   The cases:
 *
 *     1. absolute codebase is used:
 *        the message digest is calculated for non-empty .jar file
 *        (source directory: ${com.sun.jini.qa.jars}):
 *        codebases:
 *        1 - httpmd:/qa1-httpmd.jar;MD5=AAAAA
 *        2 - httpmd:/qa1-httpmd.jar;MD5=AAAAA,comments
 *        3 - httpmd:/qa1-httpmd.jar;md5=AAAAA
 *        4 - httpmd:/qa1-httpmd.jar;md5=AAAAA,comments
 *        5 - httpmd:/qa1-httpmd.jar;SHA=AAAAA
 *        6 - httpmd:/qa1-httpmd.jar;SHA=AAAAA,comments
 *        7 - httpmd:/qa1-httpmd.jar;sha=AAAAA
 *        8 - httpmd:/qa1-httpmd.jar;sha=AAAAA,comments
 *        9 - httpmd:/qa1-httpmd.jar;MD5=AAAAA?query=qqq
 *       10 - httpmd:/qa1-httpmd.jar;MD5=AAAAA,comments?query=qqq
 *       11 - httpmd:/qa1-httpmd.jar;md5=AAAAA?query=qqq
 *       12 - httpmd:/qa1-httpmd.jar;md5=AAAAA,comments?query=qqq
 *       13 - httpmd:/qa1-httpmd.jar;SHA=AAAAA?query=qqq
 *       14 - httpmd:/qa1-httpmd.jar;SHA=AAAAA,comments?query=qqq
 *       15 - httpmd:/qa1-httpmd.jar;sha=AAAAA?query=qqq
 *       16 - httpmd:/qa1-httpmd.jar;sha=AAAAA,comments?query=qqq
 *       17 - httpmd:/qa1-httpmd.jar;MD5=AAAAA#Ref
 *       18 - httpmd:/qa1-httpmd.jar;MD5=AAAAA,comments#Ref
 *       19 - httpmd:/qa1-httpmd.jar;md5=AAAAA#Ref
 *       20 - httpmd:/qa1-httpmd.jar;md5=AAAAA,comments#Ref
 *       21 - httpmd:/qa1-httpmd.jar;SHA=AAAAA#Ref
 *       22 - httpmd:/qa1-httpmd.jar;SHA=AAAAA,comments#Ref
 *       23 - httpmd:/qa1-httpmd.jar;sha=AAAAA#Ref
 *       24 - httpmd:/qa1-httpmd.jar;sha=AAAAA,comments#Ref
 *       25 - httpmd:/qa1-httpmd.jar;md5=AAAAA httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *       26 - httpmd:/qa1-httpmd.jar;md5=AAAAA,comments httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *       27 - httpmd://localhost/qa1-httpmd.jar;MD5=AAAAA
 *       28 - httpmd://localhost:1111/qa1-httpmd.jar;MD5=AAAAA
 *       29 - httpmd://localhost/qa1-httpmd.jar;MD5=AAAAA,comments
 *       30 - httpmd://localhost:1111/qa1-httpmd.jar;MD5=AAAAA,comments
 *       31 - httpmd://localhost/qa1-httpmd.jar;MD5=AAAAA?query=qqq
 *       32 - httpmd://localhost:1111/qa1-httpmd.jar;MD5=AAAAA?query=qqq
 *       33 - httpmd://localhost/qa1-httpmd.jar;MD5=AAAAA,comments?query=qqq
 *       34 - httpmd://localhost:1111/qa1-httpmd.jar;MD5=AAAAA,comments?query=qqq
 *       35 - httpmd://localhost/qa1-httpmd.jar;MD5=AAAAA#Ref
 *       36 - httpmd://localhost:1111/qa1-httpmd.jar;MD5=AAAAA#Ref
 *       37 - httpmd://localhost/qa1-httpmd.jar;MD5=AAAAA,comments#Ref
 *       38 - httpmd://localhost:1111/qa1-httpmd.jar;MD5=AAAAA,comments#Ref
 *       39 - httpmd://localhost/qa1-httpmd.jar;md5=AAAAA httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *       40 - httpmd://localhost:1111/qa1-httpmd.jar;md5=AAAAA httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *       41 - httpmd://localhost/qa1-httpmd.jar;md5=AAAAA,comments httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *       42 - httpmd://localhost:1111/qa1-httpmd.jar;md5=AAAAA,comments httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *        The codebase updated to include computed digests with
 *        {@link HttpmdUtil#computeDigestCodebase(String,String)} method is compared
 *        with the expected one.
 *        (computeDigestCodebase1_*)
 *
 *     2. absolute codebase is used:
 *        the message digest is calculated for an empty file:
 *        (source directory: current working directory):
 *        codebases:
 *        1 - httpmd:/empty_file;MD5=AAAAA
 *        2 - httpmd:/empty_file;MD5=AAAAA,comments
 *        3 - httpmd:/empty_file;md5=AAAAA
 *        4 - httpmd:/empty_file;md5=AAAAA,comments
 *        5 - httpmd:/empty_file;SHA=AAAAA
 *        6 - httpmd:/empty_file;SHA=AAAAA,comments
 *        7 - httpmd:/empty_file;sha=AAAAA
 *        8 - httpmd:/empty_file;sha=AAAAA,comments
 *        9 - httpmd:/empty_file;MD5=AAAAA?query=qqq
 *       10 - httpmd:/empty_file;MD5=AAAAA,comments?query=qqq
 *       11 - httpmd:/empty_file;md5=AAAAA?query=qqq
 *       12 - httpmd:/empty_file;md5=AAAAA,comments?query=qqq
 *       13 - httpmd:/empty_file;SHA=AAAAA?query=qqq
 *       14 - httpmd:/empty_file;SHA=AAAAA,comments?query=qqq
 *       15 - httpmd:/empty_file;sha=AAAAA?query=qqq
 *       16 - httpmd:/empty_file;sha=AAAAA,comments?query=qqq
 *       17 - httpmd:/empty_file;MD5=AAAAA#Ref
 *       18 - httpmd:/empty_file;MD5=AAAAA,comments#Ref
 *       19 - httpmd:/empty_file;md5=AAAAA#Ref
 *       20 - httpmd:/empty_file;md5=AAAAA,comments#Ref
 *       21 - httpmd:/empty_file;SHA=AAAAA#Ref
 *       22 - httpmd:/empty_file;SHA=AAAAA,comments#Ref
 *       23 - httpmd:/empty_file;sha=AAAAA#Ref
 *       24 - httpmd:/empty_file;sha=AAAAA,comments#Ref
 *       25 - httpmd:/empty_file;md5=AAAAA httpmd:/empty_file;md5=AAAAA
 *       26 - httpmd:/empty_file;md5=AAAAA,comments httpmd:/empty_file;md5=AAAAA
 *       27 - httpmd://localhost/empty_file;MD5=AAAAA
 *       28 - httpmd://localhost:1111/empty_file;MD5=AAAAA
 *       29 - httpmd://localhost/empty_file;MD5=AAAAA,comments
 *       30 - httpmd://localhost:1111/empty_file;MD5=AAAAA,comments
 *       31 - httpmd://localhost/empty_file;MD5=AAAAA?query=qqq
 *       32 - httpmd://localhost:1111/empty_file;MD5=AAAAA?query=qqq
 *       33 - httpmd://localhost/empty_file;MD5=AAAAA,comments?query=qqq
 *       34 - httpmd://localhost:1111/empty_file;MD5=AAAAA,comments?query=qqq
 *       35 - httpmd://localhost/empty_file;MD5=AAAAA#Ref
 *       36 - httpmd://localhost:1111/empty_file;MD5=AAAAA#Ref
 *       37 - httpmd://localhost/empty_file;MD5=AAAAA,comments#Ref
 *       38 - httpmd://localhost:1111/empty_file;MD5=AAAAA,comments#Ref
 *       39 - httpmd://localhost/empty_file;md5=AAAAA httpmd:/empty_file;md5=AAAAA
 *       40 - httpmd://localhost:1111/empty_file;md5=AAAAA httpmd:/empty_file;md5=AAAAA
 *       41 - httpmd://localhost/empty_file;md5=AAAAA,comments httpmd:/empty_file;md5=AAAAA
 *       42 - httpmd://localhost:1111/empty_file;md5=AAAAA,comments httpmd:/empty_file;md5=AAAAA
 *        The codebase updated to include computed digests with
 *        {@link HttpmdUtil#computeDigestCodebase(String,String)} method is compared
 *        with the expected one.
 *        (computeDigestCodebase2_*)
 *
 *     3. relative codebase is used:
 *        the message digest is calculated for non-empty .jar file
 *          (source directory: ${com.sun.jini.qa.jars}):
 *        codebases:
 *        1 - httpmd:qa1-httpmd.jar;MD5=AAAAA
 *        2 - httpmd:qa1-httpmd.jar;MD5=AAAAA,comments
 *        3 - httpmd:qa1-httpmd.jar;md5=AAAAA
 *        4 - httpmd:qa1-httpmd.jar;md5=AAAAA,comments
 *        5 - httpmd:qa1-httpmd.jar;SHA=AAAAA
 *        6 - httpmd:qa1-httpmd.jar;SHA=AAAAA,comments
 *        7 - httpmd:qa1-httpmd.jar;sha=AAAAA
 *        8 - httpmd:qa1-httpmd.jar;sha=AAAAA,comments
 *        9 - httpmd:qa1-httpmd.jar;MD5=AAAAA?query=qqq
 *       10 - httpmd:qa1-httpmd.jar;MD5=AAAAA,comments?query=qqq
 *       11 - httpmd:qa1-httpmd.jar;md5=AAAAA?query=qqq
 *       12 - httpmd:qa1-httpmd.jar;md5=AAAAA,comments?query=qqq
 *       13 - httpmd:qa1-httpmd.jar;SHA=AAAAA?query=qqq
 *       14 - httpmd:qa1-httpmd.jar;SHA=AAAAA,comments?query=qqq
 *       15 - httpmd:qa1-httpmd.jar;sha=AAAAA?query=qqq
 *       16 - httpmd:qa1-httpmd.jar;sha=AAAAA,comments?query=qqq
 *       17 - httpmd:qa1-httpmd.jar;MD5=AAAAA#Ref
 *       18 - httpmd:qa1-httpmd.jar;MD5=AAAAA,comments#Ref
 *       19 - httpmd:qa1-httpmd.jar;md5=AAAAA#Ref
 *       20 - httpmd:qa1-httpmd.jar;md5=AAAAA,comments#Ref
 *       21 - httpmd:qa1-httpmd.jar;SHA=AAAAA#Ref
 *       22 - httpmd:qa1-httpmd.jar;SHA=AAAAA,comments#Ref
 *       23 - httpmd:qa1-httpmd.jar;sha=AAAAA#Ref
 *       24 - httpmd:qa1-httpmd.jar;sha=AAAAA,comments#Ref
 *       25 - httpmd:qa1-httpmd.jar;md5=AAAAA httpmd:qa1-httpmd-another.jar;md5=AAAAA
 *       26 - httpmd:qa1-httpmd.jar;md5=AAAAA,comments httpmd:qa1-httpmd-another.jar;md5=AAAAA
 *       27 - httpmd:qa1-httpmd.jar;md5=AAAAA,comments httpmd:/qa1-httpmd-another.jar;md5=AAAAA
 *        The codebase updated to include computed digests with
 *        {@link HttpmdUtil#computeDigestCodebase(String,String)} method is compared
 *        with the expected one.
 *        (computeDigestCodebase3_*)
 *
 *     4. relative codebase is used:
 *        the message digest is calculated for an empty file:
 *          (source directory: current working directory):
 *        codebases:
 *        1 - httpmd:empty_file;MD5=AAAAA
 *        2 - httpmd:empty_file;MD5=AAAAA,comments
 *        3 - httpmd:empty_file;md5=AAAAA
 *        4 - httpmd:empty_file;md5=AAAAA,comments
 *        5 - httpmd:empty_file;SHA=AAAAA
 *        6 - httpmd:empty_file;SHA=AAAAA,comments
 *        7 - httpmd:empty_file;sha=AAAAA
 *        8 - httpmd:empty_file;sha=AAAAA,comments
 *        9 - httpmd:empty_file;MD5=AAAAA?query=qqq
 *       10 - httpmd:empty_file;MD5=AAAAA,comments?query=qqq
 *       11 - httpmd:empty_file;md5=AAAAA?query=qqq
 *       12 - httpmd:empty_file;md5=AAAAA,comments?query=qqq
 *       13 - httpmd:empty_file;SHA=AAAAA?query=qqq
 *       14 - httpmd:empty_file;SHA=AAAAA,comments?query=qqq
 *       15 - httpmd:empty_file;sha=AAAAA?query=qqq
 *       16 - httpmd:empty_file;sha=AAAAA,comments?query=qqq
 *       17 - httpmd:empty_file;MD5=AAAAA#Ref
 *       18 - httpmd:empty_file;MD5=AAAAA,comments#Ref
 *       19 - httpmd:empty_file;md5=AAAAA#Ref
 *       20 - httpmd:empty_file;md5=AAAAA,comments#Ref
 *       21 - httpmd:empty_file;SHA=AAAAA#Ref
 *       22 - httpmd:empty_file;SHA=AAAAA,comments#Ref
 *       23 - httpmd:empty_file;sha=AAAAA#Ref
 *       24 - httpmd:empty_file;sha=AAAAA,comments#Ref
 *       25 - httpmd:empty_file;md5=AAAAA httpmd:empty_file;md5=AAAAA
 *       26 - httpmd:empty_file;md5=AAAAA,comments httpmd:empty_file;md5=AAAAA
 *       27 - httpmd:empty_file;md5=AAAAA,comments httpmd:/empty_file;md5=AAAAA
 *        The codebase updated to include computed digests with
 *        {@link HttpmdUtil#computeDigestCodebase(String,String)} method is compared
 *        with the expected one.
 *        (computeDigestCodebase4_*)
 *
 *     5. codebase contains non-HTTPMD URL;
 *        java.lang.IllegalArgumentException is expected;
 *        (source directory: ${com.sun.jini.qa.jars});
 *        codebases:
 *        1 - qa1-httpmd.jar;md5=AAAAA
 *        2 - /qa1-httpmd.jar;md5=AAAAA
 *        3 - file:qa1-httpmd.jar;md5=AAAAA
 *        4 - file:/qa1-httpmd.jar;md5=AAAAA
 *        5 - http:qa1-httpmd.jar;md5=AAAAA
 *        6 - http:/qa1-httpmd.jar;md5=AAAAA
 *        7 - http:/qa1-httpmd.jar;md5=AAAAA httpmd:/qa1-httpmd.jar;md5=AAAAA
 *        8 - http:qa1-httpmd.jar;md5=AAAAA httpmd:qa1-httpmd.jar;md5=AAAAA
 *        9 - httpmd:/qa1-httpmd.jar;md5=AAAAA http:/qa1-httpmd.jar;md5=AAAAA
 *       10 - httpmd:qa1-httpmd.jar;md5=AAAAA http:qa1-httpmd.jar;md5=AAAAA
 *       11 - file:/qa1-httpmd.jar;md5=AAAAA httpmd:/qa1-httpmd.jar;md5=AAAAA
 *       12 - file:qa1-httpmd.jar;md5=AAAAA httpmd:qa1-httpmd.jar;md5=AAAAA
 *       13 - httpmd:/qa1-httpmd.jar;md5=AAAAA file:/qa1-httpmd.jar;md5=AAAAA
 *       14 - httpmd:qa1-httpmd.jar;md5=AAAAA file:qa1-httpmd.jar;md5=AAAAA
 *        (computeDigestCodebase5_*)
 *
 *     6. codebase is incorrect; (ComputeDigestCodebase5_*)
 *        java.io.IOException is expected;
 *        (source directory: current working directory);
 *        codebases:
 *        1 - httpmd:non-exist;md5=AAAAA (represents non-existent file)
 *        2 - httpmd:/non-exist;md5=AAAAA (represents non-existent file)
 *        3 - httpmd://localhost/non-exist;md5=AAAAA (represents non-existent file)
 *        4 - httpmd://localhost:1111/non-exist;md5=AAAAA (represents non-existent file)
 *        (computeDigestCodebase6_*)
 *
 *     7. incorrect source directory;
 *        java.io.IOException is expected;
 *        codebase: httpmd:qa1-httpmd.jar;md5=AAAAA
 *        1 -  source directory isn't directory
 *             (source directory: ${com.sun.jini.qa.jars}/qa1-httpmd.jar)
 *        2 -  non-existent source directory (source directory: /non-exist-dir)
 *        (computeDigestCodebase7_*)
 *
 *     8. codebase has incorrect syntax;
 *        java.net.MalformedURLException is expected;
 *        (source directory: ${com.sun.jini.qa.jars});
 *        codebases:
 *        1 - httpmd:qa1-httpmd.jar
 *        2 - httpmd:qa1-httpmd.jar,comments
 *        3 - httpmd:/qa1-httpmd.jar
 *        4 - httpmd:/qa1-httpmd.jar,comments
 *        5 - httpmd:qa1-httpmd.jar;
 *        6 - httpmd:qa1-httpmd.jar;,comments
 *        7 - httpmd:/qa1-httpmd.jar;
 *        8 - httpmd:/qa1-httpmd.jar;,comments
 *        9 - httpmd:qa1-httpmd.jar;md5
 *       10 - httpmd:qa1-httpmd.jar;md5,comments
 *       11 - httpmd:/qa1-httpmd.jar;md5
 *       12 - httpmd:/qa1-httpmd.jar;md5,comments
 *       13 - httpmd:qa1-httpmd.jar;md5=
 *       14 - httpmd:qa1-httpmd.jar;md5=,comments
 *       15 - httpmd:/qa1-httpmd.jar;md5=
 *       16 - httpmd:/qa1-httpmd.jar;md5=,comments
 *        (computeDigestCodebase8_*)
 *
 *     9. codebase is null;
 *        (source directory: ${com.sun.jini.qa.jars});
 *        java.lang.NullPointerException is expected;
 *        (computeDigestCodebase9_*)
 *
 *    10. source directory is null;
 *        codebase: httpmd:/qa1-httpmd.jar;md5=AAAAA,comments
 *        java.lang.NullPointerException is expected;
 *        (computeDigestCodebase10_*)
 *
 *    11. source directory is null; codebase is null;
 *        java.lang.NullPointerException is expected;
 *        (computeDigestCodebase11_*)
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     - ComputeDigestCodebase.TestItem
 *         auxiliary class that describes a Test Case
 *     - ComputeDigestCodebase
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD urls creating
 *       ({@link Handler} is used as HTTPMD Protocol handler)
 *   Test performs the following steps in each test case:
 *     - getting test parameters,
 *     - creating an empty file,
 *     - invoking {@link HttpmdUtil#computeDigestCodebase(String,String)},
 *     - comparing the codebase updated to include computed digests with
 *       {@link HttpmdUtil#computeDigestCodebase(String,String)} method
 *       or an Exception thrown by {@link HttpmdUtil#computeDigestCodebase(String,String)}
 *       method with the expected codebase with corrected message digest value or an expected Exception.
 *
 * </pre>
 */
public class ComputeDigestCodebase extends QATest {
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
     * Getting Test Cases names.
     *
     * @return Test Cases names
     */
    public String[] getTestCaseNames() {
        String cName =
                getTestClassName().substring(getTestClassName().lastIndexOf(".")
                + 1);
        int i = 1;
        StringBuffer sb = new StringBuffer();

        while (true) {
            int num = config.getIntConfigVal(cName + ".testCases" + "." + i, 0);

            if (num == 0) {
                break;
            }

            for (int j = 1; j <= num; j++) {
                sb.append(cName + "." + i + "." + j + " ");
            }
            i++;
        }
        sb.setLength(sb.length() - 1);
        return sb.toString().split(" ");
    }

    /**
     * <pre>
     * This method performs all preparations.
     * These preparations include the following:
     *  - getting test parameters,
     *  - creating an empty file.
     * Test parameters:
     *    ComputeDigestCodebase.testCases - Test Cases names
     *    &lt;TestCaseName&gt;.SrcDir     - source directory
     *    &lt;TestCaseName&gt;.CodeBase   - codebase
     *    &lt;TestCaseName&gt;.Expected   - expected result of
     *    {@link HttpmdUtil#computeDigestCodebase(String,String)}
     *                                      method (null if the updated codebase
     *                                      is expected or Exception otherwise)
     * </pre>
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Creating an empty file (is deleted when VM exits) */
        File emptyFile = new File(System.getProperty("java.io.tmpdir")
                + System.getProperty("file.separator") + "empty_file");
        emptyFile.createNewFile();
        emptyFile.deleteOnExit();

        /* Getting test parameters and creating TestItem objects */
        String[] tc_names = getTestCaseNames();

        for (int i = 0; i < tc_names.length; i++) {
            items.add(i, new TestItem(tc_names[i]));
        }
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
     * Updating codebase with
     * {@link HttpmdUtil#computeDigestCodebase(String,String)}
     * method and comparing the updated codebase (or Exception) with expected
     * result.
     *
     * @param ti TestItem object that descibes a Test Case
     * @return true (if the updated codebase (or Exception) is equal to
     *         the expected one) or false otherwise
     */
    public boolean checker(TestItem ti) {
        logger.log(Level.FINE,
                "HttpmdUtil.computeDigestCodebase(" + ti.getSrcDir() + ", "
                + ti.getCodeBase() + ") ");

        try {
            String ret = HttpmdUtil.computeDigestCodebase(ti.getSrcDir(),
                    ti.getCodeBase());
            return ti.checker(ret);
        } catch (Exception e) {
            return ti.checker(e);
        }
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
         * Source Directory.
         * The value is specified by &lt;TestCaseName&gt;.SrcDir test property.
         */
        protected String srcDir;

        /**
         * Codebase.
         * The value is specified by &lt;TestCaseName&gt;.CodeBase test
         * property.
         */
        protected String codeBase;

        /**
         * Expected Codebase.
         * The value is calculated from &lt;TestCaseName&gt;.Expected test
         * property.
         */
        protected String expectedCodebase;

        /**
         * Expected Exception Class.
         * The value is calculated from &lt;TestCaseName&gt;.Expected test
         * property.
         */
        protected Class expectedClass;

        /**
         * Creating TestItem object (Constructor)
         * Test parameters:
         *    ComputeDigestCodebase.testCases - Test Cases names
         *    &lt;TestCaseName&gt;.SrcDir     - source directory
         *    &lt;TestCaseName&gt;.CodeBase   - codebase
         *    &lt;TestCaseName&gt;.Expected   - expected result of
         * {@link HttpmdUtil#computeDigestCodebase(String,String)}
         *                                      method (null if the updated codebase
         *                                      codebase is expected or Exception
         *                                      otherwise)
         *
         * @param tcname Test Case name
         * @throws Exception if any exception occured while TestItem object
         * creation
         */
        public TestItem(String tcname) throws Exception {
            final String pat = "AAAAA";
            int ind = tcname.indexOf('.');
            String tc = tcname.substring(0, ind);
            String num = tcname.substring(ind + 1);
            testCaseName = tcname;
            srcDir = replacePound(config.getStringConfigVal(tc + ".SrcDir."
                    + num, null));
            codeBase = replacePound(config.getStringConfigVal(tc + ".CodeBase."
                    + num, null));
            String exp = replacePound(config.getStringConfigVal(tc
                    + ".Expected." + num, null));

            if (exp == null) {

                /*
                 * The codebase updated to include computed digests with
                 * {@link HttpmdUtil#computeDigestCodebase(String,String)}
                 * method is expected
                 */
                StringBuffer expCodebase = new StringBuffer();
                String[] codeBases = codeBase.split(" ");

                for (int i = 0; i < codeBases.length; i++) {
                    String cbase = codeBases[i];

                    /* Getting message digest algorithm from the codebase */
                    String algorithm = cbase.substring(cbase.indexOf(";") + 1,
                            cbase.indexOf("="));

                    /* Getting filename */
                    int indColon = cbase.indexOf(":");
                    int indSlash = cbase.lastIndexOf("/");
                    int indSemiColon = cbase.indexOf(";");
                    int indBegin = ((indColon > indSlash) ? indColon : indSlash) + 1;
                    int indEnd = indSemiColon;
                    String filename = cbase.substring(indBegin, indEnd);

                    /* Computing message digest */
                    String md = computeMD(srcDir
                            + System.getProperty("file.separator") + filename,
                            algorithm);

                    /* Correcting message digest in codebase */
                    String ccbase = cbase.replaceFirst(pat, md);
                    expCodebase.append(((i == 0) ? "" : " ") + ccbase);
                }
                expectedCodebase = expCodebase.toString();
            } else {

                /*
                 * The Exception thrown by
                 * {@link HttpmdUtil#computeDigestCodebase(String,String)}
                 * method is expected.
                 */
                expectedClass = Class.forName(exp);
            }
        }

        /**
         * Computing message digest for the file.
         *
         * @param pathname   pathname
         * @param alg        message digest algorithm
         * @throws Exception if the message digest can't be computed
         *
         * @return message digest as a String in hexadecimal format or null
         *         if file doesn't exist
         */
        protected String computeMD(String pathname, String alg)
                throws Exception {
            final int BUFSIZE = 2048;
            MessageDigest md;

            /* Checking existence of the file */
            File file = new File(pathname);

            /* Computing message digest for the file */
            md = MessageDigest.getInstance(alg);
            FileInputStream fin = new FileInputStream(file);
            BufferedInputStream bin = new BufferedInputStream(fin, BUFSIZE);
            DigestInputStream in = new DigestInputStream(bin, md);
            in.on(true);
            byte[] buf = new byte[BUFSIZE];
            byte[] mdbyte = new byte[BUFSIZE];

            while (true) {
                int n = in.read(buf, 0, buf.length);

                if (n < 0) {
                    break;
                }
            }
            md = in.getMessageDigest();
            mdbyte = md.digest();
            return digestString(mdbyte);
        }

        /**
         * Converts a message digest to a String in hexadecimal format.
         *
         * @param digest message digest
         *
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
         * Compare a codebase with the expected one.
         *
         * @param cb codebase
         * @return result of comparison (true (if cb is equal to the
         *         expected codebase) or false)
         */
        public boolean checker(String cb) {
            logger.log(Level.FINE, "Expected Codebase: " + expectedCodebase);
            logger.log(Level.FINE, "Returned Codebase: " + cb);

            if (cb.equals(expectedCodebase)) {
                return true;
            }
            return false;
        }

        /**
         * Compare a Exception with the expected one.
         *
         * @param e Exception
         * @return result of comparison (true or false)
         */
        public boolean checker(Exception e) {
            Class returnedClass = e.getClass();
            logger.log(Level.FINE, "Expected Class: " + expectedClass);
            logger.log(Level.FINE, "Returned Class: " + returnedClass);

            if (expectedClass.isAssignableFrom(returnedClass)) {
                return true;
            }
            return false;
        }

        /**
         * Replacing &lt;PoundSign&gt; with # sign.
         *
         * @param from    string
         *
         * @return String object with &lt;PoundSign&gt; replaced with # sign
         */
        public String replacePound(String from) {
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
         * This method returns source directory.
         *
         * @return the source directory
         */
        public String getSrcDir() {
            return srcDir;
        }

        /**
         * This method returns codebase.
         *
         * @return the codebase
         */
        public String getCodeBase() {
            return codeBase;
        }
    }
}
