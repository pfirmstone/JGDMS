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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the JFR-event-driven pinning detection pipeline.
 *
 * <p>These tests use the real JDK Flight Recorder ({@link RecordingStream}) to
 * capture live JFR events and feed the captured event data into
 * {@link JfrTelemetryServiceImpl} to verify the end-to-end pipeline.
 *
 * <p>Events are produced using a custom {@link SimulatedPinningEvent} that
 * measures real elapsed time with {@code begin()} / {@code commit()}.  This
 * approach is portable across all JDK versions: it does not depend on
 * {@code synchronized}-block pinning behaviour (which was removed in JDK 24
 * by JEP 491) and does not require JDK 21+ virtual-thread support.
 *
 * <h2>JFR event pipeline (tested here)</h2>
 * <pre>
 * [SimulatedPinningEvent.begin() … commit()]
 *        ↓
 * JFR records the event with real measured duration
 *        ↓
 * RecordingStream.onEvent() callback accumulates duration + count
 *        ↓
 * PinningReport built from real JFR event data
 *        ↓
 * JfrTelemetryServiceImpl.reportPinning() → threshold check → VerdictRegistry
 * </pre>
 *
 * @see JfrTelemetryServiceImpl
 * @see PinningReport
 * @since 3.1.1
 */
public class JfrPinningIntegrationTest {

    // -------------------------------------------------------------------------
    // Custom JFR event used as a portable stand-in for jdk.VirtualThreadPinned.
    //
    // Using a custom event avoids the synchronized-block pinning mechanism that
    // was removed in JDK 24 (JEP 491).  The event records a real elapsed
    // duration via begin() / commit(), exercising exactly the same RecordingStream
    // infrastructure that production code uses for jdk.VirtualThreadPinned.
    // -------------------------------------------------------------------------

    @Name("au.net.zeus.jgdms.test.SimulatedPinningEvent")
    @Label("Simulated Pinning Event")
    @Category({"Test"})
    @StackTrace(false)
    @Enabled(false)
    static final class SimulatedPinningEvent extends Event {}

    // Stable name used when enabling / listening for the event in RecordingStream.
    static final String SIM_EVENT = "au.net.zeus.jgdms.test.SimulatedPinningEvent";

    static {
        // Touch the inner class so JFR registers the event type before any
        // RecordingStream is opened.
        new SimulatedPinningEvent();
    }

    // -------------------------------------------------------------------------
    // Stub Verdict Registry
    // -------------------------------------------------------------------------

    private static class StubVerdictRegistry implements VerdictRegistry {

        final List<PinningReport> pinningReports = new ArrayList<>();

        public synchronized void reportPinning(PinningReport report) {
            pinningReports.add(report);
        }

        public void reportCrash(CrashReport report) {}
        public void registerAnalysisEngine(String id, PublicKey k, String alg) {}
        public void revokeAnalysisEngine(String id) {}
        public void submitVerdict(String id, SignedVerdict v) {}
        public void submitReport(String id, JarAnalysisReport r) {}
        public RegistryVerdict getVerdict(Set<Uri> u) { return null; }
        public RegistryVerdict getVerdictByHash(String h) { return null; }
        public EventRegistration registerVerdictListener(RemoteEventListener l,
                Set<Uri> u, MarshalledInstance h, long d) { return null; }
        public long renewEventLease(Uuid id, long d) throws UnknownLeaseException { return 0L; }
        public void cancelEventLease(Uuid id) throws UnknownLeaseException {}
    }

    // -------------------------------------------------------------------------
    // Helper: emit one SimulatedPinningEvent with a real measured duration.
    //
    // begin() records the start timestamp; Thread.sleep() gives a measurable
    // duration; commit() records the end timestamp and writes the event to the
    // active JFR recording.  RecordedEvent.getDuration() then returns the
    // elapsed time, analogous to what jdk.VirtualThreadPinned reports.
    // -------------------------------------------------------------------------

    private static void emitOnePinningEvent(long durationMillis)
            throws InterruptedException {
        SimulatedPinningEvent evt = new SimulatedPinningEvent();
        evt.begin();
        Thread.sleep(durationMillis);
        evt.commit();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that the JFR {@link RecordingStream} can capture a real custom
     * JFR event with a non-zero measured duration.
     *
     * <p>Uses {@link SimulatedPinningEvent} — no JDK version requirement.
     */
    @Test
    public void testJfrDetectsSimulatedPinningEvent() throws Exception {
        CountDownLatch eventDetected = new CountDownLatch(1);
        AtomicLong detectedNanos = new AtomicLong(0);

        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            rs.enable(SIM_EVENT);
            rs.onEvent(SIM_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    detectedNanos.addAndGet(e.getDuration().toNanos());
                    eventDetected.countDown();
                }
            });
            rs.startAsync();

            emitOnePinningEvent(50);

