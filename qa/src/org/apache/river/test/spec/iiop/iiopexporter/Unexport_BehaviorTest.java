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


/**
 * <pre>
 * Purpose
 *  This test verifies the following:
 *    IiopExporter.unexport method will always return true.
 *
 * Test Cases
 *   This test uses different constructors to construct IiopExporter.
 *   In cases when IiopExporter is constructed by no-arg constructor or with
 *   null orb, stub will be manually connected to the orb.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestRemoteObject - object that implements java.rmi.Remote interface.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a IiopExporter1
 *     2) construct a TestRemoteObject
 *     3) invoke export method of constructed IiopExporter1 with constructed
 *        TestRemoteObject as a parameter
 *     4) invoke unexport method from constructed IiopExporter1 with true value
 *        for 'force' parameter
 *     5) assert that unexport method invocation will return true as a result
 *     6) invoke unexport method of constructed IiopExporter1 with true value
 *        for 'force' parameter again
 *     7) assert that unexport method invocation will return true as a result
 *     8) invoke unexport method of constructed IiopExporter1 with false value
 *        for 'force' parameter
 *     9) assert that unexport method invocation will return true as a result
 *     10) construct a IiopExporter2
 *     11) invoke export method of constructed IiopExporter2 with constructed
 *         TestRemoteObject as a parameter
 *     12) invoke unexport method of constructed IiopExporter2 with false
 *         value for 'force' parameter
 *     13) assert that unexport method invocation will return true as a result
 *     14) invoke unexport method of constructed IiopExporter2 with false
 *         value for 'force' parameter again
 *     15) assert that unexport method invocation will return true as a result
 *     16) invoke unexport method of constructed IiopExporter2 with true
 *         value for 'force' parameter
 *     17) assert that unexport method invocation will return true as a result
 *     18) construct a IiopExporter3
 *     19) invoke export method of constructed IiopExporter3 with constructed
 *         TestRemoteObject as a parameter
 *     20) start thread which will invoke unexport method of constructed
 *         IiopExporter3
 *     21) invoke remote method of constructed TestRemoteObject
 *     22) invoke unexport method of constructed IiopExporter3 with false
 *         value for 'force' parameter (in started separate thread)
 *     23) assert that unexport method invocation will return true as a result
 *     24) construct a IiopExporter4
 *     25) invoke export method of constructed IiopExporter4 with constructed
 *         TestRemoteObject as a parameter
 *     26) start thread which will invoke unexport method of constructed
 *         IiopExporter4
 *     27) invoke remote method of constructed TestRemoteObject
 *     28) invoke unexport method of constructed IiopExporter4 with true
 *         value for 'force' parameter (in started separate thread)
 *     29) assert that unexport method invocation will return true as a result
 * </pre>
 */
public class Unexport_BehaviorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        IiopExporter ie1 = createIiopExporter();
        TestRemoteObject tro = new TestRemoteObject();
        ie1.export(tro);
        logger.log(Level.FINE,
                "Invoke unexport method of constructed"
                + " IiopExporter1 with 'true' value...");
        boolean uRes = ie1.unexport(true);

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter1 with 'true' value has returned false "
                    + "while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        logger.log(Level.FINE,
                "Invoke unexport method of constructed"
                + " IiopExporter1 with 'true' value again...");
        uRes = ie1.unexport(true);

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter1 with 'true' value again has returned "
                    + "false while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        logger.log(Level.FINE,
                "Invoke unexport method of constructed"
                + " IiopExporter1 with 'false' value...");
        uRes = ie1.unexport(false);

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter1 with 'false' value has returned false "
                    + "while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        IiopExporter ie2 = createIiopExporter();
        ie2.export(tro);
        logger.log(Level.FINE,
                "Invoke unexport method of constructed"
                + " IiopExporter2 with 'false' value...");
        uRes = ie2.unexport(false);

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter2 with 'false' value has returned false "
                    + "while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        logger.log(Level.FINE,
                "Invoke unexport method of constructed"
                + " IiopExporter2 with 'false' value again...");
        uRes = ie2.unexport(false);

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter2 with 'false' value again has returned "
                    + "false while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        logger.log(Level.FINE,
                "Invoke unexport method of constructed"
                + " IiopExporter2 with 'true' value...");
        uRes = ie2.unexport(true);

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter2 with 'true' value has returned false "
                    + "while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        IiopExporter ie3 = createIiopExporter();
        TestRemoteInterface stub = (TestRemoteInterface) ie3.export(tro);

        // bind stub to the ORB manually if needed
        if ((cType == NOARG_FACTORY) || useNullOrb) {
            connectStub(stub);
        }
        Unexporter u = new Unexporter(ie3, false);
        logger.log(Level.FINE,
                "Start thread which will invoke unexport method"
                + " of constructed IiopExporter3 with 'false' value...");
        u.start();
        stub.wait(new Integer(5000));
        uRes = u.getResult();

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter3 with 'false' value while remote "
                    + "call is in progress has returned false "
                    + "while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
        IiopExporter ie4 = createIiopExporter();
        stub = (TestRemoteInterface) ie4.export(tro);

        // bind stub to the ORB manually if needed
        if ((cType == NOARG_FACTORY) || useNullOrb) {
            connectStub(stub);
        }
        u = new Unexporter(ie4, true);
        logger.log(Level.FINE,
                "Start thread which will invoke unexport method"
                + " of constructed IiopExporter4 with 'true' value...");
        u.start();
        stub.wait(new Integer(5000));
        uRes = u.getResult();

        if (!uRes) {
            // FAIL
            throw new TestException(
                    "performed unexport method invocation of constructed "
                    + "IiopExporter4 with 'true' value while remote "
                    + "call is in progress has returned false "
                    + "while true is expected.");
        } else {
            // PASS
            logger.log(Level.FINE, "Method returned true as expected.");
        }
    }


    /**
     * Auxiliary class which will invoke unexport() method of specified
     * JrmpExporter.
     */
    class Unexporter extends Thread {

        /** IiopExporter which unexport method will be invoked */
        IiopExporter iExp;

        /** Value of parameter for unexport method invocation */
        boolean val;

        /** Result of unexport method invocation */
        boolean res;

        /**
         * Constructor which initialize fields of the class.
         *
         * @param exp IiopExporter instance.
         * @param val value fo unexport method invocation
         */
        public Unexporter(IiopExporter exp, boolean val) {
            this.iExp = exp;
            this.val = val;
            res = false;
        }

        /**
         * Main method which will invoke unexport() method of JrmpExporter.
         */
        public void run() {
            try {
                // wait for a while to let main thread start remote invocation
                sleep(1000);

                // invoke unexport method
                res = iExp.unexport(val);
            } catch (Exception ex) {}
        }

        /**
         * Returns result of unexport method invocation.
         *
         * @return result of unexport method invocation
         */
        public boolean getResult() {
            return res;
        }
    }
}
