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

import java.util.Collection;
import java.util.Set;

/**
 * Interface implemented by the object which drives the test
 */
public interface TestCoordinator {

    /** exit code for successful test run */
    public static final int SUCCESS = 0;

    /** exit code for a test failure detected */
    public static final int FAILURE = 1;

    /** exit code for a timeout detected on a remote call */
    public static final int TIMEOUT = 2;

    /**
     * Get the number of client threads being run
     *
     * @return the number of client threads
     */
    public int getThreadCount();

    /**
     * Gets the set of test clients which are running tests. The number
     * of elements in the collection should be the same as the value
     * returned by <code>getThreadCount</code>
     *
     * @return the collection test clients created for this test run
     */
    public Collection getTestClients();

    /**
     * Get the set of tests to run. This is a set of Strings which may
     * be test numbers or method names. If empty or null, all tests
     * are to be run
     *
     * @return the collection of tests to run
     */
    public Set getTests();

    /**
     * Abort the current test run. This may be called at the end of a
     * successful run to force an exit of the VM, or to prematurely
     * terminate the run due to a failure or timeout.
     *
     * @param failureCode an integer value indicating the success/failure
     * of the run.
     */
    public void abortRun(int failureCode);

    /**
     * The the default <code>InstanceCarrier</code> for the
     * <code>TestClient</code>. This is provided to support disabling
     * the bridge, and is only meaningful for a single threaded run.
     */
    public InstanceCarrier getDefaultInstanceCarrier();
}
