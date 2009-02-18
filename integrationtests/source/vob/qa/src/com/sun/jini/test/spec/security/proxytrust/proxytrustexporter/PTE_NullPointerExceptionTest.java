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
package com.sun.jini.test.spec.security.proxytrust.proxytrustexporter;

import java.util.logging.Level;

// net.jini
import net.jini.export.Exporter;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ValidMainExporter;
import com.sun.jini.test.spec.security.proxytrust.util.ValidBootExporter;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     ProxyTrustExporter constructor throws NullPointerException if any
 *     argument is null.
 *
 * Action
 *   The test performs the following steps:
 *     1) construct a ProxyTrustExporter with non-null value for mainExporter
 *        argument and null value for bootExporter argument
 *     2) assert that NullPointerException will be thrown
 *     3) construct a ProxyTrustExporter with null value for mainExporter
 *        argument and non-null value for bootExporter argument
 *     4) assert that NullPointerException will be thrown
 *     5) construct a ProxyTrustExporter with null value for mainExporter
 *        argument and null value for bootExporter argument
 *     6) assert that NullPointerException will be thrown
 * </pre>
 */
public class PTE_NullPointerExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        Exporter me = new ValidMainExporter();
        Exporter be = new ValidBootExporter();

        try {
            createPTE(me, null);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustExporter did not "
                    + "throw any exception while NullPointerException was "
                    + "expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of ProxyTrustExporter "
                    + "threw NullPointerException as expected.");
        }

        try {
            createPTE(null, be);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustExporter did not "
                    + "throw any exception while NullPointerException was "
                    + "expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of ProxyTrustExporter "
                    + "threw NullPointerException as expected.");
        }

        try {
            createPTE(null, null);

            // FAIL
            throw new TestException(
                    "Constructor invocation of ProxyTrustExporter did not "
                    + "throw any exception while NullPointerException was "
                    + "expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of ProxyTrustExporter "
                    + "threw NullPointerException as expected.");
        }
    }
}
