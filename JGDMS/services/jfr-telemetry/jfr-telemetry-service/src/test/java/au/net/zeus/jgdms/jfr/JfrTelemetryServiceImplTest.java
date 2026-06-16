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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import java.security.PublicKey;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link JfrTelemetryServiceImpl}.
 *
 * @since 3.1.1
 */
public class JfrTelemetryServiceImplTest {

    // -------------------------------------------------------------------------
    // Stub Verdict Registry
    // -------------------------------------------------------------------------

    /**
     * Stub VerdictRegistry that records all {@code reportPinning} calls.
     */
    private static class StubVerdictRegistry implements VerdictRegistry {

        final List<PinningReport> pinningReports = new ArrayList<PinningReport>();
        final List<CrashReport>   crashReports   = new ArrayList<CrashReport>();

        @Override
        public synchronized void reportPinning(PinningReport report) {
            pinningReports.add(report);
        }

        @Override
        public void reportCrash(CrashReport report) {
            crashReports.add(report);
        }

        @Override
        public void registerAnalysisEngine(String engineId, PublicKey engineKey,
                                           String sigAlgorithm) {}
        @Override
        public void revokeAnalysisEngine(String engineId) {}
        @Override
        public void submitReport(String engineId, JarAnalysisReport report) {}
        @Override
        public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) { return null; }
        @Override
        public RegistryVerdict getVerdictByHash(String contentHash) { return null; }
        @Override
        public EventRegistration registerVerdictListener(RemoteEventListener l,
                                                         Set<Uri> urls,
                                                         MarshalledInstance h,
                                                         long d) { return null; }
        @Override
        public long renewEventLease(Uuid id, long d)
                throws UnknownLeaseException { return 0L; }
        @Override
        public void cancelEventLease(Uuid id) throws UnknownLeaseException {}

