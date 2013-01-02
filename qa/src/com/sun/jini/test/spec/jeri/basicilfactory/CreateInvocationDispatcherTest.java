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
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ServerCapabilities;
import net.jini.export.ExportPermission;

import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;
import com.sun.jini.test.spec.jeri.util.FakeServerCapabilities;
import com.sun.jini.test.spec.jeri.util.FakeRemoteImpl;

import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   BasicILFactory.createInvocationDispatcher protected method.
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
 *     3) FakeEmptyMethodConstraints
 *          -getConstraints method returns InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an empty iterator
 *     4) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints.EMPTY
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeBasicILFactory
 *     2) call createInvocationDispatcher method with combinations
 *        of null parameters and verify NullPointerExceptions are thrown
 *     3) call createInvocationDispatcher method with Collection
 *        parameter containing null elements and verify
 *        NullPointerException is thrown
 *     4) call createInvocationDispatcher method with Collection
 *        parameter containing non-Method elements and verify
 *        IllegalArgumentException is thrown
 * </pre>
 */
public class CreateInvocationDispatcherTest extends QATestEnvironment implements Test {

    // test case infrastructure
    class FakeBasicILFactory extends BasicILFactory {
        public FakeBasicILFactory() { 
            super(new FakeMethodConstraints(null),ExportPermission.class); 
        }
        public InvocationDispatcher createInvocationDispatcher(
            Collection methods, Remote impl, ServerCapabilities caps)
            throws ExportException
        {
            return super.createInvocationDispatcher(methods,impl,caps);
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
        FakeServerCapabilities sc = new FakeServerCapabilities(null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "createInvocationDispatcher(null,null,null)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationDispatcher(null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "createInvocationDispatcher(null,Remote,ServerCapabilities)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationDispatcher(null,ri,sc);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: createInvocationDispatcher("
            + "Collection,null,ServerCapabilities)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.createInvocationDispatcher(
                new ArrayList(),null,sc);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "createInvocationDispatcher(Collection,Remote,null)");
        logger.log(Level.FINE,"");

        try {
           fakeILFactory.createInvocationDispatcher(
               new ArrayList(),ri,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "createInvocationDispatcher(Collection with null element,"
            + "Remote,ServerCapabilities)");
        logger.log(Level.FINE,"");

        try {
            ArrayList al = new ArrayList();
            al.add(null);
            fakeILFactory.createInvocationDispatcher(al,ri,sc);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 6: createInvocationDispatcher("
            + "Collection with non-Method element,"
            + "Remote,ServerCapabilities)");
        logger.log(Level.FINE,"");

        try {
            ArrayList al = new ArrayList();
            al.add(sc);
            fakeILFactory.createInvocationDispatcher(al,ri,sc);
            assertion(false);
        } catch (IllegalArgumentException ignore) {
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

