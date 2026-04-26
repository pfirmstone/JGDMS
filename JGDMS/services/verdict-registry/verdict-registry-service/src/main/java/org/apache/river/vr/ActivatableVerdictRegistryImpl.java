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
package org.apache.river.vr;

import java.rmi.RemoteException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Set;
import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.codebase.CrashReport;
import org.apache.river.api.codebase.RegistryVerdict;
import org.apache.river.api.codebase.SignedVerdict;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.net.Uri;
import org.apache.river.config.Config;
import org.apache.river.service.support.AbstractJiniService;
import org.apache.river.service.support.JiniServiceParameters;
import org.apache.river.start.lifecycle.LifeCycle;
import org.apache.river.vr.proxy.VerdictRegistryProxy;

/**
 * Activatable, Jini-aware wrapper around {@link VerdictRegistryImpl}.
 *
 * <p>This class adds the full Jini service infrastructure to the core
 * {@link VerdictRegistryImpl}: it reads all configuration from a Jini
 * {@link Configuration}, exports itself via a Jini exporter, builds a
 * {@link VerdictRegistryProxy} for clients, and registers with lookup
 * services.  All infrastructure boilerplate is inherited from
 * {@link AbstractJiniService}.
 *
 * <p>Service-specific configuration is validated eagerly by
 * {@link VrServiceParameters} before the service object is constructed:
 * if any mandatory entry is missing or invalid a
 * {@link ConfigurationException} is thrown immediately, rather than being
 * swallowed and re-thrown later in {@code start()}.
 *
 * <h2>Activatable constructor</h2>
 * The {@code (ActivationID, String[])} constructor satisfies the Phoenix
 * activation-group contract and is used when the service is re-activated
 * after a JVM restart.
 *
 * <h2>Non-activatable constructor</h2>
 * The {@code (String[], LifeCycle)} constructor supports the
 * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}
 * framework for transient deployments.
 *
 * <h2>Configuration component</h2>
 * All entries are read from component {@value #COMPONENT}.
 * Common infrastructure entries (exporter, lookup groups/locators/attributes,
 * codebase fields) are documented in {@link JiniServiceParameters}.
 * Service-specific mandatory entries are:
 * <ul>
 *   <li>{@code registryPrivateKey} ({@link PrivateKey}) — the registry signing key</li>
 *   <li>{@code registrySigAlgorithm} ({@link String}) — JCA algorithm for registry
 *       signatures (e.g. {@code "SHA256withRSA"})</li>
 *   <li>{@code phoenixPublicKey} ({@link PublicKey}) — Phoenix public key for
 *       {@link CrashReport} verification</li>
 *   <li>{@code phoenixSigAlgorithm} ({@link String}) — JCA algorithm used by Phoenix</li>
 *   <li>{@code quorumMinimum} ({@code int}, default {@code 1}) — minimum independent
 *       SAFE verdicts before a SAFE {@link RegistryVerdict} is issued</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @see VerdictRegistryImpl
 * @see VerdictRegistry
 * @see JiniServiceParameters
 * @see AbstractJiniService
 * @since 3.1.1
 */
