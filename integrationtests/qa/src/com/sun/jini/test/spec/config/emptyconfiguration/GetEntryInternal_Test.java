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

package com.sun.jini.test.spec.config.emptyconfiguration;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;
import com.sun.jini.test.spec.config.util.TestComponent;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getEntryInternal method of
 *   EmptyConfiguration class.
 *
 * Actions:
 *   Test checks set of assertions and performs the following steps for that:
 *    1) always throws an exception -- this configuration contains no entries.
 *       throws NoSuchEntryException unless <code>component</code>,
 *       <code>name</code>, or <code>type</code> is <code>null</code>
 *           Steps:
 *       construct EmptyConfiguration object;
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, TestComponent.class as type,
 *       and new instance of Object class as data arguments;
 *       assert that NoSuchEntryException is thrown;
 *    2) throws NullPointerException if <code>component</code>,
 *       ... is <code>null</code>
 *           Steps:
 *       construct EmptyConfiguration object;
 *       call getEntryInternal method from this object passing
 *       null as component,
 *       "entry" as name, TestComponent.class as type,
 *       and new instance of Object class as data arguments;
 *       assert that NullPointerException is thrown;
 *    3) throws NullPointerException if ...,
 *       <code>name</code>, or ... is <code>null</code>
 *           Steps:
 *       construct EmptyConfiguration object;
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       null as name, TestComponent.class as type,
 *       and new instance of Object class as data arguments;
 *       assert that NullPointerException is thrown;
 *    4) throws NullPointerException if ...,
 *       ... <code>type</code> is <code>null</code>
 *           Steps:
 *       construct EmptyConfiguration object;
 *       call getEntryInternal method from this object passing
 *       "com.sun.jini.test.spec.config.util.TestComponent" as component,
 *       "entry" as name, null as type,
 *       and new instance of Object class as data arguments;
 *       assert that NullPointerException is thrown;
 * </pre>
 */
public class GetEntryInternal_Test extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        EmptyConfiguration emptyConfiguration = EmptyConfiguration.INSTANCE;
        Class ecClass = emptyConfiguration.getClass();
        Method [] getEntryInternalMethods = ecClass.getMethods();
        Method getEntryInternalMethod = ecClass.getDeclaredMethod(
                "getEntryInternal",
                new Class[] {
                    String.class,
                    String.class,
                    Class.class,
                    Object.class});
        getEntryInternalMethod.setAccessible(true);
        Object data = new Object();

        // 1 - simple case
        try {
            getEntryInternalMethod.invoke(
                    emptyConfiguration,
                    new Object [] {
                        "com.sun.jini.test.spec.config.util.TestComponent",
                        "entry",
                        TestComponent.class,
                        data});                
            throw new TestException(
                    "NoSuchEntryException should be thrown");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof NoSuchEntryException)) {
                cause.printStackTrace();
                throw new TestException(
                        "NoSuchEntryException should be thrown");
            }
        }

        // 2 - component is  null
        try {
            getEntryInternalMethod.invoke(
                    emptyConfiguration,
                    new Object [] {
                        null,
                        "entry",
                        TestComponent.class,
                        data});                
            throw new TestException(
                    "NullPointerException should be thrown");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof NullPointerException)) {
                cause.printStackTrace();
                throw new TestException(
                        "NullPointerException should be thrown");
            }
        }

        // 3 - name is  null
        emptyConfiguration = EmptyConfiguration.INSTANCE;
        try {
            getEntryInternalMethod.invoke(
                    emptyConfiguration,
                    new Object [] {
                        "com.sun.jini.test.spec.config.util.TestComponent",
                        null,
                        TestComponent.class,
                        data});                
            throw new TestException(
                "NullPointerException should be thrown if name is null");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof NullPointerException)) {
                cause.printStackTrace();
                throw new TestException(
                        "NullPointerException should be thrown");
            }
        }

        // 4 - type is  null
        emptyConfiguration = EmptyConfiguration.INSTANCE;
        try {
            getEntryInternalMethod.invoke(
                    emptyConfiguration,
                    new Object [] {
                        "com.sun.jini.test.spec.config.util.TestComponent",
                        "entry",
                        null,
                        data});                
            throw new TestException(
                "NullPointerException should be thrown if type is null");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof NullPointerException)) {
                cause.printStackTrace();
                throw new TestException(
                        "NullPointerException should be thrown");
            }
        }
    }
}
