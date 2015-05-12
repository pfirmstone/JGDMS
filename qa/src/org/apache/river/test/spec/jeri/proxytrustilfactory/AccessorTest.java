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
package org.apache.river.test.spec.jeri.proxytrustilfactory;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.ProxyTrustILFactory;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.TrustVerifier;

import org.apache.river.test.spec.jeri.util.FakeProxyTrustILFactory;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ProxyTrustILFactory
 *   getRemoteInterfaces protected method.
 *
 * Test Cases
 *   Test cases are defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeProxyTrustILFactory
 *          -a concrete impl of ProxyTrustILFactory used to
 *           access protected methods of ProxyTrustILFactory
 *     2) FakeRemoteImpl
 *          -a class that implements a Remote and a non-Remote interface
 *     3) FakeServerProxyTrustImpl
 *          -extends FakeRemoteImpl
 *          -a class that implements ServerProxyTrust and a sub interface
 *           of the Remote interface implmented by FakeRemoteImpl
 *     4) FakeIllegalServerProxyTrustImpl
 *          -extends FakeServerProxyTrustImpl
 *          -a class that implements an illegal Remote interface
 *
 * Actions
 *   The test performs the following steps:
 *       1) construct a FakeProxyTrustILFactory
 *       2) call getRemoteInterfaces method with null parameter
 *          and verify NullPointerException is thrown
 *       3) call getRemoteInterfaces method with an instance
 *          of FakeRemoteImpl and verify ExportException is thrown
 *       4) call getRemoteInterfaces method with an instance
 *          of FakeIllegalServerProxyTrustImpl and verify
 *          ExportException is thrown
 *       5) call getRemoteInterfaces method with an instance
 *          of FakeServerProxyTrustImpl and verify an array containing the
 *          FakeServerProxyTrustImpl's Remote interfaces and
 *          ProxyTrust interface is returned
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
        implements FakeRemoteInterface, FakeInterface
    {
        public void fakeMethod1() throws RemoteException { }
        public void fakeMethod3() { }
    }
    class FakeServerProxyTrustImpl extends FakeRemoteImpl
        implements FakeRemoteSubInterface, ServerProxyTrust
    {
        public void fakeMethod1() throws RemoteException { }
        public void fakeMethod2() throws RemoteException { }
        public TrustVerifier getProxyVerifier() throws RemoteException {
            return null;
        }
    }
    class FakeIllegalServerProxyTrustImpl extends FakeServerProxyTrustImpl 
        implements FakeIllegalRemoteInterface
    {
        public void fakeMethod1() { }
    }


    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeProxyTrustILFactory fakeILFactory = 
            new FakeProxyTrustILFactory();
        FakeRemoteImpl fakeRemoteImpl = new FakeRemoteImpl();
        FakeServerProxyTrustImpl fakeServerProxyTrustImpl = 
            new FakeServerProxyTrustImpl();
        FakeIllegalServerProxyTrustImpl fakeIllegalServerProxyTrustImpl = 
            new FakeIllegalServerProxyTrustImpl();

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: getRemoteInterfaces(null)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getRemoteInterfaces(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: getRemoteInterfaces"
            + "(non ServerProxyTrust Remote implementation)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getRemoteInterfaces(fakeRemoteImpl);
            assertion(false);
        } catch (ExportException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: getRemoteInterfaces"
            + "(ServerProxyTrust with illegal Remote interface)");
        logger.log(Level.FINE,"");

        try {
            fakeILFactory.getRemoteInterfaces(
                fakeIllegalServerProxyTrustImpl);
            assertion(false);
        } catch (ExportException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: getRemoteInterfaces"
            + " normal case");
        logger.log(Level.FINE,"");

        Class[] interfaces = fakeILFactory.getRemoteInterfaces(
            fakeServerProxyTrustImpl);
        assertion(interfaces.length == 3,
            "number of interfaces: " + interfaces.length);
        assertion(interfaces[0] == FakeRemoteInterface.class,
            "interface[0] is: " + interfaces[0]);
        assertion(interfaces[1] == FakeRemoteSubInterface.class,
            "interface[1] is: " + interfaces[1]);
        assertion(interfaces[2] == ProxyTrust.class,
            "interface[2] is: " + interfaces[2]);
    }

    // inherit javadoc
    public void tearDown() {
    }

}

