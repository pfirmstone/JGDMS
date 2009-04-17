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

package com.sun.jini.test.spec.config.abstractconfiguration;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.jini.test.spec.config.util.TestComponent;
import com.sun.jini.test.spec.config.util.DefaultTestComponent;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationNotFoundException;
import net.jini.config.NoSuchEntryException;
import net.jini.config.AbstractConfiguration;
import net.jini.config.AbstractConfiguration.Primitive;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getEntry methods of
 *   AbstractConfiguration class.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeAbstractConfiguration class that implements
 *        AbstractConfiguration
 *
 * Test Cases:
 *   This test contains three test cases:
 *    a case with a method:
 *      public Object getEntry(String component, String name,
 *              Class type)
 *    a case with a method:
 *      public Object getEntry(String component, String name,
 *              Class type, Object defaultValue)
 *    a case with a method:
 *      public Object getEntry(String component, String name,
 *              Class type, Object defaultValue, Object data)
 *    Some actions are performed not for all cases.
 *
 * Actions:
 *   Test checks set of assertions and performs the following steps for that:
 *    1) Method returns an object of the specified type using the information
 *       in the entry matching the specified component and name;
 *       It returns the result of calling
 *       getEntryInternal(String,String,Class,Object)
 *       with the specified arguments ... .
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that FakeAbstractConfiguration object getEntryInternal
 *       method obtains valid component argument;
 *       assert that FakeAbstractConfiguration object getEntryInternal
 *       method obtains valid name argument;
 *       assert that FakeAbstractConfiguration object getEntryInternal
 *       method obtains valid type argument;
 *       assert that FakeAbstractConfiguration object getEntryInternal
 *       method obtains valid data argument;
 *       assert that returned value is the same as getEntryInternal
 *       method return value;
 *    2) Method returns an object ... returning the default
 *       value if no matching entry is found ...
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "unexistEntry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that returned value is equal to DefaultTestComponent instance.
 *    3) Method returns an object ... returning the default
 *       value if no matching entry is found and the default value
 *       is not Configuration.NO_DEFAULT.
 *       throws NoSuchEntryException if no matching entry is found and
 *       <code>defaultValue</code> is <code>NO_DEFAULT</code>;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "unexistEntry" as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that NoSuchEntryException is thrown.
 *    4) If <code>type</code> is a primitive type, then the result
 *       is returned as an instance of the associated wrapper class;
 *       It returns the result of calling
 *       getEntryInternal(String,String,Class,Object)
 *       ... converting results of type {@link Primitive} into
 *       the associated wrapper type.
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "intEntry" as name, int.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that returned value is a valid instance of the associated
 *       wrapper class.
 *       Repeat test for all primitive types.
 *    5) Implementation checks that <code>component</code>,
 *       ... is not <code>null</code>;
 *       throws NullPointerException if <code>component</code>,
 *       ... is <code>null</code>
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       null as component,
 *       "entry" as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that NullPointerException is thrown.
 *    6) Implementation checks that ...,
 *       <code>name</code> ... are not <code>null</code>;
 *       throws NullPointerException if ... <code>name</code> ...
 *       is <code>null</code>
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       null as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that NullPointerException is thrown.
 *    7) Implementation checks that ...,
 *       ..., and <code>type</code> are not <code>null</code>;
 *       throws NullPointerException if ... <code>type</code> is
 *       <code>null</code>
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, null as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that NullPointerException is thrown.
 *    8) The default implementation checks that ... <code>component</code>
 *       is a valid qualified identifier;
 *       throws IllegalArgumentException if <code>component</code> is not      
 *       <code>null</code> and is not a valid <i>QualifiedIdentifier</i>;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       invalid qualified identifier as component,
 *       "entry" as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that IllegalArgumentException is thrown.
 *    9) The default implementation checks that ... <code>name</code> is
 *       a valid identifier;
 *       throws IllegalArgumentException ... if 
 *       <code>name</code> is not <code>null</code> and is not a valid          
 *       <i>Identifier</i>;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       not a valid Identifier as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that IllegalArgumentException is thrown.
 *    10) The default implementation checks that ... and that
 *       <code>defaultValue</code> is of the right type;
 *       throws IllegalArgumentException ... if <code>type</code> is
 *       a reference type and     
 *       <code>defaultValue</code> is not <code>NO_DEFAULT</code>,              
 *       <code>null</code>, or an instance of <code>type</code>;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       new instance of Object as defaultValue,
 *       and null as data arguments;
 *       assert that IllegalArgumentException is thrown.
 *    11) If the call throws an exception ..., it
 *       throws a <code>ConfigurationException</code>
 *       with the original exception as the cause;
 *       throws ConfigurationException if a matching entry is found but a
 *       problem occurs creating the object for the entry.
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       adjust this object that it should throw exception from list:
 *         {@link java.lang.ArrayIndexOutOfBoundsException},
 *         {@link java.lang.SecurityException},
 *         {@link java.lang.NullPointerException},
 *         {@link java.lang.ArithmeticException},
 *         {@link java.lang.ArrayStoreException},
 *         {@link java.lang.ClassCastException},
 *         {@link java.util.EmptyStackException},
 *         {@link java.lang.IllegalArgumentException},
 *         {@link java.lang.IllegalMonitorStateException},
 *         {@link java.lang.IllegalStateException},
 *         {@link java.lang.IndexOutOfBoundsException},
 *         {@link java.util.MissingResourceException},
 *         {@link java.lang.NegativeArraySizeException},
 *         {@link java.util.NoSuchElementException},
 *         {@link java.lang.NullPointerException},
 *         {@link java.security.ProviderException},
 *         {@link java.lang.SecurityException},
 *         {@link java.lang.reflect.UndeclaredThrowableException},
 *         {@link java.lang.UnsupportedOperationException}
 *       when getEntryInternal will be called;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       DefaultTestComponent instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that the ConfigurationException with the corresponding
 *       cause is thrown.
 *    12) If the call throws an exception other than ...
 *       a {@link ConfigurationException};
 *       throws NoSuchEntryException if no matching entry is found and
 *       <code>defaultValue</code> is <code>NO_DEFAULT</code>;
 *       Any <code>Error</code> thrown while creating the object
 *       is propagated to the caller; it is not wrapped in 
 *       a <code>ConfigurationException</code>.
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       adjust this object that it should throw exception from list:
 *         {@link ConfigurationException}
 *         {@link ConfigurationNotFoundException}
 *         {@link NoSuchEntryException}
 *         {@link Error}
 *       exception when getEntryInternal will be called;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that the same exception is thrown.
 *    13) throws ConfigurationException ... if
 *       <code>type</code> is a reference type and the result for the matching
 *       entry is not either <code>null</code> or an instance of
 *       <code>type</code>;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       adjust this object that it should return some object with invalid type;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       Configuration.NO_DEFAULT instance as defaultValue,
 *       and new instance of Object class as data arguments;
 *       assert that the ConfigurationException is thrown.
 *    14) throws ConfigurationException ... if <code>type</code> is a
 *       primitive type and the
 *       result is not an instance of the associated wrapper class;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       adjust this object that it should return some object with invalid type;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "intEntry" as name, int.class as type,
 *       Configuration.NO_DEFAULT as defaultValue,
 *       and null as data arguments;
 *       assert that the ConfigurationException is thrown.
 *       Repeat test for all primitive types.
 *    15) throws IllegalArgumentException ... if          
 *       <code>type</code> is a primitive type and <code>defaultValue</code> is 
 *       not <code>NO_DEFAULT</code> or an instance of the associated wrapper   
 *       class;
 *           Steps:
 *       construct a FakeAbstractConfiguration object;
 *       call getEntry method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "intEntry" as name, int.class as type,
 *       some object with invalid type as defaultValue,
 *       and null as data arguments;
 *       assert that the ConfigurationException is thrown.
 *       Repeat test for all primitive types.
 * </pre>
 */
