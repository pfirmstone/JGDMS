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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 *   <li>Periodic sweep: a background daemon thread resets aggregation state
 *       at a configurable interval so that one-off pinning events do not
 *       permanently condemn a codebase.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <ul>
 *   <li>{@link #aggregates} is a {@link ConcurrentHashMap}; entries are
 *       created lock-free via {@code computeIfAbsent}.</li>
 *   <li>Per-codebase state ({@link PinningState}) uses {@code AtomicLong}
 *       for all counters.  The {@code submitted} flag is guarded by
 *       {@code synchronized(state)} to guarantee exactly-once submission to the
 *       Verdict Registry per sweep window.  The flag is reset if the
 *       VerdictRegistry is unavailable or the remote call fails, so that a
 *       future threshold crossing will retry.</li>
 *   <li>The periodic sweep runs in a single-threaded daemon executor; it
 *       replaces each {@link PinningState} entry atomically using
 *       {@link ConcurrentHashMap#replace}.</li>
 * </ul>
 *
 * <h2>Defaults</h2>
 * <ul>
 *   <li>Pinned-nanosecond threshold: {@value #DEFAULT_PINNED_NANOS_THRESHOLD}
 *       ns (30 seconds of cumulative carrier-thread pinning).</li>
 *   <li>Event-count threshold: {@value #DEFAULT_EVENT_COUNT_THRESHOLD} events.
 *   </li>
 *   <li>Sweep interval: {@value #DEFAULT_SWEEP_INTERVAL_MINUTES} minutes.</li>
 * </ul>
 *
 * <p>The Jini lifecycle wrapper {@link ActivatableJfrTelemetryServiceImpl}
 * reads these from a Jini {@link net.jini.config.Configuration} and constructs
 * an instance of this class.
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
         * {@code reportPinning} call.  Initialised to {@code windowStartMs}
         * so that a sweeper run that races with the very first
         * {@code reportPinning} call does not purge a brand-new state whose
         * counter has not yet been updated.
         * <p>Unlike the other {@code AtomicLong} fields, this is not
         * initialised inline because it must be set to {@code windowStartMs}
         * rather than {@code 0L} — see the constructor.
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

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Per-codebase aggregation state. Key is the canonical codebase URL set. */
    private final ConcurrentHashMap<String, PinningState> aggregates =
            new ConcurrentHashMap<String, PinningState>();

    /** Threshold: cumulative pinned ns before DANGEROUS verdict is submitted. */
    private final long pinnedNanosThreshold;

    /** Threshold: event count before DANGEROUS verdict is submitted. */
    private final long eventCountThreshold;

    /** Sweep interval in minutes. */
    private final int sweepIntervalMinutes;

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
     * Creates a service instance with default thresholds and sweep interval.
     */
    public JfrTelemetryServiceImpl() {
        this(DEFAULT_PINNED_NANOS_THRESHOLD,
             DEFAULT_EVENT_COUNT_THRESHOLD,
             DEFAULT_SWEEP_INTERVAL_MINUTES,
             null);
    }

    /**
     * Creates a service instance with explicit thresholds.
     *
     * @param pinnedNanosThreshold  cumulative pinned-ns threshold; must be
     *                              positive
     * @param eventCountThreshold   event-count threshold; must be positive
     * @param sweepIntervalMinutes  sweep interval in minutes; must be positive
     * @param verdictRegistry       the Verdict Registry to notify, or
     *                              {@code null} if not yet available
     * @throws IllegalArgumentException if any numeric argument is
     *                                  non-positive
     */
    public JfrTelemetryServiceImpl(long pinnedNanosThreshold,
                                   long eventCountThreshold,
                                   int  sweepIntervalMinutes,
                                   VerdictRegistry verdictRegistry) {
        if (pinnedNanosThreshold <= 0)
            throw new IllegalArgumentException(
                    "pinnedNanosThreshold must be positive");
        if (eventCountThreshold <= 0)
            throw new IllegalArgumentException(
                    "eventCountThreshold must be positive");
        if (sweepIntervalMinutes <= 0)
            throw new IllegalArgumentException(
                    "sweepIntervalMinutes must be positive");

        this.pinnedNanosThreshold = pinnedNanosThreshold;
        this.eventCountThreshold  = eventCountThreshold;
        this.sweepIntervalMinutes = sweepIntervalMinutes;
        this.verdictRegistry      = verdictRegistry;

        this.sweepExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r,
                                "JGDMS-JfrTelemetryService-Sweeper");
                        t.setDaemon(true);
                        return t;
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the periodic aggregation-state sweep.
     *
     * <p>This method is called by {@link ActivatableJfrTelemetryServiceImpl}
     * from its {@code onExported} callback, after the service has been
     * successfully exported and is ready to receive calls.
     */
    void startSweeper() {
        sweepFuture = sweepExecutor.scheduleAtFixedRate(
                this::sweep,
                sweepIntervalMinutes,
                sweepIntervalMinutes,
                TimeUnit.MINUTES);
        logger.log(Level.CONFIG,
                "JFR Telemetry sweeper started; interval={0} min,"
                        + " pinnedNanosThreshold={1} ns,"
                        + " eventCountThreshold={2}",
                new Object[]{
                    sweepIntervalMinutes,
                    pinnedNanosThreshold,
                    eventCountThreshold
                });
    }

    /**
     * Shuts down the sweep executor.
     *
     * <p>Called by {@link ActivatableJfrTelemetryServiceImpl#destroy()}.
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
     * <p>Called by the Jini wrapper after discovering the registry via
     * {@link net.jini.lookup.ServiceDiscoveryManager}.
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

        Set<Uri> urls      = report.getCodebaseUrls();
        String   key       = codebaseKey(urls);
        long     nowMs     = System.currentTimeMillis();

        PinningState state = aggregates.computeIfAbsent(
                key, k -> new PinningState(k, uriSetToStrings(urls), nowMs));

        long totalPinnedNanos = state.pinnedNanos.addAndGet(report.getPinnedNanos());
        long totalEventCount  = state.eventCount.addAndGet(report.getEventCount());
        state.lastSeenMs.set(nowMs);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "Pinning recorded: key={0}, newPinnedNanos={1},"
                            + " totalPinnedNanos={2}, totalEvents={3}",
                    new Object[]{key, report.getPinnedNanos(),
                        totalPinnedNanos, totalEventCount});
        }

        // Check threshold and submit to VerdictRegistry if exceeded.
        if (!state.submitted
                && (totalPinnedNanos >= pinnedNanosThreshold
                        || totalEventCount >= eventCountThreshold)) {
            submitToRegistry(state, totalPinnedNanos, totalEventCount, nowMs);
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
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to submit the aggregate pinning data to the Verdict Registry.
     * The {@link PinningState#submitted} flag is set to {@code true} exactly
     * once, and only after both the report has been built and the registry
     * reference is confirmed to be non-null.  This guarantees that a
     * transiently-unavailable registry does not permanently suppress future
     * submission attempts within the same sweep window.
     */
    private void submitToRegistry(PinningState state,
                                  long totalPinnedNanos,
                                  long totalEventCount,
                                  long nowMs) {
        // Atomically claim the submission right.
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
     * Resets the {@link PinningState#submitted} flag so that the next
     * threshold crossing will attempt another submission.  Called whenever a
     * submission attempt fails (registry unavailable, build error, or
     * {@link RemoteException}).
     */
    private static void resetSubmitted(PinningState state) {
        synchronized (state) { state.submitted = false; }
    }

    /**
     * Periodic sweep: clears per-codebase aggregation state so that a
     * one-off pinning burst in a previous window does not permanently
     * condemn a codebase.  Only entries whose {@code submitted} flag is
     * already {@code true} (or that have not been seen in the last sweep
     * interval) are cleared.
     */
    private void sweep() {
        long cutoffMs = System.currentTimeMillis()
                - (sweepIntervalMinutes * 60_000L);
        int removed = 0;
        for (Map.Entry<String, PinningState> entry : aggregates.entrySet()) {
            PinningState state = entry.getValue();
            // Remove states that were either already submitted or have been
            // inactive for the full sweep interval.
            if (state.submitted || state.lastSeenMs.get() < cutoffMs) {
                aggregates.remove(entry.getKey(), state);
                removed++;
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
     * Sorts the URI strings lexicographically before joining.
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
