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

package com.sun.jini.test.impl.start;

import java.util.logging.Level;

import com.sun.jini.test.share.BaseQATest;

import java.util.ArrayList;
import java.util.Properties;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.Test;

/**
 * This class is an abstract class that acts as the base class which
 * most, if not all, tests of the <code>ServiceStarter</code> utility
 * class should extend.
 * 
 * This class provides an implementation of the <code>construct</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 *
 * Any test class that extends this class is required to implement the 
 * <code>run</code> method which defines the actual functions that must
 * be executed in order to verify the assertions addressed by that test.
 * 
 */
abstract public class AbstractBaseTest extends BaseQATest implements Test {

    /** Performs actions necessary to prepare for execution of the 
     *  current test
     */
    public Test construct(QAConfig  config) throws Exception {
        delayLookupStart = true;
        super.construct(config);
        return this;
    }
}


