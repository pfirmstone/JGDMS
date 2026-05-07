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
package au.net.zeus.jgdms.downloader;

import au.net.zeus.jgdms.api.codebase.CodebaseDownloader;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.downloader.CodebaseDownloaderImpl.EngineEntry;
import au.net.zeus.jgdms.service.support.AbstractJiniService;
import au.net.zeus.jgdms.service.support.JiniServiceParameters;
import java.io.IOException;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceItem;
import net.jini.id.Uuid;
import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscoveryManager;
import org.apache.river.api.net.Uri;
import org.apache.river.config.Config;
import org.apache.river.start.lifecycle.LifeCycle;

/**
 * Activatable, Jini-aware wrapper around {@link CodebaseDownloaderImpl}.
 *
 * <p>This class adds the full Jini service infrastructure to the core
 * {@link CodebaseDownloaderImpl}: it reads all configuration from a Jini
 * {@link Configuration}, exports itself via a Jini exporter, builds a
 * lightweight admin proxy for clients, and registers with lookup services.
 * All infrastructure boilerplate is inherited from {@link AbstractJiniService}.
 *
 * <h2>Activatable constructor</h2>
 * The {@code (ActivationID, String[])} constructor satisfies the Phoenix
 * activation-group contract.
 *
 * <h2>Non-activatable constructor</h2>
 * The {@code (String[], LifeCycle)} constructor supports
 * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
 *
 * <h2>Service discovery</h2>
 * After export, a {@link ServiceDiscoveryManager} is started to watch for
 * new Jini service registrations in the configured monitoring lookup
 * groups/locators.  When a service that implements
 * {@link net.jini.export.CodebaseAccessor} is discovered, its codebase
 * annotation is parsed into individual URIs and enqueued for
 * download/analysis in the core {@link CodebaseDownloaderImpl}.
 *
 * <h2>Configuration component</h2>
 * All entries are read from component {@value #COMPONENT}.
 * Common infrastructure entries are documented in
 * {@link JiniServiceParameters}.  Service-specific entries are:
 * <ul>
 *   <li>{@code baePool} ({@link EngineEntry}[], mandatory) — the BAE
 *       pool; each element pairs an engine ID with its remote proxy</li>
 *   <li>{@code verdictRegistry} ({@link VerdictRegistry}, mandatory) —
 *       the registry to which signed reports are submitted</li>
 *   <li>{@code maxJarSizeBytes} ({@code int},
 *       default {@link CodebaseDownloaderImpl#DEFAULT_MAX_JAR_SIZE_BYTES})
 *       — upper bound on downloaded JAR size</li>
 *   <li>{@code connectTimeoutMs} ({@code int},
 *       default {@link CodebaseDownloaderImpl#DEFAULT_CONNECT_TIMEOUT_MS})
 *       — HTTP connection timeout</li>
 *   <li>{@code readTimeoutMs} ({@code int},
 *       default {@link CodebaseDownloaderImpl#DEFAULT_READ_TIMEOUT_MS})
 *       — HTTP read timeout</li>
 *   <li>{@code workerThreads} ({@code int},
 *       default {@link CodebaseDownloaderImpl#DEFAULT_WORKER_THREADS})
 *       — number of parallel download threads</li>
 *   <li>{@code shutdownTimeoutMs} ({@code long}, default {@code 30000})
 *       — graceful-shutdown wait</li>
 *   <li>{@code monitorLookupGroups} ({@code String[]},
 *       default {@code {""}}) — lookup groups to monitor for new services</li>
 *   <li>{@code monitorLookupLocators} ({@link LookupLocator}[],
 *       default empty) — lookup locators to monitor</li>
 *   <li>{@code initialCodebaseUrls} ({@code String[]}, default empty) —
 *       codebase URLs to submit immediately at startup</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @see CodebaseDownloaderImpl
 * @see CodebaseDownloader
 * @see JiniServiceParameters
 * @see AbstractJiniService
 * @since 3.1.1
 */