            boolean arrived = eventDetected.await(5, TimeUnit.SECONDS);
            assertTrue("RecordingStream must deliver the SimulatedPinningEvent", arrived);
        }

        assertTrue("Detected event duration must be > 0", detectedNanos.get() > 0L);
    }

    /**
     * End-to-end test: real JFR event → {@link PinningReport} →
     * {@link JfrTelemetryServiceImpl} → {@link VerdictRegistry}.
     *
     * <p>This test simulates the full client-side JFR pipeline:
     * <ol>
     *   <li>A {@link RecordingStream} listens for {@link SimulatedPinningEvent}
     *       events.</li>
     *   <li>Each event is accumulated into {@code totalNanos} / {@code totalCount}
     *       (matching what a real client agent would do before batching).</li>
     *   <li>After the observation window, a {@link PinningReport} is built from
     *       the real event data and submitted to
     *       {@link JfrTelemetryServiceImpl#reportPinning}.</li>
     *   <li>Because the single event already exceeds the very low test threshold,
     *       the service immediately submits an aggregate report to the stub
     *       {@link VerdictRegistry}.</li>
     * </ol>
     */
    @Test
    public void testEndToEndJfrEventToVerdictRegistry() throws Exception {
        // 1 ns threshold → any real event with positive duration will trigger
        long nanoThreshold = 1L;
        StubVerdictRegistry stub = new StubVerdictRegistry();
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                nanoThreshold, 1000L, 60, stub);

        Uri testUri = new Uri("https://test.example.com/lib.jar");
        Uri[] uriArray = new Uri[]{testUri};

        AtomicLong totalNanos  = new AtomicLong(0L);
        AtomicLong totalCount  = new AtomicLong(0L);
        CountDownLatch eventDetected = new CountDownLatch(1);

        long windowStartMs = System.currentTimeMillis();

        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            rs.enable(SIM_EVENT);
            rs.onEvent(SIM_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    totalNanos.addAndGet(e.getDuration().toNanos());
                    totalCount.incrementAndGet();
                    eventDetected.countDown();
                }
            });
            rs.startAsync();

            emitOnePinningEvent(50);

            boolean arrived = eventDetected.await(5, TimeUnit.SECONDS);
            assertTrue("RecordingStream must deliver the SimulatedPinningEvent", arrived);
        }

        long windowEndMs = System.currentTimeMillis();

        long capturedNanos = totalNanos.get();
        long capturedCount = totalCount.get();

        assertTrue("Must have captured at least one event", capturedCount >= 1);
        assertTrue("Captured event duration must be > 0", capturedNanos > 0);

        // Build a PinningReport from the real JFR data and submit it.
        PinningReport report = new PinningReport(
                uriArray, capturedNanos, capturedCount, windowStartMs, windowEndMs);

        // Threshold is 1 ns, so the service must escalate immediately.
        svc.reportPinning(report);

        synchronized (stub) {
            assertEquals("VerdictRegistry must receive exactly one escalation",
                    1, stub.pinningReports.size());
        }

        PinningReport submitted = stub.pinningReports.get(0);
        assertTrue("Submitted report must include real pinned nanos",
                submitted.getPinnedNanos() >= capturedNanos);
        assertTrue("Submitted report must include real event count",
                submitted.getEventCount() >= capturedCount);
    }

    /**
     * Verifies that the JFR detection pipeline correctly accumulates multiple
     * events before submitting a single batched report.
     */
    @Test
    public void testMultipleEventsAccumulateBeforeThreshold() throws Exception {
        final int TARGET_EVENTS = 3;

        AtomicLong totalNanos = new AtomicLong(0L);
        AtomicLong totalCount = new AtomicLong(0L);
        CountDownLatch allDetected = new CountDownLatch(TARGET_EVENTS);

        long windowStartMs = System.currentTimeMillis();

        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            rs.enable(SIM_EVENT);
            rs.onEvent(SIM_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    totalNanos.addAndGet(e.getDuration().toNanos());
                    totalCount.incrementAndGet();
                    allDetected.countDown();
                }
            });
            rs.startAsync();

            for (int i = 0; i < TARGET_EVENTS; i++) {
                emitOnePinningEvent(20);
            }

            boolean arrived = allDetected.await(10, TimeUnit.SECONDS);
            assertTrue("RecordingStream must deliver " + TARGET_EVENTS
                    + " SimulatedPinningEvents", arrived);
        }

        long windowEndMs = System.currentTimeMillis();

        long capturedNanos = totalNanos.get();
        long capturedCount = totalCount.get();

        assertTrue("Must have captured " + TARGET_EVENTS + " events",
                capturedCount >= TARGET_EVENTS);
        assertTrue("Cumulative event duration must be > 0", capturedNanos > 0);

        // Threshold is just below the total accumulated duration — the full
        // batched report must cross it and produce exactly one escalation.
        long nanoThreshold = capturedNanos / 2;
        StubVerdictRegistry stub = new StubVerdictRegistry();
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                nanoThreshold, 10_000L, 60, stub);

        Uri testUri  = new Uri("https://multi.example.com/lib.jar");
        Uri[] uriArr = new Uri[]{testUri};

        PinningReport batchReport = new PinningReport(
                uriArr, capturedNanos, capturedCount, windowStartMs, windowEndMs);
        svc.reportPinning(batchReport);

        synchronized (stub) {
            assertEquals("Batched report must produce exactly one escalation",
                    1, stub.pinningReports.size());
        }
    }

    /**
     * Verifies that a {@link RecordingStream} that listens for an unknown event
     * name receives no events and completes without error.
     *
     * <p>This exercises the graceful-degradation path that production code
     * uses on JVMs where {@code jdk.VirtualThreadPinned} is not available.
     */
    @Test
    public void testRecordingStreamIgnoresUnknownEventName() throws Exception {
        AtomicLong count = new AtomicLong(0L);
        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            // Enabling a non-existent event name is silently ignored by JFR.
            rs.enable("au.net.zeus.jgdms.test.NonExistentEvent");
            rs.onEvent("au.net.zeus.jgdms.test.NonExistentEvent",
                    new Consumer<RecordedEvent>() {
                        public void accept(RecordedEvent e) {
                            count.incrementAndGet();
                        }
                    });
            rs.startAsync();
            Thread.sleep(200);
        }

        assertEquals("No events must arrive for an unknown event name",
                0L, count.get());
    }
}
