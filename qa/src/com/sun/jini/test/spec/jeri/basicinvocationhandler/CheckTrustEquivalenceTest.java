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
package com.sun.jini.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.Confidentiality;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.security.proxytrust.TrustEquivalence;

import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;

import java.lang.reflect.Proxy;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method when checkTrustEquivalence method is passed to the
 *   invoke method.
 *
 *   This test verifies the behavior of the BasicInvocationHandler
 *   method when checkTrustEquivalence method is called on
 *   an instance of BasicInvocationHandler.
 *
 * Test Cases
 *   This test iterates over a set of tuples.  Each tuple
 *   denotes one test case and is defined by the variables:
 *     Class[] proxyInterfaces
 *     InvocationConstraint[] clientConstraints
 *     InvocationConstraint[] serverConstraints
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface1 and FakeInterface2
 *          -two top-level interfaces, each declaring one method
 *           and implement TrustEquivalence
 *     2) FakeSubInterface1
 *          -a sub-interface of FakeInterface1 which overloads it's method
 *     3) FakeObjectEndpoint
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *          -checkTrustEquivalence method returns true
 *     4) FakeTrustedObjectEndpoint
 *          -implements TrustEquivalence
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *          -checkTrustEquivalence method returns boolean passed to constructor
 *     5) FakeMethodConstraints
 *          -getConstraints method returns InvocationConstraints created
 *           from InvocationConstraint[] passed to constructor or, if null,
 *           InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an iterator over
 *           return value of getConstraints
 *
 * Actions
 *   The test performs the following steps:
 *     1) for each test case, i:
 *        1) for each test case, j:
 *           1) for each of {FakeObjectEndpoint,
 *                           FakeTrustedObjectEndpoint(false),
 *                           FakeTrustedObjectEndpoint(true)}:
 *              1) constructs a BasicInvocationHandler with current
 *                 ObjectEndpoint, clientConstraints from tuple i, and 
 *                 serverConstraints from tuple i
 *              2) create a dynamic proxy that implements proxyInterfaces
 *                 from tuple i and the created BasicInvocationHandler
 *              3) create a BasicInvocationHandler and dynamic proxy as above,
 *                 but use values from tuple j
 *              4) for each proxy and handler, verify
 *                 checkTrustEquivalence returns expected value
 * </pre>
 */
public class CheckTrustEquivalenceTest extends QATestEnvironment implements Test {

    // an ObjectEndpoint that impls TrustEquivalence and is configurable
    class FakeTrustedObjectEndpoint 
        extends FakeObjectEndpoint implements TrustEquivalence 
    {
        private boolean trusted;
        public FakeTrustedObjectEndpoint(boolean trusted) {
            super();
            this.trusted = trusted;
        }
        public boolean checkTrustEquivalence(Object obj) { return trusted; }
    }

    interface FakeInterface1 extends TrustEquivalence {
        public Object fake1Method() throws Throwable;
    }

    interface FakeSubInterface1 extends FakeInterface1 {
        public Object fake1Method() throws Throwable;
    }

    interface FakeInterface2 extends TrustEquivalence {
        public Object fake2Method() throws Throwable;
    }

    Class fi1 = FakeInterface1.class;
    Class fsi1 = FakeSubInterface1.class;
    Class fi2 = FakeInterface2.class;

    // test cases
    Object[][] cases = {
        //proxyInterfaces,objectEndpoint,clientConstraints,serverConstraints
        { new Class[] {TrustEquivalence.class}, 
          new InvocationConstraint[] {},
          new InvocationConstraint[] {}
        },
        { new Class[] {fi1},
          new InvocationConstraint[] {Integrity.YES},
          new InvocationConstraint[] {}
        },
        { new Class[] {fsi1},
          new InvocationConstraint[] {},
          new InvocationConstraint[] {Integrity.YES}
        },
        { new Class[] {fi2},
          new InvocationConstraint[] {Integrity.YES},
          new InvocationConstraint[] {Integrity.YES}
        },
        { new Class[] {fi1,fi2},
          new InvocationConstraint[] {Integrity.YES},
          new InvocationConstraint[] {Integrity.NO}
        }
    };

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;
        Class[] proxyInterfaces1;
        Class[] proxyInterfaces2;
        InvocationConstraint[] client1;
        InvocationConstraint[] client2;
        InvocationConstraint[] server1;
        InvocationConstraint[] server2;
        FakeObjectEndpoint oe1 = new FakeObjectEndpoint();
        FakeObjectEndpoint oe2 = new FakeTrustedObjectEndpoint(false);
        FakeObjectEndpoint oe3 = new FakeTrustedObjectEndpoint(true);
        BasicInvocationHandler handler1;
        BasicInvocationHandler handler2;
        TrustEquivalence proxy1;
        TrustEquivalence proxy2;

