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
import org.apache.river.test.spec.config.util.InterfaceTestComponent;
import org.apache.river.test.spec.config.util.AbstractTestComponent;
import org.apache.river.test.spec.config.util.TestComponent;
import org.apache.river.test.spec.config.util.DefaultTestComponent;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getEntryType method of
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
 *   1) Returns the static type of the expression specified for the entry with
 *      the specified component and name.
 *   Steps:
 *   a) construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = new TestComponent();
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "entry" as name arguments;
 *      assert that valid type (TestComponent.class) is returned;
 *   b) construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = TestComponent.getInt($data);
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "entry" as name arguments;
 *      assert that valid type (int.class) is returned;
 *      repeat this test for all primitive types;
 *   2) return ... <code>null</code> if the value of the entry is the
 *      <code>null</code> literal.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = null;
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "entry" as name arguments;
 *      assert that null is returned;
 *   3) throws NoSuchEntryException if no matching entry is found.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = null;
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "unexistedEntry" as name arguments;
 *      assert that NoSuchEntryException is thrown;
 *   4) ConfigurationException if a matching entry is found but a
 *      problem occurs determining the type of the entry.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = new UnexistedType();
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "entry" as name arguments;
 *      assert that ConfigurationException is thrown;
 *   5) IllegalArgumentException if <code>component</code> is not
 *      <code>null</code> and is not a valid <i>QualifiedIdentifier</i>.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = new TestComponent();
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.#%^&" as component and "entry" as name arguments;
 *      assert that IllegalArgumentException is thrown;
 *   6) IllegalArgumentException if ... <code>name</code> is not
 *      <code>null</code> and is not a valid <i>Identifier</i>.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = new TestComponent();
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "#%^&" as name arguments;
 *      assert that IllegalArgumentException is thrown;
 *   7) NullPointerException if either argument is <code>null</code>.
 *   Steps:
 *     a) construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = new TestComponent();
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and null as name arguments;
 *      assert that NullPointerException is thrown;
 *     b) construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = new TestComponent();
 *        };
 *      call getEntryType method from this object passing
 *      null as component and "entry" as name arguments;
 *      assert that NullPointerException is thrown;
 *   8) test that the return value is the static type of the entry,
 *      not the runtime type.
 *   Steps:
 *      adjust the TestComponent staticMethod so that if shoud return
 *      TestComponent accessor:
 *          TestComponent.staticEntry = new DefaultTestComponent();
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry = TestComponent.staticMethod();
 *        };
 *      call getEntryType method from this object passing
 *      "org.apache.river.test.spec.config.util.TestComponent" as component
 *      and "entry" as name arguments;
 *      assert that valid static type (TestComponent.class) not the
 *      runtime type (DefaultTestComponent.class) is returned;
 *      repeat test for interface and abstract class;
 * </pre>
 */
public class GetEntryType_Test extends Template_Test {


    /**
     * Table of test cases for all primitive classes.
     * Structure: config content, type, data value
     */
    final static Object[] [] primitiveCases = {
        {   "entry = TestComponent.getBoolean($data)",
            boolean.class,
            new Boolean(true)
        },
        {   "entry = TestComponent.getByte($data)",
            byte.class,
            new Byte((byte) 5)
        },
        {   "entry = TestComponent.getChar($data)",
            char.class,
            new Character('f')
        },
        {   "entry = TestComponent.getShort($data)",
            short.class,
            new Short((short) 11222)
        },
        {   "entry = TestComponent.getInt($data)",
            int.class,
            new Integer(1222333)
        },
        {   "entry = TestComponent.getLong($data)",
            long.class,
            new Long(111222333444L)
        },
        {   "entry = TestComponent.getFloat($data)",
            float.class,
            new Float(1.5f)
        },
        {   "entry = TestComponent.getDouble($data)",
            double.class,
            new Double(2.5d)
        }
    };

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
        
