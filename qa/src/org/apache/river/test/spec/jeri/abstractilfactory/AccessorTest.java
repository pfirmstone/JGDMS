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
package org.apache.river.test.spec.jeri.abstractilfactory;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.AbstractILFactory;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;

import org.apache.river.test.spec.jeri.util.FakeAbstractILFactory;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the AbstractILFactory
 *   getExtraProxyInterfaces, getInvocationDispatcherMethods,
 *   getProxyInterfaces, and getRemoteInterfaces protected methods.
 *
 * Test Cases
 *   Test cases are defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeAbstractILFactory
 *          -a concrete impl of AbstractILFactory used to
 *           access protected methods of AbstractILFactory
 *     2) FakeRemoteImpl
 *          -a class that implements a Remote and non-Remote interface
 *           and RemoteMethodControl
 *     3) FakeRemoteSubImpl
 *          -extends FakeRemoteImpl
 *          -a class that implements RemoteMethodControl and a sub interface
 *           of the Remote interface implmented by FakeRemoteImpl
 *     4) FakeIllegalRemoteImpl
 *          -extends FakeRemoteImpl
 *          -a class that implements RemoteMethodControl and an illegal
 *           Remote interface
 *
 * Actions
 *   The test performs the following steps:
 *       1) construct a FakeAbstractILFactory
 *     getExtraProxyInterfaces test cases:
 *       2) call getExtraProxyInterfaces method with null parameter
 *          and verify NullPointerException is thrown
 *       3) call getExtraProxyInterfaces method with an instance
 *          of FakeRemoteSubImpl and verify an array containing the
 *          RemoteMethodControl interface is returned
 *     getProxyInterfaces test cases:
 *       4) call getProxyInterfaces method with null parameter
 *          and verify NullPointerException is thrown
 *       5) call getProxyInterfaces method with an instance
 *          of FakeRemoteSubImpl and verify an array containing the
 *          RemoteMethodControl interface and FakeRemoteSubImpl's
 *          interfaces is returned
 *     getRemoteInterfaces test cases:
 *       6) call getRemoteInterfaces method with null parameter
 *          and verify NullPointerException is thrown
 *       7) call getRemoteInterfaces method with an instance
 *          of FakeIllegalRemoteImpl and verify ExportException is thrown
 *       8) call getRemoteInterfaces method with an instance
 *          of FakeRemoteSubImpl and verify an array containing the
 *          FakeRemoteSubImpl's Remote interfaces is returned
 *     getInvocationDispatcherMethods test cases:
 *       9) call getInvocationDispatcherMethods method with null parameter
 *          and verify NullPointerException is thrown
 *      10) call getInvocationDispatcherMethods method with an instance
 *          of FakeIllegalRemoteImpl and verify ExportException is thrown
 *      11) call getInvocationDispatcherMethods method with an instance
 *          of FakeRemoteSubImpl and verify a Set containing Method objects
 *          from FakeRemoteSubImpl's Remote interfaces is returned
 * </pre>
 */
public class AccessorTest extends QATestEnvironment implements Test {

    interface FakeRemoteInterface extends Remote {
        public void fakeMethod1() throws RemoteException;
    }
    interface FakeRemoteSubInterface extends FakeRemoteInterface {
        public void fakeMethod1() throws RemoteException;
        public void fakeMethod2() throws RemoteException;
    }
    interface FakeIllegalRemoteInterface extends FakeRemoteInterface {
        public void fakeMethod1();
    }
    interface FakeInterface {
        public void fakeMethod3();
    }


    class FakeRemoteImpl 
        implements FakeRemoteInterface, FakeInterface, RemoteMethodControl 
    {
        public void fakeMethod1() throws RemoteException { }
        public void fakeMethod3() { }
        public MethodConstraints getConstraints() { return null; }
        public RemoteMethodControl setConstraints(MethodConstraints c) {
            return null;
        }
    }
    class FakeRemoteSubImpl extends FakeRemoteImpl
        implements FakeRemoteSubInterface, RemoteMethodControl
    {
        public void fakeMethod1() throws RemoteException { }
        public void fakeMethod2() throws RemoteException { }
        public MethodConstraints getConstraints() { return null; }
        public RemoteMethodControl setConstraints(MethodConstraints c) {
            return null;
        }
    }
    class FakeIllegalRemoteImpl extends FakeRemoteImpl 
        implements FakeIllegalRemoteInterface, RemoteMethodControl
    {
        public void fakeMethod1() { }
        public MethodConstraints getConstraints() { return null; }
        public RemoteMethodControl setConstraints(MethodConstraints c) {
            return null;
        }
    }


    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    public void run() throws Exception {
        FakeAbstractILFactory fakeILFactory = new FakeAbstractILFactory();
        FakeRemoteSubImpl fakeRemoteSubImpl = new FakeRemoteSubImpl();
        FakeIllegalRemoteImpl fakeIllegalRemoteImpl = 
            new FakeIllegalRemoteImpl();

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: getExtraProxyInterfaces");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getExtraProxyInterfaces(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        Class[] interfaces = 
            fakeILFactory.getExtraProxyInterfaces(fakeRemoteSubImpl);
        assertion(interfaces.length == 1,
            "number of interfaces: " + interfaces.length);
        assertion(interfaces[0] == RemoteMethodControl.class,
            "interface[0] is: " + interfaces[0]);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: getProxyInterfaces");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getProxyInterfaces(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        interfaces = fakeILFactory.getProxyInterfaces(fakeRemoteSubImpl);
        assertion(interfaces.length == 3,
            "number of interfaces: " + interfaces.length);
        assertion(interfaces[0] == FakeRemoteInterface.class,
            "interface[0] is: " + interfaces[0]);
        assertion(interfaces[1] == FakeRemoteSubInterface.class,
            "interface[1] is: " + interfaces[1]);
        assertion(interfaces[2] == RemoteMethodControl.class,
            "interface[2] is: " + interfaces[2]);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: getRemoteInterfaces");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getRemoteInterfaces(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        try {
            fakeILFactory.getRemoteInterfaces(fakeIllegalRemoteImpl);
            assertion(false);
        } catch (ExportException ignore) {
        }

        interfaces = fakeILFactory.getRemoteInterfaces(fakeRemoteSubImpl);
        assertion(interfaces.length == 2,
            "number of interfaces: " + interfaces.length);
        assertion(interfaces[0] == FakeRemoteInterface.class,
            "interface[0] is: " + interfaces[0]);
        assertion(interfaces[1] == FakeRemoteSubInterface.class,
            "interface[1] is: " + interfaces[1]);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "getInvocationDispatcherMethods");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getInvocationDispatcherMethods(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        try {
            fakeILFactory.getInvocationDispatcherMethods(
                fakeIllegalRemoteImpl);
            assertion(false);
        } catch (ExportException ignore) {
        }

        Collection methods = 
            fakeILFactory.getInvocationDispatcherMethods(fakeRemoteSubImpl);
        assertion(methods instanceof Set);
        assertion(methods.size() == 2,
            "number of methods: " + methods.size());
        Method m = FakeRemoteInterface.class.getMethod("fakeMethod1",null);
        assertion(methods.contains(m),"method is: " + m);
        m = FakeRemoteSubInterface.class.getMethod("fakeMethod2",null);
        assertion(methods.contains(m),"method is: " + m);
    }

    public void tearDown() {
    }

}

