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


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
 *   method.
 *   doWithServerContext() method sets the server context for the current thread
 *   to the supplied context collection and ...
 *   When this method returns, the thread's server context is unset.
 *
 * Infrastructure:
 *   - {@link DoWithServerContext_UnsetContext}
 *       performs actions
 *   - {@link com.sun.jini.test.spec.export.util.FakeType}
 *       used as an element in a context
 *
 * Actions:
 *   Test performs the following steps:
 *     - creating context to set with the FakeType object;
 *     - invoking doWithServerContext() method for executing {@link java.lang.Runnable}
 *       object within the created context (run() method of the Runnable object
 *       invokes {@link net.jini.export.ServerContext#getServerContext()} and
 *       verifies that no exceptions are thrown);
 *     - invoking ServerContext.getServerContext() and verifying that
 *       {@link java.rmi.server.ServerNotActiveException} is thrown, that
 *       proves that thread's server context is unset after doWithServerContext()
 *       method returns.
 *
 * </pre>
 */
public class DoWithServerContext_UnsetContext extends QATest {
    QAConfig config;

    /**
     * Server context collection to set by means of
     * {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)}
     * method.
     */
    ArrayList context;

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

        /* Create server context collection */
        context = new ArrayList();
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {

        /*
         * Verify that server context is set within doWithServerContext(),
         * i.e. no exception is thrown while getServerContext()
         */
        logger.log(Level.FINE, "\nVerify that server context is set within "
                + "doWithServerContext(), i.e. no exception is thrown while "
                + "getServerContext()");
        logger.log(Level.FINE,
                "\t+++++ doWithServerContext(runnable, context)");
        ServerContext.doWithServerContext(new Runnable() {

            public void run() {

                try {
                    logger.log(Level.FINE, "\t\t+++++ getServerContext()");
                    ServerContext.getServerContext();
                    logger.log(Level.FINE, "Server context is set and no "
                            + "exception is thrown");
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

        /*
         * Verify that server context is unset when
         * doWithServerContext() method returns,
         * i.e. ServerNotActiveException exception is thrown while
         * getServerContext()
         */
        logger.log(Level.FINE, "\nVerify that server context is unset when "
                + "doWithServerContext() method returns, i.e. "
                + "ServerNotActiveException exception is thrown while "
                + "getServerContext()");
        try {
            logger.log(Level.FINE, "\t+++++ getServerContext()");
            ServerContext.getServerContext();
            throw new TestException(
                    "" + " test failed:: "
                    + "Server context is set and no exception is thrown");
        } catch (ServerNotActiveException e) {
            logger.log(Level.FINE, "getServerContext() has thrown: " + e);
        } catch (Exception e) {
            throw new TestException(
                    "" + " test failed:: "
                    + "getServerContext() has thrown: " + e);
        }
        
        return;
    }
}
