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
import com.sun.jini.test.spec.security.proxytrust.util.InvHandler;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     For all other methods, invoke method of ProxyTrustInvocationHandler
 *     returns the object obtained by delegating to the main proxy: the
 *     specified method is reflectively invoked on the main proxy with the
 *     specified arguments, unless the method's declaring class is not public
 *     but the main proxy is an instance of that declaring class and the main
 *     proxy's class is public, in which case the corresponding method of the
 *     main proxy's class is reflectively invoked instead. Invoke returns the
 *     value returned by executing the specified method on the specified proxy
 *     with the specified arguments, or null if the method has void return type.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Interface1Proxy - normal dynamic proxy class implementing TestInterface1
 *     NPIProxy - normal dynamic proxy class implementing NonPublicInterface
 *     Interface1MainProxy - normal main proxy implementing TestInterface1 with
 *             test1(Integer) method implementation returning it's argument
 *     NPIMainProxy - normal main proxy implementing NonPublicInterface with
 *             test1(Integer) method implementation returning it's argument + 1
 *     ValidBootProxy - normal boot proxy
 *     TestInterface1 - public interface, having "Integer test1(Integer)" and
 *             "void test()" methods
 *     NonPublicInterface - nonpublic interface, having "Integer test1(Integer)"
 *             and "void test()" method
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with NPIMainProxy and
 *        ValidBootProxy as parameters
 *     2) construct NPIProxy with the same ProxyTrustInvocationHandler as
 *        tested one
 *     3) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with NPIProxy, 'test1' method and args[0] = new Integer(5)
 *     4) assert that 'test1(5)' method of the NPIMainProxy class will
 *        be reflectively invoked 1 time.
 *     5) assert that Integer(6) will be returned
 *     6) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with NPIProxy, 'test' and args = null
 *     7) assert that 'test()' method of the NPIMainProxy class will be
 *        reflectively invoked 1 time.
 *     8) assert that null will be returned
 *     9) construct ProxyTrustInvocationHandler2 with Interface1MainProxy and
 *        ValidBootProxy as parameters
 *     10) construct Interface1Proxy with the same ProxyTrustInvocationHandler
 *         as tested one
 *     11) invoke 'invoke' method of constructed ProxyTrustInvocationHandler2
 *         with Interface1Proxy, 'test1' method and args[0] = new Integer(5)
 *     12) assert that 'test1(5)' method of the Interface1MainProxy
 *         class will be reflectively invoked 1 time.
 *     13) assert that Integer(5) will be returned
 *     14) invoke 'invoke' method of constructed ProxyTrustInvocationHandler2
 *         with Interface1Proxy, 'test' method and args = null
 *     15) assert that 'test()' method of the Interface1MainProxy class will be
 *         reflectively invoked 1 time.
 *     16) assert that null will be returned
 * </pre>
 */
public class InvokeTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RMCTENPIImpl mImpl = new RMCTENPIImpl();
        RemoteMethodControl main = (RemoteMethodControl)
                ProxyTrustUtil.newProxyInstance(mImpl, new InvHandler(mImpl));
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Object proxy = ProxyTrustUtil.newProxyInstance(new NPIImpl(), ptih);
        Method m = NonPublicInterface.class.getDeclaredMethod("test1",
                new Class[] { int.class });
        Object res = ptihInvoke(ptih, proxy, m,
                new Object[] { new Integer(5) });

        if (mImpl.getTest1Num() != 1) {
            // FAIL
            throw new TestException(
                    "'test1' method of main proxy was called "
                    + mImpl.getTest1Num() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'test1' method of main proxy was called "
                + " 1 time as expected.");

        if (((Integer) res).intValue() != 6) {
            // FAIL
            throw new TestException(
                    "'test1' method of main proxy returned " + res
                    + " while 6 was expected.");
        }

        // PASS
        logger.fine("'test1' method of main proxy returned 6 as expected.");
        m = NonPublicInterface.class.getDeclaredMethod("test",
                new Class[0]);
        res = ptihInvoke(ptih, proxy, m, null);

        if (mImpl.getTestNum() != 1) {
            // FAIL
            throw new TestException(
                    "'test' method of main proxy was called "
                    + mImpl.getTestNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'test' method of main proxy was called "
                + " 1 time as expected.");

        if (res != null) {
            // FAIL
            throw new TestException(
                    "'test' method of main proxy returned " + res
                    + " while null was expected.");
        }

        // PASS
        logger.fine("'test' method of main proxy returned null "
                + "as expected.");
        Interface1RMCTEImpl mImpl1 = new Interface1RMCTEImpl();
        main = newMainProxy(mImpl1);
        ptih = createPTIH(main, boot);
        proxy = ProxyTrustUtil.newProxyInstance(new Interface1Impl(), ptih);
        m = TestInterface1.class.getDeclaredMethod("test1",
                new Class[] { int.class });
        res = ptihInvoke(ptih, proxy, m, new Object[] { new Integer(5) });

        if (mImpl1.getTest1Num() != 1) {
            // FAIL
            throw new TestException(
                    "'test1' method of main proxy was called "
                    + mImpl1.getTest1Num()
                    + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'test1' method of main proxy was called "
                + " 1 time as expected.");

        if (((Integer) res).intValue() != 5) {
            // FAIL
            throw new TestException(
                    "'test1' method of main proxy returned " + res
                    + " while 5 was expected.");
        }

        // PASS
        logger.fine("'test1' method of main proxy returned 6 as expected.");
        m = TestInterface1.class.getDeclaredMethod("test", new Class[0]);
        res = ptihInvoke(ptih, proxy, m, null);

        if (mImpl1.getTestNum() != 1) {
            // FAIL
            throw new TestException(
                    "'test' method of main proxy was called "
                    + mImpl.getTestNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'test' method of main proxy was called "
                + " 1 time as expected.");

        if (res != null) {
            // FAIL
            throw new TestException(
                    "'test' method of main proxy returned " + res
                    + " while null was expected.");
        }

        // PASS
        logger.fine("'test' method of main proxy returned null "
                + "as expected.");
    }
}