        public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener, MarshalledInstance handback, long leaseDuration) throws RemoteException {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private static final long LOW_PINNED_NANOS_THRESHOLD = 1_000_000L; // 1 ms
    private static final long LOW_EVENT_COUNT_THRESHOLD  = 5L;
    private static final int  SWEEP_INTERVAL_MINUTES     = 60;

    private StubVerdictRegistry stubRegistry;
    private JfrTelemetryServiceImpl service;
    private Uri uri1;
    private Uri uri2;

    @Before
    public void setUp() throws Exception {
        stubRegistry = new StubVerdictRegistry();
        service = new JfrTelemetryServiceImpl(
                LOW_PINNED_NANOS_THRESHOLD,
                LOW_EVENT_COUNT_THRESHOLD,
                SWEEP_INTERVAL_MINUTES,
                stubRegistry);
        uri1 = new Uri("https://example.com/lib-a.jar");
        uri2 = new Uri("https://example.com/lib-b.jar");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testInitialStateIsZero() throws RemoteException, Exception {
        Set<Uri> urls = makeUriSet(uri1);
        assertEquals(0L, service.getPinnedNanos(urls));
        assertEquals(0L, service.getPinCount(urls));
    }

    @Test
    public void testAccumulatePinningBelowThreshold() throws Exception {
        Set<Uri> urls = makeUriSet(uri1);
        PinningReport r = new PinningReport(
                new Uri[]{uri1}, 100L, 1L,
                System.currentTimeMillis() - 1000L,
                System.currentTimeMillis());

        service.reportPinning(r);

        assertEquals(100L, service.getPinnedNanos(urls));
        assertEquals(1L,   service.getPinCount(urls));
        assertTrue("No report should have been sent below threshold",
                stubRegistry.pinningReports.isEmpty());
    }

    @Test
    public void testNanosThresholdTriggersSingleSubmission() throws Exception {
        Uri[] uris = new Uri[]{uri1};
        Set<Uri> urlSet = makeUriSet(uri1);
        long now = System.currentTimeMillis();

        // First report from clientA: just below threshold.
        PinningReport r1 = new PinningReport(uris, LOW_PINNED_NANOS_THRESHOLD - 1,
                1L, now - 2000L, now - 1000L);
        service.reportPinning(r1, "clientA");
        assertTrue(stubRegistry.pinningReports.isEmpty());

        // Second report from a DIFFERENT client crosses the codebase threshold.
        // (A second report from clientA for the same codebase would be deduped.)
        PinningReport r2 = new PinningReport(uris, 2L, 1L, now - 1000L, now);
        service.reportPinning(r2, "clientB");

        assertEquals("Registry should have been called exactly once",
                1, stubRegistry.pinningReports.size());

        PinningReport submitted = stubRegistry.pinningReports.get(0);
        assertTrue(submitted.getPinnedNanos() >= LOW_PINNED_NANOS_THRESHOLD);
    }

    @Test
    public void testEventCountThresholdTriggersSingleSubmission()
            throws Exception {
        Uri[] uris = new Uri[]{uri2};
        Set<Uri> urlSet = makeUriSet(uri2);
        long now = System.currentTimeMillis();

        // Submit events one-by-one from DISTINCT clients until the codebase
        // threshold is crossed.  Each client contributes one (deduped) report
        // for this codebase.
        for (int i = 0; i < LOW_EVENT_COUNT_THRESHOLD; i++) {
            PinningReport r = new PinningReport(uris, 10L, 1L,
                    now - 1000L, now);
            service.reportPinning(r, "client-" + i);
        }

        assertEquals("Registry should have been called exactly once",
                1, stubRegistry.pinningReports.size());
    }

    @Test
    public void testThresholdCrossedOnlyOnce() throws Exception {
        Uri[] uris = new Uri[]{uri1};
        long now = System.currentTimeMillis();

        // Submit many events that each exceed the pinned-nanos threshold.
        for (int i = 0; i < 20; i++) {
            PinningReport r = new PinningReport(uris, LOW_PINNED_NANOS_THRESHOLD,
                    1L, now - 1000L, now);
            service.reportPinning(r);
        }

        assertEquals("Registry must be called at most once per window",
                1, stubRegistry.pinningReports.size());
    }

    @Test
    public void testMultipleCodebasesIndependent() throws Exception {
        Uri[] uris1 = new Uri[]{uri1};
        Uri[] uris2 = new Uri[]{uri2};
        long now = System.currentTimeMillis();

        PinningReport r1 = new PinningReport(uris1, LOW_PINNED_NANOS_THRESHOLD,
                1L, now - 1000L, now);
        PinningReport r2 = new PinningReport(uris2, LOW_PINNED_NANOS_THRESHOLD,
                1L, now - 1000L, now);

        service.reportPinning(r1);
        service.reportPinning(r2);

        assertEquals("One submission per codebase", 2,
                stubRegistry.pinningReports.size());
    }

    @Test(expected = NullPointerException.class)
    public void testReportPinningRejectsNull() throws Exception {
        service.reportPinning(null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetPinnedNanosRejectsNull() throws Exception {
        service.getPinnedNanos(null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetPinCountRejectsNull() throws Exception {
        service.getPinCount(null);
    }

    @Test
    public void testNullVerdictRegistryLogsAndContinues() throws Exception {
        // Service with no VerdictRegistry — should not throw
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                LOW_PINNED_NANOS_THRESHOLD,
                LOW_EVENT_COUNT_THRESHOLD,
                SWEEP_INTERVAL_MINUTES,
                null);
        Uri[] uris = new Uri[]{uri1};
        long now = System.currentTimeMillis();
        PinningReport r = new PinningReport(uris, LOW_PINNED_NANOS_THRESHOLD + 1,
                1L, now - 1000L, now);
        // Must not throw even though registry is null.
        svc.reportPinning(r);
    }

    @Test
    public void testRetryAfterNullRegistryBecomesAvailable() throws Exception {
        // Service starts with no VerdictRegistry.
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                LOW_PINNED_NANOS_THRESHOLD,
                LOW_EVENT_COUNT_THRESHOLD,
                SWEEP_INTERVAL_MINUTES,
                null);
        Uri[] uris = new Uri[]{uri1};
        long now = System.currentTimeMillis();

        // First report (clientA) crosses threshold but VR is null — submitted
        // flag must be reset so that a later report can retry.
        PinningReport r1 = new PinningReport(uris, LOW_PINNED_NANOS_THRESHOLD + 1,
                1L, now - 2000L, now - 1000L);
        svc.reportPinning(r1, "clientA");

        // Now inject a real registry and send another report (from a different
        // client, so it is not deduped) for the same already-over-threshold
        // codebase; this must trigger the retry.
        StubVerdictRegistry registry = new StubVerdictRegistry();
        svc.setVerdictRegistry(registry);

        PinningReport r2 = new PinningReport(uris, 1L, 1L, now - 1000L, now);
        svc.reportPinning(r2, "clientB");

        assertEquals("Registry must receive the report after becoming available",
                1, registry.pinningReports.size());
    }

    @Test
    public void testCanonicalKeyIsOrderIndependent() {
        Set<Uri> a = makeUriSet(uri1, uri2);
        Set<Uri> b = makeUriSet(uri2, uri1);
        assertEquals(JfrTelemetryServiceImpl.codebaseKey(a),
                     JfrTelemetryServiceImpl.codebaseKey(b));
    }

    // -------------------------------------------------------------------------
    // Per-client rate-limit + dedup (STD-002)
    // -------------------------------------------------------------------------

    /**
     * One client cannot cross a codebase's event-count threshold on its own:
     * deduplication counts at most one report per (client, codebase) per window,
     * so repeated reports from a single identity for the same codebase are
     * dropped and the threshold is never reached.
     */
    @Test
    public void testDedup_SingleClientCannotInflateCodebase() throws Exception {
        // Threshold is 5 events; per-report cap is 1 event.
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                Long.MAX_VALUE, 5L, SWEEP_INTERVAL_MINUTES, stubRegistry,
                /*maxReportsPerClientPerWindow*/ 100,
                /*rateLimitWindowMillis*/ 60_000L,
                /*maxPinnedNanosPerReport*/ Long.MAX_VALUE,
                /*maxEventsPerReport*/ 1L);

        Uri[] uris = new Uri[]{uri1};
        long now = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            svc.reportPinning(
                    new PinningReport(uris, 0L, 1L, now - 1000L, now), "attacker");
        }

        assertTrue("Dedup must prevent one client from crossing the threshold",
                stubRegistry.pinningReports.isEmpty());
        // Exactly one (deduped) event should have been counted.
        assertEquals(1L, svc.getPinCount(makeUriSet(uri1)));
    }

    /**
     * The rate limiter drops reports once a client exceeds its per-window quota,
     * counting different codebases (which bypass dedup).
     */
    @Test
    public void testRateLimit_DropsExcessReportsFromOneClient() throws Exception {
        int quota = 3;
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                Long.MAX_VALUE, Long.MAX_VALUE, SWEEP_INTERVAL_MINUTES, stubRegistry,
                /*maxReportsPerClientPerWindow*/ quota,
                /*rateLimitWindowMillis*/ 60_000L,
                /*maxPinnedNanosPerReport*/ Long.MAX_VALUE,
                /*maxEventsPerReport*/ Long.MAX_VALUE);

        long now = System.currentTimeMillis();
        // Five DISTINCT codebases from the same client; only `quota` accepted.
        for (int i = 0; i < 5; i++) {
            Uri u = new Uri("https://example.com/cb-" + i + ".jar");
            svc.reportPinning(
                    new PinningReport(new Uri[]{u}, 7L, 1L, now - 1000L, now),
                    "noisy-client");
        }

        long accepted = 0;
        for (int i = 0; i < 5; i++) {
            accepted += svc.getPinCount(
                    makeUriSet(new Uri("https://example.com/cb-" + i + ".jar")));
        }
        assertEquals("Only the per-window quota of reports must be accepted",
                (long) quota, accepted);
    }

    /**
     * Different client identities are rate-limited independently: each client
     * gets its own quota.
     */
    @Test
    public void testRateLimit_DifferentClientsIndependent() throws Exception {
        int quota = 2;
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                Long.MAX_VALUE, Long.MAX_VALUE, SWEEP_INTERVAL_MINUTES, stubRegistry,
                quota, 60_000L, Long.MAX_VALUE, Long.MAX_VALUE);

        long now = System.currentTimeMillis();
        // clientA exhausts its quota across distinct codebases.
        for (int i = 0; i < 5; i++) {
            svc.reportPinning(new PinningReport(
                    new Uri[]{new Uri("https://a.example/" + i + ".jar")},
                    1L, 1L, now - 1000L, now), "clientA");
        }
        // clientB reports a fresh codebase — must still be accepted (own quota).
        Uri bUri = new Uri("https://b.example/lib.jar");
        svc.reportPinning(new PinningReport(
                new Uri[]{bUri}, 9L, 3L, now - 1000L, now), "clientB");

        assertEquals("clientB's report must be counted despite clientA's quota",
                3L, svc.getPinCount(makeUriSet(bUri)));
    }

    /**
     * A single forged report cannot contribute more than the per-report caps to
     * a codebase's totals.
     */
    @Test
    public void testPerReportCap_LimitsSingleReportContribution() throws Exception {
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                Long.MAX_VALUE, Long.MAX_VALUE, SWEEP_INTERVAL_MINUTES, stubRegistry,
                100, 60_000L,
                /*maxPinnedNanosPerReport*/ 1_000L,
                /*maxEventsPerReport*/ 2L);

        Uri[] uris = new Uri[]{uri1};
        long now = System.currentTimeMillis();
        // Forged huge values; must be capped to (1_000 ns, 2 events).
        svc.reportPinning(new PinningReport(
                uris, 999_999_999L, 999L, now - 1000L, now), "forger");

        assertEquals(1_000L, svc.getPinnedNanos(makeUriSet(uri1)));
        assertEquals(2L,     svc.getPinCount(makeUriSet(uri1)));
    }

    /**
     * The public {@code reportPinning} path falls back to a single shared
     * anonymous bucket when no client subject is available (e.g. a local call):
     * a second public call for the same codebase is deduped.
     */
    @Test
    public void testAnonymousFallback_DedupsLocalCalls() throws Exception {
        JfrTelemetryServiceImpl svc = new JfrTelemetryServiceImpl(
                Long.MAX_VALUE, Long.MAX_VALUE, SWEEP_INTERVAL_MINUTES, stubRegistry);

        Uri[] uris = new Uri[]{uri1};
        long now = System.currentTimeMillis();
        svc.reportPinning(new PinningReport(uris, 5L, 1L, now - 1000L, now));
        svc.reportPinning(new PinningReport(uris, 5L, 1L, now - 1000L, now));

        // Second anonymous report for the same codebase is deduped → only one
        // event counted.
        assertEquals(1L, svc.getPinCount(makeUriSet(uri1)));
    }

    /**
     * {@code currentClientId()} returns the anonymous bucket id when invoked
     * outside a remote call.
     */
    @Test
    public void testCurrentClientId_NotInRemoteCall_IsAnonymous() {
        assertEquals(JfrTelemetryServiceImpl.ANONYMOUS_CLIENT_ID,
                service.currentClientId());
    }

    /**
     * The subject-to-id derivation is stable and order-independent over the
     * subject's principals, and falls back to anonymous for an empty subject.
     */
    @Test
    public void testClientIdFromSubject_StableAndOrderIndependent() {
        java.security.Principal p1 =
                new javax.security.auth.x500.X500Principal("CN=alice");
        java.security.Principal p2 =
                new javax.security.auth.x500.X500Principal("CN=bob");

        java.util.Set<java.security.Principal> set1 =
                new java.util.LinkedHashSet<>();
        set1.add(p1); set1.add(p2);
        java.util.Set<java.security.Principal> set2 =
                new java.util.LinkedHashSet<>();
        set2.add(p2); set2.add(p1);

        javax.security.auth.Subject s1 = new javax.security.auth.Subject(
                false, set1, java.util.Collections.emptySet(),
                java.util.Collections.emptySet());
        javax.security.auth.Subject s2 = new javax.security.auth.Subject(
                false, set2, java.util.Collections.emptySet(),
                java.util.Collections.emptySet());

        assertEquals(JfrTelemetryServiceImpl.clientIdFromSubject(s1),
                     JfrTelemetryServiceImpl.clientIdFromSubject(s2));
        assertEquals(JfrTelemetryServiceImpl.ANONYMOUS_CLIENT_ID,
                JfrTelemetryServiceImpl.clientIdFromSubject(null));
        assertEquals(JfrTelemetryServiceImpl.ANONYMOUS_CLIENT_ID,
                JfrTelemetryServiceImpl.clientIdFromSubject(
                        new javax.security.auth.Subject()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Set<Uri> makeUriSet(Uri... uris) {
        java.util.LinkedHashSet<Uri> s = new java.util.LinkedHashSet<Uri>();
        for (Uri u : uris) s.add(u);
        return java.util.Collections.unmodifiableSet(s);
    }
}
