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

import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientSubject;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.telemetry.JfrTelemetryService;
import au.net.zeus.jgdms.api.telemetry.PinningReport;

/**
 * Core, standalone implementation of the {@link JfrTelemetryService}.
 *
 * <p>This class is a plain Java object with no Jini infrastructure
 * dependencies.  It handles:
 * <ul>
 *   <li>Per-codebase aggregation of {@link PinningReport} data received from
 *       client JVMs.</li>
 *   <li>Threshold detection: when a codebase's cumulative pinned-nanosecond
 *       total or event count crosses the configured threshold the service
 *       submits an aggregate {@link PinningReport} to the
 *       {@link VerdictRegistry}.</li>
 *   <li><strong>Per-client rate-limiting and deduplication</strong> (STD-002):
 *       the authenticated caller identity is derived from the Jini server
 *       context; each client is limited to a configurable number of accepted
 *       reports per fixed window, repeated reports for the same
 *       {@code (client, codebase)} pair within a window are dropped, and the
 *       contribution of any single report is capped.  This prevents one client
 *       from inflating a codebase's totals across the threshold by itself.</li>
 *   <li>Periodic sweep: a background daemon thread resets aggregation state
 *       at a configurable interval so that one-off pinning events do not
 *       permanently condemn a codebase.</li>
 * </ul>
 *
 * <h2>Client identity</h2>
 * The authenticated caller is obtained via
 * {@link ServerContext#getServerContextElement(Class)
 * ServerContext.getServerContextElement(ClientSubject.class)} and a stable
 * id string is derived from the returned {@link Subject}'s principal names.
 * If no client subject is available (unauthenticated, local, or not in a
 * remote call) the report is attributed to a single shared
 * {@value #ANONYMOUS_CLIENT_ID} bucket (conservative: all unauthenticated
 * callers share one rate-limit budget).
 *
 * <h2>Defaults</h2>
 * <ul>
 *   <li>Pinned-nanosecond threshold: {@value #DEFAULT_PINNED_NANOS_THRESHOLD} ns.</li>
 *   <li>Event-count threshold: {@value #DEFAULT_EVENT_COUNT_THRESHOLD} events.</li>
 *   <li>Sweep interval: {@value #DEFAULT_SWEEP_INTERVAL_MINUTES} minutes.</li>
 *   <li>Max accepted reports per client per window:
 *       {@value #DEFAULT_MAX_REPORTS_PER_CLIENT_PER_WINDOW}.</li>
 *   <li>Rate-limit window: {@value #DEFAULT_RATE_LIMIT_WINDOW_MILLIS} ms.</li>
 *   <li>Max pinned ns counted from a single report:
 *       {@value #DEFAULT_MAX_PINNED_NANOS_PER_REPORT} ns.</li>
 *   <li>Max events counted from a single report:
 *       {@value #DEFAULT_MAX_EVENTS_PER_REPORT}.</li>
 * </ul>
 *
 * @see JfrTelemetryService
 * @see ActivatableJfrTelemetryServiceImpl
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class JfrTelemetryServiceImpl implements JfrTelemetryService {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Default total pinned duration (ns) before a DANGEROUS verdict is submitted. */
    public static final long DEFAULT_PINNED_NANOS_THRESHOLD = 30_000_000_000L; // 30 s

    /** Default total event count before a DANGEROUS verdict is submitted. */
    public static final long DEFAULT_EVENT_COUNT_THRESHOLD = 100L;

    /** Default sweep interval: 60 minutes between aggregation resets. */
    public static final int DEFAULT_SWEEP_INTERVAL_MINUTES = 60;

    /**
     * Default maximum number of <em>accepted</em> reports a single client
     * identity may contribute per rate-limit window.  Reports beyond this are
     * dropped.  Chosen so that no single client can, on its own, drive a
     * codebase across {@link #DEFAULT_EVENT_COUNT_THRESHOLD} within a window
     * even at the per-report cap.
     */
    public static final int DEFAULT_MAX_REPORTS_PER_CLIENT_PER_WINDOW = 10;

    /** Default rate-limit window: 60 seconds. */
    public static final long DEFAULT_RATE_LIMIT_WINDOW_MILLIS = 60_000L;

    /**
     * Default cap on the pinned-nanosecond contribution counted from any single
     * report (5 s).  A forged report claiming an enormous duration cannot
     * contribute more than this.
     */
    public static final long DEFAULT_MAX_PINNED_NANOS_PER_REPORT = 5_000_000_000L; // 5 s

    /** Default cap on the event-count contribution counted from any single report. */
    public static final long DEFAULT_MAX_EVENTS_PER_REPORT = 10L;

    /** Bucket id used for callers with no authenticated client subject. */
    static final String ANONYMOUS_CLIENT_ID = "anonymous";

    private static final Logger logger =
            Logger.getLogger(JfrTelemetryServiceImpl.class.getName());

    // -------------------------------------------------------------------------
    // Per-codebase aggregation state
    // -------------------------------------------------------------------------

    /**
     * Holds aggregated pinning statistics for a single codebase URL set.
     * All mutable fields use atomic types; the {@code submitted} flag is
     * guarded by {@code synchronized(this)} to ensure the Verdict Registry
     * is notified at most once per successful submission per sweep window.
     */
    static final class PinningState {

        /** Canonical string form of the codebase URL set (used as map key). */
        final String codebaseKey;

        /** RFC3986 codebase URI strings in insertion order. */
        final String[] codebaseUrls;

        /** Wall-clock time (epoch ms) of the first report in this window. */
        final long windowStartMs;

        /** Cumulative carrier-thread-pinned duration in nanoseconds. */
        final AtomicLong pinnedNanos = new AtomicLong(0L);

        /** Number of {@code jdk.VirtualThreadPinned} events observed. */
        final AtomicLong eventCount  = new AtomicLong(0L);

        /**
         * Last-seen wall-clock time (epoch ms), updated atomically on each
         * accepted {@code reportPinning} call.
         */
        final AtomicLong lastSeenMs;

        /**
         * Set to {@code true} exactly once when the threshold is crossed and
         * the aggregate report has been successfully submitted to the Verdict
         * Registry.  Guarded by {@code synchronized(this)}.  Reset to
         * {@code false} if the registry is unavailable or the remote call
         * fails, allowing the next threshold crossing to retry.
         */
        volatile boolean submitted = false;

        PinningState(String codebaseKey, String[] codebaseUrls, long windowStartMs) {
            this.codebaseKey   = codebaseKey;
            this.codebaseUrls  = codebaseUrls;
            this.windowStartMs = windowStartMs;
            this.lastSeenMs    = new AtomicLong(windowStartMs);
        }
    }

    /**
     * Per-client rate-limit + dedup state for a single fixed window.
     * Guarded by {@code synchronized(this)}.
     */
    static final class ClientRateState {

        /** Epoch-ms start of the current fixed window. */
        long windowStartMs;

        /** Count of accepted reports in the current window. */
        int acceptedInWindow;

        /** Codebase keys already counted for this client in the current window. */
        final Set<String> seenCodebaseKeys = new java.util.HashSet<String>();

        ClientRateState(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Per-codebase aggregation state. Key is the canonical codebase URL set. */
    private final ConcurrentHashMap<String, PinningState> aggregates =
            new ConcurrentHashMap<String, PinningState>();

    /** Per-client rate-limit / dedup state. Key is the client identity string. */
    private final ConcurrentHashMap<String, ClientRateState> clientRates =
            new ConcurrentHashMap<String, ClientRateState>();

    /** Threshold: cumulative pinned ns before DANGEROUS verdict is submitted. */
    private final long pinnedNanosThreshold;

    /** Threshold: event count before DANGEROUS verdict is submitted. */
    private final long eventCountThreshold;

    /** Sweep interval in minutes. */
    private final int sweepIntervalMinutes;

    /** Max accepted reports per client per rate-limit window. */
    private final int maxReportsPerClientPerWindow;

    /** Rate-limit window length in milliseconds. */
    private final long rateLimitWindowMillis;

    /** Cap on pinned-ns contribution counted from any single report. */
    private final long maxPinnedNanosPerReport;

    /** Cap on event-count contribution counted from any single report. */
    private final long maxEventsPerReport;

    /**
     * The Verdict Registry to notify when a threshold is crossed.
     * May be {@code null} if the registry connection has not yet been
     * established; in that case the event is logged and the submission is
     * retried on the next threshold crossing.
     */
    private volatile VerdictRegistry verdictRegistry;

    /** Daemon executor for the periodic sweep task. */
    private final ScheduledExecutorService sweepExecutor;

    /** Handle for the scheduled sweep task, retained for graceful shutdown. */
    private ScheduledFuture<?> sweepFuture;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a service instance with default thresholds, sweep interval, and
     * per-client rate-limit / dedup settings.
     */
    public JfrTelemetryServiceImpl() {
        this(DEFAULT_PINNED_NANOS_THRESHOLD,
             DEFAULT_EVENT_COUNT_THRESHOLD,
             DEFAULT_SWEEP_INTERVAL_MINUTES,
             null);
    }

    /**
     * Creates a service instance with explicit thresholds and the default
     * per-client rate-limit / dedup settings.
     *
     * @param pinnedNanosThreshold  cumulative pinned-ns threshold; must be positive
     * @param eventCountThreshold   event-count threshold; must be positive
     * @param sweepIntervalMinutes  sweep interval in minutes; must be positive
     * @param verdictRegistry       the Verdict Registry to notify, or {@code null}
     * @throws IllegalArgumentException if any numeric argument is non-positive
     */
    public JfrTelemetryServiceImpl(long pinnedNanosThreshold,
                                   long eventCountThreshold,
                                   int  sweepIntervalMinutes,
                                   VerdictRegistry verdictRegistry) {
        this(pinnedNanosThreshold, eventCountThreshold, sweepIntervalMinutes,
             verdictRegistry,
             DEFAULT_MAX_REPORTS_PER_CLIENT_PER_WINDOW,
             DEFAULT_RATE_LIMIT_WINDOW_MILLIS,
             DEFAULT_MAX_PINNED_NANOS_PER_REPORT,
             DEFAULT_MAX_EVENTS_PER_REPORT);
    }

    /**
     * Creates a service instance with explicit thresholds and per-client
     * rate-limit / dedup settings.
     *
     * @param pinnedNanosThreshold          cumulative pinned-ns threshold; positive
     * @param eventCountThreshold           event-count threshold; positive
     * @param sweepIntervalMinutes          sweep interval in minutes; positive
     * @param verdictRegistry               the Verdict Registry, or {@code null}
     * @param maxReportsPerClientPerWindow  max accepted reports per client per
     *                                      window; positive
     * @param rateLimitWindowMillis         rate-limit window length (ms); positive
     * @param maxPinnedNanosPerReport       per-report pinned-ns cap; positive
     * @param maxEventsPerReport            per-report event-count cap; positive
     * @throws IllegalArgumentException if any numeric argument is non-positive
     */
    public JfrTelemetryServiceImpl(long pinnedNanosThreshold,
                                   long eventCountThreshold,
                                   int  sweepIntervalMinutes,
                                   VerdictRegistry verdictRegistry,
                                   int  maxReportsPerClientPerWindow,
                                   long rateLimitWindowMillis,
                                   long maxPinnedNanosPerReport,
                                   long maxEventsPerReport) {
        if (pinnedNanosThreshold <= 0)
            throw new IllegalArgumentException("pinnedNanosThreshold must be positive");
        if (eventCountThreshold <= 0)
            throw new IllegalArgumentException("eventCountThreshold must be positive");
        if (sweepIntervalMinutes <= 0)
            throw new IllegalArgumentException("sweepIntervalMinutes must be positive");
        if (maxReportsPerClientPerWindow <= 0)
            throw new IllegalArgumentException("maxReportsPerClientPerWindow must be positive");
        if (rateLimitWindowMillis <= 0)
            throw new IllegalArgumentException("rateLimitWindowMillis must be positive");
        if (maxPinnedNanosPerReport <= 0)
            throw new IllegalArgumentException("maxPinnedNanosPerReport must be positive");
        if (maxEventsPerReport <= 0)
            throw new IllegalArgumentException("maxEventsPerReport must be positive");

        this.pinnedNanosThreshold = pinnedNanosThreshold;
        this.eventCountThreshold  = eventCountThreshold;
        this.sweepIntervalMinutes = sweepIntervalMinutes;
        this.verdictRegistry      = verdictRegistry;
        this.maxReportsPerClientPerWindow = maxReportsPerClientPerWindow;
        this.rateLimitWindowMillis = rateLimitWindowMillis;
        this.maxPinnedNanosPerReport = maxPinnedNanosPerReport;
        this.maxEventsPerReport = maxEventsPerReport;

        this.sweepExecutor = new java.util.concurrent.ScheduledThreadPoolExecutor(1,
                Thread.ofVirtual().name("JGDMS-JfrTelemetryService-Sweeper").factory());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the periodic aggregation-state sweep.
     */
    void startSweeper() {
        sweepFuture = sweepExecutor.scheduleAtFixedRate(
                this::sweep,
                sweepIntervalMinutes,
                sweepIntervalMinutes,
                TimeUnit.MINUTES);
        logger.log(Level.CONFIG,
                "JFR Telemetry sweeper started; interval={0} min,"
                        + " pinnedNanosThreshold={1} ns, eventCountThreshold={2},"
                        + " maxReportsPerClientPerWindow={3}, rateLimitWindowMs={4},"
                        + " maxPinnedNanosPerReport={5}, maxEventsPerReport={6}",
                new Object[]{
                    sweepIntervalMinutes,
                    pinnedNanosThreshold,
                    eventCountThreshold,
                    maxReportsPerClientPerWindow,
                    rateLimitWindowMillis,
                    maxPinnedNanosPerReport,
                    maxEventsPerReport
                });
    }

    /**
     * Shuts down the sweep executor.
     */
    void shutdown() {
        if (sweepFuture != null) {
            sweepFuture.cancel(false);
        }
        sweepExecutor.shutdown();
        try {
            sweepExecutor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Injects (or replaces) the {@link VerdictRegistry} reference.
     *
     * @param registry the Verdict Registry; may be {@code null} to clear
     */
    void setVerdictRegistry(VerdictRegistry registry) {
        this.verdictRegistry = registry;
    }

    // -------------------------------------------------------------------------
    // JfrTelemetryService implementation
    // -------------------------------------------------------------------------

    @Override
    public void reportPinning(PinningReport report) throws RemoteException {
        if (report == null) throw new NullPointerException("report");
        reportPinning(report, currentClientId());
    }

    /**
     * Records a pinning report attributed to the given client identity.
     *
     * <p>This is the rate-limited / deduplicated core; the public
     * {@link #reportPinning(PinningReport)} derives {@code clientId} from the
     * Jini server context and delegates here.  It is package-private so that
     * unit tests can exercise the per-client limiter without a live server
     * context.
     *
     * <p>Enforcement order:
     * <ol>
     *   <li>Cap this report's contribution to
     *       {@link #maxPinnedNanosPerReport} / {@link #maxEventsPerReport}.</li>
     *   <li>Drop if the client has already been counted for this codebase in
     *       the current window (dedup).</li>
     *   <li>Drop if the client has reached its per-window report quota
     *       (rate-limit).</li>
     *   <li>Otherwise aggregate and check the codebase threshold.</li>
     * </ol>
     *
     * @param report   the pinning report; must be non-null
     * @param clientId the caller's stable identity; must be non-null
     */
    void reportPinning(PinningReport report, String clientId)
            throws RemoteException {
        if (report == null)   throw new NullPointerException("report");
        if (clientId == null) throw new NullPointerException("clientId");

        Set<Uri> urls  = report.getCodebaseUrls();
        String   key   = codebaseKey(urls);
        long     nowMs = System.currentTimeMillis();

        // 1. Cap the contribution of a single report so a forged report cannot
        //    inflate totals by itself.
        long cappedNanos  = Math.min(Math.max(0L, report.getPinnedNanos()),
                                     maxPinnedNanosPerReport);
        long cappedEvents = Math.min(Math.max(0L, report.getEventCount()),
                                     maxEventsPerReport);

        // 2 & 3. Per-client dedup + rate-limit (atomic over the client state).
        if (!admitForClient(clientId, key, nowMs)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Dropping over-limit/duplicate pinning report:"
                                + " client={0}, codebase={1}",
                        new Object[]{clientId, key});
            }
            return;
        }

        // 4. Aggregate and check threshold.
        PinningState state = aggregates.computeIfAbsent(
                key, k -> new PinningState(k, uriSetToStrings(urls), nowMs));

        long totalPinnedNanos = state.pinnedNanos.addAndGet(cappedNanos);
        long totalEventCount  = state.eventCount.addAndGet(cappedEvents);
        state.lastSeenMs.set(nowMs);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "Pinning recorded: client={0}, key={1}, addedNanos={2},"
                            + " totalPinnedNanos={3}, totalEvents={4}",
                    new Object[]{clientId, key, cappedNanos,
                        totalPinnedNanos, totalEventCount});
        }

        if (!state.submitted
                && (totalPinnedNanos >= pinnedNanosThreshold
                        || totalEventCount >= eventCountThreshold)) {
            submitToRegistry(state, totalPinnedNanos, totalEventCount, nowMs);
        }
    }

    /**
     * Applies the per-client dedup + rate-limit policy for one report.  Returns
     * {@code true} if the report should be aggregated, {@code false} if it must
     * be dropped.  The decision and the bookkeeping update are performed
     * atomically under the client's lock so concurrent reports from the same
     * client cannot both slip past the quota.
     *
     * @param clientId the caller identity
     * @param key      the codebase key
     * @param nowMs    current epoch ms
     * @return {@code true} to accept, {@code false} to drop
     */
    private boolean admitForClient(String clientId, String key, long nowMs) {
        ClientRateState rate = clientRates.computeIfAbsent(
                clientId, k -> new ClientRateState(nowMs));
        synchronized (rate) {
            // Roll the fixed window if it has elapsed.
            if (nowMs - rate.windowStartMs >= rateLimitWindowMillis) {
                rate.windowStartMs = nowMs;
                rate.acceptedInWindow = 0;
                rate.seenCodebaseKeys.clear();
            }
            // Dedup: same (client, codebase) already counted this window.
            if (rate.seenCodebaseKeys.contains(key)) {
                return false;
            }
            // Rate-limit: client has reached its per-window quota.
            if (rate.acceptedInWindow >= maxReportsPerClientPerWindow) {
                return false;
            }
            rate.acceptedInWindow++;
            rate.seenCodebaseKeys.add(key);
            return true;
        }
    }

    @Override
    public long getPinnedNanos(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        PinningState state = aggregates.get(codebaseKey(codebaseUrls));
        return state == null ? 0L : state.pinnedNanos.get();
    }

    @Override
    public long getPinCount(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        PinningState state = aggregates.get(codebaseKey(codebaseUrls));
        return state == null ? 0L : state.eventCount.get();
    }

    // -------------------------------------------------------------------------
    // Client identity
    // -------------------------------------------------------------------------

    /**
     * Derives a stable client-id string for the current remote call.
     *
     * <p>Queries the Jini server context for a {@link ClientSubject}; if one is
     * present and authenticated, joins its principal names (sorted, so order is
     * stable) into the id.  If the call is not remote, no client subject is
     * available, or the subject has no principals, returns
     * {@value #ANONYMOUS_CLIENT_ID} (a single shared bucket — conservative).
     *
     * @return a non-null, stable client identity string
     */
    String currentClientId() {
        try {
            ClientSubject cs = (ClientSubject)
                    ServerContext.getServerContextElement(ClientSubject.class);
            if (cs == null) {
                return ANONYMOUS_CLIENT_ID;
            }
            Subject subject = cs.getClientSubject();
            return clientIdFromSubject(subject);
        } catch (ServerNotActiveException e) {
            // Not in a remote call (local / test invocation).
            return ANONYMOUS_CLIENT_ID;
        } catch (SecurityException e) {
            // Not permitted to read the client subject — treat as anonymous.
            logger.log(Level.FINE,
                    "Not permitted to read client subject; using anonymous bucket",
                    e);
            return ANONYMOUS_CLIENT_ID;
        } catch (RuntimeException | Error e) {
            // Resolving the server context can fail for reasons unrelated to
            // the caller (e.g. a ServerContext.Spi provider that is not on the
            // classpath raises ServiceConfigurationError).  A telemetry report
            // must never be dropped because identity resolution failed, so fall
            // back to the shared anonymous bucket (conservative).
            logger.log(Level.FINE,
                    "Could not resolve client identity from server context;"
                            + " using anonymous bucket",
                    e);
            return ANONYMOUS_CLIENT_ID;
        }
    }

    /**
     * Builds a stable identity string from a client {@link Subject}'s
     * principals.  Returns {@value #ANONYMOUS_CLIENT_ID} if the subject is
     * {@code null} or has no principals.
     */
    static String clientIdFromSubject(Subject subject) {
        if (subject == null) return ANONYMOUS_CLIENT_ID;
        Set<Principal> principals = subject.getPrincipals();
        if (principals == null || principals.isEmpty()) {
            return ANONYMOUS_CLIENT_ID;
        }
        List<String> names = new ArrayList<String>(principals.size());
        for (Principal p : principals) {
            if (p != null && p.getName() != null) {
                names.add(p.getName());
            }
        }
        if (names.isEmpty()) return ANONYMOUS_CLIENT_ID;
        java.util.Collections.sort(names); // stable regardless of iteration order
        return String.join("|", names);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to submit the aggregate pinning data to the Verdict Registry.
     */
    private void submitToRegistry(PinningState state,
                                  long totalPinnedNanos,
                                  long totalEventCount,
                                  long nowMs) {
        synchronized (state) {
            if (state.submitted) return;
            state.submitted = true;
        }

        long eventCount = Math.min(totalEventCount, PinningReport.MAX_EVENT_COUNT);

        PinningReport aggregate;
        try {
            aggregate = buildUriArray(state.codebaseUrls,
                    totalPinnedNanos, eventCount,
                    state.windowStartMs, nowMs);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to build PinningReport for VerdictRegistry; key={0}",
                    state.codebaseKey);
            resetSubmitted(state);
            return;
        }

        VerdictRegistry vr = this.verdictRegistry;
        if (vr == null) {
            logger.log(Level.WARNING,
                    "Threshold crossed but VerdictRegistry is not available;"
                            + " key={0}, pinnedNanos={1}, events={2}",
                    new Object[]{state.codebaseKey, totalPinnedNanos,
                        totalEventCount});
            resetSubmitted(state);
            return;
        }

        try {
            vr.reportPinning(aggregate);
            logger.log(Level.INFO,
                    "Submitted PinningReport to VerdictRegistry: key={0},"
                            + " pinnedNanos={1}, events={2}",
                    new Object[]{state.codebaseKey, totalPinnedNanos,
                        totalEventCount});
        } catch (RemoteException e) {
            logger.log(Level.WARNING,
                    "Failed to submit PinningReport to VerdictRegistry;"
                            + " key=" + state.codebaseKey,
                    e);
            resetSubmitted(state);
        }
    }

    /**
     * Resets the {@link PinningState#submitted} flag so that the next threshold
     * crossing will attempt another submission.
     */
    private static void resetSubmitted(PinningState state) {
        synchronized (state) { state.submitted = false; }
    }

    /**
     * Periodic sweep: clears stale per-codebase aggregation state and stale
     * per-client rate-limit state so neither a one-off pinning burst nor an
     * idle client's bookkeeping accumulates indefinitely.
     */
    private void sweep() {
        long now      = System.currentTimeMillis();
        long cutoffMs = now - (sweepIntervalMinutes * 60_000L);
        int removed = 0;
        for (Map.Entry<String, PinningState> entry : aggregates.entrySet()) {
            PinningState state = entry.getValue();
            if (state.submitted || state.lastSeenMs.get() < cutoffMs) {
                aggregates.remove(entry.getKey(), state);
                removed++;
            }
        }
        // Drop client rate state whose window has long elapsed.
        for (Map.Entry<String, ClientRateState> entry : clientRates.entrySet()) {
            ClientRateState rate = entry.getValue();
            synchronized (rate) {
                if (now - rate.windowStartMs >= rateLimitWindowMillis) {
                    clientRates.remove(entry.getKey(), rate);
                }
            }
        }
        if (removed > 0) {
            logger.log(Level.FINE,
                    "JFR Telemetry sweep complete: removed {0} stale entries",
                    removed);
        }
    }

    /**
     * Produces a canonical, order-independent key for a codebase URL set.
     */
    static String codebaseKey(Set<Uri> uris) {
        String[] sorted = new String[uris.size()];
        int i = 0;
        for (Uri u : uris) {
            sorted[i++] = u.toString();
        }
        Arrays.sort(sorted);
        return String.join("|", sorted);
    }

    private static String[] uriSetToStrings(Set<Uri> uris) {
        String[] result = new String[uris.size()];
        int i = 0;
        for (Uri u : uris) {
            result[i++] = u.toString();
        }
        return result;
    }

    /**
     * Rebuilds a {@link PinningReport} from stored URI strings, used when
     * submitting the aggregate to the Verdict Registry.
     */
    private static PinningReport buildUriArray(String[] codebaseUrls,
                                               long pinnedNanos,
                                               long eventCount,
                                               long startMs,
                                               long endMs)
            throws Exception {
        Uri[] uris = new Uri[codebaseUrls.length];
        for (int i = 0; i < codebaseUrls.length; i++) {
            uris[i] = new Uri(codebaseUrls[i]);
        }
        return new PinningReport(uris, pinnedNanos, eventCount, startMs, endMs);
    }
}
