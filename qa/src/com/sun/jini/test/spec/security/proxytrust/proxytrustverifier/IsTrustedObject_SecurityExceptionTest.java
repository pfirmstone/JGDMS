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
package com.sun.jini.test.spec.security.proxytrust.proxytrustverifier;

import java.util.logging.Level;

// net.jini
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustVerifier;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;
import com.sun.jini.test.spec.security.proxytrust.util.NonProxyObjectThrowingSE;
import com.sun.jini.test.spec.security.proxytrust.util.IHandlerThrowingSE;
import com.sun.jini.test.spec.security.proxytrust.util.ValidNonProxyObject;
import com.sun.jini.test.spec.security.proxytrust.util.ValidIHandler;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustThrowingSE1;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustThrowingSE2;
import com.sun.jini.test.spec.security.proxytrust.util.TrueProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.TrueTrustVerifierContext;
import com.sun.jini.test.spec.security.proxytrust.util.TrustVerifierContext;
import
  com.sun.jini.test.spec.security.proxytrust.util.TrustVerifierContextThrowingSE;


/**
 * <pre>
 * Purpose
 *   This test verifies the isTrustedObject method of ProxyTrustVerifier
 *   when SecurityExceptions are thrown.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonProxyObjectThrowingSE - class whose 'getProxyTrustIterator' method
 *             always throws SecurityException
 *     ValidNonProxyObject - class having constructor with 1 parameter: Object[]
 *             and public method 'ProxyTrustIterator getProxyTrustIterator()'
 *             returning TestTrustIterator with objects specified in
 *             constructor's parameter
 *     IHandlerThrowingSE - invocation handler whose 'getProxyTrustIterator'
 *             method always throws SecurityException
 *     ValidIHandler - invocation handler having constructor
 *             with 1 parameter: Object[] and having public method
 *             'ProxyTrustIterator getProxyTrustIterator()' returning
 *             TrustIterator with objects specified in constructor's parameter
 *     ProxyTrustThrowingSE1 - class implementing ProxyTrust and
 *             RemoteMethodControl interfaces whose 'getProxyVerifier'
 *             method always throws SecurityException
 *     ProxyTrustThrowingSE2 - class implementing RemoteMethodControl and
 *             ProxyTrust interfaces whose 'getProxyVerifier' method returns
 *             TrustVerifierThrowingSE instance
 *     TrueProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns
 *             TrueTrustVerifier instance
 *     ValidMCContext - array of objects having MethodConstraints instance with
 *             non-empty security constraints for the
 *             ProxyTrust.getProxyVerifier method
 *     TrustVerifierContextThrowingSE - class implementing TrustVerifier.Context
 *             interface having constructor with 1 parameter: Collection. This
 *             value will be returned by getCallerContext method.
 *             'isTrustedObject' method of this class always throws
 *             SecurityException
 *     TrueTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface having constructor with 1 parameter:  Object[].
 *             Collection of those objects will be returned by getCallerContext
 *             method. 'isTrustedObject' method of this class always returns
 *             true
 *     TestTrustIterator - class implementing ProxyTrustIterator interface
 *             having constructor with 1 paramter: Object[], whose 'next' method
 *             returns objects specified in constructor's parameter in the same
 *             sequence (i.e. obj[0] first, then obj[1] ... etc.)
 *     TrustVerifierThrowingSE - class implementing TrustVerifier interface
 *             whose 'isTrustedObject' method always throws SecurityException
 *     TrueTrustVerifier - class implementing TrustVerifier interface whose
 *             'isTrustedObject' method always returns true
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustVerifier
 *     2) construct TrueTrustVerifierContext with ValidMCContext as a parameter
 *     3) construct NonProxyObjectThrowingSE
 *     4) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        NonProxyObjectThrowingSE and TrueTrustVerifierContext as parameters
 *     5) assert that SecurityException will be thrown
 *
 *     6) construct dynamic proxy instance with IHandlerThrowingSE invocation
 *        handler (Proxy1)
 *     7) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        Proxy1 and TrueTrustVerifierContext as parameters
 *     8) assert that SecurityException will be thrown
 *
 *     9) construct ValidNonProxyObject1 with ProxyTrustThrowingSE1 instance as
 *        a parameter
 *     10) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject1 and TrueTrustVerifierContext as parameters
 *     11) assert that SecurityException will be thrown
 *
 *     12) construct ValidIHandler1 with ProxyTrustThrowingSE1 instance as a
 *         parameter
 *     13) construct dynamic proxy instance with ValidIHandler1 invocation
 *         handler (Proxy2)
 *     14) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy2 and TrueTrustVerifierContext as parameters
 *     15) assert that SecurityException will be thrown
 *
 *     16) construct ValidNonProxyObject2 with ProxyTrustThrowingSE2 instance as
 *         a parameter
 *     17) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject2 and TrueTrustVerifierContext as parameters
 *     18) assert that SecurityException will be thrown
 *
 *     19) construct ValidIHandler2 with ProxyTrustThrowingSE2 instance as a
 *         parameter
 *     20) construct dynamic proxy instance with ValidIHandler2 invocation
 *         handler (Proxy3)
 *     21) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy3 and TrueTrustVerifierContext as parameters
 *     22) assert that SecurityException will be thrown
 *
 *     23) construct TrustVerifierContextThrowingSE with ValidMCContext as a
 *         parameter
 *     24) construct ValidNonProxyObject3 with TrueProxyTrust instance as a
 *         parameter
 *     25) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject3 and TrustVerifierContextThrowingSE as
 *         parameters
 *     26) assert that SecurityException will be thrown
 *
 *     27) construct ValidIHandler3 with TrueProxyTrust instance as a
 *         parameter
 *     28) construct dynamic proxy instance with ValidIHandler3 invocation
 *         handler (Proxy4)
 *     29) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy4 and TrustVerifierContextThrowingSE as parameters
 *     30) assert that SecurityException will be thrown
 *
 *     31) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingSE1) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy5)
 *     32) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy5 and TrueTrustVerifierContext as parameters
 *     33) assert that SecurityException will be thrown
 *
 *     34) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingSE2) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy6)
 *     35) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy6 and TrueTrustVerifierContext as parameters
 *     36) assert that SecurityException will be thrown
 *
 *     37) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingSE1) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy7)
 *     38) construct ValidNonProxyObject4 with Proxy7 instance as a
 *         parameter
 *     39) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject4 and TrustVerifierContext with limit 1
 *         as parameters
 *     40) assert that SecurityException will be thrown
 *
 *     41) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingSE2) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy8)
 *     42) construct ValidNonProxyObject5 with Proxy8 instance as a
 *         parameter
 *     43) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject5 and TrustVerifierContext with limit 1
 *         as parameters
 *     44) assert that SecurityException will be thrown
 *
 *     45) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingSE1) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy9)
 *     46) construct ValidIHandler4 with Proxy9 instance as a parameter
 *     47) construct dynamic proxy instance with ValidIHandler4 invocation
 *         handler (Proxy10)
 *     48) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy10 and TrustVerifierContext with limit 1 as parameters
 *     49) assert that SecurityException will be thrown
 *
 *     50) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingSE2) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy11)
 *     51) construct ValidIHandler5 with Proxy11 instance as a parameter
 *     52) construct dynamic proxy instance with ValidIHandler5 invocation
 *         handler (Proxy12)
 *     53) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy12 and TrustVerifierContext with limit 1 as parameters
 *     54) assert that SecurityException will be thrown
 * </pre>
 */
