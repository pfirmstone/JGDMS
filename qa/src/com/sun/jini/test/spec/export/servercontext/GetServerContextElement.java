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
package com.sun.jini.test.spec.export.servercontext;

import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collection;

// davis packages
import net.jini.export.ServerContext;

// java.rmi
import java.rmi.server.ServerNotActiveException;

// Server Context Elements
import com.sun.jini.test.spec.export.util.FakeType;
import com.sun.jini.test.spec.export.util.AnFakeType;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link net.jini.export.ServerContext#getServerContextElement(Class)}
 *   method.
 *   getServerContextElement() returns the first element in the current
 *   server context collection (obtained by calling
 *   {@link net.jini.export.ServerContext#getServerContext()}) that is an
 *   instance of the given type. If no element in the collection is an
 *   instance of the type, then null is returned.
 *   Parameters:
 *     type - the type of the element
 *   Returns:
 *     the first element in the server context collection that is an instance
 *     of the type or null
 *
 * Test Cases:
 *   TestCase 1:
 *     getServerContextElement(FakeType.class) method is invoked when an empty
 *     context is set for the current thread.
 *     It's expected that null is returned.
 *   TestCase 2:
 *     getServerContextElement(FakeType.class) method is invoked when context is
 *     set for the current thread; in this context collection only one element
 *     is an instance of the type FakeType.
 *     It's expected that this element of the type FakeType is returned.
 *   TestCase 3:
 *     getServerContextElement(FakeType.class) method is invoked when context is
 *     set for the current thread; there are 2 elements that are instances of
 *     the type FakeType in this context collection.
 *     It's expected that the first element of the type FakeType is returned.
 *
 * Infrastructure:
 *     - {@link GetServerContextElement}
 *         performs actions
 *     - {@link com.sun.jini.test.spec.export.util.FakeType}
 *         used as an element in a context
 *     - {@link com.sun.jini.test.spec.export.util.AnFakeType}
 *         used as an element in a context
 *
 * Actions:
 *   Test performs the following steps:
 *     - creating context collection elements:
 *         - 2 FakeType objects and
 *         - 1 AnFakeType object;
 *     - creating an empty context collection;
 *     - creating context collection that contains only one element of the
 *       type FakeType;
 *     - creating context collection that contains 2 elements of the type
 *       FakeType.
 *   In each test case the test invokes getServerContextElement() method;
 *   the server context is set with
 *   {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
 *   method. The returned result is compared with the expected one.
 *
 * </pre>
 */
public class GetServerContextElement extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.FakeType}
     */
    FakeType cnxtElement1;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.FakeType}
     */
    FakeType cnxtElement2;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.AnFakeType}
     */
    AnFakeType cnxtElement3;

    /**
     * Server context collection for use in TestCase #1.
     */
    ArrayList context1;

    /**
     * Server context collection for use in TestCase #2.
     */
    ArrayList context2;

    /**
     * Server context collection for use in TestCase #3.
     */
    ArrayList context3;

    /**
     * String that describes a test case result.
     */
    String testCaseResultDesc = null;

    /**
     * This method performs all preparations.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Create server context elements */
        cnxtElement1 = new FakeType("Fake Context Element #1");
        cnxtElement2 = new FakeType("Fake Context Element #2");
        cnxtElement3 = new AnFakeType(1234567890);

        /*
         * Create server context collection for TestCase #1.
         * It's an empty server context collection without any element of
         * the type FakeType.
         */
        context1 = new ArrayList();
        context1.clear();
        // logger.log(Level.FINE,
        // "context for TestCase #1:: " + context1.toString());

        /*
         * Create server context collection for TestCase #2.
         * It consists of 1 element of the type FakeType and
         *                1 element of the type AnFakeType only.
         */
        context2 = new ArrayList();
        context2.clear();
        context2.add(cnxtElement1);
        context2.add(cnxtElement3);
        // logger.log(Level.FINE,
        // "context for TestCase #2:: " + context2.toString());

        /*
         * Create server context collection for TestCase #3.
         * It consists of 2 elements of the type FakeType and
         *                1 element of the type AnFakeType only.
         */
        context3 = new ArrayList();
        context3.clear();
        context3.add(cnxtElement1);
        context3.add(cnxtElement2);
        context3.add(cnxtElement3);
        // logger.log(Level.FINE,
        // "context for TestCase #3:: " + context3.toString());
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {

        /*
         * TestCase 1: An empty server context collection
         *             (no elements of the type FakeType)
         */
        testCaseActions(1,
                "An empty server context collection (no elements of the type "
                + "FakeType)", (Collection) context1, FakeType.class, null);

        /*
         * TestCase 2: Server context collection consists of:
         *               - 1 element of the type FakeType and
         *               - 1 element of the type AnFakeType only
         */
        testCaseActions(2,
                "Server context collection consists of 1 element of the type "
                + "FakeType and 1 element of the type AnFakeType only",
                (Collection) context2, FakeType.class, (Object) cnxtElement1);

        /*
         * TestCase 3: Server context collection consists of:
         *               - 2 elements of the type FakeType and
         *               - 1 element of the type AnFakeType only
         */
        testCaseActions(3,
                "Server context collection consists of 2 elements of the type "
                + "FakeType and 1 element of the type AnFakeType only",
                (Collection) context3, FakeType.class, (Object) cnxtElement1);

        return;
    }

    /**
     * This method performs each test case actions.
     * The current server context is set with
     * {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}.
     * In the run() method of the supplied {@link java.lang.Runnable} object
     * {@link net.jini.export.ServerContext#getServerContextElement(Class)} is
     * invoked. The result of getServerContextElement() is compared with the
     * supplied expected one.
     *
     * @param tc_num  TestCase #
     * @param tc_desc TestCase description
     * @param context server context collection to set with
     * {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
     * @param type the type of element in the current server context collection
     * @param expCnxtElement expected element returned by
     * {@link net.jini.export.ServerContext#getServerContextElement(Class)}
     */
    public void testCaseActions(int tc_num, String tc_desc,
            Collection context, final Class type, final Object expCnxtElement)
            throws Exception {
        logger.log(Level.FINE, "\n\n\t+++++ TestCase " + tc_num + " +++++\n");
        logger.log(Level.FINE, tc_desc);
        logger.log(Level.FINE, "The supplied context:: " + context);

        ServerContext.doWithServerContext(new Runnable() {
            public void run() {

                /*
                 * Try to get the first element in the current server
                 * context collection that is an instance of the specified
                 * type.
                 */
                try {
                    Object retCnxtElement =
                            ServerContext.getServerContextElement(type);
                    logger.log(Level.FINE,
                            "\texpected element:: " + expCnxtElement);
                    logger.log(Level.FINE,
                            "\treturned element:: " + retCnxtElement);

                    if (expCnxtElement == retCnxtElement) {
                        testCaseResultDesc = null;
                    } else if (!type.isInstance(retCnxtElement)) {
                        testCaseResultDesc =
                                "Returned element in the current server "
                                + "context collection isn't instance of "
                                + type;
                    } else if (!retCnxtElement.equals(expCnxtElement)) {
                        testCaseResultDesc =
                                "Returned element in the current server "
                                + "context collection isn't equal to the "
                                + "expected one";
                    }
                } catch (Exception e) {
                    testCaseResultDesc =
                            "ServerContext.getServerContextElement("
                            + type + ") has thrown: " + e;
                }
            }
        }
        , context);

        if (testCaseResultDesc != null) {
            logger.log(Level.FINE, "The reason of the FAIL:: " + testCaseResultDesc);
            throw new TestException(testCaseResultDesc);
        }

        return;
    }
}
