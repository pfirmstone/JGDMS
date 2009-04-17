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
package com.sun.jini.test.spec.security.proxytrust.proxytrustexporter;

import java.util.logging.Level;

// java.lang
import java.lang.reflect.Proxy;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustExporter;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.BaseExporter;
import com.sun.jini.test.spec.security.proxytrust.util.ValidMainExporter;
import com.sun.jini.test.spec.security.proxytrust.util.ValidBootExporter;
import com.sun.jini.test.spec.security.proxytrust.util.SPTRemoteObject;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Exports the specified main remote object and returns a dynamic proxy for
 *     the object. A Proxy class is generated that implements all of the same
 *     interfaces as the main proxy class (but in an unspecified order) and is
 *     defined by the same class loader as the main proxy class. The dynamic
 *     proxy returned by this method is an instance of that generated class
 *     containing a ProxyTrustInvocationHandler instance created with the main
 *     proxy and the bootstrap proxy.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     ValidMainExporter - exporter which implements all needed for mainExporter
 *             parameter interfaces
 *     ValidBootExporter - exporter which implements all needed for bootExporter
 *             parameter interfaces
 *     SPTRemoteObject - normal remote object which implements ServerProxyTrust
 *             interface
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustExporter with ValidMainExporter and
 *        ValidBootExporter as parameters
 *     2) invoke export method of constructed ProxyTrustExporter with
 *        SPTRemoteObject as a parameter
 *     3) assert that export methods of both ValidMainExporter and
 *        ValidBootExporter will be invoked once each other
 *     4) assert that returned by export method object will be a Proxy class
 *     5) assert that returned by export method dynamic proxy will implement the
 *        same interfaces as the main proxy (i.e. object returned by export
 *        method invocation of ValidMainExporter with SPTRemoteObject as a
 *        parameter)
 *     6) assert that returned by export method dynamic proxy will be defined in
 *        the same class loader as the main proxy
 *     7) assert that returned by export method dynamic proxy will contain
 *        ProxyTrustInvocationHandler instance created with main proxy and
 *        bootstrap proxy (i.e. object returned by export method invocation of
 *        ValidBootExporter with SPTRemoteObject as a parameter)
 * </pre>
 */
public class ExportTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ValidMainExporter me = new ValidMainExporter();
        ValidBootExporter be = new ValidBootExporter();
        SPTRemoteObject ro = new SPTRemoteObject();
        ProxyTrustExporter pte = createPTE(me, be);
        Object res = pte.export(ro);

        if (me.getExpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Export method of mainExporter was called "
                    + me.getExpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Export method of mainExporter was called 1 time "
                + "as expected.");

        if (be.getExpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Export method of bootExporter was called "
                    + be.getExpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Export method of bootExporter was called 1 time "
                + "as expected.");

        if (!Proxy.isProxyClass(res.getClass())) {
            // FAIL
            throw new TestException(
                    "Returned by 'export' method result is not a dynamic "
                    + "Proxy class.");
        }

        // PASS
        logger.fine("Returned by 'export' method result is a dynamic "
                + "Proxy class as expected.");

        if (!ProxyTrustUtil.sameInterfaces(res, me.getProxy())) {
            // FAIL
            throw new TestException(
                    "List of interfaces implemented by class returned by "
                    + "'export' method call: "
                    + ProxyTrustUtil.interfacesToString(res)
                    + " is not equal to the one required: "
                    + ProxyTrustUtil.interfacesToString(me.getProxy()));
        }

        // PASS
        logger.fine("List of interfaces implemented by class returned by "
                + "'export' method call is the same as the one for "
                + "main proxy class as expected.");

        if (res.getClass().getClassLoader() !=
                me.getProxy().getClass().getClassLoader()) {
            // FAIL
            throw new TestException(
                    "Returned by 'export' method result is defined in "
                    + "class loader other than proxy produced by "
                    + "ValidMainExporter's 'export' method.");
        }

        // PASS
        logger.fine("Returned by 'export' method result is defined in "
                + "the same class loader as proxy produced by "
                + "ValidMainExporter's 'export' method as expected.");
        ProxyTrustInvocationHandler ptih = new ProxyTrustInvocationHandler(
                (RemoteMethodControl) me.getProxy(),
                (ProxyTrust) be.getProxy());

        if (!ptih.equals(Proxy.getInvocationHandler(res))) {
            // FAIL
            throw new TestException(
                    "Returned by 'export' method result does not contain "
                    + "ProxyTrustInvocationHandler instance created with "
                    + "main proxy and bootstrap proxy.");
        }

        // PASS
        logger.fine("Returned by 'export' method result contains "
                + "ProxyTrustInvocationHandler instance created with "
                + "main proxy and bootstrap proxy as expected.");
    }
}
