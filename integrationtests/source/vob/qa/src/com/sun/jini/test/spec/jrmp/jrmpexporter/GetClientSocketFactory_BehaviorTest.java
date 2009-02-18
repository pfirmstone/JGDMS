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
import java.rmi.server.RMIClientSocketFactory;

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
 *     getClientSocketFactory method of JrmpExporter returns the client socket
 *     factory for this exporter, or null if none.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     EmptyClientSocketFactoryImpl - empty class implementing
 *     java.rmi.server.RMIClientSocketFactory interface to construct
 *     RMIClientSocketFactory instance
 *
 * Action
 *   For each test case when JrmpExporter is constructed using constructor
 *   accepting RMIClientSocketFactory parameter the test performs the following
 *   steps:
 *     1) construct a JrmpExporter using null as RMIClientSocketFactory
 *        parameter
 *     2) invoke getClientSocketFactory method of constructed JrmpExporter
 *     3) assert that getClientSocketFactory method invocation will return null
 *     4) construct a JrmpExporter using non-null RMIClientSocketFactory as a
 *        parameter
 *     5) invoke getClientSocketFactory method of constructed JrmpExporter
 *     6) assert that getClientSocketFactory method invocation will return
 *        the same RMIClientSocketFactory as specified in constructor parameter
 *   For each test case when JrmpExporter is constructed using constructor
 *   without RMIClientSocketFactory parameter the test performs the following
 *   steps:
 *     1) construct a JrmpExporter
 *     2) invoke getClientSocketFactory method of constructed JrmpExporter
 *     3) assert that getClientSocketFactory method invocation will return null
 * </pre>
 */
public class GetClientSocketFactory_BehaviorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();
        RMIClientSocketFactory resCsf = je.getClientSocketFactory();

        if (cType == SOCKS_FACTORY || cType == ID_SOCKS_FACTORY) {
            if (!resCsf.equals(cCsf)) {
                // FAIL
                throw new TestException(
                        "getClientSocketFactory method of JrmpExporter "
                        + "with " + cCsf + " returns " + resCsf
                        + " as a result while " + cCsf + " is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getClientSocketFactory method of JrmpExporter"
                        + " with " + cCsf + " returns " + cCsf
                        + " as a result as expected.");
            }
            cCsf = null;
            je = createJrmpExporter();
            resCsf = je.getClientSocketFactory();

            if (resCsf != null) {
                // FAIL
                throw new TestException(
                        "getClientSocketFactory method of JrmpExporter "
                        + "with null RMIClientSocketFactory returns "
                        + resCsf + " as a result while null is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getClientSocketFactory method of JrmpExporter with"
                        + " null RMIClientSocketFactory returns"
                        + " null as a result as expected.");
            }
        } else {
            if (resCsf != null) {
                // FAIL
                throw new TestException(
                        "getClientSocketFactory method of "
                        + "JrmpExporter constructed by constructor "
                        + "without RMIClientSocketFactory parameter "
                        + "returns " + resCsf
                        + " as a result while null is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getClientSocketFactory method of "
                        + "JrmpExporter constructed by constructor "
                        + "without RMIClientSocketFactory parameter "
                        + "returns null RMIClientSocketFactory "
                        + "as expected.");
            }
        }
    }
}
