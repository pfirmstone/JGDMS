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

import java.lang.reflect.InvocationHandler;

// net.jini
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustVerifier;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.ProxyTrustUtil;
import org.apache.river.test.spec.security.proxytrust.util.InvHandler;
import org.apache.river.test.spec.security.proxytrust.util.ValidIHandler;
import org.apache.river.test.spec.security.proxytrust.util.StaticMethodIHandler;
import org.apache.river.test.spec.security.proxytrust.util.WrongMethodIHandler;
import org.apache.river.test.spec.security.proxytrust.util.NonRMCProxyTrust;
import org.apache.river.test.spec.security.proxytrust.util.TrueProxyTrust;
import org.apache.river.test.spec.security.proxytrust.util.FalseProxyTrust;
import org.apache.river.test.spec.security.proxytrust.util.NullProxyTrust;
import org.apache.river.test.spec.security.proxytrust.util.TestClassLoader;
import org.apache.river.test.spec.security.proxytrust.util.TrueTrustVerifierContext;
import org.apache.river.test.spec.security.proxytrust.util.TrustVerifierContext;


/**
 * <pre>
 * Purpose
 *   This test verifies the isTrustedObject method of ProxyTrustVerifier for
 *   dynamic proxy main objects, with at most one iterator, and no exceptions
 *   thrown.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NoMethodIHandler - invocation handler having no method
 *             'ProxyTrustIterator getProxyTrustIterator()'
 *     StaticMethodIHandler - invocation handler having static method
 *             'ProxyTrustIterator getProxyTrustIterator()'
 *     WrongMethodIHandler - invocation handler having public method
 *             'ProxyTrustIterator getProxyTrustIterator() throws FakeException'
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
 *     NullProxyTrust - class implementing RemoteMethodControl and ProxyTrust
 *             interfaces whose 'getProxyVerifier' method returns null
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
 *     3) construct dynamic proxy instance with NoMethodIHandler invocation
 *        handler (Proxy1)
 *     4) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *         Proxy1 and TrueTrustVerifierContext as parameters
 *     5) assert that false will be returned
 *     6) construct dynamic proxy instance with StaticMethodIHandler
 *        invocation handler (Proxy2)
 *     7) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier with
 *        Proxy2 and TrueTrustVerifierContext as parameters
 *     8) assert that false will be returned
 *     9) construct dynamic proxy instance with WrongMethodIHandler
 *        invocation handler (Proxy3)
 *     10) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with Proxy3 and TrueTrustVerifierContext as parameters
 *     11) assert that false will be returned
 *
 *     12) construct ValidIHandler1 with NonRMCProxyTrust instance as
 *         a parameter
 *     13) construct dynamic proxy instance with ValidIHandler1 invocation
 *         handler (Proxy4)
 *     14) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy4 and TrueTrustVerifierContext as parameters
 *     15) assert that 'getProxyTrustIterator' method of ValidIHandler1 will
 *         be invoked
 *     16) assert that false will be returned
 *
 *     17) construct ValidIHandler2 with TrueProxyTrust instance as a parameter
 *     18) construct dynamic proxy instance with ValidIHandler2 invocation
 *         handler (Proxy5)
 *     19) construct TrustVerifierContext1 with ValidMCContext as a parameter
 *     20) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy5 and TrustVerifierContext1 as parameters
 *     21) assert that 'getProxyTrustIterator' method of ValidIHandler2 will
 *         be invoked
 *     22) assert that 'isTrustedObject' method of TrustVerifierContext1 will be
 *         invoked with TrueProxyTrust instance specified in ValidIHandler2
 *         constructor's parameter
 *     23) assert that false will be returned
 *
 *     24) construct ValidIHandler3 with TrueProxyTrust and another
 *         TrueProxyTrust instances as a parameter
 *     25) construct dynamic proxy instance with ValidIHandler3 invocation
 *         handler (Proxy6)
 *     26) construct TrustVerifierContext2 with ValidMCContext as a parameter
 *     27) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy6 and TrustVerifierContext2 as parameters
 *     28) assert that 'getProxyTrustIterator' method of ValidIHandler3 will
 *         be invoked
 *     29) assert that 'isTrustedObject' method of TrustVerifierContext2 will be
 *         invoked twice: first time with first TrueProxyTrust instance
 *         specified in ValidIHandler3 constructor's parameter and second time
 *         with the second one
 *     30) assert that false will be returned
 *
 *     31) construct ValidIHandler4 with NullProxyTrust instance as a parameter
 *     32) construct dynamic proxy instance with ValidIHandler4 invocation
 *         handler (Proxy7)
 *     33) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy7 and TrueTrustVerifierContext as parameters
 *     34) assert that 'getProxyTrustIterator' method of ValidIHandler4 will
 *         be invoked
 *     35) assert that 'isTrustedObject' method of TrueTrustVerifierContext will
 *         be invoked with NullProxyTrust instance specified in ValidIHandler4
 *         constructor's parameter
 *     36) assert that 'getProxyVerifier' method of NullProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     37) assert that false will be returned
 *
 *     38) construct ValidIHandler5 with TrueProxyTrust, another
 *         TrueProxyTrust and NullProxyTrust instances as a parameter
 *     39) construct dynamic proxy instance with ValidIHandler5 invocation
 *         handler (Proxy8)
 *     40) construct TrustVerifierContext3 with ValidMCContext as a parameter
 *     41) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy8 and TrustVerifierContext3 as parameters
 *     42) assert that 'getProxyTrustIterator' method of ValidIHandler5 will
 *         be invoked
 *     43) assert that 'isTrustedObject' method of TrustVerifierContext3 will be
 *         invoked 3 times, each time with 1 parameter: the same TrueProxyTrust,
 *         another TrueProxyTrust and NullProxyTrust as in ValidIHandler5
 *         constructor subsequently
 *     44) assert that 'getProxyVerifier' method of NullProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     45) assert that false will be returned
 *
 *     46) construct ValidIHandler6 with TrueProxyTrust, another
 *         TrueProxyTrust and FalseProxyTrust instances as a parameter
 *     47) construct dynamic proxy instance with ValidIHandler6 invocation
 *         handler (Proxy9)
 *     48) construct TrustVerifierContext4 with ValidMCContext as a parameter
 *     49) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy9 and TrustVerifierContext4 as parameters
 *     50) assert that 'getProxyTrustIterator' method of ValidIHandler6 will
 *         be invoked
 *     51) assert that 'isTrustedObject' method of TrustVerifierContext4 will be
 *         invoked 3 times, each time with 1 parameter: the same TrueProxyTrust,
 *         another TrueProxyTrust and FalseProxyTrust as in ValidIHandler6
 *         constructor subsequently
 *     52) assert that 'getProxyVerifier' method of FalseProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext 
 *     53) assert that 'isTrustedObject' of FalseTrustVerifier returned by
 *         previous invocation will be invoked with the same Proxy9 and
 *         TrustVerifierContext4 objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     54) assert that false will be returned
 *
 *     55) construct ValidIHandler7 with FalseProxyTrust, another
 *         FalseProxyTrust and TrueProxyTrust instances as a parameter
 *     56) construct dynamic proxy instance with ValidIHandler7 invocation
 *         handler (Proxy10)
 *     57) construct TrustVerifierContext5 with ValidMCContext as a parameter
 *     58) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy10 and TrustVerifierContext5 as parameters
 *     59) assert that 'getProxyTrustIterator' method of ValidIHandler7 will
 *         be invoked
 *     60) assert that 'isTrustedObject' method of TrustVerifierContext5 will be
 *         invoked 3 times, each time with 1 parameter: the same
 *         FalseProxyTrust, another FalseProxyTrust and TrueProxyTrust as in
 *         ValidIHandler7 constructor subsequently
 *     61) assert that 'getProxyVerifier' method of TrueProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     62) assert that 'isTrustedObject' of TrueTrustVerifier returned by
 *         previous invocation will be invoked with the same Proxy10 and
 *         TrustVerifierContext5 objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     63) assert that true will be returned
 *
 *     64) construct ValidIHandler8 with 3 different FalseProxyTrust
 *         instances and TrueProxyTrust instances as a parameter
 *     65) construct dynamic proxy instance with ValidIHandler7 invocation
 *         handler (Proxy11)
 *     66) construct TrustVerifierContext6 with ValidMCContext as a parameter
 *     67) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy11 and TrustVerifierContext6 as parameters
 *     68) assert that 'getProxyTrustIterator' method of ValidIHandler8 will
 *         be invoked
 *     69) assert that 'isTrustedObject' method of TrustVerifierContext6 will be
 *         invoked 4 times: first 3 times with the same FalseProxyTrust
 *         instances as in ValidIHandler8 constructor subsequently, and 4-th
 *         time with TrueProxyTrust as in ValidNonProxyObject9 constructor
 *     70) assert that 'getProxyVerifier' method of 3-rd FalseProxyTrust will be
 *         invoked, using as the client constraint the same MethodConstraints,
 *         containing in ValidMCContext
 *     71) assert that 'isTrustedObject' of FalseTrustVerifier returned by
 *         previous invocation will be invoked with the same Proxy11 and
 *         TrustVerifierContext6 objects as for
 *         ProxyTrustVerifier.isTrustedObject method
 *     72) assert that false will be returned
 *
 *     74) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (Proxy12)
 *     75) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy12 and TrueTrustVerifierContext as parameters
 *     76) assert that false will be returned
 *
 *     77) construct dynamic proxy instance with invocation handler that
 *         is itself a dynamic proxy instance containing a ValidIHandler
 *         (Proxy13)
 *     78) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy13 and TrueTrustVerifierContext as parameters
 *     79) assert that false will be returned
 *
 *     80) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust and RemoteMethodControl (Proxy14)
 *     81) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy14 and TrueTrustVerifierContext as parameters
 *     82) assert that false will be returned
 *
 *     83) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (Proxy15)
 *     84) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy15 and TrueTrustVerifierContext as parameters
 *     85) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         but in parent loader
 *     86) assert that getProxyVerifier method of TrueProxyTrust is called
 *     87) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with Proxy15
 *     88) assert that true will be returned
 *
 *     89) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in a non-RMI child
 *         loader (Proxy16)
 *     90) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy16 and TrueTrustVerifierContext as parameters
 *     91) assert that false will be returned
 *
 *     92) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl (InnerProxy17)
 *     93) construct dynamic proxy instance with ValidIHandler containing
 *         InnerProxy17 (Proxy17)
 *     94) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy17 and TrueTrustVerifierContext as parameters
 *     95) assert that 'getProxyTrustIterator' method of ValidIHandler will
 *         be invoked
 *     96) assert that isTrustedObject method of context is called with
 *         InnerProxy17
 *     97) assert that getProxyVerifier method of TrueProxyTrust is called
 *     98) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with Proxy17
 *     99) assert that true will be returned
 *         
 *    100) construct dynamic proxy instance with NoMethodIHandler handler
 *         implementing ProxyTrust (returning TrueProxyTrust) and
 *         RemoteMethodControl, with proxy class defined in RMI child loader
 *         (InnerProxy18)
 *    101) construct dynamic proxy instance with ValidIHandler containing
 *         InnerProxy18 (Proxy18)
 *    102) invoke 'isTrustedObject' method of constructed ProxyTrustVerifier
 *         with constructed Proxy18 and TrustVerifierContext with limit of
 *         1 as parameters
 *    103) assert that 'getProxyTrustIterator' method of ValidIHandler will
 *         be invoked
 *    104) assert that isTrustedObject method of context is called with
 *         InnerProxy18
 *    105) assert that isTrustedObject method of context is called with
 *         dynamic proxy implementing same interfaces, with same handler,
 *         as InnerProxy18, but in parent loader
 *    106) assert that getProxyVerifier method of TrueProxyTrust is called
 *    107) assert that isTrustedObject method of TrueTrustVerifier is called
 *         with Proxy18
 *    108) assert that true will be returned
 * </pre>
 */
public class IsTrustedObjectOneIterationProxyTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustVerifier ptv = new ProxyTrustVerifier();
        Object fakeObj = new Object();
        Object[] testObjs = new Object[] {
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new InvHandler(fakeObj)),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new StaticMethodIHandler(fakeObj)),
            ProxyTrustUtil.newProxyInstance(fakeObj,
                    new WrongMethodIHandler(fakeObj)),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] { new NonRMCProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] { new TrueProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj,
                    new Object[] {
                        new TrueProxyTrust(), new TrueProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj,
                    new Object[] { new NullProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj,
                    new Object[] { new TrueProxyTrust(),
                        new TrueProxyTrust(), new NullProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj,
                    new Object[] { new TrueProxyTrust(),
                        new TrueProxyTrust(), new FalseProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj,
                    new Object[] { new FalseProxyTrust(),
                        new FalseProxyTrust(), new TrueProxyTrust() })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj,
                    new Object[] { new FalseProxyTrust(),
                        new FalseProxyTrust(), new FalseProxyTrust(),
			new TrueProxyTrust() })),
	    ProxyTrustUtil.newProxyInstance(new NonRMCProxyTrust()),
	    ProxyTrustUtil.newProxyInstance(fakeObj, (InvocationHandler)
		    ProxyTrustUtil.newProxyInstance(new InvHandler(fakeObj),
			new ValidIHandler(new InvHandler(fakeObj),
				new Object[] { new TrueProxyTrust() }))),
	    ProxyTrustUtil.newProxyInstance(new TrueProxyTrust()),
	    newRMIMainProxy(new TrueProxyTrust()),
	    newMainProxy(new TrueProxyTrust()),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] {
			ProxyTrustUtil.newProxyInstance(new TrueProxyTrust())
		    })),
            ProxyTrustUtil.newProxyInstance(fakeObj, new ValidIHandler(
                    fakeObj, new Object[] {
			newRMIMainProxy(new TrueProxyTrust())
		    })),
        };
        TrustVerifier.Context[] testCtxs = new TrustVerifier.Context[] {
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
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
	    new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrueTrustVerifierContext(new Object[] { validMC }),
            new TrustVerifierContext(new Object[] { validMC }, 1),
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
