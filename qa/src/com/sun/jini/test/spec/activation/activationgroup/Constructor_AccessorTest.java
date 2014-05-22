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

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.spec.activation.util.FakeActivationGroup;
import java.util.logging.Level;
import java.rmi.activation.ActivationGroupID;
import java.rmi.server.RemoteRef;
/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the ActivationGroup
 *   abstract class during normal constructor call.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeActivationGroup implementation of
 *        the tested ActivationGroup class
 *
 * Actions:
 *   Test performs the following steps:
 *       1) construct an ActivationGroupID object
 *          passing null as a parameter
 *       2) construct an FakeActivationGroup object
 *          passing null as a parameter
 *       3) construct an FakeActivationGroup object
 *          passing ActivationGroupID object as a parameter
 *       4) get the RemoteRef from the constructed object;
 *          assert that it is instance of UnicastServerRef
 *          (exporting the grop as UnicastRemoteObject)
 * </pre>
 */
public class Constructor_AccessorTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        FakeActivationGroup.setLogger(logger);
        FakeActivationGroup fag;
        ActivationGroupID aid;
        aid = new ActivationGroupID(null);
        fag = new FakeActivationGroup(null);
        fag = new FakeActivationGroup(aid);
        RemoteRef ref = fag.getRef();
        logger.log(Level.FINEST, "ActivationGroup.ref = " + ref);
        Class unicastServerRefClass = Class.forName("sun.rmi.server.UnicastServerRef");
        assertion(unicastServerRefClass.isInstance(ref),
                "ActivationGroup should be exported as"
                + " UnicastRemoteObject");
    }
}
