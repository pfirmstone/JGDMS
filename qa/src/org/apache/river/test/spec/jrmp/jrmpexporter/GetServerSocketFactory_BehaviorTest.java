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
package org.apache.river.test.spec.jrmp.jrmpexporter;

import java.util.logging.Level;

// java.rmi
import java.rmi.server.RMIServerSocketFactory;

// java.util
import java.util.logging.Level;

// net.jini
import net.jini.jrmp.JrmpExporter;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.jrmp.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     getServerSocketFactory method of JrmpExporter returns the client socket
 *     factory for this exporter, or null if none.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     EmptyServerSocketFactoryImpl - empty class implementing
 *     java.rmi.server.RMIServerSocketFactory interface to construct
 *     RMIServerSocketFactory instance
 *
 * Action
 *   For each test case when JrmpExporter is constructed using constructor
 *   accepting RMIServerSocketFactory parameter the test performs the following
 *   steps:
 *     1) construct a JrmpExporter using null as RMIServerSocketFactory
 *        parameter
 *     2) invoke getServerSocketFactory method of constructed JrmpExporter
 *     3) assert that getServerSocketFactory method invocation will return null
 *     4) construct a JrmpExporter using non-null RMIServerSocketFactory as a
 *        parameter
 *     5) invoke getServerSocketFactory method of constructed JrmpExporter
 *     6) assert that getServerSocketFactory method invocation will return
 *        the same RMIServerSocketFactory as specified in constructor parameter
 *   For each test case when JrmpExporter is constructed using constructor
 *   without RMIServerSocketFactory parameter the test performs the following
 *   steps:
 *     1) construct a JrmpExporter
 *     2) invoke getServerSocketFactory method of constructed JrmpExporter
 *     3) assert that getServerSocketFactory method invocation will return null
 * </pre>
 */
public class GetServerSocketFactory_BehaviorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();
        RMIServerSocketFactory resSsf = je.getServerSocketFactory();

        if (cType == SOCKS_FACTORY || cType == ID_SOCKS_FACTORY) {
            if (!resSsf.equals(cSsf)) {
                // FAIL
                throw new TestException(
                        "getServerSocketFactory method of JrmpExporter "
                        + "with " + cSsf + " returns " + resSsf
                        + " as a result while " + cSsf + " is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getServerSocketFactory method of JrmpExporter"
                        + " with " + cSsf + " returns " + cSsf
                        + " as a result as expected.");
            }
            cSsf = null;
            je = createJrmpExporter();
            resSsf = je.getServerSocketFactory();

            if (resSsf != null) {
                // FAIL
                throw new TestException(
                        "getServerSocketFactory method of JrmpExporter "
                        + "with null RMIServerSocketFactory returns "
                        + resSsf + " as a result while null is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getServerSocketFactory method of JrmpExporter with"
                        + " null RMIServerSocketFactory returns"
                        + " null as a result as expected.");
            }
        } else {
            if (resSsf != null) {
                // FAIL
                throw new TestException(
                        "getServerSocketFactory method of "
                        + "JrmpExporter constructed by constructor "
                        + "without RMIServerSocketFactory parameter "
                        + "returns " + resSsf
                        + " as a result while null is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getServerSocketFactory method of "
                        + "JrmpExporter constructed by constructor "
                        + "without RMIServerSocketFactory parameter "
                        + "returns null RMIServerSocketFactory "
                        + "as expected.");
            }
        }
    }
}
