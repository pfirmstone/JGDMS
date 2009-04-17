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
 *   {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
 *   method.
 *   doWithServerContext() method sets the server context for the current thread
 *   to the supplied context collection and ...
 *   If a server context is currently set for the current thread, that server
 *   context cannot be reset; that is, a server context cannot be overwritten
 *   or nested. If a server context is already set for the current thread, an
 *   IllegalStateException is thrown.
 *   Throws:
 *     {@link java.lang.IllegalStateException} - if the context is already
 *                                               set for this thread
 *
 * Infrastructure:
 *   - {@link DoWithServerContext_IllegalStateException}
 *       performs actions
 *   - {@link com.sun.jini.test.spec.export.util.FakeType}
 *       used as an element in a context
 *   - {@link com.sun.jini.test.spec.export.util.AnFakeType}
 *       used as an element in a context
 *
 * Actions:
 *   - set server context containing an element of type FakeType and launch
 *     in this context run() method of the supplied {@link java.lang.Runnable}
 *     object by means of ServerContext.doWithServerContext() method;
 *   - in the run() method of the {@link java.lang.Runnable} object attempt
 *     to invoke ServerContext.doWithServerContext() with a supplied nested
 *     server context containing an element of type AnFakeType; verify that the
 *     appropriate IllegalStateException is thrown;
 *   - in the same run() method invoke
 *     ServerContext.getServerContextElement(AnFakeType.class); verify that
 *     ServerContext.getServerContextElement(AnFakeType.class) returns null
 *     after the (failed) attempt to set the nested server context (that proves
 *     that the second server context did not overwrite the first one).
 *   - in the same run() method invoke
 *     ServerContext.getServerContextElement(FakeType.class); verify that
 *     ServerContext.getServerContextElement(FakeType.class) returns the same
 *     element in the original server context, not null (that proves that the
 *     attempt to set a nested server context didn't erase the first one set).
 *
 * </pre>
 */
public class DoWithServerContext_IllegalStateException extends QATest {
    QAConfig config;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.FakeType}
     */
    FakeType cxtElement;

    /**
     * Server context element of the type
     * {@link com.sun.jini.test.spec.export.util.AnFakeType}
     */
    AnFakeType anCxtElement;

    /**
     * Server context element returned by
     * {@link net.jini.export.ServerContext#getServerContextElement(Class)
     * method.
     */
    Object retCnxtElement;

    /**
     * Server context collection to set by means of
     * {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
     * method.
     */
    ArrayList context;

    /**
     * Nested server context collection to set by means of
     * {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
     * method.
     */
    ArrayList ancontext;

    /**
     * String that describes the test result.
     */
    String testResult = null;

    /**
     * This method performs all preparations.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Create server context elements of different types */
        cxtElement = new FakeType("Fake Context Element");
        anCxtElement = new AnFakeType(1234567890);

        /* Create server context collections with the created elements */
        context = new ArrayList();
        context.add(cxtElement);
        
        ancontext = new ArrayList();
        ancontext.add(anCxtElement);
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.FINE,
                "\n\t+++++ doWithServerContext(runnable, context)");

        ServerContext.doWithServerContext(new Runnable() {

            public void run() {

                /*
                 * Verify that server context can't be overwritten with
                 * the nested server context.
                 */
                logger.log(Level.FINE,
                        "\n\t+++++ Attempt to overwrite server context "
                        + "with the supplied nested server context");

                try {
                    logger.log(Level.FINE,
                            "\n\t\t+++++ doWithServerContext(runnable, "
                            + "ancontext)");
                    ServerContext.doWithServerContext(new Runnable() {

                        public void run() {}
                    }
                    , (Collection) ancontext);
                    testResult = "Server context has been overwritten: "
                            + "expected IllegalStateException, "
                            + "but no exception has been thrown";
                    Thread.currentThread().interrupt();
                } catch (IllegalStateException e) {
                    logger.log(Level.FINE,
                            "IllegalStateException has been thrown while "
                            + "attempt to set a nested server context");
                } catch (Exception e) {
                    testResult = "Server context hasn't been overwritten: "
                            + "expected IllegalStateException, " + "but \""
                            + e + "\" has been thrown";
                    Thread.currentThread().interrupt();
                }

                logger.log(Level.FINE,
                        "\n\t+++++ Attempt to get server context "
                        + "element of type AnFakeType");

                try {
                    logger.log(Level.FINE,
                            "\n\t\t+++++ getServerContextElement("
                            + "AnFakeType.class)");
                    retCnxtElement =
                            ServerContext.getServerContextElement(
                            AnFakeType.class);
                    if (retCnxtElement != null) {
                        testResult = "Server context has been overwritten; "
                                + "there is an element of type AnFakeType: "
                                + retCnxtElement;
                        Thread.currentThread().interrupt();
                    }
                    logger.log(Level.FINE,
                            "Server context hasn't been overwritten; "
                            + "no element of type AnFakeType; "
                            + "getServerContextElement(AnFakeType.class) "
                            + "returned: null");
                } catch (ServerNotActiveException e) {
                    testResult =
                            "Unexpected ServerNotActiveException has been "
                            + "thrown while getServerContextElement("
                            + "AnFakeType.class)";
                    Thread.currentThread().interrupt();
                }

                /*
                 * Verify that the attempt to set a nested server context
                 * didn't erase the first one set.
                 */
                logger.log(Level.FINE,
                        "\n\t+++++ Attempt to get server context "
                        + "element of type FakeType");
                try {
                    logger.log(Level.FINE,
                            "\n\t\t+++++ getServerContextElement("
                            + "FakeType.class)");
                    retCnxtElement =
                            ServerContext.getServerContextElement(
                            FakeType.class);

                    if (!((FakeType) retCnxtElement).equals(cxtElement)) {
                        testResult =
                                "Attempt to set a nested server context "
                                + "has erased the first one set; "
                                + "getServerContextElement(FakeType.class) "
                                + "returned:" + retCnxtElement;
                        Thread.currentThread().interrupt();
                    }
                    logger.log(Level.FINE,
                            "getServerContextElement(FakeType.class) "
                            + "returned: " + retCnxtElement);
                } catch (ServerNotActiveException e) {
                    testResult =
                            "Unexpected ServerNotActiveException has been "
                            + "thrown while getServerContextElement("
                            + "FakeType.class)";
                    Thread.currentThread().interrupt();
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
