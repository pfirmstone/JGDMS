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
package org.apache.river.bae;

import java.rmi.RemoteException;
import java.security.PrivateKey;
import java.util.Set;
import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import org.apache.river.api.codebase.BytecodeAnalysisEngine;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.net.Uri;
import org.apache.river.bae.proxy.BytecodeAnalysisEngineProxy;
import org.apache.river.config.Config;
import org.apache.river.service.support.AbstractJiniService;
import org.apache.river.service.support.JiniServiceParameters;
import org.apache.river.start.lifecycle.LifeCycle;

/**
 * Activatable, Jini-aware wrapper around {@link BytecodeAnalysisEngineImpl}.
 *
 * <p>This class adds the full Jini service infrastructure to the core
 * {@link BytecodeAnalysisEngineImpl}: it reads all configuration from a Jini
 * {@link Configuration}, exports itself via a Jini exporter, builds a
 * {@link BytecodeAnalysisEngineProxy} for clients, and registers with lookup
 * services.  All infrastructure boilerplate is inherited from
 * {@link AbstractJiniService}.
 *
 * <p>Service-specific configuration is validated eagerly by
 * {@link BaeServiceParameters} before the service object is constructed:
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
 *   <li>{@code enginePrivateKey} ({@link PrivateKey}) \u2014 the engine's signing key</li>
 *   <li>{@code engineSigAlgorithm} ({@link String}) \u2014 JCA algorithm
 *       (e.g. {@code "SHA256withRSA"})</li>
 *   <li>{@code engineId} ({@link String}) \u2014 stable engine identifier</li>
 *   <li>{@code verdictRegistry} ({@link VerdictRegistry}) \u2014 registry
 *       to which signed verdicts are submitted</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @see BytecodeAnalysisEngineImpl
 * @see BytecodeAnalysisEngine
 * @see JiniServiceParameters
 * @see AbstractJiniService
 * @since 3.1.1
 */
public class ActivatableBytecodeAnalysisEngineImpl
        extends AbstractJiniService
        implements BytecodeAnalysisEngine {

    /** Configuration component name for this service. */
    static final String COMPONENT = "org.apache.river.bae";

    /** The core implementation to which all service calls are delegated. */
    private final BytecodeAnalysisEngineImpl impl;

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
    public ActivatableBytecodeAnalysisEngineImpl(ActivationID activationID,
                                                  String[] data)
            throws Exception {
        this(new BaeServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             ActivatableBytecodeAnalysisEngineImpl.class.getClassLoader()),
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
    public ActivatableBytecodeAnalysisEngineImpl(String[] configArgs,
                                                  LifeCycle lifeCycle)
            throws Exception {
        this(new BaeServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             ActivatableBytecodeAnalysisEngineImpl.class.getClassLoader()),
                     null),
             lifeCycle);
    }

    /**
     * Internal constructor: receives pre-validated parameters so that field
     * assignments are unconditionally safe.
     */
    private ActivatableBytecodeAnalysisEngineImpl(BaeServiceParameters params,
                                                   LifeCycle lifeCycle) {
        super(params, lifeCycle);
        this.impl = new BytecodeAnalysisEngineImpl(
                params.enginePrivateKey,
                params.engineSigAlgorithm,
                params.engineId,
                params.verdictRegistry);
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    @Override
    protected Object createProxy(Object stub) {
        return new BytecodeAnalysisEngineProxy((BytecodeAnalysisEngine) stub);
    }

    @Override
    protected Class<?> getServiceInterface() {
        return BytecodeAnalysisEngine.class;
    }

    // -------------------------------------------------------------------------
    // BytecodeAnalysisEngine -- delegated to core impl
    // -------------------------------------------------------------------------

    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        getReadyState().check();
        impl.requestAnalysis(codebaseUrls);
    }

    // -------------------------------------------------------------------------
    // Parameter object
    // -------------------------------------------------------------------------

    /**
     * Parameter object for {@link ActivatableBytecodeAnalysisEngineImpl}.
     *
     * <p>Reads and validates all mandatory service-specific configuration
     * entries during construction.  Any {@link ConfigurationException} is
     * thrown immediately, before the service object is created.
     */
    public static final class BaeServiceParameters extends JiniServiceParameters {

        /** Engine private key for signing verdicts. */
        public final PrivateKey enginePrivateKey;

        /** JCA algorithm name for engine signatures (e.g. {@code "SHA256withRSA"}). */
        public final String engineSigAlgorithm;

        /** Stable engine identifier used to register with the {@link VerdictRegistry}. */
        public final String engineId;

        /** Registry to which signed verdicts are submitted. */
        public final VerdictRegistry verdictRegistry;

        /**
         * Reads BAE-specific configuration entries after delegating common
         * entries to {@link JiniServiceParameters}.
         *
         * @param config       the Jini configuration; must be non-null
         * @param activationID the Phoenix activation ID, or {@code null} for
         *                     non-activatable deployments
         * @throws ConfigurationException if any mandatory entry is missing,
         *                                null, or of the wrong type
         */
        public BaeServiceParameters(Configuration config,
                                    ActivationID activationID)
                throws ConfigurationException {
            super(config, COMPONENT, activationID, BytecodeAnalysisEngine.class);

            this.enginePrivateKey = (PrivateKey) Config.getNonNullEntry(
                    config, COMPONENT, "enginePrivateKey", PrivateKey.class);

            this.engineSigAlgorithm = Config.getNonNullEntry(
                    config, COMPONENT, "engineSigAlgorithm", String.class);

            this.engineId = Config.getNonNullEntry(
                    config, COMPONENT, "engineId", String.class);

            this.verdictRegistry = (VerdictRegistry) Config.getNonNullEntry(
                    config, COMPONENT, "verdictRegistry", VerdictRegistry.class);
        }
    }
}
