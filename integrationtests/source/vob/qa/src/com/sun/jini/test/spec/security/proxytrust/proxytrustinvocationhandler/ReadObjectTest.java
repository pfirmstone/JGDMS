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

// java
import java.io.InvalidObjectException;
import java.rmi.MarshalledObject;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.RMCTEImpl;
import com.sun.jini.test.spec.security.proxytrust.util.RMCPTTEImpl;
import com.sun.jini.test.spec.security.proxytrust.util.RMCTEReadRMC;
import com.sun.jini.test.spec.security.proxytrust.util.RMCPTTEReadRMCPT;
import com.sun.jini.test.spec.security.proxytrust.util.RMCPTTEReadPTTE;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     ReadObject method of ProxyTrustInvocationHandler verifies that the main
 *     proxy is an instance of TrustEquivalence, and that the bootstrap proxy
 *     is an instance of both RemoteMethodControl and TrustEquivalence.
 *     It throws InvalidObjectException if the main proxy is not an instance of
 *     TrustEquivalence, or the bootstrap proxy is not an instance of both
 *     RemoteMethodControl and TrustEquivalence.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     RMCImpl - class implementing Serializable and RemoteMethodControl
 *             interfaces
 *     RMCPTImpl - class implementing Serializable, RemoteMethodControl and
 *             ProxyTrust interfaces
 *     PTTEImpl - class implementing Serializable, ProxyTrust and
 *             TrustEquivalence interfaces
 *     RMCTEReadRMC - class implementing Serializable, RemoteMethodControl and
 *             TrustEquivalence interfaces, having 'readResolve' which
 *             returns RMCImpl instance
 *     RMCPTTEReadRMCPT - class implementing Serializable, ProxyTrust,
 *             RemoteMethodControl and TrustEquivalence interfaces, having
 *             'readResolve' which returns RMCPTImpl instance
 *     RMCPTTEReadPTTE - class implementing Serializable, ProxyTrust,
 *             RemoteMethodControl and TrustEquivalence interfaces, having
 *             'readResolve' which returns PTTEImpl instance
 *     RMCTEImpl - class implementing Serializable, RemoteMethodControl and
 *             TrustEquivalence interfaces
 *     RMCPTTEImpl - class implementing Serializable, RemoteMethodControl,
 *             ProxyTrust and TrustEquivalence interfaces
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler1 with RMCTEImpl and RMCPTTEImpl
 *        as parameters
 *     2) construct MarshalledObject1 with ProxyTrustInvocationHandler1 as a
 *        parameter
 *     3) invoke get method of MarshalledObject1
 *     4) assert that object will be got without exceptions
 *     5) construct ProxyTrustInvocationHandler2 with RMCTEReadRMC and
 *        RMCPTTEImpl as parameters
 *     6) construct MarshalledObject2 with ProxyTrustInvocationHandler2 as a
 *        parameter
 *     7) invoke get method of MarshalledObject2
 *     8) assert that InvalidObjectException will be thrown
 *     9) construct ProxyTrustInvocationHandler3 with RMCTEImpl and
 *        RMCPTTEReadRMCPT as parameters
 *     10) construct MarshalledObject3 with ProxyTrustInvocationHandler3 as a
 *         parameter
 *     11) invoke get method of MarshalledObject3
 *     12) assert that InvalidObjectException will be thrown
 *     13) construct ProxyTrustInvocationHandler4 with RMCTEImpl and
 *         RMCPTTEReadPTTE as parameters
 *     14) construct MarshalledObject4 with ProxyTrustInvocationHandler4 as a
 *         parameter
 *     15) invoke get method of MarshalledObject4
 *     16) assert that InvalidObjectException will be thrown
 * </pre>
 */
public class ReadObjectTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = new RMCTEImpl();
        ProxyTrust boot = new RMCPTTEImpl();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        MarshalledObject mo = new MarshalledObject(ptih);

        try {
            mo.get();

            // PASS
            logger.fine("'get' method invocation of MarshalledObject "
                    + "did not throw any exception as expected.");
        } catch (Exception e) {
            // FAIL
            throw new TestException(
                    "'get' method invocation of MarshalledObject "
                    + "threw " + e + " while no exceptions were expected.");
        }
        ptih = createPTIH(new RMCTEReadRMC(), boot);
        mo = new MarshalledObject(ptih);

        try {
            mo.get();

            // FAIL
            throw new TestException(
                    "'get' method invocation of MarshalledObject did not "
                    + "throw any exception while InvalidObjectException "
                    + "was expected.");
        } catch (InvalidObjectException ioe) {
            // PASS
            logger.fine("'get' method invocation of MarshalledObject "
                    + "threw InvalidObjectException as expected.");
        }
        ptih = createPTIH(main, new RMCPTTEReadRMCPT());
        mo = new MarshalledObject(ptih);

        try {
            mo.get();

            // FAIL
            throw new TestException(
                    "'get' method invocation of MarshalledObject did not "
                    + "throw any exception while InvalidObjectException "
                    + "was expected.");
        } catch (InvalidObjectException ioe) {
            // PASS
            logger.fine("'get' method invocation of MarshalledObject "
                    + "threw InvalidObjectException as expected.");
        }
        ptih = createPTIH(main, new RMCPTTEReadPTTE());
        mo = new MarshalledObject(ptih);

        try {
            mo.get();

            // FAIL
            throw new TestException(
                    "'get' method invocation of MarshalledObject did not "
                    + "throw any exception while InvalidObjectException "
                    + "was expected.");
        } catch (InvalidObjectException ioe) {
            // PASS
            logger.fine("'get' method invocation of MarshalledObject "
                    + "threw InvalidObjectException as expected.");
        }
    }
}
