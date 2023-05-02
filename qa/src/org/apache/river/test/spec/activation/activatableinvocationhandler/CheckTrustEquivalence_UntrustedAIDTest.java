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
package org.apache.river.test.spec.activation.activatableinvocationhandler;

import java.util.logging.Level;
import net.jini.activation.ActivatableInvocationHandler;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import net.jini.activation.arg.ActivationID;
import org.apache.river.test.spec.activation.util.FakeActivationID;
import org.apache.river.test.spec.activation.util.RMCProxy;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   {@link ActivatableInvocationHandler} class checkTrustEquivalence method
 *   when handler is the same, but ActivationID checkTrustEquivalence method
 *   returnes fail.
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
 *       2) adjust FakeActivationID so that it's checkTrustEquivalence
 *          method should return false;
 *          call the checkTrustEquivalence method passing
 *          the same constructed handler as a parameter;
 *          verify the return value is false
 * </pre>
 */
public class CheckTrustEquivalence_UntrustedAIDTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        FakeActivationID aid = new FakeActivationID(logger);
        RMCProxy fp = new RMCProxy(logger);
        ActivatableInvocationHandler handler = new
                ActivatableInvocationHandler(aid, fp);
	FakeActivationID aid2 = new FakeActivationID(logger);
        RMCProxy fp2 = new RMCProxy(logger);
        ActivatableInvocationHandler handler2 = new
                ActivatableInvocationHandler(aid2, fp2);
        aid2.setTrustEquivalence(false);
        assertion(!handler.checkTrustEquivalence(handler2),
                "checkTrustEquivalence should return false,"
                + " if ActivationID.checkTrustEquivalence returns false");
    }
}
