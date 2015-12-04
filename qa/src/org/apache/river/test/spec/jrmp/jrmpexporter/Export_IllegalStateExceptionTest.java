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
import org.apache.river.test.spec.jrmp.util.TestRemoteObject;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     JrmpExporter.export method will throw IllegalStateException if an object
 *     has already been exported via this exporter.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestRemoteObject - object that implements java.rmi.Remote interface.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a JrmpExporter
 *     2) construct TestRemoteObject1
 *     3) invoke export method from constructed JrmpExporter with constructed
 *        TestRemoteObject1 as a parameter
 *     4) invoke export method from constructed JrmpExporter with constructed
 *        TestRemoteObject1 as a parameter again
 *     5) assert that IllegalStateException will be thrown
 *     6) construct TestRemoteObject2
 *     7) invoke export method from constructed JrmpExporter with constructed
 *        TestRemoteObject2 as a parameter.
 *     8) assert that IllegalStateException will be thrown
 *     9) invoke unexport method from constructed JrmpExporter
 *     10) invoke export method from constructed JrmpExporter with constructed
 *         TestRemoteObject2 as a parameter.
 *     11) assert that IllegalStateException will be thrown
 * </pre>
 */
public class Export_IllegalStateExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();
        TestRemoteObject tro1 = new TestRemoteObject("TestObject1");
        TestRemoteObject tro2 = new TestRemoteObject("TestObject2");
        je.export(tro1);

        try {
            je.export(tro1);

            // FAIL
            throw new TestException(
                    "IllegalStateException has not been thrown during "
                    + "second invocation of export method with the same "
                    + tro1 + " as a parameter.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown during "
                    + "second invocation of export method with the same "
                    + tro1 + " as a parameter as expected.");
        }

        try {
            je.export(tro2);

            // FAIL
            throw new TestException("IllegalStateException has not been thrown "
                    + "during invocation of export method with another"
                    + tro2 + " as a parameter.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.log(Level.FINE,
                    "IllegalStateException has been thrown "
                    + "during invocation of export method with another "
                    + tro2 + " as a parameter as expected.");
        }

        // unexport test object
        je.unexport(true);
    }
}
