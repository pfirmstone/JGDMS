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
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.ProxyTrustUtil;
import org.apache.river.test.spec.security.proxytrust.util.RMCImpl;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Invoke method of ProxyTrustInvocationHandler throws
 *     IllegalArgumentException if the declaring class of the specified method
 *     is not public and either the main proxy is not an instance of that
 *     declaring class or the main proxy's class is not public. An exception is
 *     thrown if the specified method is RemoteMethodControl.setConstraints
 *     and the specified proxy is not an instance of a dynamic proxy class
 *     containing this invocation handler.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     RMCNonProxy - non-proxy class implementing RemoteMethodControl
 *     RMCProxy - proxy class implementing RemoteMethodControl containing
 *             another ProxyTrustInvocationHandler
 *     NPIProxy - normal dynamic proxy class implementing NonPublicInterface
 *     NPIMainProxy - normal main proxy implementing NonPublicInterface
 *     Interface1MainProxy - normal main proxy which does not implement
 *             NonPublicInterface
 *     ValidBootProxy - normal boot proxy
 *     NonPublicInterface - nonpublic interface, having test() method
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with Interface1MainProxy and
 *        ValidBootProxy as parameters
 *     2) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with NPIProxy and 'test()' method
 *     3) assert that IllegalArgumentException will be thrown
 *     4) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with RMCNonProxy and 'setConstraints' method
 *     5) assert that some kind of Exception will be thrown
 *     6) invoke 'invoke' method of constructed ProxyTrustInvocationHandler1
 *        with RMCProxy and 'setConstraints' method
 *     7) assert that some kind of Exception will be thrown
 *     8) construct ProxyTrustInvocationHandler2 with RMCTENPINonPublicClass and
 *        ValidBootProxy as parameters
 *     9) invoke 'invoke' method of constructed ProxyTrustInvocationHandler2
 *        with NPIProxy and 'test()' method
 *     10) assert that some IllegalArgumentException will be thrown
 * </pre>
 */
public class Invoke_ExceptionsTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = createValidMainProxy();
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Object proxy = ProxyTrustUtil.newProxyInstance(new NPIImpl(), ptih);
        Method m = NonPublicInterface.class.getDeclaredMethod(
                "test", new Class[0]);
        Object res = null;

        try {
            ptihInvoke(ptih, proxy, m, null);

            // FAIL
            throw new TestException(
                    "'invoke' invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "IllegalArgumentException was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("'invoke' invocation of "
                    + "ProxyTrustInvocationHandler threw " + iae
                    + " as expected.");
        }
        proxy = new RMCImpl();
        m = RemoteMethodControl.class.getDeclaredMethod(
                "setConstraints", new Class[] { MethodConstraints.class });
        MethodConstraints mc = new BasicMethodConstraints(
                new InvocationConstraints(
                    new InvocationConstraint[] { ClientAuthentication.YES },
                    null));

        try {
            ptihInvoke(ptih, proxy, m, new Object[] { mc });

            // FAIL
            throw new TestException(
                    "'invoke' invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "some kind of exception was expected.");
        } catch (Exception e) {
            // PASS
            logger.fine("'invoke' invocation of "
                    + "ProxyTrustInvocationHandler threw " + e
                    + " as expected.");
        }
        proxy = ProxyTrustUtil.newProxyInstance(new RMCImpl(),
                new ProxyTrustInvocationHandler(main, boot));

        try {
            ptihInvoke(ptih, proxy, m, new Object[] { mc });

            // FAIL
            throw new TestException(
                    "'invoke' invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "some kind of exception was expected.");
        } catch (Exception e) {
            // PASS
            logger.fine("'invoke' invocation of "
                    + "ProxyTrustInvocationHandler threw " + e
                    + " as expected.");
        }
        ptih = createPTIH(new RMCTENPINonPublicClass(), boot);
        proxy = ProxyTrustUtil.newProxyInstance(new NPIImpl(), ptih);
        m = NonPublicInterface.class.getDeclaredMethod("test",
                new Class[0]);

        try {
            ptihInvoke(ptih, proxy, m, null);

            // FAIL
            throw new TestException(
                    "'invoke' invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "some kind of exception was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("'invoke' invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + iae + " as expected.");
        }
    }
}
