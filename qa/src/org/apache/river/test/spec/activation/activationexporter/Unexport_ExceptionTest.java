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
package org.apache.river.test.spec.activation.activationexporter;

import java.util.logging.Level;
import java.rmi.activation.ActivationID;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import net.jini.export.Exporter;
import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivatableInvocationHandler;
import org.apache.river.test.spec.activation.util.FakeExporter;
import org.apache.river.test.spec.activation.util.FakeActivationID;
import org.apache.river.test.spec.activation.util.MethodSetProxy;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.Remote;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the ActivationExporter
 *   class during exceptional call of unexport method.
 *
 * Test Cases:
 *   This test contains one test case defined by the Actions section below.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeActivationID
 *     2) FakeExporter
 *     3) MethodSetProxy
 *
 * Actions:
 *   Test performs the following steps:
 *     1) construct a activationExporter object passing
 *        FakeActivationID and FakeExporter as a
 *        parameters
 *     2) verify no exception was thrown
 *     3) construct some Remote object (MethodSetProxy)
 *     4) call unexport method of this activationExporter passing
 *        false as a parameter
 *     5) assert IllegalStateException is thrown
 *     6) call unexport method of this activationExporter passing
 *        true as a parameter
 *     7) assert IllegalStateException is thrown
 * </pre>
 */
public class Unexport_ExceptionTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        // action step 1
        ActivationID aid = new FakeActivationID(logger);
        Exporter exporter = new FakeExporter(logger);
        ActivationExporter activationExporter =
                new ActivationExporter(aid, exporter);

        // action step 2
        // action step 3
        Remote fup = new MethodSetProxy(logger);

        // action step 4
        try {
            boolean result2 = activationExporter.unexport(false);
            throw new TestException(
                    "IllegalStateException should be thrown");
        } catch (IllegalStateException t) {
            // action step 5
        }

        // action step 6
        try {
            boolean result2 = activationExporter.unexport(true);
            throw new TestException(
                    "IllegalStateException should be thrown");
        } catch (IllegalStateException t) {
            // action step 7
        }
    }
}
