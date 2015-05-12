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
package org.apache.river.test.spec.jeri.basicobjectendpoint;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.BasicObjectEndpoint;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

import org.apache.river.test.spec.jeri.util.FakeObjectEndpoint;
import org.apache.river.test.spec.jeri.util.FakeEndpoint;
import org.apache.river.test.spec.jeri.util.FakeOutboundRequest;
import org.apache.river.test.spec.jeri.util.FakeOutboundRequestIterator;

import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicObjectEndpoint
 *   method when checkTrustEquivalence method is called.
 *
 * Test Cases
 *   This test contains these test cases:
 *     1) new BasicObjectEndpoint(FakeEndpoint,               uuid1, false),
 *     2) new BasicObjectEndpoint(FakeEndpoint,               uuid2, false),
 *     3) new BasicObjectEndpoint(FakeEndpoint,               uuid1, true),
 *     4) new BasicObjectEndpoint(FakeEndpoint,               uuid2, true),
 *     5) new BasicObjectEndpoint(FakeTrustedEndpoint(false), uuid1, false),
 *     6) new BasicObjectEndpoint(FakeTrustedEndpoint(false), uuid2, false),
 *     7) new BasicObjectEndpoint(FakeTrustedEndpoint(false), uuid1, true),
 *     8) new BasicObjectEndpoint(FakeTrustedEndpoint(false), uuid2, true),
 *     9) new BasicObjectEndpoint(FakeTrustedEndpoint(true),  uuid1, false),
 *    10) new BasicObjectEndpoint(FakeTrustedEndpoint(true),  uuid2, false),
 *    11) new BasicObjectEndpoint(FakeTrustedEndpoint(true),  uuid1, true),
 *    12) new BasicObjectEndpoint(FakeTrustedEndpoint(true),  uuid2, true)
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObjectEndpoint
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *     2) FakeEndpoint
 *          -implements Endpoint
 *          -newRequest method throws AssertionError
 *     3) FakeTrustedEndpoint
 *          -implements Endpoint and TrustEquivalence
 *          -newRequest method throws AssertionError
 *          -checkTrustEquivalence method returns boolean passed to constructor
 *
 * Actions
 *   The test performs the following steps:
 *     1) for each test case, i:
 *        1) for each test case, j:
 *           1) construct BasicObjectEndpoint as shown in tuple i
 *           2) construct BasicObjectEndpoint as shown in tuple j
 *           3) call checkTrustEquivalence on BasicObjectEndpoint i,
 *              passing in BasicObjectEndpoint j
 *           4) assert correct result is returned
 * </pre>
 */
public class CheckTrustEquivalenceTest extends QATestEnvironment implements Test {

    // an Endpoint that impls TrustEquivalence and is configurable
    class FakeTrustedEndpoint 
        extends FakeEndpoint implements TrustEquivalence 
    {
        private boolean trusted;
        public FakeTrustedEndpoint(boolean trusted) {
            super(new FakeOutboundRequestIterator(
                new FakeOutboundRequest(),false));
            this.trusted = trusted;
        }
        public boolean checkTrustEquivalence(Object obj) { return trusted; }
    }

    FakeEndpoint fakeEndpoint = new FakeEndpoint(
        new FakeOutboundRequestIterator(new FakeOutboundRequest(),false));
    FakeTrustedEndpoint fakeUntrustedEndpoint = new FakeTrustedEndpoint(false);
    FakeTrustedEndpoint fakeTrustedEndpoint = new FakeTrustedEndpoint(true);

    Uuid uuid1 = UuidFactory.create(1,2);
    Uuid uuid2 = UuidFactory.create(2,1);

    Boolean t = Boolean.TRUE;
    Boolean f = Boolean.FALSE;

    // test cases
    Object[][] cases = {
        //endpoint,uuid,enableDGC
        { fakeEndpoint,          uuid1, f },
        { fakeEndpoint,          uuid2, f },
        { fakeEndpoint,          uuid1, t },
        { fakeEndpoint,          uuid2, t },
        { fakeUntrustedEndpoint, uuid1, f },
        { fakeUntrustedEndpoint, uuid2, f },
        { fakeUntrustedEndpoint, uuid1, t },
        { fakeUntrustedEndpoint, uuid2, t },
        { fakeTrustedEndpoint,   uuid1, f },
        { fakeTrustedEndpoint,   uuid2, f },
        { fakeTrustedEndpoint,   uuid1, t },
        { fakeTrustedEndpoint,   uuid2, t }
    };

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;
        BasicObjectEndpoint boe1;
        BasicObjectEndpoint boe2;

        logger.log(Level.FINE,"=================================");
        boe1 = new BasicObjectEndpoint(fakeTrustedEndpoint,uuid1,false);
        logger.log(Level.FINE,"test case " + (counter++) + ": "
            + ",BasicObjectEndpoint1: " + boe1
            + ",BasicObjectEndpoint2: new Object()");
        logger.log(Level.FINE,"");

        assertion(! boe1.checkTrustEquivalence(new Object()));

        for (int i = 0; i < cases.length; i++) {
            for (int j = 0; j < cases.length; j++) {
                boe1 = new BasicObjectEndpoint(
                    (FakeEndpoint)cases[i][0],
                    (Uuid)cases[i][1],
                    ((Boolean)cases[i][2]).booleanValue());
                boe2 = new BasicObjectEndpoint(
                    (FakeEndpoint)cases[j][0],
                    (Uuid)cases[j][1],
                    ((Boolean)cases[j][2]).booleanValue());

                logger.log(Level.FINE,"=================================");
                logger.log(Level.FINE,"test case " + (counter++) + ": "
                    + "i=" + i + ",j=" + j
                    + ",BasicObjectEndpoint1: " + boe1
                    + ",BasicObjectEndpoint2: " + boe2);
                logger.log(Level.FINE,"");

                if (cases[i][0] == fakeTrustedEndpoint &&
                    cases[i][1] == cases[j][1] && 
                    cases[i][2] == cases[j][2]) 
                {
                    assertion(boe1.checkTrustEquivalence(boe2));
                } else {
                    assertion(! boe1.checkTrustEquivalence(boe2));
                }

            }//inner for loop
        }//outer for loop
    }

    // inherit javadoc
    public void tearDown() {
    }

}

