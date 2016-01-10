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
package org.apache.river.test.spec.id.uuid;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.id.util.FakeUuid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the Uuid.write method.
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
 *     1) create a Uuid using UuidFactory.create(mostSig,leastSig)
 *     2) call the write method with null
 *        and assert a NullPointerException is thrown
 *     3) for each test case, performs the following steps:
 *          1) construct a ByteArrayOutputStream and pass it to the write
 *             method of the constructed Uuid
 *          2) verify the write method correctly writes mostSig and leastSig
 *             to the ByteArrayOutputStream
 * </pre>
 */
public class WriteTest extends QATestEnvironment implements Test {

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
        Uuid uuid = UuidFactory.create(0,0);
        int counter = 1;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) + ": write(null)");
        logger.log(Level.FINE,"");

        try {
            uuid.write((OutputStream) null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            long mostSig = cases[i][0];
            long leastSig = cases[i][1];
            logger.log(Level.FINE,"test case " + (counter++) + ": write(" 
                + mostSig + "," + leastSig + ")");
            logger.log(Level.FINE,"");

            uuid = UuidFactory.create(mostSig,leastSig);

            // call method and verify the proper result
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            uuid.write(baos);

            DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(baos.toByteArray()));
            assertion(mostSig == dis.readLong());
            assertion(leastSig == dis.readLong());
        }

        return;
    }

    public void tearDown() {
    }

}

