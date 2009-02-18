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
package com.sun.jini.test.spec.activation.activatableinvocationhandler;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import java.rmi.activation.ActivationID;
import net.jini.activation.ActivatableInvocationHandler;
import com.sun.jini.test.spec.activation.util.MethodSetProxy;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the ActivatableInvocationHandler
 *   during normal and exceptional constructor call:
 *      ActivatableInvocationHandler(
 *              java.rmi.activation.ActivationID id,
 *              java.rmi.Remote underlyingProxy)
 *   Chack that constructor throws NullPointerException if the id
 *   is null.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) MethodSetProxy
 *
 * Actions:
 *   Test performs the following steps:
 *       1) construct a ActivatableInvocationHandler object
 *          passing FakeActivationID and MethodSetProxy as a
 *          parameters
 *          verify instance of ActivatableInvocationHandler is created
 *       2) construct a ActivatableInvocationHandler object
 *          passing FakeActivationID and null as a parameters
 *          verify instance of ActivatableInvocationHandler is created
 *       3) construct a ActivatableInvocationHandler object
 *          passing null and null as a parameters
 *          verify NullPointerException is thrown
 * </pre>
 */
public class Constructor_AccessorTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        ActivatableInvocationHandler handler;
        ActivationID aid = new ActivationID(null);
        MethodSetProxy fp = new MethodSetProxy(logger);
        handler = new ActivatableInvocationHandler(aid, fp);
        aid = new ActivationID(null);
        handler = new ActivatableInvocationHandler(aid, null);
        try {
            handler = new ActivatableInvocationHandler(null, null);
            throw new TestException(
                    "ActivatableInvocationHandler constructior"
                    + " should throws NullPointerException if"
                    + " the activation identifier is null");
        } catch (NullPointerException ignore) {
        }
    }
}
