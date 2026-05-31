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

import au.net.zeus.jgdms.api.codebase.AnalysisException;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.BytecodeAnalysisEngine;
import au.net.zeus.jgdms.api.codebase.ClassAnalysisResult;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.downloader.CodebaseDownloaderImpl.EngineEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.net.Uri;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link CodebaseDownloaderImpl}.
 *
 * <p>These tests use the JDK's built-in {@link HttpServer} (in
 * {@code com.sun.net.httpserver}) to serve synthetic JAR content
 * on a loopback HTTP port, verifying the full download → hash →
 * analyse → submit pipeline without any real remote services.
 *
 * @since 3.1.1
 */
public class CodebaseDownloaderImplTest {

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /** Minimal one-class JAR used in tests. */
    private static byte[] MINIMAL_JAR;

    private HttpServer httpServer;
    private int httpPort;
    private String baseUrl;

    @Before
    public void setUp() throws Exception {
        MINIMAL_JAR = buildMinimalJar();

        // Start a loopback HTTP server.
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        httpServer.start();
        httpPort = httpServer.getAddress().getPort();
        baseUrl  = "http://localhost:" + httpPort;
    }

    @After
    public void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // -------------------------------------------------------------------------
    // Helper: build a minimal valid JAR in memory
    // -------------------------------------------------------------------------

