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

// net.jini
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.core.constraint.RemoteMethodControl;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.ProxyTrustUtil;
import org.apache.river.test.spec.security.proxytrust.util.RMCImpl;
import org.apache.river.test.spec.security.proxytrust.util.RMCPTImpl;
import org.apache.river.test.spec.security.proxytrust.util.PTTEImpl;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     ProxyTrustInvocationHandler constructor IllegalArgumentException if the
 *     main proxy is not an instance of TrustEquivalence, or the bootstrap proxy
 *     is not an instance of RemoteMethodControl or TrustEquivalence
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     RMCNonTEMainProxy - class which implements RemoteMethodControl interface
 *             but does not implements TrustEquivalence interface
 *     RMCPTNonTEBootProxy - class which implements ProxyTrust and
 *             RemoteMethodControl interfaces but does not implement
 *             TrustEquivalence one
 *     PTTENonRMCBootProxy - class which implements ProxyTrust and
 *             TrustEquivalence interfaces but does not implements
 *             RemoteMethodControl one
 *     ValidMainProxy - class which implements RemoteMethodControl and
 *             TrustEquivalence interfaces
 *     ValidBootProxy - class which implements ProxyTrust, RemoteMethodControl
 *             and TrustEquivalence interfaces
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with ValidMainProxy and
 *        RMCPTNonTEBootProxy as parameters
 *     2) assert that IllegalArgumentException will be thrown
 *     3) construct ProxyTrustInvocationHandler2 with ValidMainProxy and
 *        PTTENonRMCBootProxy as parameters
 *     4) assert that IllegalArgumentException will be thrown
 *     5) construct ProxyTrustInvocationHandler3 with RMCNonTEMainProxy and
 *        ValidBootProxy as parameters
 *     6) assert that IllegalArgumentException will be thrown
 * </pre>
 */
public class PTIH_IllegalArgumentExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = createValidMainProxy();
        ProxyTrust boot = (ProxyTrust) ProxyTrustUtil.newProxyInstance(
                new RMCPTImpl());

        try {
            createPTIH(main, boot);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "IllegalArgumentException was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + "IllegalArgumentException as expected.");
        }
        boot = (ProxyTrust) ProxyTrustUtil.newProxyInstance(new PTTEImpl());

        try {
            createPTIH(main, boot);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "IllegalArgumentException was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + "IllegalArgumentException as expected.");
        }
        main = (RemoteMethodControl) ProxyTrustUtil.newProxyInstance(
                new RMCImpl());
        boot = createValidBootProxy();

        try {
            createPTIH(main, boot);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "IllegalArgumentException was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + "IllegalArgumentException as expected.");
        }
    }
}
