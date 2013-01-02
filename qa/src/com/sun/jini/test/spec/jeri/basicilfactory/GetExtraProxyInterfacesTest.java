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
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.export.ExportPermission;

import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;
import com.sun.jini.test.spec.jeri.util.FakeRemoteImpl;

import java.rmi.Remote;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   BasicILFactory.getExtraProxyInterfaces protected method.
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
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeBasicILFactory
 *     2) call getExtraProxyInterfaces method with null parameter
 *        and verify NullPointerException is thrown
 *     3) call getExtraProxyInterfaces method with an instance
 *        of FakeRemoteImpl and verify an array containing
 *        RemoteMethodControl and TrustEquivalence interfaces is returned
 * </pre>
 */
public class GetExtraProxyInterfacesTest extends QATestEnvironment implements Test {

    // test case infrastructure
    class FakeBasicILFactory extends BasicILFactory {
        public FakeBasicILFactory() { 
            super(new FakeMethodConstraints(null),ExportPermission.class); 
        }
        public Class[] getExtraProxyInterfaces(Remote impl) {
            return super.getExtraProxyInterfaces(impl);
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

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "getExtraProxyInterfaces(null)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getExtraProxyInterfaces(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "getExtraProxyInterfaces(Remote)");
        logger.log(Level.FINE,"");

        Class[] interfaces = fakeILFactory.getExtraProxyInterfaces(ri);
        assertion(interfaces.length == 2,
            "number of interfaces: " + interfaces.length);
        assertion(interfaces[0] == RemoteMethodControl.class,
            "interface[0] is: " + interfaces[0]);
        assertion(interfaces[1] == TrustEquivalence.class,
            "interface[1] is: " + interfaces[1]);
    }

    // inherit javadoc
    public void tearDown() {
    }

}

