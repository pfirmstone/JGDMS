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
package com.sun.jini.qa.harness;

/**
 * This interface must implemented by all tests supported by the test
 * harness.  The following sequence of events is performed for each
 * test:
 * <p><ul>
 *   <li> the test class is instantiated and it's no-arg constructor called.
 *        The constructor will typically perform minimal initialization, 
 *        since the test does not have access to the test environment
 *   <li> the setup method is called, passing the config object.
 *        This provides an opportunity for performing any test setup
 *        that relies on accessing configuration parameters.
 *   <li> the run method is called to run the test
 *   <li> the teardown method is called to clean up state or services
 *        created by the test. This method is called
 *        even if <code>setup</code> or <code>run</code> throws an exception.
 * </ul> 
 */
public interface Test {

    /**
     * Failure type. Indicates that a test failed because the product failed,
     * or an unrecognized environmental failure occured. 
     */
    public final static int TEST = 0;
    
    /**
     * Failure type. Indicates that a test failed because of a recognized
     * environmental problem. These will typically be due to 
     * intermittent or recurring configuration dependent failures
     * which have been determined to result from problems external
     * to the product.
     */
    public final static int ENV = 1;
    
    /**
     * Failure type. Indicates that a test failed but the reason for 
     * failure may be external to the product being tested.
     */
    public final static int INDEF = 2;

    /** Failure type indicating that the test should not be run. */
    public final static int SKIP = 3;

    /** Failure type indicating that the test should be rerun. */
    public final static int RERUN = 4;

    /** Failure type (for analyzers) indicating failure not recognized */
    public final static int UNKNOWN = 5;
 
    /**
     * Failure type indicating no failure occured. This is a special
     * value which should be returned by a FailureAnalyzer to indicate
     * that the exception represents a passing condition for the test
     */
    public final static int PASSED = 6;

    /**
     * Performs setup for this test.  If an exception is thrown, the
     * <code>tearDown</code> method will still be called.
     *
     * @param config the test properties
     * @throws Exception if setup fails for any reason
     */
    public void setup(QAConfig config) throws Exception;

    /**
     * Execute the body of the test. 
     *
     * @throws Exception if the test fails for any reason
     */
    public void run() throws Exception;

    /**
     * Tears down any setup that was needed to run the test.  This method is
     * called even if setup throws an exception, and so must be designed to
     * be tolerant of test state that is not completely initialized.
     */
    public void tearDown();
}
