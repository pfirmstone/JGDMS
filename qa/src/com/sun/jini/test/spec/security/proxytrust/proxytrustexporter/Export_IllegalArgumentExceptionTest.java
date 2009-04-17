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

// java.rmi
import java.rmi.Remote;
import java.rmi.server.ExportException;

// net.jini
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ProxyTrustExporter;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ValidMainExporter;
import com.sun.jini.test.spec.security.proxytrust.util.ValidBootExporter;
import com.sun.jini.test.spec.security.proxytrust.util.SPTRemoteObject;
import com.sun.jini.test.spec.security.proxytrust.util.RMCImpl;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Export method throws IllegalArgumentException if the specified remote
 *     object is not an instance of ServerProxyTrust, or if the export of 
 *     the main remote object throws IllegalArgumentException. Export method
 *     throws ExportException if export of the bootstrap remote object
 *     throws IllegalArgumentException.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     IAEExporter - exporter whose export method always throws
 *             IllegalArgumentException
 *     NonSPTRemoteObject - remote object which does not implements
 *             ServerProxyTrust interface
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
 *        IAEExporter as parameters
 *     2) invoke export method of constructed ProxyTrustExporter1 with
 *        SPTRemoteObject as a parameter
 *     3) assert that ExportException will be thrown
 *     4) construct ProxyTrustExporter2 with IAEExporter and
 *        ValidBootExporter as parameters
 *     5) invoke export method of constructed ProxyTrustExporter2 with
 *        SPTRemoteObject as a parameter
 *     6) assert that IllegalArgumentException will be thrown
 *     7) construct ProxyTrustExporter3 with ValidMainExporter and
 *        ValidBootExporter as parameters
 *     8) invoke export method of constructed ProxyTrustExporter3 with
 *        NonSPTRemoteObject as a parameter
 *     9) assert that IllegalArgumentException will be thrown
 * </pre>
 */
public class Export_IllegalArgumentExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        Exporter me = new ValidMainExporter();
        Exporter be = new IAEExporter();
        Remote ro = new SPTRemoteObject();
        ProxyTrustExporter pte = createPTE(me, be);

        try {
            pte.export(ro);

            // FAIL
            throw new TestException(
                    "export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter did not throw any exception "
                    + "while ExportException was expected.");
        } catch (ExportException iae) {
            // PASS
            logger.fine("export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter threw ExportException "
                    + "as expected.");
        }
        me = new IAEExporter();
        be = new ValidBootExporter();
        pte = createPTE(me, be);

        try {
            pte.export(ro);

            // FAIL
            throw new TestException(
                    "export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter did not throw any exception "
                    + "while IllegalArgumentException was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter threw IllegalArgumentException "
                    + "as expected.");
        }
        me = new ValidBootExporter();
        ro = new RMCImpl();
        pte = createPTE(me, be);

        try {
            pte.export(ro);

            // FAIL
            throw new TestException(
                    "export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter did not throw any exception "
                    + "while IllegalArgumentException was expected.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine("export(" + ro + ") method of constructed "
                    + "ProxyTrustExporter threw IllegalArgumentException "
                    + "as expected.");
        }
    }


    /**
     * Exporter whose export method always throws IllegalArgumentException
     */
    class IAEExporter implements Exporter {

        /**
         * Always throws IllegalArgumentException.
         */
        public Remote export(Remote impl) throws ExportException {
            throw new IllegalArgumentException("Test exception.");
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
