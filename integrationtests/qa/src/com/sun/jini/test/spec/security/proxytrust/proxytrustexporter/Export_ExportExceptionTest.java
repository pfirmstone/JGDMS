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
import java.rmi.RemoteException;
import java.rmi.server.ExportException;

// net.jini
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustExporter;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ValidMainExporter;
import com.sun.jini.test.spec.security.proxytrust.util.ValidBootExporter;
import com.sun.jini.test.spec.security.proxytrust.util.SPTRemoteObject;
import com.sun.jini.test.spec.security.proxytrust.util.RMCImpl;
import com.sun.jini.test.spec.security.proxytrust.util.RMCTEImpl;
import com.sun.jini.test.spec.security.proxytrust.util.RMCPTImpl;
import com.sun.jini.test.spec.security.proxytrust.util.PTTEImpl;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Export method throws ExportException if the export of either remote
 *     object throws ExportException, or if the main proxy is not an instance
 *     of both RemoteMethodControl and TrustEquivalence, or if the main proxy
 *     is an instance of a non-public class that implements a non-public
 *     interface, or if the bootstrap proxy is not an instance of ProxyTrust,
 *     RemoteMethodControl, and TrustEquivalence.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TENonRMCMainExporter - exporter whose export method returns proxy which
 *             implements TrustEquivalence but does not implement
 *             RemoteMethodControl interface
 *     TENonTEMainExporter - exporter whose export method returns proxy which
 *             implements RemoteMethodControl but does not implement
 *             TrustEquivalence interface
 *     RMCTENonPublicIntMainExporter - non-public exporter whose export method
 *             returns proxy which implements RemoteMethodControl,
 *             TrustEquivalence and non-public interface
 *     ExportExceptionExporter - exporter whose export method always throws
 *             ExportException
 *     RMCTENonPTBootExporter - exporter whose export method returns proxy which
 *             implements RemoteMethodControl and TrustEquivalence interfaces
 *             but does not implement ProxyTrust interface
 *     PTTENonRMCBootExporter - exporter whose export method returns proxy which
 *             implements ProxyTrust nad TrustEquivalence interfaces but does
 *             not implement RemoteMethodControl interface
 *     RMCPTNonTEBootExporter - exporter whose export method returns proxy which
 *             implements RemoteMethodControl and ProxyTrust interfaces
 *             but does not implement TrustEquivalence interface
 *     ValidMainExporter - exporter which implements all needed for mainExporter
 *             parameter interfaces
 *     ValidBootExporter - exporter which implements all needed for bootExporter
 *             parameter interfaces
 *     SPTRemoteObject - normal remote object which implements ServerProxyTrust
 *             interface
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustExporter with TENonRMCMainExporter,
 *        TENonTEMainExporter, RMCTENonPublicIntMainExporter and
 *        ExportExceptionExporter subsequently for mainExporter parameter and
 *        ValidBootExporter for bootExporter parameter
 *     2) invoke export method of constructed ProxyTrustExporter with
 *        SPTRemoteObject as a parameter
 *     3) assert that ExportException will be thrown
 *     4) construct ProxyTrustExporter with ValidMainExporter for mainExporter
 *        parameter and RMCTENonPTBootExporter, PTTENonRMCBootExporter,
 *        RMCPTNonTEBootExporter and ExportExceptionExporter subsequently for
 *        bootExporter parameter
 *     5) invoke export method of constructed ProxyTrustExporter with
 *        SPTRemoteObject as a parameter
 *     6) assert that ExportException will be thrown
 * </pre>
 */
