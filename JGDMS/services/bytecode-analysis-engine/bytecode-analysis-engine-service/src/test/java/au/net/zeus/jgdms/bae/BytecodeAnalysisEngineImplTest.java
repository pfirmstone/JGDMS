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
import java.io.IOException;
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
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import org.apache.river.api.net.Uri;
import org.junit.Before;
import org.junit.Test;

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
    // containsDangerousCode — dangerous pattern mixed with benign entries
    // =========================================================================

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
                new SynchronousQueue<Runnable>(),
                new ThreadPoolExecutor.AbortPolicy());

        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM, "e1", mockRegistry,
                executor);

        // Occupy the single worker thread.
        executor.execute(new Runnable() {
            @Override public void run() {
                taskStarted.countDown();
                try { releaseTask.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
                new ArrayBlockingQueue<Runnable>(10),
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
    // Private helpers
    // =========================================================================

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
}
