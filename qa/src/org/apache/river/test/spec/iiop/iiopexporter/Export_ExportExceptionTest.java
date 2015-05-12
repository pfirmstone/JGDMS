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

// java.rmi
import java.rmi.Remote;
import java.rmi.server.ExportException;

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
 *     IiopExporter.export method will throw ExportException if some problem
 *     occurs while exporting the object.
 *
 * Test Cases
 *   This test uses different constructors to construct IiopExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestRemoteObjectWithoutStub - class which implements java.rmi.Remote
 *     interface without generating stub/tie classes.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a IiopExporter
 *     2) construct TestRemoteObjectWithoutStub
 *     3) invoke export method from constructed IiopExporter with constructed
 *        TestRemoteObjectWithoutStub as a parameter
 *     4) assert that ExportException will be thrown
 * </pre>
 */
public class Export_ExportExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        IiopExporter ie = createIiopExporter();
        TestRemoteObjectWithoutStub trows = new TestRemoteObjectWithoutStub();

        try {
            ie.export(trows);

            // FAIL
            throw new TestException(
                    "Export method invocation with remote object"
                    + " without stub/tie classes does not produce any"
                    + " exceptions.");
        } catch (ExportException ee) {
            // PASS
            logger.log(Level.FINE,
                    "ExportException has been thrown during"
                    + " invocation of export method with remote object"
                    + " without stub/tie classes as the parameter as"
                    + " expected.");
        }
    }


    /**
     * Auxiliary class just implementing java.rmi.Remote interface.
     * Note: no stub class will be produced for this class.
     */
    class TestRemoteObjectWithoutStub implements Remote {

        /**
         * Default Constructor requiring no arguments.
         */
        public TestRemoteObjectWithoutStub() {}
    }
}
