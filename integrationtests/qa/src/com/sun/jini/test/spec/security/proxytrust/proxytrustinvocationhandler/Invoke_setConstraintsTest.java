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
import java.lang.reflect.Proxy;

// net.jini
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;
import com.sun.jini.test.spec.security.proxytrust.util.RMCTEImpl;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     If the specified method is RemoteMethodControl.setConstraints, invoke
 *     method of ProxyTrustInvocationHandler returns a new proxy (an instance of
 *     the same class as the specified proxy) containing an instance of this
 *     class with a new main proxy and the same bootstrap proxy from this
 *     handler. The new main proxy is obtained by delegating to the existing
 *     main proxy of this handler (as described below). The specified proxy's
 *     class must have a public constructor with a single parameter of type
 *     InvocationHandler. The delegating proceed as following: the specified
 *     method is reflectively invoked on the main proxy with the specified
 *     arguments, unless the method's declaring class is not public but the
 *     main proxy is an instance of that declaring class and the main proxy's
 *     class is public, in which case the corresponding method of the main
 *     proxy's class is reflectively invoked instead.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     RMCProxy - normal dynamic proxy class implementing RemoteMethodControl
 *             interface
 *     ValidMainProxy - normal main proxy
 *     ValidBootProxy - normal boot proxy
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler with ValidMainProxy and
 *        ValidBootProxy as parameters
 *     2) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        RMCProxy and 'setConstraints' method
 *     3) assert that value returned by 'invoke' method will implement the same
 *        interfaces as RMCProxy
 *     4) assert that setConstraints method of ValidMainProxy class will
 *        be invoked 1 time with the same arguments as specified in arg[] array
 *     5) assert that value returned by 'invoke' method will contain an instance
 *        of ProxyTrustInvocationHandler
 *     6) assert that invocation handler containing in the value returned by
 *        'invoke' method contains the same ValidBootProxy
 * </pre>
 */
public class Invoke_setConstraintsTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RMCTEImpl mImpl = new RMCTEImpl();
        RemoteMethodControl main = (RemoteMethodControl)
                ProxyTrustUtil.newProxyInstance(mImpl);
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Object proxy = ProxyTrustUtil.newProxyInstance(
                new RMCTEImpl(), ptih);
        Method m = RemoteMethodControl.class.getDeclaredMethod(
                "setConstraints", new Class[] { MethodConstraints.class });
        MethodConstraints mc = new BasicMethodConstraints(
                new InvocationConstraints(
                    new InvocationConstraint[] { ClientAuthentication.YES },
                    null));
        Object res = ptihInvoke(ptih, proxy, m, new Object[] { mc });

        if (mImpl.getSetConstraintsNum() != 1) {
            // FAIL
            throw new TestException(
                    "'setConstraints' method of main proxy was called "
                    + mImpl.getSetConstraintsNum()
                    + " times while 1 was expected.");
        }

        // PASS
        logger.fine("'setConstraints' method of main proxy was called "
                + " 1 time as expected.");

        if (mImpl.getSetConstraintsArg() != mc) {
            // FAIL
            throw new TestException(
                    "'setConstraints' method of main proxy was called with "
                    + mImpl.getSetConstraintsArg()
                    + " argument while " + mc + " was expected.");
        }

        // PASS
        logger.fine("'setConstraints' method of main proxy was called with "
                + "the same argument as 'invoke' method of "
                + "ProxyTrustInvocationHandler as expected.");

        if (!ProxyTrustUtil.equalInterfaces(res, main)) {
            // FAIL
            throw new TestException(
                    "List of interfaces implemented by class returned by "
                    + "'invoke' method call: "
                    + ProxyTrustUtil.interfacesToString(res)
                    + " is not equal to the one required: "
                    + ProxyTrustUtil.interfacesToString(main));
        }

        // PASS
        logger.fine("List of interfaces implemented by class returned by "
                + "'invoke' method call is the same as the one for "
                + "main proxy class as expected.");

        if (!(Proxy.getInvocationHandler(res)
                instanceof ProxyTrustInvocationHandler)) {
            // FAIL
            throw new TestException(
                    "Result of 'invoke' method invocation of "
                    + "ProxyTrustInvocationHandler does not contain an "
                    + "instance of ProxyTrustInvocationHandler.");
        }

        // PASS
        logger.fine("Result of 'invoke' method invocation of "
                + "ProxyTrustInvocationHandler contains an instance of "
                + "ProxyTrustInvocationHandler as expected.");
        m = ProxyTrustInvocationHandler.class.getDeclaredMethod(
                "getProxyTrustIterator", new Class[0]);
        m.setAccessible(true);
        Object obj = ((ProxyTrustIterator) m.invoke(
                Proxy.getInvocationHandler(res), null)).next();

        if (boot != obj) {
            // FAIL
            throw new TestException(
                    "Invocation handler of result of 'invoke' "
                    + "method invocation of ProxyTrustInvocationHandler "
                    + "does not contain the same boot proxy.");
        }

        // PASS
        logger.fine("Invocation handler of result of 'invoke' "
                    + "method invocation of ProxyTrustInvocationHandler "
                    + "contains the same boot proxy as expected.");
    }
}