    private static byte[] buildMinimalJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            JarEntry entry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(entry);
            byte[] manifest = "Manifest-Version: 1.0\n".getBytes("UTF-8");
            jos.write(manifest);
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Serves {@code bytes} at the given path with status 200. */
    private void serveBytes(String path, byte[] bytes) {
        httpServer.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type",
                    "application/java-archive");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    /** Serves an HTTP 404 at the given path. */
    private void serve404(String path) {
        httpServer.createContext(path, exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
    }

    // -------------------------------------------------------------------------
    // Stub VerdictRegistry
    // -------------------------------------------------------------------------

    private static final class CapturingVerdictRegistry implements VerdictRegistry {

        final Map<String, JarAnalysisReport> reports = new HashMap<>();

        @Override
        public synchronized void submitReport(String engineId,
                                              JarAnalysisReport report)
                throws RemoteException {
            reports.put(engineId, report);
        }

        @Override public void registerAnalysisEngine(String id, PublicKey k, String alg) {}
        @Override public void revokeAnalysisEngine(String id) {}
        @Override public void submitVerdict(String id, au.net.zeus.jgdms.api.codebase.SignedVerdict v) {}
        @Override public void reportCrash(au.net.zeus.jgdms.api.codebase.CrashReport r) {}
        @Override public EventRegistration registerVerdictListener(
                RemoteEventListener l, Set<Uri> u, MarshalledInstance h, long d) { return null; }
        @Override public long renewEventLease(Uuid id, long d) throws UnknownLeaseException { return 0; }
        @Override public void cancelEventLease(Uuid id) throws UnknownLeaseException {}
        @Override public au.net.zeus.jgdms.api.codebase.RegistryVerdict getVerdict(Set<Uri> u) { return null; }
        @Override public au.net.zeus.jgdms.api.codebase.RegistryVerdict getVerdictByHash(String h) { return null; }
        @Override public void reportPinning(au.net.zeus.jgdms.api.telemetry.PinningReport report) {}

        public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener, MarshalledInstance handback, long leaseDuration) throws RemoteException {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }

    // -------------------------------------------------------------------------
    // Stub BytecodeAnalysisEngine
    // -------------------------------------------------------------------------

    private static final class StubEngine implements BytecodeAnalysisEngine {

        private final String engineId;
        volatile AnalysisRequest lastRequest;
        private final CountDownLatch latch;

        StubEngine(String engineId, CountDownLatch latch) {
            this.engineId = engineId;
            this.latch    = latch;
        }

        @Override
        public JarAnalysisReport analyzeJar(AnalysisRequest request)
                throws AnalysisException, RemoteException {
            this.lastRequest = request;
            Map<String, ClassAnalysisResult> empty = Collections.emptyMap();
            // Build a minimal signed report with a dummy signature.
            JarAnalysisReport report = new JarAnalysisReport(
                    request.getContentHash(),
                    empty,
                    new byte[]{1, 2, 3});
            latch.countDown();
            return report;
        }

        @Override
        public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {}
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testSha256HexIsConsistent() {
        byte[] data = {0x01, 0x02, 0x03};
        String h1 = CodebaseDownloaderImpl.computeSha256Hex(data);
        String h2 = CodebaseDownloaderImpl.computeSha256Hex(data);
        Assert.assertEquals("SHA-256 should be deterministic", h1, h2);
        Assert.assertEquals("SHA-256 hex should be 64 chars", 64, h1.length());
        Assert.assertTrue("SHA-256 hex should be lowercase hex",
                h1.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testSha256HexDiffersForDifferentInput() {
        byte[] a = {0x01};
        byte[] b = {0x02};
        Assert.assertFalse("SHA-256 should differ for different inputs",
                CodebaseDownloaderImpl.computeSha256Hex(a).equals(
                        CodebaseDownloaderImpl.computeSha256Hex(b)));
    }

    @Test
    public void testDownloadJarSuccess() throws Exception {
        String path = "/test.jar";
        serveBytes(path, MINIMAL_JAR);

        CountDownLatch latch  = new CountDownLatch(1);
        StubEngine engine     = new StubEngine("e1", latch);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                Collections.singletonList(new EngineEntry("e1", engine)),
                vr, MINIMAL_JAR.length + 1, 5_000, 5_000, 1);

        Uri uri = new Uri(baseUrl + path);
        Set<Uri> uris = new LinkedHashSet<>();
        uris.add(uri);
        impl.submitForAnalysis(uris);

        Assert.assertTrue("analyzeJar should be called within 5s",
                latch.await(5, TimeUnit.SECONDS));

        impl.shutdown(5_000);

        Assert.assertEquals(1, vr.reports.size());
        Assert.assertNotNull(vr.reports.get("e1"));
        String expectedHash = CodebaseDownloaderImpl.computeSha256Hex(MINIMAL_JAR);
        Assert.assertEquals(expectedHash,
                engine.lastRequest.getContentHash());
    }

    @Test
    public void testDownloadJar404SkipsAnalysis() throws Exception {
        String path = "/missing.jar";
        serve404(path);

        CountDownLatch latch  = new CountDownLatch(1);
        StubEngine engine     = new StubEngine("e1", latch);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                Collections.singletonList(new EngineEntry("e1", engine)),
                vr, DEFAULT_MAX, 5_000, 5_000, 1);

        Uri uri = new Uri(baseUrl + path);
        Set<Uri> uris = new LinkedHashSet<>();
        uris.add(uri);
        impl.submitForAnalysis(uris);

        // Give the worker time to process the failure.
        boolean called = latch.await(2, TimeUnit.SECONDS);
        impl.shutdown(5_000);

        Assert.assertFalse("analyzeJar must NOT be called on 404", called);
        Assert.assertTrue("No reports should be submitted on 404",
                vr.reports.isEmpty());
    }

    private static final int DEFAULT_MAX = CodebaseDownloaderImpl.DEFAULT_MAX_JAR_SIZE_BYTES;

    @Test
    public void testDeduplicationByContentHash() throws Exception {
        String path = "/dedup.jar";
        serveBytes(path, MINIMAL_JAR);

        // Latch of 1: only one analysis should occur.
        CountDownLatch latch  = new CountDownLatch(1);
        StubEngine engine     = new StubEngine("e1", latch);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                Collections.singletonList(new EngineEntry("e1", engine)),
                vr, DEFAULT_MAX, 5_000, 5_000, 1);

        Uri uri = new Uri(baseUrl + path);
        Set<Uri> uris = Collections.singleton(uri);

        impl.submitForAnalysis(uris);

        // Wait for the analysis to complete.
        Assert.assertTrue("analyzeJar should be called within 5s",
                latch.await(5, TimeUnit.SECONDS));

        // Verify the hash is tracked so any future submission of the same
        // content will be deduplicated.
        String expectedHash = CodebaseDownloaderImpl.computeSha256Hex(MINIMAL_JAR);

        impl.shutdown(5_000);

        Assert.assertTrue("Hash should be in submittedHashes after analysis",
                impl.getSubmittedHashes().contains(expectedHash));
    }

    @Test
    public void testNonHttpSchemeIsRejected() throws Exception {
        CountDownLatch latch  = new CountDownLatch(1);
        StubEngine engine     = new StubEngine("e1", latch);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                Collections.singletonList(new EngineEntry("e1", engine)),
                vr, DEFAULT_MAX, 5_000, 5_000, 1);

        Uri uri = new Uri("ftp://example.com/something.jar");
        impl.submitForAnalysis(Collections.singleton(uri));

        boolean called = latch.await(2, TimeUnit.SECONDS);
        impl.shutdown(5_000);

        Assert.assertFalse("analyzeJar must NOT be called for non-http(s) URI", called);
        Assert.assertTrue(vr.reports.isEmpty());
    }

