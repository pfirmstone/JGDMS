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
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import au.net.zeus.jgdms.api.codebase.BytecodeAnalysisEngine;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import org.apache.river.api.net.Uri;

/**
 * Server-side implementation of the {@link BytecodeAnalysisEngine} service.
 *
 * <p>This class runs in a dedicated Phoenix activation group with restrictive
 * security permissions.  It downloads and deep-parses untrusted bytecode from
 * the supplied codebase URLs, produces a {@link SignedVerdict} signed with the
 * engine's private key, and submits it to the configured
 * {@link VerdictRegistry}.
 *
 * <h2>Analysis algorithm</h2>
 * For each JAR reachable from the codebase URLs, every {@code .class} entry is
 * scanned.  The constant pool of each class file is parsed and every
 * {@code CONSTANT_Utf8} entry is checked against a set of
 * <em>dangerous patterns</em>.  A match immediately yields
 * {@link VerdictType#DANGEROUS}.  If no dangerous pattern is found in any class
 * across all JARs, the verdict is {@link VerdictType#SAFE}.
 *
 * <h2>Dangerous patterns</h2>
 * The following constant-pool strings are considered dangerous:
 * <ul>
 *   <li>{@code java/lang/Runtime} — OS-process execution</li>
 *   <li>{@code java/lang/ProcessBuilder} — OS-process execution</li>
 *   <li>{@code java/lang/ProcessImpl} — OS-process execution (JDK internal)</li>
 *   <li>{@code sun/misc/Unsafe} — direct memory access</li>
 *   <li>{@code jdk/internal/misc/Unsafe} — direct memory access (JDK 9+)</li>
 *   <li>{@code java/lang/ClassLoader} — arbitrary class-loading</li>
 * </ul>
 * <p>Note: native library loading ({@code loadLibrary}) is intentionally
 * <em>not</em> treated as dangerous here; whether a service is allowed to
 * load native code is governed by the Jini/Phoenix security policy rather
 * than by bytecode analysis.
 * Additionally, if a JAR entry cannot be read (e.g. due to a network error),
 * the verdict is conservatively {@link VerdictType#DANGEROUS}.
 *
 * <h2>Thread safety</h2>
 * {@link #requestAnalysis} is safe for concurrent use.  Each analysis request
 * is executed asynchronously on a shared daemon thread pool.
 *
 * <h2>Graceful Shutdown</h2>
 * Call {@link #shutdown(long)} to initiate graceful shutdown. This method:
 * <ol>
 *   <li>Stops accepting new analysis requests</li>
 *   <li>Waits for all in-flight tasks to complete (with timeout)</li>
 *   <li>Terminates the thread pool</li>
 * </ol>
 *
 * <p>For forced termination, use {@link #shutdownNow()}, but note that this may
 * leave verdicts unpublished and pending tasks cancelled.
 *
 * <p>The shutdown state can be queried via {@link #isShutdown()} and
 * {@link #isTerminated()}.
 *
 * @see BytecodeAnalysisEngine
 * @see VerdictRegistry
 * @since 3.1.1
 */
public class BytecodeAnalysisEngineImpl implements BytecodeAnalysisEngine {

    // -------------------------------------------------------------------------
    // Shutdown state machine
    // -------------------------------------------------------------------------

    /**
     * Lifecycle states for this engine instance.
     */
    enum ShutdownState {
        /** The engine is running normally and accepts new analysis requests. */
        RUNNING,
        /** Shutdown has been requested; new requests are rejected. */
        SHUTDOWN_REQUESTED,
        /** Shutdown is complete; the thread pool has terminated. */
        SHUTDOWN_COMPLETE
    }

    private static final Logger logger =
            Logger.getLogger(BytecodeAnalysisEngineImpl.class.getName());

    // -------------------------------------------------------------------------
    // Thread-pool constants
    // -------------------------------------------------------------------------

    private static final int  ANALYSIS_POOL_MAX_THREADS        = 4;
    private static final long ANALYSIS_POOL_KEEP_ALIVE_SECONDS = 60L;

    /**
     * Maximum number of analysis tasks that may be pending in the executor
     * queue at any one time.  Submissions beyond this limit are rejected with
     * a {@link RejectedExecutionException} to prevent unbounded memory growth
     * (denial-of-service protection).
     */
    static final int ANALYSIS_QUEUE_MAX_SIZE = 1000;

