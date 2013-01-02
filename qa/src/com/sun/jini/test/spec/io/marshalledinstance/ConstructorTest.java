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
package com.sun.jini.test.spec.io.marshalledinstance;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.io.MarshalledInstance;

import com.sun.jini.test.spec.io.util.FakeObject;

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.logging.Level;
import java.util.ArrayList;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of MarshalledInstance
 *   during normal and exceptional constructor calls.
 *
 * Test Cases
 *   Test cases are defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObject
 *          -implements Serializable
 *          -custom readObject method throws AssertionError
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct MarshalledInstance((Object)null)
 *     2) assert no exception is thrown
 *     3) construct MarshalledInstance((MarshalledObject)null)
 *     4) assert NullPointerException is thrown
 *     5) construct MarshalledInstance(new Object())
 *     6) assert IOException is thrown
 *     7) construct MarshalledInstance(new FakeObject())
 *     8) assert no exception is thrown
 *     9) construct MarshalledInstance(new FakeObject(),null)
 *    10) assert NullPointerException is thrown
 *    11) construct MarshalledInstance(new Object(),context)
 *    12) assert IOException is thrown
 *    13) construct MarshalledInstance(new FakeObject(),context)
 *    14) assert no exception is thrown
 * </pre>
 */
public class ConstructorTest extends QATestEnvironment implements Test {

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        MarshalledInstance instance;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "MarshalledInstance((Object)null)");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance((Object)null);
        } catch (Throwable caught) {
            assertion(false,caught.toString());
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "MarshalledInstance((MarshalledObject)null)");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance((MarshalledObject)null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "MarshalledInstance(new Object())");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance(new Object());
            assertion(false);
        } catch (IOException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "MarshalledInstance(new FakeObject())");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance(
                new FakeObject(new AssertionError()));
        } catch (Throwable caught) {
            assertion(false,caught.toString());
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "MarshalledInstance(new FakeObject(),null)");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance(
                new FakeObject(new AssertionError()),null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 6: "
            + "MarshalledInstance(new Object(),context)");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance(
                new Object(),new ArrayList());
            assertion(false);
        } catch (IOException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 7: "
            + "MarshalledInstance(new FakeObject(),context)");
        logger.log(Level.FINE,"");

        try {
            instance = new MarshalledInstance(
                new FakeObject(new AssertionError()),new ArrayList());
        } catch (Throwable caught) {
            assertion(false,caught.toString());
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

