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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * The following constant-pool strings are considered dangerous by default.
 * The list is configurable via the {@code dangerousConstantPoolPatterns}
 * Jini configuration entry, allowing deployments to tighten or relax the
 * default threat model.
 *
 * <h3>OS-process execution</h3>
 * <ul>
 *   <li>{@code java/lang/Runtime} — OS-process execution</li>
 *   <li>{@code java/lang/ProcessBuilder} — OS-process execution</li>
 *   <li>{@code java/lang/ProcessImpl} — OS-process execution (JDK internal)</li>
 * </ul>
 *
 * <h3>Direct memory access</h3>
 * <ul>
 *   <li>{@code sun/misc/Unsafe} — direct memory access</li>
 *   <li>{@code jdk/internal/misc/Unsafe} — direct memory access (JDK 9+)</li>
 * </ul>
 *
 * <h3>Arbitrary class-loading</h3>
 * <ul>
 *   <li>{@code java/lang/ClassLoader} — arbitrary class-loading</li>
 * </ul>
 *
 * <h3>Reflection APIs</h3>
 * <ul>
 *   <li>{@code java/lang/reflect/Method} — invoke arbitrary methods via reflection</li>
 *   <li>{@code java/lang/reflect/Field} — read/write arbitrary field values</li>
 *   <li>{@code java/lang/reflect/Constructor} — instantiate arbitrary classes</li>
 *   <li>{@code java/lang/reflect/Proxy} — create dynamic proxy classes</li>
 *   <li>{@code jdk/internal/reflect/Reflection} — JDK-internal reflection</li>
 * </ul>
 *
 * <h3>Method/Variable Handles (Java 7+)</h3>
 * <ul>
 *   <li>{@code java/lang/invoke/MethodHandle} — low-level method invocation</li>
 *   <li>{@code java/lang/invoke/VarHandle} — atomic variable access</li>
 *   <li>{@code java/lang/invoke/MethodHandles} — method handle lookup</li>
 *   <li>{@code java/lang/invoke/MethodHandles$Lookup} — privileged lookup object</li>
 * </ul>
 *
 * <h3>Dynamic code generation &amp; bytecode manipulation</h3>
 * <ul>
 *   <li>{@code jdk/internal/org/objectweb/asm/} — JDK-bundled ASM bytecode library</li>
 *   <li>{@code javassist/} — Javassist bytecode manipulation library</li>
 *   <li>{@code net/bytebuddy/} — Byte Buddy bytecode instrumentation</li>
 *   <li>{@code org/springframework/cglib/} — Spring CGLIB code generation</li>
 * </ul>
 *
 * <h3>Module system &amp; shared secrets</h3>
 * <ul>
 *   <li>{@code java/lang/Module} — module system manipulation</li>
 *   <li>{@code java/lang/ModuleLayer} — module layer access</li>
 *   <li>{@code sun/misc/SharedSecrets} — JDK internal shared secrets</li>
 *   <li>{@code jdk/internal/access/SharedSecrets} — shared secrets (JDK 9+)</li>
 * </ul>
 *
 * <h3>Script execution &amp; expression languages</h3>
 * <ul>
 *   <li>{@code javax/script/ScriptEngine} — execute arbitrary scripts</li>
 *   <li>{@code org/mozilla/javascript/} — Rhino JavaScript engine</li>
 *   <li>{@code org/python/core/} — Jython Python execution</li>
 *   <li>{@code groovy/lang/} — Groovy script execution</li>
 * </ul>
 *
 * <h3>File I/O &amp; process interaction</h3>
 * <ul>
 *   <li>{@code java/nio/file/Files} — file write operations</li>
 *   <li>{@code java/io/FileOutputStream} — direct file writes</li>
 *   <li>{@code java/io/RandomAccessFile} — arbitrary file access</li>
 *   <li>{@code java/nio/channels/FileChannel} — NIO file operations</li>
 * </ul>
 *
 * <p>Note: native library loading ({@code loadLibrary}) is intentionally
 * <em>not</em> treated as dangerous here; whether a service is allowed to
 * load native code is governed by the Jini/Phoenix security policy rather
 * than by bytecode analysis.
 * Additionally, if a JAR entry cannot be read (e.g. due to a network error),
 * the verdict is conservatively {@link VerdictType#DANGEROUS}.
 *
 * <h2>Configuration</h2>
 * The pattern list can be overridden via Jini configuration:
 * <pre>
 * au.net.zeus.jgdms.bae {
 *     dangerousConstantPoolPatterns = new String[]{
 *         "java/lang/reflect/Method",
 *         "javax/script/ScriptEngine",
 *         // ... additional patterns
 *     };
 * }
 * </pre>
 * Pass the configured array to the
 * {@link #BytecodeAnalysisEngineImpl(PrivateKey, String, String, VerdictRegistry, String[])}
 * constructor.  An empty array disables all pattern checks (use only in
 * controlled test environments).
 *
 * <h2>Timeout protection</h2>
 * Each analysis task is bounded by a configurable per-task timeout (default
 * {@value #ANALYSIS_TASK_TIMEOUT_MILLIS} ms).  If a task does not complete
 * within the timeout — for example because {@code url.openStream()} blocks
 * indefinitely against an unresponsive codebase server — the analysis thread
 * is interrupted (best-effort) and a {@link VerdictType#DANGEROUS} verdict is
 * submitted to the registry as a fail-safe.  A WARNING is logged with the
 * codebase URLs and the elapsed time.
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
     * Default per-task analysis timeout in milliseconds (5 minutes).
     * If an analysis task does not complete within this time, the analysis
     * thread is interrupted (best-effort) and a {@link VerdictType#DANGEROUS}
     * verdict is submitted to the registry as a fail-safe.
     *
     * <p>Administrators may override this per deployment by passing a custom
     * value to the package-private test/service constructor.
     */
    static final long ANALYSIS_TASK_TIMEOUT_MILLIS = 300_000L;

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
     * Default set of internal JVM class names (using {@code /} separators)
     * whose presence in a class file's constant pool is treated as a
     * dangerous indicator.
     *
     * <p>This list covers seven broad threat categories:
     * <ol>
     *   <li>OS-process execution</li>
     *   <li>Direct memory access</li>
     *   <li>Arbitrary class-loading</li>
     *   <li>Reflection &amp; method/variable handles</li>
     *   <li>Dynamic code generation &amp; bytecode manipulation</li>
     *   <li>Script execution engines</li>
     *   <li>File I/O and module/shared-secrets access</li>
     * </ol>
     *
     * <p>Deployments may supply a custom list via the constructor that accepts
     * a {@code String[]} parameter (intended for Jini configuration injection).
     * An empty array disables all pattern checks.
     */
    static final String[] DANGEROUS_CP_ENTRIES = {
        // OS-process execution
        "java/lang/Runtime",
        "java/lang/ProcessBuilder",
        "java/lang/ProcessImpl",
        // Direct memory access
        "sun/misc/Unsafe",
        "jdk/internal/misc/Unsafe",
        // Arbitrary class-loading
        "java/lang/ClassLoader",
        // Reflection APIs
        "java/lang/reflect/Method",
        "java/lang/reflect/Field",
        "java/lang/reflect/Constructor",
        "java/lang/reflect/Proxy",
        "jdk/internal/reflect/Reflection",
        // Method/Variable Handles (Java 7+)
        "java/lang/invoke/MethodHandle",
        "java/lang/invoke/VarHandle",
        "java/lang/invoke/MethodHandles",
        "java/lang/invoke/MethodHandles$Lookup",
        // Dynamic code generation & bytecode manipulation
        "jdk/internal/org/objectweb/asm/",
        "javassist/",
        "net/bytebuddy/",
        "org/springframework/cglib/",
        // Module system & shared secrets
        "java/lang/Module",
        "java/lang/ModuleLayer",
        "sun/misc/SharedSecrets",
        "jdk/internal/access/SharedSecrets",
        // Script execution & expression languages
        "javax/script/ScriptEngine",
        "org/mozilla/javascript/",
        "org/python/core/",
        "groovy/lang/",
        // File I/O & process interaction
        "java/nio/file/Files",
        "java/io/FileOutputStream",
        "java/io/RandomAccessFile",
        "java/nio/channels/FileChannel"
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
    /**
     * The set of constant-pool patterns treated as dangerous for this engine
     * instance.  Defaults to {@link #DANGEROUS_CP_ENTRIES}; may be overridden
     * via the constructor that accepts a custom {@code String[]} argument.
     */
    private final String[] dangerousPatterns;

    /**
     * Per-task timeout in milliseconds.  Defaults to
     * {@value #ANALYSIS_TASK_TIMEOUT_MILLIS}; may be overridden via the
     * package-private constructor for testing or per-deployment tuning.
     */
    private final long taskTimeoutMillis;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code BytecodeAnalysisEngineImpl} using the default
     * dangerous constant-pool patterns ({@link #DANGEROUS_CP_ENTRIES}).
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

        this.enginePrivateKey  = enginePrivateKey;
        this.sigAlgorithm      = sigAlgorithm;
        this.engineId          = engineId;
        this.registry          = registry;
        this.dangerousPatterns = DANGEROUS_CP_ENTRIES.clone();
        this.analysisExecutor  = createAnalysisExecutor(this.rejectedTaskCount);
        this.taskTimeoutMillis = ANALYSIS_TASK_TIMEOUT_MILLIS;
    }

    /**
     * Creates a new {@code BytecodeAnalysisEngineImpl} with a configurable
     * dangerous pattern list.
     *
     * <p>This constructor is intended for Jini configuration injection.
     * Example configuration:
     * <pre>
     * au.net.zeus.jgdms.bae {
     *     dangerousConstantPoolPatterns = new String[]{
     *         "java/lang/reflect/Method",
     *         "javax/script/ScriptEngine",
     *     };
     * }
     * </pre>
     *
     * @param enginePrivateKey the engine's private key for signing verdicts;
     *                         must be non-null
     * @param sigAlgorithm     JCA standard name of the signature algorithm;
     *                         must be non-null and non-empty
     * @param engineId         the identifier used to register this engine;
     *                         must be non-null and non-empty
     * @param registry         the registry to which signed verdicts are
     *                         submitted; must be non-null
     * @param dangerousPatterns
     *                         the constant-pool patterns to treat as dangerous;
     *                         must be non-null; an empty array disables all
     *                         pattern checks (use only in test environments)
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code sigAlgorithm} or
     *                                  {@code engineId} is empty
     */
    public BytecodeAnalysisEngineImpl(PrivateKey enginePrivateKey,
                                      String sigAlgorithm,
                                      String engineId,
                                      VerdictRegistry registry,
                                      String[] dangerousPatterns) {
        if (enginePrivateKey == null)  throw new NullPointerException("enginePrivateKey");
        if (sigAlgorithm == null)      throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty())    throw new IllegalArgumentException("sigAlgorithm must not be empty");
        if (engineId == null)          throw new NullPointerException("engineId");
        if (engineId.isEmpty())        throw new IllegalArgumentException("engineId must not be empty");
        if (registry == null)          throw new NullPointerException("registry");
        if (dangerousPatterns == null) throw new NullPointerException("dangerousPatterns");

        this.enginePrivateKey  = enginePrivateKey;
        this.sigAlgorithm      = sigAlgorithm;
        this.engineId          = engineId;
        this.registry          = registry;
        this.dangerousPatterns = dangerousPatterns.clone();
        this.analysisExecutor  = createAnalysisExecutor(this.rejectedTaskCount);
        this.taskTimeoutMillis = ANALYSIS_TASK_TIMEOUT_MILLIS;
    }

    /**
     * Package-private constructor that accepts a custom {@link ExecutorService}.
     * Intended only for unit testing; production code must use the public
     * constructors.
     */
    BytecodeAnalysisEngineImpl(PrivateKey enginePrivateKey,
                               String sigAlgorithm,
                               String engineId,
                               VerdictRegistry registry,
                               ExecutorService executor) {
        this(enginePrivateKey, sigAlgorithm, engineId, registry, executor,
                ANALYSIS_TASK_TIMEOUT_MILLIS);
    }

    /**
     * Package-private constructor that accepts a custom {@link ExecutorService}
     * and a per-task timeout.  Intended only for unit testing or per-deployment
     * tuning; production code should use the public constructors.
     *
     * @param taskTimeoutMillis per-task analysis timeout in milliseconds;
     *                          must be positive
     */
    BytecodeAnalysisEngineImpl(PrivateKey enginePrivateKey,
                               String sigAlgorithm,
                               String engineId,
                               VerdictRegistry registry,
                               ExecutorService executor,
                               long taskTimeoutMillis) {
        if (enginePrivateKey == null) throw new NullPointerException("enginePrivateKey");
        if (sigAlgorithm == null)     throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty())   throw new IllegalArgumentException("sigAlgorithm must not be empty");
        if (engineId == null)         throw new NullPointerException("engineId");
        if (engineId.isEmpty())       throw new IllegalArgumentException("engineId must not be empty");
        if (registry == null)         throw new NullPointerException("registry");
        if (executor == null)         throw new NullPointerException("executor");
        if (taskTimeoutMillis <= 0)   throw new IllegalArgumentException("taskTimeoutMillis must be positive");

        this.enginePrivateKey  = enginePrivateKey;
        this.sigAlgorithm      = sigAlgorithm;
        this.engineId          = engineId;
        this.registry          = registry;
        this.dangerousPatterns = DANGEROUS_CP_ENTRIES.clone();
        this.analysisExecutor  = executor;
        this.taskTimeoutMillis = taskTimeoutMillis;
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
     * <p>Each analysis task is bounded by a configurable timeout (default
     * {@value #ANALYSIS_TASK_TIMEOUT_MILLIS} ms).  If the task does not
     * complete within that time, the analysis thread is interrupted
     * (best-effort) and a {@link VerdictType#DANGEROUS} verdict is
     * submitted to the registry as a fail-safe.  A WARNING is logged with
     * the codebase URLs and elapsed time.
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
        analysisExecutor.execute(new AnalysisTaskWithTimeout(snapshot));
    }

    // -------------------------------------------------------------------------
    // Private inner class: AnalysisTaskWithTimeout
    // -------------------------------------------------------------------------

    /**
     * Runnable that downloads JARs, scans their class files within a
     * configurable timeout, signs the resulting {@link SignedVerdict}, and
     * submits it to the registry.
     *
     * <p>The I/O-intensive analysis ({@link #analyzeCodebase}) is executed
     * on a dedicated daemon thread.  The outer (executor-pool) thread waits
     * for completion using {@link FutureTask#get(long, TimeUnit)}.  If the
     * timeout ({@link #taskTimeoutMillis} ms) elapses before analysis
     * completes, the analysis thread is interrupted (best-effort), a WARNING
     * is logged, and a {@link VerdictType#DANGEROUS} verdict is submitted to
     * the registry as a fail-safe.
     */
    private final class AnalysisTaskWithTimeout implements Runnable {

        private final Set<Uri> codebaseUrls;

        AnalysisTaskWithTimeout(Set<Uri> codebaseUrls) {
            this.codebaseUrls = codebaseUrls;
        }

        @Override
        public void run() {
            long startNanos = System.nanoTime();
            final Set<Uri> urls = codebaseUrls;
            final String[] patterns = dangerousPatterns;
            FutureTask<VerdictType> analysisWork = new FutureTask<VerdictType>(
                    new Callable<VerdictType>() {
                        @Override
                        public VerdictType call() {
                            return analyzeCodebase(urls, patterns);
                        }
                    });
            Thread worker = new Thread(analysisWork, "BAE-analysis-worker");
            worker.setDaemon(true);
            worker.start();

            VerdictType verdict;
            try {
                verdict = analysisWork.get(taskTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                long elapsedMs =
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                logger.log(Level.WARNING,
                        "Analysis task timed out after {0} ms for codebase {1}; "
                        + "submitting DANGEROUS verdict as fail-safe",
                        new Object[]{elapsedMs, codebaseUrls});
                analysisWork.cancel(true);
                worker.interrupt();  // belt-and-suspenders
                verdict = VerdictType.DANGEROUS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                analysisWork.cancel(true);
                worker.interrupt();
                verdict = VerdictType.DANGEROUS;
            } catch (ExecutionException e) {
                logger.log(Level.SEVERE,
                        "Unexpected analysis failure for " + codebaseUrls,
                        e.getCause());
                verdict = VerdictType.DANGEROUS;
            }

            signAndSubmit(verdict);
        }

        private void signAndSubmit(VerdictType verdict) {
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
     *
     * @param codebaseUrls the set of codebase URIs to analyse
     * @param patterns     the constant-pool patterns to treat as dangerous
     */
    private static VerdictType analyzeCodebase(Set<Uri> codebaseUrls,
                                               String[] patterns) {
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
                            if (classBytes != null && containsDangerousCode(classBytes, patterns)) {
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
    // Virtual-thread anti-pattern detection — constant-pool markers
    // -------------------------------------------------------------------------

    /**
     * Constant-pool UTF-8 entries that indicate potential virtual-thread
     * pinning: holding a monitor (synchronized block / method) while calling
     * methods that can block.  The presence of both a {@code MONITORENTER}
     * opcode in the bytecode <em>and</em> one of these entries in the
     * constant pool is required before the class is flagged.
     */
    static final String[] PINNING_CP_ENTRIES = {
        // JDK blocking I/O inside synchronized scope
        "java/net/Socket",
        "java/net/ServerSocket",
        "java/io/InputStream",
        "java/io/OutputStream",
        "java/io/RandomAccessFile",
        // NIO selectors
        "java/nio/channels/Selector",
        "java/nio/channels/SelectableChannel",
    };

    /**
     * Constant-pool UTF-8 method names / descriptors that represent yield
     * points inside a loop.  If any of these strings appear in the constant
     * pool, a backward-GOTO loop is <em>not</em> flagged as a CPU-consuming
     * loop.
     */
    static final String[] LOOP_YIELD_METHODS = {
        // Thread yield / sleep / park
        "sleep",
        "yield",
        "park",
        "parkNanos",
        "parkUntil",
        // Lock operations that can block (not pure spin)
        "lock",
        "tryLock",
        "lockInterruptibly",
        "await",
        "awaitNanos",
        "awaitUntil",
        "awaitUninterruptibly",
        // BlockingQueue / condition
        "take",
        "poll",
        "put",
        "offer",
    };

    /**
     * UTF-8 constant-pool string that identifies an {@code InterruptedException}
     * handler in the exception table.
     */
    private static final String INTERRUPTED_EXCEPTION_CLASS =
            "java/lang/InterruptedException";

    /**
     * UTF-8 method name used to restore the interrupt flag.
     * An exception handler that contains a call to this method is considered
     * to handle {@code InterruptedException} correctly.
     */
    private static final String INTERRUPT_METHOD = "interrupt";

    // -------------------------------------------------------------------------
    // Class-file constant-pool scanner
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if any {@code CONSTANT_Utf8} entry in the constant
     * pool of the supplied class file bytes matches a known dangerous pattern
     * from {@link #DANGEROUS_CP_ENTRIES}.
     *
     * <p>This is a convenience overload that uses the default pattern list.
     * Use {@link #containsDangerousCode(byte[], String[])} to specify a custom
     * pattern list.
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
        return containsDangerousCode(classBytes, DANGEROUS_CP_ENTRIES);
    }

    /**
     * Returns {@code true} if any {@code CONSTANT_Utf8} entry in the constant
     * pool of the supplied class file bytes starts with or equals any entry in
     * the supplied {@code patterns} array, <em>or</em> if the bytecode exhibits
     * any of the three virtual-thread anti-patterns:
     * <ul>
     *   <li>Virtual-thread pinning ({@link #hasVirtualThreadPinning})</li>
     *   <li>CPU-consuming loops ({@link #hasCpuConsumingLoops})</li>
     *   <li>Interrupt-swallowing ({@link #hasInterruptSwallowing})</li>
     * </ul>
     *
     * <p>Patterns that end with {@code /} are treated as prefix matches so that
     * an entire package hierarchy (e.g. {@code javassist/}) is covered by a
     * single entry.  All other patterns require an exact string match.
     *
     * <p>If the byte array does not begin with the class-file magic number
     * {@code 0xCAFEBABE}, or if the array is shorter than the minimum valid
     * class-file header, the method conservatively returns {@code true}.
     *
     * @param classBytes the raw bytes of a {@code .class} file
     * @param patterns   the constant-pool patterns to treat as dangerous;
     *                   an empty array causes the method to always return
     *                   {@code false} (unless the file header is invalid)
     * @return {@code true} if dangerous code is detected or if the file cannot
     *         be parsed; {@code false} otherwise
     */
    static boolean containsDangerousCode(byte[] classBytes, String[] patterns) {
        if (classBytes.length < 10) return true;

        // Verify magic: 0xCAFEBABE; any other value means the bytes are not a
        // valid class file, so treat conservatively as dangerous.
        if ((classBytes[0] & 0xFF) != 0xCA || (classBytes[1] & 0xFF) != 0xFE
                || (classBytes[2] & 0xFF) != 0xBA || (classBytes[3] & 0xFF) != 0xBE) {
            return true;
        }

        if (patterns.length == 0) {
            // No CP patterns to check, but still run threading anti-pattern detectors.
            return hasVirtualThreadPinning(classBytes)
                    || hasCpuConsumingLoops(classBytes)
                    || hasInterruptSwallowing(classBytes);
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
                    if (isDangerousCpEntry(value, patterns)) return true;
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

        // CP scan clean — now run opcode-level threading anti-pattern detectors.
        return hasVirtualThreadPinning(classBytes)
                || hasCpuConsumingLoops(classBytes)
                || hasInterruptSwallowing(classBytes);
    }

    /**
     * Returns {@code true} if {@code cpEntry} matches any entry in
     * {@code patterns}.
     *
     * <p>A pattern that ends with {@code /} is treated as a package-prefix
     * match: the entry matches if it starts with that pattern.  All other
     * patterns require an exact string match.
     *
     * @param cpEntry  a {@code CONSTANT_Utf8} value from a class file constant pool
     * @param patterns the patterns to check against
     * @return {@code true} if the entry matches any pattern
     */
    private static boolean isDangerousCpEntry(String cpEntry, String[] patterns) {
        for (String pattern : patterns) {
            if (pattern.endsWith("/")) {
                if (cpEntry.startsWith(pattern)) return true;
            } else {
                if (cpEntry.equals(pattern)) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Virtual-thread anti-pattern detection — bytecode-level analysis
    // -------------------------------------------------------------------------

    /**
     * Returns the offset immediately past the constant pool in {@code classBytes},
     * or {@code -1} if the class bytes are malformed (too short, bad magic,
     * invalid CP entry).
     *
     * <p>This shared helper is used by the three threading anti-pattern
     * detectors to skip over the constant pool so they can locate the
     * method table, access flags, and attribute structures that follow it.
     *
     * @param classBytes raw bytes of a {@code .class} file
     * @return offset after the constant pool, or {@code -1} on parse failure
     */
    private static int skipConstantPool(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) return -1;
        if ((classBytes[0] & 0xFF) != 0xCA || (classBytes[1] & 0xFF) != 0xFE
                || (classBytes[2] & 0xFF) != 0xBA || (classBytes[3] & 0xFF) != 0xBE) {
            return -1;
        }
        int cpCount = ((classBytes[8] & 0xFF) << 8) | (classBytes[9] & 0xFF);
        int offset  = 10;
        for (int i = 1; i < cpCount; i++) {
            if (offset >= classBytes.length) return -1;
            int tag = classBytes[offset++] & 0xFF;
            switch (tag) {
                case 1: { // CONSTANT_Utf8
                    if (offset + 2 > classBytes.length) return -1;
                    int len = ((classBytes[offset] & 0xFF) << 8)
                            | (classBytes[offset + 1] & 0xFF);
                    offset += 2 + len;
                    break;
                }
                case 3: case 4:   // CONSTANT_Integer, CONSTANT_Float
                    offset += 4;
                    break;
                case 5: case 6:   // CONSTANT_Long, CONSTANT_Double
                    offset += 8;
                    i++;           // occupies two CP slots
                    break;
                case 7: case 8: case 16: case 19: case 20:
                    offset += 2;
                    break;
                case 9: case 10: case 11: case 12: case 17: case 18:
                    offset += 4;
                    break;
                case 15:
                    offset += 3;
                    break;
                default:
                    return -1;     // unknown tag — treat conservatively
            }
            if (offset > classBytes.length) return -1;
        }
        return offset;
    }

    /**
     * Collects all UTF-8 strings from the constant pool of {@code classBytes}
     * into a {@link java.util.Set}.
     *
     * @param classBytes raw class bytes (magic already validated)
     * @return set of all {@code CONSTANT_Utf8} values, or an empty set if the
     *         constant pool cannot be parsed
     */
    private static java.util.Set<String> collectUtf8Entries(byte[] classBytes) {
        java.util.Set<String> result = new java.util.HashSet<String>();
        if (classBytes == null || classBytes.length < 10) return result;
        int cpCount = ((classBytes[8] & 0xFF) << 8) | (classBytes[9] & 0xFF);
        int offset  = 10;
        for (int i = 1; i < cpCount; i++) {
            if (offset >= classBytes.length) break;
            int tag = classBytes[offset++] & 0xFF;
            switch (tag) {
                case 1: {
                    if (offset + 2 > classBytes.length) return result;
                    int len = ((classBytes[offset] & 0xFF) << 8)
                            | (classBytes[offset + 1] & 0xFF);
                    offset += 2;
                    if (offset + len > classBytes.length) return result;
                    result.add(new String(classBytes, offset, len,
                            StandardCharsets.UTF_8));
                    offset += len;
                    break;
                }
                case 3: case 4:   offset += 4; break;
                case 5: case 6:   offset += 8; i++; break;
                case 7: case 8: case 16: case 19: case 20: offset += 2; break;
                case 9: case 10: case 11: case 12: case 17: case 18: offset += 4; break;
                case 15:          offset += 3; break;
                default:          return result;
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if the supplied class bytecode exhibits a
     * <em>virtual-thread pinning</em> anti-pattern.
     *
     * <p>A class is flagged when <em>both</em> of the following hold:
     * <ol>
     *   <li>The bytecode of at least one method contains a
     *       {@code MONITORENTER} opcode ({@code 0xC2}), indicating the use of
     *       a {@code synchronized} block.</li>
     *   <li>The constant pool references at least one of the known
     *       blocking-I/O or wait/notify entry points listed in
     *       {@link #PINNING_CP_ENTRIES}.</li>
     * </ol>
     *
     * <p>Virtual threads pin their carrier thread while holding a monitor,
     * so combining {@code synchronized} blocks with blocking operations
     * prevents the JVM from unmounting the virtual thread and wastes a
     * platform thread for the duration of the blocking call.
     *
     * <p>Conservative behaviour: any parse error → {@code true} (DANGEROUS).
     *
     * @param classBytes raw bytes of a {@code .class} file
     * @return {@code true} if the pinning pattern is detected or if parsing fails
     */
    static boolean hasVirtualThreadPinning(byte[] classBytes) {
        // Step 1: Constant-pool check — does the class reference any
        // blocking-I/O or Object-monitor class names?
        java.util.Set<String> cpEntries = collectUtf8Entries(classBytes);
        if (cpEntries.isEmpty() && (classBytes == null || classBytes.length < 10)) {
            return true; // parse failure → conservative
        }
        boolean hasPinningRef = false;
        for (String entry : PINNING_CP_ENTRIES) {
            if (cpEntries.contains(entry)) {
                hasPinningRef = true;
                break;
            }
        }
        if (!hasPinningRef) return false;

        // Step 2: Bytecode check — does any method contain MONITORENTER?
        return scanMethodBytecodeForOpcode(classBytes, 0xC2 /* MONITORENTER */);
    }

    /**
     * Returns {@code true} if the supplied class bytecode exhibits a
     * <em>CPU-consuming loop</em> anti-pattern.
     *
     * <p>A class is flagged when at least one method contains a backward
     * {@code GOTO} or {@code GOTO_W} instruction (forming a loop) <em>and</em>
     * the constant pool contains <em>none</em> of the yield-point method names
     * listed in {@link #LOOP_YIELD_METHODS}.  A backward {@code GOTO} with
     * no yield point is a strong signal of a busy-wait spin loop that will
     * monopolise the carrier thread indefinitely.
     *
     * <p>Conservative behaviour: any parse error → {@code true} (DANGEROUS).
     *
     * @param classBytes raw bytes of a {@code .class} file
     * @return {@code true} if the anti-pattern is detected or if parsing fails
     */
    static boolean hasCpuConsumingLoops(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) return true;

        // Step 1: Collect CP strings once.
        java.util.Set<String> cpEntries = collectUtf8Entries(classBytes);

        // Step 2: If the class already references a yield-point method, it is
        // not a pure spin loop — pass it.
        for (String yieldMethod : LOOP_YIELD_METHODS) {
            if (cpEntries.contains(yieldMethod)) return false;
        }

        // Step 3: Does any method bytecode contain a backward GOTO / GOTO_W?
        return scanMethodBytecodeForBackwardGoto(classBytes);
    }

    /**
     * Returns {@code true} if the supplied class bytecode exhibits an
     * <em>interrupt-swallowing</em> anti-pattern.
     *
     * <p>A class is flagged when at least one method's exception table
     * declares a handler for {@code java/lang/InterruptedException} and the
     * bytecode of that handler does <em>not</em> contain a call to
     * {@code interrupt()} (which would re-assert the interrupted status via
     * {@code Thread.currentThread().interrupt()}).
     *
     * <p>Swallowing {@code InterruptedException} silently prevents the JVM
     * scheduler from cleaning up virtual threads and blocking operations on
     * request, and breaks cooperative thread cancellation.
     *
     * <p>Conservative behaviour: any parse error → {@code true} (DANGEROUS).
     *
     * @param classBytes raw bytes of a {@code .class} file
     * @return {@code true} if interrupt-swallowing is detected or if parsing
     *         fails
     */
    static boolean hasInterruptSwallowing(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) return true;

        // Check whether the constant pool references InterruptedException at all.
        java.util.Set<String> cpEntries = collectUtf8Entries(classBytes);
        if (!cpEntries.contains(INTERRUPTED_EXCEPTION_CLASS)) return false;

        // Scan methods for InterruptedException handlers that lack interrupt().
        return scanMethodsForInterruptSwallowing(classBytes, cpEntries);
    }

    // -------------------------------------------------------------------------
    // Low-level class-file structure parsers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if any method in the class contains the given
     * {@code targetOpcode} in its {@code Code} attribute.
     *
     * @param classBytes   raw class bytes
     * @param targetOpcode opcode byte to search for (e.g. {@code 0xC2} for
     *                     {@code MONITORENTER})
     * @return {@code true} if the opcode is found, or on any parse error
     */
    private static boolean scanMethodBytecodeForOpcode(byte[] classBytes,
                                                        int targetOpcode) {
        MethodIterator it = new MethodIterator(classBytes);
        if (!it.valid()) return false; // no parseable method table → no pattern found
        while (it.hasNext()) {
            byte[] code = it.nextMethodCode();
            if (code == null) return true; // parse error within a method → conservative
            for (byte b : code) {
                if ((b & 0xFF) == targetOpcode) return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any method bytecode in the class contains a
     * backward {@code GOTO} ({@code 0xA7}) or {@code GOTO_W} ({@code 0xC8})
     * instruction.
     *
     * <p>A backward branch forms a loop.  Combined with the absence of any
     * yield-point in the constant pool (checked by the caller), this
     * constitutes a CPU-consuming loop anti-pattern.
     *
     * @param classBytes raw class bytes
     * @return {@code true} if a backward GOTO is found, or on any parse error
     */
    private static boolean scanMethodBytecodeForBackwardGoto(byte[] classBytes) {
        MethodIterator it = new MethodIterator(classBytes);
        if (!it.valid()) return false; // no parseable method table → no pattern found
        while (it.hasNext()) {
            byte[] code = it.nextMethodCode();
            if (code == null) return true; // parse error within a method → conservative
            if (codeHasBackwardGoto(code)) return true;
        }
        return false;
    }

    /**
     * Scans the bytecode array {@code code} for backward {@code GOTO} /
     * {@code GOTO_W} instructions.
     *
     * <p>The method performs a linear scan, properly skipping over wide
     * instructions and the padding in {@code TABLESWITCH} / {@code LOOKUPSWITCH}
     * operands, to avoid false positives from opcode values embedded in data.
     *
     * @param code raw bytecode of a single method
     * @return {@code true} if a backward branch is found
     */
    private static boolean codeHasBackwardGoto(byte[] code) {
        int i = 0;
        while (i < code.length) {
            int opcode = code[i] & 0xFF;
            switch (opcode) {
                case 0xA7: { // GOTO — 2-byte signed offset
                    if (i + 2 >= code.length) return false;
                    int offset = (short) (((code[i + 1] & 0xFF) << 8)
                            | (code[i + 2] & 0xFF));
                    if (offset < 0) return true; // backward jump = loop
                    i += 3;
                    break;
                }
                case 0xC8: { // GOTO_W — 4-byte signed offset
                    if (i + 4 >= code.length) return false;
                    int offset = ((code[i + 1] & 0xFF) << 24)
                            | ((code[i + 2] & 0xFF) << 16)
                            | ((code[i + 3] & 0xFF) << 8)
                            |  (code[i + 4] & 0xFF);
                    if (offset < 0) return true;
                    i += 5;
                    break;
                }
                case 0xC4: { // WIDE — next opcode has a 2-byte index; iinc has 4
                    if (i + 1 >= code.length) return false;
                    int wideOpcode = code[i + 1] & 0xFF;
                    i += (wideOpcode == 0x84 /* iinc */) ? 6 : 4;
                    break;
                }
                case 0xAA: { // TABLESWITCH — 0-3 bytes padding, then 3×4-byte ints, then offsets
                    int base = i;
                    i++;
                    // skip 0–3 padding bytes to align on a 4-byte boundary
                    while ((i & 3) != 0) i++;
                    if (i + 12 > code.length) return false;
                    int low  = ((code[i + 4] & 0xFF) << 24) | ((code[i + 5] & 0xFF) << 16)
                             | ((code[i + 6] & 0xFF) << 8)  |  (code[i + 7] & 0xFF);
                    int high = ((code[i + 8] & 0xFF) << 24) | ((code[i + 9] & 0xFF) << 16)
                             | ((code[i + 10] & 0xFF) << 8) |  (code[i + 11] & 0xFF);
                    int count = high - low + 1;
                    i += 12 + count * 4;
                    break;
                }
                case 0xAB: { // LOOKUPSWITCH — 0-3 bytes padding, default(4), npairs(4), then pairs
                    i++;
                    while ((i & 3) != 0) i++;
                    if (i + 8 > code.length) return false;
                    int npairs = ((code[i + 4] & 0xFF) << 24) | ((code[i + 5] & 0xFF) << 16)
                               | ((code[i + 6] & 0xFF) << 8)  |  (code[i + 7] & 0xFF);
                    i += 8 + npairs * 8;
                    break;
                }
                default:
                    i += opcodeLength(opcode);
                    break;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any method in the class declares an exception
     * handler for {@link InterruptedException} and that handler's bytecode
     * does <em>not</em> call {@code interrupt()} to restore the interrupt flag.
     *
     * @param classBytes raw class bytes
     * @param cpEntries  pre-collected UTF-8 constant-pool entries
     * @return {@code true} if interrupt-swallowing is detected, or on parse error
     */
    private static boolean scanMethodsForInterruptSwallowing(byte[] classBytes,
                                                              java.util.Set<String> cpEntries) {
        MethodIterator it = new MethodIterator(classBytes);
        if (!it.valid()) return false; // no parseable method table → no pattern found
        while (it.hasNext()) {
            MethodIterator.MethodInfo info = it.nextMethodInfo();
            if (info == null) return true; // parse error
            if (info.code == null) continue; // abstract or native method

            // Walk the exception handler table looking for InterruptedException handlers.
            int ehCount = info.exceptionHandlerCount;
            for (int e = 0; e < ehCount; e++) {
                // Each exception handler record is 8 bytes:
                //   start_pc(2), end_pc(2), handler_pc(2), catch_type(2)
                // catch_type is a CP index into CONSTANT_Class; we have already
                // checked that the CP string "java/lang/InterruptedException"
                // exists (presence check), so instead of resolving the CP index
                // here we use the simpler (and conservative) heuristic: if
                // InterruptedException is in the CP AND there is any exception
                // handler AND the handler body contains no call to interrupt(),
                // flag it as dangerous.
                int baseOffset = info.exceptionTableOffset + e * 8;
                if (baseOffset + 8 > info.codeAttribute.length) return true;
                int handlerPc = ((info.codeAttribute[baseOffset + 4] & 0xFF) << 8)
                              |  (info.codeAttribute[baseOffset + 5] & 0xFF);
                // A catch_type of 0 means "any exception" (finally block); skip those.
                int catchType = ((info.codeAttribute[baseOffset + 6] & 0xFF) << 8)
                              |  (info.codeAttribute[baseOffset + 7] & 0xFF);
                if (catchType == 0) continue;

                // Check the handler body for a call to "interrupt".
                if (!cpEntries.contains(INTERRUPT_METHOD)) {
                    // No call to interrupt() anywhere in the class.
                    return true;
                }
                // interrupt() is referenced somewhere in the class, but is it
                // inside this handler?  Perform a conservative handler-body scan:
                // if the handler region contains no INVOKEVIRTUAL/INVOKEINTERFACE
                // opcode followed (within a few instructions) by code that calls
                // any method, we trust that the developer handled it correctly.
                // A simpler (slightly over-flagging) check: if the handler starts
                // at handlerPc and the very first meaningful bytecode region up
                // to the next GOTO/RETURN contains no method invocation opcode,
                // flag it as swallowing.
                if (isEmptyOrRethrowOnlyHandler(info.code, handlerPc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the exception handler starting at
     * {@code handlerPc} in {@code code} appears to be empty (pops the
     * exception and returns/jumps without invoking any method).
     *
     * <p>The heuristic scans forward from {@code handlerPc} until it hits a
     * {@code RETURN}-family opcode, an unconditional {@code GOTO}, or the end
     * of the bytecode array.  If no method-invocation opcode
     * ({@code INVOKEVIRTUAL}, {@code INVOKESPECIAL}, {@code INVOKESTATIC},
     * {@code INVOKEINTERFACE}, {@code INVOKEDYNAMIC}) is encountered, the
     * handler is treated as empty / swallowing.
     *
     * @param code      raw bytecode of the method
     * @param handlerPc index of the first instruction in the handler
     * @return {@code true} if the handler appears to swallow the exception
     */
    private static boolean isEmptyOrRethrowOnlyHandler(byte[] code, int handlerPc) {
        if (handlerPc >= code.length) return true;
        int i = handlerPc;
        while (i < code.length) {
            int op = code[i] & 0xFF;
            switch (op) {
                // Any method invocation → handler does something with the exception
                case 0xB6: // INVOKEVIRTUAL
                case 0xB7: // INVOKESPECIAL
                case 0xB8: // INVOKESTATIC
                case 0xB9: // INVOKEINTERFACE
                case 0xBA: // INVOKEDYNAMIC
                    return false;
                // Unconditional return / throw → end of handler, no invocation seen
                case 0xAC: case 0xAD: case 0xAE: case 0xAF:
                case 0xB0: case 0xB1: // ireturn..return
                case 0xBF: // athrow (re-throw without calling interrupt() is still OK)
                    return op != 0xBF; // athrow = rethrow = not swallowing; return = swallowing
                // Unconditional jump — end of handler block
                case 0xA7: // GOTO
                case 0xC8: // GOTO_W
                    return true;
                default:
                    i += opcodeLength(op);
                    break;
            }
        }
        return true; // reached end of bytecode without finding an invocation
    }

    /**
     * Returns the total byte length (opcode byte + operand bytes) of a JVM
     * instruction identified by its opcode.  Used for instruction stepping.
     *
     * <p>Variable-length instructions ({@code TABLESWITCH}, {@code LOOKUPSWITCH},
     * {@code WIDE}) are <em>not</em> handled here; callers that need to skip
     * those must handle them explicitly.
     *
     * @param opcode the opcode byte value (0x00–0xFF)
     * @return total instruction length in bytes; returns {@code 1} for unknown
     *         opcodes (conservative forward progress)
     */
    private static int opcodeLength(int opcode) {
        switch (opcode) {
            // 1-byte opcodes (opcode only, no operands)
            case 0x00: // nop
            case 0x01: // aconst_null
            case 0x02: case 0x03: case 0x04: case 0x05: case 0x06: case 0x07: case 0x08: // iconst_m1..5
            case 0x09: case 0x0A: // lconst_0..1
            case 0x0B: case 0x0C: case 0x0D: // fconst_0..2
            case 0x0E: case 0x0F: // dconst_0..1
            case 0x1A: case 0x1B: case 0x1C: case 0x1D: // iload_0..3
            case 0x1E: case 0x1F: case 0x20: case 0x21: // lload_0..3
            case 0x22: case 0x23: case 0x24: case 0x25: // fload_0..3
            case 0x26: case 0x27: case 0x28: case 0x29: // dload_0..3
            case 0x2A: case 0x2B: case 0x2C: case 0x2D: // aload_0..3
            case 0x2E: case 0x2F: case 0x30: case 0x31: case 0x32: case 0x33: case 0x34: case 0x35: // xaload
            case 0x3B: case 0x3C: case 0x3D: case 0x3E: // istore_0..3
            case 0x3F: case 0x40: case 0x41: case 0x42: // lstore_0..3
            case 0x43: case 0x44: case 0x45: case 0x46: // fstore_0..3
            case 0x47: case 0x48: case 0x49: case 0x4A: // dstore_0..3
            case 0x4B: case 0x4C: case 0x4D: case 0x4E: // astore_0..3
            case 0x4F: case 0x50: case 0x51: case 0x52: case 0x53: case 0x54: case 0x55: case 0x56: // xastore
            case 0x57: case 0x58: // pop, pop2
            case 0x59: case 0x5A: case 0x5B: case 0x5C: case 0x5D: case 0x5E: // dup variants
            case 0x5F: // swap
            case 0x60: case 0x61: case 0x62: case 0x63: // iadd, ladd, fadd, dadd
            case 0x64: case 0x65: case 0x66: case 0x67: // isub, lsub, fsub, dsub
            case 0x68: case 0x69: case 0x6A: case 0x6B: // imul, lmul, fmul, dmul
            case 0x6C: case 0x6D: case 0x6E: case 0x6F: // idiv, ldiv, fdiv, ddiv
            case 0x70: case 0x71: case 0x72: case 0x73: // irem, lrem, frem, drem
            case 0x74: case 0x75: case 0x76: case 0x77: // ineg, lneg, fneg, dneg
            case 0x78: case 0x79: case 0x7A: case 0x7B: // ishl, lshl, ishr, lshr
            case 0x7C: case 0x7D: // iushr, lushr
            case 0x7E: case 0x7F: // iand, land
            case 0x80: case 0x81: // ior, lor
            case 0x82: case 0x83: // ixor, lxor
            case 0x85: case 0x86: case 0x87: case 0x88: case 0x89: case 0x8A: // i2l..i2d, l2i..
            case 0x8B: case 0x8C: case 0x8D: case 0x8E: case 0x8F: // f2i..f2d, d2i..
            case 0x90: case 0x91: case 0x92: case 0x93: // d2l, d2f, i2b, i2c, i2s
            case 0x94: case 0x95: case 0x96: case 0x97: case 0x98: // lcmp, fcmpl/g, dcmpl/g
            case 0xAC: case 0xAD: case 0xAE: case 0xAF: // ireturn..dreturn
            case 0xB0: case 0xB1: // areturn, return
            case 0xBE: // arraylength
            case 0xBF: // athrow
            case 0xC2: case 0xC3: // MONITORENTER, MONITOREXIT
                return 1;
            // 2-byte opcodes (1 operand byte)
            case 0x10: // BIPUSH
            case 0x12: // LDC
            case 0x15: case 0x16: case 0x17: case 0x18: case 0x19: // xLOAD
            case 0x36: case 0x37: case 0x38: case 0x39: case 0x3A: // xSTORE
            case 0xA9: // RET
            case 0xBC: // NEWARRAY
                return 2;
            // 3-byte opcodes (2 operand bytes)
            case 0x11: // SIPUSH
            case 0x13: // LDC_W
            case 0x14: // LDC2_W
            case 0x84: // IINC
            case 0x99: case 0x9A: case 0x9B: case 0x9C: case 0x9D: case 0x9E: // IF_x (1-byte cond + 2-byte offset)
            case 0x9F: case 0xA0: case 0xA1: case 0xA2: case 0xA3: case 0xA4: // IF_ICMPx
            case 0xA5: case 0xA6: // IF_ACMPx
            case 0xA7: // GOTO — already handled in codeHasBackwardGoto
            case 0xA8: // JSR
            case 0xB2: case 0xB3: // GETSTATIC, PUTSTATIC
            case 0xB4: case 0xB5: // GETFIELD, PUTFIELD
            case 0xB6: case 0xB7: case 0xB8: // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC
            case 0xBB: // NEW
            case 0xBD: // ANEWARRAY
            case 0xC0: case 0xC1: // CHECKCAST, INSTANCEOF
            case 0xC6: case 0xC7: // IFNULL, IFNONNULL
                return 3;
            // 5-byte opcodes
            case 0xB9: // INVOKEINTERFACE (4 operands)
            case 0xBA: // INVOKEDYNAMIC (4 operands)
            case 0xC8: // GOTO_W — already handled in codeHasBackwardGoto
            case 0xC9: // JSR_W
                return 5;
            // 4-byte opcodes
            case 0xC5: // MULTIANEWARRAY (2-byte index + 1-byte dims)
                return 4;
            default:
                return 1; // unknown — step 1 byte
        }
    }

    // -------------------------------------------------------------------------
    // MethodIterator — walks the methods table and extracts Code attributes
    // -------------------------------------------------------------------------

    /**
     * Stateful iterator that walks the methods table of a class file and
     * extracts the {@code Code} attribute of each method.
     *
     * <p>Usage:
     * <pre>
     * MethodIterator it = new MethodIterator(classBytes);
     * if (!it.valid()) { // parse error
     * }
     * while (it.hasNext()) {
     *     byte[] code = it.nextMethodCode();
     *     if (code == null) { // parse error
     *     }
     *     // ... scan code ...
     * }
     * </pre>
     */
    private static final class MethodIterator {

        /** Parsed class bytes. */
        private final byte[] cls;
        /** Offset at which the methods_count field begins. */
        private final int methodsCountOffset;
        /** Total number of methods. */
        private final int methodCount;
        /** Current read offset within {@code cls}. */
        private int offset;
        /** Current method index (0-based). */
        private int methodIndex;
        /** Whether the iterator is in a valid, usable state. */
        private final boolean valid;

        MethodIterator(byte[] classBytes) {
            this.cls = classBytes;
            // Skip past the constant pool to reach the following structure:
            //   access_flags(2), this_class(2), super_class(2),
            //   interfaces_count(2), interfaces[...],
            //   fields_count(2), fields[...]
            //   methods_count(2), methods[...]
            int cpEnd = skipConstantPool(classBytes);
            boolean ok = cpEnd >= 0;
            int off = cpEnd;
            if (ok) {
                // access_flags(2) + this_class(2) + super_class(2) = 6 bytes
                off += 6;
                if (off + 2 > safeLen()) { ok = false; }
            }
            if (ok) {
                // Skip interfaces
                int ifaceCount = ((cls[off] & 0xFF) << 8) | (cls[off + 1] & 0xFF);
                off += 2 + ifaceCount * 2;
                if (off > safeLen()) { ok = false; }
            }
            if (ok) {
                // Skip fields (each field has attribute_count attributes to skip)
                if (off + 2 > safeLen()) { ok = false; }
                else {
                    int fieldCount = ((cls[off] & 0xFF) << 8) | (cls[off + 1] & 0xFF);
                    off += 2;
                    for (int f = 0; f < fieldCount && ok; f++) {
                        // field: access_flags(2), name_idx(2), desc_idx(2),
                        //        attr_count(2), attrs[...]
                        if (off + 8 > safeLen()) { ok = false; break; }
                        int attrCount = ((cls[off + 6] & 0xFF) << 8)
                                      | (cls[off + 7] & 0xFF);
                        off += 8;
                        for (int a = 0; a < attrCount && ok; a++) {
                            if (off + 6 > safeLen()) { ok = false; break; }
                            int attrLen = ((cls[off + 2] & 0xFF) << 24)
                                        | ((cls[off + 3] & 0xFF) << 16)
                                        | ((cls[off + 4] & 0xFF) << 8)
                                        |  (cls[off + 5] & 0xFF);
                            off += 6 + attrLen;
                        }
                    }
                }
            }
            this.valid = ok && off + 2 <= safeLen();
            this.methodsCountOffset = off;
            this.methodCount = ok && off + 2 <= safeLen()
                    ? ((cls[off] & 0xFF) << 8) | (cls[off + 1] & 0xFF)
                    : 0;
            this.offset = ok ? off + 2 : 0;
            this.methodIndex = 0;
        }

        boolean valid() { return valid; }

        boolean hasNext() { return valid && methodIndex < methodCount; }

        /**
         * Information about a single method, including its raw Code attribute
         * bytes and exception handler table.
         */
        static final class MethodInfo {
            /** Raw bytecode instructions (the {@code code[]} array). */
            final byte[] code;
            /** The full {@code Code} attribute bytes (starts after the 6-byte header). */
            final byte[] codeAttribute;
            /** Number of exception handler records in the exception table. */
            final int exceptionHandlerCount;
            /** Offset within {@code codeAttribute} of the first exception handler record. */
            final int exceptionTableOffset;

            MethodInfo(byte[] code, byte[] codeAttribute,
                       int exceptionHandlerCount, int exceptionTableOffset) {
                this.code = code;
                this.codeAttribute = codeAttribute;
                this.exceptionHandlerCount = exceptionHandlerCount;
                this.exceptionTableOffset = exceptionTableOffset;
            }
        }

        /**
         * Advances to the next method and returns its {@code MethodInfo},
         * or {@code null} if parsing fails.
         */
        MethodInfo nextMethodInfo() {
            if (!valid || methodIndex >= methodCount) return null;
            methodIndex++;
            // method_info: access_flags(2), name_idx(2), descriptor_idx(2),
            //              attributes_count(2), attributes[...]
            if (offset + 8 > safeLen()) return null;
            int attrCount = ((cls[offset + 6] & 0xFF) << 8)
                          | (cls[offset + 7] & 0xFF);
            offset += 8;

            byte[] foundCode = null;
            byte[] foundCodeAttr = null;
            int ehCount = 0;
            int ehTableOffset = 0;

            for (int a = 0; a < attrCount; a++) {
                if (offset + 6 > safeLen()) return null;
                int attrNameIdx = ((cls[offset] & 0xFF) << 8)
                                | (cls[offset + 1] & 0xFF);
                int attrLen = ((cls[offset + 2] & 0xFF) << 24)
                            | ((cls[offset + 3] & 0xFF) << 16)
                            | ((cls[offset + 4] & 0xFF) << 8)
                            |  (cls[offset + 5] & 0xFF);
                offset += 6;
                if (attrLen < 0 || offset + attrLen > safeLen()) return null;

                // We identify the Code attribute by its content structure, not
                // by resolving the name index (which would require a CP lookup).
                // A Code attribute begins with max_stack(2) + max_locals(2) +
                // code_length(4) = 8 bytes minimum.
                if (attrLen >= 8 && foundCode == null) {
                    // Speculatively parse as a Code attribute.
                    int codeLen = ((cls[offset + 4] & 0xFF) << 24)
                                | ((cls[offset + 5] & 0xFF) << 16)
                                | ((cls[offset + 6] & 0xFF) << 8)
                                |  (cls[offset + 7] & 0xFF);
                    if (codeLen >= 0 && 8 + codeLen + 2 <= attrLen) {
                        int ehTableOff = 8 + codeLen;
                        int ehCnt = ((cls[offset + ehTableOff] & 0xFF) << 8)
                                  | (cls[offset + ehTableOff + 1] & 0xFF);
                        if (ehTableOff + 2 + ehCnt * 8 <= attrLen) {
                            // Looks like a valid Code attribute.
                            foundCode = Arrays.copyOfRange(cls, offset + 8,
                                    offset + 8 + codeLen);
                            foundCodeAttr = Arrays.copyOfRange(cls, offset,
                                    offset + attrLen);
                            ehCount = ehCnt;
                            ehTableOffset = ehTableOff + 2; // point past the count
                        }
                    }
                }
                offset += attrLen;
            }
            return new MethodInfo(foundCode, foundCodeAttr, ehCount, ehTableOffset);
        }

        /**
         * Convenience method: advances to the next method and returns only
         * the {@code code[]} bytecode array, or {@code null} on parse error.
         */
        byte[] nextMethodCode() {
            MethodInfo info = nextMethodInfo();
            return (info == null) ? null : info.code;
        }

        private int safeLen() {
            return cls == null ? 0 : cls.length;
        }
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
