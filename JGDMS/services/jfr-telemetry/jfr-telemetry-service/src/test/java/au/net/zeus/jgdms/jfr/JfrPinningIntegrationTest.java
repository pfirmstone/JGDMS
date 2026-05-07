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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the JFR-event-driven pinning detection pipeline.
 *
 * <p>These tests use the real JDK Flight Recorder ({@link RecordingStream}) to
 * capture live {@code jdk.VirtualThreadPinned} events caused by virtual threads
 * that block inside {@code synchronized} blocks, and feed the captured event
 * data into {@link JfrTelemetryServiceImpl} to verify the end-to-end pipeline.
 *
 * <h2>Skip conditions</h2>
 * Tests that require real {@code jdk.VirtualThreadPinned} events are guarded by
 * {@link Assume#assumeTrue} and are automatically skipped on JDK &lt; 21, where
 * virtual threads and their JFR events are not available.
 *
 * <h2>JFR event pipeline (tested here)</h2>
 * <pre>
 * [Virtual thread blocks in synchronized]
 *        ↓
 * JVM emits jdk.VirtualThreadPinned JFR event
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
    // Stub Verdict Registry
    // -------------------------------------------------------------------------

    private static class StubVerdictRegistry implements VerdictRegistry {

        final List<PinningReport> pinningReports = new ArrayList<PinningReport>();

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
    // Test name for the JFR event we are interested in
    // -------------------------------------------------------------------------

    static final String VTP_EVENT = "jdk.VirtualThreadPinned";

    // -------------------------------------------------------------------------
    // Helper: check if jdk.VirtualThreadPinned is available on this JVM
    // -------------------------------------------------------------------------

    private static boolean hasVirtualThreadPinnedEvent() {
        for (EventType et : FlightRecorder.getFlightRecorder().getEventTypes()) {
            if (VTP_EVENT.equals(et.getName())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helper: create a virtual thread via reflection
    //
    // Thread.ofVirtual() is JDK 21+.  We invoke it via reflection so that this
    // test class compiles on Java 8/17.  On JDK < 21 the Assume guard in each
    // test ensures we never reach this method.
    // -------------------------------------------------------------------------

    private static Thread startVirtualThread(Runnable r) throws Exception {
        // Thread.ofVirtual().start(r)
        Object vtBuilder = Thread.class.getMethod("ofVirtual").invoke(null);
        Class<?> builderInterface = Class.forName("java.lang.Thread$Builder");
        return (Thread) builderInterface.getMethod("start", Runnable.class)
                .invoke(vtBuilder, r);
    }

    // -------------------------------------------------------------------------
    // Helper: cause a single real jdk.VirtualThreadPinned event.
    //
    // A virtual thread blocks (via Thread.sleep) inside a synchronized block.
    // On JDK 21 this causes the JVM to emit a jdk.VirtualThreadPinned event
    // whose duration equals (at least) the sleep time.
    // -------------------------------------------------------------------------

    private static void causeOnePinningEvent(long sleepMillis) throws Exception {
        final Object lock = new Object();
        Thread vt = startVirtualThread(new Runnable() {
            public void run() {
                synchronized (lock) {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        vt.join(sleepMillis * 5 + 2000);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that the JFR {@code RecordingStream} can capture a real
     * {@code jdk.VirtualThreadPinned} event when a virtual thread blocks
     * inside a {@code synchronized} block.
     *
     * <p>Requires JDK 21+ — automatically skipped otherwise.
     */
    @Test
    public void testJfrDetectsVirtualThreadPinning() throws Exception {
        Assume.assumeTrue(
                "jdk.VirtualThreadPinned event not available (need JDK 21+)",
                hasVirtualThreadPinnedEvent());

        CountDownLatch pinDetected = new CountDownLatch(1);
        AtomicLong detectedNanos = new AtomicLong(0);

        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            rs.enable(VTP_EVENT);
            rs.onEvent(VTP_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    detectedNanos.addAndGet(e.getDuration().toNanos());
                    pinDetected.countDown();
                }
            });
            rs.startAsync();

            causeOnePinningEvent(50);

            boolean eventArrived = pinDetected.await(5, TimeUnit.SECONDS);
            assertTrue("JFR must detect at least one jdk.VirtualThreadPinned event",
                    eventArrived);
        }

        assertTrue("Detected pinned duration must be > 0", detectedNanos.get() > 0L);
    }

    /**
     * End-to-end test: real JFR events → {@link PinningReport} → 
     * {@link JfrTelemetryServiceImpl} → {@link VerdictRegistry}.
     *
     * <p>This test simulates the full client-side JFR pipeline:
     * <ol>
     *   <li>A {@link RecordingStream} listens for {@code jdk.VirtualThreadPinned}
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
     *
     * <p>Requires JDK 21+ — automatically skipped otherwise.
     */
    @Test
    public void testEndToEndJfrEventToVerdictRegistry() throws Exception {
        Assume.assumeTrue(
                "jdk.VirtualThreadPinned event not available (need JDK 21+)",
                hasVirtualThreadPinnedEvent());

        // 1 ns threshold → any real pinning event will trigger
        long nanoThreshold = 1L;
        StubVerdictRegistry stub = new StubVerdictRegistry();
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                nanoThreshold, 1000L, 60, stub);

        Uri testUri = new Uri("https://test.example.com/lib.jar");
        Set<Uri> codebaseUrls = new LinkedHashSet<Uri>();
        codebaseUrls.add(testUri);
        Uri[] uriArray = new Uri[]{testUri};

        // Accumulators (populated by the JFR stream callback)
        AtomicLong totalNanos  = new AtomicLong(0L);
        AtomicLong totalCount  = new AtomicLong(0L);
        CountDownLatch pinDetected = new CountDownLatch(1);

        long windowStartMs = System.currentTimeMillis();

        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            rs.enable(VTP_EVENT);
            rs.onEvent(VTP_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    totalNanos.addAndGet(e.getDuration().toNanos());
                    totalCount.incrementAndGet();
                    pinDetected.countDown();
                }
            });
            rs.startAsync();

            // Cause a real pinning event
            causeOnePinningEvent(50);

            // Wait for the JFR stream to deliver the event
            boolean arrived = pinDetected.await(5, TimeUnit.SECONDS);
            assertTrue("JFR must deliver at least one VirtualThreadPinned event", arrived);
        }

        long windowEndMs = System.currentTimeMillis();

        // 2. Build a PinningReport from the real JFR data and submit it.
        //    This mirrors what a production JFR agent would do.
        long capturedNanos  = totalNanos.get();
        long capturedCount  = totalCount.get();

        assertTrue("Must have captured at least one event", capturedCount >= 1);
        assertTrue("Captured pinned duration must be > 0", capturedNanos > 0);

        PinningReport report = new PinningReport(
                uriArray, capturedNanos, capturedCount, windowStartMs, windowEndMs);

        // 3. Submit to the service: threshold is 1 ns, so it must escalate.
        svc.reportPinning(report);

        // 4. Verify that the VerdictRegistry received an aggregate report.
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
     * {@code jdk.VirtualThreadPinned} events across several virtual threads
     * before submitting a single batched report.
     *
     * <p>Requires JDK 21+ — automatically skipped otherwise.
     */
    @Test
    public void testMultipleEventsAccumulateBeforeThreshold() throws Exception {
        Assume.assumeTrue(
                "jdk.VirtualThreadPinned event not available (need JDK 21+)",
                hasVirtualThreadPinnedEvent());

        final int TARGET_EVENTS = 3;

        AtomicLong totalNanos = new AtomicLong(0L);
        AtomicLong totalCount = new AtomicLong(0L);
        CountDownLatch allDetected = new CountDownLatch(TARGET_EVENTS);

        long windowStartMs = System.currentTimeMillis();

        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            rs.enable(VTP_EVENT);
            rs.onEvent(VTP_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    totalNanos.addAndGet(e.getDuration().toNanos());
                    totalCount.incrementAndGet();
                    allDetected.countDown();
                }
            });
            rs.startAsync();

            // Cause TARGET_EVENTS separate pinning events
            for (int i = 0; i < TARGET_EVENTS; i++) {
                causeOnePinningEvent(20);
            }

            boolean arrived = allDetected.await(10, TimeUnit.SECONDS);
            assertTrue("JFR must deliver " + TARGET_EVENTS + " VirtualThreadPinned events",
                    arrived);
        }

        long windowEndMs = System.currentTimeMillis();

        long capturedNanos = totalNanos.get();
        long capturedCount = totalCount.get();

        assertTrue("Must have captured " + TARGET_EVENTS + " events",
                capturedCount >= TARGET_EVENTS);
        assertTrue("Cumulative pinned nanos must be > 0", capturedNanos > 0);

        // Threshold set above the individual-event level but below the total —
        // submit batched report to the service.
        long nanoThreshold = capturedNanos / 2; // will be exceeded by the full total
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
     * Verifies that the JFR detection logic (using {@link RecordingStream}) can
     * run on JDK 17+ even without virtual-thread support — the event handler
     * is simply never called and the test skips gracefully.
     *
     * <p>This test always runs (no {@code Assume} guard) and exercises the
     * "JFR available but VirtualThreadPinned not registered" branch.
     */
    @Test
    public void testGracefulSkipOnJdkWithoutVirtualThreads() throws Exception {
        boolean eventAvailable = hasVirtualThreadPinnedEvent();

        if (eventAvailable) {
            // On JDK 21+ this path is not exercised — that is expected.
            return;
        }

        // On JDK < 21: verify we can open a RecordingStream without errors,
        // and that no VTP events arrive (because the event type does not exist).
        AtomicLong count = new AtomicLong(0L);
        try (RecordingStream rs = new RecordingStream()) {
            rs.setReuse(false);
            // Enabling a non-existent event name is silently ignored.
            rs.enable(VTP_EVENT);
            rs.onEvent(VTP_EVENT, new Consumer<RecordedEvent>() {
                public void accept(RecordedEvent e) {
                    count.incrementAndGet();
                }
            });
            rs.startAsync();
            Thread.sleep(200);
        }

        assertEquals("No VirtualThreadPinned events must arrive on JDK < 21",
                0L, count.get());
    }
}
