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
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;
import net.jini.config.ConfigurationFile;
import net.jini.config.AbstractConfiguration.Primitive;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import com.sun.jini.test.spec.config.util.TestComponent;
import com.sun.jini.test.spec.config.util.DefaultTestComponent;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getEntryInternal method of
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
 *    1) Returns an object created using the information in the entry matching
 *       the specified component and name, and the specified data, for the
 *       requested type.
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = new TestComponent($data);
 *         }
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that valid TestComponent object is returned;
 *       assert that data argument was passed to TestComponent
 *       constructor;
 *    2) If the entry value is a primitive, then the object
 *       returned is an instance of
 *       {@link Primitive AbstractConfiguration.Primitive}.
 *       This implementation uses <code>type</code> to perform conversions on
 *       primitive values.
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with valid content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = TestComponent.getInt($data);
 *         };
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, int.class as type,
 *       some new Integer instance as defaultValue,
 *       and new instance of Integer class as data arguments;
 *       assert that valid Primitive object is returned;
 *       assert that getValue call from this object is returned valid value;
 *       repeat this test for all primitive types;
 *    3) Throws: 
 *       NoSuchEntryException - if no matching entry is found 
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = new TestComponent($data);
 *         };
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "unexistEntry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that NoSuchEntryException is thrown;
 *    4) Throws: 
 *       NullPointerException - if component, ... is null 
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = new TestComponent($data);
 *         };
 *       call getEntryInternal method from this object passing
 *       null as component,
 *       "entry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that NullPointerException is thrown;
 *    5) Throws: 
 *       NullPointerException - if ... name ... is null 
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = new TestComponent($data);
 *         };
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       null as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that NullPointerException is thrown;
 *    6) Throws: 
 *       NullPointerException - if ... type is null 
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = new TestComponent($data);
 *         };
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, null as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that NullPointerException is thrown;
 *    7) Throws: 
 *       ConfigurationException - if a matching entry is found but
 *       a problem occurs creating the object for the entry;
 *     Steps:
 *       construct a ConfigurationFile object passing options
 *       with the valid file name with pointed content as a first element;
 *       The content is:
 *         import com.sun.jini.test.spec.config.util.TestComponent;
 *         com.sun.jini.test.spec.config.util.TestComponent {
 *             entry = TestComponent.throwException($data);
 *         };
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that ConfigurationException is thrown;
 * </pre>
 */
public class GetEntryInternal_Test extends Template_Test {

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
                "import com.sun.jini.test.spec.config.util.TestComponent;\n"
                + "com.sun.jini.test.spec.config.util.TestComponent {\n";
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
                    "com.sun.jini.test.spec.config.util.TestComponent."
                    + entryLine };
            return (FakeConfigurationFile) callConstructor(
                    testCase, confFile, optionsWithOverride);
        }
    }

    /**
     * Start test sub case. Actions see in class description.
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
                "entry = new TestComponent($data)",
                null);
        Object data = new DefaultTestComponent();
        Object result = configurationFile.getEntryInternal(
                "com.sun.jini.test.spec.config.util.TestComponent",
                "entry",
                TestComponent.class,
                data);
        if (!(result instanceof TestComponent)) {
            throw new TestException(
                    "Result is not the TestComponent class as was expected");
        }
        TestComponent dtc = (TestComponent) result;
        if (dtc.data != data) {
            throw new TestException(
                    "Data was not delivered properly");
        }

        // 2 - primitive types
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
            result = configurationFile.getEntryInternal(
                    "com.sun.jini.test.spec.config.util.TestComponent",
                    "entry",
                    type,
                    data);
            if (!(result instanceof Primitive)) {
                throw new TestException(
                        "Result is not the Primitive class as was expected");
            }
            Object value = ((Primitive)result).getValue();
            if (!value.equals(data)) {
                throw new TestException(
                    "getValue returnes invalid value "
                    + value + ", was expected " + data);
            }
        }

        // 3 - unexisted entry
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        data = new DefaultTestComponent();
        try {
            result = configurationFile.getEntryInternal(
                    "com.sun.jini.test.spec.config.util.TestComponent",
                    "unexistEntry",
                    TestComponent.class,
                    data);
            throw new TestException(
                    "NoSuchEntryException should be thrown if"
                    + " no such entry exists");
        } catch (NoSuchEntryException ignore) {
        }

        // 4 - component is null
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        data = new DefaultTestComponent();
        try {
            result = configurationFile.getEntryInternal(
                    null,
                    "entry",
                    TestComponent.class,
                    data);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " no such entry exists");
        } catch (NullPointerException ignore) {
        }

        // 5 - name is null
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        data = new DefaultTestComponent();
        try {
            result = configurationFile.getEntryInternal(
                    "com.sun.jini.test.spec.config.util.TestComponent",
                    null,
                    TestComponent.class,
                    data);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " no such entry exists");
        } catch (NullPointerException ignore) {
        }

        // 6 - type is null
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = new TestComponent($data)",
                null);
        data = new DefaultTestComponent();
        try {
            result = configurationFile.getEntryInternal(
                    "com.sun.jini.test.spec.config.util.TestComponent",
                    "entry",
                    null,
                    data);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " no such entry exists");
        } catch (NullPointerException ignore) {
        }

        // 7 - problem occurs creating the object for the entry
        configurationFile = createCF(
                testCase,
                testSubCase,
                "entry = TestComponent.throwException($data)",
                null);
        data = new DefaultTestComponent();
        try {
            result = configurationFile.getEntryInternal(
                    "com.sun.jini.test.spec.config.util.TestComponent",
                    "entry",
                    TestComponent.class,
                    data);
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " problem occurs creating the object for the entry");
        } catch (ConfigurationException ignore) {
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
