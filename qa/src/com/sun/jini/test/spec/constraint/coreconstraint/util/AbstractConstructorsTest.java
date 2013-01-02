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
package com.sun.jini.test.spec.constraint.coreconstraint.util;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;

// java.util
import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;


/**
 * Abstract class to test constructors.
 */
abstract public class AbstractConstructorsTest extends QATestEnvironment implements Test {
    protected QAConfig config;

    /**
     * Execution of a Test Case. Actions see in the particular test description.
     */
    abstract public void runTestCase(Object testCase) throws TestException;

    /**
     * Auxiliary method to obtain the array of the Objects that describe
     * Test Cases.
     */
    abstract public Object[] getTestCases();

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        config = getConfig();
        Object[] tc = getTestCases();

        for (int i = 0; i < tc.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ Test Case #" + (i + (int) 1));
            runTestCase(tc[i]);
        }
        logger.log(Level.INFO, "======================================");
        return;
    }
}
