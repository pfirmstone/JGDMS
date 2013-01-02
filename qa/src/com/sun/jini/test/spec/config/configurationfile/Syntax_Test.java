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
import net.jini.config.Configuration;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import com.sun.jini.test.spec.config.util.TestComponent;
import com.sun.jini.test.spec.config.util.TestComponent.InternalTestComponent;
import com.sun.jini.test.spec.config.util.DefaultTestComponent;

/**
 * <pre>
 * Purpose:
 *   This test verifies the correctness of the syntax analysis of
 *   a configuration source of ConfigurationFile class.
 *
 * Actions:
 *   Test checks set of valid and broken syntax constructions and performs
 *   usually the following steps for that:
 *    construct a ConfigurationFile object passing options
 *    with the valid file name with pointed content as a first element;
 *    call getEntryInternal method from this object passing
 *    "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *    "entry" as name, TestComponent.class as type,
 *    DefaultTestComponent instance as defaultValue,
 *    and new instance of Object class as data arguments;
 *
 *   contents, checked assertions and notes for each syntax rule:
 *    1) <i>Source</i>:
 *       <i>Imports</i><sub>opt</sub> <i>Components</i><sub>opt</sub>
 *      a) absence of Imports; source content:
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new Object();
 *             }
 *         assert that entry is returned;
 *      b) absence of Components; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *         assert that NoSuchEntryException is thrown;
 *      c) invalid sequence of Imports and Components; source content:
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new Object();
 *             }
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *         assert that ConfigurationException is thrown;
 *
 *    2) <i>Imports</i>:
 *       <i>Import</i>
 *       <i>Imports</i> <i>Import</i>
 *      a) two Import items; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             import com.sun.jini.test.spec.config.util.DefaultTestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *                 entry2 = new DefaultTestComponent();
 *             }
 *         assert that entry is returned;
 *         assert that entry2 is returned;
 *      b) two the same Import items; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *         assert that entry is returned;
 *
 *    3) <i>Import</i>:
 *       import <i>PackageName</i> . * ;
 *       import <i>PackageName</i> . <i>ClassName</i> . * ;
 *       import <i>PackageName</i> . <i>ClassName</i> ;
 *      a) first rule variant; source content:
 *             import com.sun.jini.test.spec.config.util.*;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *         assert that entry is returned;
 *      b) second rule variant; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent.*;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new InternalTestComponent();
 *             }
 *         assert that entry is returned;
 *      c) third rule variant; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *         assert that entry is returned;
 *
 *    4) <i>PackageName</i>:
 *       <i>QualifiedIdentifier</i>
 *      a) broken PackageName; source content:
 *             import com.sun.jini.\@#%^&;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    5) <i>ClassName</i>:
 *       <i>QualifiedIdentifier</i>
 *      a) broken ClassName; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new #%^&();
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    6) <i>Components</i>:
 *       <i>Component</i>
 *       <i>Components</i> <i>Component</i>
 *      a) two Component items; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             import com.sun.jini.test.spec.config.util.DefaultTestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *             com.sun.jini.test.spec.config.util.DefaultTestComponent {
 *                 entry2 = new DefaultTestComponent();
 *             }
 *         assert that entry is returned;
 *         assert that entry2 is returned;
 *      b) two the same name Component items; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry2 = new TestComponent();
 *             }
 *         assert that entry is returned;
 *         assert that entry2 is returned;
 *      c) two Component items with the same entry name; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             import com.sun.jini.test.spec.config.util.DefaultTestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             }
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new DefaultTestComponent();
 *             }
 *         assert that ConfigurationException is thrown;
 *      d) additional ';' between components; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *             };
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry2 = new TestComponent();
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    7) <i>Component</i>:
 *       <i>QualifiedIdentifier</i> { <i>Entries</i><sub>opt</sub> }
 *      a) empty Entries; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *             }
 *         assert that NoSuchEntryException is thrown;
 *
 *    8) <i>Entries</i>:
 *       <i>Entry</i>
 *       <i>Entries</i> <i>Entry</i>
 *      a) two Entry items; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             import com.sun.jini.test.spec.config.util.DefaultTestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *                 entry2 = new DefaultTestComponent();
 *             }
 *         assert that entry is returned;
 *         assert that entry2 is returned;
 *      a) additional ';' between entries; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             import com.sun.jini.test.spec.config.util.DefaultTestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *                 ;
 *                 entry2 = new DefaultTestComponent();
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    9) <i>Entry</i>:
 *       <i>EntryModifiers</i><sub>opt</sub> <i>Identifier</i> = <i>Expr</i> ;
 *      a) missing "=" ; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry new TestComponent();
 *             }
 *         assert that ConfigurationException is thrown;
 *      a) missing ";" at the end of entry; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent()
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    10) <i>EntryModifiers</i>:
 *        static
 *        private
 *        static private
 *        private static
 *      a) static modifier; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 static entry = TestComponent.staticEntry;
 *             }
 *         assert valid entry is returned;
 *      b) private modifier; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 private entry2 = new TestComponent();
 *                 entry = entry2;
 *             }
 *         assert valid entry is returned;
 *      c) private modifier protection; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 private entry = new TestComponent();
 *             }
 *         assert that NoSuchEntryException is thrown;
 *      d) static private modifier; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 static private entry2 =
 *                         TestComponent.staticEntry;
 *                 entry = entry2;
 *             }
 *         assert valid entry is returned;
 *      e) static private modifier protection; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 static private entry =
 *                         TestComponent.staticEntry;
 *             }
 *         assert that NoSuchEntryException is thrown;
 *      f) private static modifier; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 private static entry2 =
 *                         TestComponent.staticEntry;
 *                 entry = entry2;
 *             }
 *         assert valid entry is returned;
 *      g) static private modifier protection; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 private static entry =
 *                         TestComponent.staticEntry;
 *             }
 *         assert that NoSuchEntryException is thrown;
 *      h) invalid modifier; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 public entry = new TestComponent();
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    11) <i>Expr</i>:
 *        <i>Literal</i>
 *        <i>TypeName</i> . class
 *        <i>EntryName</i>
 *        <i>ThisReference</i>
 *        <i>FieldName</i>
 *        <i>Cast</i>
 *        <i>NewExpr</i>
 *        <i>MethodCall</i>
 *        <i>Data</i>
 *        <i>Loader</i>
 *      a) missing expression; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = ;
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    12) <i>Literal</i>:
 *        <i>IntegerLiteral</i>
 *        <i>FloatingPointLiteral</i>
 *        <i>BooleanLiteral</i>
 *        <i>CharacterLiteral</i>
 *        <i>StringLiteral</i>
 *        <i>NullLiteral</i>
 *      a) different literal expressions; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryInteger = new Integer(1);
 *                 entryFloatingPoint = 1.0;
 *                 entryBoolean = true;
 *                 entryCharacter = 'a';
 *                 entryString = "hello word";
 *                 entryNull = null;
 *             }
 *         assert all entries are returned and valid;
 *
 *    13) <i>TypeName</i>:
 *        <i>ClassName</i>
 *        <i>ClassName</i> [ ]
 *        <i>PrimitiveType</i>
 *        <i>PrimitiveType</i> [ ]
 *      a) different TypeName expressions; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryInteger = Integer.class;
 *                 entryIntegerArray = Integer[].class;
 *                 entryBoolean = boolean.class;
 *                 entryBooleanArray = boolean[].class;
 *                 entryChar = char.class;
 *                 entryCharArray = char[].class;
 *                 entryByte = byte.class;
 *                 entryByteArray = byte[].class;
 *                 entryShort = short.class;
 *                 entryShortArray = short[].class;
 *                 entryInt = int.class;
 *                 entryIntArray = int[].class;
 *                 entryLong = long.class;
 *                 entryLongArray = long[].class;
 *                 entryFloat = float.class;
 *                 entryFloatArray = float[].class;
 *                 entryDouble = double.class;
 *                 entryDoubleArray = double[].class;
 *             }
 *         assert all entries are returned and valid;
 *
 *    14) <i>EntryName</i>:
 *        <i>QualifiedIdentifier</i>
 *      a) reference to another entry; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryInteger = 1;
 *                 entryLong = entryInteger;
 *             }
 *         assert all entries are returned and valid;
 *
 *    15) <i>ThisReference</i>:
 *        this
 *      a) reference to this; source content:
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryThis = this;
 *             }
 *         assert that entry is returned;
 *
 *    16) <i>FieldName</i>:
 *        <i>QualifiedIdentifier</i> . <i>Identifier</i>
 *      a) reference to field name; source content:
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry =
 *                 com.sun.jini.test.spec.config.util.TestComponent.staticEntry;
 *             }
 *         assert that entry is returned;
 *
 *    17) <i>Cast</i>:
 *        ( <i>TypeName</i> ) <i>Expr</i>
 *      a) valid cast; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = (TestComponent) new DefaultTestComponent();
 *             }
 *         assert that entry is returned;
 *      b) invalid cast; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = (TestComponent) new Object();
 *             }
 *         assert that ConfigurationException is thrown;
 *
 *    18) <i>NewExpr</i>:
 *       new <i>QualifiedIdentifier</i> ( <i>ExprList</i><sub>opt</sub> )
 *       new <i>QualifiedIdentifier</i> [ ] { <i>ExprList</i><sub>opt</sub> }
 *      a) new expressions; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry = new TestComponent();
 *                 entryArray = new Integer[] {};
 *                 entry = new Integer(2);
 *                 entryArray = new Integer[] {0, 1, 2};
 *                 entryArray = new Integer[] {0, 1, 2,};
 *                 entryArray = new Integer[] {,};
 *             } 
 *         assert all entries are returned and valid;
 * 
 *      b) ConfigurationException thrown with invalid array initializer:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryArray = new Integer[] {,,};
 *         assert ConfigurationException is thrown  
 *
 *    19) <i>MethodCall</i>:
 *        <i>StaticMethodName</i> ( <i>ExprList</i><sub>opt</sub> )
 *
 *        <i>StaticMethodName</i>:
 *        <i>QualifiedIdentifier</i> . <i>Identifier</i>
 *
 *        <i>ExprList</i>:
 *        <i>Expr</i>
 *        <i>ExprList</i> , <i>Expr</i>
 *      a) different method calls; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entry1 = com.sun.jini.test.spec
 *                         .config.util.TestComponent.staticMethod();
 *                 entry2 = TestComponent.staticMethod(entry1);
 *                 entry3 = TestComponent.staticMethod(0, 1, 2);
 *             }
 *         assert all entries are returned and valid;
 *
 *    20) <i>Data</i>:
 *        $data
 *      a) data usage; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryDirect = $data;
 *                 entryData = TestComponent.staticMethod($data);
 *             }
 *         assert all entries are returned and valid;
 *
 *    21) <i>Loader</i>:
 *        $loader
 *      a) loader usage, ConfigurationFile constructor should use ClassLoader
 *         argument; source content:
 *             import com.sun.jini.test.spec.config.util.TestComponent;
 *             com.sun.jini.test.spec.config.util.TestComponent {
 *                 entryDirect = $loader;
 *                 entryCasted = (ClassLoader)$loader;
 *             }
 *         assert all entries are returned and valid;
 * 
 *   22) String Concatenation
 *       a) Make sure that a string can be concatenated with a valid
 *          ConfigurationFile expression.
 *       b) Make sure two non-string literals or non-string objects cannot
 *          be concatenated.
 *       c) Test for left associativity.
 * </pre>
 */