        // 1a - simple case
        FakeConfigurationFile configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        Object data = new DefaultTestComponent();
        Class result = configurationFile.getEntryType(
                "org.apache.river.test.spec.config.util.TestComponent",
                "entry");
        if (result != TestComponent.class) {
            throw new TestException("Invalid type was returned");
        }

        // 1b - primitive types
        for (int j = 0; j < primitiveCases.length; ++j) {
            Object[] subCase = primitiveCases[j];
            String entryLine = (String) subCase[0];
            Class type = (Class) subCase[1];
            data = subCase[2];
            configurationFile = createCF(
                    testCase,
                    testSubCase,
                    entryLine,
                    null);
            result = configurationFile.getEntryType(
                    "org.apache.river.test.spec.config.util.TestComponent",
                    "entry");
            if (result != type) {
                throw new TestException(
                        "Result type " + result
                        + " is not valid, expected type is " + type);
            }
        }

        // 2 - null case
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = null",
                null);
        result = configurationFile.getEntryType(
                "org.apache.river.test.spec.config.util.TestComponent",
                "entry");
        if (result != null) {
            throw new TestException("null should be returned");
        }

        // 3 - unexisted entry
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        try {
            result = configurationFile.getEntryType(
                    "org.apache.river.test.spec.config.util.TestComponent",
                    "unexistEntry");
            throw new TestException(
                    "NoSuchEntryException should be thrown if"
                    + " no such entry exists");
        } catch (NoSuchEntryException ignore) {
        }

        // 4 - unexisted type
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new UnexistedType()",
                null);
        try {
            result = configurationFile.getEntryType(
                    "org.apache.river.test.spec.config.util.TestComponent",
                    "entry");
            throw new TestException(
                    "ConfigurationException should be thrown if a matching"
                    + " entry is found but a problem occurs determining"
                    + " the type of the entry");
        } catch (ConfigurationException ignore) {
        }

        // 5 - illegal component
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        try {
            result = configurationFile.getEntryType(
                    "org.apache.river.#%^&",
                    "entry");
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " no such entry exists");
        } catch (IllegalArgumentException ignore) {
        }

        // 6 - illegal entry name
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        try {
            result = configurationFile.getEntryType(
                    "org.apache.river.test.spec.config.util.TestComponent",
                    "#%^&");
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " no such entry exists");
        } catch (IllegalArgumentException ignore) {
        }

        // 7a - name is null
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        try {
            result = configurationFile.getEntryType(
                    "org.apache.river.test.spec.config.util.TestComponent",
                    null);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " no such entry exists");
        } catch (NullPointerException ignore) {
        }

        // 7b - component is null
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        try {
            result = configurationFile.getEntryType(
                    null,
                    "entry");
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " no such entry exists");
        } catch (NullPointerException ignore) {
        }

        // 8a - test that the return value is the static type
        TestComponent.staticEntry = new DefaultTestComponent();
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = TestComponent.staticMethod()",
                null);
        result = configurationFile.getEntryType(
                "org.apache.river.test.spec.config.util.TestComponent",
                "entry");
        if (result != TestComponent.class) {
            throw new TestException("Invalid type was returned");
        }

        // 8b - test that the return value is the static type
        TestComponent.staticEntry = new DefaultTestComponent();
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = TestComponent.getInterfaceTestComponent()",
                null);
        result = configurationFile.getEntryType(
                "org.apache.river.test.spec.config.util.TestComponent",
                "entry");
        if (result != InterfaceTestComponent.class) {
            throw new TestException("Invalid type was returned");
        }

        // 8c - test that the return value is the abstract type
        TestComponent.staticEntry = new DefaultTestComponent();
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = TestComponent.getAbstractTestComponent()",
                null);
        result = configurationFile.getEntryType(
                "org.apache.river.test.spec.config.util.TestComponent",
                "entry");
        if (result != AbstractTestComponent.class) {
            throw new TestException("Invalid type was returned");
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