public class GetEntry_Test extends QATest {
    /**
     * An object to point to method:
     *      public Object getEntry(String component, String name,
     *              Class type)
     */
    Object GET_ENTRY_3ARG_CASE = new Object() {
        public String toString() {
            return "GetEntry_Test.GET_ENTRY_3ARG_CASE";
        }
    };
    
    /**
     * An object to point to method:
     *      public Object getEntry(String component, String name,
     *              Class type, Object defaultValue)
     */
    Object GET_ENTRY_4ARG_CASE = new Object() {
        public String toString() {
            return "GetEntry_Test.GET_ENTRY_4ARG_CASE";
        }
    };
    
    /**
     * An object to point to method:
     *      public Object getEntry(String component, String name,
     *              Class type, Object defaultValue, Object data)
     */
    Object GET_ENTRY_5ARG_CASE = new Object() {
        public String toString() {
            return "GetEntry_Test.GET_ENTRY_5ARG_CASE";
        }
    };
    
    Object[] testCases = new Object[] {
        GET_ENTRY_3ARG_CASE,
        GET_ENTRY_4ARG_CASE,
        GET_ENTRY_5ARG_CASE
    };


    /**
     * Call some getEntry method according to the arguments.
     */
    public Object callGetEntry(Configuration conf,
                               Object testCase,
                               String component,
                               String name,
                               Class type,
                               Object defaultValue,
                               Object data)
	    throws ConfigurationException, TestException {
        if (       testCase == GET_ENTRY_3ARG_CASE) {
            return conf.getEntry(component, name, type);
        } else if (testCase == GET_ENTRY_4ARG_CASE) {
            return conf.getEntry(component, name, type, defaultValue);
        } else if (testCase == GET_ENTRY_5ARG_CASE) {
            return conf.getEntry(component, name, type, defaultValue, data);
        } else {
            throw new TestException("Unexpected test case");
        }
    }

