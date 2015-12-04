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
package org.apache.river.test.spec.export.servercontext;

import java.util.logging.Level;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig; // base class for QAConfig
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collection;

// davis packages
import net.jini.export.ServerContext;

// java.rmi
import java.rmi.server.ServerNotActiveException;

// Server Context Element
import org.apache.river.test.spec.export.util.FakeType;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
 *   method.
 *   doWithServerContext() method sets the server context for the current thread
 *   to the supplied context collection and invokes the run method of the
 *   supplied runnable object.
 *   Parameters:
 *     runnable - the action to perform in the server context
 *     context  - the context to set
 *   Throws:
 *     {@link java.lang.NullPointerException} - if context or runnable is null
 *
 * Test Cases:
 *   TestCase 1:
 *     doWithServerContext(runnable,null) method is invoked;
 *     It's expected that NullPointerException is thrown.
 *   TestCase 2:
 *     doWithServerContext(null,context) method is invoked;
 *     It's expected that NullPointerException is thrown.
 *   TestCase 3:
 *     doWithServerContext(runnable,context) method is invoked;
 *     where
 *       runnable is an object that implements Runnable interface;
 *           in the run() method of this object the first context element in the
 *           supplied server context collection which is an instance of the type
 *           FakeType is obtained using
 *           {@link net.jini.export.ServerContext#getServerContextElement(Class)};
 *       context is the server context collection to set;
 *           this collection contains FakeType object.
 *     It's expected that the run() method of the object is performed in
 *     the specified context.
 *
 * Infrastructure:
 *     - {@link DoWithServerContext}
 *         performs actions
 *     - {@link org.apache.river.test.spec.export.util.FakeType}
 *         used as an element in a context
 *
 * Actions:
 *   Test performs the following steps:
 *     - creating FakeType object;
 *     - creating server context collection with the created FakeType object.
 *   Then test performs the following steps in each test case:
 *     - invoking doWithServerContext() method;
 *     - comparing result of the invocation with the expected result.
 *
 * </pre>
 */
public class DoWithServerContext extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * Context element of the type
     * {@link org.apache.river.test.spec.export.util.FakeType}
     */
    FakeType cxtElement;

    /**
     * Returned context element of the type
     * {@link org.apache.river.test.spec.export.util.FakeType}
     *
     */
    FakeType retCnxtElement;

    /**
     * Context to set by doWithServerContext() method.
     */
    ArrayList context;

    /**
     * This method performs all preparations.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Create context element */
        cxtElement = new FakeType("Fake Context Element");

        /* Create server context collection with the created element */
        context = new ArrayList();
        context.add(cxtElement);
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {

        /* TestCase #1 */
        logger.log(Level.FINE, "\n\t+++++ doWithServerContext(runnable, null)");

        try {
            ServerContext.doWithServerContext(new Runnable() {

                public void run() {}
            }
            , (Collection) null);
        } catch (NullPointerException e) {
            logger.log(Level.FINE, "Exception has been thrown: " + e);
        } catch (Exception e) {
            throw new TestException(
                    "" + " test failed:: "
                    + "Exception has been thrown: " + e);
        }

        /* TestCase #2 */
        logger.log(Level.FINE, "\n\t+++++ doWithServerContext(null, context)");

        try {
            ServerContext.doWithServerContext(null, (Collection) context);
            throw new TestException(
                    "" + " test failed:: "
                    + "No exception has been thrown");
        } catch (NullPointerException e) {
            logger.log(Level.FINE, "Exception has been thrown: " + e);
        } catch (Exception e) {
            throw new TestException(
                    "" + " test failed:: "
                    + "Exception has been thrown: " + e);
        }

        /* TestCase #3 */
        logger.log(Level.FINE,
                "\n\t+++++ doWithServerContext(runnable, context)");

        ServerContext.doWithServerContext(new Runnable() {

            public void run() {
                Object retCnxtElem = null;

                try {
                    retCnxtElem =
                            ServerContext.getServerContextElement(
                                    FakeType.class);
                } catch (ServerNotActiveException e) {}

                retCnxtElement = (FakeType) retCnxtElem;
            }
        }
        , (Collection) context);
        logger.log(Level.FINE,
                "Expected server context element: " + cxtElement);
        logger.log(Level.FINE,
                "Returned server context element: " + retCnxtElement);

        if (!retCnxtElement.equals(cxtElement)) {
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }
}
