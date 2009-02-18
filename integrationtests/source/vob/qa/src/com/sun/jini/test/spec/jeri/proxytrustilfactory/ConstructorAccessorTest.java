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
package com.sun.jini.test.spec.jeri.proxytrustilfactory;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.jeri.ProxyTrustILFactory;
import net.jini.export.ExportPermission;
import net.jini.core.constraint.MethodConstraints;

import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;

import java.lang.reflect.Method;
import java.security.Permission;
import java.security.BasicPermission;
import java.io.File;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ProxyTrustILFactory
 *   during normal and exceptional constructor calls.
 *
 *   This test verifies the behavior of the
 *   getPermissionClass, getServerConstraints, and getClassLoader methods.
 *
 * Test Cases
 *   This test contains these normal test cases:
 *     1) new ProxyTrustILFactory(null,null)
 *     2) new ProxyTrustILFactory(MethodConstraints,null)
 *     3) new ProxyTrustILFactory(null,Class)
 *     4) new ProxyTrustILFactory(MethodConstraints,Class)
 *     5) repeat 1 thru 4 with null and non-null ClassLoader parameters
 *   and these exceptional test cases:
 *     6) new ProxyTrustILFactory(null,AbstractPermission.class)
 *     7) new ProxyTrustILFactory(null,File.class)
 *     8) new ProxyTrustILFactory(null,DefaultConstructorPermission.class)
 *     9) new ProxyTrustILFactory(
 *            null,StringConstructorExceptionPermission.class)
 *    10) new ProxyTrustILFactory(
 *            null,MethodConstructorExceptionPermission.class)
 *    11) repeat 6 thru 10 with null and non-null ClassLoader parameters
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeProxyTrustILFactory
 *          -a sublass of ProxyTrustILFactory that gives access to 
 *           protected methods
 *     2) FakeEmptyMethodConstraints
 *          -getConstraints method returns InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an empty iterator
 *     3) PermissionClass
 *          -Permission.class (abstract)
 *     4) DefaultConstructorPermissionClass
 *          -a concrete sublass of Permission with a non-arg constructor
 *     5) StringConstructorExceptionPermissionClass
 *          -a concrete sublass of Permission with a String constructor
 *           that throws Exception
 *     6) MethodConstructorExceptionPermissionClass
 *          -a concrete sublass of Permission with a Method constructor
 *           that throws Exception
 *
 * Actions
 *   For test cases 1 thru 5 the test performs the following steps:
 *     1) construct FakeProxyTrustILFactory
 *     2) assert getPermissionClass, getServerConstraints, and
 *        getClassLoader return the objects passed to the constructor
 *   For test cases 6 thru 11 the test performs the following steps:
 *     3) construct FakeProxyTrustILFactory
 *     4) assert IllegalArgumentException is thrown
 * </pre>
 */
public class ConstructorAccessorTest extends QATest {

    // fake ProxyTrustILFactory subclass
    class FakeProxyTrustILFactory extends ProxyTrustILFactory {
        public FakeProxyTrustILFactory(MethodConstraints c, Class p) {
            this(c,p,null); 
        }
        public FakeProxyTrustILFactory(MethodConstraints c, Class p, 
            ClassLoader l) 
        {
            super(c,p,l); 
        }
        public ClassLoader getClassLoader0() { return super.getClassLoader(); }
    }

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

    // test case infrastructure
    FakeMethodConstraints mc = new FakeMethodConstraints(null);
    Class pc           = ExportPermission.class;
    Class ap           = Permission.class;
    Class file         = File.class;
    Class dcp          = DefaultConstructorPermission.class;
    Class scep         = StringConstructorExceptionPermission.class;
    Class mcep         = MethodConstructorExceptionPermission.class;
    ClassLoader loader = this.getClass().getClassLoader();
    Boolean t          = Boolean.TRUE;
    Boolean f          = Boolean.FALSE;

    // test cases
    Object[][] cases = {
        //methodConstraints, permClass, legal test case
        {null, null, null,   t},
        {null, null, loader, t},
        {mc,   null, null,   t},
        {mc,   null, loader, t},
        {null, pc,   null,   t},
        {null, pc,   loader, t},
        {mc,   pc,   null,   t},
        {mc,   pc,   loader, t},
        {null, ap,   loader, f},
        {null, file, null,   f},
        {null, dcp,  loader, f},
        {null, scep, null,   f},
        {null, mcep, loader, f}
    };

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeProxyTrustILFactory factory;
        int counter = 1;

        for (int i = 0; i < cases.length; i++) {
            FakeMethodConstraints mConstraints = 
                (FakeMethodConstraints)cases[i][0];
            Class permClass = (Class)cases[i][1];
            ClassLoader classLoader = (ClassLoader)cases[i][2];
            boolean legal = ((Boolean)cases[i][3]).booleanValue();

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case " + (counter++) + ": "
                + "legal:" + legal 
                + ",methodConstraints:" + mConstraints
                + ",permClass:" + permClass
                + ",classLoader:" + classLoader);
            logger.log(Level.FINE,"");

            if (legal) {
                factory = 
                    new FakeProxyTrustILFactory(mConstraints,permClass);
                assertion(factory.getServerConstraints() == mConstraints);
                assertion(factory.getPermissionClass() == permClass);
                assertion(factory.getClassLoader0() == null);

                factory = new FakeProxyTrustILFactory(
                    mConstraints,permClass,classLoader);
                assertion(factory.getServerConstraints() == mConstraints);
                assertion(factory.getPermissionClass() == permClass);
                assertion(factory.getClassLoader0() == classLoader);
            } else {
                try {
                    new FakeProxyTrustILFactory(mConstraints,permClass);
                    assertion(false);
                } catch (IllegalArgumentException ignore) { }

                try {
                    new FakeProxyTrustILFactory(
                        mConstraints,permClass,classLoader);
                    assertion(false);
                } catch (IllegalArgumentException ignore) { }
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

