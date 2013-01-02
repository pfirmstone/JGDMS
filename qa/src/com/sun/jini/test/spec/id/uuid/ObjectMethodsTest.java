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
package com.sun.jini.test.spec.id.uuid;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

import com.sun.jini.test.spec.id.util.FakeUuid;

import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the Uuid
 *   equals, hashCode, and toString methods.
 * 
 * Test Cases
 *   Test cases are defined by the Actions section below.
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeUuid
 *          -extends Uuid and passes it's constructor args to Uuid
 * 
 * Actions
 *   The test performs the following steps:
 *     1) construct one Uuid instance from FakeUuid
 *        and create a second Uuid using UuidFactory.create, passing in the
 *        same args to each
 *     2) verify instances are .equals to themselves (reflexive)
 *        and .equals to each other (symmetric)
 *     3) verify instances .hashCode methods return the same value
 *     4) verify instances .toString methods return the same, non-null
 *        string representation which is properly formatted
 *     5) construct another FakeUuid using different args than before
 *        and verify it is not .equals to the other two Uuid instances
 *        and verify it's .toString method returns a differnt, non-null
 *        string representation
 * </pre>
 */
public class ObjectMethodsTest extends QATestEnvironment implements Test {

    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    public void run() throws Exception {
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "normal equal hashCode, equals, toString method calls");
        logger.log(Level.FINE,"");

        // construct two instances of Uuid
        Uuid uuid1 = UuidFactory.create(13,11);
        Uuid uuid2 = new FakeUuid(13,11);

        // verify Uuid equals, hashCode, and toString methods
        assertion(uuid1.equals(uuid2));
        assertion(uuid2.equals(uuid1));
        assertion(uuid1.equals(uuid1));
        assertion(uuid2.equals(uuid2));
        assertion(uuid1.hashCode() == uuid2.hashCode());
        assertion(uuid1.toString() != null);
        assertion(uuid1.toString().equals(uuid2.toString()));
        assertion(
            uuid1.toString().equals("00000000-0000-000d-0000-00000000000b"),
            "uuid1.toString(): " + uuid1);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "normal non-equal equals and toString method calls");
        logger.log(Level.FINE,"");

        // construct a third instance of Uuid
        Uuid uuid3 = new FakeUuid(11,13); //reversed bits

        // verify Uuid equals, hashCode, and toString methods
        assertion(! uuid1.equals(uuid3));
        assertion(! uuid3.equals(uuid1));
        assertion(! uuid1.toString().equals(uuid3.toString()));

        return;
    }

    public void tearDown() {
    }

}

