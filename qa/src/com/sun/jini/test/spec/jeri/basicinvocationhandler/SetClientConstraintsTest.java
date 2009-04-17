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

import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;
import net.jini.core.constraint.MethodConstraints;

import com.sun.jini.test.spec.jeri.util.FakeBasicInvocationHandler;
import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the 
 *   BasicInvocationHandler.setClientConstraints method.
 *
 * Test Cases
 *   This test contains these test cases:
 *     1) setClientConstraints(null)
 *     2) setClientConstraints(MethodConstraints) returns normally
 *     3) setClientConstraints(MethodConstraints) throws exception
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
 *     3) FakeBasicInvocationHandler
 *          -subclasses BasicInvocationHandler
 *          -gives access to protected methods
 *     4) FakeBadConstructorBasicInvocationHandler
 *          -subclasses BasicInvocationHandler
 *          -has only no-arg constructor
 *     5) FakeConstructorExceptionBasicInvocationHandler
 *          -subclasses BasicInvocationHandler
 *          -constructor throws exception
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeBasicInvocationHandler, passing in an instance of
 *        a FakeObjectEndpoint and FakeMethodConstraints
 *     2) call setClientConstraints(null)
 *     3) verify the get method return the appropriate values
 *     4) construct a FakeMethodConstraints
 *     5) call setClientConstraints(FakeMethodConstraints)
 *     6) verify the get method return the appropriate values
 *     7) construct a FakeBadConstructorBasicInvocationHandler
 *     8) call setClientConstraints(FakeMethodConstraints)
 *     9) verify UndeclaredThrowableException is thrown
 *    10) construct a FakeConstructorExceptionBasicInvocationHandler
 *    11) call setClientConstraints(FakeMethodConstraints)
 *    12) verify UndeclaredThrowableException is thrown
 * </pre>
 */
public class SetClientConstraintsTest extends QATest {

    // a BasicInvocationHandler with a non-conforming constructor
    class FakeBadConstructorBasicInvocationHandler extends
          FakeBasicInvocationHandler
    {
        public FakeBadConstructorBasicInvocationHandler() {
            super(new FakeObjectEndpoint(),null);
        }
    }

    // a BasicInvocationHandler that throws an exception in its constructor
    class FakeConstructorExceptionBasicInvocationHandler extends
          FakeBasicInvocationHandler
    {
        public FakeConstructorExceptionBasicInvocationHandler() {
            super(new FakeObjectEndpoint(),null);
        }
        public FakeConstructorExceptionBasicInvocationHandler(
            FakeConstructorExceptionBasicInvocationHandler bih,
            MethodConstraints mc) throws Exception
        {
            super(new FakeObjectEndpoint(),null);
            throw new Exception();
        }
    }

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeMethodConstraints clientConstraints = 
            new FakeMethodConstraints(null);
        FakeBasicInvocationHandler newHandler;
        FakeBasicInvocationHandler handler = 
            new FakeBasicInvocationHandler(new FakeObjectEndpoint(),null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: setClientConstraints(null)");
        logger.log(Level.FINE,"");

        newHandler = 
            (FakeBasicInvocationHandler)handler.setClientConstraints(null);
        assertion(newHandler.getClientConstraints() == null);
        assertion(newHandler.getServerConstraints() == null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "setClientConstraints(MethodConstraints)");
        logger.log(Level.FINE,"");

        newHandler = 
            (FakeBasicInvocationHandler)handler.setClientConstraints(
                clientConstraints);
        assertion(newHandler.getClientConstraints() == clientConstraints);
        assertion(newHandler.getServerConstraints() == null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "setClientConstraints(MethodConstraints) throws exception");
        logger.log(Level.FINE,"");

        try {
            handler = new FakeBadConstructorBasicInvocationHandler();
            handler.setClientConstraints(clientConstraints);
            assertion(false);
        } catch (UndeclaredThrowableException ignore) {
        }

        try {
            handler = new FakeConstructorExceptionBasicInvocationHandler();
            handler.setClientConstraints(clientConstraints);
            assertion(false);
        } catch (UndeclaredThrowableException ignore) {
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

