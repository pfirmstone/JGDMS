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
package org.apache.river.test.spec.security.proxytrust.proxytrustverifier;

import java.util.logging.Level;

// net.jini
import net.jini.security.proxytrust.ProxyTrustVerifier;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.TrueTrustVerifierContext;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     IsTrustedObject method of ProxyTrustVerifier throws NullPointerException
 *     if any argument is null.
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustVerifier
 *     2) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *        with non-null Object parameter and null Context
 *     3) assert that NullPointerException will be thrown
 *     4) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *        with null Object parameter and non-null Context
 *     5) assert that NullPointerException will be thrown
 *     6) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *        with null Object parameter and null Context
 *     7) assert that NullPointerException will be thrown
 * </pre>
 */
public class IsTrustedObject_NullPointerExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustVerifier ptv = new ProxyTrustVerifier();

        try {
            ptvIsTrustedObject(ptv, new Object(), null);

            // FAIL
            throw new TestException(
                    "'isTrustedObject' method call of ProxyTrustVerifier "
                    + "did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("'isTrustedObject' method call of "
                    + "ProxyTrustVerifier threw NullPointerException "
                    + "as expected.");
        }

        try {
            ptvIsTrustedObject(ptv, null,
                    new TrueTrustVerifierContext(new Object[] { validMC }));

            // FAIL
            throw new TestException(
                    "'isTrustedObject' method call of ProxyTrustVerifier "
                    + "did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("'isTrustedObject' method call of "
                    + "ProxyTrustVerifier threw NullPointerException "
                    + "as expected.");
        }

        try {
            ptvIsTrustedObject(ptv, null, null);

            // FAIL
            throw new TestException(
                    "'isTrustedObject' method call of ProxyTrustVerifier "
                    + "did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("'isTrustedObject' method call of "
                    + "ProxyTrustVerifier threw NullPointerException "
                    + "as expected.");
        }
    }
}