public class ActivatableCodebaseDownloaderImpl
        extends AbstractJiniService
        implements CodebaseDownloader {

    private static final Logger logger =
            Logger.getLogger(ActivatableCodebaseDownloaderImpl.class.getName());

    /** Configuration component name for this service. */
    static final String COMPONENT = "au.net.zeus.jgdms.downloader";

    /** The core implementation to which all work is delegated. */
    private final CodebaseDownloaderImpl impl;

    /** Maximum time to wait for in-flight tasks during graceful shutdown. */
    private final long shutdownTimeoutMs;

    /** Lookup groups to monitor for new service registrations. */
    private final String[] monitorLookupGroups;

    /** Lookup locators to monitor for new service registrations. */
    private final LookupLocator[] monitorLookupLocators;

    /** Initial codebase URLs to submit immediately after start. */
    private final String[] initialCodebaseUrls;

    /**
     * Discovery manager used to find lookup services for monitoring.
     * Created in {@link #onExported(Object)}; terminated in
     * {@link #destroy()}.
     */
    private volatile LookupDiscoveryManager monitorLdm;

    /**
     * Service discovery manager used to detect new service registrations.
     * Created in {@link #onExported(Object)}; terminated in
     * {@link #destroy()}.
     */
    private volatile ServiceDiscoveryManager monitorSdm;

    /**
     * Lookup cache that drives {@link ServiceDiscoveryListenerImpl}.
     * Terminated via {@link ServiceDiscoveryManager#terminate()} in
     * {@link #destroy()}.
     */
    @SuppressWarnings("unused")
    private volatile LookupCache monitorCache;

    // -------------------------------------------------------------------------
    // Public constructors
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.
     *
     * @param activationID the activation ID assigned by Phoenix
     * @param data         configuration arguments
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing, null, or of the wrong type
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableCodebaseDownloaderImpl(ActivationID activationID,
                                              String[] data)
            throws Exception {
        this(new CdServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             ActivatableCodebaseDownloaderImpl.class.getClassLoader()),
                     activationID),
             null);
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing, null, or of the wrong type
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableCodebaseDownloaderImpl(String[] configArgs,
                                              LifeCycle lifeCycle)
            throws Exception {
        this(new CdServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             ActivatableCodebaseDownloaderImpl.class.getClassLoader()),
                     null),
             lifeCycle);
    }

    /**
     * Internal constructor: receives pre-validated parameters so that field
     * assignments are unconditionally safe.
     */
    private ActivatableCodebaseDownloaderImpl(CdServiceParameters params,
                                               LifeCycle lifeCycle)
            throws IOException {
        super(params, lifeCycle);

        List<EngineEntry> pool = Arrays.asList(params.baePool);
        this.impl = new CodebaseDownloaderImpl(
                pool,
                params.verdictRegistry,
                params.maxJarSizeBytes,
                params.connectTimeoutMs,
                params.readTimeoutMs,
                params.workerThreads);
        this.shutdownTimeoutMs    = params.shutdownTimeoutMs;
        this.monitorLookupGroups  = params.monitorLookupGroups.clone();
        this.monitorLookupLocators = params.monitorLookupLocators.clone();
        this.initialCodebaseUrls  = params.initialCodebaseUrls.clone();
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    /**
     * Called after the service stub has been exported.
     *
     * <p>Starts the service-discovery watcher and submits any initial
     * codebase URLs configured via {@code initialCodebaseUrls}.
     */
    @Override
    protected void onExported(Object stub) throws RemoteException {
        startServiceDiscovery();
        submitInitialUrls();
    }

    @Override
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        // The CodebaseDownloader is not a client-facing service; there is
        // no smart proxy.  The raw server stub is returned so that operators
        // can invoke submitForAnalysis() for administrative purposes.
        return stub;
    }

    @Override
    protected Class<?>[] getServiceInterfaces() {
        return new Class<?>[]{ CodebaseDownloader.class };
    }

    // -------------------------------------------------------------------------
    // CodebaseDownloader
    // -------------------------------------------------------------------------

    @Override
    public void submitForAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        getReadyState().check();
        impl.submitForAnalysis(codebaseUrls);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Terminates service discovery, waits for in-flight download tasks to
     * complete, then delegates to {@link AbstractJiniService#destroy()}.
     */
    @Override
    public synchronized void destroy() {
        terminateServiceDiscovery();
        try {
            impl.shutdown(shutdownTimeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.destroy();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Starts the {@link LookupDiscoveryManager} and
     * {@link ServiceDiscoveryManager} used to monitor lookup services for
     * new service registrations.
     *
     * <p>Failures are logged but do not cause {@link #onExported} to throw,
     * so the service remains fully functional for manually submitted URLs.
     */
    private void startServiceDiscovery() {
        try {
            monitorLdm = new LookupDiscoveryManager(
                    monitorLookupGroups, monitorLookupLocators, null);
            monitorSdm = new ServiceDiscoveryManager(monitorLdm, null);
            monitorCache = monitorSdm.createLookupCache(
                    new ServiceTemplate(null, null, null),
                    null,
                    new ServiceDiscoveryListenerImpl());
            logger.info("Codebase Downloader: service discovery started.");
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "Failed to start service discovery; will only process "
                            + "manually submitted URLs.", e);
        }
    }

    /**
     * Terminates the service-discovery infrastructure if it was started.
     */
    private void terminateServiceDiscovery() {
        ServiceDiscoveryManager sdm = monitorSdm;
        if (sdm != null) {
            try {
                sdm.terminate();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING,
                        "Error terminating ServiceDiscoveryManager", e);
            }
        }
        LookupDiscoveryManager ldm = monitorLdm;
        if (ldm != null) {
            try {
                ldm.terminate();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING,
                        "Error terminating LookupDiscoveryManager", e);
            }
        }
    }

    /**
     * Parses {@link #initialCodebaseUrls} and submits them to the download
     * pipeline.
     */
    private void submitInitialUrls() {
        if (initialCodebaseUrls.length == 0) return;
        Set<Uri> uris = new LinkedHashSet<>();
        for (String urlStr : initialCodebaseUrls) {
            if (urlStr == null || urlStr.isEmpty()) continue;
            try {
                uris.add(new Uri(urlStr));
            } catch (URISyntaxException e) {
                logger.log(Level.WARNING,
                        "Invalid initial codebase URL: " + urlStr, e);
            }
        }
        if (!uris.isEmpty()) {
            impl.submitForAnalysis(uris);
        }
    }

    // -------------------------------------------------------------------------
    // Inner listener
    // -------------------------------------------------------------------------

    /**
     * Forwards newly discovered service items to the
     * {@link CodebaseDownloaderImpl}.
     */
    private final class ServiceDiscoveryListenerImpl
            implements ServiceDiscoveryListener {

        @Override
        public void serviceAdded(ServiceDiscoveryEvent e) {
            ServiceItem item = e.getPostEventServiceItem();
            if (item != null) {
                impl.onServiceItem(item);
            }
        }

        @Override
        public void serviceRemoved(ServiceDiscoveryEvent e) {
            // Removal events do not affect the analysis pipeline.
        }

        @Override
        public void serviceChanged(ServiceDiscoveryEvent e) {
            // Re-inspect on attribute change: codebase annotation may have
            // been updated (e.g. after a service restart at a new URL).
            ServiceItem item = e.getPostEventServiceItem();
            if (item != null) {
                impl.onServiceItem(item);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Parameter object
    // -------------------------------------------------------------------------

    /**
     * Parameter object for {@link ActivatableCodebaseDownloaderImpl}.
     *
     * <p>Reads and validates all mandatory and optional service-specific
     * configuration entries during construction.
     */
    public static final class CdServiceParameters extends JiniServiceParameters {

        /** The BAE pool: each element associates an engine ID with its proxy. */
        public final EngineEntry[] baePool;

        /** Registry to which signed analysis reports are submitted. */
        public final VerdictRegistry verdictRegistry;

        /** Upper bound on downloaded JAR size in bytes. */
        public final int maxJarSizeBytes;

        /** HTTP connection-establishment timeout in milliseconds. */
        public final int connectTimeoutMs;

        /** HTTP socket read timeout in milliseconds. */
        public final int readTimeoutMs;

        /** Number of parallel download worker threads. */
        public final int workerThreads;

        /** Maximum time to wait for in-flight tasks during graceful shutdown. */
        public final long shutdownTimeoutMs;

        /** Lookup groups to monitor for new service registrations. */
        public final String[] monitorLookupGroups;

        /** Lookup locators to monitor for new service registrations. */
        public final LookupLocator[] monitorLookupLocators;

        /** Codebase URLs to submit immediately at startup. */
        public final String[] initialCodebaseUrls;

        /**
         * Reads downloader-specific configuration entries after delegating
         * common entries to {@link JiniServiceParameters}.
         *
         * @param config       the Jini configuration; must be non-null
         * @param activationID the Phoenix activation ID, or {@code null}
         *                     for non-activatable deployments
         * @throws ConfigurationException if any mandatory entry is missing,
         *                                null, or of the wrong type
         */
        public CdServiceParameters(Configuration config,
                                    ActivationID activationID)
                throws ConfigurationException {
            super(config, COMPONENT, activationID, CodebaseDownloader.class);

            // Mandatory: the BAE pool.  Each element must be an EngineEntry.
            Object[] rawPool = (Object[]) Config.getNonNullEntry(
                    config, COMPONENT, "baePool", Object[].class);
            List<EngineEntry> pool = new ArrayList<>(rawPool.length);
            for (Object o : rawPool) {
                if (!(o instanceof EngineEntry)) {
                    throw new ConfigurationException(
                            COMPONENT + ".baePool elements must be "
                                    + EngineEntry.class.getName()
                                    + ", got: " + (o == null ? "null" : o.getClass().getName()));
                }
                pool.add((EngineEntry) o);
            }
            if (pool.isEmpty()) {
                throw new ConfigurationException(
                        COMPONENT + ".baePool must not be empty");
            }
            this.baePool = pool.toArray(new EngineEntry[0]);

            // Mandatory: verdict registry.
            this.verdictRegistry = (VerdictRegistry) Config.getNonNullEntry(
                    config, COMPONENT, "verdictRegistry", VerdictRegistry.class);

            // Optional tuning parameters with documented defaults.
            this.maxJarSizeBytes = Config.getIntEntry(
                    config, COMPONENT, "maxJarSizeBytes",
                    CodebaseDownloaderImpl.DEFAULT_MAX_JAR_SIZE_BYTES,
                    1, Integer.MAX_VALUE);

            this.connectTimeoutMs = Config.getIntEntry(
                    config, COMPONENT, "connectTimeoutMs",
                    CodebaseDownloaderImpl.DEFAULT_CONNECT_TIMEOUT_MS,
                    0, Integer.MAX_VALUE);

            this.readTimeoutMs = Config.getIntEntry(
                    config, COMPONENT, "readTimeoutMs",
                    CodebaseDownloaderImpl.DEFAULT_READ_TIMEOUT_MS,
                    0, Integer.MAX_VALUE);

            this.workerThreads = Config.getIntEntry(
                    config, COMPONENT, "workerThreads",
                    CodebaseDownloaderImpl.DEFAULT_WORKER_THREADS,
                    1, Integer.MAX_VALUE);

            this.shutdownTimeoutMs = Config.getLongEntry(
                    config, COMPONENT, "shutdownTimeoutMs",
                    30_000L, 1L, Long.MAX_VALUE);

            this.monitorLookupGroups = Config.getNonNullEntry(
                    config, COMPONENT, "monitorLookupGroups",
                    String[].class, new String[]{""}).clone();

            this.monitorLookupLocators = Config.getNonNullEntry(
                    config, COMPONENT, "monitorLookupLocators",
                    LookupLocator[].class, new LookupLocator[0]).clone();

            this.initialCodebaseUrls = Config.getNonNullEntry(
                    config, COMPONENT, "initialCodebaseUrls",
                    String[].class, new String[0]).clone();
        }
    }
}
