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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarOutputStream;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import org.apache.river.api.net.Uri;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    // Synthetic class-file builder for bytecode-level tests
    // =========================================================================

    /**
     * Minimal class-file builder used by threading anti-pattern tests.
     *
     * <p>Produces a structurally valid {@code .class} file (magic, version,
     * constant pool, access flags, this/super class refs, no interfaces, no
     * fields) with a single method whose {@code Code} attribute contains the
     * supplied bytecode and exception handler table.
     *
     * <p>Constant pool layout (1-based indices):
     * <ol>
     *   <li>CONSTANT_Utf8 "SyntheticClass"    (class name)</li>
     *   <li>CONSTANT_Class → #1</li>
     *   <li>CONSTANT_Utf8 "java/lang/Object"  (super class)</li>
     *   <li>CONSTANT_Class → #3</li>
     *   <li>CONSTANT_Utf8 "run"               (method name)</li>
     *   <li>CONSTANT_Utf8 "()V"               (method descriptor)</li>
     *   <li>CONSTANT_Utf8 "Code"              (attribute name)</li>
     *   <li..N> extra UTF-8 entries supplied by caller
     * </ol>
     */
    private static final class ClassBuilder {

        private final ByteArrayOutputStream cpBaos = new ByteArrayOutputStream();
        private final DataOutputStream cpDos = new DataOutputStream(cpBaos);
        private int cpIndex = 1; // next free CP slot (1-based)

        // Fixed CP entries (written first)
        private final int classNameIdx;
        private final int classRefIdx;
        private final int superNameIdx;
        private final int superRefIdx;
        private final int methodNameIdx;
        private final int methodDescIdx;
        private final int codeAttrIdx;

        ClassBuilder() throws IOException {
            classNameIdx  = addUtf8("SyntheticClass");
            classRefIdx   = addClassRef(classNameIdx);
            superNameIdx  = addUtf8("java/lang/Object");
            superRefIdx   = addClassRef(superNameIdx);
            methodNameIdx = addUtf8("run");
            methodDescIdx = addUtf8("()V");
            codeAttrIdx   = addUtf8("Code");
        }

        /** Adds a CONSTANT_Utf8 entry; returns its 1-based index. */
        int addUtf8(String value) throws IOException {
            byte[] b = value.getBytes(StandardCharsets.UTF_8);
            cpDos.writeByte(1);
            cpDos.writeShort(b.length);
            cpDos.write(b);
            return cpIndex++;
        }

        /** Adds a CONSTANT_Class entry pointing to {@code nameIdx}; returns its index. */
        private int addClassRef(int nameIdx) throws IOException {
            cpDos.writeByte(7); // CONSTANT_Class
            cpDos.writeShort(nameIdx);
            return cpIndex++;
        }

        /**
         * Assembles the complete class file bytes with a single {@code run()}
         * method containing the supplied bytecode and exception handlers.
         *
         * @param bytecode         raw method bytecode
         * @param exceptionHandlers exception handler table entries, each an
         *                          {@code int[4]}: {start_pc, end_pc, handler_pc, catch_type}
         *                          (catch_type = 0 means finally; use a CP Class index otherwise)
         */
        byte[] build(byte[] bytecode, int[][] exceptionHandlers) throws IOException {
            cpDos.flush();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(out);

            // Magic + version
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);   // minor
            dos.writeShort(52);  // major (Java 8)

            // Constant pool
            dos.writeShort(cpIndex); // cp_count
            dos.write(cpBaos.toByteArray());

            // access_flags=0x0021 (ACC_PUBLIC | ACC_SUPER), this_class, super_class
            dos.writeShort(0x0021);
            dos.writeShort(classRefIdx);
            dos.writeShort(superRefIdx);

            // No interfaces
            dos.writeShort(0);
            // No fields
            dos.writeShort(0);

            // One method
            dos.writeShort(1);
            buildMethod(dos, bytecode, exceptionHandlers);

            // No class attributes
            dos.writeShort(0);

            dos.flush();
            return out.toByteArray();
        }

        /** Convenience overload: no exception handlers. */
        byte[] build(byte[] bytecode) throws IOException {
            return build(bytecode, new int[0][]);
        }

        private void buildMethod(DataOutputStream dos, byte[] bytecode,
                                  int[][] exceptionHandlers) throws IOException {
            // method_info: access_flags, name_index, descriptor_index, attributes_count
            dos.writeShort(0x0001); // ACC_PUBLIC
            dos.writeShort(methodNameIdx);
            dos.writeShort(methodDescIdx);
            dos.writeShort(1); // one attribute: Code

            // Code attribute
            // Compute length:
            // max_stack(2) + max_locals(2) + code_length(4) + code(N)
            // + exception_table_length(2) + handlers(8 each)
            // + attributes_count(2) = total
            int ehCount = exceptionHandlers.length;
            int codeAttrLen = 2 + 2 + 4 + bytecode.length
                    + 2 + ehCount * 8
                    + 2;

            dos.writeShort(codeAttrIdx); // attribute_name_index
            dos.writeInt(codeAttrLen);
            dos.writeShort(10);  // max_stack
            dos.writeShort(10);  // max_locals
            dos.writeInt(bytecode.length);
            dos.write(bytecode);

            // Exception table
            dos.writeShort(ehCount);
            for (int[] eh : exceptionHandlers) {
                dos.writeShort(eh[0]); // start_pc
                dos.writeShort(eh[1]); // end_pc
                dos.writeShort(eh[2]); // handler_pc
                dos.writeShort(eh[3]); // catch_type (0 = any)
            }
            dos.writeShort(0); // no sub-attributes
        }
    }

    // =========================================================================
    // hasVirtualThreadPinning — positive cases
    // =========================================================================

    /**
     * A class that contains MONITORENTER (0xC2) and references java/io/InputStream
     * (a blocking-I/O pinning CP entry) must be flagged.
     */
    @Test
    public void testHasVirtualThreadPinning_MonitorenterWithObjectRef_IsTrue()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/io/InputStream");
        // bytecode: MONITORENTER + RETURN
        byte[] code = {(byte)0xC2, (byte)0xB1};
        byte[] classBytes = cb.build(code);
        assertTrue(BytecodeAnalysisEngineImpl.hasVirtualThreadPinning(classBytes));
    }

    /**
     * A class that contains MONITORENTER and references java/net/Socket must be flagged.
     */
    @Test
    public void testHasVirtualThreadPinning_MonitorenterWithSocketRef_IsTrue()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/net/Socket");
        // bytecode: MONITORENTER + MONITOREXIT + RETURN
        byte[] code = {(byte)0xC2, (byte)0xC3, (byte)0xB1};
        byte[] classBytes = cb.build(code);
        assertTrue(BytecodeAnalysisEngineImpl.hasVirtualThreadPinning(classBytes));
    }

    /**
     * A class that contains MONITORENTER and references java/nio/channels/Selector
     * must be flagged.
     */
    @Test
    public void testHasVirtualThreadPinning_MonitorenterWithSelectorRef_IsTrue()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/nio/channels/Selector");
        byte[] code = {(byte)0xC2, (byte)0xB1};
        byte[] classBytes = cb.build(code);
        assertTrue(BytecodeAnalysisEngineImpl.hasVirtualThreadPinning(classBytes));
    }

    // =========================================================================
    // hasVirtualThreadPinning — negative cases
    // =========================================================================

    /**
     * A class with no MONITORENTER opcode must not be flagged, even if it
     * references a pinning CP entry.
     */
    @Test
    public void testHasVirtualThreadPinning_NoMonitorenter_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/net/Socket");
        // bytecode: just RETURN — no MONITORENTER
        byte[] code = {(byte)0xB1};
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasVirtualThreadPinning(classBytes));
    }

    /**
     * A class with MONITORENTER but no pinning CP entries must not be flagged.
     */
    @Test
    public void testHasVirtualThreadPinning_MonitorenterNoPinningRef_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("com/example/Foo"); // not a pinning entry
        byte[] code = {(byte)0xC2, (byte)0xB1};
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasVirtualThreadPinning(classBytes));
    }

    /**
     * A minimal stub (CP-only class with no method table) must not be flagged
     * by the pinning detector.
     */
    @Test
    public void testHasVirtualThreadPinning_StubClassNoMethods_IsFalse()
            throws IOException {
        // buildClassWithUtf8 creates a CP-only class (no method table)
        byte[] classBytes = buildClassWithUtf8("java/net/Socket");
        assertFalse(BytecodeAnalysisEngineImpl.hasVirtualThreadPinning(classBytes));
    }

    // =========================================================================
    // hasCpuConsumingLoops — positive cases
    // =========================================================================

    /**
     * A class with a backward GOTO (-3 offset from the GOTO instruction itself,
     * forming a tight infinite loop) and no yield-point CP entries must be flagged.
     */
    @Test
    public void testHasCpuConsumingLoops_BackwardGoto_IsTrue() throws IOException {
        ClassBuilder cb = new ClassBuilder();
        // No yield-point strings in CP (the builder adds "run", "()V", "Code" etc.
        // but none of the loop-yield method names).
        // bytecode: NOP GOTO -3 (offset -3 → back to the NOP)
        // GOTO = 0xA7; offset bytes: 0xFF, 0xFD = -3 in signed short
        byte[] code = {
            (byte)0x00,             // 0: NOP
            (byte)0xA7,             // 1: GOTO
            (byte)0xFF, (byte)0xFD  // 2-3: offset -3 → pc = 1 + (-3) = -2? let's use -2
        };
        // Actually: GOTO offset is relative to the position of the GOTO instruction.
        // GOTO at pc=1, offset=-3 → target = 1 + (-3) = -2 (wraps, but negative = backward)
        // For the test, we just need offset < 0.
        byte[] classBytes = cb.build(code);
        assertTrue(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    /**
     * A backward GOTO loop that also references "sleep" in the constant pool
     * must NOT be flagged — sleep is a valid yield point.
     */
    @Test
    public void testHasCpuConsumingLoops_BackwardGotoWithSleep_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("sleep"); // yield point
        byte[] code = {
            (byte)0x00,             // NOP
            (byte)0xA7,             // GOTO
            (byte)0xFF, (byte)0xFD  // offset -3
        };
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    /**
     * A backward GOTO loop that also references "yield" in the constant pool
     * must NOT be flagged.
     */
    @Test
    public void testHasCpuConsumingLoops_BackwardGotoWithYield_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("yield");
        byte[] code = {
            (byte)0x00,
            (byte)0xA7,
            (byte)0xFF, (byte)0xFD
        };
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    /**
     * A backward GOTO loop that also references "park" must NOT be flagged.
     */
    @Test
    public void testHasCpuConsumingLoops_BackwardGotoWithPark_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("park");
        byte[] code = {
            (byte)0x00,
            (byte)0xA7,
            (byte)0xFF, (byte)0xFD
        };
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    // =========================================================================
    // hasCpuConsumingLoops — negative cases
    // =========================================================================

    /**
     * A class with only forward GOTO (no loop) must not be flagged, even without
     * yield-point CP entries.
     */
    @Test
    public void testHasCpuConsumingLoops_ForwardGotoOnly_IsFalse() throws IOException {
        ClassBuilder cb = new ClassBuilder();
        // GOTO +2 (forward): skip over NOP, then RETURN
        byte[] code = {
            (byte)0xA7,             // 0: GOTO
            (byte)0x00, (byte)0x03, // 1-2: offset +3 → skip to pc=3
            (byte)0x00,             // 3: NOP
            (byte)0xB1              // 4: RETURN
        };
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    /**
     * A class with no GOTO at all must not be flagged.
     */
    @Test
    public void testHasCpuConsumingLoops_NoGoto_IsFalse() throws IOException {
        ClassBuilder cb = new ClassBuilder();
        byte[] code = {(byte)0x00, (byte)0xB1}; // NOP, RETURN
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    /**
     * A minimal stub (CP-only, no method table) must not be flagged.
     */
    @Test
    public void testHasCpuConsumingLoops_StubClassNoMethods_IsFalse()
            throws IOException {
        byte[] classBytes = buildClassWithUtf8("loop");
        assertFalse(BytecodeAnalysisEngineImpl.hasCpuConsumingLoops(classBytes));
    }

    // =========================================================================
    // hasInterruptSwallowing — positive cases
    // =========================================================================

    /**
     * A method that catches {@code InterruptedException} (CP reference present)
     * and the handler body is just RETURN (no invocation opcode) must be flagged.
     *
     * <p>Exception handler table: catch_type points to a Class CP entry.  In this
     * minimal test we use a non-zero catch_type value (8 = index into CP) and
     * ensure the CP does NOT contain "interrupt" so the check fires immediately.
     */
    @Test
    public void testHasInterruptSwallowing_EmptyHandler_IsTrue() throws IOException {
        ClassBuilder cb = new ClassBuilder();
        // Add "java/lang/InterruptedException" to CP
        cb.addUtf8("java/lang/InterruptedException");
        // bytecode:
        //   0: NOP          (try block start)
        //   1: GOTO +3      (skip handler, jump to RETURN at 6)
        //   4: POP          (handler start: discard the exception)
        //   5: RETURN       (empty handler — swallows)
        byte[] code = {
            (byte)0x00,             // 0: NOP (try start)
            (byte)0xA7,             // 1: GOTO
            (byte)0x00, (byte)0x04, // 2-3: +4 → pc=5 (RETURN — wait, handler at 4)
            (byte)0x57,             // 4: POP  (handler_pc=4)
            (byte)0xB1              // 5: RETURN
        };
        // Exception handler: start_pc=0, end_pc=1, handler_pc=4, catch_type=8 (non-zero = InterruptedException)
        int[][] handlers = {{0, 1, 4, 8}};
        byte[] classBytes = cb.build(code, handlers);
        assertTrue(BytecodeAnalysisEngineImpl.hasInterruptSwallowing(classBytes));
    }

    /**
     * A class whose CP contains "java/lang/InterruptedException" but whose
     * handler also contains "interrupt" (a method invocation) in the CP is
     * NOT flagged — we assume the developer called {@code Thread.currentThread().interrupt()}.
     */
    @Test
    public void testHasInterruptSwallowing_HandlerCallsInterrupt_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/lang/InterruptedException");
        cb.addUtf8("interrupt"); // interrupt() method referenced
        // bytecode:
        //   0: NOP
        //   1: GOTO +5 (skip to RETURN)
        //   4: INVOKEVIRTUAL (0xB6) index1 index2 → calls interrupt
        //   7: RETURN
        byte[] code = {
            (byte)0x00,             // 0: NOP
            (byte)0xA7,             // 1: GOTO
            (byte)0x00, (byte)0x06, // 2-3: +6 → pc=7
            (byte)0xB6,             // 4: INVOKEVIRTUAL (handler body)
            (byte)0x00, (byte)0x00, // 5-6: method index (stub — just needs the opcode)
            (byte)0xB1              // 7: RETURN
        };
        int[][] handlers = {{0, 1, 4, 8}};
        byte[] classBytes = cb.build(code, handlers);
        assertFalse(BytecodeAnalysisEngineImpl.hasInterruptSwallowing(classBytes));
    }

    // =========================================================================
    // hasInterruptSwallowing — negative cases
    // =========================================================================

    /**
     * A class with NO reference to InterruptedException must never be flagged.
     */
    @Test
    public void testHasInterruptSwallowing_NoInterruptedException_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        // No "java/lang/InterruptedException" in CP
        byte[] code = {(byte)0x57, (byte)0xB1}; // POP, RETURN
        int[][] handlers = {{0, 1, 0, 8}}; // some handler, but IE not in CP
        byte[] classBytes = cb.build(code, handlers);
        assertFalse(BytecodeAnalysisEngineImpl.hasInterruptSwallowing(classBytes));
    }

    /**
     * A class with InterruptedException in the CP but no exception handlers
     * must not be flagged.
     */
    @Test
    public void testHasInterruptSwallowing_NoHandlers_IsFalse() throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/lang/InterruptedException");
        byte[] code = {(byte)0xB1}; // RETURN, no handlers
        byte[] classBytes = cb.build(code);
        assertFalse(BytecodeAnalysisEngineImpl.hasInterruptSwallowing(classBytes));
    }

    /**
     * A catch-all finally handler (catch_type = 0) that has no invocation must
     * NOT be flagged because finally blocks are not interrupt handlers.
     */
    @Test
    public void testHasInterruptSwallowing_FinallyHandler_IsFalse()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/lang/InterruptedException");
        byte[] code = {(byte)0x57, (byte)0xBF}; // POP, ATHROW (re-throw)
        // catch_type = 0 → finally block, should be ignored
        int[][] handlers = {{0, 1, 0, 0}};
        byte[] classBytes = cb.build(code, handlers);
        assertFalse(BytecodeAnalysisEngineImpl.hasInterruptSwallowing(classBytes));
    }

    /**
     * A minimal stub class (CP-only, no method table) must not be flagged even
     * if InterruptedException appears in the CP.
     */
    @Test
    public void testHasInterruptSwallowing_StubClassNoMethods_IsFalse()
            throws IOException {
        byte[] classBytes = buildClassWithUtf8("java/lang/InterruptedException");
        assertFalse(BytecodeAnalysisEngineImpl.hasInterruptSwallowing(classBytes));
    }

    // =========================================================================
    // containsDangerousCode integration — threading detectors via main entry point
    // =========================================================================

    /**
     * {@code containsDangerousCode} must flag a class that has both MONITORENTER
     * and a blocking-I/O CP reference, even if no CP pattern from the default
     * dangerous list is present.
     */
    @Test
    public void testContainsDangerousCode_VirtualThreadPinning_IsTrue()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/net/Socket");
        byte[] code = {(byte)0xC2, (byte)0xB1}; // MONITORENTER, RETURN
        byte[] classBytes = cb.build(code);
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(classBytes,
                new String[0]));
    }

    /**
     * {@code containsDangerousCode} must flag a class that has a CPU-consuming
     * spin loop (backward GOTO, no yield point).
     */
    @Test
    public void testContainsDangerousCode_CpuConsumingLoop_IsTrue()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        byte[] code = {
            (byte)0x00,
            (byte)0xA7,
            (byte)0xFF, (byte)0xFD  // backward GOTO
        };
        byte[] classBytes = cb.build(code);
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(classBytes,
                new String[0]));
    }

    /**
     * {@code containsDangerousCode} must flag a class that swallows
     * {@code InterruptedException} in an empty handler.
     */
    @Test
    public void testContainsDangerousCode_InterruptSwallowing_IsTrue()
            throws IOException {
        ClassBuilder cb = new ClassBuilder();
        cb.addUtf8("java/lang/InterruptedException");
        byte[] code = {
            (byte)0x00,
            (byte)0xA7,
            (byte)0x00, (byte)0x04,
            (byte)0x57,
            (byte)0xB1
        };
        int[][] handlers = {{0, 1, 4, 8}};
        byte[] classBytes = cb.build(code, handlers);
        assertTrue(BytecodeAnalysisEngineImpl.containsDangerousCode(classBytes,
                new String[0]));
    }
}
