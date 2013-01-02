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
package com.sun.jini.test.spec.jeri.basicobjectendpoint;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.id.UuidFactory;
import net.jini.id.Uuid;

import com.sun.jini.test.spec.jeri.util.FakeEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequestIterator;

import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicObjectEndpoint
 *   during normal and exceptional constructor calls.
 *
 *   This test verifies the behavior of the
 *   BasicObjectEndpoint.getEnableDGC, BasicObjectEndpoint.getEndpoint
 *   and BasicObjectEndpoint.getObjectIdentifier methods.
 *
 * Test Cases
 *   This test contains these test cases: (* indicates a "don't care" value)
 *     1) new BasicObjectEndpoint(null,null,*)
 *     2) new BasicObjectEndpoint(Endpoint,null,*)
 *     3) new BasicObjectEndpoint(null,Uuid,*)
 *     4) new BasicObjectEndpoint(Endpoint,Uuid,true)
 *     5) new BasicObjectEndpoint(Endpoint,Uuid,false)
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeEndpoint
 *          -implements Endpoint
 *          -newRequest method throws AssertionError
 *
 * Actions
 *   The test performs the following steps:
 *     new BasicObjectEndpoint(null,null,*)
 *       1) construct a BasicObjectEndpoint, passing in null for Endpoint
 *          and Uuid arguments
 *       2) assert NullPointerException is thrown
 *     new BasicObjectEndpoint(Endpoint,null,*)
 *       3) construct a FakeEndpoint
 *       4) construct a BasicObjectEndpoint, passing in FakeEndpoint
 *          and a null Uuid
 *       5) assert NullPointerException is thrown
 *     new BasicObjectEndpoint(null,Uuid,*)
 *       6) construct a Uuid
 *       7) construct a BasicObjectEndpoint, passing in null Endpoint
 *          and the Uuid
 *       8) assert NullPointerException is thrown
 *     new BasicObjectEndpoint(Endpoint,Uuid,*)
 *       9) construct a BasicObjectEndpoint, passing in FakeEndpoint,
 *          the Uuid object created above, and (true/false)
 *      10) assert getEndpoint and getObjectIdentifier methods return
 *          the same objects passed to the constructor
 *      11) assert getEnableDGC returns the same value passed
 *          to the constructor
 * </pre>
 */
public class ConstructorAccessorTest extends QATestEnvironment implements Test {

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        BasicObjectEndpoint boe;
        Endpoint ep = new FakeEndpoint(new FakeOutboundRequestIterator(
            new FakeOutboundRequest(),false));
        Uuid uuid = UuidFactory.create(1,2);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "BasicObjectEndpoint(null,null,false)");
        logger.log(Level.FINE,"");

        try {
            boe = new BasicObjectEndpoint(null,null,false);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "BasicObjectEndpoint(Endpoint,null,false)");
        logger.log(Level.FINE,"");

        try {
            boe = new BasicObjectEndpoint(ep,null,false);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "BasicObjectEndpoint(null,Uuid,false)");
        logger.log(Level.FINE,"");

        try {
            boe = new BasicObjectEndpoint(null,uuid,false);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "accessor methods return constructor args");
        logger.log(Level.FINE,"");

        boe = new BasicObjectEndpoint(ep,uuid,true);
        assertion(boe.getEndpoint() == ep);
        assertion(boe.getObjectIdentifier() == uuid);
        assertion(boe.getEnableDGC() == true);

        boe = new BasicObjectEndpoint(ep,uuid,false);
        assertion(boe.getEnableDGC() == false);
    }

    // inherit javadoc
    public void tearDown() {
    }

}

