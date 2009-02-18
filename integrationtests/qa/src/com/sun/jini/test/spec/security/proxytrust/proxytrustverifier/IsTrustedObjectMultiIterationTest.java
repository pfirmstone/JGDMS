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
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustVerifier;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;
import com.sun.jini.test.spec.security.proxytrust.util.ValidNonProxyObject;
import com.sun.jini.test.spec.security.proxytrust.util.ValidIHandler;
import com.sun.jini.test.spec.security.proxytrust.util.NonRMCProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.TrueProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.FalseProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.TrueTrustVerifierContext;
import com.sun.jini.test.spec.security.proxytrust.util.TrustVerifierContext;


/**
 * <pre>
 * Purpose
 *   This test verifies the isTrustedObject method of ProxyTrustVerifier for
 *   multiple iterations.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     ValidNonProxyObject - class having constructor with 1 parameter: Object[]
 *             and public method 'ProxyTrustIterator getProxyTrustIterator()'
 *             returning TestTrustIterator with objects specified in
 *             constructor's parameter
 *     ValidIHandler - invocation handler having constructor
 *             with 1 parameter: Object[] and having public method
 *             'ProxyTrustIterator getProxyTrustIterator()' returning
 *             TrustIterator with objects specified in constructor's parameter
 *     NonRMCProxyTrust - class implementing ProxyTrust but is not
 *             implementing RemoteMethodControl interface whose
 *             'getProxyVerifier' method returns TrueTrustVerifier instance
 *     FalseProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns
 *             FalseTrustVerifier instance
 *     TrueProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns
 *             TrueTrustVerifier instance
 *     ValidMCContext - array of objects having MethodConstraints instance with
 *             non-empty security constraints for the
 *             ProxyTrust.getProxyVerifier method
 *     TrustVerifierContext - class implementing TrustVerifier.Context interface
 *             having constructor with 1 parameter:  Object[]. Collection of
 *             those objects will be returned by getCallerContext method.
 *             'isTrustedObject' method of this class returns false when it is
 *             invoked for the first or for the second time but returns true
 *             always after that
 *     TrueTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface having constructor with 1 parameter:  Object[].
 *             Collection of those objects will be returned by getCallerContext
 *             method. 'isTrustedObject' method of this class always returns
 *             true
 *     TestTrustIterator - class implementing ProxyTrustIterator interface
 *             having constructor with 1 paramter: Object[], whose 'next' method
 *             returns objects specified in constructor's parameter in the same
 *             sequence (i.e. obj[0] first, then obj[1] ... etc.)
 *     TrueTrustVerifier - class implementing TrustVerifier interface whose
 *             'isTrustedObject' method always returns true
 *     FalseTrustVerifier - class implementing TrustVerifier interface whose
 *             'isTrustedObject' method always returns false
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustVerifier
 *     2) construct TrueTrustVerifierContext with ValidMCContext as a parameter
 *     3) construct ValidNonProxyObject1 with TrueProxyTrust instance as
 *        a parameter
 *     4) construct ValidNonProxyObject2 with ValidNonProxyObject1 as
 *        a parameter
 *     5) construct ValidNonProxyObject3 with ValidNonProxyObject2 as
 *        a parameter
 *     6) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        ValidNonProxyObject3 and TrueTrustVerifierContext as parameters
 *     7) assert that 'getProxyTrustIterator' method of ValidNonProxyObject3
 *        will be invoked 1-st
 *     8) assert that 'getProxyTrustIterator' method of ValidNonProxyObject2
 *        will be invoked 2-nd
 *     9) assert that 'getProxyTrustIterator' method of ValidNonProxyObject1
 *        will be invoked 3-rd
 *     10) assert that 'isTrustedObject' method of TrueTrustVerifierContext will
 *         be invoked with TrueProxyTrust instance specified in
 *         ValidNonProxyObject1 constructor's parameter
 *     11) assert that 'getProxyVerifier' method of TrueProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     12) assert that 'isTrustedObject' of TrueTrustVerifier returned by
 *         previous invocation will be invoked with the same
 *         ValidNonProxyObject3 and TrueTrustVerifierContext objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     13) assert that true will be returned
 *
 *     14) construct ValidIHandler1 with TrueProxyTrust instance as a parameter
 *     15) construct dynamic proxy instance with ValidIHandler1 invocation
 *         handler (Proxy1)
 *     16) construct ValidIHandler2 with Proxy1 as a parameter
 *     17) construct dynamic proxy instance with ValidIHandler2 invocation
 *         handler (Proxy2)
 *     18) construct ValidIHandler3 with Proxy2 as a parameter
 *     19) construct dynamic proxy instance with ValidIHandler3 invocation
 *         handler (Proxy3)
 *     20) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy3 and TrueTrustVerifierContext as parameters
 *     21) assert that 'getProxyTrustIterator' method of ValidIHandler3 will
 *         be invoked 1-st
 *     22) assert that 'getProxyTrustIterator' method of ValidIHandler2 will
 *         be invoked 2-nd
 *     23) assert that 'getProxyTrustIterator' method of ValidIHandler1 will
 *         be invoked 3-rd
 *     24) assert that 'isTrustedObject' method of TrueTrustVerifierContext will
 *         be invoked with TrueProxyTrust instance specified in ValidIHandler1
 *         constructor's parameter
 *     25) assert that 'getProxyVerifier' method of TrueProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     26) assert that 'isTrustedObject' of TrueTrustVerifier returned by
 *         previous invocation will be invoked with the same Proxy3 and
 *         TrueTrustVerifierContext objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     27) assert that true will be returned
 *
 *     28) construct ValidIHandler4 with TrueProxyTrust instance as a parameter
 *     29) construct dynamic proxy instance with ValidIHandler4 invocation
 *         handler (Proxy4)
 *     30) construct ValidNonProxyObject4 with Proxy4 as a parameter
 *     31) construct ValidIHandler5 with ValidNonProxyObject4 as a parameter
 *     32) construct dynamic proxy instance with ValidIHandler5 invocation
 *         handler (Proxy5)
 *     33) construct ValidNonProxyObject5 with Proxy5 as a parameter
 *     34) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject5 and TrueTrustVerifierContext as parameters
 *     35) assert that 'getProxyTrustIterator' method of ValidNonProxyObject5
 *         will be invoked 1-st
 *     36) assert that 'getProxyTrustIterator' method of ValidIHandler5 will
 *         be invoked 2-nd
 *     37) assert that 'getProxyTrustIterator' method of ValidNonProxyObject4
 *         will be invoked 3-rd
 *     38) assert that 'getProxyTrustIterator' method of ValidIHandler4 will
 *         be invoked 4-th
 *     39) assert that 'isTrustedObject' method of TrueTrustVerifierContext will
 *         be invoked with TrueProxyTrust instance specified in ValidIHandler4
 *         constructor's parameter
 *     40) assert that 'getProxyVerifier' method of TrueProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     41) assert that 'isTrustedObject' of TrueTrustVerifier returned by
 *         previous invocation will be invoked with the same
 *         ValidNonProxyObject5 and TrueTrustVerifierContext objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     42) assert that true will be returned
 *
 *     43) construct ValidNonProxyObject6 with FalseProxyTrust instance as a
 *         parameter
 *     44) construct ValidIHandler6 with ValidNonProxyObject6 as a parameter
 *     45) construct dynamic proxy instance with ValidIHandler6 invocation
 *         handler (Proxy6)
 *     46) construct ValidNonProxyObject7 with TrueProxyTrust instance as a
 *         parameter
 *     47) construct ValidIHandler7 with TrueProxyTrust instance as a parameter
 *     48) construct dynamic proxy instance with ValidIHandler7 invocation
 *         handler (Proxy7)
 *     49) construct ValidNonProxyObject8 with (NonRMCProxyTrust, Proxy7,
 *         ValidNonProxyObject7 and Proxy6) as a parameter
 *     50) construct TrustVerifierContext with ValidMCContext as a parameter
 *     51) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject8 and TrustVerifierContext as parameters
 *     52) assert that 'getProxyTrustIterator' method of ValidNonProxyObject8
 *         will be invoked 1-st
 *     53) assert that 'getProxyTrustIterator' method of ValidIHandler7 will
 *         be invoked 2-nd
 *     54) assert that 'isTrustedObject' method of TrustVerifierContext will be
 *         invoked with TrueProxyTrust instance specified in ValidIHandler7
 *         constructor as a parameter
 *     55) assert that 'getProxyTrustIterator' method of ValidNonProxyObject7
 *         will be invoked 3-rd
 *     56) assert that 'isTrustedObject' method of TrustVerifierContext will be
 *         invoked with TrueProxyTrust instance specified in
 *         ValidNonProxyObject7 constructor as a parameter
 *     57) assert that 'getProxyTrustIterator' method of ValidIHandler6 will
 *         be invoked 4-th
 *     58) assert that 'getProxyTrustIterator' method of ValidNonProxyObject6
 *         will be invoked 5-th
 *     59) assert that 'isTrustedObject' method of TrustVerifierContext will be
 *         invoked with FalseProxyTrust instance specified in
 *         ValidNonProxyObject6 constructor as a parameter
 *     60) assert that 'getProxyVerifier' method of FalseProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     61) assert that 'isTrustedObject' of FalseTrustVerifier returned by
 *         previous invocation will be invoked with the same
 *         ValidNonProxyObject8 and TrustVerifierContext objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     62) assert that false will be returned
 *
 *     63) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (InnerProxy1)
 *     64) construct ValidIHandler8 with InnerProxy1 instance as a parameter
 *     65) construct dynamic proxy instance with ValidIHandler8 invocation
 *         handler (Proxy8)
 *     66) construct ValidIHandler9 with Proxy8 as a parameter
 *     67) construct dynamic proxy instance with ValidIHandler2 invocation
 *         handler (Proxy9)
 *     68) construct ValidIHandler10 with Proxy9 as a parameter
 *     69) construct dynamic proxy instance with ValidIHandler10 invocation
 *         handler (Proxy10)
 *     70) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy10 and TrustVerifierContext with limit 1 as parameters
 *     71) assert that 'getProxyTrustIterator' method of ValidIHandler10 will
 *         be invoked 1-st
 *     72) assert that 'getProxyTrustIterator' method of ValidIHandler9 will
 *         be invoked 2-nd
 *     73) assert that 'getProxyTrustIterator' method of ValidIHandler8 will
 *         be invoked 3-rd
 *     74) assert that isTrustedObject method of context is called with
 *         InnerProxy1
 *     75) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as InnerProxy1, but in parent loader
 *     76) assert that getProxyVerifier method of TrueProxyTrust is called
 *     77) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with Proxy10
 *     78) assert that true will be returned
 *
 *     79) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (InnerProxy2)
 *     80) construct ValidIHandler11 with InnerProxy2 instance as a parameter
 *     81) construct dynamic proxy instance with ValidIHandler11 invocation
 *         handler (Proxy11)
 *     82) construct ValidNonProxyObject9 with Proxy11 as a parameter
 *     83) construct ValidIHandler12 with ValidNonProxyObject9 as a parameter
 *     84) construct dynamic proxy instance with ValidIHandler12 invocation
 *         handler (Proxy12)
 *     85) construct ValidNonProxyObject10 with Proxy12 as a parameter
 *     86) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject10 and TrustVerifierContext with limit 1
 *         as parameters
 *     87) assert that 'getProxyTrustIterator' method of ValidNonProxyObject10
 *         will be invoked 1-st
 *     88) assert that 'getProxyTrustIterator' method of ValidIHandler12 will
 *         be invoked 2-nd
 *     89) assert that 'getProxyTrustIterator' method of ValidNonProxyObject9
 *         will be invoked 3-rd
 *     90) assert that 'getProxyTrustIterator' method of ValidIHandler11 will
 *         be invoked 4-th
 *     91) assert that isTrustedObject method of context is called with
 *         InnerProxy2
 *     92) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as InnerProxy2, but in parent loader
 *     93) assert that getProxyVerifier method of TrueProxyTrust is called
 *     94) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with ValidNonProxyObject10
 *     95) assert that true will be returned
 *
 *     96) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning FalseProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (InnerProxy3)
 *     97) construct ValidNonProxyObject11 with InnerProxy3 instance as a
 *         parameter
 *     98) construct ValidIHandler13 with ValidNonProxyObject11 as a parameter
 *     99) construct dynamic proxy instance with ValidIHandler13 invocation
 *         handler (Proxy13)
 *    100) construct ValidNonProxyObject12 with TrueProxyTrust instance as a
 *         parameter
 *    101) construct ValidIHandler14 with TrueProxyTrust instance as a parameter
 *    102) construct dynamic proxy instance with ValidIHandler14 invocation
 *         handler (Proxy14)
 *    103) construct ValidNonProxyObject13 with (NonRMCProxyTrust, Proxy14,
 *         ValidNonProxyObject12 and Proxy13) as a parameter
 *    104) construct TrustVerifierContext with ValidMCContext as a parameter
 *    105) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject13 and TrustVerifierContext with limit 3
 *         as parameters
 *    106) assert that 'getProxyTrustIterator' method of ValidNonProxyObject13
 *         will be invoked 1-st
 *    107) assert that 'getProxyTrustIterator' method of ValidIHandler14 will
 *         be invoked 2-nd
 *    108) assert that 'isTrustedObject' method of TrustVerifierContext will be
 *         invoked with TrueProxyTrust instance specified in ValidIHandler14
 *         constructor as a parameter
 *    109) assert that 'getProxyTrustIterator' method of ValidNonProxyObject12
 *         will be invoked 3-rd
 *    110) assert that 'isTrustedObject' method of TrustVerifierContext will be
 *         invoked with TrueProxyTrust instance specified in
 *         ValidNonProxyObject12 constructor as a parameter
 *    111) assert that 'getProxyTrustIterator' method of ValidIHandler13 will
 *         be invoked 4-th
 *    112) assert that 'getProxyTrustIterator' method of ValidNonProxyObject11
 *         will be invoked 5-th
 *    113) assert that isTrustedObject method of context is called with
 *         InnerProxy3
 *    114) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as InnerProxy3, but in parent loader
 *    115) assert that getProxyVerifier method of FalseProxyTrust is called
 *    116) assert that isTrustedObject method of FalseTrustVerifier is called
 *         with ValidNonProxyObject13
 *    117) assert that false will be returned
 *
 *     79) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in non-RMI child
 *         loader (InnerProxy4)
 *     80) construct ValidIHandler15 with InnerProxy4 instance as a parameter
 *     81) construct dynamic proxy instance with ValidIHandler15 invocation
 *         handler (Proxy15)
 *     82) construct ValidNonProxyObject14 with Proxy15 as a parameter
 *     83) construct ValidIHandler16 with ValidNonProxyObject14 as a parameter
 *     84) construct dynamic proxy instance with ValidIHandler16 invocation
 *         handler (Proxy16)
 *     85) construct ValidNonProxyObject15 with Proxy16 as a parameter
 *     86) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with ValidNonProxyObject15 and TrustVerifierContext with limit 1
 *         as parameters
 *     87) assert that 'getProxyTrustIterator' method of ValidNonProxyObject15
 *         will be invoked 1-st
 *     88) assert that 'getProxyTrustIterator' method of ValidIHandler16 will
 *         be invoked 2-nd
 *     89) assert that 'getProxyTrustIterator' method of ValidNonProxyObject14
 *         will be invoked 3-rd
 *     90) assert that 'getProxyTrustIterator' method of ValidIHandler15 will
 *         be invoked 4-th
 *     91) assert that isTrustedObject method of context is called with
 *         InnerProxy4
 *     95) assert that false will be returned
 * </pre>
 */
public class IsTrustedObjectMultiIterationTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustVerifier ptv = new ProxyTrustVerifier();
        Object fakeObj = new Object();
        Object[] testObjs = new Object[] {
            new ValidNonProxyObject(new Object[] { new ValidNonProxyObject(
                    new Object[] {new ValidNonProxyObject(new Object[] {
                        new TrueProxyTrust() }) }) }),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                fakeObj, new Object[] { ProxyTrustUtil.newProxyInstance(
                    fakeObj, new ValidIHandler(fakeObj, new Object[] {
                        ProxyTrustUtil.newProxyInstance(fakeObj,
                            new ValidIHandler(fakeObj, new Object[] {
                                new TrueProxyTrust() })) })) })),
            new ValidNonProxyObject(new Object [] {
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] {
                        new ValidNonProxyObject(new Object[] {
                            ProxyTrustUtil.newProxyInstance(fakeObj,
                                new ValidIHandler(fakeObj, new Object[] {
                                    new TrueProxyTrust() })) }) })) }),
            new ValidNonProxyObject(new Object[] {
                new NonRMCProxyTrust(),
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] { new TrueProxyTrust() })),
                new ValidNonProxyObject(new Object[] {
                    new TrueProxyTrust() }),
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] { new ValidNonProxyObject(
                        new Object[] { new FalseProxyTrust() }) })) }),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                fakeObj, new Object[] { ProxyTrustUtil.newProxyInstance(
                    fakeObj, new ValidIHandler(fakeObj, new Object[] {
                        ProxyTrustUtil.newProxyInstance(fakeObj,
                            new ValidIHandler(fakeObj, new Object[] {
                                newRMIMainProxy(
					 new TrueProxyTrust()) })) })) })),
            new ValidNonProxyObject(new Object [] {
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] {
                        new ValidNonProxyObject(new Object[] {
                            ProxyTrustUtil.newProxyInstance(fakeObj,
                                new ValidIHandler(fakeObj, new Object[] {
                                    newRMIMainProxy(
					 new TrueProxyTrust()) })) }) })) }),
            new ValidNonProxyObject(new Object[] {
                new NonRMCProxyTrust(),
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] { new TrueProxyTrust() })),
                new ValidNonProxyObject(new Object[] {
                    new TrueProxyTrust() }),
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] { new ValidNonProxyObject(
                        new Object[] {
			    newRMIMainProxy(new FalseProxyTrust()) }) })) }),
            new ValidNonProxyObject(new Object [] {
                ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] {
                        new ValidNonProxyObject(new Object[] {
                            ProxyTrustUtil.newProxyInstance(fakeObj,
                                new ValidIHandler(fakeObj, new Object[] {
                                    ProxyTrustUtil.newProxyInstance(
					 new TrueProxyTrust()) })) }) })) })
        };
        TrustVerifier.Context[] testCtxs = new TrustVerifier.Context[] {
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 3),
            new TrustVerifierContext(new Object[] { validMC }, 1)
        };
        String res = null;

        for (int i = 0; i < testObjs.length; ++i) {
            logger.fine("================ CASE #" + (i + 1)
                    + "================");
            res = checkResult(new Boolean(ptvIsTrustedObject(ptv,
                    testObjs[i], testCtxs[i])));

            if (res != null) {
                // FAIL
                throw new TestException(res);
            }
        }
    }
}
