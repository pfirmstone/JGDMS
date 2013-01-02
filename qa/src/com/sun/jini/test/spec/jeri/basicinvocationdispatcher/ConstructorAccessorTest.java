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

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.export.ExportPermission;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.Confidentiality;

import com.sun.jini.test.spec.jeri.util.FakeServerCapabilities;
import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;
import com.sun.jini.test.spec.jeri.util.FakeBasicInvocationDispatcher;

import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.security.Permission;
import java.security.BasicPermission;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationDispatcher
 *   during normal and exceptional constructor and getClassLoader calls.
 *
 * Test Cases
 *   This test contains these NullPointerException test cases (* = don't cares):
 *     1) new BasicInvocationDispatcher(null,null,*,*,*)
 *     2) new BasicInvocationDispatcher(Collection,null,*,*,*)
 *     3) new BasicInvocationDispatcher(null,ServerCapabilities,*,*,*)
 *     4) new BasicInvocationDispatcher(
 *                Collection containing null element,ServerCapabilities,*,*,*)
 *
 *   these IllegalArgumentException test cases:
 *     5) new BasicInvocationDispatcher(Collection,ServerCapabilities,*,
 *                Permission.class,*)
 *     6) new BasicInvocationDispatcher(Collection,ServerCapabilities,*,
 *                File.class,*)
 *     7) new BasicInvocationDispatcher(Collection,ServerCapabilities,*,
 *                DefaultConstructorPermission.class,*)
 *     8) new BasicInvocationDispatcher(Collection,ServerCapabilities,*,
 *                StringConstructorExceptionPermission.class,*)
 *     9) new BasicInvocationDispatcher(Collection,ServerCapabilities,*,
 *                MethodConstructorExceptionPermission.class,*)
 *    10) new BasicInvocationDispatcher(
 *                Collection containing non-Method element,
 *                ServerCapabilities,*,*,*);
 *
 *   these ExportException test cases:
 *    11) new BasicInvocationDispatcher(Collection,ServerCapabilities,null,*,*)
 *    12) new BasicInvocationDispatcher(Collection,ServerCapabilities,
 *                MethodConstraints,*,*)
 *
 *   and these normal test cases:
 *    13) new FakeBasicInvocationDispatcher(Collection,ServerCapabilities,
 *                null,*,null)
 *    14) new FakeBasicInvocationDispatcher(Collection,ServerCapabilities,
 *                null,*,ClassLoader)
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
 *   For test cases 1 thru 4 the test performs the following steps:
 *     1) construct a BasicInvocationDispatcher with combinations
 *        of null parameters and verify NullPointerExceptions are thrown
 *     2) construct a BasicInvocationDispatcher with Collection
 *        parameter containing null elements and verify
 *        NullPointerException is thrown
 *   For test cases 5 thru 10 the test performs the following steps:
 *     3) construct BasicInvocationDispatcher with bad Permission class
 *     4) assert IllegalArgumentException is thrown
 *   For test cases 11 thru 12 the test performs the following steps:
 *     5) construct FakeServerCapabilities, passing in Confidentiality.YES
 *     6) construct BasicInvocationDispatcher with FakeServerCapabilities
 *        and null MethodConstraints
 *     7) assert ExportException is thrown
 *     8) construct FakeServerCapabilities, passing in Integrity.YES and
 *        Confidentiality.YES
 *     9) construct FakeMethodConstraints, passing in null
 *    10) construct BasicInvocationDispatcher with FakeServerCapabilities
 *        and FakeMethodConstraints
 *    11) assert ExportException is thrown
 *    12) construct FakeServerCapabilities, passing in Integrity.NO
 *    13) construct BasicInvocationDispatcher with FakeServerCapabilities
 *        and null MethodConstraints
 *    14) assert ExportException is thrown
 *   For test cases 13 thru 14 the test performs the following steps:
 *    15) construct FakeBasicInvocationDispatcher with good parameters
 *    16) assert getClassLoader returns proper value
 * </pre>
 */
public class ConstructorAccessorTest extends QATestEnvironment implements Test {

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

