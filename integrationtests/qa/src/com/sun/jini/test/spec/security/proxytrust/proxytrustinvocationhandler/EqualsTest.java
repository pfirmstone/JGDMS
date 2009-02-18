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
import java.lang.reflect.InvocationHandler;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.Interface1RMCTEImpl;
import com.sun.jini.test.spec.security.proxytrust.util.Interface2RMCPTTEImpl;
import com.sun.jini.test.spec.security.proxytrust.util.InvHandler;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Equals method of ProxyTrustInvocationHandler returns true if the argument
 *     is an instance of this class with the same main proxy and the same
 *     bootstrap proxy, and false otherwise.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     ValidMainProxy - normal main proxy
 *     ValidMainProxy1 - another normal main proxy
 *     ValidBootProxy - normal boot proxy
 *     ValidBootProxy1 - another normal boot proxy
 *     InvHandler - test invocation handler which is not an instance of
 *         ProxyTrustInvocationHandler
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with ValidMainProxy and
 *        ValidBootProxy as parameters
 *     2) construct ProxyTrustInvocationHandler2 with the same ValidMainProxy as
 *        for ProxyTrustInvocationHandler1 and ValidBootProxy1 as parameter
 *     3) construct ProxyTrustInvocationHandler3 with ValidMainProxy1 and
 *        the same ValidBootProxy as for ProxyTrustInvocationHandler1 as
 *        parameters
 *     4) construct ProxyTrustInvocationHandler4 with the same ValidMainProxy
 *        and ValidBootProxy as for ProxyTrustInvocationHandler1 as parameters
 *     5) invoke 'equals' method of constructed
 *        ProxyTrustInvocationHandler1 with the same
 *        ProxyTrustInvocationHandler1 as a parameter
 *     6) assert that true will be returned
 *     7) invoke 'equals' method of constructed
 *        ProxyTrustInvocationHandler1 with ProxyTrustInvocationHandler2
 *        as a parameter
 *     8) assert that false will be returned
 *     9) invoke 'equals' method of constructed
 *        ProxyTrustInvocationHandler1 with ProxyTrustInvocationHandler3
 *        as a parameter
 *     10) assert that false will be returned
 *     11) invoke 'equals' method of constructed
 *         ProxyTrustInvocationHandler1 with InvHandler as a parameter
 *     12) assert that false will be returned
 *     13) invoke 'equals' method of constructed
 *         ProxyTrustInvocationHandler1 with ProxyTrustInvocationHandler4 as
 *         a parameter
 *     14) assert that true will be returned
 * </pre>
 */
public class EqualsTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = newMainProxy(new Interface1RMCTEImpl());
        ProxyTrust boot = newBootProxy(new Interface2RMCPTTEImpl());
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        InvocationHandler[] ih = new InvocationHandler [] {
            ptih,
            createPTIH(main,
                       newBootProxy(new Interface2RMCPTTEImpl())),
            createPTIH(newMainProxy(new Interface1RMCTEImpl()),
                       boot),
            new InvHandler(new Interface1RMCTEImpl()),
            createPTIH(main, boot) };
        boolean[] expRes = new boolean[] { true, false, false, false, true };
        boolean res;

        for (int i = 0; i < ih.length; ++i) {
            res = ptihEquals(ptih, ih[i]);

            if (res != expRes[i]) {
                // FAIL
                throw new TestException(
                        "'equals' method of constructed "
                        + "ProxyTrustInvocationHandler returned " + res
                        + ", while " + expRes[i] + " was expected.");
            }

            // PASS
            logger.fine("'equals' method of constructed "
                    + "ProxyTrustInvocationHandler returned " + res
                    + " as expected.");
        }
    }
}
