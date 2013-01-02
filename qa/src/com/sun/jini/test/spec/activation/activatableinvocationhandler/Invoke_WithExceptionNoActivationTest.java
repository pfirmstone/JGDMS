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
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.OutboundRequest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.jini.test.spec.activation.util.FakeActivationID;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;
import java.rmi.activation.ActivationException;
import java.lang.reflect.UndeclaredThrowableException;
import com.sun.jini.test.spec.activation.util.ExceptionThrowingInterface;
import com.sun.jini.test.spec.activation.util.ExceptionThrowingProxy;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the {@link ActivatableInvocationHandler}  *   class invoke method when an exception (possibly wrapped) is thrown during
 *   a call
 *
 * Test Cases
 *   This test contains fore test cases, one for each exception from list:
 *     {@link ArrayIndexOutOfBoundsException},
 *     {@link AssertionError},
 *     {@link RemoteException},
 *     {@link MarshalException},
 *     {@link UnmarshalException},
 *     {@link SecurityException},
 *     {@link UndeclaredThrowableException},
 *     {@link NullPointerException},
 *     {@link LinkageError}
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) {@link ExceptionThrowingInterface}
 *     5) {@link ExceptionThrowingProxy}
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a ExceptionThrowingProxy
 *     2) construct a ActivatableInvocationHandler, passing the
 *        ExceptionThrowingProxy
 *     3) create proxy instance with this constructed handler and
 *        ExceptionThrowingInterface interface
 *     4) tune ExceptionThrowingProxy to throw tested exception
 *        in case of call of throwsException method
 *     5) invoke throwsException on the proxy instance 
 *     6) assert the tested exception is thrown
 * </pre>
 */
public class Invoke_WithExceptionNoActivationTest extends QATestEnvironment implements Test {
    Throwable[] cases = { 
              new ArrayIndexOutOfBoundsException(),
              new AssertionError(),
              new RemoteException(),
              new MarshalException(""),
              new UnmarshalException(""),
              new SecurityException(),
              new UndeclaredThrowableException(null),
              new NullPointerException(),
              new LinkageError()
    };

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            Throwable testedException = cases[i];
            logger.log(Level.FINEST, "test case: " + testedException);
            FakeActivationID aid = new FakeActivationID(null);
            // 1
            ExceptionThrowingProxy fup = new ExceptionThrowingProxy(logger);
            // 2
            InvocationHandler handler = new
                    ActivatableInvocationHandler(aid, fup);
            // 3
            ExceptionThrowingInterface fi = (ExceptionThrowingInterface)
                Proxy.newProxyInstance(
                        ExceptionThrowingInterface.class.getClassLoader(),
                        new Class[] {ExceptionThrowingInterface.class},
                        handler);
            try {
                // 4
                fup.exceptionForThrow(testedException);
                // 5
                fi.throwsException();
                throw new TestException(
                        testedException.toString() + " should be thrown");
            } catch (Throwable t) {
                // 6
                assertion(testedException.equals(t),
                        testedException.toString() + " should be thrown");
            }
        }
    }
}