public class ActivatableVerdictRegistryImpl
        extends AbstractJiniService
        implements VerdictRegistry {

    /** Configuration component name for this service. */
    static final String COMPONENT = "org.apache.river.vr";

    /** The core implementation to which all VerdictRegistry calls are delegated. */
    private final VerdictRegistryImpl impl;

    // -------------------------------------------------------------------------
    // Public constructors
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.  Required by Phoenix: this signature is called
     * automatically by the activation group when the service is activated.
     *
     * @param activationID the activation ID assigned by the activation system
     * @param data         configuration arguments used by
     *                     {@link ConfigurationProvider#getInstance}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing, null, or of the wrong type
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableVerdictRegistryImpl(ActivationID activationID,
                                          String[] data)
            throws Exception {
        this(new VrServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             ActivatableVerdictRegistryImpl.class.getClassLoader()),
                     activationID),
             null);
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments passed to
     *                   {@link ConfigurationProvider#getInstance}
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing, null, or of the wrong type
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableVerdictRegistryImpl(String[] configArgs,
                                          LifeCycle lifeCycle)
            throws Exception {
        this(new VrServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             ActivatableVerdictRegistryImpl.class.getClassLoader()),
                     null),
             lifeCycle);
    }

    /**
     * Internal constructor: receives pre-validated parameters so that field
     * assignments are unconditionally safe.
     */
    private ActivatableVerdictRegistryImpl(VrServiceParameters params,
                                           LifeCycle lifeCycle) {
        super(params, lifeCycle);
        this.impl = new VerdictRegistryImpl(
                params.registryPrivateKey,
                params.registrySigAlgorithm,
                params.phoenixPublicKey,
                params.phoenixSigAlgorithm,
                params.quorumMinimum);
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    /**
     * Provides the exported server stub to the core {@link VerdictRegistryImpl}
     * so it can populate event sources and build leases correctly.
     */
    @Override
    protected void onExported(Object stub) throws RemoteException {
        impl.setEventSource((VerdictRegistry) stub);
    }

    @Override
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        return new VerdictRegistryProxy((VerdictRegistry) stub, serviceUuid);
    }

    @Override
    protected Class<?>[] getServiceInterfaces() {
        return new Class<?>[]{ VerdictRegistry.class };
    }

    // -------------------------------------------------------------------------
    // VerdictRegistry — delegate all calls to the core implementation
    // -------------------------------------------------------------------------

    @Override
    public void registerAnalysisEngine(String engineId,
                                       PublicKey engineKey,
                                       String sigAlgorithm) throws RemoteException {
        getReadyState().check();
        impl.registerAnalysisEngine(engineId, engineKey, sigAlgorithm);
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        getReadyState().check();
        impl.revokeAnalysisEngine(engineId);
    }

    @Override
    public void submitVerdict(String engineId,
                              SignedVerdict verdict) throws RemoteException {
        getReadyState().check();
        impl.submitVerdict(engineId, verdict);
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        getReadyState().check();
        impl.reportCrash(report);
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        getReadyState().check();
        return impl.getVerdict(codebaseUrls);
    }

    @Override
    public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                     Set<Uri> codebaseUrls,
                                                     MarshalledInstance handback,
                                                     long leaseDuration)
            throws RemoteException {
        getReadyState().check();
        return impl.registerVerdictListener(listener, codebaseUrls, handback, leaseDuration);
    }

    @Override
    public long renewEventLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException {
        getReadyState().check();
        return impl.renewEventLease(leaseId, duration);
    }

    @Override
    public void cancelEventLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException {
        getReadyState().check();
        impl.cancelEventLease(leaseId);
    }

    // -------------------------------------------------------------------------
    // Parameter object
    // -------------------------------------------------------------------------

    /**
     * Parameter object for {@link ActivatableVerdictRegistryImpl}.
     *
     * <p>Reads and validates all mandatory service-specific configuration
     * entries during construction.  Any {@link ConfigurationException} is
     * thrown immediately, before the service object is created.
     */
    public static final class VrServiceParameters extends JiniServiceParameters {

        /** Registry private key for signing {@link RegistryVerdict} objects. */
        public final PrivateKey registryPrivateKey;

        /** JCA algorithm name for registry signatures (e.g. {@code "SHA256withRSA"}). */
        public final String registrySigAlgorithm;

        /** Phoenix public key used to verify {@link CrashReport} signatures. */
        public final PublicKey phoenixPublicKey;

        /** JCA algorithm name used by Phoenix to sign {@link CrashReport} objects. */
        public final String phoenixSigAlgorithm;

        /** Minimum independent SAFE verdicts required before issuing a SAFE result. */
        public final int quorumMinimum;

        /**
         * Reads VR-specific configuration entries after delegating common
         * entries to {@link JiniServiceParameters}.
         *
         * @param config       the Jini configuration; must be non-null
         * @param activationID the Phoenix activation ID, or {@code null} for
         *                     non-activatable deployments
         * @throws ConfigurationException if any mandatory entry is missing,
         *                                null, or of the wrong type
         */
        public VrServiceParameters(Configuration config,
                                   ActivationID activationID)
                throws ConfigurationException {
            super(config, COMPONENT, activationID, VerdictRegistry.class);

            this.registryPrivateKey = (PrivateKey) Config.getNonNullEntry(
                    config, COMPONENT, "registryPrivateKey", PrivateKey.class);

            this.registrySigAlgorithm = Config.getNonNullEntry(
                    config, COMPONENT, "registrySigAlgorithm", String.class);

            this.phoenixPublicKey = (PublicKey) Config.getNonNullEntry(
                    config, COMPONENT, "phoenixPublicKey", PublicKey.class);

            this.phoenixSigAlgorithm = Config.getNonNullEntry(
                    config, COMPONENT, "phoenixSigAlgorithm", String.class);

            this.quorumMinimum = Config.getIntEntry(
                    config, COMPONENT, "quorumMinimum", 1, 1, Integer.MAX_VALUE);
        }
    }
}
