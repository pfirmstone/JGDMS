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
package org.apache.river.test.spec.iiop.iiopexporter;

import java.util.logging.Level;

// java.util
import java.util.logging.Level;

// net.jini
import net.jini.iiop.IiopExporter;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.iiop.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     IiopExporter.unexport method will throw IllegalStateException if an
 *     object was not exported via this exporter.
 *
 * Test Cases
 *   This test uses different constructors to construct IiopExporter.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct IiopExporter1
 *     2) invoke unexport method from constructed IiopExporter1 with true as
 *        a parameter
 *     3) assert that IllegalStateException will be thrown
 *     4) invoke unexport method from constructed IiopExporter1 with false as
 *        a parameter
 *     5) assert that IllegalStateException will be thrown
 *     6) construct IiopExporter2
 *     7) invoke unexport method from constructed IiopExporter2 with false as
 *        a parameter
 *     8) assert that IllegalStateException will be thrown
 *     9) invoke unexport method from constructed IiopExporter2 with true as
 *        a parameter
 *     10) assert that IllegalStateException will be thrown
 * </pre>
 */
public class Unexport_IllegalStateExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        IiopExporter ie1 = createIiopExporter();

        try {
            ie1.unexport(true);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown "
                    + "during invocation of unexport method with "
                    + "true as a parameter when no objects were previously "
                    + "exported via this IiopExporter1.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of unexport method with "
                    + "true as a parameter of IiopExporter1 as expected.");
        }

        try {
            ie1.unexport(false);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown "
                    + "during invocation of unexport method with "
                    + "false as a parameter when no objects were "
                    + "previously exported via this IiopExporter1.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of unexport method with "
                    + "false as a parameter of IiopExporter1 as expected.");
        }
        IiopExporter ie2 = createIiopExporter();

        try {
            ie2.unexport(false);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown "
                    + "during invocation of unexport method with "
                    + "false as a parameter when no objects were previously"
                    + " exported via this IiopExporter2.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of unexport method with "
                    + "false as a parameter of IiopExporter2 as expected.");
        }

        try {
            ie2.unexport(true);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown "
                    + "during invocation of unexport method with "
                    + "true as a parameter when no objects were "
                    + "previously exported via this IiopExporter2.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of unexport method with "
                    + "true as a parameter of IiopExporter2 as expected.");
        }
    }
}
