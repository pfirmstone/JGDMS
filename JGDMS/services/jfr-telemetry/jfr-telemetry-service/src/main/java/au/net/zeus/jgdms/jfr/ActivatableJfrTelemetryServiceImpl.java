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
package au.net.zeus.jgdms.jfr;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Set;
import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.id.Uuid;
import org.apache.river.api.net.Uri;
import org.apache.river.config.Config;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.telemetry.JfrTelemetryService;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import au.net.zeus.jgdms.service.annotation.JiniService;
import au.net.zeus.jgdms.service.support.AbstractJiniService;
import au.net.zeus.jgdms.service.support.JiniServiceParameters;
import org.apache.river.start.lifecycle.LifeCycle;

/**
 * Activatable, Jini-aware wrapper around {@link JfrTelemetryServiceImpl}.
 *
 * <p>This class adds the full Jini service infrastructure to the core
 * {@link JfrTelemetryServiceImpl}: it reads all configuration from a Jini
 * {@link Configuration}, exports itself via a Jini exporter, builds a
 * {@link JfrTelemetryServiceProxy} for clients, and registers with lookup
 * services.  All infrastructure boilerplate is inherited from
 * {@link AbstractJiniService}.
 *
 * <p>Service-specific configuration is validated eagerly by
 * {@link JfrServiceParameters} before the service object is constructed.
 *
 * <h2>SPIFFE identity</h2>
 * This service is provisioned with SVID
 * {@code spiffe://jgdms.example.org/host/telemetry}.  The bootstrap policy
 * ({@code SpiffePolicyFile}) restricts {@link #reportPinning} to the
 * {@code spiffe://…/client/<id>} principal pattern.
 *
 * <h2>Verdict Registry integration</h2>
 * The optional configuration entry {@code verdictRegistry} must provide a
 * pre-prepared {@link VerdictRegistry} proxy.  When set, the service will
 * submit aggregate {@link PinningReport} objects to the registry when
 * thresholds are crossed.  When absent, the service still collects telemetry
 * but only logs threshold-crossing events.
 *
 * <h2>Configuration component</h2>
 * All entries are read from component {@value #COMPONENT}.
 * Common infrastructure entries are documented in {@link JiniServiceParameters}.
 * Service-specific optional entries:
 * <ul>
 *   <li>{@code pinnedNanosThreshold} ({@code long}, default
 *       {@link JfrTelemetryServiceImpl#DEFAULT_PINNED_NANOS_THRESHOLD}) —
 *       cumulative carrier-thread-pinned nanoseconds before DANGEROUS verdict
 *       is submitted</li>
 *   <li>{@code eventCountThreshold} ({@code long}, default
 *       {@link JfrTelemetryServiceImpl#DEFAULT_EVENT_COUNT_THRESHOLD}) —
 *       event count before DANGEROUS verdict is submitted</li>
 *   <li>{@code sweepIntervalMinutes} ({@code int}, default
 *       {@link JfrTelemetryServiceImpl#DEFAULT_SWEEP_INTERVAL_MINUTES}) —
 *       minutes between aggregation-state sweeps</li>
 *   <li>{@code verdictRegistry} ({@link VerdictRegistry}, optional) — a
 *       prepared proxy for the Verdict Registry service</li>
 * </ul>
 *
 * <h2>Activatable constructor</h2>
 * The {@code (ActivationID, String[])} constructor satisfies the Phoenix
 * activation-group contract.
 *
 * <h2>Non-activatable constructor</h2>
 * The {@code (String[], LifeCycle)} constructor supports the
 * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter} framework.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @see JfrTelemetryServiceImpl
 * @see JfrTelemetryService
 * @see JiniServiceParameters
 * @see AbstractJiniService
 * @since 3.1.1
 */
@JiniService(
        api       = JfrTelemetryService.class,   // the service (remote) API interface
        component = "au.net.zeus.jgdms.jfr")     // configuration
