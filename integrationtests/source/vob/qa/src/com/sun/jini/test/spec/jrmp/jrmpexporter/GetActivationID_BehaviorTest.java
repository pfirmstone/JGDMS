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
package com.sun.jini.test.spec.jrmp.jrmpexporter;

import java.util.logging.Level;

// java.rmi
import java.rmi.activation.ActivationID;

// java.util
import java.util.logging.Level;

// net.jini
import net.jini.jrmp.JrmpExporter;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.jrmp.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     getActivationID method of JrmpExporter returns the activation ID
 *     associated with the object exported by this exporter, or null if
 *     activation is not being used with this exporter.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     EmptyActivatorImpl - empty class implementing
 *     java.rmi.activation.Activator interface to construct ActivationID
 *     instance
 *
 * Action
 *   For each test case when JrmpExporter is constructed using constructor
 *   accepting ActivationID parameter the test performs the following steps:
 *     1) construct a JrmpExporter using non-null ActivationID parameter
 *     2) invoke getActivationID method of constructed JrmpExporter
 *     3) assert that getActivationID method invocation will return ActivationID
 *        specified in JrmpExporter's constructor invocation
 *   For each test case when JrmpExporter is constructed using constructor
 *   without ActivationID parameter the test performs the following steps:
 *     1) construct a JrmpExporter
 *     2) invoke getActivationID method of constructed JrmpExporter
 *     3) assert that getActivationID method invocation will return null
 * </pre>
 */
public class GetActivationID_BehaviorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();
        ActivationID resId = je.getActivationID();

        if (cId == null) {
            if (resId != null) {
                // FAIL
                throw new TestException(
                        "getActivationID method of JrmpExporter constructed"
                        + " by constructor without ActivationID parameter"
                        + " returns " + resId
                        + " as a result while null is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getActivationID method of JrmpExporter constructed"
                        + " by constructor without ActivationID parameter"
                        + " returns null ActivationID as expected.");
            }
        } else {
            if (!resId.equals(cId)) {
                // FAIL
                throw new TestException(
                        "getActivationID method of JrmpExporter with " + cId
                        + " returns " + resId + " as a result while " + cId
                        + " is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getActivationID method of JrmpExporter with "
                        + cId + " returns " + cId
                        + " as a result as expected.");
            }
        }
    }
}
