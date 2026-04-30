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
package au.net.zeus.jgdms.bae;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import org.apache.river.api.net.Uri;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BytecodeAnalysisEngineImpl}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Constructor argument guards</li>
 *   <li>{@link BytecodeAnalysisEngineImpl#requestAnalysis} argument guards</li>
 *   <li>{@link BytecodeAnalysisEngineImpl#containsDangerousCode} — all dangerous
 *       patterns, safe classes, and malformed inputs</li>
 *   <li>{@link BytecodeAnalysisEngineImpl#canonicalBytes} — canonical byte
 *       representation used for signing</li>
 *   <li>Metrics collection — queue, analysis, verdict, and submission counters</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class BytecodeAnalysisEngineImplTest {

    private static final String SIG_ALGORITHM = "SHA256withRSA";

    private KeyPair engineKeyPair;
    private VerdictRegistry mockRegistry;

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        engineKeyPair = kpg.generateKeyPair();

        // Minimal mock: only requestAnalysis() behaviour matters here;
        // we just need the constructor to accept a non-null registry.
        mockRegistry = new VerdictRegistry() {
            @Override
            public void registerAnalysisEngine(String id,
                    java.security.PublicKey k, String alg) {}
            @Override
            public void revokeAnalysisEngine(String id) {}
            @Override
            public void submitVerdict(String id,
                    au.net.zeus.jgdms.api.codebase.SignedVerdict v) {}
            @Override
            public void reportCrash(
                    au.net.zeus.jgdms.api.codebase.CrashReport r) {}
            @Override
            public au.net.zeus.jgdms.api.codebase.RegistryVerdict getVerdict(
                    Set<Uri> u) { return null; }
            @Override
            public net.jini.core.event.EventRegistration registerVerdictListener(
                    net.jini.core.event.RemoteEventListener l,
                    Set<Uri> u,
                    net.jini.io.MarshalledInstance h,
                    long d) { return null; }
            @Override
            public long renewEventLease(net.jini.id.Uuid id, long d)
                    throws net.jini.core.lease.UnknownLeaseException { return d; }
            @Override
            public void cancelEventLease(net.jini.id.Uuid id)
                    throws net.jini.core.lease.UnknownLeaseException {}
        };
    }

    // =========================================================================
    // Constructor guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testConstructorNullPrivateKeyThrowsNPE() {
        new BytecodeAnalysisEngineImpl(null, SIG_ALGORITHM, "e1", mockRegistry);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullSigAlgorithmThrowsNPE() {
        new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), null, "e1", mockRegistry);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptySigAlgorithmThrowsIAE() {
        new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), "", "e1", mockRegistry);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullEngineIdThrowsNPE() {
        new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, null, mockRegistry);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyEngineIdThrowsIAE() {
        new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "", mockRegistry);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRegistryThrowsNPE() {
        new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", null);
    }

    // =========================================================================
    // requestAnalysis argument guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testRequestAnalysisNullThrowsNPE() throws RemoteException {
        BytecodeAnalysisEngineImpl impl = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry);
        impl.requestAnalysis(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRequestAnalysisEmptySetThrowsIAE() throws RemoteException {
        BytecodeAnalysisEngineImpl impl = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry);
        impl.requestAnalysis(Collections.<Uri>emptySet());
    }

    // =========================================================================
    // containsDangerousCode — byte array too short
    // =========================================================================

    @Test
    public void testContainsDangerousCode_TooShort_IsTrue() {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                new byte[]{(byte) 0xCA, (byte) 0xFE}));
    }

    @Test
    public void testContainsDangerousCode_ExactlyNineBytes_IsTrue() {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                new byte[9]));
    }

    // =========================================================================
    // containsDangerousCode — invalid magic number
    // =========================================================================

    @Test
    public void testContainsDangerousCode_BadMagic_IsTrue() {
        byte[] bytes = new byte[10];
        bytes[0] = 0; // magic should be 0xCAFEBABE
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(bytes));
    }

    // =========================================================================
    // containsDangerousCode — clean class (no dangerous entries)
    // =========================================================================

    @Test
    public void testContainsDangerousCode_CleanClass_IsFalse() throws IOException {
        byte[] classBytes = buildClassWithUtf8("Hello", "world");
        assertFalse(BytecodeAnalysisEngineImpl.containsDangerousCode(classBytes));
    }

    @Test
    public void testContainsDangerousCode_EmptyConstantPool_IsFalse() throws IOException {
        // cp_count = 1 means no entries (constant pool indices start at 1).
        byte[] classBytes = buildClassWithUtf8(/* no entries */);
        assertFalse(BytecodeAnalysisEngineImpl.containsDangerousCode(classBytes));
    }

    // =========================================================================
    // containsDangerousCode — each dangerous pattern
    // =========================================================================

    @Test
    public void testContainsDangerousCode_Runtime_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/Runtime")));
    }

    @Test
    public void testContainsDangerousCode_ProcessBuilder_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/ProcessBuilder")));
    }

    @Test
    public void testContainsDangerousCode_ProcessImpl_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/ProcessImpl")));
    }

    @Test
    public void testContainsDangerousCode_SunMiscUnsafe_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("sun/misc/Unsafe")));
    }

    @Test
    public void testContainsDangerousCode_JdkInternalUnsafe_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("jdk/internal/misc/Unsafe")));
    }

    @Test
    public void testContainsDangerousCode_ClassLoader_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/ClassLoader")));
    }

    // =========================================================================
    // containsDangerousCode — loadLibrary is no longer a dangerous pattern
    // =========================================================================

    @Test
    public void testContainsDangerousCode_LoadLibrary_IsFalse() throws IOException {
        // "loadLibrary" was removed from DANGEROUS_CP_ENTRIES; native access
        // is governed by the security policy, not bytecode analysis.
        assertFalse(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("loadLibrary")));
    }

    // =========================================================================
    // containsDangerousCode — new patterns added in extended detection
    // =========================================================================

    @Test
    public void testContainsDangerousCode_ReflectionMethod_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/reflect/Method")));
    }

    @Test
    public void testContainsDangerousCode_ReflectionField_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/reflect/Field")));
    }

    @Test
    public void testContainsDangerousCode_ReflectionConstructor_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/reflect/Constructor")));
    }

    @Test
    public void testContainsDangerousCode_ReflectionProxy_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/reflect/Proxy")));
    }

    @Test
    public void testContainsDangerousCode_MethodHandle_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/invoke/MethodHandle")));
    }

    @Test
    public void testContainsDangerousCode_VarHandle_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/invoke/VarHandle")));
    }

    @Test
    public void testContainsDangerousCode_MethodHandles_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/invoke/MethodHandles")));
    }

    @Test
    public void testContainsDangerousCode_ASM_IsTrue() throws IOException {
        // Prefix pattern "jdk/internal/org/objectweb/asm/" should match any class in that package
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("jdk/internal/org/objectweb/asm/ClassWriter")));
    }

    @Test
    public void testContainsDangerousCode_Javassist_IsTrue() throws IOException {
        // Prefix pattern "javassist/" should match any class in the javassist package hierarchy
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("javassist/ClassPool")));
    }

    @Test
    public void testContainsDangerousCode_ByteBuddy_IsTrue() throws IOException {
        // Prefix pattern "net/bytebuddy/" should match any ByteBuddy class
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("net/bytebuddy/ByteBuddy")));
    }

    @Test
    public void testContainsDangerousCode_ScriptEngine_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("javax/script/ScriptEngine")));
    }

    @Test
    public void testContainsDangerousCode_FileOutputStream_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/io/FileOutputStream")));
    }

    @Test
    public void testContainsDangerousCode_Module_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/Module")));
    }

    @Test
    public void testContainsDangerousCode_SharedSecrets_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("sun/misc/SharedSecrets")));
    }

    // =========================================================================
    // containsDangerousCode — custom pattern list
    // =========================================================================

    @Test
    public void testContainsDangerousCode_CustomPatterns_IsTrue() throws IOException {
        // A pattern that is NOT in the default list; the class bytes contain it.
        String customPattern = "com/example/dangerous/EvilClass";
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8(customPattern),
                new String[]{customPattern}));
    }

    @Test
    public void testContainsDangerousCode_CustomPrefixPattern_IsTrue() throws IOException {
        // A prefix pattern (ending with '/') should match any sub-class name.
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("com/example/evil/EvilSubClass"),
                new String[]{"com/example/evil/"}));
    }

    @Test
    public void testContainsDangerousCode_NoPatterns_EmptyList_IsFalse() throws IOException {
        // With an empty pattern list even a normally-dangerous class is allowed.
        assertFalse(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/Runtime"),
                new String[0]));
    }

    @Test
    public void testContainsDangerousCode_CustomPatterns_DefaultDangerousNotDetected()
            throws IOException {
        // Custom list has only one entry; default dangerous patterns are NOT matched.
        assertFalse(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("java/lang/Runtime"),
                new String[]{"com/example/other/Class"}));
    }



    @Test
    public void testContainsDangerousCode_DangerousAmongBenign_IsTrue() throws IOException {
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(
                buildClassWithUtf8("foo", "bar", "java/lang/Runtime", "baz")));
    }

    // =========================================================================
    // canonicalBytes — determinism and content
    // =========================================================================

    @Test
    public void testCanonicalBytes_DeterministicForSameInput()
            throws IOException, URISyntaxException {
        Uri[] uris = new Uri[]{ new Uri("http://example.com/a.jar") };
        byte[] b1 = BytecodeAnalysisEngineImpl.canonicalBytes(
                uris, au.net.zeus.jgdms.api.codebase.VerdictType.SAFE, 1000L);
        byte[] b2 = BytecodeAnalysisEngineImpl.canonicalBytes(
                uris, au.net.zeus.jgdms.api.codebase.VerdictType.SAFE, 1000L);
        assertTrue(java.util.Arrays.equals(b1, b2));
    }

    @Test
    public void testCanonicalBytes_DifferentVerdicts_Differ()
            throws IOException, URISyntaxException {
        Uri[] uris = new Uri[]{ new Uri("http://example.com/a.jar") };
        byte[] safe = BytecodeAnalysisEngineImpl.canonicalBytes(
                uris, au.net.zeus.jgdms.api.codebase.VerdictType.SAFE, 1000L);
        byte[] dangerous = BytecodeAnalysisEngineImpl.canonicalBytes(
                uris, au.net.zeus.jgdms.api.codebase.VerdictType.DANGEROUS, 1000L);
        assertFalse(java.util.Arrays.equals(safe, dangerous));
    }

    @Test
    public void testCanonicalBytes_DifferentTimestamps_Differ()
            throws IOException, URISyntaxException {
        Uri[] uris = new Uri[]{ new Uri("http://example.com/a.jar") };
        byte[] b1 = BytecodeAnalysisEngineImpl.canonicalBytes(
                uris, au.net.zeus.jgdms.api.codebase.VerdictType.SAFE, 1000L);
        byte[] b2 = BytecodeAnalysisEngineImpl.canonicalBytes(
                uris, au.net.zeus.jgdms.api.codebase.VerdictType.SAFE, 2000L);
        assertFalse(java.util.Arrays.equals(b1, b2));
    }

    // =========================================================================
    // requestAnalysis — bounded-queue rejection
    // =========================================================================

    /**
     * When the executor's work queue is full, {@code requestAnalysis} must
     * propagate a {@link RejectedExecutionException} to the caller.
     *
     * <p>A {@link SynchronousQueue} (zero-capacity hand-off) paired with a
     * single-thread pool ensures that a second concurrent submission is
     * rejected as soon as the one thread is occupied.
     */
    @Test(expected = RejectedExecutionException.class)
    public void testRequestAnalysis_QueueFull_ThrowsRejectedExecutionException()
            throws Exception {
        final CountDownLatch taskStarted  = new CountDownLatch(1);
        final CountDownLatch releaseTask  = new CountDownLatch(1);

        // 1 thread, zero-capacity queue → any submission while the thread is
        // busy is immediately rejected.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry,
                executor);

        // Occupy the single worker thread.
        executor.execute(() -> {
            taskStarted.countDown();
            try { releaseTask.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        taskStarted.await(); // wait until the worker is truly running

        try {
            Set<Uri> uris = Collections.singleton(new Uri("http://example.com/a.jar"));
            // Thread is busy, queue has no capacity → must be rejected.
            engine.requestAnalysis(uris);
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * When the queue has capacity, {@code requestAnalysis} must accept the
     * task without throwing an exception.
     */
    @Test
    public void testRequestAnalysis_WithCapacity_Succeeds() throws Exception {
        // 1 thread, queue of 10 — plenty of room for a single task.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry,
                executor);

        try {
            // Should not throw — queue is empty, task is accepted.
            Set<Uri> uris = Collections.singleton(new Uri("http://example.com/b.jar"));
            engine.requestAnalysis(uris);
        } finally {
            executor.shutdownNow();
        }
    }


    // =========================================================================
    // AnalysisTaskWithTimeout — timeout fires → DANGEROUS verdict
    // =========================================================================

    /**
     * When the codebase server accepts the TCP connection but never sends any
     * HTTP response, {@code analyzeCodebase} blocks indefinitely on
     * {@code url.openStream()}.  After the configured timeout the engine must
     * submit a {@link VerdictType#DANGEROUS} verdict to the registry as a
     * fail-safe.
     */
    @Test
    public void testAnalysisTask_Timeout_SubmitsDangerousVerdict() throws Exception {
        // Start a server that accepts connections but never responds.
        final ServerSocket blockingServer = new ServerSocket(0);
        Thread blockingServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket client = blockingServer.accept();
                    // Hold the connection open without sending any data.
                    Thread.sleep(30_000L);
                    client.close();
                } catch (Exception ignored) {}
            }
        });
        blockingServerThread.setDaemon(true);
        blockingServerThread.start();

        final AtomicReference<VerdictType> submittedVerdict = new AtomicReference<>();
        final CountDownLatch verdictLatch = new CountDownLatch(1);
        VerdictRegistry trackingRegistry = createTrackingRegistry(submittedVerdict, verdictLatch);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadPoolExecutor.AbortPolicy());
        // Use a 400 ms timeout so the test completes quickly.
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", trackingRegistry,
                executor, 400L);
        try {
            Set<Uri> uris = Collections.singleton(
                    new Uri("http://localhost:" + blockingServer.getLocalPort() + "/test.jar"));
            engine.requestAnalysis(uris);
            assertTrue("Timeout verdict not received within 5 s",
                    verdictLatch.await(5, TimeUnit.SECONDS));
            assertEquals(VerdictType.DANGEROUS, submittedVerdict.get());
        } finally {
            blockingServer.close();
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // AnalysisTaskWithTimeout — completes before timeout → correct verdict
    // =========================================================================

    /**
     * When the analysis completes normally (before the timeout), the engine
     * must submit the actual verdict — {@link VerdictType#SAFE} for an empty
     * JAR that contains no dangerous patterns — not a spurious DANGEROUS.
     */
    @Test
    public void testAnalysisTask_CompletesBeforeTimeout_SubmitsSafeVerdict() throws Exception {
        // Build an empty JAR (no class files → no dangerous patterns → SAFE).
        java.io.File safeTestJar = java.io.File.createTempFile("bae-safe-test", ".jar");
        safeTestJar.deleteOnExit();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(safeTestJar))) {
            // intentionally empty
        }

        final AtomicReference<VerdictType> submittedVerdict = new AtomicReference<>();
        final CountDownLatch verdictLatch = new CountDownLatch(1);
        VerdictRegistry trackingRegistry = createTrackingRegistry(submittedVerdict, verdictLatch);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadPoolExecutor.AbortPolicy());
        // Generous 10 s timeout — analysis should finish almost instantly.
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", trackingRegistry,
                executor, 10_000L);
        try {
            Set<Uri> uris = Collections.singleton(new Uri(safeTestJar.toURI().toString()));
            engine.requestAnalysis(uris);
            assertTrue("Safe verdict not received within 5 s",
                    verdictLatch.await(5, TimeUnit.SECONDS));
            assertEquals(VerdictType.SAFE, submittedVerdict.get());
        } finally {
            safeTestJar.delete();
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // AnalysisTaskWithTimeout — timeout releases the executor thread
    // =========================================================================

    /**
     * After a timeout fires, the executor thread that was waiting on
     * {@code FutureTask.get()} must be released and available to process
     * subsequent tasks.  This test verifies that the engine is not deadlocked
     * after a timeout by submitting a second (fast) task and confirming it
     * also produces a verdict.
     */
    @Test
    public void testAnalysisTask_TimeoutInterrupts_Thread() throws Exception {
        // First server: blocks the initial analysis task.
        final ServerSocket blockingServer = new ServerSocket(0);
        Thread blockingServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket client = blockingServer.accept();
                    Thread.sleep(30_000L);
                    client.close();
                } catch (Exception ignored) {}
            }
        });
        blockingServerThread.setDaemon(true);
        blockingServerThread.start();

        // Second task: an empty JAR served from the local file system.
        java.io.File fastCompletionJar = java.io.File.createTempFile("bae-timeout-interrupt", ".jar");
        fastCompletionJar.deleteOnExit();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(fastCompletionJar))) {
            // intentionally empty
        }

        // Registry that counts how many verdicts arrive.
        final CountDownLatch twoVerdicts = new CountDownLatch(2);
        VerdictRegistry countingRegistry = new VerdictRegistry() {
            @Override
            public void registerAnalysisEngine(String id,
                    java.security.PublicKey k, String alg) {}
            @Override
            public void revokeAnalysisEngine(String id) {}
            @Override
            public void submitVerdict(String id, SignedVerdict v) {
                twoVerdicts.countDown();
            }
            @Override
            public void reportCrash(
                    au.net.zeus.jgdms.api.codebase.CrashReport r) {}
            @Override
            public au.net.zeus.jgdms.api.codebase.RegistryVerdict getVerdict(
                    Set<Uri> u) { return null; }
            @Override
            public net.jini.core.event.EventRegistration registerVerdictListener(
                    net.jini.core.event.RemoteEventListener l,
                    Set<Uri> u,
                    net.jini.io.MarshalledInstance h,
                    long d) { return null; }
            @Override
            public long renewEventLease(net.jini.id.Uuid id, long d)
                    throws net.jini.core.lease.UnknownLeaseException { return d; }
            @Override
            public void cancelEventLease(net.jini.id.Uuid id)
                    throws net.jini.core.lease.UnknownLeaseException {}
        };

        // Two-thread pool; 400 ms timeout.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadPoolExecutor.AbortPolicy());
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", countingRegistry,
                executor, 400L);
        try {
            // Submit the blocking task first.
            Set<Uri> blockingUris = Collections.singleton(
                    new Uri("http://localhost:" + blockingServer.getLocalPort() + "/test.jar"));
            engine.requestAnalysis(blockingUris);

            // Submit the fast (safe-JAR) task immediately after.
            Set<Uri> safeUris = Collections.singleton(new Uri(fastCompletionJar.toURI().toString()));
            engine.requestAnalysis(safeUris);

            // Both tasks must produce a verdict within a reasonable window.
            // The first times out (→ DANGEROUS), the second completes fast (→ SAFE).
            assertTrue("Both verdicts not received within 6 s",
                    twoVerdicts.await(6, TimeUnit.SECONDS));
        } finally {
            blockingServer.close();
            fastCompletionJar.delete();
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================


    /**
     * Returns a minimal {@link VerdictRegistry} that stores the last verdict
     * type passed to {@link VerdictRegistry#submitVerdict} in
     * {@code verdictRef} and counts down {@code latch} once.
     */
    private static VerdictRegistry createTrackingRegistry(
            final AtomicReference<VerdictType> verdictRef,
            final CountDownLatch latch) {
        return new VerdictRegistry() {
            @Override
            public void registerAnalysisEngine(String id,
                    java.security.PublicKey k, String alg) {}
            @Override
            public void revokeAnalysisEngine(String id) {}
            @Override
            public void submitVerdict(String id, SignedVerdict v) {
                verdictRef.set(v.getVerdict());
                latch.countDown();
            }
            @Override
            public void reportCrash(
                    au.net.zeus.jgdms.api.codebase.CrashReport r) {}
            @Override
            public au.net.zeus.jgdms.api.codebase.RegistryVerdict getVerdict(
                    Set<Uri> u) { return null; }
            @Override
            public net.jini.core.event.EventRegistration registerVerdictListener(
                    net.jini.core.event.RemoteEventListener l,
                    Set<Uri> u,
                    net.jini.io.MarshalledInstance h,
                    long d) { return null; }
            @Override
            public long renewEventLease(net.jini.id.Uuid id, long d)
                    throws net.jini.core.lease.UnknownLeaseException { return d; }
            @Override
            public void cancelEventLease(net.jini.id.Uuid id)
                    throws net.jini.core.lease.UnknownLeaseException {}
        };
    }

    /**
     * Builds a minimal synthetic class file with the supplied {@code CONSTANT_Utf8}
     * entries in the constant pool.  The file is intentionally skeletal: it
     * contains only a valid magic number, version, and the requested CP entries;
     * the remaining class-file fields are absent, but
     * {@link BytecodeAnalysisEngineImpl#containsDangerousCode} only reads
     * the constant pool and therefore accepts this form.
     */
    private static byte[] buildClassWithUtf8(String... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        // Magic
        dos.writeInt(0xCAFEBABE);
        // Minor / major version (Java 8)
        dos.writeShort(0);
        dos.writeShort(52);
        // Constant pool count: one-based, so N entries → count = N + 1
        dos.writeShort(entries.length + 1);
        for (String entry : entries) {
            byte[] utf8 = entry.getBytes(StandardCharsets.UTF_8);
            dos.writeByte(1); // CONSTANT_Utf8 tag
            dos.writeShort(utf8.length);
            dos.write(utf8);
        }
        dos.flush();
        return baos.toByteArray();
    }

    // =========================================================================
    // Metrics — queue
    // =========================================================================

    /**
     * Verifies that {@link BytecodeAnalysisEngineImpl#getMetrics()} exposes
     * {@code queue.totalRejections} and that the counter is incremented when
     * {@link BytecodeAnalysisEngineImpl.LoggingAbortPolicy} fires.
     */
    @Test
    public void testMetrics_QueueRejection_IncrementCounter() throws Exception {
        final CountDownLatch taskStarted = new CountDownLatch(1);
        final CountDownLatch releaseTask = new CountDownLatch(1);

        // 1 thread, zero-capacity queue → any submission while thread is busy
        // triggers the rejection handler.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        // Replace the executor's rejection handler with one that updates the
        // engine's rejectedTaskCount (simulating what the production executor
        // does via LoggingAbortPolicy).
        executor.setRejectedExecutionHandler(
                new BytecodeAnalysisEngineImpl.LoggingAbortPolicy(engine.rejectedTaskCount));

        // Occupy the single worker thread.
        executor.execute(() -> {
            taskStarted.countDown();
            try { releaseTask.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        taskStarted.await();

        @SuppressWarnings("unchecked")
        Map<String, Object> before = engine.getMetrics();
        assertEquals(0L, ((Map<String, Object>) before.get("queue")).get("totalRejections"));

        Set<Uri> uris = Collections.singleton(new Uri("http://example.com/a.jar"));
        try {
            engine.requestAnalysis(uris);
        } catch (RejectedExecutionException expected) {
            // expected
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> after = engine.getMetrics();
        assertEquals(1L, ((Map<String, Object>) after.get("queue")).get("totalRejections"));
        assertEquals(1L, engine.totalRejections());
    }

    /**
     * Verifies that the peak queue depth is tracked and exposed via
     * {@link BytecodeAnalysisEngineImpl#getMetrics()}.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_PeakQueueDepth_Tracked() throws Exception {
        final CountDownLatch taskRunning = new CountDownLatch(1);
        final CountDownLatch releaseTask = new CountDownLatch(1);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        Map<String, Object> initial = engine.getMetrics();
        assertEquals(0L, ((Map<String, Object>) initial.get("queue")).get("peakDepth"));

        // Occupy the single thread so that subsequent submissions queue up.
        executor.execute(() -> {
            taskRunning.countDown();
            try { releaseTask.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        taskRunning.await();

        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_peak_test.jar"));
        engine.requestAnalysis(uris);

        Map<String, Object> after = engine.getMetrics();
        assertTrue((long) ((Map<String, Object>) after.get("queue")).get("peakDepth") >= 1L);

        releaseTask.countDown();
        executor.shutdownNow();
    }

    // =========================================================================
    // Metrics — analysis attempts and duration
    // =========================================================================

    /**
     * Each call to {@link BytecodeAnalysisEngineImpl#requestAnalysis} must
     * increment {@code analysis.totalAttempts} by one.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_AnalysisAttempt_Incremented() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_attempt.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, Object> metrics = engine.getMetrics();
        Map<String, Object> analysis = (Map<String, Object>) metrics.get("analysis");
        assertEquals(1L, analysis.get("totalAttempts"));
        assertEquals(1L, analysis.get("totalCompleted"));
    }

    /**
     * After a completed analysis, at least one duration histogram bucket must
     * have been incremented.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_AnalysisDuration_RecordedInBucket() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_duration.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, Object> metrics = engine.getMetrics();
        Map<String, Object> analysis = (Map<String, Object>) metrics.get("analysis");
        Map<String, Object> buckets = (Map<String, Object>) analysis.get("durationBuckets");

        long totalInBuckets = (long) buckets.get("0_100ms")
                + (long) buckets.get("100_500ms")
                + (long) buckets.get("500_1000ms")
                + (long) buckets.get("1_5s")
                + (long) buckets.get("5_10s")
                + (long) buckets.get("10s_plus");
        assertEquals(1L, totalInBuckets);
    }

    /**
     * Calling {@link BytecodeAnalysisEngineImpl#recordAnalysisTimeout()} must
     * increment {@code analysis.totalTimeouts} in the metrics snapshot.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_Timeout_Incremented() {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry);

        Map<String, Object> before = engine.getMetrics();
        assertEquals(0L,
                ((Map<String, Object>) before.get("analysis")).get("totalTimeouts"));

        engine.recordAnalysisTimeout();

        Map<String, Object> after = engine.getMetrics();
        assertEquals(1L,
                ((Map<String, Object>) after.get("analysis")).get("totalTimeouts"));
    }

    // =========================================================================
    // Metrics — parse errors
    // =========================================================================

    /**
     * An IOException while reading a JAR must increment
     * {@code analysis.classParseErrors}.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_ParseError_Incremented() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        // A non-existent file URL causes IOException in analyzeCodebase,
        // which increments classParseErrors.
        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_parse_error.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, Object> metrics = engine.getMetrics();
        Map<String, Object> analysis = (Map<String, Object>) metrics.get("analysis");
        assertEquals(1L, analysis.get("classParseErrors"));
    }

    // =========================================================================
    // Metrics — verdict distribution
    // =========================================================================

    /**
     * A non-existent JAR URL yields a DANGEROUS verdict.  The
     * {@code verdict.dangerous} counter must be 1 and {@code verdict.safe}
     * must remain 0.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_VerdictCounts_Dangerous() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_verdict.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, Object> metrics = engine.getMetrics();
        Map<String, Object> verdict = (Map<String, Object>) metrics.get("verdict");
        assertEquals(1L, verdict.get("dangerous"));
        assertEquals(0L, verdict.get("safe"));
    }

    /**
     * Analysing a JAR whose class files contain no dangerous patterns must
     * increment {@code verdict.safe} and leave {@code verdict.dangerous} at 0.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_VerdictCounts_Safe() throws Exception {
        File safeJar = createMinimalSafeJar();
        try {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    1, 1, 0L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(10),
                    Executors.defaultThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());

            BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                    engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

            Set<Uri> uris = Collections.singleton(
                    new Uri(safeJar.toURI().toString()));
            engine.requestAnalysis(uris);

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            Map<String, Object> metrics = engine.getMetrics();
            Map<String, Object> verdict = (Map<String, Object>) metrics.get("verdict");
            assertEquals(1L, verdict.get("safe"));
            assertEquals(0L, verdict.get("dangerous"));
        } finally {
            safeJar.delete();
        }
    }

    // =========================================================================
    // Metrics — registry submission
    // =========================================================================

    /**
     * A successful {@code registry.submitVerdict()} call must increment
     * {@code submission.successful} and record a latency observation.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_RegistrySubmissionSuccess_Counted() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_submit.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, Object> metrics = engine.getMetrics();
        Map<String, Object> submission = (Map<String, Object>) metrics.get("submission");
        assertEquals(1L, submission.get("successful"));
        assertEquals(0L, submission.get("failed"));

        Map<String, Object> latencyBuckets =
                (Map<String, Object>) submission.get("latencyBuckets");
        long totalLatency = (long) latencyBuckets.get("0_100ms")
                + (long) latencyBuckets.get("100_500ms")
                + (long) latencyBuckets.get("500_1000ms")
                + (long) latencyBuckets.get("1000ms_plus");
        assertEquals(1L, totalLatency);
    }

    /**
     * When {@code registry.submitVerdict()} throws, {@code submission.failed}
     * must be incremented and the exception class must appear in
     * {@code submission.errorsByType}.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_RegistrySubmissionFailure_Counted() throws Exception {
        // Registry that always throws on submitVerdict.
        VerdictRegistry failingRegistry = new VerdictRegistry() {
            @Override public void registerAnalysisEngine(String id,
                    java.security.PublicKey k, String alg) {}
            @Override public void revokeAnalysisEngine(String id) {}
            @Override public void submitVerdict(String id, SignedVerdict v)
                    throws RemoteException {
                throw new RemoteException("simulated failure");
            }
            @Override public void reportCrash(
                    au.net.zeus.jgdms.api.codebase.CrashReport r) {}
            @Override public au.net.zeus.jgdms.api.codebase.RegistryVerdict getVerdict(
                    Set<Uri> u) { return null; }
            @Override public net.jini.core.event.EventRegistration registerVerdictListener(
                    net.jini.core.event.RemoteEventListener l, Set<Uri> u,
                    net.jini.io.MarshalledInstance h, long d) { return null; }
            @Override public long renewEventLease(net.jini.id.Uuid id, long d)
                    throws net.jini.core.lease.UnknownLeaseException { return d; }
            @Override public void cancelEventLease(net.jini.id.Uuid id)
                    throws net.jini.core.lease.UnknownLeaseException {}
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", failingRegistry, executor);

        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_fail.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, Object> metrics = engine.getMetrics();
        Map<String, Object> submission = (Map<String, Object>) metrics.get("submission");
        assertEquals(0L, submission.get("successful"));
        assertEquals(1L, submission.get("failed"));

        Map<String, Long> errorsByType =
                (Map<String, Long>) submission.get("errorsByType");
        assertTrue(errorsByType.containsKey("java.rmi.RemoteException"));
        assertEquals(1L, (long) errorsByType.get("java.rmi.RemoteException"));
    }

    // =========================================================================
    // Metrics — thread safety
    // =========================================================================

    /**
     * Concurrent calls to {@link BytecodeAnalysisEngineImpl#getMetrics()} must
     * never throw.
     */
    @Test
    public void testMetrics_Snapshot_ThreadSafe() throws Exception {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry);

        // Artificially inflate counters to make the snapshot non-trivial.
        engine.rejectedTaskCount.incrementAndGet();
        engine.recordAnalysisTimeout();

        int threads = 8;
        int iterations = 500;
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done  = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < iterations; j++) {
                    try {
                        assertNotNull(engine.getMetrics());
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                }
                done.countDown();
            }).start();
        }

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);

        assertTrue("Concurrent getMetrics() threw exceptions: " + errors,
                errors.isEmpty());
    }

    // =========================================================================
    // Metrics — recent-verdicts circular buffer
    // =========================================================================

    /**
     * After submitting more than 100 analysis tasks the circular buffer must
     * wrap and only retain the last 100 entries; the index must not grow
     * unboundedly.
     */
    @Test
    public void testMetrics_RecentVerdicts_CircularBuffer() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4, 4, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        // Submit 110 analysis tasks; each fails fast (non-existent file URL).
        for (int i = 0; i < 110; i++) {
            Set<Uri> uris = Collections.singleton(
                    new Uri("file:///nonexistent_buf_" + i + ".jar"));
            engine.requestAnalysis(uris);
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // recentVerdictIndex counts total writes; index into the array is
        // taken modulo 100 so every slot is occupied.
        int filledSlots = 0;
        for (BytecodeAnalysisEngineImpl.VerdictRecord r : engine.recentVerdicts) {
            if (r != null) filledSlots++;
        }
        assertEquals(100, filledSlots);
    }

    // =========================================================================
    // Metrics — reset
    // =========================================================================

    /**
     * {@link BytecodeAnalysisEngineImpl#resetMetrics()} must zero all counters
     * and clear the recent-verdicts buffer.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMetrics_Reset_ClearsCounters() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry, executor);

        // Run one analysis to populate counters.
        Set<Uri> uris = Collections.singleton(new Uri("file:///nonexistent_reset.jar"));
        engine.requestAnalysis(uris);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // At least one counter must be non-zero before reset.
        Map<String, Object> before = engine.getMetrics();
        long attemptsBefore =
                (long) ((Map<String, Object>) before.get("analysis")).get("totalAttempts");
        assertTrue(attemptsBefore > 0);

        engine.resetMetrics();

        Map<String, Object> after = engine.getMetrics();
        Map<String, Object> queueAfter    = (Map<String, Object>) after.get("queue");
        Map<String, Object> analysisAfter = (Map<String, Object>) after.get("analysis");
        Map<String, Object> verdictAfter  = (Map<String, Object>) after.get("verdict");
        Map<String, Object> submitAfter   = (Map<String, Object>) after.get("submission");

        assertEquals(0L, queueAfter.get("totalRejections"));
        assertEquals(0L, queueAfter.get("peakDepth"));
        assertEquals(0L, analysisAfter.get("totalAttempts"));
        assertEquals(0L, analysisAfter.get("totalCompleted"));
        assertEquals(0L, analysisAfter.get("totalTimeouts"));
        assertEquals(0L, analysisAfter.get("classParseErrors"));
        assertEquals(0L, verdictAfter.get("safe"));
        assertEquals(0L, verdictAfter.get("dangerous"));
        assertEquals(0L, submitAfter.get("successful"));
        assertEquals(0L, submitAfter.get("failed"));

        // Verify the circular buffer was cleared.
        for (BytecodeAnalysisEngineImpl.VerdictRecord r : engine.recentVerdicts) {
            assertNull(r);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Creates a minimal JAR file in the system temp directory containing a
     * single class file with no dangerous constant-pool patterns.  The caller
     * is responsible for deleting the file after use.
     */
    private static File createMinimalSafeJar() throws Exception {
        File jar = File.createTempFile("bae-test-safe-", ".jar");
        jar.deleteOnExit();
        try (JarOutputStream jos =
                new JarOutputStream(new FileOutputStream(jar))) {
            JarEntry entry = new JarEntry("SafeClass.class");
            jos.putNextEntry(entry);
            jos.write(buildClassWithUtf8("Hello", "world"));
            jos.closeEntry();
        }
        return jar;
    }

}