    /**
     * Cumulative count of analysis tasks that have been rejected because the
     * bounded work queue was full.  Instance-level so each engine instance
     * tracks its own rejection count independently.  Useful for operational
     * monitoring.
     */
    private final AtomicLong rejectedTaskCount = new AtomicLong();

    // -------------------------------------------------------------------------
    // Dangerous constant-pool patterns
    // -------------------------------------------------------------------------

    /**
     * Internal JVM class names (using {@code /} separators) and method names
     * whose presence in a class file's constant pool is treated as a
     * dangerous indicator.
     */
    private static final String[] DANGEROUS_CP_ENTRIES = {
        "java/lang/Runtime",
        "java/lang/ProcessBuilder",
        "java/lang/ProcessImpl",
        "sun/misc/Unsafe",
        "jdk/internal/misc/Unsafe",
        "java/lang/ClassLoader"
    };

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Engine's private key, used to sign {@link SignedVerdict} objects. */
    private final PrivateKey enginePrivateKey;

    /** JCA standard name of the signature algorithm (e.g. {@code "SHA256withRSA"}). */
    private final String sigAlgorithm;

    /**
     * Stable identifier under which this engine is registered with the
     * {@link VerdictRegistry}.
     */
    private final String engineId;

    /** The registry to which {@link SignedVerdict} objects are submitted. */
    private final VerdictRegistry registry;

    /** Thread pool for asynchronous analysis tasks. */
    private final ExecutorService analysisExecutor;

    /** Current lifecycle state of this engine. */
    private final AtomicReference<ShutdownState> shutdownState =
            new AtomicReference<>(ShutdownState.RUNNING);

    /**
     * Fast flag checked by {@link #requestAnalysis} to reject new requests
     * once shutdown has been requested, without needing to read the full
     * {@link #shutdownState}.
     */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    /**
     * Count of analysis tasks currently executing (started but not yet
     * finished).  Incremented at the start of each task, decremented in the
     * task's {@code finally} block.
     */
    private final AtomicInteger inFlightTaskCount = new AtomicInteger(0);

