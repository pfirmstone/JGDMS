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
import java.util.Set;
import java.util.HashSet;
import java.net.URL;
import net.jini.config.ConfigurationFile;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getEntryNames method of
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
 *   1) private components.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with the pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry1 = new TestComponent();
 *            static entry2 = new TestComponent();
 *            private entry3 = new TestComponent();
 *            private static entry4 = new TestComponent();
 *        }
 *      call getEntryNames method;
 *      assert that valid set (containing entry1 and entry2) is returned;
 *   2) several components.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry1 = new TestComponent();
 *        }
 *        org.apache.river.test.spec.config.util.DefaultTestComponent {
 *            entry2 = new Integer(1);
 *        }
 *      call getEntryNames method;
 *      assert that valid set (containing entry1 and entry2) is returned;
 *   3) call getEntryNames more than ones.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry1 = new TestComponent();
 *            entry2 = "Some string";
 *        }
 *      call getEntryNames method;
 *      assert that valid set (containing entry1 and entry2) is
 *      returned;
 *      call getEntryNames method again;
 *      assert that valid set (containing entry1 and entry2) is
 *      returned;
 *   4) several instances of the same component.
 *   Steps:
 *      construct a ConfigurationFile object passing options
 *      with the valid file name with pointed content as a first element;
 *      The content is:
 *        import org.apache.river.test.spec.config.util.TestComponent;
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry1 = new TestComponent();
 *        }
 *        org.apache.river.test.spec.config.util.DefaultTestComponent {
 *            entry2 = new Integer(1);
 *        }
 *        org.apache.river.test.spec.config.util.TestComponent {
 *            entry3 = "Some string";
 *        }
 *      call getEntryNames method;
 *      assert that valid set (containing entry1, entry2 and entry3) is
 *      returned;
 * </pre>
 */
public class GetEntryNames_Test extends Template_Test {

    /**
     * Test actions description.
     *
     * Structure:
     * configuration file source,
     * number of runs,
     * list of expected result entry names.
     */
    Object[] [] testActions = new Object[] [] {
        {     "import org.apache.river.test.spec.config.util.TestComponent;\n"
            + "org.apache.river.test.spec.config.util.TestComponent {\n"
            + "    entry1 = new TestComponent();\n"
            + "    static entry2 = new TestComponent();\n"
            + "    private entry3 = new TestComponent();\n"
            + "    private static entry4 = new TestComponent();\n"
            + "}\n",
            new Integer(1),
            "org.apache.river.test.spec.config.util.TestComponent.entry1",
            "org.apache.river.test.spec.config.util.TestComponent.entry2"
        },
        {     "import org.apache.river.test.spec.config.util.TestComponent;\n"
            + "org.apache.river.test.spec.config.util.TestComponent {\n"
            + "    entry1 = new TestComponent();\n"
            + "}\n"
            + "org.apache.river.test.spec.config.util.DefaultTestComponent {\n"
            + "    entry2 = new Integer(1);\n"
            + "}\n",
            new Integer(1),
            "org.apache.river.test.spec.config.util.TestComponent.entry1",
            "org.apache.river.test.spec.config.util.DefaultTestComponent.entry2"
        },
        {     "import org.apache.river.test.spec.config.util.TestComponent;\n"
            + "org.apache.river.test.spec.config.util.TestComponent {\n"
            + "    entry1 = new TestComponent();\n"
            + "    entry2 = new Integer(1);\n"
            + "}\n",
            new Integer(2),
            "org.apache.river.test.spec.config.util.TestComponent.entry1",
            "org.apache.river.test.spec.config.util.TestComponent.entry2"
        },
        {     "import org.apache.river.test.spec.config.util.TestComponent;\n"
            + "org.apache.river.test.spec.config.util.TestComponent {\n"
            + "    entry1 = new TestComponent();\n"
            + "}\n"
            + "org.apache.river.test.spec.config.util.DefaultTestComponent {\n"
            + "    entry2 = new Integer(1);\n"
            + "}\n"
            + "org.apache.river.test.spec.config.util.TestComponent {\n"
            + "    entry3 = \"Some string\";\n"
            + "}\n",
            new Integer(1),
            "org.apache.river.test.spec.config.util.TestComponent.entry1",
            "org.apache.river.test.spec.config.util.DefaultTestComponent.entry2",
            "org.apache.river.test.spec.config.util.TestComponent.entry3"
        }
    };
    
    /**
     * Start test sub case.
     */
    public void runSubCase(Object testCase, Object testSubCase)
            throws Exception {
        logger.log(Level.INFO, "--> " + testCase.toString()
                + " " + testSubCase.toString());
        Set expectedENs = null;
        for (int j = 0; j < testActions.length; ++j) {
            Object[] testAction = testActions[j];
            String conf = (String) testAction[0];
            String entryOverride =
                    "org.apache.river.test.spec.config.util.TestComponent.entry5";
            expectedENs = new HashSet();
            for (int k = 2; k < testAction.length; ++k) {
                expectedENs.add((String) testAction[k]);
            }
            if (testSubCase == OPT_OVERRIDE_SUBCASE) {
                 expectedENs.add(entryOverride);
            }
            
            createFile(confFile, conf);
            String[] options = null;
            if (testSubCase == OPT_FILE_SUBCASE) {
                String[] optionsWithFile = { confFile.getPath() };
                options = optionsWithFile;
            } else if (testSubCase == OPT_URL_SUBCASE) {
                URL confFileURL = confFile.toURI().toURL();
                String[] optionsWithURL = { confFileURL.toString() };
                options = optionsWithURL;
            } else { // if (testSubCase == OPT_OVERRIDE_SUBCASE) {
                String[] optionsWithOverride = {
                        confFile.getPath(),
                        entryOverride + " = new Object()\n" };
                options = optionsWithOverride;
            }

            ConfigurationFile configurationFile =
                    callConstructor(testCase, confFile, options);
                    
            int numberOfRuns = ((Integer) testAction[1]).intValue();
            for (int l = 0; l < numberOfRuns; ++l) { // more then once
                Set ens = configurationFile.getEntryNames();

                if (!ens.containsAll(expectedENs)){
                    throw new TestException(
                            "Returned set contains not expected elements");
                }
                if (!expectedENs.containsAll(ens)){
                    throw new TestException(
                        "Returned set doesn't contains all expected elements");
                }
            }

            confFile.delete();
        }
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