    @Test
    public void testMultipleBaesInPool() throws Exception {
        String path = "/multi-bae.jar";
        serveBytes(path, MINIMAL_JAR);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        StubEngine engine1   = new StubEngine("e1", latch1);
        StubEngine engine2   = new StubEngine("e2", latch2);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        java.util.List<EngineEntry> pool = new java.util.ArrayList<>();
        pool.add(new EngineEntry("e1", engine1));
        pool.add(new EngineEntry("e2", engine2));

        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                pool, vr, DEFAULT_MAX, 5_000, 5_000, 2);

        Uri uri = new Uri(baseUrl + path);
        impl.submitForAnalysis(Collections.singleton(uri));

        Assert.assertTrue("Engine 1 should analyse within 5s",
                latch1.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("Engine 2 should analyse within 5s",
                latch2.await(5, TimeUnit.SECONDS));

        impl.shutdown(5_000);

        Assert.assertEquals("Both engines should submit a report",
                2, vr.reports.size());
        Assert.assertNotNull(vr.reports.get("e1"));
        Assert.assertNotNull(vr.reports.get("e2"));
    }

    @Test
    public void testEngineEntryRejectsNullEngineId() {
        try {
            new EngineEntry(null, new StubEngine("x", new CountDownLatch(0)));
            Assert.fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // pass
        }
    }

    @Test
    public void testEngineEntryRejectsEmptyEngineId() {
        try {
            new EngineEntry("", new StubEngine("x", new CountDownLatch(0)));
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    public void testEngineEntryRejectsNullEngine() {
        try {
            new EngineEntry("valid-id", null);
            Assert.fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // pass
        }
    }

    @Test
    public void testConstructorRejectsEmptyBaePool() {
        try {
            new CodebaseDownloaderImpl(
                    Collections.emptyList(),
                    new CapturingVerdictRegistry());
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    public void testConstructorRejectsNullVerdictRegistry() {
        StubEngine engine = new StubEngine("e1", new CountDownLatch(0));
        try {
            new CodebaseDownloaderImpl(
                    Collections.singletonList(new EngineEntry("e1", engine)),
                    null);
            Assert.fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // pass
        }
    }

    @Test
    public void testJarSizeLimitEnforced() throws Exception {
        String path = "/huge.jar";
        serveBytes(path, MINIMAL_JAR);

        CountDownLatch latch  = new CountDownLatch(1);
        StubEngine engine     = new StubEngine("e1", latch);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        // Set limit to 1 byte — smaller than any valid JAR.
        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                Collections.singletonList(new EngineEntry("e1", engine)),
                vr, 1 /* 1-byte limit */, 5_000, 5_000, 1);

        Uri uri = new Uri(baseUrl + path);
        impl.submitForAnalysis(Collections.singleton(uri));

        boolean called = latch.await(2, TimeUnit.SECONDS);
        impl.shutdown(5_000);

        Assert.assertFalse("analyzeJar must NOT be called when size limit exceeded",
                called);
        Assert.assertTrue(vr.reports.isEmpty());
    }

    /**
     * Verifies that an HTTP redirect response (3xx) is treated as a failure
     * rather than being followed.  This prevents SSRF attacks where a
     * malicious server could redirect the downloader to an internal address.
     */
    @Test
    public void testRedirectIsNotFollowed() throws Exception {
        String targetPath   = "/redirect-target.jar";
        String redirectPath = "/redirect-source.jar";

        // Serve the real JAR at the target path.
        serveBytes(targetPath, MINIMAL_JAR);

        // Serve a 302 redirect at the source path.
        httpServer.createContext(redirectPath, exchange -> {
            exchange.getResponseHeaders().add("Location",
                    baseUrl + targetPath);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        CountDownLatch latch  = new CountDownLatch(1);
        StubEngine engine     = new StubEngine("e1", latch);
        CapturingVerdictRegistry vr = new CapturingVerdictRegistry();

        CodebaseDownloaderImpl impl = new CodebaseDownloaderImpl(
                Collections.singletonList(new EngineEntry("e1", engine)),
                vr, DEFAULT_MAX, 5_000, 5_000, 1);

        Uri uri = new Uri(baseUrl + redirectPath);
        impl.submitForAnalysis(Collections.singleton(uri));

        boolean called = latch.await(2, TimeUnit.SECONDS);
        impl.shutdown(5_000);

        Assert.assertFalse(
                "analyzeJar must NOT be called when server returns a redirect",
                called);
        Assert.assertTrue(
                "No report should be submitted for a redirected URI",
                vr.reports.isEmpty());
    }
}
