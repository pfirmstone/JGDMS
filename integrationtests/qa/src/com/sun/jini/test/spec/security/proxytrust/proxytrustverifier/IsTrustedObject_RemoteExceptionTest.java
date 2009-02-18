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

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustVerifier;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;
import com.sun.jini.test.spec.security.proxytrust.util.NonProxyObjectThrowingRE;
import com.sun.jini.test.spec.security.proxytrust.util.IHandlerThrowingRE;
import com.sun.jini.test.spec.security.proxytrust.util.ValidNonProxyObject;
import com.sun.jini.test.spec.security.proxytrust.util.ValidIHandler;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustThrowingRE1;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustThrowingRE2;
import com.sun.jini.test.spec.security.proxytrust.util.TrueProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.TrueTrustVerifierContext;
import com.sun.jini.test.spec.security.proxytrust.util.TrustVerifierContext;
import
  com.sun.jini.test.spec.security.proxytrust.util.TrustVerifierContextThrowingRE;


/**
 * <pre>
 * Purpose
 *   This test verifies the isTrustedObject method of ProxyTrustVerifier
 *   when RemoteExceptions are thrown.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonProxyObjectThrowingRE - class whose 'getProxyTrustIterator'
 *             method returns TrustIteratorThrowingRE
 *     ValidNonProxyObject - class having constructor with 1 parameter: Object[]
 *             and public method 'ProxyTrustIterator getProxyTrustIterator()'
 *             returning TestTrustIterator with objects specified in
 *             constructor's parameter
 *     IHandlerThrowingRE - invocation handler whose 'getProxyTrustIterator'
 *             method returns TrustIteratorThrowingRE
 *     ValidIHandler - invocation handler having constructor
 *             with 1 parameter: Object[] and having public method
 *             'ProxyTrustIterator getProxyTrustIterator()' returning
 *             TrustIterator with objects specified in constructor's parameter
 *     ProxyTrustThrowingRE1 - class implementing ProxyTrust and
 *             RemoteMethodControl interfaces whose 'getProxyVerifier'
 *             method always throws RemoteException
 *     ProxyTrustThrowingRE2 - class implementing RemoteMethodControl and
 *             ProxyTrust interfaces whose 'getProxyVerifier' method returns
 *             TrustVerifierThrowingRE instance
 *     NonRMCProxyTrust - class implementing ProxyTrust but is not
 *             implementing RemoteMethodControl interface whose
 *             'getProxyVerifier' method returns TrueTrustVerifier instance
 *     TrueProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns
 *             TrueTrustVerifier instance
 *     ValidMCContext - array of objects having MethodConstraints instance with
 *             non-empty security constraints for the
 *             ProxyTrust.getProxyVerifier method
 *     TrustVerifierContextThrowingRE - class implementing TrustVerifier.Context
 *             interface having constructor with 1 parameter: Collection. This
 *             value will be returned by getCallerContext method.
 *             'isTrustedObject' method of this class always throws
 *             RemoteException
 *     TrueTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface having constructor with 1 parameter:  Object[].
 *             Collection of those objects will be returned by getCallerContext
 *             method. 'isTrustedObject' method of this class always returns
 *             true
 *     TestTrustIterator - class implementing ProxyTrustIterator interface
 *             having constructor with 1 paramter: Object[], whose 'next' method
 *             returns objects specified in constructor's parameter in the same
 *             sequence (i.e. obj[0] first, then obj[1] ... etc.)
 *     TrustIteratorThrowingRE - class implementing ProxyTrustIterator interface
 *             whose 'next' method always throws RemoteException
 *     TrustVerifierThrowingRE - class implementing TrustVerifier interface
 *             whose 'isTrustedObject' method always throws RemoteException
 *     TrueTrustVerifier - class implementing TrustVerifier interface whose
 *             'isTrustedObject' method always returns true
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustVerifier
 *     2) construct TrueTrustVerifierContext with ValidMCContext as a parameter
 *     3) construct NonProxyObjectThrowingRE
 *     4) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        NonProxyObjectThrowingRE and TrueTrustVerifierContext as parameters
 *     5) assert that RemoteException will be thrown
 *
 *     6) construct dynamic proxy instance with IHandlerThrowingRE invocation
 *        handler (Proxy1)
 *     7) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        Proxy1 and TrueTrustVerifierContext as parameters
 *     8) assert that RemoteException will be thrown
 *
 *     9) construct ValidNonProxyObject1 with ProxyTrustThrowingRE1 instance
 *        as a parameter
 *     10) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject1 and TrueTrustVerifierContext as parameters
 *     11) assert that 'setException' method of TestTrustIterator will be
 *         invoked
 *     12) assert that the same RemoteException as in 'setException' method will
 *         be thrown
 *
 *     13) construct ValidIHandler1 with ProxyTrustThrowingRE1 instance as a
 *         parameter
 *     14) construct dynamic proxy instance with ValidIHandler1 invocation
 *         handler (Proxy2)
 *     15) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy2 and TrueTrustVerifierContext as parameters
 *     16) assert that 'setException' method of TestTrustIterator will be
 *         invoked
 *     17) assert that the same RemoteException as in 'setException' method will
 *         be thrown
 *
 *     18) construct ValidNonProxyObject2 with ProxyTrustThrowingRE2 instance
 *         as a parameter
 *     19) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject2 and TrueTrustVerifierContext as parameters
 *     20) assert that RemoteException will be thrown
 *
 *     21) construct ValidIHandler2 with ProxyTrustThrowingRE2 instance as a
 *         parameter
 *     22) construct dynamic proxy instance with ValidIHandler2 invocation
 *         handler (Proxy3)
 *     23) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy3 and TrueTrustVerifierContext as parameters
 *     24) assert that RemoteException will be thrown
 *
 *     25) construct TrustVerifierContextThrowingRE with ValidMCContext as a
 *         parameter
 *     26) construct ValidNonProxyObject3 with TrueProxyTrust instance as a
 *         parameter
 *     27) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject3 and TrustVerifierContextThrowingRE as
 *         parameters
 *     28) assert that 'setException' method of TestTrustIterator will be
 *         invoked
 *     29) assert that the same RemoteException as in 'setException' method will
 *         be thrown
 *
 *     30) construct ValidIHandler3 with TrueProxyTrust instance as a
 *         parameter
 *     31) construct dynamic proxy instance with ValidIHandler3 invocation
 *         handler (Proxy4)
 *     32) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy4 and TrustVerifierContextThrowingRE as parameters
 *     33) assert that 'setException' method of TestTrustIterator will be
 *         invoked
 *     34) assert that the same RemoteException as in 'setException' method will
 *         be thrown
 *
 *     35) construct ValidIHandler4 with (ProxyTrustThrowingRE1(1),
 *         NonRMCProxyTrust and another ProxyTrustThrowingRE1(2) instance)
 *         as a parameter (whose 'getProxyTrustIterator' method returns
 *         TestTrustIterator1)
 *     36) construct dynamic proxy instance with ValidIHandler4 invocation
 *         handler (Proxy5)
 *     37) construct ValidNonProxyObject4 with (ProxyTrustThrowingRE1(3),
 *         NonRMCProxyTrust, Proxy5 and another ProxyTrustThrowingRE1(4)
 *         instance) as a parameter (whose 'getProxyTrustIterator' method
 *         returns TestTrustIterator2)
 *     38) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject4 and TrueTrustVerifierContext as parameters
 *     39) assert that 'setException' method of TestTrustIterator2 will be
 *         invoked 1-st with RemoteException produced by
 *         ProxyTrustThrowingRE1(3)
 *     40) assert that 'setException' method of TestTrustIterator1 will be
 *         invoked 2-nd with RemoteException produced by
 *         ProxyTrustThrowingRE1(1)
 *     41) assert that 'setException' method of TestTrustIterator1 will be
 *         invoked 3-rd with RemoteException produced by
 *         ProxyTrustThrowingRE1(2)
 *     42) assert that 'setException' method of TestTrustIterator2 will be
 *         invoked 4-th with RemoteException produced by
 *         ProxyTrustThrowingRE1(4)
 *     43) assert that the same RemoteException as in 5-th 'setException' method
 *         will be thrown
 *
 *     44) construct ValidNonProxyObject5 with (ProxyTrustThrowingRE1(1) and
 *         TrueProxyTrust) as a parameter (whose 'getProxyTrustIterator' method
 *         returns TestTrustIterator3)
 *     45) construct ValidIHandler5 with (ProxyTrustThrowingRE1(2) and
 *         ValidNonProxyObject5) as a parameter (whose 'getProxyTrustIterator'
 *         method returns TestTrustIterator4)
 *     46) construct dynamic proxy instance with ValidIHandler5 invocation
 *         handler (Proxy6)
 *     47) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy6 and TrustVerifierContextThrowingRE as parameters
 *     48) assert that 'setException' method of TestTrustIterator4 will be
 *         invoked 1-st with RemoteException produced by
 *         ProxyTrustThrowingRE1(2)
 *     49) assert that 'setException' method of TestTrustIterator3 will be
 *         invoked 2-nd with RemoteException produced by
 *         ProxyTrustThrowingRE1(1)
 *     50) assert that 'setException' method of TestTrustIterator3 will be
 *         invoked 3-rd with RemoteException produced by
 *         TrustVerifierContextThrowingRE
 *     51) assert that 'setException' method of TestTrustIterator4 will be
 *         invoked 4-th with the same RemoteException produced by
 *         TrustVerifierContextThrowingRE
 *     52) assert that the same RemoteException as in 4-th 'setException' method
 *         will be thrown
 *
 *     53) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingRE1) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy7)
 *     54) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy7 and TrueTrustVerifierContext as parameters
 *     55) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as Proxy7, but in parent loader
 *     56) assert that getProxyVerifier method of ProxyTrustThrowingRE1 is
 *         called, throwing RemoteException
 *     57) assert that the same RemoteException is thrown as result
 *
 *     58) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingRE2) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy8)
 *     59) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy8 and TrueTrustVerifierContext as parameters
 *     60) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as Proxy8, but in parent loader
 *     61) assert that getProxyVerifier method of ProxyTrustThrowingRE2 is
 *         called, returning TrustVerifierThrowingRE
 *     62) assert that isTrustedObject of TrustVerifierThrowingRE is called,
 *         throwing RemoteException
 *     63) assert that the same RemoteException is thrown as result
 *
 *     64) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingRE1) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy9)
 *     65) construct ValidNonProxyObject6 with Proxy9
 *     66) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject6 and TrustVerifierContext
 *         with limit 1 as parameters
 *     67) assert that getProxyTrustIterator of ValidNonProxyObject6 is
 *         called
 *     68) assert that isTrustedObject method of context is called with Proxy9
 *     69) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as Proxy9, but in parent loader
 *     70) assert that getProxyVerifier method of ProxyTrustThrowingRE1 is
 *         called, throwing RemoteException
 *     71) assert that setException is called with that RemoteException
 *     72) assert that the same RemoteException is thrown as result
 *
 *     73) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingRE2) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy10)
 *     74) construct ValidNonProxyObject7 with Proxy10
 *     75) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject7 and TrustVerifierContext
 *         with limit 1 as parameters
 *     76) assert that getProxyTrustIterator of ValidNonProxyObject7 is
 *         called
 *     77) assert that isTrustedObject method of context is called with Proxy10
 *     78) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as Proxy10, but in parent loader
 *     79) assert that getProxyVerifier method of ProxyTrustThrowingRE2 is
 *         called, returning TrustVerifierThrowingRE
 *     80) assert that isTrustedObject of TrustVerifierThrowingRE is called,
 *         throwing RemoteException
 *     81) assert that the same RemoteException is thrown as result
 *
 *     82) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingRE1) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy11)
 *     83) construct ValidIHandler6 with Proxy11 as a parameter
 *     84) construct dynamic proxy with ValidIHandler6 as handler (Proxy12)
 *     85) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy12 and TrustVerifierContext with limit 1 as parameters
 *     86) assert that getProxyTrustIterator of ValidIHandler6 is called
 *     87) assert that isTrustedObject method of context is called with Proxy11
 *     88) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as Proxy11, but in parent loader
 *     89) assert that getProxyVerifier method of ProxyTrustThrowingRE1 is
 *         called, throwing RemoteException
 *     90) assert that setException is called with that RemoteException
 *     91) assert that the same RemoteException is thrown as result
 *
 *     91) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning ProxyTrustThrowingRE2) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy13)
 *     92) construct ValidIHandler7 with Proxy13 as a parameter
 *     93) construct dynamic proxy with ValidIHandler7 as handler (Proxy14)
 *     94) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy14 and TrustVerifierContext with limit 1 as parameters
 *     95) assert that getProxyTrustIterator of ValidIHandler7 is called
 *     96) assert that isTrustedObject method of context is called with Proxy13
 *     97) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as Proxy13, but in parent loader
 *     98) assert that getProxyVerifier method of ProxyTrustThrowingRE2 is
 *         called, returning TrustVerifierThrowingRE
 *     99) assert that isTrustedObject of TrustVerifierThrowingRE is called,
 *         throwing RemoteException
 *    100) assert that the same RemoteException is thrown as result
 * </pre>
 */
