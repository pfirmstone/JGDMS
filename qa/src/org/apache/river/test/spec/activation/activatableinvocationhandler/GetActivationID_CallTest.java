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
package org.apache.river.test.spec.activation.activatableinvocationhandler;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UID;
import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.UnknownObjectException;
import org.apache.river.test.spec.activation.util.MethodSetProxy;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the
 *   ActivatableInvocationHandler.getActivationID method
 *   during normal call. It should return the
 *   activation identifier.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeActivationID
 *     2) MethodSetProxy
 *
 * Actions:
 *   Test performs the following steps:
 *       1) construct a ActivatableInvocationHandler object
 *          passing FakeActivationID and MethodSetProxy as a parameters
 *       2) call getActivationID method from created object
 *       3) assert the returned value is the same as used in constructor
 * </pre>
 */
public class GetActivationID_CallTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        ActivationID aid = new ActivationID(){
            @Override
            public Remote activate(boolean bln) throws ActivationException, UnknownObjectException, RemoteException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
            @Override
            public UID getUID(){
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };
        MethodSetProxy fp = new MethodSetProxy(logger);
        ActivatableInvocationHandler handler =
                new ActivatableInvocationHandler(aid, fp);
        ActivationID aid2 = handler.getActivationID();

        if (aid2 != aid) {
            throw new TestException(
                    "getActivationID method shoud return the same"
                    + " value as used in constructor");
        };
    }
}
