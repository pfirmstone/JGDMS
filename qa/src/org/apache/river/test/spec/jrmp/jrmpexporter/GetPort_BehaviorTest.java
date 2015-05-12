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
 *     getPort method of JrmpExporter returns the port used by this exporter,
 *     or zero if an anonymous port is used.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Action
 *   For each test case except constructor with no arguments the test
 *   performs the following steps:
 *     1) construct a JrmpExporter using 0 as the port parameter
 *        (anonymous port)
 *     2) invoke getPort method of constructed JrmpExporter
 *     3) assert that getPort method invocation will return 0
 *     4) construct a JrmpExporter using non-zero port as a parameter
 *     5) invoke getPort method of constructed JrmpExporter
 *     6) assert that getPort method invocation will return the same port number
 *        as specified in constructor parameter
 *  For constructor with no arguments the test performs the following steps:
 *    1) construct a JrmpExporter using no-arg constructor
 *    2) invoke getPort method of constructed JrmpExporter
 *    3) assert that getPort method invocation will return 0
 * </pre>
 */
public class GetPort_BehaviorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();
        int port = je.getPort();

        if (cType != NOARG_FACTORY) {
            if (port != cPort) {
                // FAIL
                throw new TestException(
                        "getPort method of JrmpExporter with " + cPort
                        + " port returns " + port + " as a result while "
                        + cPort + " is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getPort method of JrmpExporter with " + cPort
                        + " port returns " + cPort + " as expected.");
            }
            cPort = 0;
            je = createJrmpExporter();
            port = je.getPort();

            if (port != 0) {
                // FAIL
                throw new TestException(
                        "getPort method of JrmpExporter with "
                        + "anonymous port returns " + port
                        + " as a result while 0 is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getPort method of JrmpExporter with "
                        + "anonymous port returns 0 as expected.");
            }
        } else {
            if (port != 0) {
                // FAIL
                throw new TestException(
                        "getPort method of JrmpExporter "
                        + " constructed by no-arg constructor returns "
                        + port + " as a result while 0 is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "getPort method of JrmpExporter constructed"
                        + " by no-arg constructor returns 0 as expected.");
            }
        }
    }
}
