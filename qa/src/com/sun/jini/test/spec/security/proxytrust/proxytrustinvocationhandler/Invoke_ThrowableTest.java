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
package com.sun.jini.test.spec.security.proxytrust.proxytrustinvocationhandler;

import java.util.logging.Level;

// java.lang
import java.lang.reflect.Method;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;
import com.sun.jini.test.spec.security.proxytrust.util.TestInterface1;
import com.sun.jini.test.spec.security.proxytrust.util.Interface1Impl;
import com.sun.jini.test.spec.security.proxytrust.util.Interface1RMCTEImpl;
import com.sun.jini.test.spec.security.proxytrust.util.FakeException;
import com.sun.jini.test.spec.security.proxytrust.util.InvHandler;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Invoke method of ProxyTrustInvocationHandler throws
 *     the exception thrown by executing the specified method.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Interface1Proxy - normal dynamic proxy class implementing TestInterface1
 *     NPIProxy - normal dynamic proxy class implementing NonPublicInterface
 *     Interface1MainProxy - normal main proxy implementing TestInterface1 with
 *             exTest() method throwing FakeException
 *     NPIMainProxy - normal main proxy implementing NonPublicInterface with
 *             exTest() method throwing FakeException
 *     ValidBootProxy - normal boot proxy
 *     TestInterface1 - public interface, having
 *             "public void exTest(void) throws FakeException" method
 *     NonPublicInterface - nonpublic interface, having 
 *             "public void exTest(void) throws FakeException" method
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with NPIMainProxy and
 *        ValidBootProxy as parameters
 *     2) construct NPIProxy with the same ProxyTrustInvocationHandler as
 *        tested one
 *     3) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with NPIProxy, 'exTest' method
 *     4) assert that the exTest method of the NPIMainProxy class will be
 *        reflectively invoked 1 time.
 *     5) assert that FakeException will be thrown
 *     6) construct ProxyTrustInvocationHandler2 with Interface1MainProxy and
 *        ValidBootProxy as parameters
 *     7) construct Interface1Proxy with the same ProxyTrustInvocationHandler as
 *        tested one
 *     8) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with Interface1Proxy, 'exTest' method
 *     9) assert that the exTest method of the Interface1MainProxy class
 *        will be reflectively invoked 1 time.
 *     10) assert that FakeException will be thrown
 * </pre>
 */
public class Invoke_ThrowableTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RMCTENPIImpl mImpl = new RMCTENPIImpl();
        RemoteMethodControl main = (RemoteMethodControl)
                ProxyTrustUtil.newProxyInstance(mImpl,
                        new InvHandler(mImpl));
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Object proxy = ProxyTrustUtil.newProxyInstance(new NPIImpl(), ptih);
        Method m = NonPublicInterface.class.getDeclaredMethod("exTest",
                new Class[0]);
        Object res = null;

        try {
            res = ptihInvoke(ptih, proxy, m, null);

            // FAIL
            throw new TestException(
                    "'invoke' invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "FakeException was expected.");
        } catch (FakeException fe) {
            // PASS
            logger.fine("'invoke' invocation of "
                    + "ProxyTrustInvocationHandler threw " + fe
                    + " as expected.");
        }

        if (mImpl.getExTestNum() != 1) {
            // FAIL
            throw new TestException(
                    "'exTest' method of main proxy was called "
                    + mImpl.getExTestNum()
                    + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'exTest' method of main proxy was called "
                + " 1 time as expected.");
        Interface1RMCTEImpl mImpl1 = new Interface1RMCTEImpl();
        main = newMainProxy(mImpl1);
        ptih = createPTIH(main, boot);
        proxy = ProxyTrustUtil.newProxyInstance(new Interface1Impl(), ptih);
        m = TestInterface1.class.getDeclaredMethod("exTest", new Class[0]);

        try {
            res = ptihInvoke(ptih, proxy, m, null);

            // FAIL
            throw new TestException(
                    "'invoke' invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "FakeException was expected.");
        } catch (FakeException fe) {
            // PASS
            logger.fine("'invoke' invocation of "
                    + "ProxyTrustInvocationHandler threw " + fe
                    + " as expected.");
        }

        if (mImpl.getExTestNum() != 1) {
            // FAIL
            throw new TestException(
                    "'exTest' method of main proxy was called "
                    + mImpl.getExTestNum()
                    + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'exTest' method of main proxy was called "
                + " 1 time as expected.");
    }
}