    /**
     * Latch used during shutdown to block until all in-flight tasks complete.
     * Created in {@link #shutdown(long)} with a count equal to the number of
     * in-flight tasks at that moment; {@code null} at all other times.
     */
    private volatile CountDownLatch allTasksComplete;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code BytecodeAnalysisEngineImpl}.
     *
     * @param enginePrivateKey the engine's private key for signing verdicts;
     *                         must be non-null
     * @param sigAlgorithm     JCA standard name of the signature algorithm
     *                         (e.g. {@code "SHA256withRSA"}); must be non-null
     *                         and non-empty
     * @param engineId         the identifier used to register this engine with
     *                         the {@link VerdictRegistry}; must be non-null and
     *                         non-empty
     * @param registry         the registry to which signed verdicts are
     *                         submitted; must be non-null
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code sigAlgorithm} or
     *                                  {@code engineId} is empty
     */
    public BytecodeAnalysisEngineImpl(PrivateKey enginePrivateKey,
                                      String sigAlgorithm,
                                      String engineId,
                                      VerdictRegistry registry) {
        if (enginePrivateKey == null) throw new NullPointerException("enginePrivateKey");
        if (sigAlgorithm == null)     throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty())   throw new IllegalArgumentException("sigAlgorithm must not be empty");
        if (engineId == null)         throw new NullPointerException("engineId");
        if (engineId.isEmpty())       throw new IllegalArgumentException("engineId must not be empty");
        if (registry == null)         throw new NullPointerException("registry");

        this.enginePrivateKey = enginePrivateKey;
        this.sigAlgorithm     = sigAlgorithm;
        this.engineId         = engineId;
        this.registry         = registry;
        this.analysisExecutor = createAnalysisExecutor(this.rejectedTaskCount);
    }

    /**
     * Package-private constructor that accepts a custom {@link ExecutorService}.
     * Intended only for unit testing; production code must use the public
     * four-argument constructor.
     */
    BytecodeAnalysisEngineImpl(PrivateKey enginePrivateKey,
                               String sigAlgorithm,
                               String engineId,
                               VerdictRegistry registry,
                               ExecutorService executor) {
        if (enginePrivateKey == null) throw new NullPointerException("enginePrivateKey");
        if (sigAlgorithm == null)     throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty())   throw new IllegalArgumentException("sigAlgorithm must not be empty");
        if (engineId == null)         throw new NullPointerException("engineId");
        if (engineId.isEmpty())       throw new IllegalArgumentException("engineId must not be empty");
        if (registry == null)         throw new NullPointerException("registry");
        if (executor == null)         throw new NullPointerException("executor");

        this.enginePrivateKey = enginePrivateKey;
        this.sigAlgorithm     = sigAlgorithm;
        this.engineId         = engineId;
        this.registry         = registry;
        this.analysisExecutor = executor;
    }

    // -------------------------------------------------------------------------
    // BytecodeAnalysisEngine
    // -------------------------------------------------------------------------

    /**
     * Queues an asynchronous analysis of the JARs identified by
     * {@code codebaseUrls} and returns immediately.  The resulting
     * {@link SignedVerdict} is submitted to the {@link VerdictRegistry} when
     * the analysis completes.
     *
     * <p>If the internal task queue is full (more than
     * {@value #ANALYSIS_QUEUE_MAX_SIZE} tasks pending), the request is
     * rejected immediately with a {@link RejectedExecutionException} and
     * a WARNING is logged.  Callers should back off and retry.
     *
     * @param codebaseUrls the ordered set of RFC3986-normalised codebase URIs;
     *                     must be non-null and non-empty
     * @throws NullPointerException       if {@code codebaseUrls} is {@code null}
     * @throws IllegalArgumentException   if {@code codebaseUrls} is empty
     * @throws IllegalStateException      if the engine is shutting down
     * @throws RejectedExecutionException if the analysis task queue is full
     * @throws RemoteException            never thrown directly; declared for the
     *                                    remote interface contract
     */
    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null)   throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");
        if (shutdownRequested.get()) {
            throw new IllegalStateException(
                    "BytecodeAnalysisEngine is shutting down; "
                    + "new analysis requests are not accepted");
        }

        // Snapshot the set so the task is independent of caller mutations.
        Set<Uri> snapshot = new LinkedHashSet<Uri>(codebaseUrls);
        analysisExecutor.execute(new AnalysisTask(snapshot));
    }

    // -------------------------------------------------------------------------
    // Shutdown methods
    // -------------------------------------------------------------------------

    /**
     * Initiates graceful shutdown of this engine.
     *
     * <p>After this method is called:
     * <ol>
     *   <li>New analysis requests are rejected with {@link IllegalStateException}.</li>
     *   <li>All in-flight analysis tasks are allowed to complete.</li>
     *   <li>The method blocks until all in-flight tasks finish or
     *       {@code timeoutMs} milliseconds elapse.</li>
     *   <li>The internal thread pool is shut down.</li>
     * </ol>
     *
     * <p>This method is idempotent: calling it more than once is safe.
     *
     * @param timeoutMs maximum time in milliseconds to wait for in-flight
     *                  tasks to complete; use {@code 0} to wait indefinitely
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting for tasks to complete
     */
    public void shutdown(long timeoutMs) throws InterruptedException {
        CountDownLatch latch;
        synchronized (this) {
            if (!shutdownRequested.compareAndSet(false, true)) {
                return; // already shutting down or shut down
            }
            shutdownState.set(ShutdownState.SHUTDOWN_REQUESTED);

            // Read inFlightTaskCount and install the latch under the same lock
            // that AnalysisTask.run() holds when it decrements the count and
            // checks the latch.  This ensures no task can "escape" between our
            // read and the latch installation.
            int inFlight = inFlightTaskCount.get();
            if (inFlight > 0) {
                allTasksComplete = new CountDownLatch(inFlight);
            }
            latch = allTasksComplete;

            analysisExecutor.shutdown();
        }

        // Await OUTSIDE the synchronized block so tasks can enter it to
        // decrement inFlightTaskCount and count down the latch.
        if (latch != null) {
            boolean completed;
            if (timeoutMs <= 0) {
                latch.await();
                completed = true;
            } else {
                completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            }
            if (!completed) {
                logger.log(Level.WARNING,
                        "Shutdown timeout: {0} tasks did not complete within {1}ms",
                        new Object[]{inFlightTaskCount.get(), timeoutMs});
            }
        }

        synchronized (this) {
            shutdownState.set(ShutdownState.SHUTDOWN_COMPLETE);
        }
    }

    /**
     * Blocks until all in-flight analysis tasks have completed after a
     * {@link #shutdown(long)} call, or the timeout expires.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the {@code timeout} argument
     * @return {@code true} if all tasks completed before the timeout;
     *         {@code false} if the timeout elapsed
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return analysisExecutor.awaitTermination(timeout, unit);
    }

    /**
     * Attempts to stop all actively executing analysis tasks and halts the
     * processing of waiting tasks.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop processing
     * actively executing tasks.  For example, typical implementations will
     * cancel via {@link Thread#interrupt}, so any task that fails to respond to
     * interrupts may never terminate.
     *
     * @return list of tasks that were awaiting execution but never started
     */
    public synchronized List<Runnable> shutdownNow() {
        shutdownRequested.set(true);
        shutdownState.set(ShutdownState.SHUTDOWN_REQUESTED);
        List<Runnable> pending = analysisExecutor.shutdownNow();
        shutdownState.set(ShutdownState.SHUTDOWN_COMPLETE);
        return pending;
    }

    /**
     * Returns {@code true} if shutdown has been initiated on this engine
     * (either {@link #shutdown(long)} or {@link #shutdownNow()} has been
     * called).
     *
     * @return {@code true} if shutdown has been requested
     */
    public boolean isShutdown() {
        return shutdownRequested.get();
    }

    /**
     * Returns {@code true} if all tasks have completed following a shutdown
     * request.  Note that {@code isTerminated} is never {@code true} unless
     * {@link #shutdown(long)} or {@link #shutdownNow()} was called first.
     *
     * @return {@code true} if the executor has terminated
     */
    public boolean isTerminated() {
        return analysisExecutor.isTerminated();
    }

    // -------------------------------------------------------------------------
    // Private inner class: AnalysisTask
    // -------------------------------------------------------------------------

    /**
     * Runnable that downloads JARs, scans their class files, signs the
     * resulting {@link SignedVerdict}, and submits it to the registry.
     */
    private final class AnalysisTask implements Runnable {

        private final Set<Uri> codebaseUrls;

        AnalysisTask(Set<Uri> codebaseUrls) {
            this.codebaseUrls = codebaseUrls;
        }

        @Override
        public void run() {
            inFlightTaskCount.incrementAndGet();
            try {
                VerdictType verdict = analyzeCodebase(codebaseUrls);
                long timestamp = System.currentTimeMillis();
                try {
                    Uri[] sortedUrls = sortedUriArray(codebaseUrls);
                    byte[] canonical = canonicalBytes(sortedUrls, verdict, timestamp);
                    byte[] sig       = sign(enginePrivateKey, sigAlgorithm, canonical);
                    SignedVerdict sv = new SignedVerdict(sortedUrls, verdict, timestamp, sig);
                    registry.submitVerdict(engineId, sv);
                    logger.log(Level.INFO,
                            "Submitted {0} verdict for {1} to registry",
                            new Object[]{verdict, codebaseUrls});
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                            "Failed to sign or submit verdict for " + codebaseUrls, e);
                }
            } finally {
                // Decrement and check the latch atomically under the same
                // monitor used by shutdown() to read inFlightTaskCount and
                // install allTasksComplete.  This eliminates the race where a
                // task completes between shutdown()'s count-read and latch-
                // creation, leaving the latch count permanently stuck above zero.
                synchronized (BytecodeAnalysisEngineImpl.this) {
                    inFlightTaskCount.decrementAndGet();
                    CountDownLatch latch = allTasksComplete;
                    if (latch != null) {
                        latch.countDown();
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Analysis logic
    // -------------------------------------------------------------------------

    /**
     * Downloads all JARs in {@code codebaseUrls} and scans their class files.
     * Returns {@link VerdictType#DANGEROUS} as soon as a dangerous pattern is
     * found, or if a JAR cannot be read.  Returns {@link VerdictType#SAFE}
     * only when every class file in every JAR has been scanned clean.
     */
    private static VerdictType analyzeCodebase(Set<Uri> codebaseUrls) {
        for (Uri uri : codebaseUrls) {
            URL url;
            try {
                url = uri.toURL();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Cannot convert URI to URL: " + uri, e);
                return VerdictType.DANGEROUS;
            }
            try {
                InputStream raw = url.openStream();
                try {
                    JarInputStream jis = new JarInputStream(raw);
                    try {
                        JarEntry entry;
                        while ((entry = jis.getNextJarEntry()) != null) {
                            if (!entry.getName().endsWith(".class")) continue;
                            byte[] classBytes = readFully(jis);
                            if (classBytes != null && containsDangerousCode(classBytes)) {
                                logger.log(Level.INFO,
                                        "Dangerous class found: {0} in {1}",
                                        new Object[]{entry.getName(), uri});
                                return VerdictType.DANGEROUS;
                            }
                        }
                    } finally {
                        jis.close();
                    }
                } finally {
                    raw.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to read JAR at " + uri
                        + " - treating as DANGEROUS", e);
                return VerdictType.DANGEROUS;
            }
        }
        return VerdictType.SAFE;
    }

    // -------------------------------------------------------------------------
    // Class-file constant-pool scanner
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if any {@code CONSTANT_Utf8} entry in the constant
     * pool of the supplied class file bytes matches a known dangerous pattern.
     *
     * <p>The JVM class-file format specifies the constant pool immediately
     * after the 10-byte file header (magic, minor version, major version,
     * constant-pool count).  Each constant-pool entry begins with a single
     * tag byte.  This method iterates over all entries, extracting
     * {@code CONSTANT_Utf8} values and checking them against
     * {@link #DANGEROUS_CP_ENTRIES}.
     *
     * <p>If the byte array does not begin with the class-file magic number
     * {@code 0xCAFEBABE}, or if the array is shorter than the minimum valid
     * class-file header, the method conservatively returns {@code true}.
     *
     * @param classBytes the raw bytes of a {@code .class} file
     * @return {@code true} if dangerous code is detected or if the file cannot
     *         be parsed; {@code false} otherwise
     */
    static boolean containsDangerousCode(byte[] classBytes) {
        if (classBytes.length < 10) return true;

        // Verify magic: 0xCAFEBABE; any other value means the bytes are not a
        // valid class file, so treat conservatively as dangerous.
        if ((classBytes[0] & 0xFF) != 0xCA || (classBytes[1] & 0xFF) != 0xFE
                || (classBytes[2] & 0xFF) != 0xBA || (classBytes[3] & 0xFF) != 0xBE) {
            return true;
        }

        // The class file header is: magic(4), minor_version(2), major_version(2),
        // constant_pool_count(2).  The count is at bytes 8-9.
        int cpCount = ((classBytes[8] & 0xFF) << 8) | (classBytes[9] & 0xFF);
        int offset  = 10;

        for (int i = 1; i < cpCount; i++) {
            if (offset >= classBytes.length) break;
            int tag = classBytes[offset++] & 0xFF;
            switch (tag) {
                case 1: { // CONSTANT_Utf8
                    if (offset + 2 > classBytes.length) return true;
                    int len = ((classBytes[offset] & 0xFF) << 8)
                            | (classBytes[offset + 1] & 0xFF);
                    offset += 2;
                    if (offset + len > classBytes.length) return true;
                    String value = new String(classBytes, offset, len,
                            StandardCharsets.UTF_8);
                    offset += len;
                    if (isDangerousCpEntry(value)) return true;
                    break;
                }
                case 3: case 4: // CONSTANT_Integer, CONSTANT_Float
                    offset += 4;
                    break;
                case 5: case 6: // CONSTANT_Long, CONSTANT_Double
                    offset += 8;
                    i++; // per the JVM specification, Long and Double each occupy
                         // two consecutive constant-pool slots
                    break;
                case 7: case 8: case 16: case 19: case 20:
                    // CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType,
                    // CONSTANT_Module, CONSTANT_Package
                    offset += 2;
                    break;
                case 9: case 10: case 11: case 12: case 17: case 18:
                    // CONSTANT_Fieldref, CONSTANT_Methodref,
                    // CONSTANT_InterfaceMethodref, CONSTANT_NameAndType,
                    // CONSTANT_Dynamic, CONSTANT_InvokeDynamic
                    offset += 4;
                    break;
                case 15: // CONSTANT_MethodHandle
                    offset += 3;
                    break;
                default:
                    // Unknown tag: class file is malformed or uses a constant-pool
                    // tag introduced in a newer JVM version that this parser does not
                    // yet recognise.  Treat conservatively as dangerous and log the
                    // tag value to aid diagnosis.
                    logger.log(Level.WARNING,
                            "Unknown constant-pool tag {0}; treating class as dangerous",
                            tag);
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code cpEntry} matches any entry in
     * {@link #DANGEROUS_CP_ENTRIES}.
     *
     * @param cpEntry a {@code CONSTANT_Utf8} value from a class file constant pool
     * @return {@code true} if the entry is on the dangerous list
     */
    private static boolean isDangerousCpEntry(String cpEntry) {
        for (String dangerous : DANGEROUS_CP_ENTRIES) {
            if (cpEntry.equals(dangerous)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * {@link RejectedExecutionHandler} that logs a WARNING, increments the
     * per-engine rejection counter, and throws {@link RejectedExecutionException}
     * when the bounded analysis work queue is full.
     */
    private static final class LoggingAbortPolicy implements RejectedExecutionHandler {

        private final AtomicLong rejectedTaskCount;

        LoggingAbortPolicy(AtomicLong rejectedTaskCount) {
            this.rejectedTaskCount = rejectedTaskCount;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            long count = rejectedTaskCount.incrementAndGet();
            logger.log(Level.WARNING,
                    "Analysis task rejected: queue is full "
                    + "(limit={0}, total rejections={1}). Task: {2}",
                    new Object[]{ANALYSIS_QUEUE_MAX_SIZE, count, r});
            throw new RejectedExecutionException(
                    "Analysis queue full (limit=" + ANALYSIS_QUEUE_MAX_SIZE + ")");
        }
    }

    /**
     * Creates the daemon thread pool used for asynchronous analysis tasks.
     *
     * <p>The work queue is bounded to {@value #ANALYSIS_QUEUE_MAX_SIZE} entries.
     * If the queue is full when a new task is submitted, the
     * {@link LoggingAbortPolicy} logs a WARNING, increments the supplied
     * {@code rejectedTaskCount} counter, and throws a
     * {@link RejectedExecutionException} to the caller.
     *
     * @param rejectedTaskCount per-engine counter incremented on each rejection
     */
    private static ExecutorService createAnalysisExecutor(
            final AtomicLong rejectedTaskCount) {
        ThreadFactory daemonFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "BAE-analysis");
                t.setDaemon(true);
                return t;
            }
        };
        return new ThreadPoolExecutor(
                0, ANALYSIS_POOL_MAX_THREADS, ANALYSIS_POOL_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(ANALYSIS_QUEUE_MAX_SIZE),
                daemonFactory,
                new LoggingAbortPolicy(rejectedTaskCount));
    }

    /**
     * Returns a sorted copy of the URI set as a {@link Uri} array (sorted by
     * UTF-8 string value of each URI).
     */
    private static Uri[] sortedUriArray(Set<Uri> uris) {
        Uri[] arr = uris.toArray(new Uri[0]);
        Arrays.sort(arr, new java.util.Comparator<Uri>() {
            @Override
            public int compare(Uri a, Uri b) {
                return a.toString().compareTo(b.toString());
            }
        });
        return arr;
    }

    /**
     * Produces the canonical byte representation that is signed for a
     * {@link SignedVerdict}.  The format mirrors
     * {@code VerdictRegistryImpl#canonicalBytesForVerdict}:
     * <ol>
     *   <li>For each URL in lexicographic order:
     *       4-byte big-endian byte-length, then UTF-8 bytes.</li>
     *   <li>4-byte big-endian {@link VerdictType#ordinal()}.</li>
     *   <li>8-byte big-endian timestamp.</li>
     * </ol>
     *
     * @param sortedUrls URIs in lexicographic order
     * @param verdict    the verdict type
     * @param timestamp  UTC milliseconds since the epoch
     * @return the canonical byte array
     * @throws IOException if the byte-array output stream fails (should not
     *                     happen in practice)
     */
    static byte[] canonicalBytes(Uri[] sortedUrls,
                                  VerdictType verdict,
                                  long timestamp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (Uri uri : sortedUrls) {
            byte[] b = uri.toString().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(verdict.ordinal());
        dos.writeLong(timestamp);
        dos.flush();
        return baos.toByteArray();
    }

    /**
     * Signs {@code data} with the given private key and algorithm.
     *
     * @param key       the private key
     * @param algorithm JCA algorithm name
     * @param data      bytes to sign
     * @return the DER-encoded signature bytes
     */
    private static byte[] sign(PrivateKey key, String algorithm, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(algorithm);
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    /**
     * Reads all bytes from {@code in} without closing the stream (caller
     * controls the lifecycle of the underlying {@link JarInputStream}).
     *
     * @param in the input stream to read from
     * @return the bytes read, or {@code null} if an I/O error occurs
     */
    private static byte[] readFully(InputStream in) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "I/O error reading class file entry", e);
            return null;
        }
        return baos.toByteArray();
    }
}
