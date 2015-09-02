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
import java.lang.reflect.InvocationHandler;

// net.jini
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.Interface1RMCTEImpl;
import org.apache.river.test.spec.security.proxytrust.util.Interface2RMCPTTEImpl;
import org.apache.river.test.spec.security.proxytrust.util.Interface3RMCTEImpl;
import org.apache.river.test.spec.security.proxytrust.util.Interface4RMCPTTEImpl;
import org.apache.river.test.spec.security.proxytrust.util.InvHandler;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     CheckTrustEquivalence method of ProxyTrustInvocationHandler returns true
 *     if the argument is an instance of this class, and calling the
 *     checkTrustEquivalence method on the main proxy of this invocation
 *     handler, passing the main proxy of the argument, returns true, and
 *     calling the checkTrustEquivalence method on the bootstrap proxy of this
 *     invocation handler, passing the bootstrap proxy of the argument, returns
 *     true, and returns false otherwise.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Interface1MainProxy - normal main proxy implementing TestInterface1 whose
 *             checkTrustEquivalence method returns true if argument implements
 *             the same interfaces and false otherwise
 *     Interface3MainProxy - normal main proxy implementing TestInterface3
 *     Interface2BootProxy - normal boot proxy implementing TestInterface2 whose
 *             checkTrustEquivalence method returns true if argument implements
 *             the same interfaces and false otherwise
 *     Interface4BootProxy - normal boot proxy implementing TestInterface4
 *     InvHandler - test invocation handler which is not an instance of
 *             ProxyTrustInvocationHandler
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with Interface1MainProxy and
 *        Interface2BootProxy as parameters
 *     2) construct another Interface2BootProxy instance (Interface2BootProxy1)
 *     3) construct ProxyTrustInvocationHandler2 with Interface3MainProxy and
 *        Interface2BootProxy1 instance as parameters
 *     4) construct another Interface1MainProxy instance (Interface1MainProxy1)
 *     5) construct ProxyTrustInvocationHandler3 with Interface1MainProxy1
 *        and Interface4BootProxy as parameters
 *     6) construct another Interface1MainProxy instance (Interface1MainProxy2)
 *     7) construct another Interface2BootProxy instance (Interface2BootProxy2)
 *     8) construct ProxyTrustInvocationHandler4 with Interface1MainProxy1
 *        and Interface2BootProxy1 as parameters
 *     9) invoke 'checkTrustEquivalence' method of constructed
 *        ProxyTrustInvocationHandler1 with InvHandler as a parameter
 *     10) assert that false will be returned
 *     11) invoke 'checkTrustEquivalence' method of constructed
 *         ProxyTrustInvocationHandler1 with ProxyTrustInvocationHandler2
 *         as a parameter
 *     12) assert that false will be returned
 *     13) invoke 'checkTrustEquivalence' method of constructed
 *         ProxyTrustInvocationHandler1 with ProxyTrustInvocationHandler3
 *         as a parameter
 *     14) assert that false will be returned
 *     15) invoke 'checkTrustEquivalence' method of constructed
 *         ProxyTrustInvocationHandler1 with ProxyTrustInvocationHandler4
 *         as a parameter
 *     16) assert that true will be returned
 *     17) invoke 'checkTrustEquivalence' method of constructed
 *         ProxyTrustInvocationHandler1 with the same
 *         ProxyTrustInvocationHandler1 as a parameter
 *     18) assert that true will be returned
 * </pre>
 */
public class CheckTrustEquivalenceTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustInvocationHandler ptih = createPTIH(
                newMainProxy(new Interface1RMCTEImpl()),
                newBootProxy(new Interface2RMCPTTEImpl()));
        InvocationHandler[] ih = new InvocationHandler [] {
            new InvHandler(new Interface1RMCTEImpl()),
            createPTIH(newMainProxy(new Interface3RMCTEImpl()),
                       newBootProxy(new Interface2RMCPTTEImpl())),
            createPTIH(newMainProxy(new Interface1RMCTEImpl()),
                       newBootProxy(new Interface4RMCPTTEImpl())),
            createPTIH(newMainProxy(new Interface1RMCTEImpl()),
                       newBootProxy(new Interface2RMCPTTEImpl())),
            ptih };
        boolean[] expRes = new boolean[] { false, false, false, true, true };
        boolean res;

        for (int i = 0; i < ih.length; ++i) {
            res = ptihCheckTrustEquivalence(ptih, ih[i]);

            if (res != expRes[i]) {
                // FAIL
                throw new TestException(
                        "'checkTrustEquivalence' method of constructed "
                        + "ProxyTrustInvocationHandler returned " + res
                        + ", while " + expRes[i] + " was expected.");
            }

            // PASS
            logger.fine("'checkTrustEquivalence' method of constructed "
                    + "ProxyTrustInvocationHandler returned " + res
                    + " as expected.");
        }
    }
}
