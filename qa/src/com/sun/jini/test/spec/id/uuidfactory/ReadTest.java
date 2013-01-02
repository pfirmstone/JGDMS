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
package com.sun.jini.test.spec.id.uuidfactory;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the UuidFactory.read method.
 * 
 * Test Cases
 *   This test iterates over a set of long value pairs.  Each pair
 *   denotes one test case and is defined by the variables:
 *      long mostSig
 *      long leastSig
 * 
 * Infrastructure
 *   This test requires no special infrastructure.
 * 
 * Actions
 *   The test performs the following steps:
 *     1) call the UuidFactory.read method with null
 *        and assert a NullPointerException is thrown
 *     2) for each test case, performs the following steps:
 *          1) copy mostSig and leastSig to a byte array in the appropriate
 *             format for UuidFactory.read
 *          2) construct a ByteArrayInputStream with the byte array
 *             and pass it to UuidFactory.read
 *          3) verify the returned Uuid contains the correct values
 * </pre>
 */
public class ReadTest extends QATestEnvironment implements Test {

    long[][] cases = {
        { 0, 0 },
        { 0, 1 },
        { 1, 0 },
        { 0, -1 },
        { -1, 0 },
        { 1349, 2247 },
        { 1349, -2247 },
        { Long.MAX_VALUE, Long.MAX_VALUE },
        { Long.MAX_VALUE, Long.MIN_VALUE },
        { Long.MIN_VALUE, Long.MIN_VALUE },
        { Long.MIN_VALUE, Long.MAX_VALUE }
    };

    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    public void run() throws Exception {
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: read(null)");
        logger.log(Level.FINE,"");

        try {
            UuidFactory.read(null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            long mostSig = cases[i][0];
            long leastSig = cases[i][1];
            logger.log(Level.FINE,"test case " + (i+2) + ": read(" 
                + mostSig + "," + leastSig + ")");
            logger.log(Level.FINE,"");

            // call method and verify the proper result
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeLong(mostSig);
            dos.writeLong(leastSig);

            Uuid uuid = UuidFactory.read(
                new ByteArrayInputStream(baos.toByteArray()));

            assertion(mostSig == uuid.getMostSignificantBits());
            assertion(leastSig == uuid.getLeastSignificantBits());
        }

        return;
    }

    public void tearDown() {
    }

}

