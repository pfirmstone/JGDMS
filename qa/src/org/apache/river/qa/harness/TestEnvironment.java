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
 * This interface must implemented by all tests supported by the test
 * harness.  The following sequence of events is performed for each
 * test:
 * <p><ul>
 *   <li> the test environment class is instantiated and it's no-arg constructor called.
 *        The constructor will typically perform minimal initialization, 
 *        since the test environment does not have access to the test description
 *   <li> the construct method is called, passing the config object derived from the test description.
 *        This provides an opportunity for performing any test construction
 *        that relies on accessing configuration parameters.
 *   <li> the run method is called to run the test returned by the construct method.
 *   <li> the teardown method is called to clean up state or services
 *        created by the test. This method is called
 *        even if <code>construct</code> or <code>run</code> throws an exception.
 * </ul> 
 */
public interface TestEnvironment {

    /**
     * Constructs a test and its environment.  
     * If an exception is thrown, the <code>tearDown</code> method will 
     * still be called.
     *
     * @param config the test properties
     * @return Test to run.
     * @throws Exception if construct fails for any reason
     */
    Test construct(QAConfig config) throws Exception;

    /**
     * Tears down any construct that was needed to run the test.  This method is
     * called even if construct throws an exception, and so must be designed to
     * be tolerant of test state that is not completely initialized.
     */
    void tearDown();
    
}
