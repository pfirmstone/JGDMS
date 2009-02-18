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

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.io.MarshalledInstance;

import com.sun.jini.test.spec.io.util.FakeObject;

import java.io.File;
import java.rmi.MarshalledObject;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of MarshalledInstance
 *   convertToMarshalledObjectTest method calls.
 * 
 * Test Cases
 *   This test iterates over a set of pairs, where each pair contains
 *   an object and a boolean.  Each pair
 *   denotes one test case and is defined by the variables:
 *      Object  marshalObject
 *      boolean unmarshal
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObject
 *          -implements Serializable
 *          -custom readObject method throws AssertionError
 * 
 * Actions
 *   For each test case the test performs the following steps:
 *     1) if unmarshal is true
 *          1) construct MarshalledInstance((Object)marshalObject)
 *          2) call convertToMarshalledObject() and assert returned
 *             MarshalledObject get method returns an equivalent marshalObject
 *     2) construct MarshalledObject(marshalObject)
 *     3) construct MarshalledInstance, passing in constructed MarshalledObject
 *     4) call convertToMarshalledObject() 
 *     5) assert returned MarshalledObject is equivalent to 
 *        passed in MarshalledObject
 *     6) if unmarshal is true
 *          1) assert returned MarshalledObject's get method returns an object
 *             equivalent to marshalObject
 * </pre>
 */
public class ConvertToMarshalledObjectTest extends QATest {

    // test cases
    Object[][] cases = {
        // marshalObject, unmarshal
        {null,                                 Boolean.TRUE},
        {new File("foo"),                      Boolean.TRUE},
        {new FakeObject(new AssertionError()), Boolean.FALSE}
    };

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        MarshalledInstance mi;
        Object marshalObject;
        Object unmarshalObject;
        boolean unmarshal;

        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            marshalObject = cases[i][0]; 
            unmarshal = ((Boolean)cases[i][1]).booleanValue();
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "input object:" + marshalObject
                + ",unmarshal:" + unmarshal);
            logger.log(Level.FINE,"");

            if (unmarshal) {
                mi = new MarshalledInstance((Object)marshalObject);
                unmarshalObject = mi.convertToMarshalledObject().get();

                if (marshalObject == null) {
                    assertion(unmarshalObject == null);
                } else {
                    assertion(marshalObject.equals(unmarshalObject));
                }
            }

            MarshalledObject mo_in = new MarshalledObject(marshalObject);
            mi = new MarshalledInstance(mo_in);
            MarshalledObject mo_out = mi.convertToMarshalledObject();
            assertion(mo_in.equals(mo_out));

            if (unmarshal) {
                unmarshalObject = mo_out.get();
                if (marshalObject == null) {
                    assertion(unmarshalObject == null);
                } else {
                    assertion(marshalObject.equals(unmarshalObject));
                }
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