public class IsTrustedObject_RemoteExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustVerifier ptv = new ProxyTrustVerifier();
        Object fakeObj = new Object();
        Object[] testObjs = new Object[] {
            new NonProxyObjectThrowingRE(),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new IHandlerThrowingRE(fakeObj)),
            new ValidNonProxyObject(new Object[] {
                new ProxyTrustThrowingRE1() }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        new ProxyTrustThrowingRE1() })),
            new ValidNonProxyObject(new Object[] {
                new ProxyTrustThrowingRE2() }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        new ProxyTrustThrowingRE2() })),
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust() }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        new TrueProxyTrust() })),
            new ValidNonProxyObject(new Object[] {
                new ProxyTrustThrowingRE1(),
                new Object(),
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                        fakeObj, new Object[] {
                            new ProxyTrustThrowingRE1(),
                            new Object(),
                            new ProxyTrustThrowingRE1() })),
                new ProxyTrustThrowingRE1() }),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] {
                        new ProxyTrustThrowingRE1(),
                        new ValidNonProxyObject(new Object[] {
                            new ProxyTrustThrowingRE1(),
                            new TrueProxyTrust() }) })),
	    newRMIMainProxy(new ProxyTrustThrowingRE1()),
	    newRMIMainProxy(new ProxyTrustThrowingRE2()),
            new ValidNonProxyObject(new Object[] {
                newRMIMainProxy(new ProxyTrustThrowingRE1()) }),
            new ValidNonProxyObject(new Object[] {
                newRMIMainProxy(new ProxyTrustThrowingRE2()) }),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        newRMIMainProxy(new ProxyTrustThrowingRE1()) })),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new ValidIHandler(fakeObj, new Object[] {
                        newRMIMainProxy(new ProxyTrustThrowingRE2()) })),
        };
        TrustVerifier.Context[] testCtxs = new TrustVerifier.Context[] {
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContextThrowingRE(new Object[] { validMC }),
            new TrustVerifierContextThrowingRE(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContextThrowingRE(new Object[] { validMC }),
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
                String res =
		    checkResult(new Boolean(
			   ptvIsTrustedObject(ptv, testObjs[i], testCtxs[i])));

                // FAIL
		if (res != null) {
		    throw new TestException(res);
		}
                throw new TestException(
                        "'isTrustedObject' method call of "
                        + "ProxyTrustVerifier did not throw any exception "
                        + "while RemoteException was expected.");
            } catch (RemoteException re) {
                // PASS
                logger.fine("'isTrustedObject' method call of "
                        + "ProxyTrustVerifier threw RemoteException "
                        + "as expected.");
                String res = checkResult(re);

                if (res != null) {
                    // FAIL
                    throw new TestException(res);
                }
            }
        }
    }
}