public class Export_ExportExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustExporter pte;
        Exporter be = new ValidBootExporter();
        Remote sre = new SPTRemoteObject();
        Exporter[] exp = new Exporter[] {
            new TENonRMCMainExporter(),
            new RMCNonTEMainExporter(),
            new RMCTENonPublicIntMainExporter(),
            new ExportExceptionExporter() };

        for (int i = 0; i < exp.length; ++i) {
            pte = createPTE(exp[i], be);

            try {
                pte.export(sre);

                // FAIL
                throw new TestException(
                        "export(" + sre + ") method of constructed "
                        + "ProxyTrustExporter did not throw any exception "
                        + "while ExportException was expected.");
            } catch (ExportException ee) {
                // PASS
                logger.fine("export(" + sre + ") method of constructed "
                        + "ProxyTrustExporter threw ExportException "
                        + "as expected.");
            }
        }
        Exporter me = new ValidMainExporter();
        exp = new Exporter[] {
            new RMCTENonPTBootExporter(),
            new PTTENonRMCBootExporter(),
            new RMCPTNonTEBootExporter(),
            new ExportExceptionExporter() };

        for (int i = 0; i < exp.length; ++i) {
            pte = createPTE(me, exp[i]);

            try {
                pte.export(sre);

                // FAIL
                throw new TestException(
                        "export(" + sre + ") method of constructed "
                        + "ProxyTrustExporter did not throw any exception "
                        + "while ExportException was expected.");
            } catch (ExportException ee) {
                // PASS
                logger.fine("export(" + sre + ") method of constructed "
                        + "ProxyTrustExporter threw ExportException "
                        + "as expected.");
            }
        }
    }


    /**
     * Exporter whose export method returns proxy which implements
     * TrustEquivalence but does not implement RemoteMethodControl interface.
     */
    class TENonRMCMainExporter implements Exporter {

        /**
         * Returns proxy which implements TrustEquivalence does not implement
         * RemoteMethodControl interface.
         *
         * @return proxy which implements TrustEquivalence does not implement
         *         RemoteMethodControl interface
         */
        public Remote export(Remote impl) throws ExportException {
            return new PTTEImpl();
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


    /**
     * Exporter whose export method returns proxy which implements
     * RemoteMethodControl but does not implement TrustEquivalence interface.
     */
    class RMCNonTEMainExporter implements Exporter {

        /**
         * Returns proxy which implements RemoteMethodControl does not implement
         * TrustEquivalence interface.
         *
         * @return proxy which implements RemoteMethodControl does not implement
         *         TrustEquivalence interface
         */
        public Remote export(Remote impl) throws ExportException {
            return new RMCImpl();
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


    /**
     * Non-public exporter whose export method returns proxy which implements
     * RemoteMethodControl, TrustEquivalence and non-public interface.
     */
    private class RMCTENonPublicIntMainExporter implements Exporter {

        /**
         * Returns proxy which implements RemoteMethodControl, TrustEquivalence
         * and non-public interface.
         *
         * @return proxy which implements RemoteMethodControl, TrustEquivalence
         *         and non-public interface
         */
        public Remote export(Remote impl) throws ExportException {
            return new RMCTENPIImpl();
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


    /**
     * Exporter whose export method always throws ExportException
     */
    class ExportExceptionExporter implements Exporter {

        /**
         * Always throws ExportException.
         */
        public Remote export(Remote impl) throws ExportException {
            throw new ExportException("Test exception.");
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


    /**
     * Exporter whose export method returns proxy which implements
     * RemoteMethodControl and TrustEquivalence but does not implement
     * ProxyTrust interface.
     */
    class RMCTENonPTBootExporter implements Exporter {

        /**
         * Returns proxy which implements RemoteMethodControl and
         * TrustEquivalence but does not implement ProxyTrust interface.
         *
         * @return proxy which implements RemoteMethodControl and
         *         TrustEquivalence but does not implement ProxyTrust interface
         */
        public Remote export(Remote impl) throws ExportException {
            return new RMCTEImpl();
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


    /**
     * Exporter whose export method returns proxy which implements ProxyTrust
     * and TrustEquivalence but does not implement RemoteMethodControl
     * interface.
     */
    class PTTENonRMCBootExporter implements Exporter {

        /**
         * Returns proxy which implements ProxyTrust and TrustEquivalence but
         * does not implement RemoteMethodControl interface.
         *
         * @return proxy which implements ProxyTrust and TrustEquivalence but
         *         does not implement RemoteMethodControl interface
         */
        public Remote export(Remote impl) throws ExportException {
            return new PTTEImpl();
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


    /**
     * Exporter whose export method returns proxy which implements
     * RemoteMethodControl and ProxyTrust but does not implement
     * TrustEquivalence interface.
     */
    class RMCPTNonTEBootExporter implements Exporter {

        /**
         * Returns proxy which implements RemoteMethodControl and ProxyTrust
         * but does not implement TrustEquivalence interface.
         *
         * @return proxy which implements RemoteMethodControl and ProxyTrust
         *         but does not implement TrustEquivalence interface
         */
        public Remote export(Remote impl) throws ExportException {
            return new RMCPTImpl();
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


    /**
     * Non-public interface.
     */
    private interface NonPublicInterface {}


    /**
     * Auxiliary remote class implementing RemoteMethodControl, TrustEquivalence
     * and NonPublicInterface interfaces.
     */
    class RMCTENPIImpl extends RMCTEImpl implements NonPublicInterface {}
}