public class IsTrustedObject_SecurityExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustVerifier ptv = new ProxyTrustVerifier();
        Object fakeObj = new Object();
        Object[] testObjs = new Object[] {
            new NonProxyObjectThrowingSE(),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new IHandlerThrowingSE(fakeObj)),
            new ValidNonProxyObject(new Object[] {
                new ProxyTrustThrowingSE1() }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        new ProxyTrustThrowingSE1() })),
            new ValidNonProxyObject(new Object[] {
                new ProxyTrustThrowingSE2() }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        new ProxyTrustThrowingSE2() })),
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust() }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        new TrueProxyTrust() })),
	    newRMIMainProxy(new ProxyTrustThrowingSE1()),
	    newRMIMainProxy(new ProxyTrustThrowingSE2()),
            new ValidNonProxyObject(new Object[] {
                newRMIMainProxy(new ProxyTrustThrowingSE1()) }),
            new ValidNonProxyObject(new Object[] {
                newRMIMainProxy(new ProxyTrustThrowingSE2()) }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        newRMIMainProxy(new ProxyTrustThrowingSE1()) })),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        newRMIMainProxy(new ProxyTrustThrowingSE2()) }))
        };
        TrustVerifier.Context[] testCtxs = new TrustVerifier.Context[] {
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContextThrowingSE(new Object[] { validMC }),
            new TrustVerifierContextThrowingSE(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 1)
        };

        for (int i = 0; i < testObjs.length; ++i) {
            try {
                logger.fine("================ CASE #" + (i + 1)
                        + "================");
		ptvIsTrustedObject(ptv, testObjs[i], testCtxs[i]);

                // FAIL
                throw new TestException(
                        "'isTrustedObject' method call of "
                        + "ProxyTrustVerifier did not throw any exception "
                        + "while SecurityException was expected.");
            } catch (SecurityException se) {
                // PASS
                logger.fine("'isTrustedObject' method call of "
                        + "ProxyTrustVerifier threw SecurityException "
                        + "as expected.");
            }
        }
    }
}
