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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
 * @see BytecodeAnalysisEngine
 * @see VerdictRegistry
 * @since 3.1.1
 */
public class BytecodeAnalysisEngineImpl implements BytecodeAnalysisEngine {

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
     *
     * <p>Package-private to allow the {@link LoggingAbortPolicy} (constructed
     * by tests) and the metrics test harness to access it directly.
     */
    final AtomicLong rejectedTaskCount = new AtomicLong();

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

    /**
     * Reference to the {@link ThreadPoolExecutor} used for analysis, or
     * {@code null} if a custom {@link ExecutorService} was supplied via the
     * package-private test constructor.  Used to compute the live queue depth.
     */
    private final ThreadPoolExecutor poolExecutor;

    // -------------------------------------------------------------------------
    // Metrics fields
    // -------------------------------------------------------------------------

    // -- Queue metrics
    final AtomicLong peakQueueDepth = new AtomicLong(0);

    // -- Analysis task metrics
    private final AtomicLong totalAnalysisAttempts  = new AtomicLong();
    private final AtomicLong totalAnalysisCompleted = new AtomicLong();
    private final AtomicLong analysisTimeouts        = new AtomicLong();
    private final AtomicLong classParseErrors        = new AtomicLong();

    // -- Analysis duration histogram (milliseconds)
    private final AtomicLong analysisDuration_0_100ms    = new AtomicLong();
    private final AtomicLong analysisDuration_100_500ms  = new AtomicLong();
    private final AtomicLong analysisDuration_500_1000ms = new AtomicLong();
    private final AtomicLong analysisDuration_1_5s       = new AtomicLong();
    private final AtomicLong analysisDuration_5_10s      = new AtomicLong();
    private final AtomicLong analysisDuration_10s_plus   = new AtomicLong();

    // -- Verdict distribution
    private final AtomicLong safeVerdicts      = new AtomicLong();
    private final AtomicLong dangerousVerdicts = new AtomicLong();
    private final AtomicLong analysisErrors    = new AtomicLong();

    // -- Recent verdicts circular buffer (last 100)
    final VerdictRecord[] recentVerdicts     = new VerdictRecord[100];
    private final AtomicInteger   recentVerdictIndex = new AtomicInteger(0);

    // -- Registry submission metrics
    private final AtomicLong successfulSubmissions = new AtomicLong();
    private final AtomicLong failedSubmissions     = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> submissionErrorTypes
            = new ConcurrentHashMap<>();

