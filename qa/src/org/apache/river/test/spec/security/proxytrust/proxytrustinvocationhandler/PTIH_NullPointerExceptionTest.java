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


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     ProxyTrustInvocationHandler constructor throws NullPointerException if
 *     any argument is null.
 *
 * Action
 *   The test performs the following steps:
 *     1) construct a ProxyTrustInvocationHandler with non-null value for main
 *        proxy and null value for bootstrap proxy
 *     2) assert that NullPointerException will be thrown
 *     3) construct a ProxyTrustInvocationHandler with null value for main proxy
 *        and non-null value for bootstrap proxy
 *     4) assert that NullPointerException will be thrown
 *     5) construct a ProxyTrustInvocationHandler with null value for main proxy
 *        and null value for bootstrap proxy
 *     6) assert that NullPointerException will be thrown
 * </pre>
 */
public class PTIH_NullPointerExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = createValidMainProxy();
        ProxyTrust boot = createValidBootProxy();

        try {
            createPTIH(main, null);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + "NullPointerException as expected.");
        }

        try {
            createPTIH(null, boot);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + "NullPointerException as expected.");
        }

        try {
            createPTIH(null, null);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustInvocationHandler "
                    + "did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "ProxyTrustInvocationHandler threw "
                    + "NullPointerException as expected.");
        }
    }
}
