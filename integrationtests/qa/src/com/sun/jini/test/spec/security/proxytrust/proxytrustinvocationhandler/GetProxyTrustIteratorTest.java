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
import net.jini.security.proxytrust.ProxyTrustIterator;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     GetProxyTrustIterator method of ProxyTrustInvocationHandler returns an
 *     iterator that produces the bootstrap proxy as the only element of the
 *     iteration.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     ValidMainProxy - normal main proxy
 *     ValidBootProxy - normal boot proxy
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler with ValidMainProxy and
 *        ValidBootProxy as parameters
 *     2) reflectively "setAccessible(true)" for getProxyTrustIterator
 *        method
 *     3) reflectively invoke 'getProxyTrustIterator' method of constructed
 *        ProxyTrustInvocationHandler
 *     4) assert that ProxyTrustIterator will be returned with ValidBootProxy as
 *        the only element of the iteration.
 * </pre>
 */
public class GetProxyTrustIteratorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = createValidMainProxy();
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Method m = ProxyTrustInvocationHandler.class.getDeclaredMethod(
                "getProxyTrustIterator", new Class[0]);
        m.setAccessible(true);
        ProxyTrustIterator res = (ProxyTrustIterator) m.invoke(ptih, null);

        if (!res.hasNext()) {
            // FAIL
            throw new TestException(
                    "'getProxyTrustIterator' method of constructed "
                    + "ProxyTrustInvocationHandler returned empty "
                    + "ProxyTrustIterator.");
        }
        Object cont = res.next();

        if (cont != boot) {
            // FAIL
            throw new TestException(
                    "'getProxyTrustIterator' method of constructed "
                    + "ProxyTrustInvocationHandler returned "
                    + "ProxyTrustIterator, 1-st element of which contains "
                    + cont + " while " + boot + " was expected.");
        }

        if (res.hasNext()) {
            // FAIL
            throw new TestException(
                    "'getProxyTrustIterator' method of constructed "
                    + "ProxyTrustInvocationHandler returned "
                    + "ProxyTrustIterator which contains more than 1 "
                    + "element.");
        }
    }
}
