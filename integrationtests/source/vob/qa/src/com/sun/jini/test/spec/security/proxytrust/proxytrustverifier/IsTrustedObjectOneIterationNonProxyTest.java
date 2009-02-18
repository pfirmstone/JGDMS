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
import com.sun.jini.test.spec.security.proxytrust.util.ValidNonProxyObject;
import com.sun.jini.test.spec.security.proxytrust.util.NonProxyStaticMethodObject;
import com.sun.jini.test.spec.security.proxytrust.util.NonProxyWrongMethodObject;
import com.sun.jini.test.spec.security.proxytrust.util.TrueProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.FalseProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.NullProxyTrust;
import com.sun.jini.test.spec.security.proxytrust.util.TrueTrustVerifierContext;
import com.sun.jini.test.spec.security.proxytrust.util.TrustVerifierContext;


/**
 * <pre>
 * Purpose
 *   This test verifies the isTrustedObject method of ProxyTrustVerifier for
 *   non-dynamic proxy main objects, with at most one iterator, and no
 *   exceptions thrown.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonProxyNoMethodObject - class having no method
 *             'ProxyTrustIterator getProxyTrustIterator()' and which is not a
 *             dynamic proxy class
 *     NonProxyStaticMethodObject - class having static method
 *             'ProxyTrustIterator getProxyTrustIterator()' and which is not a
 *             dynamic proxy class
 *     NonProxyWrongMethodObject - class having public method
 *             'ProxyTrustIterator getProxyTrustIterator() throws FakeException'
 *             and which is not a dynamic proxy class
 *     ValidNonProxyObject - class having constructor with 1 parameter: Object[]
 *             and public method 'ProxyTrustIterator getProxyTrustIterator()'
 *             returning TestTrustIterator with objects specified in
 *             constructor's parameter
 *     NonRMCProxyTrust - class implementing ProxyTrust but is not
 *             implementing RemoteMethodControl interface whose
 *             'getProxyVerifier' method returns TrueTrustVerifier instance
 *     FalseProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns
 *             FalseTrustVerifier instance
 *     TrueProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns
 *             TrueTrustVerifier instance
 *     NullProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns null
 *     NoMCContext - array of objects having no MethodConstraints instances
 *     EmptyMCContext - array of objects having MethodConstraints instance with
 *             empty security constraints for the ProxyTrust.getProxyVerifier
 *             method
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
 *     2) construct ValidNonProxyObject1 with TrueProxyTrust instance as a
 *        parameter
 *     3) construct TrueTrustVerifierContext1 with NoMCContext as a parameter
 *     4) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        constructed ValidNonProxyObject1 and TrueTrustVerifierContext1 as
 *        parameters
 *     5) assert that false will be returned
 *
 *     6) construct TrueTrustVerifierContext2 with EmptyMCContext as a parameter
 *     7) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        constructed ValidNonProxyObject1 and TrueTrustVerifierContext2 as
 *        parameters
 *     8) assert that false will be returned
 *
 *     9) construct TrueTrustVerifierContext3 with ValidMCContext as a parameter
 *     10) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with NonProxyNoMethodObject and TrueTrustVerifierContext3 as
 *         parameters
 *     11) assert that false will be returned
 *
 *     12) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with NonProxyStaticMethodObject and TrueTrustVerifierContext3 as
 *         parameters
 *     13) assert that false will be returned
 *
 *     14) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with NonProxyWrongMethodObject and TrueTrustVerifierContext3 as
 *         parameters
 *     15) assert that false will be returned
 *
 *     16) construct ValidNonProxyObject2 with NonRMCProxyTrust instance as
 *         a parameter
 *     17) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject2 and TrueTrustVerifierContext3
 *         as parameters
 *     18) assert that 'getProxyTrustIterator' method of ValidNonProxyObject2
 *         will be invoked
 *     19) assert that false will be returned
 *
 *     20) construct ValidNonProxyObject3 with TrueProxyTrust instance as a
 *         parameter
 *     21) construct TrustVerifierContext1 with ValidMCContext as a parameter
 *     22) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject3 and TrustVerifierContext1 as
 *         parameters
 *     23) assert that 'getProxyTrustIterator' method of ValidNonProxyObject3
 *         will be invoked
 *     24) assert that 'isTrustedObject' method of TrustVerifierContext1 will be
 *         invoked with TrueProxyTrust instance specified in
 *         ValidNonProxyObject3 constructor's parameter
 *     25) assert that false will be returned
 *
 *     26) construct ValidNonProxyObject4 with TrueProxyTrust and another
 *         TrueProxyTrust instances as a parameter
 *     27) construct TrustVerifierContext2 with ValidMCContext as a parameter
 *     28) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject4 and TrustVerifierContext2 as
 *         parameters
 *     29) assert that 'getProxyTrustIterator' method of ValidNonProxyObject4
 *         will be invoked
 *     30) assert that 'isTrustedObject' method of TrustVerifierContext2 will be
 *         invoked twice: first time with first TrueProxyTrust instance
 *         specified in ValidNonProxyObject4 constructor's parameter and second
 *         time with the second one
 *     31) assert that false will be returned
 *
 *     32) construct ValidNonProxyObject5 with NullProxyTrust instance as a
 *         parameter
 *     33) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject5 and TrueTrustVerifierContext as
 *         parameters
 *     34) assert that 'getProxyTrustIterator' method of ValidNonProxyObject5
 *         will be invoked
 *     35) assert that 'isTrustedObject' method of TrueTrustVerifierContext will
 *         be invoked with NullProxyTrust instance specified in
 *         ValidNonProxyObject5 constructor's parameter
 *     36) assert that 'getProxyVerifier' method of NullProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     37) assert that false will be returned
 *
 *     38) construct ValidNonProxyObject6 with TrueProxyTrust, another
 *         TrueProxyTrust and NullProxyTrust instances as a parameter
 *     39) construct TrustVerifierContext3 with ValidMCContext as a parameter
 *     40) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject6 and TrustVerifierContext3 as
 *         parameters
 *     41) assert that 'getProxyTrustIterator' method of ValidNonProxyObject6
 *         will be invoked
 *     42) assert that 'isTrustedObject' method of TrustVerifierContext3 will be
 *         invoked 3 times, each time with 1 parameter: the same TrueProxyTrust,
 *         another TrueProxyTrust and NullProxyTrust as in ValidNonProxyObject6
 *         constructor subsequently
 *     43) assert that 'getProxyVerifier' method of NullProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     44) assert that false will be returned
 *
 *     45) construct ValidNonProxyObject7 with TrueProxyTrust, another
 *         TrueProxyTrust and FalseProxyTrust instances as a parameter
 *     46) construct TrustVerifierContext4 with ValidMCContext as a parameter
 *     47) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject7 and TrustVerifierContext4 as
 *         parameters
 *     48) assert that 'getProxyTrustIterator' method of ValidNonProxyObject7
 *         will be invoked
 *     49) assert that 'isTrustedObject' method of TrustVerifierContext4 will be
 *         invoked 3 times, each time with 1 parameter: the same TrueProxyTrust,
 *         another TrueProxyTrust and FalseProxyTrust as in ValidNonProxyObject7
 *         constructor subsequently
 *     50) assert that 'getProxyVerifier' method of FalseProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     51) assert that 'isTrustedObject' of FalseTrustVerifier returned by
 *         previous invocation will be invoked with the same
 *         ValidNonProxyObject7 and TrustVerifierContext4 objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     52) assert that false will be returned
 *
 *     53) construct ValidNonProxyObject8 with FalseProxyTrust, another
 *         FalseProxyTrust and TrueProxyTrust instances as a parameter
 *     54) construct TrustVerifierContext5 with ValidMCContext as a parameter
 *     55) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject8 and TrustVerifierContext5 as
 *         parameters
 *     56) assert that 'getProxyTrustIterator' method of ValidNonProxyObject8
 *         will be invoked
 *     57) assert that 'isTrustedObject' method of TrustVerifierContext5 will be
 *         invoked 3 times, each time with 1 parameter: the same
 *         FalseProxyTrust, another FalseProxyTrust and TrueProxyTrust as in
 *         ValidNonProxyObject8 constructor subsequently
 *     58) assert that 'getProxyVerifier' method of TrueProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     59) assert that 'isTrustedObject' of TrueTrustVerifier returned by
 *         previous invocation will be invoked with the same
 *         ValidNonProxyObject8 and TrustVerifierContext5 objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     60) assert that true will be returned
 *
 *     61) construct ValidNonProxyObject9 with 3 different FalseProxyTrust
 *         instances and TrueProxyTrust instances as a parameter
 *     62) construct TrustVerifierContext6 with ValidMCContext as a parameter
 *     63) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject9 and TrustVerifierContext6 as
 *         parameters
 *     64) assert that 'getProxyTrustIterator' method of ValidNonProxyObject9
 *         will be invoked
 *     65) assert that 'isTrustedObject' method of TrustVerifierContext6 will be
 *         invoked 4 times: first 3 times with the same FalseProxyTrust
 *         instances as in ValidNonProxyObject9 constructor subsequently, and
 *         4-th time with TrueProxyTrust as in ValidNonProxyObject9 constructor
 *     66) assert that 'getProxyVerifier' method of 3-rd FalseProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     67) assert that 'isTrustedObject' of FalseTrustVerifier returned by
 *         previous invocation will be invoked with the same
 *         ValidNonProxyObject9 and TrustVerifierContext6 objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     68) assert that false will be returned
 *
 *     69) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (InnerProxy10)
 *     70) construct ValidNonProxyObject10 with InnerProxy10 as a parameter
 *     71) construct TrustVerifierContext10 with ValidMCContext as a parameter
 *         and limit of 1
 *     72) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject10 and TrustVerifierContext10 as
 *         parameters
 *     73) assert that 'getProxyTrustIterator' method of ValidNonProxyObject10
 *         will be invoked
 *     74) assert that isTrustedObject method of context is called with
 *         InnerProxy10
 *     75) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as InnerProxy10, but in parent loader
 *     76) assert that getProxyVerifier method of TrueProxyTrust is called
 *     77) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with ValidNonProxyObject10
 *     78) assert that true will be returned
 *
 *     79) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in non-RMI child
 *         loader (InnerProxy11)
 *     80) construct ValidNonProxyObject11 with InnerProxy11 as a parameter
 *     81) construct TrustVerifierContext11 with ValidMCContext as a parameter
 *         and limit of 1
 *     82) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject11 and TrustVerifierContext11 as
 *         parameters
 *     83) assert that 'getProxyTrustIterator' method of ValidNonProxyObject11
 *         will be invoked
 *     84) assert that isTrustedObject method of context is called with
 *         InnerProxy11
 *     85) assert that false will be returned
 *
 *     86) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (InnerProxy12)
 *     87) construct ValidNonProxyObject12 with a FalseTrustVerifier instance,
 *         another FalseTrustVerifier instance, and InnerProxy12 as parameters
 *     88) construct TrustVerifierContext12 with ValidMCContext as a parameter
 *         and limit of 3
 *     89) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject12 and TrustVerifierContext12 as
 *         parameters
 *     90) assert that 'getProxyTrustIterator' method of ValidNonProxyObject12
 *         will be invoked
 *     91) assert that isTrustedObject method of context is called with
 *         first FalseTrustVerifier instance
 *     92) assert that isTrustedObject method of context is called with
 *         second FalseTrustVerifier instance
 *     93) assert that isTrustedObject method of context is called with
 *         InnerProxy12
 *     94) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as InnerProxy12, but in parent loader
 *     95) assert that getProxyVerifier method of TrueProxyTrust is called
 *     96) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with ValidNonProxyObject12
 *     97) assert that true will be returned
 *
 *     98) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in non-RMI child
 *         loader (InnerProxy13)
 *     99) construct ValidNonProxyObject13 with a FalseTrustVerifier instance,
 *         another FalseTrustVerifier instance, and InnerProxy13 as parameters
 *    100) construct TrustVerifierContext13 with ValidMCContext as a parameter
 *         and limit of 3
 *    101) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed ValidNonProxyObject13 and TrustVerifierContext13 as
 *         parameters
 *    102) assert that 'getProxyTrustIterator' method of ValidNonProxyObject13
 *         will be invoked
 *    103) assert that isTrustedObject method of context is called with
 *         first FalseTrustVerifier instance
 *    104) assert that isTrustedObject method of context is called with
 *         second FalseTrustVerifier instance
 *    105) assert that isTrustedObject method of context is called with
 *         InnerProxy13
 *    106) assert that false will be returned
 * </pre>
 */
