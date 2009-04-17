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
package com.sun.jini.test.spec.jeri.basicinvocationdispatcher;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.export.ExportPermission;

import java.security.Permission;
import java.security.BasicPermission;
import java.lang.reflect.Method;
import java.io.File;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of
 *   BasicInvocationDispatcher.checkPermissionClass method.
 *
 * Test Cases
 *   This test contains these test cases:
 *     1) checkPermissionClass(null)
 *     2) checkPermissionClass(ExportPermission.class)
 *     3) checkPermissionClass(Permission.class)
 *     4) checkPermissionClass(File.class)
 *     5) checkPermissionClass(DefaultConstructorPermission.class)
 *     6) checkPermissionClass(StringConstructorExceptionPermission.class)
 *     7) checkPermissionClass(MethodConstructorExceptionPermission.class)
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeMethodConstraints
 *          -getConstraints method returns InvocationConstraints created
 *           from InvocationConstraint[] passed to constructor or, if null,
 *           InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an iterator over
 *           return value of getConstraints
 *     2) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints created
 *           from InvocationConstraint[] passed to constructor or, if null,
 *           InvocationConstraints.EMPTY
 *     3) DefaultConstructorPermissionClass
 *          -a concrete sublass of Permission with a non-arg constructor
 *     4) StringConstructorExceptionPermissionClass
 *          -a concrete sublass of Permission with a String constructor
 *           that throws Exception
 *     5) MethodConstructorExceptionPermissionClass
 *          -a concrete sublass of Permission with a Method constructor
 *           that throws Exception
 *
 * Actions
 *   For each test cases 1 and 2 the test performs the following steps:
 *     1) call BasicInvocationDispatcher.checkPermissionClass
 *        method with the specified value
 *     2) assert no exception is thrown
 *   For each test cases 3 thru 7 the test performs the following steps:
 *     3) call BasicInvocationDispatcher.checkPermissionClass
 *        method with bad Permission class
 *     4) assert IllegalArgumentException is thrown
 * </pre>
 */
public class CheckPermissionClassTest extends QATest {

    // fake Permission subclasses
    class DefaultConstructorPermission extends BasicPermission {
        public DefaultConstructorPermission() { super("fake"); }
    }
    class StringConstructorExceptionPermission extends BasicPermission {
        public StringConstructorExceptionPermission(String s)
            throws Exception
        { super("fake"); }
    }
    class MethodConstructorExceptionPermission extends BasicPermission {
        public MethodConstructorExceptionPermission(Method m)
            throws Exception
        { super("fake"); }
    }

    // test cases
    Object[] cases = {
        Permission.class,
        File.class,
        DefaultConstructorPermission.class,
        StringConstructorExceptionPermission.class,
        MethodConstructorExceptionPermission.class
    };

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": permClass: null");
        logger.log(Level.FINE,"");

        //should return normally
        BasicInvocationDispatcher.checkPermissionClass(null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": permClass: valid permission");
        logger.log(Level.FINE,"");

        //should return normally
        BasicInvocationDispatcher.checkPermissionClass(
            ExportPermission.class);

        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Class permClass = (Class)cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                + ": bad permClass:" + permClass);
            logger.log(Level.FINE,"");

            try {
                BasicInvocationDispatcher.checkPermissionClass(permClass);
                assertion(false);
            } catch (IllegalArgumentException ignore) {
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

