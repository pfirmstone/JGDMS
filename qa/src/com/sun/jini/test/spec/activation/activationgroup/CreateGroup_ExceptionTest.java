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
import com.sun.jini.test.spec.activation.util.FakeActivationGroupID;
import com.sun.jini.test.spec.activation.util.FakeActivationSystem;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.activation.ActivationGroup;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupDesc.CommandEnvironment;
import java.util.Properties;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the createGroup method
 *   of {@link ActivationGroup} class when invalig {@link ActivationGroupDesc}
 *   ia passed as a parameter. {@link ActivationException} is waited.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) {@link FakeActivationGroupID}
 *     2) {@link FakeActivationSystem}
 *
 * Actions:
 *   Test performs the following steps:
 *       1) construct an FakeActivationSystem object
 *       2) construct an FakeActivationGroupID object
 *          passing FakeActivationSystem as a parameter
 *       2) construct an ActivationGroupDesc object
 *          passing empty properties and some command line as
 *          a parameters. It will be invalid ActivationGroupDesc
 *          because it refers not to subclass of
 *          net.jini.activation.ActivationGroup
 *       3) run createGroup method passing FakeActivationGroupID
 *          and ActivationGroupDesc as a parameters
 *       4) verify ActivationException is thrown
 * </pre>
 */
public class CreateGroup_ExceptionTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        ActivationSystem system = new FakeActivationSystem(logger);
        ActivationGroupID agid = new FakeActivationGroupID(logger, system);
        String program = "java";
        String[] options = {""};
        Properties props = new Properties();
        CommandEnvironment cmd = new CommandEnvironment(program, options);
        ActivationGroupDesc gd = new ActivationGroupDesc(props, cmd);
        try {
            java.rmi.activation.ActivationGroup ag =
                    ActivationGroup.createGroup(agid, gd, 0);
        } catch (ActivationException ae) {
        }
    }
}
