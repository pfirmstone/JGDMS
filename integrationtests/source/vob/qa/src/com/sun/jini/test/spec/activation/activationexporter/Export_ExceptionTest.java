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
package com.sun.jini.test.spec.activation.activationexporter;

import java.util.logging.Level;
import java.rmi.activation.ActivationID;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.activation.util.FakeActivationID;
import net.jini.export.Exporter;
import net.jini.activation.ActivationExporter;
import com.sun.jini.test.spec.activation.util.FakeExporter;
import com.sun.jini.test.spec.activation.util.MethodSetProxy;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.Remote;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the {@link ActivationExporter}
 *   class during exceptional call of export method.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) {@link FakeActivationID}
 *     2) {@link FakeExporter}
 *
 * Actions:
 *   Test performs the following steps:
 *     1) construct a {@link ActivationExporter} object passing
 *        {@link FakeActivationID} and FakeExporter as a
 *        parameters
 *     2) verify no exception was thrown
 *     3) construct a {@link ActivationExporter} object
 *        passing {@link FakeActivationID} and null as a parameters
 *     4) verify {@link NullPointerException} is thrown
 *     5) construct a activationExporter object
 *        passing null and {@link FakeExporter} as a parameters
 *     6) verify {@link NullPointerException} is thrown
 * </pre>
 */
public class Export_ExceptionTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        // action step 1
        ActivationID aid = new FakeActivationID(logger);
        Exporter exporter = new FakeExporter(logger);
        ActivationExporter activationExporter = new ActivationExporter(aid,
                exporter);
        // action step 2
        // action step 3

        try {
            Remote result = activationExporter.export(null);
            throw new TestException(
                    "NullPointerException should be thrown");
        } catch (NullPointerException t) {
            // action step 4
        }
        // action step 5
        Remote fup = new MethodSetProxy(logger);
        // action step 6
        Remote result2 = activationExporter.export(fup);

        try {
            Remote result3 = activationExporter.export(fup);
            throw new TestException(
                    "IllegalStateException should be thrown");
        } catch (IllegalStateException t) {
            // action step 7
        }
    }
}