public class IsTrustedObjectOneIterationNonProxyTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustVerifier ptv = new ProxyTrustVerifier();
        MethodConstraints emptyMC = new BasicMethodConstraints(
                new BasicMethodConstraints.MethodDesc[] {
                    new BasicMethodConstraints.MethodDesc(
                            "getProxyVerifier",
                            InvocationConstraints.EMPTY) });
        Object[] testObjs = new Object[] {
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust() }),
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust() }),
            new Object(),
            new NonProxyStaticMethodObject(),
            new NonProxyWrongMethodObject(),
            new ValidNonProxyObject(new Object[] {
                new Object() }),
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust() }),
            new ValidNonProxyObject(new Object[] {
                new TrueProxyTrust(), new TrueProxyTrust() }),
            new ValidNonProxyObject(new Object[] { new NullProxyTrust() }),
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust(),
                new TrueProxyTrust(), new NullProxyTrust() }),
            new ValidNonProxyObject(new Object[] { new TrueProxyTrust(),
                new TrueProxyTrust(), new FalseProxyTrust() }),
            new ValidNonProxyObject(new Object[] { new FalseProxyTrust(),
                new FalseProxyTrust(), new TrueProxyTrust() }),
            new ValidNonProxyObject(new Object[] { new FalseProxyTrust(),
                new FalseProxyTrust(), new FalseProxyTrust(),
                new TrueProxyTrust() }),
            new ValidNonProxyObject(new Object[] {
		newRMIMainProxy(new TrueProxyTrust()) }),
            new ValidNonProxyObject(new Object[] {
		newMainProxy(new TrueProxyTrust()) }),
            new ValidNonProxyObject(new Object[] { new FalseProxyTrust(),
		new FalseProxyTrust(),
		newRMIMainProxy(new TrueProxyTrust()) }),
            new ValidNonProxyObject(new Object[] { new FalseProxyTrust(),
		new FalseProxyTrust(),
		newMainProxy(new TrueProxyTrust()) })
        };
        TrustVerifier.Context[] testCtxs = new TrustVerifier.Context[] {
            new TrueTrustVerifierContext(new Object[] { new Object() }),
            new TrueTrustVerifierContext(new Object[] { emptyMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 1),
            new TrustVerifierContext(new Object[] { validMC }, 3),
            new TrustVerifierContext(new Object[] { validMC }, 3)
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