    /**
     * Start test execution for the case. Actions see in class description.
     */
    public void runCase(Object testCase) throws Exception {
        logger.log(Level.INFO, "--------------------------");
        final String componentName =
                FakeAbstractConfiguration.validComponentName;
        final Object[][] primitiveCases =
                FakeAbstractConfiguration.primitiveCases;
        String entryName = null;
        Object defaultValue = null;
        Object data = null;
        Object result = null;

        // 1
        FakeAbstractConfiguration conf = new FakeAbstractConfiguration();
        entryName = conf.validEntryName;
        defaultValue = new DefaultTestComponent();
        data = new Object();
        result = callGetEntry(conf,
                testCase,
                componentName,
                entryName,
                TestComponent.class,
                defaultValue,
                data);
        if (!(conf.getComponent().equals(componentName))) {
            throw new TestException(
                    "component argument was not delivered successfully");
        }
        if (!(conf.getName().equals(entryName))) {
            throw new TestException(
                    "name argument was not delivered successfully");
        }
        if (!(conf.getType().equals(TestComponent.class))) {
            throw new TestException(
                    "type argument was not delivered successfully");
        }
        if (testCase == GET_ENTRY_5ARG_CASE
                && !(conf.getData().equals(data))) {
            throw new TestException(
                    "data argument was not delivered successfully");
        }
        if (conf.getReturn() != result) {
            throw new TestException(
                    "getEntry returns invalid object");
        }

        // 2
        if (       testCase == GET_ENTRY_4ARG_CASE
                || testCase == GET_ENTRY_5ARG_CASE) {
            conf = new FakeAbstractConfiguration();
            entryName = "unexistEntry";
            defaultValue = new DefaultTestComponent();
            result = callGetEntry(conf,
                    testCase,
                    componentName,
                    entryName,
                    TestComponent.class,
                    defaultValue,
                    data);
            if (!(result.equals(defaultValue))) {
                throw new TestException(
                        "getEntry should return default test component");
            }
        }

        // 3
        if (       testCase == GET_ENTRY_4ARG_CASE
                || testCase == GET_ENTRY_5ARG_CASE) {
            conf = new FakeAbstractConfiguration();
            entryName = "unexistEntry";
            try {
                result = callGetEntry(conf,
                        testCase,
                        componentName,
                        entryName,
                        TestComponent.class,
                        Configuration.NO_DEFAULT,
                        data);
                throw new TestException(
                        "NoSuchEntryException should be thrown"
                        + " in case of absent entry"
                        + " and dafault value is equal to"
                        + " Configuration.NO_DEFAULT");
            } catch (NoSuchEntryException ignore) {
                logger.log(Level.INFO,
                        "NoSuchEntryException in case of absent entry"
                        + " and dafault value is equal to"
                        + " Configuration.NO_DEFAULT");
            }
        }

        // 4
        for (int j = 0; j < primitiveCases.length; ++j) {
            Object[] subCase = primitiveCases[j];
            logger.log(Level.INFO, "-- subcase: " + subCase[0]);
            entryName = (String) subCase[0];
            Object returnValue = subCase[1];
            defaultValue = subCase[2];
            Class entryType = (Class) subCase[3];
            conf = new FakeAbstractConfiguration();
            result = callGetEntry(conf, testCase, componentName,
                    entryName, entryType, defaultValue, null);
            if (!(result.equals(returnValue))) {
                throw new TestException(
                        "getEntry returns invalid value: " + result);
            }
        }
        
        // 5
        conf = new FakeAbstractConfiguration();
        entryName = conf.validEntryName;
        try {
            result = callGetEntry(conf,
                    testCase,
                    null,
                    entryName,
                    TestComponent.class,
                    Configuration.NO_DEFAULT,
                    null);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " component is null");
        } catch (NullPointerException ignore) {
            logger.log(Level.INFO,
                    "NullPointerException in case of"
                    + " component is null");
        }
        
