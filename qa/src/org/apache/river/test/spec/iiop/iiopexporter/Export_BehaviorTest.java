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
import org.apache.river.test.spec.iiop.util.TestRemoteObject;
import org.apache.river.test.spec.iiop.util.TestRemoteInterface;
import org.apache.river.test.spec.iiop.util.AbstractTestBase;

// javax.rmi
import javax.rmi.CORBA.Stub;

// org.omg
import org.omg.CORBA.ORB;
import org.omg.CORBA.BAD_OPERATION;


/**
 * <pre>
 * Purpose
 *  This test verifies the following:
 *    If an ORB was specified during construction of this exporter, then the
 *    returned by export method RMI-IIOP stub will be connected to it.
 *
 * Test Cases
 *   This test uses different constructors to construct IiopExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestRemoteObject - object that implements java.rmi.Remote interface.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a IiopExporter1, using no-arg constructor
 *     2) construct a TestRemoteObject
 *     3) invoke export method of constructed IiopExporter1 with constructed
 *        TestRemoteObject as a parameter
 *     4) when IiopExporter is constructed using non-null ORB:
 *        assert that returned RMI-IIOP stub will be connected to the same
 *        ORB as specified in constructor's parameter.
 *        Otherwise (when IiopExporter is constructed using null ORB or
 *        by no-arg constructor: assert that returned RMI-IIOP stub will
 *        not be connected to any ORB
 * </pre>
 */
public class Export_BehaviorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        IiopExporter ie = createIiopExporter();
        TestRemoteObject tro = new TestRemoteObject("TestObject");
        TestRemoteInterface stub = (TestRemoteInterface) ie.export(tro);
        ORB o;

        if ((cType == NOARG_FACTORY) || useNullOrb) {
            // we used no-arg constructor or null orb
            try {
                o = ((Stub) stub)._get_delegate().orb((Stub) stub);

                if (o == null) {
                    // PASS
                    logger.log(Level.FINE,
                            "Stub returned by export method is not "
                            + "connected to any ORB as expected.");
                } else {
                    // FAIL
                    throw new TestException(
                            "Stub returned by export method is connected "
                            + "to " + o + " ORB while it was expected that "
                            + "it will be unconnected.");
                }
            } catch (BAD_OPERATION bo) {
                // PASS
                logger.log(Level.FINE,
                        "Stub returned by export method is not connected "
                        + "to any ORB as expected.");
            }
        } else {
            // we used non-null orb in IiopExporter's constructor
            o = ((Stub) stub)._get_delegate().orb((Stub) stub);

            if (o != orb) {
                // FAIL
                throw new TestException(
                        "Stub returned by export method is connected to "
                        + o + " while " + orb + " is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "Stub returned by export method is connected to "
                        + "the same ORB as specified in IiopExporter's "
                        + "constructor as expected.");
            }
        }
    }
}
