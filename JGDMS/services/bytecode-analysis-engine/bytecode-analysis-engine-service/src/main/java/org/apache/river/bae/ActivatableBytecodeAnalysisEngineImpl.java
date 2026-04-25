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

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.PrivateKey;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.activation.ActivationExporter;
import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.api.codebase.BytecodeAnalysisEngine;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.net.Uri;
import org.apache.river.api.util.Startable;
import org.apache.river.bae.proxy.BytecodeAnalysisEngineProxy;
import org.apache.river.config.Config;
import org.apache.river.proxy.CodebaseProvider;
import org.apache.river.start.lifecycle.LifeCycle;
import org.apache.river.thread.ReadyState;

/**
 * Activatable, Jini-aware wrapper around {@link BytecodeAnalysisEngineImpl}.
 *
 * <p>This class adds the full Jini service infrastructure to the core
 * {@link BytecodeAnalysisEngineImpl}: it reads all configuration from a Jini
 * {@link Configuration}, exports itself via a Jini {@link Exporter},
 * builds a {@link BytecodeAnalysisEngineProxy} for clients, and registers
 * with lookup services via a {@link JoinManager}.
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
 * All configuration entries are read from the component
 * {@value #COMPONENT}:
 * <ul>
 *   <li>{@code enginePrivateKey} ({@link PrivateKey}) — the engine's signing
 *       key; mandatory</li>
 *   <li>{@code engineSigAlgorithm} ({@link String}) — JCA algorithm for
 *       engine signatures (e.g. {@code "SHA256withRSA"}); mandatory</li>
 *   <li>{@code engineId} ({@link String}) — stable identifier under which
 *       this engine is registered with the {@link VerdictRegistry};
 *       mandatory</li>
 *   <li>{@code verdictRegistry} ({@link VerdictRegistry}) — remote reference
 *       to the registry; mandatory</li>
 *   <li>{@code serverExporter} ({@link Exporter}) — used to export this
 *       service; defaults to a {@link BasicJeriExporter} over TCP</li>
 *   <li>{@code initialLookupGroups} ({@code String[]}, default
 *       {@code {""}}) — initial lookup groups to join</li>
 *   <li>{@code initialLookupLocators}
 *       ({@link net.jini.core.discovery.LookupLocator}[], default empty)
 *       — initial lookup locators to join</li>
 *   <li>{@code initialLookupAttributes} ({@link Entry}[], default empty)
 *       — additional lookup attributes</li>
 *   <li>{@code Codebase_Annotation} ({@link String}, default {@code ""})
 *       — codebase annotation for the proxy class</li>
 *   <li>{@code Codebase_CertFactoryType} ({@link String}, default
 *       {@code "X.509"})</li>
 *   <li>{@code Codebase_CertPathEncoding} ({@link String}, default
 *       {@code "PkiPath"})</li>
 *   <li>{@code Codebase_Certs} ({@code byte[]}, default empty array)
 *       — DER-encoded certificate path</li>
 * </ul>
 *
 * @see BytecodeAnalysisEngineImpl
 * @see BytecodeAnalysisEngine
 * @since 3.1.1
 */
