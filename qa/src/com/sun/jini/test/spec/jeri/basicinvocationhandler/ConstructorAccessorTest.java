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

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;

import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;

import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler
 *   during normal and exceptional constructor calls.
 *
 *   This test verifies the behavior of the
 *   BasicInvocationHandler.getObjectEndpoint,
 *   BasicInvocationHandler.getClientConstraints, and
 *   BasicInvocationHandler.getServerConstraints methods.
 *
 * Test Cases
 *   This test contains these test cases:
 *     1) new BasicInvocationHandler((BasicInvocationHandler)null,*)
 *     2) new BasicInvocationHandler((ObjectEndpoint)null,*)
 *     3) new BasicInvocationHandler(BasicInvocationHandler,MethodConstraints)
 *     4) new BasicInvocationHandler(BasicInvocationHandler,null)
 *     5) new BasicInvocationHandler(ObjectEndpoint,MethodConstraints)
 *     6) new BasicInvocationHandler(ObjectEndpoint,null)
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
 *     1) construct a BasicInvocationHandler, passing in null as the
 *        BasicInvocationHandler
 *     2) verify NullPointerException is thrown
 *     3) construct a BasicInvocationHandler, passing in null as the
 *        ObjectEndpoint
 *     4) verify NullPointerException is thrown
 *     5) construct a BasicInvocationHandler, passing in an instance of
 *        a FakeObjectEndpoint and FakeMethodConstraints
 *     6) verify the get method return the appropriate values
 *     7) construct a BasicInvocationHandler, passing in an instance of
 *        a FakeObjectEndpoint and null MethodConstraints
 *     8) verify the get method return the appropriate values
 *     9) construct a BasicInvocationHandler, passing in the instance of
 *        a BasicInvocationHandler created above and FakeMethodConstraints
 *    10) verify the get method return the appropriate values
 *    11) construct a BasicInvocationHandler, passing in the instance of
 *        a BasicInvocationHandler created above and null MethodConstraints
 *    12) verify the get method return the appropriate values
 * </pre>
 */
public class ConstructorAccessorTest extends QATestEnvironment implements Test {

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        BasicInvocationHandler handler;
        BasicInvocationHandler handler2;
        FakeMethodConstraints clientConstraints = 
            new FakeMethodConstraints(null);
        FakeMethodConstraints serverConstraints = 
            new FakeMethodConstraints(null);
        FakeObjectEndpoint oe = new FakeObjectEndpoint();

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: null constructor args");
        logger.log(Level.FINE,"");

        try {
            handler = new BasicInvocationHandler(
                (BasicInvocationHandler)null,clientConstraints);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        try {
            handler = new BasicInvocationHandler(
                (ObjectEndpoint)null,serverConstraints);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "accessor methods returns constructor args");
        logger.log(Level.FINE,"");

        handler = new BasicInvocationHandler(oe,serverConstraints);
        assertion(handler.getObjectEndpoint() == oe);
        assertion(handler.getClientConstraints() == null);
        assertion(handler.getServerConstraints() == serverConstraints);

        handler2 = new BasicInvocationHandler(handler,clientConstraints);
        assertion(handler2.getObjectEndpoint() == oe);
        assertion(handler2.getClientConstraints() == clientConstraints);
        assertion(handler2.getServerConstraints() == serverConstraints);

        handler2 = new BasicInvocationHandler(handler,null);
        assertion(handler2.getObjectEndpoint() == oe);
        assertion(handler2.getClientConstraints() == null);
        assertion(handler2.getServerConstraints() == serverConstraints);

        handler = new BasicInvocationHandler(oe,null);
        assertion(handler.getObjectEndpoint() == oe);
        assertion(handler.getClientConstraints() == null);
        assertion(handler.getServerConstraints() == null);

        handler2 = new BasicInvocationHandler(handler,clientConstraints);
        assertion(handler2.getObjectEndpoint() == oe);
        assertion(handler2.getClientConstraints() == clientConstraints);
        assertion(handler2.getServerConstraints() == null);

        handler2 = new BasicInvocationHandler(handler,null);
        assertion(handler2.getObjectEndpoint() == oe);
        assertion(handler2.getClientConstraints() == null);
        assertion(handler2.getServerConstraints() == null);
    }

    // inherit javadoc
    public void tearDown() {
    }

}