public class ActivatableJfrTelemetryServiceImpl
        extends AbstractJiniService
        implements JfrTelemetryService {

    /** Configuration component name for this service. */
    static final String COMPONENT = "au.net.zeus.jgdms.jfr";

    /** The core implementation to which all calls are delegated. */
    private final JfrTelemetryServiceImpl impl;

    // -------------------------------------------------------------------------
    // Public constructors
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.  Required by Phoenix.
     *
     * @param activationID the activation ID assigned by the activation system
     * @param data         configuration arguments for
     *                     {@link ConfigurationProvider#getInstance}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing or invalid
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableJfrTelemetryServiceImpl(ActivationID activationID,
                                              String[] data)
            throws Exception {
        this(new JfrServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             ActivatableJfrTelemetryServiceImpl.class.getClassLoader()),
                     activationID),
             null);
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments for
     *                   {@link ConfigurationProvider#getInstance}
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing or invalid
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableJfrTelemetryServiceImpl(String[] configArgs,
                                              LifeCycle lifeCycle)
            throws Exception {
        this(new JfrServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             ActivatableJfrTelemetryServiceImpl.class.getClassLoader()),
                     null),
             lifeCycle);
    }

    /**
     * Internal constructor: receives pre-validated parameters.
     */
    private ActivatableJfrTelemetryServiceImpl(JfrServiceParameters params,
                                               LifeCycle lifeCycle)
            throws IOException {
        super(params, lifeCycle);
        this.impl = new JfrTelemetryServiceImpl(
                params.pinnedNanosThreshold,
                params.eventCountThreshold,
                params.sweepIntervalMinutes,
                params.verdictRegistry);
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    @Override
    protected void onExported(Object stub) throws RemoteException {
        impl.startSweeper();
    }

    // getServiceInterfaces() is inherited: it reads api() = { JfrTelemetryService.class }
    // from the @JiniService annotation above.

    /**
     * Shuts down the background sweeper when the service is destroyed.
     */
    @Override
    public synchronized void destroy() {
        impl.shutdown();
        super.destroy();
    }

    // -------------------------------------------------------------------------
    // JfrTelemetryService — delegate all calls to the core implementation
    // -------------------------------------------------------------------------

    @Override
    public void reportPinning(PinningReport report) throws RemoteException {
        getReadyState().check();
        impl.reportPinning(report);
    }

    @Override
    public long getPinnedNanos(Set<Uri> codebaseUrls) throws RemoteException {
        getReadyState().check();
        return impl.getPinnedNanos(codebaseUrls);
    }

    @Override
    public long getPinCount(Set<Uri> codebaseUrls) throws RemoteException {
        getReadyState().check();
        return impl.getPinCount(codebaseUrls);
    }

    // -------------------------------------------------------------------------
    // Parameter object
    // -------------------------------------------------------------------------

    /**
     * Parameter object for {@link ActivatableJfrTelemetryServiceImpl}.
     *
     * <p>Reads and validates all Jini service configuration entries.
     * Service-specific entries are optional and fall back to defaults from
     * {@link JfrTelemetryServiceImpl}.
     */
    public static final class JfrServiceParameters extends JiniServiceParameters {

        /** Cumulative pinned-ns threshold (optional; default 30 s). */
        public final long pinnedNanosThreshold;

        /** Event-count threshold (optional; default 100 events). */
        public final long eventCountThreshold;

        /** Sweep interval in minutes (optional; default 60 min). */
        public final int sweepIntervalMinutes;

        /**
         * Optional pre-prepared Verdict Registry proxy.
         * {@code null} when the entry is absent from the configuration.
         */
        public final VerdictRegistry verdictRegistry;

        /**
         * Reads JFR Telemetry Service configuration.
         *
         * @param config       the Jini configuration; must be non-null
         * @param activationID the Phoenix activation ID, or {@code null} for
         *                     non-activatable deployments
         * @throws ConfigurationException if any mandatory entry is missing or
         *                                invalid
         */
        public JfrServiceParameters(Configuration config,
                                    ActivationID activationID)
                throws ConfigurationException {
            super(config, COMPONENT, activationID, JfrTelemetryService.class);

            this.pinnedNanosThreshold = Config.getLongEntry(
                    config, COMPONENT, "pinnedNanosThreshold",
                    JfrTelemetryServiceImpl.DEFAULT_PINNED_NANOS_THRESHOLD,
                    1L,
                    Long.MAX_VALUE);

            this.eventCountThreshold = Config.getLongEntry(
                    config, COMPONENT, "eventCountThreshold",
                    JfrTelemetryServiceImpl.DEFAULT_EVENT_COUNT_THRESHOLD,
                    1L,
                    Long.MAX_VALUE);

            this.sweepIntervalMinutes = Config.getIntEntry(
                    config, COMPONENT, "sweepIntervalMinutes",
                    JfrTelemetryServiceImpl.DEFAULT_SWEEP_INTERVAL_MINUTES,
                    1,
                    Integer.MAX_VALUE);

            Object vrEntry = config.getEntry(
                    COMPONENT, "verdictRegistry",
                    VerdictRegistry.class, null);
            this.verdictRegistry = (VerdictRegistry) vrEntry;
        }
    }
}
