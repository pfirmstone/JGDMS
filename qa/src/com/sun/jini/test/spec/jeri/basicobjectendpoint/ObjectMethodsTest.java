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
import net.jini.jeri.ObjectEndpoint;
import net.jini.id.UuidFactory;
import net.jini.id.Uuid;

import com.sun.jini.test.spec.jeri.util.FakeEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequestIterator;

import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicObjectEndpoint
 *   equals, hashCode, and toString methods.
 *
 * Test Cases
 *   Test cases are defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObjectEndpoint
 *          -implements ObjectEndpoint
 *          -methods throw AssertionError
 *     2) FakeEndpoint
 *          -implements Endpoint
 *          -newRequest method throws AssertionError
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct two instances of BasicObjectEndpoint,
 *        passing in FakeEndpoint and a Uuid
 *     2) verify instances are .equals to themselves (reflexive)
 *        and .equals to each other (symmetric)
 *     3) verify instances .hashCode methods return the same value
 *     4) verify instances .toString methods return non-null String objects
 *     5) construct two instances of BasicObjectEndpoint, passing in differnt
 *        FakeEndpoints
 *     6) verify instances are not .equals
 *     7) construct two instances of BasicObjectEndpoint, passing in differnt
 *        Uuids
 *     8) verify instances are not .equals
 *     9) construct two instances of BasicObjectEndpoint, passing in differnt
 *        enableDGC values
 *    10) verify instances are not .equals
 *    11) construct a FakeObjectEndpoint and verify it is
 *        not .equals to a BasicObjectEndpoint instance
 * </pre>
 */
public class ObjectMethodsTest extends QATestEnvironment implements Test {

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeEndpoint ep1 = new FakeEndpoint(new FakeOutboundRequestIterator(
            new FakeOutboundRequest(),false));
        FakeEndpoint ep2 = new FakeEndpoint(new FakeOutboundRequestIterator(
            new FakeOutboundRequest(),false));
        Uuid uuid1 = UuidFactory.create(1,2);
        Uuid uuid2 = UuidFactory.create(2,1);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "normal equals, hashCode, toString method calls");
        logger.log(Level.FINE,"");

        // construct two instances of BasicObjectEndpoint
        BasicObjectEndpoint boe1 = new BasicObjectEndpoint(ep1,uuid1,false);
        BasicObjectEndpoint boe2 = new BasicObjectEndpoint(ep1,uuid1,false);

        // verify BasicObjectEndpoint equals, hashCode, and toString methods
        assertion(! boe1.equals(null));
        assertion(boe1.equals(boe2));
        assertion(boe2.equals(boe1));
        assertion(boe1.equals(boe1));
        assertion(boe2.equals(boe2));
        assertion(boe1.hashCode() == boe2.hashCode());
        assertion(boe1.toString() != null);
        assertion(boe2.toString() != null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "equals is false when contained Endpoints are different");
        logger.log(Level.FINE,"");

        boe2 = new BasicObjectEndpoint(ep2,uuid1,false);
        assertion(! boe1.equals(boe2));

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "equals is false when contained Uuids are different");
        logger.log(Level.FINE,"");

        boe2 = new BasicObjectEndpoint(ep1,uuid2,false);
        assertion(! boe1.equals(boe2));

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "equals is false when contained enableDGC are different");
        logger.log(Level.FINE,"");

        boe2 = new BasicObjectEndpoint(ep1,uuid1,true);
        assertion(! boe1.equals(boe2));

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "equals is false with different ObjectEndpoint impl");
        logger.log(Level.FINE,"");

        ObjectEndpoint fake = new FakeObjectEndpoint();
        assertion(! boe1.equals(fake));
    }

    // inherit javadoc
    public void tearDown() {
    }

}

