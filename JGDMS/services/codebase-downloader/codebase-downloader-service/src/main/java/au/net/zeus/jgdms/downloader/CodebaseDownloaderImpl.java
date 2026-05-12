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
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceItem;
import net.jini.export.CodebaseAccessor;
import org.apache.river.api.net.Uri;

/**
 * Core (non-Jini) implementation of the Codebase Downloader (Host 4).
 *
 * <p>This class encapsulates the complete download-analyse-submit pipeline
 * without any Jini infrastructure dependency.  It is designed to be tested
 * independently of the Jini activation and export machinery.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>A codebase URI is accepted via {@link #submitForAnalysis(Set)} or
 *       {@link #onServiceItem(ServiceItem)}.</li>
 *   <li>If the URI has not been recently processed it is queued in the
 *       worker-thread pool.</li>
 *   <li>A worker thread downloads the JAR bytes over HTTP(S).</li>
 *   <li>A SHA-256 content hash is computed from the raw bytes.</li>
 *   <li>If the hash has already been submitted to the BAE pool the task
 *       is silently dropped (per-session deduplication).</li>
 *   <li>An {@link AnalysisRequest} is constructed and sent in turn to
 *       every {@link BytecodeAnalysisEngine} in the configured pool.</li>
 *   <li>Each {@link JarAnalysisReport} returned by a BAE is forwarded to
 *       the {@link VerdictRegistry} via
 *       {@link VerdictRegistry#submitReport}.</li>
 * </ol>
 *
 * <h2>Deduplication</h2>
 * <ul>
 *   <li><strong>By content hash:</strong> once a JAR with a given SHA-256
 *       hash has been submitted to the BAE pool, it is not re-submitted
 *       within the same JVM session.</li>
 *   <li><strong>By URI:</strong> the same URI is not re-processed until
 *       {@link #URL_RECHECK_INTERVAL_MS} has elapsed, to detect JAR
 *       updates at a URL.  {@code httpmd} URIs are exempt from this rule:
 *       because the content hash is embedded in the URI itself the content
 *       is immutable, so a time-based recheck is unnecessary — once an
 *       {@code httpmd} URI has been processed it is never re-queued.</li>
 * </ul>
 *
 * <h2>Security</h2>
 * JAR bytes are <em>never</em> loaded or executed on Host 4; they are
 * passed as a raw {@code byte[]} inside an {@link AnalysisRequest} to the
 * BAE host (Host 2), which is the only host that parses bytecode.
 *
 * @see ActivatableCodebaseDownloaderImpl
 * @see BytecodeAnalysisEngine
 * @see VerdictRegistry
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class CodebaseDownloaderImpl {

    private static final Logger logger =
            Logger.getLogger(CodebaseDownloaderImpl.class.getName());

    /** Default maximum JAR size accepted for download (32 MiB). */
    public static final int DEFAULT_MAX_JAR_SIZE_BYTES = 32 * 1024 * 1024;

    /** Default HTTP connection timeout in milliseconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    /** Default HTTP read timeout in milliseconds. */
    public static final int DEFAULT_READ_TIMEOUT_MS    = 30_000;

    /** Default number of parallel download worker threads. */
    public static final int DEFAULT_WORKER_THREADS     = 4;

    /**
     * Minimum interval before the same URI is re-fetched (1 hour).
     * A re-fetch detects updated JARs deployed at an existing URL.
     */
    static final long URL_RECHECK_INTERVAL_MS = 60L * 60 * 1000;

    /**
     * Maximum number of distinct URIs tracked in {@link #lastProcessed}.
     * Once the map reaches this size, newly seen URIs are discarded until
     * existing entries age out, preventing OOM from a flood of unique URIs.
     */
    static final int MAX_TRACKED_URIS = 100_000;

    /**
     * Maximum number of download tasks that may wait in the worker-pool
     * queue.  Once the queue is full, newly enqueued URIs are discarded
     * (and their {@link #lastProcessed} entries cleared so they will be
     * retried on the next discovery event), preventing unbounded heap growth.
     */
    static final int MAX_PENDING_DOWNLOADS = 1_000;

    // -------------------------------------------------------------------------
    // Inner type
    // -------------------------------------------------------------------------

    /**
     * Associates a stable engine identifier with its remote
     * {@link BytecodeAnalysisEngine} proxy.
     *
     * <p>The {@code engineId} is the same string used to register the engine
     * with the {@link VerdictRegistry} via
     * {@link VerdictRegistry#registerAnalysisEngine}.  It is required when
     * submitting a {@link JarAnalysisReport} so the registry can locate the
     * engine's public key for signature verification.
     */
    public static final class EngineEntry {

        /** The registry-unique identifier of this engine. */
        public final String engineId;

        /** The remote proxy used to submit analysis requests. */
        public final BytecodeAnalysisEngine engine;

        /**
         * Constructs an entry associating {@code engineId} with
         * {@code engine}.
         *
         * @param engineId stable, registry-unique identifier; must be
         *                 non-null and non-empty
         * @param engine   remote engine proxy; must be non-null
         * @throws NullPointerException     if either argument is
         *                                  {@code null}
         * @throws IllegalArgumentException if {@code engineId} is empty
         */
        public EngineEntry(String engineId, BytecodeAnalysisEngine engine) {
            if (engineId == null) throw new NullPointerException("engineId");
            if (engine   == null) throw new NullPointerException("engine");
            if (engineId.isEmpty())
                throw new IllegalArgumentException("engineId must not be empty");
            this.engineId = engineId;
            this.engine   = engine;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Immutable snapshot of the BAE pool supplied at construction. */
    private final List<EngineEntry> baePool;

    /** The registry to which signed reports are forwarded. */
    private final VerdictRegistry verdictRegistry;

    /**
     * Content hashes (SHA-256 hex) of JARs already submitted to the BAE
     * pool during this JVM session.  Backed by a {@link ConcurrentHashMap}
     * for lock-free concurrent access.
     */
    private final Set<String> submittedHashes;

    /**
     * Maps each URI to the wall-clock time (ms) at which processing last
     * began.  Used to enforce {@link #URL_RECHECK_INTERVAL_MS} and to
     * prevent double-submission when multiple threads or discovery events
     * concurrently reference the same URI.
     */
    private final ConcurrentHashMap<Uri, Long> lastProcessed;

    /** Thread pool that runs download-analyse-submit tasks. */
    private final ExecutorService workerPool;
    /** Back-pressure semaphore limits concurrent pending downloads. */
    private final Semaphore downloadSemaphore;

    /** Upper bound on downloaded JAR size. */
    private final int maxJarSizeBytes;

    /** HTTP connection-establishment timeout. */
    private final int connectTimeoutMs;

    /** HTTP socket read timeout. */
    private final int readTimeoutMs;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code CodebaseDownloaderImpl} with all tuning parameters
     * specified explicitly.
     *
     * @param baePool         pool of (engineId, BAE proxy) pairs; must be
     *                        non-null and non-empty
     * @param verdictRegistry registry to submit reports to; must be
     *                        non-null
     * @param maxJarSizeBytes upper bound on downloaded JAR size in bytes;
     *                        must be positive
     * @param connectTimeoutMs HTTP connection timeout in milliseconds;
     *                        must be non-negative
     * @param readTimeoutMs   HTTP read timeout in milliseconds; must be
     *                        non-negative
     * @param workerThreads   number of parallel download worker threads;
     *                        must be at least 1
     * @throws NullPointerException     if any required argument is
     *                                  {@code null}
     * @throws IllegalArgumentException if any numeric argument is out of
     *                                  range or if {@code baePool} is empty
     */
    public CodebaseDownloaderImpl(List<EngineEntry> baePool,
                                   VerdictRegistry verdictRegistry,
                                   int maxJarSizeBytes,
                                   int connectTimeoutMs,
                                   int readTimeoutMs,
                                   int workerThreads) {
        if (baePool == null)         throw new NullPointerException("baePool");
        if (verdictRegistry == null) throw new NullPointerException("verdictRegistry");
        if (baePool.isEmpty())
            throw new IllegalArgumentException("baePool must not be empty");
        if (maxJarSizeBytes <= 0)
            throw new IllegalArgumentException("maxJarSizeBytes must be positive");
        if (connectTimeoutMs < 0)
            throw new IllegalArgumentException("connectTimeoutMs must be non-negative");
        if (readTimeoutMs < 0)
            throw new IllegalArgumentException("readTimeoutMs must be non-negative");
        if (workerThreads < 1)
            throw new IllegalArgumentException("workerThreads must be at least 1");

        this.baePool          = Collections.unmodifiableList(new ArrayList<>(baePool));
        this.verdictRegistry  = verdictRegistry;
        this.submittedHashes  = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.lastProcessed    = new ConcurrentHashMap<>();
        this.maxJarSizeBytes  = maxJarSizeBytes;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs    = readTimeoutMs;
        this.workerPool       = Executors.newVirtualThreadPerTaskExecutor();
        this.downloadSemaphore = new Semaphore(MAX_PENDING_DOWNLOADS);
    }

    /**
     * Constructs a {@code CodebaseDownloaderImpl} with default tuning
     * parameters.
     *
     * @param baePool         pool of (engineId, BAE proxy) pairs; must be
     *                        non-null and non-empty
     * @param verdictRegistry registry to submit reports to; must be
     *                        non-null
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code baePool} is empty
     */
    public CodebaseDownloaderImpl(List<EngineEntry> baePool,
                                   VerdictRegistry verdictRegistry) {
        this(baePool, verdictRegistry,
             DEFAULT_MAX_JAR_SIZE_BYTES,
             DEFAULT_CONNECT_TIMEOUT_MS,
             DEFAULT_READ_TIMEOUT_MS,
             DEFAULT_WORKER_THREADS);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Queues each URI in {@code codebaseUrls} for asynchronous
     * download/analysis.
     *
     * <p>URIs that were processed within the last
     * {@link #URL_RECHECK_INTERVAL_MS} milliseconds are silently ignored.
     * {@code httpmd} URIs are exempt from the time-based recheck: because
     * their content hash is part of the URI the content is immutable, so
     * once processed they are never re-queued.
     * {@code null} elements in the set are also ignored.
     *
     * @param codebaseUrls set of RFC3986-normalised codebase URIs; must be
     *                     non-null
     * @throws NullPointerException if {@code codebaseUrls} is {@code null}
     */
    public void submitForAnalysis(Set<Uri> codebaseUrls) {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        for (Uri uri : codebaseUrls) {
            if (uri != null) {
                enqueue(uri);
            }
        }
    }

    /**
     * Inspects a newly discovered Jini {@link ServiceItem} and, if it
     * advertises codebase URLs via {@link CodebaseAccessor}, enqueues them
     * for download/analysis.
     *
     * <p>The codebase annotation is a whitespace-separated list of URIs
     * following the standard Java codebase-annotation convention.  Each
     * URI is individually enqueued.
     *
     * @param item the discovered service item; must be non-null
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public void onServiceItem(ServiceItem item) {
        if (item == null) throw new NullPointerException("item");
        Object svc = item.service;
        if (svc instanceof CodebaseAccessor) {
            try {
                String annotation = ((CodebaseAccessor) svc).getClassAnnotation();
                enqueueAnnotation(annotation);
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "Failed to retrieve codebase annotation from service item: " + item.serviceID,
                        e);
            }
        }
    }

    /**
     * Waits for in-flight tasks to complete (up to {@code timeoutMs}
     * milliseconds) and then shuts down the worker pool.
     *
     * @param timeoutMs maximum time to wait for task completion in
     *                  milliseconds; must be positive
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting
     */
    public void shutdown(long timeoutMs) throws InterruptedException {
        workerPool.shutdown();
        workerPool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // Package-private for testing
    // -------------------------------------------------------------------------

    /**
     * Returns the set of content hashes that have already been submitted
     * to the BAE pool during this session.
     *
     * <p>Exposed for unit testing.
     */
    Set<String> getSubmittedHashes() {
        return Collections.unmodifiableSet(submittedHashes);
    }

    /**
     * Returns the current number of URIs being tracked in the
     * last-processed map.
     *
     * <p>Exposed for unit testing.
     */
    int getTrackedUriCount() {
        return lastProcessed.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Enqueues a single URI for processing if it has not been recently
     * processed.
     *
     * <p>Uses {@link ConcurrentHashMap#compute} to atomically check the
     * last-processed timestamp and, if stale, update it and schedule the
     * download task.  This eliminates the TOCTOU window that would exist
     * between a plain {@code get} and a subsequent {@code put}.
     *
     * <p>{@code httpmd} URIs embed a SHA-256 or SHA-512 content digest in
     * the URI itself, so their content is immutable.  Once such a URI has
     * been processed it is never re-queued; the time-based recheck interval
     * does not apply to them.
     */
    private void enqueue(Uri uri) {
        // Safety valve: prevent unbounded map growth caused by a flood of
        // unique URIs.  URIs that are already tracked are always let through
        // (they would simply be skipped or updated below).
        if (!lastProcessed.containsKey(uri) && lastProcessed.size() >= MAX_TRACKED_URIS) {
            logger.warning("URI tracking map at capacity (" + MAX_TRACKED_URIS
                    + "); discarding URI: " + uri);
            return;
        }

        final long now = System.currentTimeMillis();
        final boolean[] shouldSubmit = {false};
        lastProcessed.compute(uri, (k, prev) -> {
            if (prev != null) {
                // httpmd URIs embed a content hash: content is immutable,
                // so a time-based recheck is unnecessary.
                if ("httpmd".equalsIgnoreCase(k.getScheme())) return prev;
                if ((now - prev) < URL_RECHECK_INTERVAL_MS) {
                    return prev;  // still within recheck interval; skip
                }
            }
            shouldSubmit[0] = true;
            return now;
        });
        if (shouldSubmit[0]) {
            if (downloadSemaphore.tryAcquire()) {
                workerPool.execute(() -> {
                    try {
                        processUri(uri, now);
                    } finally {
                        downloadSemaphore.release();
                    }
                });
            } else {
                clearLastProcessed(uri, now);
                logger.warning("Worker queue is full; will retry URI on next "
                        + "discovery event: " + uri);
            }
        }
    }

    /**
     * Parses a whitespace-separated codebase annotation and enqueues each
     * URI fragment found in it.
     */
    private void enqueueAnnotation(String annotation) {
        if (annotation == null || annotation.isEmpty()) return;
        for (String fragment : annotation.split("\\s+")) {
            if (fragment.isEmpty()) continue;
            try {
                enqueue(new Uri(fragment));
            } catch (URISyntaxException e) {
                logger.log(Level.WARNING,
                        "Ignoring malformed URI in codebase annotation: " + fragment, e);
            }
        }
    }

    /**
     * Downloads, hashes, and submits the JAR at {@code uri}.
     *
     * <p>This method is the body of each worker-pool task.  On any
     * transient failure the {@link #lastProcessed} entry is cleared so
     * that the URI will be re-queued on the next {@link #enqueue} call.
     *
     * @param uri          the codebase URI to process
     * @param submittedAt  the wall-clock time at which this task was
     *                     enqueued; used for stale-entry removal on error
     */
    private void processUri(Uri uri, long submittedAt) {
        try {
            // HTTP, HTTPS, and HTTPMD are supported.  HTTPS/HTTPMD are
            // required in production; HTTP is accepted here to support
            // testing.  HTTPMD connections verify a SHA-256 or SHA-512
            // content digest embedded in the URI.
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)
                    && !"http".equalsIgnoreCase(scheme)
                    && !"httpmd".equalsIgnoreCase(scheme)) {
                logger.warning("Unsupported URI scheme '" + scheme + "', skipping: " + uri);
                clearLastProcessed(uri, submittedAt);
                return;
            }

            byte[] jarBytes = downloadJar(uri);

            String contentHash = computeSha256Hex(jarBytes);

            // Deduplicate by content hash (fast-path exit for already-seen JARs).
            if (!submittedHashes.add(contentHash)) {
                logger.fine("JAR already analysed (hash=" + contentHash + "): " + uri);
                lastProcessed.put(uri, System.currentTimeMillis());
                return;
            }

            logger.info("Processing JAR: uri=" + uri
                    + ", bytes=" + jarBytes.length
                    + ", hash=" + contentHash);

            AnalysisRequest request = new AnalysisRequest(jarBytes, contentHash, uri);

            boolean anySuccess = false;
            for (EngineEntry entry : baePool) {
                try {
                    JarAnalysisReport report = entry.engine.analyzeJar(request);
                    verdictRegistry.submitReport(entry.engineId, report);
                    logger.info("Submitted report from engine " + entry.engineId
                            + " for hash=" + contentHash
                            + ": verdict=" + report.deriveVerdictType());
                    anySuccess = true;
                } catch (AnalysisException e) {
                    logger.log(Level.WARNING,
                            "Analysis failed (engine=" + entry.engineId
                                    + ", uri=" + uri + ")", e);
                } catch (RemoteException e) {
                    logger.log(Level.WARNING,
                            "Remote error with engine " + entry.engineId
                                    + " for uri=" + uri, e);
                }
            }

            if (anySuccess) {
                lastProcessed.put(uri, System.currentTimeMillis());
            } else {
                // All BAEs failed — remove the hash so re-analysis is possible
                submittedHashes.remove(contentHash);
                clearLastProcessed(uri, submittedAt);
                logger.warning("All BAEs failed for uri=" + uri + "; will retry.");
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Download failed for uri=" + uri, e);
            clearLastProcessed(uri, submittedAt);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Unexpected error processing uri=" + uri, e);
            clearLastProcessed(uri, submittedAt);
        }
    }

    /**
     * Removes the {@link #lastProcessed} entry for {@code uri} only if it
     * still holds the value {@code submittedAt} (i.e. no other thread has
     * updated it in the meantime).
     */
    private void clearLastProcessed(Uri uri, long submittedAt) {
        lastProcessed.remove(uri, submittedAt);
    }

    /**
     * Downloads the content at {@code uri} and returns it as a byte array.
     *
     * <p>The download is bounded by {@link #maxJarSizeBytes};
     * {@link #connectTimeoutMs} and {@link #readTimeoutMs} guard against
     * slow or hanging servers.
     *
     * <p>HTTP redirects are <strong>not</strong> followed automatically to
     * prevent Server-Side Request Forgery (SSRF): a malicious server could
     * redirect the downloader to an internal network address.
     *
     * @param uri the URI to fetch; must use HTTP, HTTPS, or HTTPMD
     * @return the raw content bytes; never null, never empty
     * @throws IOException if the download fails, the content is empty, or
     *                     the size limit is exceeded
     */
    byte[] downloadJar(Uri uri) throws IOException {
        URL url;
        try {
            url = new URL(uri.toString());
        } catch (MalformedURLException e) {
            throw new IOException("Cannot convert URI to URL: " + uri, e);
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Accept",
                "application/java-archive, application/octet-stream, */*");
        conn.setRequestProperty("User-Agent", "JGDMS-CodebaseDownloader/3.1.1");
        // Do NOT follow redirects automatically: an attacker-controlled server
        // could redirect to an internal address (SSRF).  If a redirect is
        // needed the caller must supply the final URL directly.
        conn.setInstanceFollowRedirects(false);

        int responseCode;
        try {
            responseCode = conn.getResponseCode();
        } catch (IOException e) {
            conn.disconnect();
            throw e;
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("HTTP " + responseCode + " for " + uri);
        }
        // Reject oversized content before reading if Content-Length is present.
        int contentLength = conn.getContentLength();
        if (contentLength > maxJarSizeBytes) {
            conn.disconnect();
            throw new IOException("Content-Length " + contentLength
                    + " exceeds limit " + maxJarSizeBytes + " for " + uri);
        }

        int initCapacity = (contentLength > 0) ? contentLength : 65_536;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(initCapacity);
        byte[] buf = new byte[65_536];
        // Use long to prevent integer overflow when maxJarSizeBytes is large.
        long totalRead = 0;

        try (InputStream in = conn.getInputStream()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                totalRead += n;
                if (totalRead > maxJarSizeBytes) {
                    throw new IOException("JAR size exceeds limit "
                            + maxJarSizeBytes + " for " + uri);
                }
                baos.write(buf, 0, n);
            }
        } finally {
            conn.disconnect();
        }

        if (baos.size() == 0) {
            throw new IOException("Empty response body for " + uri);
        }

        return baos.toByteArray();
    }

    /**
     * Computes the SHA-256 hex digest of {@code bytes}.
     *
     * @param bytes the input data; must be non-null
     * @return lowercase hex string of the 32-byte SHA-256 digest
     */
    static String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE spec; this can never happen.
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
