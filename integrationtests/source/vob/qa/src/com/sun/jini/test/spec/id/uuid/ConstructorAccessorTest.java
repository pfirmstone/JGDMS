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

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.id.Uuid;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectInput;
import java.util.logging.Level;

import com.sun.jini.test.spec.id.util.FakeUuid;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of Uuid
 *   during normal and exceptional constructor calls.
 * 
 *   This test verifies the behavior of the
 *   Uuid.getMostSignificantBits and Uuid.getLeastSignificantBigs methods.
 * 
 * Test Cases
 *   Test cases are defined by the Actions section below.
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeUuidExternalizable
 *          -extends Uuid and passes it's constructor args to Uuid
 *          -implements Externalizable
 *     2) FakeUuid
 *          -extends Uuid and passes it's constructor args to Uuid
 * 
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeUuidExternalizable
 *     2) assert SecurityException is thrown
 *     3) construct a FakeUuid
 *     4) assert getMostSignificantBits and getLeastSignificantBits
 *        return the correct values
 * </pre>
 */
public class ConstructorAccessorTest extends QATest {

    class FakeUuidExternalizable extends Uuid implements Externalizable {
        public FakeUuidExternalizable(long bits0, long bits1) {
            super(bits0,bits1);
        }
        public void writeExternal(ObjectOutput o) throws IOException { }
        public void readExternal(ObjectInput i) 
            throws IOException, ClassNotFoundException { }
    }

    public void setup(QAConfig sysConfig) throws Exception {
    }

    public void run() throws Exception {
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: Externalizable Uuid subclass");
        logger.log(Level.FINE,"");

        try {
            new FakeUuidExternalizable(0,0);
            assertion(false);
        } catch (SecurityException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "accessor methods return constructor args");
        logger.log(Level.FINE,"");

        Uuid uuid = new FakeUuid(Long.MAX_VALUE,Long.MIN_VALUE);
        assertion(uuid.getMostSignificantBits() == Long.MAX_VALUE);
        assertion(uuid.getLeastSignificantBits() == Long.MIN_VALUE);

        return;
    }

    public void tearDown() {
    }

}

