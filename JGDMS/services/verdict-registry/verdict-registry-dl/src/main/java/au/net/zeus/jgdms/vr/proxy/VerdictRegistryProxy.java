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
package au.net.zeus.jgdms.vr.proxy;

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Set;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.Stateless;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for the {@link VerdictRegistry} service.
 *
 * <p>This class is downloaded to client JVMs and forwards all
 * {@link VerdictRegistry} method calls to the remote server-side
 * implementation over a JERI transport channel.
 *
 * <p>The proxy is {@link AtomicSerial} and extends {@link AbstractSmartProxy},
 * which provides safe deserialization, {@link net.jini.export.ProxyAccessor},
 * and {@link net.jini.id.ReferentUuid} identity based on the service UUID.
 *
 * <p>Use the {@link #create(VerdictRegistry, Uuid)} factory method
 * rather than constructing directly; the factory automatically returns a
 * {@link ConstrainableVerdictRegistryProxy} when the server stub implements
 * {@link RemoteMethodControl}.
 *
 * @see VerdictRegistry
 * @see AbstractSmartProxy
 * @see ConstrainableVerdictRegistryProxy
 * @since 3.1.1
 */
@AtomicSerial
@Stateless  // no own serialized state; server + proxyID live on AbstractSmartProxy
public abstract class VerdictRegistryProxy
        extends AbstractSmartProxy
        implements VerdictRegistry {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method — ALWAYS returns a
     * {@link ConstrainableVerdictRegistryProxy}, and fails closed when the
     * server stub does not implement {@link RemoteMethodControl}.  This class is
     * {@code abstract} so the constrainable form is the only concrete wire proxy
     * — a client that requested Integrity/ServerAuthentication/Confidentiality
     * can never be handed a plain proxy that silently dropped them.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     * @return the constrainable proxy instance
     * @throws IllegalArgumentException if {@code server} does not implement
     *         {@link RemoteMethodControl}
     */
    public static AbstractSmartProxy create(VerdictRegistry server, Uuid proxyID) {
        if (!(server instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                    "service must be exported with a constrainable endpoint: "
                    + "server does not implement RemoteMethodControl");
        }
        // Preserve the constraints already configured on the exported stub;
        // passing null would call setConstraints(null) and discard them.
        MethodConstraints serverConstraints =
                ((RemoteMethodControl) server).getConstraints();
        return new ConstrainableVerdictRegistryProxy(
                server, proxyID, serverConstraints);
    }

    /**
     * Creates a new proxy wrapping the given server stub.  Only invoked by
     * subclass constructors — this class is abstract.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     */
    protected VerdictRegistryProxy(VerdictRegistry server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.  Only chained to by the
     * concrete subclass — this class is abstract and is never itself a wire
     * instance.
     *
     * @param arg the deserialization argument bag
     * @throws IOException if deserialization validation fails
     */
    protected VerdictRegistryProxy(GetArg arg) throws IOException, ClassNotFoundException {
        super(arg);
    }

    @Override
    public void registerAnalysisEngine(String engineId,
                                        PublicKey engineKey,
                                        String sigAlgorithm) throws RemoteException {
        ((VerdictRegistry) server).registerAnalysisEngine(engineId, engineKey, sigAlgorithm);
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        ((VerdictRegistry) server).revokeAnalysisEngine(engineId);
    }

    @Override
    public void submitReport(String engineId, JarAnalysisReport report) throws RemoteException {
        ((VerdictRegistry) server).submitReport(engineId, report);
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        ((VerdictRegistry) server).reportCrash(report);
    }

    @Override
    public void reportPinning(PinningReport report) throws RemoteException {
        ((VerdictRegistry) server).reportPinning(report);
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        return ((VerdictRegistry) server).getVerdict(codebaseUrls);
    }

    @Override
    public RegistryVerdict getVerdictByHash(String contentHash) throws RemoteException {
        return ((VerdictRegistry) server).getVerdictByHash(contentHash);
    }

    @Override
    public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                     Set<Uri> codebaseUrls,
                                                     MarshalledInstance handback,
                                                     long leaseDuration)
            throws RemoteException {
        return ((VerdictRegistry) server).registerVerdictListener(
                listener, codebaseUrls, handback, leaseDuration);
    }

    @Override
    public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener,
                                                           MarshalledInstance handback,
                                                           long leaseDuration)
            throws RemoteException {
        return ((VerdictRegistry) server).registerGlobalVerdictListener(
                listener, handback, leaseDuration);
    }

    @Override
    public long renewEventLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException {
        return ((VerdictRegistry) server).renewEventLease(leaseId, duration);
    }

    @Override
    public void cancelEventLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException {
        ((VerdictRegistry) server).cancelEventLease(leaseId);
    }

    // =========================================================================
    // Nested class: ConstrainableVerdictRegistryProxy
    // =========================================================================

    /**
     * Constrainable subclass of {@link VerdictRegistryProxy}.
     *
     * <p>Extends {@link AbstractSmartProxy.ConstrainableSmartProxy} to
     * provide full {@link RemoteMethodControl} support.  Instances are
     * produced by the {@link VerdictRegistryProxy#create} factory when the
     * server stub implements {@link RemoteMethodControl}.
     *
     * @since 3.1.1
     */
    @AtomicSerial
    @Stateless  // no own serialized state
    public static final class ConstrainableVerdictRegistryProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements VerdictRegistry {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new constrained proxy.
         *
         * @param server      the remote server stub
         * @param proxyID     the service's stable unique identifier
         * @param constraints the client method constraints; may be {@code null}
         */
        public ConstrainableVerdictRegistryProxy(VerdictRegistry server,
                                          Uuid proxyID,
                                          MethodConstraints constraints) {
            super(server, proxyID, constraints);
        }

        /**
         * {@link AtomicSerial} deserialization constructor.
         *
         * @param arg the deserialization argument bag
         * @throws IOException if deserialization validation fails
         */
        public ConstrainableVerdictRegistryProxy(GetArg arg) throws IOException, ClassNotFoundException {
            super(arg);
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableVerdictRegistryProxy(
                    (VerdictRegistry) server, getReferentUuid(), constraints);
        }

        @Override
        public void registerAnalysisEngine(String engineId,
                                           PublicKey engineKey,
                                           String sigAlgorithm) throws RemoteException {
            ((VerdictRegistry) server).registerAnalysisEngine(engineId, engineKey, sigAlgorithm);
        }

        @Override
        public void revokeAnalysisEngine(String engineId) throws RemoteException {
            ((VerdictRegistry) server).revokeAnalysisEngine(engineId);
        }

        @Override
        public void submitReport(String engineId, JarAnalysisReport report) throws RemoteException {
            ((VerdictRegistry) server).submitReport(engineId, report);
        }

        @Override
        public void reportCrash(CrashReport report) throws RemoteException {
            ((VerdictRegistry) server).reportCrash(report);
        }

        @Override
        public void reportPinning(PinningReport report) throws RemoteException {
            ((VerdictRegistry) server).reportPinning(report);
        }

        @Override
        public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
            return ((VerdictRegistry) server).getVerdict(codebaseUrls);
        }

        @Override
        public RegistryVerdict getVerdictByHash(String contentHash) throws RemoteException {
            return ((VerdictRegistry) server).getVerdictByHash(contentHash);
        }

        @Override
        public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                         Set<Uri> codebaseUrls,
                                                         MarshalledInstance handback,
                                                         long leaseDuration)
                throws RemoteException {
            return ((VerdictRegistry) server).registerVerdictListener(
                    listener, codebaseUrls, handback, leaseDuration);
        }

        @Override
        public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener,
                                                               MarshalledInstance handback,
                                                               long leaseDuration)
                throws RemoteException {
            return ((VerdictRegistry) server).registerGlobalVerdictListener(
                    listener, handback, leaseDuration);
        }

        @Override
        public long renewEventLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, RemoteException {
            return ((VerdictRegistry) server).renewEventLease(leaseId, duration);
        }

        @Override
        public void cancelEventLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException {
            ((VerdictRegistry) server).cancelEventLease(leaseId);
        }
    }
}
