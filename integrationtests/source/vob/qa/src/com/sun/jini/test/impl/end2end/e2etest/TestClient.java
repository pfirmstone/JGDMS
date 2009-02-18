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

package com.sun.jini.test.impl.end2end.e2etest;

import javax.security.auth.Subject;
import net.jini.core.constraint.InvocationConstraints;

/**
 * An interface which advertises client methods of interest to
 * other classes, primarily implementations of <code>UserInterface</code>
 */
public interface TestClient {

    /**
     * Register a <code>UserInterface</code>. Only one user interface
     * may be registered at a time.
     *
     * @param ui the <code>UserInterface</code> to register, which may be null.
     */
    public void registerUserInterface(UserInterface ui);

    /**
     * Request execution of tests, ignored if tests are currently executing.
     */
    public void executeTests();

    /**
     * Get the number of the currently executing test
     *
     * @return the test number
     */
    public int getTestNumber();

    /**
     * Get the total number of tests to be run.
     *
     * @return the total number of tests in the test suite
     */
    public int getTestTotal();

    /**
     * Get the logger for this client.
     *
     * @return the logger instance
     */
    public Logger getLogger();


    /**
     * Log a failure, using the given message
     *
     * @param message the failure message
     */
    public void logFailure(String message);

    /**
     * Log a failure, using the given message and exception in the log output.
     *
     * @param message the failure message
     * @param t the exception causing the failure
     */
    public void logFailure(String message, Throwable t);

    /**
     * Get the number of test failures encountered thus far.
     *
     * @return the failure count
     */
    public int getFailureCount();

    /**
    * Obtain the service proxy without constraints applied
    *
    * @return the unconstrained service proxy
    */
    public SmartInterface getUnconstrainedProxy();

    /**
     * Get the combined constraints which are associated with the
     * remote call in progress. If no remote call is in progress,
     * this method may return <code>null</code>.
     */
    public InvocationConstraints getCombinedConstraints();
}
