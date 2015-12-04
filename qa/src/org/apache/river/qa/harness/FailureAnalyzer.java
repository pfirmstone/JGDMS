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
 * An analyzer used to determine the failure type to associate
 * with an exception. Failure analyzers may be explicitly registered
 * by calling the <code>QAConfig addFailureAnalyzer</code> method. 
 * <code>FailureAnalyzer</code> class names may also be specified through
 * the test property named <code>"org.apache.river.qa.harness.analyzers"</code>.
 * Multiple analyzers may be listed, separated by commas or white space.
 * All analyzer classes named by this property must support
 * a no-arg constructor.
 */
public interface FailureAnalyzer {

    /**
     * Analyze the exception thrown by a test. This method will be
     * called with an argument of <code>null</code> if the test returns normally.
     * If inspection of the argument indicates that the test passed,
     * then this method should return Test.PASSED. If inspection of the
     * argument indicates that the test failed, then this method should
     * return Test.TEST (if the component under test failed) or Test.ENV (if
     * an environmental problem or configuration error caused the failure). 
     * Otherwise this method should return Test.UNKNOWN.
     *
     * @param e the exception to inspect, or <code>null</code>
     * @return the failure type
     */
    public int analyzeFailure(Throwable e);
}