public class Syntax_Test extends Template_Test {

    /**
     * Test cases description.
     *
     * Structure:
     * description (String),
     * expected result (exception or null if no exception should be thrown),
     * configuration file source,
     * tested entry name.
     * tested entry type.
     */
    Object[] [] testActions = new Object[] [] {
        {   "absence of Imports",
            null,
              "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Object();\n"
            + "}\n",
            "entry",
            Object.class
        },
        {   "absence of Components",
            NoSuchEntryException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n",
            "entry",
            Object.class
        },
        {   "invalid sequence of Imports and Components",
            ConfigurationException.class,
              "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Object();\n"
            + "}\n"
            + "import com.sun.jini.test.spec.config.util.TestComponent;\n",
            "entry",
            Object.class
        },
        {   "two Import items, part 1",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.DefaultTestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "    entry2 = new DefaultTestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "two Import items, part 2",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.DefaultTestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "    entry2 = new DefaultTestComponent();\n"
            + "}\n",
            "entry2",
            DefaultTestComponent.class
        },
        {   "two the same Import items",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "first <Import> rule variant",
            null,
              "import com.sun.jini.test.spec.config.util.*;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "second <Import> rule variant",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent.*;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new InternalTestComponent();\n"
            + "}\n",
            "entry",
            InternalTestComponent.class
        },
        {   "third <Import> rule variant",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "broken PackageName",
            ConfigurationException.class,
              "import com.sun.jini.\\@#%^&;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "broken ClassName",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new #%^&();\n"
            + "}\n",
            "entry",
            Object.class
        },
        {   "two the same name Component items, part 1",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry2 = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "two the same name Component items, part 2",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry2 = new TestComponent();\n"
            + "}\n",
            "entry2",
            TestComponent.class
        },
        {   "two Component items with the same entry name",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n"
            + "com.sun.jini.test.spec.config.util.DefaultTestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "additional ';' between components",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "};\n"
            + "com.sun.jini.test.spec.config.util.DefaultTestComponent {\n"
            + "    entry2 = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "empty Entries",
            NoSuchEntryException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "two Entry items, part 1",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.DefaultTestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "    entry2 = new DefaultTestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "two Entry items, part 2",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.DefaultTestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "    entry2 = new DefaultTestComponent();\n"
            + "}\n",
            "entry2",
            DefaultTestComponent.class
        },
        {   "additional ';' between entries",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.DefaultTestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "    ;\n"
            + "    entry2 = new DefaultTestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "missing '='",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "missing ';' at the end of entry",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "static modifier",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    static entry = TestComponent.staticEntry;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "private modifier",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    private entry2 = new TestComponent();\n"
            + "    entry = entry2;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "private modifier protection",
            NoSuchEntryException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    private entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "static private modifier",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    static private entry2 = TestComponent.staticEntry;\n"
            + "    entry = entry2;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "static private modifier protection",
            NoSuchEntryException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    static private entry = TestComponent.staticEntry;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "private static modifier",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    private static entry2 = TestComponent.staticEntry;\n"
            + "    entry = entry2;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "private static modifier protection",
            NoSuchEntryException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    private static entry = TestComponent.staticEntry;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "invalid modifier",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    public entry = TestComponent.staticEntry;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "missing expression",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = ;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "IntegerLiteral expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer(1);\n"
            + "}\n",
            "entry",
            Integer.class
        },
        {   "FloatingPointLiteral expression, variant 1.0 for double",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = 1.0;\n"
            + "}\n",
            "entry",
            double.class
        },
        {   "FloatingPointLiteral expression, variant 1.0d for double",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = 1.0f;\n"
            + "}\n",
            "entry",
            double.class
        },
        {   "FloatingPointLiteral expression, variant 1.0f for float",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = 1.0f;\n"
            + "}\n",
            "entry",
            float.class
        },
        {   "BooleanLiteral expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = true;\n"
            + "}\n",
            "entry",
            boolean.class
        },
        {   "CharacterLiteral expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = 'a';\n"
            + "}\n",
            "entry",
            char.class
        },
        {   "StringLiteral expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"hello word\";\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "NullLiteral expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = null;\n"
            + "}\n",
            "entry",
            Object.class
        },
        {   "Integer.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = Integer.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "Integer[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = Integer[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "boolean.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = boolean.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "boolean[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = boolean[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "char.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = char.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "char[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = char[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "byte.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = byte.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "byte[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = byte[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "short.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = short.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "short[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = short[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "int.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = int.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "int[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = int[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "long.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = long.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "long[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = long[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "float.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = float.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "float[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = float[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "double.class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = double.class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "double[].class expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = double[].class;\n"
            + "}\n",
            "entry",
            Class.class
        },
        {   "reference to another entry",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry2 = new TestComponent();\n"
            + "    entry = entry2;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "reference to this",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = this;\n"
            + "}\n",
            "entry",
            ConfigurationFile.class
        },
        {   "reference to the field name",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry =\n"
            + "  com.sun.jini.test.spec.config.util.TestComponent.staticEntry;\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "valid cast",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "import com.sun.jini.test.spec.config.util.DefaultTestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = (TestComponent) new DefaultTestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "invalid cast",
            ConfigurationException.class,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = (TestComponent) new Object();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "new expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new TestComponent();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "new empty array expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer[] {};\n"
            + "}\n",
            "entry",
            Integer[].class
        },
        {   "new expression with arguments",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer[] {};\n"
            + "}\n",
            "entry",
            Integer[].class
        },
        {   "new array with no initialization arguments",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer[] {};\n"
            + "}\n",
            "entry",
            Integer[].class
        },
        {   "new array with initialization expression",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new int[] {0,1,2};\n"
            + "}\n",
            "entry",
            int[].class
        },
        {   "new array with initialization expression with comma at the end",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new int[] {0,1,2,};\n"
            + "}\n",
            "entry",
            int[].class
       },
       {   "new array with initialization expression with no args and comma",
           null,
           "import com.sun.jini.test.spec.config.util.TestComponent;\n"
           + "com.sun.jini.test.spec.config.util.TestComponent {\n"
           + "    entry = new Integer[] {,};\n"
           + "}\n",
           "entry",
           Integer[].class
        },
        {   "bad new array initialization expression with comma",
            ConfigurationException.class,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer[] {,,};\n"
            + "}\n",
            "entry",
            Integer[].class
        },

        {   "method call expression",
            null,
              "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = com.sun.jini.test.spec\n"
            + "            .config.util.TestComponent.staticMethod();\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "method call with argument expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = TestComponent.staticMethod(3);\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "method call with several arguments expression",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = TestComponent.staticMethod(1,2,3);\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "data assignment",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = $data;\n"
            + "}\n",
            "entry",
            Object.class
        },
        {   "data as an argument",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = TestComponent.staticMethod($data);\n"
            + "}\n",
            "entry",
            TestComponent.class
        },
        {   "loader assignment",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = $loader;\n"
            + "}\n",
            "entry",
            ClassLoader.class
        },
        {   "loader with the cast",
            null,
              "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = (ClassLoader)$loader;\n"
            + "}\n",
            "entry",
            ClassLoader.class
        },
        {   "string concatenation of string literals",
            "ABC",
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"A\" + \"B\" + \"C\";\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with entries",
            "ABC are the first 3 letters in the alphabet",
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    private e1 = \" are \";\n"
            + "    private e2 = \"the first 3 letters in the alphabet\";\n"
            + "    entry = \"A\" + \"B\" + \"C\" + e1 + e2;\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with class names",
            "String class name is class java.lang.String",
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"String class name is \" + String.class;\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with this referemce",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"This configuration file is \" + this;\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with field name",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"The field is \" + TestComponent.data;\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with new expression",
            "A new string hello",
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"A new string \" + new String(\"hello\");\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with method call",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"A method call \" + TestComponent.staticMethod();\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with property",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"The java home is \" + \"${java.home}\";\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation of strings with $loader",
            null,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"The java home is \" + $loader;\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "concatenation of two non-string literals throws an exception",
            ConfigurationException.class,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = 1 + 2;\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "concatenation of two non-string objects throws an exception",
            ConfigurationException.class,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer(1) + new Integer(2);\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "left associativity test (left)",
            ConfigurationException.class,
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = new Integer(1) + new Integer(2) + \"is the sum\";\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "left associativity test (right)",
            "The concatenation is 12",
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"The concatenation is \" + new Integer(1) "
            + " + new Integer(2);\n"
            + "}\n",
            "entry",
            String.class
        },
        {   "string concatenation in call",
            "The concatenation is 12",
            "import com.sun.jini.test.spec.config.util.TestComponent;\n"
            + "com.sun.jini.test.spec.config.util.TestComponent {\n"
            + "    entry = \"The concatenation is \" + new String(\"1\"+\"2\");"
            + "\n}\n",
            "entry",
            String.class
        }           
    };


    /**
     * Implement one test action.
     */
    public void runAction(Object[] testCase) throws Exception {
        String description = (String)(testCase[0]);
        logger.log(Level.INFO, " # " + description);
        Object expectResult = testCase[1];
        String conf = (String)(testCase[2]);
        createFile(confFile, conf);
        String entryName = (String)(testCase[3]);
        Class entryType = (Class)(testCase[4]);
        String[] optionsWithFile = { confFile.getPath() };
        try {
            ConfigurationFile configurationFile =
                    callConstructor(OPT_TEST_CASE, null, optionsWithFile);
            Object entry = configurationFile.getEntry(
                    "com.sun.jini.test.spec.config.util.TestComponent",
                    entryName,
                    entryType,
                    Configuration.NO_DEFAULT,
                    new Integer(7));
            if (expectResult instanceof Exception) {
                throw new TestException(expectResult.getClass().getName()
                        + " should be thrown");
            } else if (expectResult!=null) {
                if (!entry.equals(expectResult)) {
                    throw new TestException("Expected entry to contain " 
                        + expectResult + " but instead it contains "
                        + entry);
                }
            }
        } catch (Exception e) {
            if (e.getClass() != expectResult) {
                throw new TestException("Unexpected exception", e);
            }
        }
    }


    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < testActions.length; ++i) {
            Object[] testAction = testActions[i];
            runAction(testAction);
        }
    }
}
