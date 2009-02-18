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
package com.sun.jini.test.spec.activation.activatableinvocationhandler;

import java.util.logging.Level;
import net.jini.activation.ActivatableInvocationHandler;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;
import java.rmi.activation.ActivationID;
import com.sun.jini.test.spec.activation.util.FakeActivationID;
import com.sun.jini.test.spec.activation.util.RMCProxy;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   ActivatableInvocationHandler.checkTrustEquivalence method
 *   during normal and exceptional calls.
 *
 * Test Cases
 *   This test contains one test case defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeActivationID
 *     2) RMCProxy
 *
 * Actions
 *   For each test case the test performs the following steps:
 *       1) construct a ActivatableInvocationHandler object
 *          passing FakeActivationID and RMCProxy as a
 *          parameters
 *       2) call the checkTrustEquivalence method passing
 *          the same constructed handler as a parameter;
 *          verify the return value is true
 *       3) construct some object;
 *          call the checkTrustEquivalence method passing
 *          constructed object as a parameter;
 *          verify the return value is false
 *       4) construct another ActivatableInvocationHandler object
 *          passing new FakeActivationID and RMCProxy as a
 *          parameters
 *       5) adjust FakeActivationID so that it's checkTrustEquivalence
 *          method should return false;
 *          call the checkTrustEquivalence method passing
 *          the second handler as a parameter;
 *          verify the return value is false
 *       6) adjust FakeActivationID so that it's checkTrustEquivalence
 *          method should return true;
 *          call the checkTrustEquivalence method passing
 *          the second handler as a parameter;
 *          verify the return value is true
 * </pre>
 */
public class CheckTrustEquivalence_Test extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        FakeActivationID aid = new FakeActivationID(logger);
        RMCProxy fp = new RMCProxy(logger);
        ActivatableInvocationHandler handler = new
                ActivatableInvocationHandler(aid, fp);
        assertion(handler.checkTrustEquivalence(handler));
        Object o = new Object();
        assertion(!handler.checkTrustEquivalence(o));

        ActivationID aid2 = new FakeActivationID(logger);
        RMCProxy fp2 = new RMCProxy(logger);
        ActivatableInvocationHandler handler2 = new
                ActivatableInvocationHandler(aid, fp);
        aid.setTrustEquivalence(false);
        assertion(!handler.checkTrustEquivalence(handler2));
        aid.setTrustEquivalence(true);
        assertion(handler.checkTrustEquivalence(handler2));
    }
}