        for (int i = 0; i < cases.length; i++) {
            proxyInterfaces1 = (Class[])cases[i][0];
            client1 = (InvocationConstraint[])cases[i][1];
            server1 = (InvocationConstraint[])cases[i][2];

            for (int j = 0; j < cases.length; j++) {
                proxyInterfaces2 = (Class[])cases[j][0];
                client2 = (InvocationConstraint[])cases[j][1];
                server2 = (InvocationConstraint[])cases[j][2];

                logger.log(Level.FINE,"=================================");
                logger.log(Level.FINE,"test case " + (counter++) + ": "
                    + "i=" + i + ",j=" + j
                    + ",ObjectEndpoint doesn't implement TrustEquivalence");
                logger.log(Level.FINE,"");

                handler1 = newHandler(oe1,client1,server1);
                handler2 = newHandler(oe1,client2,server2);
                proxy1 = newProxy(proxyInterfaces1,handler1);
                proxy2 = newProxy(proxyInterfaces2,handler2);

                // verify BasicInvocationHandler.invoke test cases
                assertion(! proxy1.checkTrustEquivalence(proxy2));
                assertion(! proxy2.checkTrustEquivalence(proxy1));
                // verify BasicInvocationHandler test cases
                assertion(! handler1.checkTrustEquivalence(handler2));
                assertion(! handler2.checkTrustEquivalence(handler1));

                logger.log(Level.FINE,"=================================");
                logger.log(Level.FINE,"test case " + (counter++) + ": "
                    + "i=" + i + ",j=" + j
                    +",ObjectEndpoint.checkTrustEquivalence returns false");
                logger.log(Level.FINE,"");

                handler1 = newHandler(oe2,client1,server1);
                handler2 = newHandler(oe2,client2,server2);
                proxy1 = newProxy(proxyInterfaces1,handler1);
                proxy2 = newProxy(proxyInterfaces2,handler2);

                // verify BasicInvocationHandler.invoke test cases
                assertion(! proxy1.checkTrustEquivalence(proxy2));
                assertion(! proxy2.checkTrustEquivalence(proxy1));
                // verify BasicInvocationHandler test cases
                assertion(! handler1.checkTrustEquivalence(handler2));
                assertion(! handler2.checkTrustEquivalence(handler1));

                logger.log(Level.FINE,"=================================");
                logger.log(Level.FINE,"test case " + (counter++) + ": "
                    + "i=" + i + ",j=" + j
                    + ",ObjectEndpoint.checkTrustEquivalence returns true");
                logger.log(Level.FINE,"");

                handler1 = newHandler(oe3,client1,server1);
                handler2 = newHandler(oe3,client2,server2);
                proxy1 = newProxy(proxyInterfaces1,handler1);
                proxy2 = newProxy(proxyInterfaces2,handler2);

                if (i == j) {
                    // verify BasicInvocationHandler.invoke test cases
                    assertion(proxy1.checkTrustEquivalence(proxy2));
                    assertion(proxy2.checkTrustEquivalence(proxy1));
                    // verify BasicInvocationHandler test cases
                    assertion(handler1.checkTrustEquivalence(handler2));
                    assertion(handler2.checkTrustEquivalence(handler1));
                } else {
                    // verify BasicInvocationHandler.invoke test cases
                    assertion(! proxy1.checkTrustEquivalence(proxy2));
                    assertion(! proxy2.checkTrustEquivalence(proxy1));
                    // verify BasicInvocationHandler test cases
                    assertion(! handler1.checkTrustEquivalence(handler2));
                    assertion(! handler2.checkTrustEquivalence(handler1));
                }
            }//inner for loop
        }//outer for loop
    }

    // inherit javadoc
    public void tearDown() {
    }

    private BasicInvocationHandler newHandler(FakeObjectEndpoint oe,
        InvocationConstraint[] client, InvocationConstraint[] server) 
    {
        return new BasicInvocationHandler(
            new BasicInvocationHandler(oe, new FakeMethodConstraints(server)),
            new FakeMethodConstraints(client));
    }
    
    private TrustEquivalence newProxy(Class[] i, BasicInvocationHandler bih) {
        return (TrustEquivalence) Proxy.newProxyInstance(
            this.getClass().getClassLoader(), i, bih);
    }

}