        // 6
        conf = new FakeAbstractConfiguration();
        try {
            result = callGetEntry(conf,
                    testCase,
                    componentName,
                    null,
                    TestComponent.class,
                    Configuration.NO_DEFAULT,
                    null);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " name is null");
        } catch (NullPointerException ignore) {
            logger.log(Level.INFO,
                    "NullPointerException in case of"
                    + " name is null");
        }
        
        // 7
        conf = new FakeAbstractConfiguration();
        entryName = conf.validEntryName;
        try {
            result = callGetEntry(conf,
                    testCase,
                    componentName,
                    entryName,
                    null,
                    Configuration.NO_DEFAULT,
                    null);
            throw new TestException(
                    "NullPointerException should be thrown if"
                    + " type is null");
        } catch (NullPointerException ignore) {
            logger.log(Level.INFO,
                    "NullPointerException in case of"
                    + " type is null");
        }
        
        // 8
        conf = new FakeAbstractConfiguration();
        entryName = conf.validEntryName;
        try {
            result = callGetEntry(conf,
                    testCase,
                    "invalid qualified identifier",
                    entryName,
                    TestComponent.class,
                    Configuration.NO_DEFAULT,
                    null);
            throw new TestException(
                    "IllegalArgumentException should be thrown if"
                    + " component is not valid qualified identifier");
        } catch (IllegalArgumentException ignore) {
            logger.log(Level.INFO,
                    "IllegalArgumentException in case of"
                    + " component is not valid qualified identifier");
        }
        
        // 9
        conf = new FakeAbstractConfiguration();
        entryName = conf.validEntryName;
        try {
            result = callGetEntry(conf,
                    testCase,
                    componentName,
                    "invalid identifier",
                    TestComponent.class,
                    Configuration.NO_DEFAULT,
                    null);
            throw new TestException(
                    "IllegalArgumentException should be thrown if"
                    + " name is not valid identifier");
        } catch (IllegalArgumentException ignore) {
            logger.log(Level.INFO,
                    "IllegalArgumentException in case of"
                    + " name is not valid identifier");
        }
        
        // 10
        if (       testCase == GET_ENTRY_4ARG_CASE
                || testCase == GET_ENTRY_5ARG_CASE) {
            conf = new FakeAbstractConfiguration();
            entryName = conf.validEntryName;
            try {
                result = callGetEntry(conf,
                        testCase,
                        componentName,
                        entryName,
                        TestComponent.class,
                        new Object(),
                        null);
                throw new TestException(
                        "IllegalArgumentException should be thrown if"
                        + " defaultValue is not of the right type");
            } catch (IllegalArgumentException ignore) {
                logger.log(Level.INFO,
                        "IllegalArgumentException in case of"
                        + " defaultValue is not of the right type");
            }
        }

        // 11
        final Throwable[] exceptionList = {
              new java.lang.ArrayIndexOutOfBoundsException(),
              new java.lang.SecurityException(),
              new java.lang.NullPointerException(),
              new java.lang.ArithmeticException(),
              new java.lang.ArrayStoreException(),
              new java.lang.ClassCastException(),
              new java.util.EmptyStackException(),
              new java.lang.IllegalArgumentException(),
              new java.lang.IllegalMonitorStateException(),
              new java.lang.IllegalStateException(),
              new java.lang.IndexOutOfBoundsException(),
              new java.util.MissingResourceException("","",""),
              new java.lang.NegativeArraySizeException(),
              new java.util.NoSuchElementException(),
              new java.lang.NullPointerException(),
              new java.security.ProviderException(),
              new java.lang.SecurityException(),
              new java.lang.reflect.UndeclaredThrowableException(null),
              new java.lang.UnsupportedOperationException() };
        for (int e = 0; e < exceptionList.length; ++e) {
            Throwable testException = exceptionList[e];
            logger.log(Level.INFO, "-- subcase: " + testException);
            conf = new FakeAbstractConfiguration();
            conf.setException(testException);
            entryName = conf.validEntryName;
            defaultValue = new DefaultTestComponent();
            data = new Object();
            try {
                result = callGetEntry(conf, testCase, componentName, entryName,
                   TestComponent.class, defaultValue, data);
                throw new TestException(
                        "getEntry should throw an exception");
            } catch (ConfigurationException ce) {
                if (!(ce.getCause().equals(testException))) {
                    throw new TestException(
                            "getEntry throws an exception with invalid cause");
                }
            }
        }
        conf.setException(null);

        // 12
        final Throwable[] exceptionList2 = {
              new ConfigurationException(""),
              new ConfigurationNotFoundException(""),
              new NoSuchEntryException(""),
              new Error() };
        for (int e = 0; e < exceptionList2.length; ++e) {
            Throwable testException = exceptionList2[e];
            logger.log(Level.INFO, "-- subcase: " + testException);
            conf = new FakeAbstractConfiguration();
            conf.setException(testException);
            entryName = conf.validEntryName;
            defaultValue = Configuration.NO_DEFAULT;
            data = new Object();
            try {
                result = callGetEntry(conf, testCase, componentName, entryName,
                   TestComponent.class, defaultValue, data);
                throw new TestException(
                        "getEntry should throw an exception");
            } catch (ConfigurationException ce) {
                if (!(ce.equals(testException))) {
                    throw new TestException(
                            "getEntry throws invalid exception");
                }
            } catch (Error er) {
                if (!(er.equals(testException))) {
                    throw new TestException(
                            "getEntry throws an exception with invalid cause");
                }
            }
        }
        conf.setException(null);

        // 13
        conf = new FakeAbstractConfiguration();
        conf.setReturn(new Object());
        entryName = conf.validEntryName;
        defaultValue = new DefaultTestComponent();
        data = new Object();
        try {
            result = callGetEntry(conf,
                    testCase,
                    componentName,
                    entryName,
                    TestComponent.class,
                    defaultValue,
                    data);
            throw new TestException(
                    "getEntry should throw ConfigurationException in case of"
                    + " the return is not of the right type");
        } catch (ConfigurationException ignore) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of"
                    + " the return is not of the right type");
        }

        // 14
        for (int j = 0; j < primitiveCases.length; ++j) {
            Object[] subCase = primitiveCases[j];
            logger.log(Level.INFO, "-- subcase: " + subCase[0]);
            entryName = (String) subCase[0];
            Object returnValue = subCase[1];
            defaultValue = subCase[2];
            Class entryType = (Class) subCase[3];
            conf = new FakeAbstractConfiguration();
            conf.setReturn(new Object());
            try {
                result = callGetEntry(conf, testCase, componentName,
                        entryName, entryType, defaultValue, null);
                throw new TestException(
                        "getEntry should throw ConfigurationException"
                        + " in case of the return is not of the right type");
            } catch (ConfigurationException ignore) {
                logger.log(Level.INFO,
                        "ConfigurationException in case of"
                        + " the return is not of the right type");
            }
        }

        // 15
        if (       testCase == GET_ENTRY_4ARG_CASE
                || testCase == GET_ENTRY_5ARG_CASE) {
            for (int j = 0; j < primitiveCases.length; ++j) {
                Object[] subCase = primitiveCases[j];
                logger.log(Level.INFO, "-- subcase: " + subCase[0]);
                entryName = (String) subCase[0];
                Object returnValue = subCase[1];
                defaultValue = new Object();
                Class entryType = (Class) subCase[3];
                conf = new FakeAbstractConfiguration();
                try {
                    result = callGetEntry(conf, testCase, componentName,
                            entryName, entryType, defaultValue, null);
                    throw new TestException(
                            "getEntry should throw IllegalArgumentException"
                            + " in case of the default value is not the"
                            + " associated wrapper class");
                } catch (IllegalArgumentException ignore) {
                    logger.log(Level.INFO,
                            "IllegalArgumentException in case of"
                            + " the default value is not the"
                            + " associated wrapper class");
                }
            }
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