    // -- Submission latency histogram (milliseconds)
    private final AtomicLong submissionLatency_0_100ms    = new AtomicLong();
    private final AtomicLong submissionLatency_100_500ms  = new AtomicLong();
    private final AtomicLong submissionLatency_500_1000ms = new AtomicLong();
    private final AtomicLong submissionLatency_1000ms_plus = new AtomicLong();

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
        ThreadPoolExecutor tpe = createAnalysisExecutor(this.rejectedTaskCount);
        this.analysisExecutor = tpe;
        this.poolExecutor     = tpe;
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
        this.poolExecutor     = (executor instanceof ThreadPoolExecutor)
                ? (ThreadPoolExecutor) executor : null;
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
     * @throws RejectedExecutionException if the analysis task queue is full
     * @throws RemoteException            never thrown directly; declared for the
     *                                    remote interface contract
     */
    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null)   throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");

        // Snapshot the set so the task is independent of caller mutations.
        Set<Uri> snapshot = new LinkedHashSet<Uri>(codebaseUrls);
        analysisExecutor.execute(new AnalysisTask(snapshot));

        // Update peak queue depth after successfully enqueuing.
        if (poolExecutor != null) {
            long depth = poolExecutor.getQueue().size() + poolExecutor.getActiveCount();
            long current = peakQueueDepth.get();
            while (depth > current) {
                if (peakQueueDepth.compareAndSet(current, depth)) break;
                current = peakQueueDepth.get();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private inner class: VerdictRecord
    // -------------------------------------------------------------------------

    /**
     * Snapshot of a single analysis verdict, stored in the
     * {@link #recentVerdicts} circular buffer for debugging.
     */
    static final class VerdictRecord {
        final long        timestamp;
        final Set<Uri>    codebaseUrls;
        final VerdictType verdict;
        final long        durationMs;

        VerdictRecord(long timestamp, Set<Uri> codebaseUrls,
                      VerdictType verdict, long durationMs) {
            this.timestamp    = timestamp;
            this.codebaseUrls = codebaseUrls;
            this.verdict      = verdict;
            this.durationMs   = durationMs;
        }
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
            totalAnalysisAttempts.incrementAndGet();
            long analysisStart = System.nanoTime();

            VerdictType verdict;
            try {
                verdict = analyzeCodebase(codebaseUrls);
            } catch (Exception e) {
                analysisErrors.incrementAndGet();
                logger.log(Level.SEVERE,
                        "Unexpected error during analysis of " + codebaseUrls, e);
                verdict = VerdictType.DANGEROUS;
            }

            long analysisDurationMs = (System.nanoTime() - analysisStart) / 1_000_000;
            totalAnalysisCompleted.incrementAndGet();
            recordAnalysisDuration(analysisDurationMs);

            // Count verdict type.
            if (verdict == VerdictType.SAFE) {
                safeVerdicts.incrementAndGet();
            } else {
                dangerousVerdicts.incrementAndGet();
            }

            // Record in the recent-verdicts circular buffer.
            int idx = (recentVerdictIndex.getAndIncrement() & Integer.MAX_VALUE) % 100;
            recentVerdicts[idx] = new VerdictRecord(
                    System.currentTimeMillis(), codebaseUrls, verdict, analysisDurationMs);

            // Sign and submit to registry.
            long submitStart = System.nanoTime();
            try {
                long timestamp = System.currentTimeMillis();
                Uri[] sortedUrls = sortedUriArray(codebaseUrls);
                byte[] canonical = canonicalBytes(sortedUrls, verdict, timestamp);
                byte[] sig       = sign(enginePrivateKey, sigAlgorithm, canonical);
                SignedVerdict sv = new SignedVerdict(sortedUrls, verdict, timestamp, sig);
                registry.submitVerdict(engineId, sv);
                long submitDurationMs = (System.nanoTime() - submitStart) / 1_000_000;
                successfulSubmissions.incrementAndGet();
                recordSubmissionLatency(submitDurationMs);
                logger.log(Level.INFO,
                        "Submitted {0} verdict for {1} to registry",
                        new Object[]{verdict, codebaseUrls});
            } catch (Exception e) {
                failedSubmissions.incrementAndGet();
                String errType = e.getClass().getName();
                submissionErrorTypes.computeIfAbsent(errType, k -> new AtomicLong())
                        .incrementAndGet();
                logger.log(Level.SEVERE,
                        "Failed to sign or submit verdict for " + codebaseUrls, e);
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
    private VerdictType analyzeCodebase(Set<Uri> codebaseUrls) {
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
                            if (classBytes == null) {
                                classParseErrors.incrementAndGet();
                                return VerdictType.DANGEROUS;
                            }
                            if (containsDangerousCode(classBytes)) {
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
                classParseErrors.incrementAndGet();
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
    static final class LoggingAbortPolicy implements RejectedExecutionHandler {

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
    private static ThreadPoolExecutor createAnalysisExecutor(
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

    // -------------------------------------------------------------------------
    // Metrics helpers
    // -------------------------------------------------------------------------

    /**
     * Records an analysis duration observation in the duration histogram.
     *
     * @param ms duration in milliseconds
     */
    private void recordAnalysisDuration(long ms) {
        if      (ms <    100) analysisDuration_0_100ms.incrementAndGet();
        else if (ms <    500) analysisDuration_100_500ms.incrementAndGet();
        else if (ms <   1000) analysisDuration_500_1000ms.incrementAndGet();
        else if (ms <   5000) analysisDuration_1_5s.incrementAndGet();
        else if (ms <  10000) analysisDuration_5_10s.incrementAndGet();
        else                  analysisDuration_10s_plus.incrementAndGet();
    }

    /**
     * Records a registry submission latency observation in the latency
     * histogram.
     *
     * @param ms latency in milliseconds
     */
    private void recordSubmissionLatency(long ms) {
        if      (ms <   100) submissionLatency_0_100ms.incrementAndGet();
        else if (ms <   500) submissionLatency_100_500ms.incrementAndGet();
        else if (ms <  1000) submissionLatency_500_1000ms.incrementAndGet();
        else                 submissionLatency_1000ms_plus.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Metrics API
    // -------------------------------------------------------------------------

    /**
     * Returns the current analysis-queue depth: the number of tasks that are
     * either queued or actively executing.
     *
     * @return current queue depth, or {@code -1} if depth cannot be determined
     *         (e.g. when a custom executor was injected for testing)
     */
    public long queueDepth() {
        if (poolExecutor != null) {
            return poolExecutor.getQueue().size() + poolExecutor.getActiveCount();
        }
        return -1L;
    }

    /**
     * Returns the cumulative count of analysis tasks that have been rejected
     * because the bounded work queue was full.
     *
     * @return total rejection count since the engine was created (or since
     *         the last {@link #resetMetrics()} call)
     */
    public long totalRejections() {
        return rejectedTaskCount.get();
    }

    /**
     * Records a timeout event for an in-progress analysis task.
     *
     * <p>This method is called by the analysis timeout handler introduced in
     * Task 2.  It is also package-private to enable direct invocation from
     * unit tests.
     */
    void recordAnalysisTimeout() {
        analysisTimeouts.incrementAndGet();
    }

    /**
     * Returns a point-in-time snapshot of all operational metrics.
     *
     * <p>The returned map has four top-level keys: {@code "queue"},
     * {@code "analysis"}, {@code "verdict"}, and {@code "submission"}, each
     * mapping to a nested {@link Map}.  All values are read atomically but
     * the snapshot is not globally consistent (individual counters may
     * advance between reads in a concurrent environment).
     *
     * @return snapshot of current metrics; never {@code null}
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Queue section.
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("currentDepth",    queueDepth());
        queue.put("peakDepth",       peakQueueDepth.get());
        queue.put("totalRejections", rejectedTaskCount.get());
        metrics.put("queue", queue);

        // Analysis section.
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("totalAttempts",    totalAnalysisAttempts.get());
        analysis.put("totalCompleted",   totalAnalysisCompleted.get());
        analysis.put("totalTimeouts",    analysisTimeouts.get());
        analysis.put("classParseErrors", classParseErrors.get());
        Map<String, Object> durationBuckets = new LinkedHashMap<>();
        durationBuckets.put("0_100ms",    analysisDuration_0_100ms.get());
        durationBuckets.put("100_500ms",  analysisDuration_100_500ms.get());
        durationBuckets.put("500_1000ms", analysisDuration_500_1000ms.get());
        durationBuckets.put("1_5s",       analysisDuration_1_5s.get());
        durationBuckets.put("5_10s",      analysisDuration_5_10s.get());
        durationBuckets.put("10s_plus",   analysisDuration_10s_plus.get());
        analysis.put("durationBuckets", durationBuckets);
        metrics.put("analysis", analysis);

        // Verdict section.
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("safe",      safeVerdicts.get());
        verdict.put("dangerous", dangerousVerdicts.get());
        verdict.put("errors",    analysisErrors.get());
        metrics.put("verdict", verdict);

        // Submission section.
        Map<String, Object> submission = new LinkedHashMap<>();
        submission.put("successful", successfulSubmissions.get());
        submission.put("failed",     failedSubmissions.get());
        Map<String, Long> errorsByType = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> e : submissionErrorTypes.entrySet()) {
            errorsByType.put(e.getKey(), e.getValue().get());
        }
        submission.put("errorsByType", errorsByType);
        Map<String, Object> latencyBuckets = new LinkedHashMap<>();
        latencyBuckets.put("0_100ms",     submissionLatency_0_100ms.get());
        latencyBuckets.put("100_500ms",   submissionLatency_100_500ms.get());
        latencyBuckets.put("500_1000ms",  submissionLatency_500_1000ms.get());
        latencyBuckets.put("1000ms_plus", submissionLatency_1000ms_plus.get());
        submission.put("latencyBuckets", latencyBuckets);
        metrics.put("submission", submission);

        return metrics;
    }

    /**
     * Resets all mutable metrics counters and histograms to zero and clears
     * the recent-verdicts circular buffer.
     *
     * <p>This method is primarily intended for testing.  In a production
     * deployment it may be used to obtain a clean baseline after a warm-up
     * period.  The reset is not atomic: counters are zeroed individually and
     * concurrent updates may be partially observed.
     */
    public void resetMetrics() {
        rejectedTaskCount.set(0);
        peakQueueDepth.set(0);
        totalAnalysisAttempts.set(0);
        totalAnalysisCompleted.set(0);
        analysisTimeouts.set(0);
        classParseErrors.set(0);
        analysisDuration_0_100ms.set(0);
        analysisDuration_100_500ms.set(0);
        analysisDuration_500_1000ms.set(0);
        analysisDuration_1_5s.set(0);
        analysisDuration_5_10s.set(0);
        analysisDuration_10s_plus.set(0);
        safeVerdicts.set(0);
        dangerousVerdicts.set(0);
        analysisErrors.set(0);
        Arrays.fill(recentVerdicts, null);
        recentVerdictIndex.set(0);
        successfulSubmissions.set(0);
        failedSubmissions.set(0);
        submissionErrorTypes.clear();
        submissionLatency_0_100ms.set(0);
        submissionLatency_100_500ms.set(0);
        submissionLatency_500_1000ms.set(0);
        submissionLatency_1000ms_plus.set(0);
    }
}
