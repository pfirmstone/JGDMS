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

import net.jini.core.constraint.InvocationConstraints;
import javax.security.auth.Subject;

/**
 * An interface which defines the operations which must be supported
 * by a user interface to the test.
 */
public interface UserInterface {

    /**
     * Set test progress statistics.
     *
     * @param testNum the number of the current test
     * @param testTotal the total number of tests to be run
     */
    public void setTestCount(int testNum, int testTotal);

    /**
     * Set the name of the ciphersuite
     *
     * @param suite the <code>CipherSuite</code>
     */
    public void setTestSuite(CipherSuite suite);

    /**
     * Set the method name of the remote call being tested
     *
     * @param testName the method name
     */
    public void setTestName(String testName);

    /**
     * Set the contextual constraints imposed by the client.
     *
     * @param constraints the contextual constraints
     */
    public void setClientContextualConstraints(InvocationConstraints constraints);

    /**
     * Set the constraints placed on the proxy by the client.
     *
     * @param constraints the client proxy constraints
     */
    public void setClientProxyConstraints(InvocationConstraints constraints);

    /**
     * Set the constraints imposed on the remote call by the server
     *
     * @param constraints the server constraints
     */
    public void setServerConstraints(InvocationConstraints constraints);

    /**
     * Set the combined constraints actually being used for the Remote call
     *
     * @param constraints the combined constraints for the call
     */
    public void setCombinedConstraints(InvocationConstraints constraints);

    /**
     * Set the authenticated client subject being used for the remote call
     *
     * @param subject the authenticated client subject
     */
    public void setClientSubject(Subject subject);

    /**
     * Set the authenticated server subject being used for the remote call
     *
     * @param subject the authenticated server subject
     */
    public void setServerSubject(Subject subject);

    /**
     * Set the number of endpoints found in a client proxy
     *
     * @param count the number of endpoints
     */
    public void setEndpointCount(int count);

    /**
     * Set the free memory which existed immediately before the remote call
     *
     * @param memory the amount of free memory
     */
    public void setPreCallFreeMemory(long memory);

    /**
     * Set the free memory which existed immediately after the remote call
     *
     * @param memory the amount of free memory
     */
    public void setPostCallFreeMemory(long memory);

    /**
     * Set the number of failures detected for this client
     *
     * @param the number of failures
     */
    public void setFailureCount(int count);

    /**
     * Set the return status of the remote call
     *
     * @param message a String describing the return status of the remote call
     */
    public void setCallStatus(String message);

    /**
     * Tell the user interface that a remote call is in progress
     */
    public void setCallInProgress();

    /**
     * Tell the user interface that a remote call has completed
     */
    public void setCallComplete();

    /**
     * Determine whether testing should be paused when a method call completes.
     *
     * @return true if testing should pause after a method call completes
     */
     /*
     * XXX It is assumed that the client provides a method for starting after
     * a pause. The TestClient interface provides the executeTests method
     * for this purpose. Should the relationship between these two methods
     * be made more apparent by somehow placing them both in the same interface?
     */
    public boolean stopAfterCall();

    /**
     * Determine whether a failure should be forced when a method call
     * completes. Primarily intended for debugging of failure handling
     *
     * @return true of a failure should be forced
     */
    public boolean forceFailure();

    /**
     * Display a failure indication to the user.
     *
     * @param message a String containing the complete failure message
     *        to be displayed to the user
     */
    public void showFailure(String message);

    /**
     * Determine whether testing should pause after a failure is detected.
     * The <code>UserInterface</code> implementation is free to assume
     * that a failure has occured when this method is called, and may
     * force test termination rather than return. It is expected that
     * 'batch runs' might implement this behavior.
     *
     * @return true if testing should pause on failure
     */
     /*
     * XXX It is assumed that the client provides a method for starting after
     * a pause. The TestClient interface provides the executeTests method
     * for this purpose. Should the relationship between these two methods
     * be made more apparent by somehow placing them both in the same interface?
     */
    public boolean stopAfterFailure();
}
