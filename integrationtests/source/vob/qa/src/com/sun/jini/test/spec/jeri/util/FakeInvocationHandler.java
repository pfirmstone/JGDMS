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
package com.sun.jini.test.spec.jeri.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * A fake <code>InvocationHandler</code> that can be configured to throw an
 * exception or verify method parameters and return a result.
 */
public class FakeInvocationHandler implements InvocationHandler {

    Logger logger;
    private Throwable invokeException;
    private Method expectedMethod;
    private Object[] expectedArgs;
    private Object returnObject;

    /**
     * Constructs a FakeInvocationHandler.
     */
    public FakeInvocationHandler() {
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
    }

    /**
     * Constructs a FakeInvocationHandler.
     *
     * @param ie the exception that the invoke method must throw if not null
     * @param em the Method that the invoke method should be called with;
     *        a check is made to ensure that they are .equals if not null
     * @param ea the Object array that the invoke method should be called with;
     *        a check is made to ensure that each element is .equals if not null
     * @param ro the return value that the invoke method must return
     *        if <code>ie</code> is not null
     */
    public FakeInvocationHandler(Throwable ie, Method em, Object[] ea,
        Object ro) 
    {
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        init(ie,em,ea,ro);
    }

    /**
     * Initializes (or re-initializes) a FakeInvocationHandler.
     *
     * @param ie the exception that the invoke method must throw if not null
     * @param em the Method that the invoke method should be called with;
     *        a check is made to ensure that they are .equals if not null
     * @param ea the Object array that the invoke method should be called with;
     *        a check is made to ensure that each element is .equals if not null
     * @param ro the return value that the invoke method must return
     *        if <code>ie</code> is not null
     */
    public void init(Throwable ie, Method em, Object[] ea, Object ro) {
        logger.entering(getClass().getName(),"init(invokeException:" + ie
            + ",expectedMethod:" + em + ",expectedArgs:" + ea 
            + ",returnObject:" + ro + ")");
        invokeException = ie;
        expectedMethod = em;
        expectedArgs = ea;
        returnObject = ro;
    }

    /**
     * Implementation of interface method.  Throws an exception or
     * verifies that method parameters are correct.
     *
     * @return ro if <code>ie</code> is not null
     * @throw ie if not null
     */
    public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable
    {
        logger.entering(getClass().getName(),"invoke("
            + "method:" + method + ",args:" + args + ")");

	if (method.getName().equals("toString")) {
            return getClass().getName();
        }

        if (invokeException != null) {
            throw invokeException;
        }

        if (expectedMethod != null && (! expectedMethod.equals(method))) {
            throw new AssertionError("Incorrect method invoked: " + method);
        }

        if (expectedArgs != null) {
            for (int i = 0; i < expectedArgs.length; i++) {
                if (expectedArgs[i] == null) {
                    if (args[i] != null) {
                        throw new AssertionError(
                            "Arg should be null: " + args[i]);
                    }
                } else if (expectedArgs[i].getClass().isArray()) {
                    if (! Arrays.equals((Object[])args[i],
                                     (Object[])expectedArgs[i]))
                    {
                        throw new AssertionError("Incorrect array argument");
                    }
                } else if (! expectedArgs[i].equals(args[i])) {
                    throw new AssertionError("Incorrect argument");
                }
            }
        }
        return returnObject;
    }
}
