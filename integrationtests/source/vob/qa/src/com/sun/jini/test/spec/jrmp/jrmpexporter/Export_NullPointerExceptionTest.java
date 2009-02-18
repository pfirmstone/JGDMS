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
 *     JrmpExporter.export method will throw NullPointerException if remote
 *     object to export is null.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a JrmpExporter
 *     2) invoke export method from constructed JrmpExporter with null remote
 *        object to export
 *     3) assert that NullPointerException will be thrown
 * </pre>
 */
public class Export_NullPointerExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        JrmpExporter je = createJrmpExporter();

        try {
            je.export(null);

            // FAIL
            throw new TestException("NullPointerException has not been thrown "
                    + "during invocation of export method with "
                    + "null as a parameter.");
        } catch (NullPointerException npe) {
            // PASS
            logger.log(Level.FINE,
                    "NullPointerException has been thrown "
                    + "during invocation of export method with "
                    + "null as a parameter as expected.");
        }
    }
}
