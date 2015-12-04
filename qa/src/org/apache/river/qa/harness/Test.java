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
package org.apache.river.qa.harness;

/**
 *
 * @author peter
 */
public interface Test {
    /**
     * Failure type. Indicates that a test failed because of a recognized
     * environmental problem. These will typically be due to
     * intermittent or recurring configuration dependent failures
     * which have been determined to result from problems external
     * to the product.
     */
    int ENV = 1;
    /**
     * Failure type. Indicates that a test failed but the reason for
     * failure may be external to the product being tested.
     */
    int INDEF = 2;
    /**
     * Failure type indicating no failure occured. This is a special
     * value which should be returned by a FailureAnalyzer to indicate
     * that the exception represents a passing condition for the test
     */
    int PASSED = 6;
    /**
     * Failure type indicating that the test should be rerun.
     */
    int RERUN = 4;
    /**
     * Failure type indicating that the test should not be run.
     */
    int SKIP = 3;
    /**
     * Failure type. Indicates that a test failed because the product failed,
     * or an unrecognized environmental failure occured.
     */
    int TEST = 0;
    /**
     * Failure type (for analyzers) indicating failure not recognized
     */
    int UNKNOWN = 5;

    /**
     * Execute the body of the test.
     *
     * @throws Exception if the test fails for any reason
     */
    void run() throws Exception;
    
}
