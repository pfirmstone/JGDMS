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
package org.apache.river.test.spec.security.proxytrust.proxytrustinvocationhandler;

import java.util.logging.Level;

// java.lang
import java.lang.reflect.Method;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.ProxyTrustUtil;
import org.apache.river.test.spec.security.proxytrust.util.Interface1Impl;
import org.apache.river.test.spec.security.proxytrust.util.Interface1TEImpl;
import org.apache.river.test.spec.security.proxytrust.util.Interface2TEImpl;
import org.apache.river.test.spec.security.proxytrust.util.Interface12TEImpl;
import org.apache.river.test.spec.security.proxytrust.util.TEImpl;
import org.apache.river.test.spec.security.proxytrust.util.TestClassLoader;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     If the specified method is TrustEquivalence.checkTrustEquivalence, invoke
 *     method of ProxyTrustInvocationHandler returns true if the argument
 *     (args[0]) is an instance of a dynamic proxy class that implements the
 *     same interfaces as the specified proxy and calling the
 *     checkTrustEquivalence method of this invocation handler with the
 *     invocation handler of that argument returns true, and returns false
 *     otherwise.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Interface1TEProxy - normal dynamic proxy class implementing
 *             TrustEquivalence and TestInterface1 interfaces
 *     Interface1Proxy - normal dynamic proxy class implementing TestInterface1
 *     Interface2TEProxy - normal dynamic proxy class implementing
 *             TrustEquivalence and TestInterface2 interfaces
 *     Interface12TEProxy - normal dynamic proxy class implementing
 *             TrustEquivalence, TestInterface1 and TestInterface2 interfaces
 *     TEProxy - normal dynamic proxy class implementing TrustEquivalence
 *             interface
 *     ValidMainProxy - normal main proxy
 *     ValidBootProxy - normal boot proxy
 *     InvHandler - test InvocationHandler
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler with ValidMainProxy and
 *        ValidBootProxy as parameters
 *     2) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *        Interface1Proxy, containing the same ProxyTrustInvocationHandler
 *     3) assert that false will be returned
 *     4) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *        Interface2TEProxy, containing the same ProxyTrustInvocationHandler
 *     5) assert that false will be returned
 *     6) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *        Interface12TEProxy, containing the same ProxyTrustInvocationHandler
 *     7) assert that false will be returned
 *     8) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *        TEProxy, containing the same ProxyTrustInvocationHandler
 *     9) assert that false will be returned
 *     10) construct an instance of Interface1TEProxy with InvHandler invocation
 *         handler (Interface1TEProxy1)
 *     11) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *         Interface1TEProxy1
 *     12) assert that false will be returned
 *     13) construct an instance of Interface1TEProxy with the same
 *         ProxyTrustInvocationHandler (Interface1TEProxy2)
 *     14) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *         Interface1TEProxy2
 *     15) assert that true will be returned
 *     16) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with Interface1TEProxy, 'checkTrustEquivalence' method and args[0] =
 *         the same Interface1TEProxy
 *     17) assert that true will be returned
 *     18) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with TEProxy, 'checkTrustEquivalence' method and args[0] =
 *         Interface1TEProxy, containing the same ProxyTrustInvocationHandler
 *     19) assert that false will be returned
 *     20) construct an instance of TEProxy with InvHandler invocation handler
 *         (TEProxy1)
 *     21) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with TEProxy, 'checkTrustEquivalence' method and args[0] = TEProxy1
 *     22) assert that false will be returned
 *     23) construct an instance of TEProxy with the same
 *         ProxyTrustInvocationHandler (TEProxy2)
 *     24) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with TEProxy, 'checkTrustEquivalence' method and args[0] = TEProxy2
 *     25) assert that true will be returned
 *     26) invoke 'invoke' method of constructed ProxyTrustInvocationHandler
 *         with TEProxy, 'checkTrustEquivalence' method and args[0] = the same
 *         TEProxy
 *     27) assert that true will be returned
 * </pre>
 */
public class Invoke_checkTrustEquivalenceTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = createValidMainProxy();
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Object proxy = ProxyTrustUtil.newProxyInstance(
                new Interface1TEImpl(), ptih);
        Method m = TrustEquivalence.class.getDeclaredMethod(
                "checkTrustEquivalence", new Class[] { Object.class });
        TestClassLoader cl = new TestClassLoader();
        Object[] args = new Object[] {
            ProxyTrustUtil.newProxyInstance(new Interface1Impl(), ptih),
            ProxyTrustUtil.newProxyInstance(new Interface2TEImpl(), ptih),
            ProxyTrustUtil.newProxyInstance(new Interface12TEImpl(), ptih),
            ProxyTrustUtil.newProxyInstance(new TEImpl(), ptih),
            ProxyTrustUtil.newProxyInstance(new Interface1TEImpl()),
            ProxyTrustUtil.newProxyInstance(new Interface1TEImpl(), ptih, cl),
            proxy };
        boolean[] expRes = new boolean[] {
            false, false, false, false, false, true, true };
        Object res;

        for (int i = 0; i < args.length; ++i) {
            res = ptihInvoke(ptih, proxy, m, new Object[] { args[i] });

            if (!isOk(res, expRes[i])) {
                // FAIL
                throw new TestException(
                        "'invoke' method of constructed "
                        + "ProxyTrustInvocationHandler returned " + res
                        + ", while Boolean(" + expRes[i]
                        + ") was expected.");
            }
        }
        proxy = ProxyTrustUtil.newProxyInstance(new TEImpl(), ptih);
        args = new Object[] {
            ProxyTrustUtil.newProxyInstance(new Interface1TEImpl(), ptih),
            ProxyTrustUtil.newProxyInstance(new TEImpl()),
            ProxyTrustUtil.newProxyInstance(new TEImpl(), ptih, cl),
            proxy };
        expRes = new boolean[] { false, false, true, true };

        for (int i = 0; i < args.length; ++i) {
            res = ptihInvoke(ptih, proxy, m, new Object[] { args[i] });

            if (!isOk(res, expRes[i])) {
                // FAIL
                throw new TestException(
                        "'invoke' method of constructed "
                        + "ProxyTrustInvocationHandler returned " + res
                        + ", while Boolean(" + expRes[i]
                        + ") was expected.");
            }
        }
    }
}
