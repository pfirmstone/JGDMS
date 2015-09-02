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

// java.rmi
import java.rmi.Remote;
import java.rmi.server.ExportException;

// net.jini
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ProxyTrustExporter;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.security.proxytrust.util.AbstractTestBase;
import org.apache.river.test.spec.security.proxytrust.util.ValidMainExporter;
import org.apache.river.test.spec.security.proxytrust.util.ValidBootExporter;
import org.apache.river.test.spec.security.proxytrust.util.SPTRemoteObject;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Export method throws IllegalStateException if the export of either remote
 *     object throws IllegalStateException.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     ISEExporter - exporter whose export method always throws
 *             IllegalStateException
 *     ValidMainExporter - exporter which implements all needed for mainExporter
 *             parameter interfaces
 *     ValidBootExporter - exporter which implements all needed for bootExporter
 *             parameter interfaces
 *     SPTRemoteObject - normal remote object which implements ServerProxyTrust
 *             interface
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustExporter1 with ValidMainExporter and
 *        ISEExporter as parameters
 *     2) invoke export method of constructed ProxyTrustExporter1 with
 *        SPTRemoteObject as a parameter
 *     3) assert that IllegalStateException will be thrown
 *     4) construct ProxyTrustExporter2 with ISEExporter and
 *        ValidBootExporter as parameters
 *     5) invoke export method of constructed ProxyTrustExporter2 with
 *        SPTRemoteObject as a parameter
 *     6) assert that IllegalStateException will be thrown
 * </pre>
 */
public class Export_IllegalStateExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        Exporter me = new ValidMainExporter();
        Exporter be = new ISEExporter();
        Remote ro = new SPTRemoteObject();
        ProxyTrustExporter pte = createPTE(me, be);

        try {
            pte.export(ro);

            // FAIL
            throw new TestException(
                    "export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter did not throw any exception "
                    + "while IllegalStateException was expected.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.fine("export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter threw IllegalStateException "
                    + "as expected.");
        }
        me = new ISEExporter();
        be = new ValidBootExporter();
        pte = createPTE(me, be);

        try {
            pte.export(ro);

            // FAIL
            throw new TestException(
                    "export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter did not throw any exception "
                    + "while IllegalStateException was expected.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.fine("export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter threw IllegalStateException "
                    + "as expected.");
        }
    }


    /**
     * Exporter whose export method always throws IllegalStateException
     */
    class ISEExporter implements Exporter {

        /**
         * Always throws IllegalStateException.
         */
        public Remote export(Remote impl) throws ExportException {
            throw new IllegalStateException("Test exception.");
        }

        /**
         * Method from Exporter interface. Does nothing.
         *
         * @return false
         */
        public boolean unexport(boolean force) {
            return false;
        }
    }
}
