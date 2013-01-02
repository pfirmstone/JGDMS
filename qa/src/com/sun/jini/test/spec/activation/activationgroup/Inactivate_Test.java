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
package com.sun.jini.test.spec.activation.activationgroup;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.activation.util.FakeActivationGroup;
import com.sun.jini.test.spec.activation.util.FakeActivationGroupID;
import com.sun.jini.test.spec.activation.util.FakeActivationSystem;
import com.sun.jini.test.spec.activation.util.FakeExporter;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.activation.ActivationGroup;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupDesc.CommandEnvironment;
import net.jini.export.Exporter;
import java.util.Properties;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the inactive method
 *   of ActivationGroup class. Test create demanded infrastructure
 *   and call inactive method with different set of parameters
 *   asserting that side effect is valid.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) {@link FakeActivationGroup} - an implementation of
 *        the tested ActivationGroup class
 *     2) {@link FakeActivationSystem}
 *     3) {@link FakeActivationGroupID}
 *     4) {@link FakeExporter}
 *
 * Actions:
 *   Test performs the following steps:
 *     1) construct an FakeActivationSystem object
 *     2) construct an FakeActivationGroupID object
 *        passing FakeActivationSystem as a parameter
 *     3) run inactivate method when no group was created;
 *        assert ActivationException is thrown
 *     4) construct an ActivationGroupDesc object
 *        passing empty properties and some command line as a parameters
 *     5) run createGroup method passing FakeActivationGroupID
 *        and ActivationGroupDesc as a parameters
 *     6) run inactivate method passing different ActivationID and Exporter
 *        as a parameter, in each case it make some asserts:
 *      - assert inactiveObject method was called;
 *      - assert the activation id in inactiveObject is the same as passed
 *        to inactivate;
 *      - assert the exporter in inactiveObject is the same as passed
 *        to inactivate;
 *      - assert the result of inactivate call is the same as returned
 *        by inactiveObject
 * </pre>
 */
public class Inactivate_Test extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        FakeActivationGroup.setLogger(logger);
        ActivationSystem system = new FakeActivationSystem(logger);
        ActivationGroupID agid = new FakeActivationGroupID(logger, system);
        ActivationID aid;
        Exporter exporter;
        try {
            aid = new ActivationID(null);
            ActivationGroup.inactive(aid, null);
            throw new TestException(
                    "ActivationException should be thrown"
                     + " if group is not active");
        } catch (ActivationException t) {
            logger.log(Level.FINEST,
                    "ActivationException in case if group is not active");
        }
        Properties props = new Properties();
        ActivationGroupDesc gd = new ActivationGroupDesc(
            "com.sun.jini.test.spec.activation.util.FakeActivationGroup",
            null,
            null,
            props,
            null);
        java.rmi.activation.ActivationGroup ag =
                ActivationGroup.createGroup(agid, gd, 0);
        boolean [] cases = {true, false};
        for (int i = 0; i < cases.length; i++) {
          boolean result = cases[i];
          logger.log(Level.FINEST, "return case: " + result);
          for (int j = 0; j < cases.length; j++) {
            if (cases[j]) {
                exporter = null;
            } else {
                exporter = new FakeExporter(logger);
            }
            logger.log(Level.FINEST, "exporter: " + exporter);

            for (int k = 0; k < cases.length; k++) {
                if (cases[k]) {
                    aid = null;
                } else {
                    aid = new ActivationID(null);
                }
                logger.log(Level.FINEST, "ActivationID: " + aid);

              FakeActivationGroup.resetInactiveObjectTouch();
              FakeActivationGroup.setInactiveObjectReturn(result);
              boolean realResult = ActivationGroup.inactive(aid,
                      exporter);
              assertion(FakeActivationGroup.getInactiveObjectTouch(),
                      "ActivationGroup.inactiveObject wasn't called");
              assertion(FakeActivationGroup.getInactiveObjectActivationID()
                      == aid,
                      "Invalid Activation ID passed to inactiveObject"
                      + " method");
              assertion(FakeActivationGroup.getInactiveObjectExporter()
                      == exporter,
                      "Invalid exporter passed to inactiveObject"
                      + " method");
              assertion(result == realResult,
                      "Invalid result from inactive method");
            }
          }
        }
    }
}
