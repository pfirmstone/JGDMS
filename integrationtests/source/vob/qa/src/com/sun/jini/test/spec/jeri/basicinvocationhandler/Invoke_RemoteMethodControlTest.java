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
package com.sun.jini.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.Confidentiality;
import net.jini.jeri.BasicInvocationHandler;

import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the 
 *   BasicInvocationHandler.invoke when the method invoked is
 *   RemoteMethodControl.setConstraints.
 *
 * Test Cases
 *   This test contains these test cases:
 *     1) setConstraints(null)
 *     2) setConstraints(MethodConstraints) returns normally
 *     3) BasicInvocationHandler.invoke with setContraints method 
 *        but bad proxy argument
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObjectEndpoint
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *     2) FakeMethodConstraints
 *          -getConstraints method returns InvocationConstraints created
 *           from InvocationConstraint[] passed to constructor or, if null,
 *           InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an iterator over
 *           return value of getConstraints
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeObjectEndpoint
 *     2) construct a FakeMethodConstraints
 *     3) construct a BasicInvocationHandler, passing in FakeObjectEndpoint
 *        and FakeMethodConstraints
 *     4) create a dynamic proxy for RemoteMethodControl using the
 *        BasicInvocationHandler
 *     5) invoke setConstraints(null) on the dynamic proxy
 *     6) verify the get method return the appropriate values
 *     7) invoke setConstraints(MethodConstraints) on the dynamic proxy
 *     8) verify the get method return the appropriate values
 *     9) invoke BasicInvocationHandler.invoke with setContraints method 
 *        but bad proxy argument
 *    10) verify an exception is thrown
 * </pre>
 */
public class Invoke_RemoteMethodControlTest extends QATest {

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        try {
            BasicInvocationHandler handler;
            RemoteMethodControl proxy, newProxy;
            FakeMethodConstraints clientConstraints = new FakeMethodConstraints(
                new InvocationConstraint[] {Integrity.YES});
            FakeMethodConstraints serverConstraints = new FakeMethodConstraints(
                new InvocationConstraint[] {Confidentiality.YES});
            FakeObjectEndpoint oe = new FakeObjectEndpoint();

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 1: setConstraints(null)");
            logger.log(Level.FINE,"");

            handler = new BasicInvocationHandler(
                new BasicInvocationHandler(oe,serverConstraints),
                clientConstraints);
            proxy = (RemoteMethodControl) Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[] {RemoteMethodControl.class}, handler);

            newProxy = 
                (RemoteMethodControl) proxy.setConstraints(null);
            handler = 
                (BasicInvocationHandler) Proxy.getInvocationHandler(newProxy);
            assertion(handler.getObjectEndpoint() == oe);
            assertion(handler.getClientConstraints() == null);
            assertion(handler.getServerConstraints() == serverConstraints);
            assertion(proxy.getConstraints() == clientConstraints);
            assertion(newProxy.getConstraints() == null);

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 2: "
                + "setConstraints(MethodConstraints)");
            logger.log(Level.FINE,"");

            handler = new BasicInvocationHandler(
                new BasicInvocationHandler(oe,serverConstraints),
                null);
            proxy = (RemoteMethodControl) Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[] {RemoteMethodControl.class}, handler);

            newProxy = 
                (RemoteMethodControl) proxy.setConstraints(clientConstraints);
            handler =
                (BasicInvocationHandler) Proxy.getInvocationHandler(newProxy);
            assertion(handler.getObjectEndpoint() == oe);
            assertion(handler.getClientConstraints() == clientConstraints);
            assertion(handler.getServerConstraints() == serverConstraints);
            assertion(proxy.getConstraints() == null);
            assertion(newProxy.getConstraints() == clientConstraints);

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 3: bad proxy arg");
            logger.log(Level.FINE,"");

            handler = new BasicInvocationHandler(oe,serverConstraints);
            Method m = RemoteMethodControl.class.getMethod(
                "setConstraints",new Class[] {MethodConstraints.class});
            try {
                handler.invoke(new Object(),m,new Object[] {clientConstraints});
                assertion(false);
            } catch (Exception ignore) {
            }

        } catch (Throwable t) {
            logger.log(Level.FINE,"Caught unexpected exception",t);
            throw new TestException("Caught unexpected exception: ",t);
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

