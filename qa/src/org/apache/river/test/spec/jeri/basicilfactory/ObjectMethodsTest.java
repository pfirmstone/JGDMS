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
package org.apache.river.test.spec.jeri.basicilfactory;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.BasicILFactory;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.InvocationLayerFactory.Instances;
import net.jini.jeri.ServerCapabilities;
import net.jini.export.ExportPermission;

import org.apache.river.test.spec.jeri.util.FakeAbstractILFactory;
import org.apache.river.test.spec.jeri.util.FakeMethodConstraints;

import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicILFactory
 *   equals, hashCode, and toString methods.
 *
 * Test Cases
 *   This test iterates over a 3-tuple.  Each 3-tuple
 *   denotes one test case and is defined by the variables:
 *     MethodConstraints methodConstraints
 *     Class             permClass
 *     ClassLoader       classLoader
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeAbstractILFactory
 *          -a concrete subclass of AbstractILFactory
 *          -methods throw AssertionError (should never be called)
 *     2) FakeInvocationLayerFactory
 *          -a concrete implementation of InvocationLayerFactory
 *          -methods throw AssertionError (should never be called)
 *     3) FakeEmptyMethodConstraints
 *          -getConstraints method returns InvocationConstraints.EMPTY
 *          -possibleConstraints method returns an empty iterator
 *
 * Actions
 *   The test performs the following steps:
 *     1) for each test case, i:
 *        1) construct one instance of BasicILFactory, passing in
 *           methodConstraints, permClass, and classLoader
 *        2) construct a FakeAbstractILFactory and verify it is
 *           not .equals to the BasicILFactory instance
 *        3) construct a FakeInvocationLayerFactory and verify it is
 *              not .equals to the BasicILFactory instance
 *        4) for each test case, j:
 *           1) construct a second instance of BasicILFactory, passing in
 *              methodConstraints, permClass, and classLoader
 *           2) verify instances are .equals to themselves (reflexive)
 *           3) verify instances .toString methods return non-null String object
 *           4) if i == j
 *                verify instances are .equals to each other (symmetric)
 *                verify instances .hashCode methods return the same value
 *              else
 *                verify instances are NOT .equals to each other (symmetric)
 * </pre>
 */
public class ObjectMethodsTest extends QATestEnvironment implements Test {

    class FakeInvocationLayerFactory implements InvocationLayerFactory {
        public InvocationLayerFactory.Instances createInstances(Remote impl,
            ObjectEndpoint oe, ServerCapabilities sc) throws ExportException
        { throw new AssertionError(); }
    }

    // test case infrastructure
    FakeMethodConstraints mc = new FakeMethodConstraints(null);
    Class pc = ExportPermission.class;
    ClassLoader loader = this.getClass().getClassLoader();

    // test cases
    Object[][] cases = {
        //methodConstraints, permClass, classLoader
        {null, null, null  },
        {null, null, loader},
        {mc,   null, null  },
        {mc,   null, loader},
        {null, pc,   null  },
        {null, pc,   loader},
        {mc,   pc,   null  },
        {mc,   pc,   loader}
    };

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        BasicILFactory factory1;
        BasicILFactory factory2;
        int counter = 1;
        for (int i = 0; i < cases.length; i++) {
            FakeMethodConstraints mConstraints1 =
                (FakeMethodConstraints)cases[i][0];
            Class permClass1 = (Class)cases[i][1];
            ClassLoader classLoader1 = (ClassLoader)cases[i][2];

            factory1 = 
                new BasicILFactory(mConstraints1,permClass1,classLoader1);

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case " + (counter++) + ": "
                + "equals is false with AbstractILFactory impl");
            logger.log(Level.FINE,"");

            FakeAbstractILFactory fa = new FakeAbstractILFactory();
            assertion(! factory1.equals(fa));

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case " + (counter++) + ": "
                + "equals is false with InvocationLayerFactory impl");
            logger.log(Level.FINE,"");

            FakeInvocationLayerFactory fi =new FakeInvocationLayerFactory();
            assertion(! factory1.equals(fi));

            for (int j = 0; j < cases.length; j++) {
                FakeMethodConstraints mConstraints2 = 
                    (FakeMethodConstraints)cases[j][0];
                Class permClass2 = (Class)cases[j][1];
                ClassLoader classLoader2 = (ClassLoader)cases[j][2];

                factory2 = new BasicILFactory(
                    mConstraints2,permClass2,classLoader2);

                logger.log(Level.FINE,"=================================");
                logger.log(Level.FINE,"test case " + (counter++) + ": "
                    + "equals, hashCode, toString method calls: "
                    + "methodConstraints1:" + mConstraints1
                    + ", permClass1:" + permClass1
                    + ", classLoader1:" + classLoader1
                    + ", methodConstraints2:" + mConstraints2
                    + ", permClass2:" + permClass2
                    + ", classLoader2:" + classLoader2);
                logger.log(Level.FINE,"");

                // verify equals, hashCode, and toString methods
                assertion(! factory1.equals(null));
                assertion(factory1.equals(factory1));
                assertion(factory2.equals(factory2));
                assertion(factory1.toString() != null);
                assertion(factory2.toString() != null);

                if (i == j) {
                    assertion(factory1.equals(factory2));
                    assertion(factory2.equals(factory1));
                    assertion(factory1.hashCode() == factory2.hashCode());
                } else {
                    assertion(! factory1.equals(factory2));
                    assertion(! factory2.equals(factory1));
                }

            } //for j

        } //for i
    }

    // inherit javadoc
    public void tearDown() {
    }

}

