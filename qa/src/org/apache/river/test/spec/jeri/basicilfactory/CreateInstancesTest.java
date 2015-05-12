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
package org.apache.river.test.spec.jeri.basicilfactory;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;
import net.jini.export.ExportPermission;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.TrustEquivalence;

import org.apache.river.test.spec.jeri.util.FakeObjectEndpoint;
import org.apache.river.test.spec.jeri.util.FakeMethodConstraints;
import org.apache.river.test.spec.jeri.util.FakeServerCapabilities;
import org.apache.river.test.spec.jeri.util.FakeMethodConstraints;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   BasicILFactory.createInstances method inherited from AbstractILFactory.
 *
 * Test Cases
 *   This test iterates over a 2-tuple.  Each 2-tuple
 *   denotes one test case and is defined by the variables:
 *     Class[] interfaces
 *     boolean legal
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObjectEndpoint
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *     2) FakeEmptyMethodConstraints
 *          -getConstraints method returns InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an empty iterator
 *     3) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints.EMPTY
 *     4) FakeRemoteInterface
 *          -extends java.rmi.Remote
 *          -declares one method that throws RemoteException
 *     5) FakeRemoteSubInterface
 *          -extends FakeRemoteInterface
 *          -declares one method that throws RemoteException
 *     6) FakeIllegalRemoteSubInterface
 *          -extends FakeRemoteInterface
 *          -declares one method that overloads the super interface method
 *           but does not throw RemoteException
 *     7) FakeInterface
 *          -does not extend java.rmi.Remote
 *          -declares one method that throws Exception
 *     8) FakeSubInterfaceRemote
 *          -extends FakeInterface
 *          -declares one method that overloads the super interface method
 *           but throws RemoteException
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct an instance of BasicILFactory and FakeObjectEndpoint
 *     2) calls createInstances method with combinations of null parameters
 *        and verifes NullPointerExceptions are thrown
 *     3) for each test case  the test performs the following steps:
 *          1) create a remote impl from interfaces using Proxy.newProxyInstance
 *          2) call createInstances with the remote impl
 *          3) if legal is true
 *               -verify that the defining class loader of the returned proxy
 *                is the same as the defining class loader of the remote impl
 *               -verify that the proxy's invocation handler uses the
 *                FakeObjectEndpoint
 *               -verify that the proxy's invocation handler uses the
 *                FakeEmptyMethodConstraints
 *               -verify that the returned proxy implements the same interfaces
 *                as the remote impl
 *             else
 *               -verify ExportException is thrown
 * </pre>
 */
public class CreateInstancesTest extends QATestEnvironment implements Test {

    // test case infrastructure
    interface FakeRemoteInterface extends Remote {
        public void fakeMethod1() throws RemoteException;
    }
    interface FakeRemoteSubInterface extends FakeRemoteInterface {
        public void fakeMethod2() throws RemoteException;
    }
    interface FakeIllegalRemoteSubInterface extends FakeRemoteInterface {
        public void fakeMethod1();
    }
    interface FakeInterface {
        public void fakeMethod3() throws Exception;
    }
    interface FakeSubInterfaceRemote extends FakeInterface {
        public void fakeMethod3() throws RemoteException;
    }

    Class fri   = FakeRemoteInterface.class;
    Class frsi  = FakeRemoteSubInterface.class;
    Class firsi = FakeIllegalRemoteSubInterface.class;
    Class fi    = FakeInterface.class;
    Class fsir  = FakeSubInterfaceRemote.class;
    Class rmc   = RemoteMethodControl.class;
    Class te    = TrustEquivalence.class;
    Boolean t   = Boolean.TRUE;
    Boolean f   = Boolean.FALSE;

    // test cases
    Object[][] cases = {
        // interfaces, legal, expectedInterfaces
        { new Class[] {fri},        t, new Class[] {fri,rmc,te} },
        { new Class[] {frsi},       t, new Class[] {frsi,rmc,te} },
        { new Class[] {frsi, fi},   t, new Class[] {frsi,rmc,te} },
        { new Class[] {frsi, fsir}, t, new Class[] {frsi,rmc,te} },
        { new Class[] {fsir, fri},  t, new Class[] {fri,rmc,te} },

        //illegal remote interfaces
        { new Class[] {firsi},      f, null },
        { new Class[] {fri,firsi},  f, null },
        { new Class[] {fi,firsi},   f, null },
        { new Class[] {firsi,fsir}, f, null }
    };

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        // create test infrastructure
        ObjectEndpoint oe = new FakeObjectEndpoint();
        FakeMethodConstraints fakeMethodConstraints =
            new FakeMethodConstraints(null);
        BasicILFactory factory = new BasicILFactory(
            fakeMethodConstraints,ExportPermission.class);
        FakeServerCapabilities sc = new FakeServerCapabilities(null);
        int counter = 1;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) 
            + ": createInstances(null,null,null)");
        logger.log(Level.FINE,"");

        // null parameter tests
        try {
            factory.createInstances(null, null, null);
            assertion(false);
        } catch (NullPointerException npe) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) 
            + ": createInstances(Remote,null,null)");
        logger.log(Level.FINE,"");

        try {
            factory.createInstances(new Remote() {}, null, null);
            assertion(false);
        } catch (NullPointerException npe) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) + ": "
            + "createInstances(null,ObjectEndpoint,null)");
        logger.log(Level.FINE,"");

        try {
            factory.createInstances(null, oe, null);
            assertion(false);
        } catch (NullPointerException npe) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) + ": "
            + "createInstances(null,null,ServerCapabilities)");
        logger.log(Level.FINE,"");

        try {
            factory.createInstances(null, null, sc);
            assertion(false);
        } catch (NullPointerException npe) {}

        for (int i = 0; i < cases.length; i++) {
            Class[] interfaces = (Class[])cases[i][0];
            boolean legal = ((Boolean)cases[i][1]).booleanValue();
            Class[] expectedInterfaces = (Class[])cases[i][2];

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case " + (counter++) + ": "
                + "legal:" + legal + ", interfaces:" + interfaces);
            logger.log(Level.FINE,"");

            Remote impl = createImpl(interfaces, oe);

            // verify createInstances behavior using legal and illegal
            // parameters
            if (legal) {
                Object proxy = 
                    factory.createInstances(impl,oe,sc).getProxy();
                InvocationHandler handler = 
                    Proxy.getInvocationHandler(proxy);
                assertion(proxy.getClass().getClassLoader() ==
                    impl.getClass().getClassLoader());
                assertion(handler instanceof BasicInvocationHandler);
                assertion(((BasicInvocationHandler)handler).
                    getObjectEndpoint() == oe);
                assertion(((BasicInvocationHandler)handler).
                    getServerConstraints() == fakeMethodConstraints);
                assertion(Arrays.equals(expectedInterfaces,
                    proxy.getClass().getInterfaces()));
            } else {
                try {
                    factory.createInstances(impl,oe,sc);
                    assertion(false);
                } catch (ExportException ignore) { }
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

    /**
     * Given an array of interfaces, this method creates a dynamic
     * proxy for the interfaces with a BasicInvocationHandler constructed
     * with the given ObjectEndpoint.
     *
     * @param interfaces array of interface, one must be Remote
     * @param oe the endpoint with which to create the BasicInvocationHandler
     */
    private Remote createImpl(Class[] interfaces, ObjectEndpoint oe) {
        BasicInvocationHandler handler =
            new BasicInvocationHandler(oe,new FakeMethodConstraints(null));
        return (Remote) Proxy.newProxyInstance(
            this.getClass().getClassLoader(),
            interfaces,
            handler);
    }

}