public class ActivatableBytecodeAnalysisEngineImpl
        implements BytecodeAnalysisEngine,
                   ProxyAccessor,
                   Startable,
                   CodebaseAccessor,
                   ServiceProxyAccessor,
                   ServiceAttributesAccessor,
                   ServiceIDAccessor {

    /** Configuration component name for this service. */
    static final String COMPONENT = "org.apache.river.bae";

    private static final Logger logger =
            Logger.getLogger(ActivatableBytecodeAnalysisEngineImpl.class.getName());

    // -------------------------------------------------------------------------
    // Service infrastructure fields
    // -------------------------------------------------------------------------

    /** The core implementation to which all calls are delegated. */
    private final BytecodeAnalysisEngineImpl impl;

    /** The Jini exporter used to export this service. */
    private final Exporter exporter;

    /**
     * The exported server stub.  Set by {@link #start()}.
     */
    private volatile BytecodeAnalysisEngine serverStub;

    /**
     * The smart proxy handed to clients via lookup.  Set by
     * {@link #start()}.
     */
    private volatile BytecodeAnalysisEngineProxy outerProxy;

    /** Service identity, generated once and reused across restarts. */
    private volatile ServiceID serviceId;

    /** Lookup attributes published to Jini lookup services. */
    private final Entry[] lookupAttrs;

    /**
     * Manages discovery and join.  {@code null} until the service is started.
     */
    private volatile JoinManager joiner;

    /** LifeCycle callback for non-activatable deployments; may be {@code null}. */
    private final LifeCycle lifeCycle;

    /** Guards access to the service: rejects calls before start or after shutdown. */
    private final ReadyState readyState = new ReadyState();

    /** Ensures start() is idempotent. */
    private boolean started = false;

    /** Any exception thrown inside the constructor for re-throw in start(). */
    private final Throwable constructorFailure;

    // CodebaseAccessor fields
    private final String codebase;
    private final String certFactoryType;
    private final String certPathEncoding;
    private final byte[] encodedCerts;

    // Lookup-discovery configuration
    private final String[] initialLookupGroups;
    private final net.jini.core.discovery.LookupLocator[] initialLookupLocators;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.  Required by Phoenix: this signature is
     * called automatically by the activation group when the service is
     * activated.
     *
     * @param activationID the activation ID assigned by the activation system
     * @param data         configuration arguments used by
     *                     {@link ConfigurationProvider#getInstance}
     * @throws Exception if construction fails
     */
    public ActivatableBytecodeAnalysisEngineImpl(ActivationID activationID,
                                                  String[] data) throws Exception {
        this(ConfigurationProvider.getInstance(
                     data,
                     ActivatableBytecodeAnalysisEngineImpl.class.getClassLoader()),
             activationID,
             null);
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments passed to
     *                   {@link ConfigurationProvider#getInstance}
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws Exception if construction fails
     */
    public ActivatableBytecodeAnalysisEngineImpl(String[] configArgs,
                                                  LifeCycle lifeCycle) throws Exception {
        this(ConfigurationProvider.getInstance(
                     configArgs,
                     ActivatableBytecodeAnalysisEngineImpl.class.getClassLoader()),
             null,
             lifeCycle);
    }

    /**
     * Internal constructor called by both public constructors after the
     * {@link Configuration} has been resolved.
     */
    private ActivatableBytecodeAnalysisEngineImpl(Configuration config,
                                                   ActivationID activationID,
                                                   LifeCycle lifeCycle) throws Exception {
        this.lifeCycle = lifeCycle;

        Throwable failure = null;
        BytecodeAnalysisEngineImpl coreImpl = null;
        Exporter exp = null;
        Entry[] attrs = null;
        String[] groups = null;
        net.jini.core.discovery.LookupLocator[] locators = null;
        String cb = "";
        String certFactory = "X.509";
        String certPathEnc = "PkiPath";
        byte[] certs = new byte[0];

        try {
            // --- Mandatory configuration ---
            PrivateKey enginePrivateKey = (PrivateKey) Config.getNonNullEntry(
                    config, COMPONENT, "enginePrivateKey", PrivateKey.class);

            String engineSigAlgorithm = Config.getNonNullEntry(
                    config, COMPONENT, "engineSigAlgorithm", String.class);

            String engineId = Config.getNonNullEntry(
                    config, COMPONENT, "engineId", String.class);

            VerdictRegistry verdictRegistry = (VerdictRegistry) Config.getNonNullEntry(
                    config, COMPONENT, "verdictRegistry", VerdictRegistry.class);

            // --- Build the core implementation ---
            coreImpl = new BytecodeAnalysisEngineImpl(
                    enginePrivateKey, engineSigAlgorithm, engineId, verdictRegistry);

            // --- Exporter ---
            if (activationID != null) {
                exp = Config.getNonNullEntry(
                        config, COMPONENT, "serverExporter", Exporter.class,
                        new ActivationExporter(
                                activationID,
                                new BasicJeriExporter(
                                        TcpServerEndpoint.getInstance(0),
                                        new AtomicILFactory(
                                                null, null,
                                                BytecodeAnalysisEngine.class.getClassLoader()),
                                        false, true)),
                        activationID);
            } else {
                exp = Config.getNonNullEntry(
                        config, COMPONENT, "serverExporter", Exporter.class,
                        new BasicJeriExporter(
                                TcpServerEndpoint.getInstance(0),
                                new AtomicILFactory(
                                        null, null,
                                        BytecodeAnalysisEngine.class.getClassLoader()),
                                false, true));
            }

            // --- Lookup-service discovery configuration ---
            groups = Config.getNonNullEntry(
                    config, COMPONENT, "initialLookupGroups",
                    String[].class, new String[]{""});

            locators = Config.getNonNullEntry(
                    config, COMPONENT, "initialLookupLocators",
                    net.jini.core.discovery.LookupLocator[].class,
                    new net.jini.core.discovery.LookupLocator[0]);

            attrs = Config.getNonNullEntry(
                    config, COMPONENT, "initialLookupAttributes",
                    Entry[].class, new Entry[0]);

            // --- CodebaseAccessor fields ---
            cb = Config.getNonNullEntry(
                    config, COMPONENT, "Codebase_Annotation", String.class, "");
            certFactory = Config.getNonNullEntry(
                    config, COMPONENT, "Codebase_CertFactoryType", String.class, "X.509");
            certPathEnc = Config.getNonNullEntry(
                    config, COMPONENT, "Codebase_CertPathEncoding", String.class, "PkiPath");
            certs = Config.getNonNullEntry(
                    config, COMPONENT, "Codebase_Certs", byte[].class, new byte[0]);

        } catch (Exception e) {
            failure = e;
        }

        this.impl                  = coreImpl;
        this.exporter              = exp;
        this.lookupAttrs           = attrs != null ? attrs.clone() : new Entry[0];
        this.initialLookupGroups   = groups != null ? groups.clone() : new String[]{""};
        this.initialLookupLocators = locators != null ? locators.clone()
                : new net.jini.core.discovery.LookupLocator[0];
        this.codebase              = cb;
        this.certFactoryType       = certFactory;
        this.certPathEncoding      = certPathEnc;
        this.encodedCerts          = certs != null ? certs.clone() : new byte[0];
        this.constructorFailure    = failure;
    }

    // -------------------------------------------------------------------------
    // Startable
    // -------------------------------------------------------------------------

    /**
     * Exports this service, creates the client-side proxy, generates or
     * restores the {@link ServiceID}, and joins Jini lookup services.
     *
     * <p>This method is idempotent: subsequent invocations return immediately
     * without repeating any initialisation.
     *
     * @throws Exception if export or discovery setup fails
     */
    @Override
    public synchronized void start() throws Exception {
        if (started) return;
        started = true;

        if (constructorFailure != null) {
            if (constructorFailure instanceof Exception) {
                throw (Exception) constructorFailure;
            }
            throw new RuntimeException(constructorFailure);
        }

        serverStub = (BytecodeAnalysisEngine) exporter.export(this);
        logger.log(Level.CONFIG, "BytecodeAnalysisEngine exported: {0}", serverStub);

        outerProxy = new BytecodeAnalysisEngineProxy(serverStub);

        if (serviceId == null) {
            net.jini.id.Uuid uuid = net.jini.id.UuidFactory.generate();
            serviceId = new ServiceID(
                    uuid.getMostSignificantBits(),
                    uuid.getLeastSignificantBits());
            logger.log(Level.CONFIG, "Generated ServiceID: {0}", serviceId);
        }

        LookupDiscoveryManager ldm = new LookupDiscoveryManager(
                initialLookupGroups, initialLookupLocators, null);
        joiner = new JoinManager(outerProxy, lookupAttrs, serviceId, ldm, null);
        logger.log(Level.INFO,
                "BytecodeAnalysisEngine started, serviceId={0}", serviceId);

        readyState.ready();
    }

    // -------------------------------------------------------------------------
    // ProxyAccessor
    // -------------------------------------------------------------------------

    @Override
    public Object getProxy() {
        return serverStub;
    }

    // -------------------------------------------------------------------------
    // ServiceProxyAccessor
    // -------------------------------------------------------------------------

    @Override
    public Object getServiceProxy() throws RemoteException {
        readyState.check();
        return outerProxy;
    }

    // -------------------------------------------------------------------------
    // ServiceAttributesAccessor
    // -------------------------------------------------------------------------

    @Override
    public Entry[] getServiceAttributes() throws IOException {
        readyState.check();
        JoinManager jm = joiner;
        if (jm != null) {
            return jm.getAttributes();
        }
        return lookupAttrs.clone();
    }

    // -------------------------------------------------------------------------
    // ServiceIDAccessor
    // -------------------------------------------------------------------------

    @Override
    public ServiceID serviceID() throws IOException {
        readyState.check();
        return serviceId;
    }

    // -------------------------------------------------------------------------
    // CodebaseAccessor
    // -------------------------------------------------------------------------

    @Override
    public String getClassAnnotation() throws IOException {
        return (codebase == null || codebase.isEmpty())
                ? CodebaseProvider.getClassAnnotation(BytecodeAnalysisEngine.class)
                : codebase;
    }

    @Override
    public String getCertFactoryType() throws IOException {
        return certFactoryType;
    }

    @Override
    public String getCertPathEncoding() throws IOException {
        return certPathEncoding;
    }

    @Override
    public byte[] getEncodedCerts() throws IOException {
        return encodedCerts.clone();
    }

    // -------------------------------------------------------------------------
    // BytecodeAnalysisEngine — delegated to core impl
    // -------------------------------------------------------------------------

    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        readyState.check();
        impl.requestAnalysis(codebaseUrls);
    }
}
