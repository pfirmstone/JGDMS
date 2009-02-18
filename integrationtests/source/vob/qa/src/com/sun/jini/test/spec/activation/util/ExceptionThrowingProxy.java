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
package com.sun.jini.test.spec.activation.util;
import java.lang.IllegalArgumentException;
import java.lang.reflect.InvocationHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.Remote;
import com.sun.jini.test.spec.activation.util.ExceptionThrowingInterface;


/**
 * An implementation of the {@link ExceptionThrowingInterface}
 * class.
 */
public class ExceptionThrowingProxy implements ExceptionThrowingInterface {
    private Logger logger;
    private Throwable testedException;

    public ExceptionThrowingProxy(Logger logger) {
        logger.log(Level.FINEST, "OK");
        this.logger = logger;
    }

    public void exceptionForThrow(Throwable testedException) {
        logger.log(Level.FINEST, "testedException="
                + testedException.toString());
        this.testedException = testedException;
    };

    public void throwsException() throws Throwable {
        logger.log(Level.FINEST, "testedException="
                + testedException.toString());
        throw testedException;
    };
}
