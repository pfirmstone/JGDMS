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
 *     JrmpExporter.unexport method will throw IllegalStateException if an
 *     object was not exported via this exporter.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a JrmpExporter
 *     2) invoke unexport method from constructed JrmpExporter with true as
 *        a parameter
 *     3) assert that IllegalStateException will be thrown
 *     4) invoke unexport method from constructed JrmpExporter with false as
 *        a parameter
 *     5) assert that IllegalStateException will be thrown
 * </pre>
 */
public class Unexport_IllegalStateExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();

        try {
            je.unexport(true);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown "
                    + "during invocation of unexport method with "
                    + "true as a parameter when no objects were previously "
                    + "exported via this JrmpExporter.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of unexport method with "
                    + "true as a parameter as expected.");
        }

        try {
            je.unexport(false);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown "
                    + "during invocation of unexport method with "
                    + "false as a parameter when no objects were "
                    + "previously exported via this JrmpExporter.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of unexport method with "
                    + "false as a parameter as expected.");
        }
    }
}
