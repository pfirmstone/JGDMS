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
import java.rmi.RemoteException;

// net.jini
import net.jini.iiop.IiopExporter;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.iiop.util.TestRemoteObject;
import org.apache.river.test.spec.iiop.util.TestRemoteInterface;
import org.apache.river.test.spec.iiop.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test tests basic IiopExporter functionality.
 *
 * Test Cases
 *   This test uses different constructors to construct IiopExporter.
 *   In cases when IiopExporter is constructed by no-arg constructor or with
 *   null orb, stub will be manually connected to the orb.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestRemoteObject - object that implements java.rmi.Remote interface.
 *     It provides 1 method:
 *     incr - increments incoming value and returns result
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a IiopExporter
 *     2) construct a TestRemoteObject
 *     3) invoke export method of constructed IiopExporter with constructed
 *        TestRemoteObject as a parameter
 *     4) assert that no exceptions will be thrown while exporting
 *        TestRemoteObject
 *     5) invoke incr method of exported TestRemoteObject in a cycle
 *     6) assert that no exceptions will be thrown during invocation of incr
 *        method
 *     7) assert that each invocation of incr method will return incoming
 *        parameter value + 1
 *     8) invoke unexport method from constructed IiopExporter
 *     9) assert that no exceptions will be thrown during invocation of
 *        unexport method
 *     10) invoke incr method of exported TestRemoteObject
 *     11) assert that java.rmi.RemoteException or
 *         org.omg.CORBA.OBJECT_NOT_EXIST will be thrown during invocation
 *         of incr method
 * </pre>
 */
public class InvocationTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        int i;
        int res;
        IiopExporter ie = createIiopExporter();
        TestRemoteObject tro = new TestRemoteObject();
        TestRemoteInterface stub;
        logger.log(Level.FINE,
                "Invoke export method of constructed IiopExporter with "
                + tro + " object as a parameter.");
        stub = (TestRemoteInterface) ie.export(tro);

        // PASS
        logger.log(Level.FINE,
                "Export method did not throw any exceptions "
                + "as expected.");

        // connect stub to the ORB manually if needed
        if ((cType == NOARG_FACTORY) || useNullOrb) {
            connectStub(stub);
        }

        for (i = 0; i < 5; ++i) {
            res = stub.incr(i);

            // PASS
            logger.log(Level.FINE,
                    "Incr method with " + i + " as a parameter did not "
                    + " throw any exceptions as expected.");

            if (res != (i + 1)) {
                // FAIL
                throw new TestException(
                        "performed incr method invocation with " + i
                        + " as a parameter returned " + res
                        + " while " + (i + 1) + " is expected.");
            } else {
                // PASS
                logger.log(Level.FINE,
                        "Performed incr method with " + i
                        + " as a parameter returned " + res
                        + " as expected.");
            }
        }
        ie.unexport(true);

        // PASS
        logger.log(Level.FINE,
                "Unexport method did not throw any exceptions "
                + "as expected.");

        try {
            stub.incr(i);

            // FAIL
            throw new TestException(
                    "performed remote invocation of incr method after "
                    + "unexporting the object did not produce any "
                    + "exceptions.");
        } catch (Exception e) {
            if ((e instanceof java.rmi.RemoteException)
                    || (e instanceof org.omg.CORBA.OBJECT_NOT_EXIST)) {
                // PASS
                logger.log(Level.FINE,
                        "Performed remote invocation of incr method after "
                        + "unexporting the object produced the following "
                        + "exception: " + e + " as expected.");
            } else {
                // FAIL
                throw new TestException(
                        "Unexpected exception " + e + " has been caught "
                        + "during remote invocation of incr method after "
                        + "unexporting the object.", e);
            }
        }
    }
}
