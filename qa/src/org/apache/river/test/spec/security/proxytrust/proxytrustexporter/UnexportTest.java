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
package org.apache.river.test.spec.security.proxytrust.proxytrustexporter;

import java.util.logging.Level;

// net.jini
import net.jini.security.proxytrust.ProxyTrustExporter;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.BaseExporter;
import org.apache.river.test.spec.security.proxytrust.util.UnexportTrueExporter;
import org.apache.river.test.spec.security.proxytrust.util.UnexportSameExporter;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     The unexport method of the main exporter is called with the specified
 *     argument and if that returns true, the unexport method of the bootstrap
 *     exporter is called with true. The result of the main unexport call is
 *     returned by this method.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     UnexportTrueExporter - exporter whose unexport method always returns true
 *     UnexportSameExporter - exporter whose unexport method returns the same
 *             value as parameter specified
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustExporter1 with UnexportTrueExporter as a parameter
 *        for mainExporter and UnexportSameExporter as a parameter for
 *        bootExporter
 *     2) invoke unexport method of constructed ProxyTrustExporter1
 *        with 'true' as a parameter
 *     3) assert that unexport methods of both UnexportTrueExporter and
 *        UnexportSameExporter will be invoked
 *     4) assert that true will be returned by unexport method invocation
 *     5) invoke unexport method of constructed ProxyTrustExporter1
 *        with 'false' as a parameter
 *     6) assert that unexport methods of both UnexportTrueExporter and
 *        UnexportSameExporter will be invoked
 *     7) assert that true will be returned by unexport method invocation
 *     8) construct ProxyTrustExporter2 with UnexportSameExporter as a parameter
 *        for mainExporter and UnexportTrueExporter bootExporter
 *     9) invoke unexport method of constructed ProxyTrustExporter2
 *        with 'false' as a parameter
 *     10) assert that only unexport methods of UnexportSameExporter will be
 *         invoked
 *     11) assert that false will be returned by unexport method invocation
 * </pre>
 */
public class UnexportTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        BaseExporter me = new UnexportTrueExporter();
        BaseExporter be = new UnexportSameExporter();
        ProxyTrustExporter pte = createPTE(me, be);
        boolean res = pte.unexport(true);
        logger.fine("Called unexport(true) method.");

        if (me.getUnexpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Unexport method of mainExporter was called "
                    + me.getUnexpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Unexport method of mainExporter was called 1 "
                + "time as expected.");

        if (be.getUnexpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Unexport method of bootExporter was called "
                    + be.getUnexpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Unexport method of bootExporter was called 1 "
                + "time as expected.");

        if (!res) {
            // FAIL
            throw new TestException(
                    "Unexport method of ProxyTrustExporter returned "
                    + "false while true was expected.");
        }

        // PASS
        logger.fine("Unexport method of ProxyTrustExporter returned "
                + "true as expected.");
        me.resetCounters();
        be.resetCounters();
        res = pte.unexport(false);
        logger.fine("Called unexport(false) method.");

        if (me.getUnexpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Unexport method of mainExporter was called "
                    + me.getUnexpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Unexport method of mainExporter was called 1 "
                + "time as expected.");

        if (be.getUnexpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Unexport method of bootExporter was called "
                    + be.getUnexpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Unexport method of bootExporter was called 1 "
                + "time as expected.");

        if (!res) {
            // FAIL
            throw new TestException(
                    "Unexport method of ProxyTrustExporter returned "
                    + "false while true was expected.");
        }

        // PASS
        logger.fine("Unexport method of ProxyTrustExporter returned "
                + "true as expected.");
        me = new UnexportSameExporter();
        be = new UnexportTrueExporter();
        pte = createPTE(me, be);
        res = pte.unexport(false);
        logger.fine("Called unexport(false) method.");

        if (me.getUnexpNum() != 1) {
            // FAIL
            throw new TestException(
                    "Unexport method of mainExporter was called "
                    + me.getUnexpNum() + " times while 1 was expected.");
        }

        // PASS
        logger.fine("Unexport method of mainExporter was called 1 "
                + "time as expected.");

        if (be.getUnexpNum() != 0) {
            // FAIL
            throw new TestException(
                    "Unexport method of bootExporter was called "
                    + be.getUnexpNum() + " times while 0 was expected.");
        }

        // PASS
        logger.fine("Unexport method of bootExporter was not called "
                + "as expected.");

        if (res) {
            // FAIL
            throw new TestException(
                    "Unexport method of ProxyTrustExporter returned "
                    + "true while false was expected.");
        }

        // PASS
        logger.fine("Unexport method of ProxyTrustExporter returned "
                + "false as expected.");
    }
}
