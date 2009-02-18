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
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
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
 *   {@link net.jini.export.ServerContext#getServerContext()} method.
 *   getServerContext() returns the server context collection for the current
 *   thread. The server context collection may be empty.
 *   Returns:
 *     the server context for the current thread
 *
 * Test Cases:
 *   TestCase 1:
 *     - server context collection containing 2 elements of type FakeType;
 *     - it's expected that the the server context returned by
 *       getServerContext() method is equal to the created server context
 *       collection (contains the same 2 elements of type FakeType only);
 *   TestCase 2:
 *     - an empty server context collection;
 *     - it's expected that the the server context returned by
 *       getServerContext() method is empty;
 *
 * Infrastructure:
 *     - {@link GetServerContext}
 *         performs actions
 *     - {@link com.sun.jini.test.spec.export.util.FakeType}
 *         used as an element in a context
 *
 * Actions:
 *   In each test case the following actions are performed:
 *   - create server context collection;
 *   - invoke
 *     {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
 *     method supplying the created server context; in the run() method of the
 *     supplied {@link java.lang.Runnable} object getServerContext() method is
 *     invoked;
 *   - verify that the server context returned by getServerContext() method is
 *     equal to the created server context collection.
 *
 * </pre>
 */
public class GetServerContext extends QATest {
    QAConfig config;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.FakeType}
     */
    FakeType cnxtEl1;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.FakeType}
     */
    FakeType cnxtEl2;

    /**
     * Server context collection to set by means of
     * {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
     * method.
     */
    ArrayList context;

    /**
     * Server context collection returned by
     * {@link net.jini.export.ServerContext#getServerContext()} method.
     */
    Collection retcontext;

    /**
     * String that describes test result.
     */
    String testResult = null;

    /**
     * This method performs all preparations.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Create server context elements of the type FakeType */
        cnxtEl1 = new FakeType("Fake Context Element #1");
        cnxtEl2 = new FakeType("Fake Context Element #2");

        /* Create server context collection with the created elements */
        context = new ArrayList();
        context.add(cnxtEl1);
        context.add(cnxtEl2);
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        // TestCase 1: Non-empty context collection
        logger.log(Level.FINE,
                "\nTestCase 1: Non-empty context collection"
                + context.toString());
        logger.log(Level.FINE,
                "\t+++++ doWithServerContext(runnable, context)");

        ServerContext.doWithServerContext(new Runnable() {

            public void run() {

                /* Get server context collection */
                try {
                    logger.log(Level.FINE, "\t\t+++++ getServerContext()");
                    retcontext = ServerContext.getServerContext();
                    logger.log(Level.FINE,
                            "expected context: " + context.toString());
                    logger.log(Level.FINE,
                            "returned context: " + retcontext.toString());
                    if (!retcontext.equals(context)) {
                        testResult =
                                "Returned server context collection isn't "
                                + "equal to the expected one";
                    }
                } catch (Exception e) {
                    testResult = "getServerContext() has thrown: " + e;
                }
            }
        }
        , (Collection) context);

        if (testResult != null) {
            logger.log(Level.FINE, "The reason of the FAIL:: " + testResult);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase 2: Empty context collection
        context.clear();
        logger.log(Level.FINE,
                "\nTestCase 2: Empty context collection" + context.toString());
        logger.log(Level.FINE,
                "\t+++++ doWithServerContext(runnable, emptycontext)");

        ServerContext.doWithServerContext(new Runnable() {

            public void run() {

                /* Get server context collection */
                try {
                    logger.log(Level.FINE, "\t\t+++++ getServerContext()");

                    retcontext = ServerContext.getServerContext();
                    logger.log(Level.FINE,
                            "expected context: " + context.toString());
                    logger.log(Level.FINE,
                            "returned context: " + retcontext.toString());
                    if (!retcontext.equals(context)) {
                        testResult =
                            "Returned server context collection isn't "
                            + "equal to the expected one";
                    }
                } catch (Exception e) {
                    testResult = "getServerContext() has thrown: " + e;
                }
            }
        }
        , (Collection) context);

        if (testResult != null) {
            logger.log(Level.FINE, "The reason of the FAIL:: " + testResult);
            throw new TestException(
                    "" + " test failed");
        }

        return;
    }
}
