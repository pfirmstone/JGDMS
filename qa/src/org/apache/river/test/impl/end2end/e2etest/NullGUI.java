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

package org.apache.river.test.impl.end2end.e2etest;

import net.jini.core.constraint.InvocationConstraints;
import java.util.Collection;
import java.util.Iterator;
import javax.security.auth.Subject;

/**
 * An implementation of <code>UserInterface</code> for the end-to-end
 * test which does nothing. This allows the GUI to be conditionally
 * excluded from the test without requiring tests for GUI existance
 * everywhere.
 */
class NullGUI implements UserInterface {

    /** if true, abort test on first failure */
    private boolean abortOnFail =
            System.getProperty("end2end.abortOnFail") != null;

    NullGUI(TestCoordinator coordinator) {
    Collection tests = coordinator.getTestClients();
    for (Iterator it = tests.iterator(); it.hasNext(); ) {
        TestClient client = (TestClient) it.next();
            client.registerUserInterface(this);
    }
    }

    /* inherit javadoc */
    public void setTestCount(int testNum, int totalTests) {
    }

    /* inherit javadoc */
    public void setTestSuite(CipherSuite suite) {
    }

    /* inherit javadoc */
    public void setTestName(String testName) {
    }

    /* inherit javadoc */
    public void setClientContextualConstraints(InvocationConstraints constraints)
    {
    }

    /* inherit javadoc */
    public void setClientProxyConstraints(InvocationConstraints constraints) {
    }

    /* inherit javadoc */
    public void setServerConstraints(InvocationConstraints constraints) {
    }

    /* inherit javadoc */
    public void setCombinedConstraints(InvocationConstraints constraints) {
    }

    /* inherit javadoc */
    public void setClientSubject(Subject subject) {
    }

    /* inherit javadoc */
    public void setServerSubject(Subject subject) {
    }

    /* inherit javadoc */
    public void setEndpointCount(int count) {
    }

    /* inherit javadoc */
    public void setFailureCount(int count) {
    }

    /* inherit javadoc */
    public void setPreCallFreeMemory(long memory) {
    }

    /* inherit javadoc */
    public void setPostCallFreeMemory(long memory) {
    }

    /* inherit javadoc */
    public void setCallStatus(String message) {
    }

    /* inherit javadoc */
    public void setCallInProgress() {
    }

    /* inherit javadoc */
    public void setCallComplete() {
    }

    /* inherit javadoc */
    public boolean stopAfterCall() {
        return false;
    }

    /* inherit javadoc */
    public boolean stopAfterFailure() {
        if (abortOnFail) {
        throw new TestException("Failure detected - aborting run",null);
        }
        return false;
    }

    /* inherit javadoc */
    public boolean forceFailure() {
        return false;
    }

    /* inherit javadoc */
    public void showFailure(String message) {
    }

}
