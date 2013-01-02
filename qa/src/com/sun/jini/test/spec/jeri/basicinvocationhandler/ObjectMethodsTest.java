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

import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeMethodConstraints;

import java.lang.reflect.Proxy;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method when equals, hashCode, or toString methods are passed to the
 *   invoke method.
 *
 *   This test verifies the behavior of the BasicInvocationHandler
 *   method when equals, hashCode, or toString methods are called on
 *   an instance of BasicInvocationHandler.
 *
 * Test Cases
 *   This test iterates over a 3-tuple.  Each 3-tuple
 *   denotes one test case and is defined by the variables:
 *     Class[] proxyInterfaces
 *     InvocationConstraint[] clientConstraints
 *     InvocationConstraint[] serverConstraints
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface1 and FakeInterface2
 *          -two top-level interfaces, each declaring one method
 *     2) FakeSubInterface1
 *          -a sub-interface of FakeInterface1 which overloads it's method
 *     3) FakeObjectEndpoint
 *          -newCall method throws AssertionError (should never be called)
 *          -executeCall method throws AssertionError (should never be called)
 *          -overloaded equals method bases equality on int passed to 
 *           constructor of FakeObjectEndpoint
 *     4) FakeMethodConstraints
 *          -getConstraints method returns InvocationConstraints created
 *           from InvocationConstraint[] passed to constructor or, if null,
 *           InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an iterator over
 *           return value of getConstraints
 *
 * Actions
 *   The test performs the following steps:
 *     1) for each test case, i:
 *        1) construct a FakeObjectEndpoint, passing in i
 *        2) constructs a BasicInvocationHandler with FakeObjectEndpoint,
 *           clientConstraints, and serverConstraints from tuple i
 *        3) create a dynamic proxy that implements proxyInterfaces from tuple i
 *           and the created BasicInvocationHandler
 *        4) for each test case, j:
 *           1) create a dynamic proxy as above, but use values from tuple j
 *     BasicInvocationHandler.invoke test cases:
 *           2) verify toString methods of the proxies return non-null 
 *              String objects
 *           3) verify the proxies are .equals to themselves (reflexive)
 *           4) if i == j
 *                verify both proxies are .equals to each other (symmetric)
 *                and they return the same hash code
 *              else 
 *                verify that neither proxy is .equals to the other
 *     BasicInvocationHandler test cases:
 *           5) verify toString methods of the invocation handlers return
 *              non-null String objects
 *           6) verify the invocation handlers are .equals to themselves 
 *              (reflexive)
 *           7) if i == j
 *                verify that both invocation handlers are .equals to each
 *                other (symmetric) and they return the same hash code
 *              else 
 *                verify that neither invocation handler is .equals to the other
 * </pre>
 */
public class ObjectMethodsTest extends QATestEnvironment implements Test {

    interface FakeInterface1 {
        public Object fake1Method() throws Throwable;
    }

    interface FakeSubInterface1 extends FakeInterface1 {
        public Object fake1Method() throws Throwable;
    }

    interface FakeInterface2 {
        public Object fake2Method() throws Throwable;
    }

    Class fi1 = FakeInterface1.class;
    Class fsi1 = FakeSubInterface1.class;
    Class fi2 = FakeInterface2.class;

    // test cases
    Object[][] cases = {
        //proxyInterfaces, clientConstraints, serverConstraints
        { new Class[] {}, 
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
        },
        { new Class[] {fi2,fsi1},
          new InvocationConstraint[] {},
          new InvocationConstraint[] {}
        },
        { new Class[] {fsi1,fi2},
          new InvocationConstraint[] {},
          new InvocationConstraint[] {}
        }
    };

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;
        Class[] proxyInterfaces;
        InvocationConstraint[] clientConstraints;
        InvocationConstraint[] serverConstraints;
        BasicInvocationHandler handler1;
        BasicInvocationHandler handler2;
        Object proxy1;
        Object proxy2;

        for (int i = 0; i < cases.length; i++) {
            proxyInterfaces = (Class[])cases[i][0];
            clientConstraints = (InvocationConstraint[])cases[i][1];
            serverConstraints = (InvocationConstraint[])cases[i][2];

            handler1 = new BasicInvocationHandler(
                new BasicInvocationHandler(
                    new FakeObjectEndpoint(i),
                    new FakeMethodConstraints(serverConstraints)),
                new FakeMethodConstraints(clientConstraints));

            proxy1 = Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                proxyInterfaces, handler1);

            for (int j = 0; j < cases.length; j++) {
                logger.log(Level.FINE,"=================================");
                logger.log(Level.FINE,"test case " + (counter++) + ": ");
                logger.log(Level.FINE,"");

                proxyInterfaces = (Class[])cases[j][0];
                clientConstraints = (InvocationConstraint[])cases[j][1];
                serverConstraints = (InvocationConstraint[])cases[j][2];

                handler2 = new BasicInvocationHandler(
                    new BasicInvocationHandler(
                        new FakeObjectEndpoint(j),
                        new FakeMethodConstraints(serverConstraints)),
                    new FakeMethodConstraints(clientConstraints));

                proxy2 = Proxy.newProxyInstance(
                    this.getClass().getClassLoader(),
                    proxyInterfaces, handler2);

                // verify BasicInvocationHandler.invoke test cases
                assertion(proxy1.toString() != null);
                assertion(proxy2.toString() != null);

                assertion(proxy1.equals(proxy1));
                assertion(proxy2.equals(proxy2));

                if (i == j) {
                    assertion(proxy1.equals(proxy2));
                    assertion(proxy2.equals(proxy1));
                    assertion(proxy1.hashCode() == proxy2.hashCode());
                } else {
                    assertion(! proxy1.equals(proxy2));
                    assertion(! proxy2.equals(proxy1));
                }

                // verify BasicInvocationHandler test cases
                assertion(handler1.toString() != null);
                assertion(handler2.toString() != null);

                assertion(handler1.equals(handler1));
                assertion(handler2.equals(handler2));
                assertion(! handler1.equals(new Object()));

                if (i == j) {
                    assertion(handler1.equals(handler2));
                    assertion(handler2.equals(handler1));
                    assertion(handler1.hashCode() == handler2.hashCode());
                } else {
                    assertion(! handler1.equals(handler2));
                    assertion(! handler2.equals(handler1));
                }
            }//inner for loop
        }//outer for loop
    }

    // inherit javadoc
    public void tearDown() {
    }

}

