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

package org.apache.river.test.spec.config.configurationfile;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;
import net.jini.config.ConfigurationFile;
import net.jini.config.AbstractConfiguration.Primitive;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import org.apache.river.test.spec.config.util.TestComponent;
import org.apache.river.test.spec.config.util.DefaultTestComponent;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getSpecialEntryType method of
 *   ConfigurationFile class.
 *
 * Test Cases:
 *   a) This test contains six test cases:
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
 *   b) Each test case contains three test subcases:
 *    a subcase when tested entry (if exists) is placed in file,
 *    a subcase when tested entry (if exists) is placed in URL,
 *    a subcase when tested entry (if exists) is placed in overriding
 *      remaining options,
 *
 * Actions:
 *   Test checks set of assertions and performs the following steps for that:
 *    1) The default implementation always throws NoSuchEntryException.
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import org.apache.river.test.spec.config.util.TestComponent;
 *         org.apache.river.test.spec.config.util.TestComponent {
 *             $entry = new TestComponent($data);
 *         }
 *       call getSpecialEntryType method from this object passing
 *       "entry" as name argument;
 *       assert that NoSuchEntryException is thrown;
 *    2) null as entry name.
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import org.apache.river.test.spec.config.util.TestComponent;
 *         org.apache.river.test.spec.config.util.TestComponent {
 *             $entry = new TestComponent($data);
 *         }
 *       call getSpecialEntryType method from this object passing
 *       null as name argument;
 *       assert that NoSuchEntryException is thrown;
 * </pre>
 */
public class GetSpecialEntryType_Test extends Template_Test {

    /**
     * Create new FakeConfigurationFile object according to
     * testCase, testSubCase.
     *
     * @param testCase test case according to testCases
     * @param testSubCase test subcase according to testSubCases
     * @param entryLine line of config file source or overriding properties
     * @param confHeader header of config file source or null for default
     * @return instance of created ConfigurationFile object
     *
     */
    protected FakeConfigurationFile createCF(
            Object testCase,
            Object testSubCase,
            String entryLine,
            String confHeader)
            throws Exception {
        String defaultConfHeader =
                "import org.apache.river.test.spec.config.util.TestComponent;\n"
                + "org.apache.river.test.spec.config.util.TestComponent {\n";
        if (confHeader != null) {
            defaultConfHeader = confHeader;
        }
        if (testSubCase == OPT_FILE_SUBCASE) {
            String conf =
                defaultConfHeader
                + entryLine
                + ";\n}\n";
            createFile(confFile, conf);
            String[] optionsWithFile = { confFile.getPath() };
            return (FakeConfigurationFile) callConstructor(
                    testCase, confFile, optionsWithFile);
        } else if (testSubCase == OPT_URL_SUBCASE) {
            String conf =
                defaultConfHeader
                + entryLine
                + ";\n}\n";
            createFile(confFile, conf);
            URL confFileURL = confFile.toURI().toURL();
            String[] optionsWithURL = { confFileURL.toString() };
            return (FakeConfigurationFile) callConstructor(
                    testCase, confFileURL, optionsWithURL);
        } else { // if (testSubCase == OPT_OVERRIDE_SUBCASE) {
            String conf =
                defaultConfHeader
                + "}\n";
            createFile(confFile, conf);
            String[] optionsWithOverride = {
                    confFile.getPath(),
                    "org.apache.river.test.spec.config.util.TestComponent."
                    + entryLine };
            return (FakeConfigurationFile) callConstructor(
                    testCase, confFile, optionsWithOverride);
        }
    }

    /**
     * Start test sub case.
     */
    public void runSubCase(Object testCase, Object testSubCase)
            throws Exception {
        logger.log(Level.INFO, "--> " + testCase.toString()
                + " " + testSubCase.toString());

        String conf = null;
        String[] optionsWithFile = { confFile.getPath() };
        
        // 1 - simple case
        FakeConfigurationFile configurationFile = createCF(
                testCase,
                testSubCase,
                "$entry = new TestComponent($data)",
                null);
        try {
            configurationFile.getSpecialEntryType("entry");
            throw new TestException(
                "NoSuchEntryException should be thrown");
        } catch (NoSuchEntryException ignore) {
        }
        
        // 2 - null case
        try {
            configurationFile.getSpecialEntryType(null);
            throw new TestException(
                "NoSuchEntryException should be thrown");
        } catch (NoSuchEntryException ignore) {
        }

        confFile.delete();
    }

    /**
     * Start test case.
     */
    public void runCase(Object testCase) throws Exception {
        for (int i = 0; i < testSubCases.length; ++i) {
            runSubCase(testCase, testSubCases[i]);
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < testCases.length; ++i) {
            runCase(testCases[i]);
        }
    }
}
