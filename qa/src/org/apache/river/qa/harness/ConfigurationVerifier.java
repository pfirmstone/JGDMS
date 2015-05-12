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
 * A verifier used to determine whether a test is capable of being
 * run in a particular environment. Tests which require a particular
 * execution environment, such as running in shared VM mode, may
 * provide implementations of this interface. A test which fails
 * to verify will not be considered to have failed.
 * <p>
 * The configuration is searched for the key 
 * <code>org.apache.river.qa.harness.verifier</code>. If the key is found, its
 * value is interpreted as the name of a class to instantiate via
 * a no-arg constructor, implementing this interface. The value of the
 * <code>canRun</code> method determines whether the test can be run.
 * If the key does not exist, then it is assumed that the test can be
 * run in all configurations.
 */
public interface ConfigurationVerifier {

    /**
     * Return an indication of whether the test should be run.
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>true</code> if the test can be run in this configuration
     */
    public boolean canRun(TestDescription td, QAConfig config);
}


















