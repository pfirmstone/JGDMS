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
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.UnknownObjectException;
import org.apache.river.test.spec.activation.util.MethodSetProxy;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the ActivatableInvocationHandler
 *   during normal and exceptional constructor call:
 *      ActivatableInvocationHandler(
 *              net.jini.activation.arg.ActivationID id,
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
public class Constructor_AccessorTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        ActivatableInvocationHandler handler;
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
        handler = new ActivatableInvocationHandler(aid, fp);
        aid = new ActivationID(){
            @Override
            public Remote activate(boolean bln) throws ActivationException, UnknownObjectException, RemoteException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
            @Override
            public UID getUID(){
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };
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
