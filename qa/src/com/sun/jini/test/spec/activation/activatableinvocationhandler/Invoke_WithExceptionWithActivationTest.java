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
import com.sun.jini.test.spec.activation.util.ExceptionThrowingInterface;
import com.sun.jini.test.spec.activation.util.FakeException;
import com.sun.jini.test.spec.activation.util.ExceptionThrowingProxy;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.UnknownHostException;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the
 *   {@link ActivatableInvocationHandler} invoke method when
 *   an exception (possibly wrapped) is thrown during a call and
 *   activation is needed
 *
 * Test Cases
 *   This test contains fore test cases, one for each exception from list:
 *          {@link ConnectException}
 *          {@link ConnectIOException}
 *          {@link NoSuchObjectException}
 *          {@link UnknownHostException}
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) {@link FakeActivationID}
 *     2) {@link ExceptionThrowingInterface}
 *     3) {@link FakeException}
 *     4) {@link ExceptionThrowingProxy}
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct an ExceptionThrowingProxy that will emulate remote
 *        exception appearing
 *     2) construct an ActivatableInvocationHandler, passing the
 *        ExceptionThrowingProxy as parameter
 *     3) create new proxy instance with this constructed handler and
 *        ExceptionThrowingInterface interface
 *     4) construct a FakeActivationID that will be return proxy instance 
 *        in case of activation
 *     5) construct second ExceptionThrowingProxy that will emulate problems
 *        during invocation (exception from cases list)
 *     6) construct an ActivatableInvocationHandler, passing the
 *        second ExceptionThrowingProxy as parameter
 *     7) create new proxy instance with the last constructed handler and
 *        ExceptionThrowingInterface interface
 *     8) tune first ExceptionThrowingProxy to throw some server side exception
 *        in case of call of throwsException method
 *     9) tune second ExceptionThrowingProxy to throw tested exception in case
 *        of call of throwsException method
 *     10) invoke throwsException on the second proxy instance 
 *     11) assert the server side exception is thrown that means that after
 *        emulated problem exception was performed activation and new
 *        server side exception is the common result
 * </pre>
 */
public class Invoke_WithExceptionWithActivationTest extends QATestEnvironment implements Test {
    Throwable[] cases = {
            new ConnectException(""),
            new ConnectIOException(""),
            new NoSuchObjectException(""),
            new UnknownHostException("") 
    };

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        Throwable serverSideException = new FakeException();
        for (int i = 0; i < cases.length; i++) {
            Throwable testedException = cases[i];
            logger.log(Level.FINEST, "test case: " + testedException);
            FakeActivationID aid = new FakeActivationID(logger);
            // 1
            ExceptionThrowingProxy afup =
                    new ExceptionThrowingProxy(logger);
            // 2
            InvocationHandler handler2 = new
                    ActivatableInvocationHandler(aid, afup);
            // 3
            ExceptionThrowingInterface fakeProxy =
                    (ExceptionThrowingInterface) Proxy.newProxyInstance(
                        ExceptionThrowingInterface.class.getClassLoader(),
                        new Class[] {ExceptionThrowingInterface.class},
                        handler2);
            // 4
            FakeActivationID aid2 = new FakeActivationID(logger, fakeProxy,
                    true);
            // 5
            ExceptionThrowingProxy fup = new ExceptionThrowingProxy(logger);
            // 6
            InvocationHandler handler = new
                    ActivatableInvocationHandler(aid2, fup);
            // 7
            ExceptionThrowingInterface fi =
                    (ExceptionThrowingInterface) Proxy.newProxyInstance(
                        ExceptionThrowingInterface.class.getClassLoader(),
                        new Class[] {ExceptionThrowingInterface.class},
                        handler);
            try {
                // 8
                afup.exceptionForThrow(serverSideException);
                // 9
                fup.exceptionForThrow(testedException);
                // 10
                fi.throwsException();
                throw new TestException(
                        serverSideException.toString()
                        + " should be thrown");
            } catch (Throwable t) {
                // 11
                assertion(serverSideException.equals(t),
                        serverSideException.toString()
                        + " should be thrown");
            }
        }
    }
}
