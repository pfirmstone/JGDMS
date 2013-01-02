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

import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of UuidFactory
 *   during normal calls to the generate and create methods.
 * 
 * Test Cases
 *   This test iterates over a set of strings, then over a set of
 *   long value pairs.  The set of strings must not contain a valid
 *   representation of a Uuid. Each string or pair denotes one
 *   test case and is defined by the variables:
 *      String uuidString
 *      long mostSig, long leastSig
 * 
 * Infrastructure
 *   This test requires no special infrastructure.
 * 
 * Actions
 *   The test performs the following steps:
 *     1) call UuidFactory.generate and check the resulting have correct
 *        variant and version fields
 *     2) call UuidFactory.generate again and check the resulting Uuid is
 *        different from the first
 *     3) for each mostSig,leastSig pair, performs the following steps:
 *          1) call UuidFactory.create(mostSig,leastSig)
 *          2) call getMostSignificantBits and getLeastSignificantBits
 *             on the returned Uuid and verify they return the correct values
 *          3) call toString on the returned Uuid and pass it to
 *             UuidFactory.create
 *          4) call getMostSignificantBits and getLeastSignificantBits
 *             on the returned Uuid and verify they return the correct values
 *     4) for each uuidString, call UuidFactory.create(uuidString)
 *        and assert the correct exception is thrown
 * </pre>
 */
public class CreateGenerateTest extends QATestEnvironment implements Test {

    long[][] cases1 = {
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

    String[] cases2 = {
        null,
        "",
        "foobar",
        "------------------------------------",
        "01234567-89ab-cdef-0123-456789ABCDEG",
        "-1234567-89ab-cdef-0123-456789ABCDEF",
        "01234567--9ab-cdef-0123-456789ABCDEF",
        "01234567-89ab--def-0123-456789ABCDEF",
        "01234567-89ab-cdef--123-456789ABCDEF",
        "01234567-89ab-cdef-0123--56789ABCDEF"
    };

    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    public void run() throws Exception {
        long mostSig;
        long leastSig;
        int counter = 1;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) + ": generate()");
        logger.log(Level.FINE,"");

        Uuid uuid1 = UuidFactory.generate();
        mostSig = uuid1.getMostSignificantBits();
        leastSig = uuid1.getLeastSignificantBits();
        assertion((leastSig >>> 62) == 0x2);  // variant is 2
        assertion(((mostSig >>> 12 ) & 0x0000000F) == 0x4); // version is 4

        Uuid uuid2 = UuidFactory.generate();
        assertion(uuid1 != uuid2);

        for (int i = 0; i < cases1.length; i++) {
            logger.log(Level.FINE,"=================================");
            mostSig = cases1[i][0];
            leastSig = cases1[i][1];
            logger.log(Level.FINE,"test case " + (counter++) + ": create(" 
                + mostSig + "," + leastSig + ")");
            logger.log(Level.FINE,"");

            // call method and verify the proper result
            uuid1 = UuidFactory.create(mostSig,leastSig);
            assertion(mostSig == uuid1.getMostSignificantBits());
            assertion(leastSig == uuid1.getLeastSignificantBits());

            uuid2 = UuidFactory.create(uuid1.toString());
            assertion(mostSig == uuid2.getMostSignificantBits());
            assertion(leastSig == uuid2.getLeastSignificantBits());
        }

        for (int j = 0; j < cases2.length; j++) {
            logger.log(Level.FINE,"=================================");
            String uuidString = cases2[j];
            logger.log(Level.FINE,"test case " + (counter++) + ": create(" 
                + uuidString + ")");
            logger.log(Level.FINE,"");

            // call method and verify the proper result
            try {
                uuid2 = UuidFactory.create(uuidString);
                assertion(false);
            } catch (NullPointerException npe) {
                assertion(uuidString == null);
            } catch (IllegalArgumentException iae) {
                // normal case
            }
        }

        return;
    }

    public void tearDown() {
    }

}