    // test case array
    Object[][] cases;

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        ArrayList noMethods = new ArrayList();
        ArrayList nullMethods = new ArrayList();
            nullMethods.add(null);

        FakeServerCapabilities caps = new FakeServerCapabilities(null);
        FakeServerCapabilities caps1 = new FakeServerCapabilities(
            new InvocationConstraint[] {Confidentiality.YES});
        FakeServerCapabilities caps2 = new FakeServerCapabilities(
            new InvocationConstraint[] {Integrity.YES,Confidentiality.YES});
        FakeServerCapabilities caps3 = new FakeServerCapabilities(
            new InvocationConstraint[] {Integrity.NO,Confidentiality.NO});

        FakeMethodConstraints mc = new FakeMethodConstraints(null);
        FakeMethodConstraints mc1 = new FakeMethodConstraints(
            new InvocationConstraint[] {Integrity.NO});

        Class normal    = ExportPermission.class;
        Class abstrct   = Permission.class;
        Class file      = File.class;
        Class noarg     = DefaultConstructorPermission.class;
        Class stringexc = StringConstructorExceptionPermission.class;
        Class methodexc = MethodConstructorExceptionPermission.class;

        NullPointerException npe     = new NullPointerException();
        IllegalArgumentException iae = new IllegalArgumentException();
        ExportException ee           = new ExportException("");

        ClassLoader loader = this.getClass().getClassLoader();

        cases = new Object[][] {
            //methods,serverCaps,serverConstraints,permClass,classLoader,exc
            {null,        null,  mc,   normal,    null,  npe},
            {noMethods,   null,  mc,   normal,    null,  npe},
            {null,        caps,  mc,   normal,    null,  npe},
            {nullMethods, caps,  mc,   normal,    null,  npe},
            {noMethods,   caps,  mc,   abstrct,   null,  iae},
            {noMethods,   caps,  mc,   file,      null,  iae},
            {noMethods,   caps,  mc,   noarg,     null,  iae},
            {noMethods,   caps,  mc,   stringexc, null,  iae},
            {noMethods,   caps,  mc,   methodexc, null,  iae},
            {noMethods,   caps1, null, normal,    null,  ee},
            {noMethods,   caps2, null, normal,    null,  ee},
            {noMethods,   caps3, mc1,  normal,    null,  ee},
            {noMethods,   caps,  null, null,      null,  null},
            {noMethods,   caps,  null, null,      loader,null}
        };
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeBasicInvocationDispatcher dispatcher;
        int counter = 1;
        for (int i = 0; i < cases.length; i++) {
            ArrayList methods = (ArrayList)cases[i][0];
            FakeServerCapabilities serverCaps = 
                (FakeServerCapabilities)cases[i][1];
            FakeMethodConstraints serverConstraints =
                (FakeMethodConstraints)cases[i][2];
            Class permClass = (Class)cases[i][3];
            ClassLoader classLoader = (ClassLoader)cases[i][4];
            Exception expectedExc = (Exception)cases[i][5];

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case " + (counter++)
                + ": methods:" + methods
                + ", serverCaps:" + serverCaps
                + ", serverConstraints:" + serverConstraints
                + ", permClass:" + permClass
                + ", classLoader:" + classLoader
                + ", expectedExc:" + expectedExc);
            logger.log(Level.FINE,"");

            try {
                dispatcher = new FakeBasicInvocationDispatcher(methods,
                    serverCaps,serverConstraints,permClass,classLoader);
                assertion(expectedExc == null,
                    "Expected " + expectedExc + " was not thrown");
                assertion(classLoader == dispatcher.getClassLoader0());
            } catch (NullPointerException npe) {
                assertion(
                    expectedExc.getClass() == npe.getClass(),
                    "Expected: " + expectedExc + "   Received: " + npe);
            } catch (IllegalArgumentException iae) {
                assertion(
                    expectedExc.getClass() == iae.getClass(),
                    "Expected: " + expectedExc + "   Received: " + iae);
            } catch (ExportException ee) {
                assertion(
                    expectedExc.getClass() == ee.getClass(),
                    "Expected: " + expectedExc + "   Received: " + ee);
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

