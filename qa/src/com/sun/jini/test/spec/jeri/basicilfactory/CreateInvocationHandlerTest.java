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
package com.sun.jini.test.spec.jeri.basicilfactory;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.jeri.BasicILFactory;
import net.jini.jeri.ObjectEndpoint;
import net.jini.export.ExportPermission;

import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;
import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeRemoteImpl;

import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.lang.reflect.InvocationHandler;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   BasicILFactory.createInvocationHandler protected method.
 *
 * Test Cases
 *   Test cases are defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeBasicILFactory
 *          -a subclass of BasicILFactory with no methods used to
 *           access protected methods of BasicILFactory
 *     2) FakeRemoteImpl
 *          -an class that implements Remote
 *     3) FakeObjectEndpoint
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *     4) FakeEmptyMethodConstraints
 *          -getConstraints method returns InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an empty iterator
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeBasicILFactory
 *     2) call createInvocationHandler method with combinations
 *        of null parameters and verify NullPointerExceptions are thrown
 *     3) call createInvocationHandler method with Class[]
 *        parameter containing null elements and verify
 *        NullPointerException is thrown
 * </pre>
 */
public class CreateInvocationHandlerTest extends QATestEnvironment implements Test {

    // test case infrastructure
    class FakeBasicILFactory extends BasicILFactory {
        public FakeBasicILFactory() { 
            super(new FakeMethodConstraints(null),ExportPermission.class); 
        }
        public InvocationHandler createInvocationHandler(Class[] interfaces, 
            Remote impl, ObjectEndpoint oe) throws ExportException
        {
            return super.createInvocationHandler(interfaces, impl, oe);
        }
    }

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeBasicILFactory fakeILFactory = new FakeBasicILFactory();
        FakeRemoteImpl ri = new FakeRemoteImpl();
        FakeObjectEndpoint oe = new FakeObjectEndpoint();

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "createInvocationHandler(null,null,null)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationHandler(null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "createInvocationHandler(null,Remote,ObjectEndpoint)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationHandler(null,ri,oe);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "createInvocationHandler(Class[],null,ObjectEndpoint)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationHandler(new Class[] {},null,oe);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "createInvocationHandler(Class[],Remote,null)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationHandler(new Class[] {},ri,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "createInvocationHandler(Class[] with null element,"
            + "Remote,ObjectEndpoint)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationHandler(
                new Class[] {Class.class,null},ri,oe);
            assertion(false);
        } catch (NullPointerException ignore) {
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

