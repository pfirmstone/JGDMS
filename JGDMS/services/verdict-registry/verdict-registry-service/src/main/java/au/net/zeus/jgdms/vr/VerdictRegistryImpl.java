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
package au.net.zeus.jgdms.vr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import org.apache.river.api.net.Uri;
import org.apache.river.constants.ThrowableConstants;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.reliableLog.ReliableLog;
import au.net.zeus.jgdms.vr.proxy.VerdictEvent;
import au.net.zeus.jgdms.vr.proxy.VerdictEventLease;

/**
 * Server-side implementation of the {@link VerdictRegistry} service.
 *
 * <p>This is the authoritative, low-risk component in the safe-codebase
 * architecture.  It manages {@code BytecodeAnalysisEngine} registration,
 * applies quorum policy over submitted {@link JarAnalysisReport} objects, and
 * produces authoritative {@link RegistryVerdict} results for clients.
 *
 * <h2>Thread safety</h2>
 * <ul>
 *   <li>The engine registration map ({@link #engines}) is a
 *       {@link ConcurrentHashMap}; reads and updates are lock-free.</li>
 *   <li>Per-codebase vote state ({@link VerdictState}) is synchronised on the
 *       individual {@code VerdictState} instance.</li>
 *   <li>The published-verdict cache ({@link #publishedVerdicts}) is a
 *       {@link ConcurrentHashMap} of immutable {@link RegistryVerdict} objects;
 *       reads are always safe.</li>
 *   <li>The {@link ReliableLog} instance ({@link #log}) is not thread-safe;
 *       all write operations on it are serialised through {@link #logLock}.</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * When constructed with a {@code logDir} path, this implementation uses
 * {@link ReliableLog} to persist all state-changing operations so that the
 * full verdict registry state can be recovered after a restart.  The log
 * stores three types of update records:
 * <ul>
 *   <li>{@link RegisterEngineRecord} / {@link RevokeEngineRecord} — engine
 *       lifecycle events.</li>
 *   <li>{@link HashVoteRecord} — a signature-verified report submission
 *       (hash-keyed push model).</li>
 *   <li>{@link HashPublishedVerdictRecord} — a newly published hash-keyed
 *       {@link RegistryVerdict} (push model, including crash/pinning
 *       condemnations).</li>
 *   <li>{@link PublishedVerdictRecord} — a newly published URL-keyed
 *       DANGEROUS {@link RegistryVerdict} (crash/pinning fail-safe).</li>
 * </ul>
 * On startup, {@link ReliableLog#recover()} replays the snapshot plus all
 * subsequent log entries to reconstruct the full in-memory state.  A new
 * snapshot is written immediately after recovery to consolidate the log.
 *
 * <h2>Canonical byte format</h2>
 * The following layout is used for every signed or verified payload:
 * <ul>
 *   <li>For each codebase URL, in lexicographic (UTF-8) sort order:
 *       4-byte big-endian byte-length, then the UTF-8 bytes.</li>
 *   <li>4-byte big-endian {@link VerdictType#ordinal()} (or exit code for
 *       crash reports).</li>
 *   <li>8-byte big-endian timestamp / incarnation (as appropriate).</li>
 *   <li>For crash reports only: 4-byte big-endian length of the
 *       {@code stderrSummary} UTF-8 bytes, then those bytes.</li>
 * </ul>
 *
 * @see VerdictRegistry
 * @since 3.1.1
 */
public class VerdictRegistryImpl implements VerdictRegistry {

    private static final Logger logger =
            Logger.getLogger(VerdictRegistryImpl.class.getName());

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /** Minimum number of independent SAFE votes required before issuing SAFE. */
    private final int quorumMinimum;

    /** The registry's private signing key, used to sign RegistryVerdict objects. */
    private final PrivateKey registryPrivateKey;

    /** JCA algorithm name for registry signatures (e.g. {@code "SHA256withRSA"}). */
    private final String registrySigAlgorithm;

    /** Phoenix activation system's public key, used to verify CrashReport signatures. */
    private final PublicKey phoenixPublicKey;

    /** JCA algorithm name used by Phoenix to sign CrashReports. */
    private final String phoenixSigAlgorithm;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Registered analysis engines: engineId → EngineRegistration. */
    private final ConcurrentHashMap<String, EngineRegistration> engines =
            new ConcurrentHashMap<String, EngineRegistration>();

    /**
     * Per-codebase vote accumulator: canonical codebase key → VerdictState.
     * Access to individual values is synchronised on the VerdictState instance.
     */
    private final ConcurrentHashMap<String, VerdictState> verdictStates =
            new ConcurrentHashMap<String, VerdictState>();

    /**
     * Published, authoritative verdicts: canonical codebase key → RegistryVerdict.
     * Written only under the corresponding VerdictState lock; read lock-free
     * because RegistryVerdict is immutable.
     */
    private final ConcurrentHashMap<String, RegistryVerdict> publishedVerdicts =
            new ConcurrentHashMap<String, RegistryVerdict>();

    /**
     * Active event-listener registrations: leaseId → ListenerRegistration.
     * Entries are added by {@link #registerVerdictListener} and removed by
     * {@link #cancelEventLease} or lazily on definite delivery failure.
     */
    private final ConcurrentHashMap<Uuid, ListenerRegistration> listenerRegistrations =
            new ConcurrentHashMap<Uuid, ListenerRegistration>();

    /**
     * Per-content-hash vote accumulator (push model): SHA-256 hex string →
     * HashVerdictState.  Access to individual values is synchronised on the
     * {@code HashVerdictState} instance.
     */
    private final ConcurrentHashMap<String, HashVerdictState> hashVerdictStates =
            new ConcurrentHashMap<String, HashVerdictState>();

    /**
     * Published hash-keyed verdicts (push model): SHA-256 hex string →
     * RegistryVerdict.  Immutable values; reads are lock-free.
     */
    private final ConcurrentHashMap<String, RegistryVerdict> hashPublishedVerdicts =
            new ConcurrentHashMap<String, RegistryVerdict>();

    /**
     * Index from codebase URI string to the set of content hashes whose reports
     * declared that URI.  Populated from {@link JarAnalysisReport#getCodebaseUrls()}
     * on each accepted {@link #submitReport}.  Used to resolve a crash- or
     * pinning-driven URL condemnation to the concrete content hash(es) that
     * must be condemned in the hash-keyed store.
     */
    private final ConcurrentHashMap<String, Set<String>> uriToHashes =
            new ConcurrentHashMap<String, Set<String>>();

    /**
     * URIs that have been condemned (via {@link #reportCrash} /
     * {@link #reportPinning}) before any report mapping them to a content hash
     * arrived.  When a later report declares one of these URIs, its content
     * hash is forced to {@link VerdictType#DANGEROUS} and the applied URIs are
     * removed from this set.
     */
    private final Set<String> pendingUrlCondemnations =
            ConcurrentHashMap.newKeySet();

    /**
     * Source of event IDs.  Each call to {@link #registerVerdictListener}
     * consumes one ID.
     */
    private final AtomicLong nextEventId = new AtomicLong(1L);

    /**
     * Persistent log for recording all state-changing operations so that
     * registry state can be recovered after a restart.  {@code null} when
     * persistence is disabled (transient mode).
     */
    private final ReliableLog log;

    /**
     * Guards all write operations on {@link #log}.  {@link ReliableLog} is
     * not thread-safe; this lock serialises concurrent updates.
     */
    private final Object logLock = new Object();

    /**
     * Thread pool used to deliver {@link VerdictEvent}s asynchronously so
     * that a slow or blocked listener does not stall verdict processing.
     */
    private final ExecutorService executorService;

    /**
     * The exported server stub, used as the event source in
     * {@link VerdictEvent} objects and as the server reference in
     * {@link VerdictEventLease} objects.  Null until
     * {@link #setEventSource(VerdictRegistry)} is called after export.
     */
    private volatile VerdictRegistry eventSource;

    /**
     * Maximum number of threads in the event-delivery thread pool.
     */
    private static final int EVENT_POOL_MAX_THREADS = 10;

    /**
     * Keep-alive time (seconds) for idle threads in the event-delivery pool.
     */
    private static final long EVENT_POOL_KEEP_ALIVE_SECONDS = 60L;

    /**
     * Maximum duration (ms) a listener lease may be renewed to: 1 day.
     */
    private static final long MAX_LISTENER_LEASE_DURATION =
            TimeUnit.DAYS.toMillis(1);

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /** Immutable record of a registered engine's public key and algorithm. */
    private static final class EngineRegistration {
        final PublicKey publicKey;
        final String sigAlgorithm;

        EngineRegistration(PublicKey publicKey, String sigAlgorithm) {
            this.publicKey    = publicKey;
            this.sigAlgorithm = sigAlgorithm;
        }
    }

    /**
     * Mutable per-codebase DANGEROUS-condemnation state.  All fields are
     * accessed only while {@code synchronized (this)}.
     *
     * <p>Only {@link #reportCrash} and {@link #reportPinning} populate this
     * URL-keyed store, publishing permanent DANGEROUS verdicts that
     * {@link #getVerdict(Set)} reads.  There is no URL-keyed SAFE quorum; SAFE
     * verdicts are produced solely on the hash-keyed push path
     * ({@link #submitReport}).
     */
    private static final class VerdictState {
        /**
         * Sorted URL strings captured when the first condemnation for this
         * codebase key arrives.  Used to issue the URL-keyed DANGEROUS verdict.
         */
        Uri[] codebaseUrls;

        /** True once a DANGEROUS RegistryVerdict has been published. Permanent. */
        boolean dangerous;
    }

    /**
     * Mutable per-content-hash vote accumulator for the push model.
     *
     * <p>All fields must be accessed only while {@code synchronized (this)}.
     * Separate from {@link VerdictState} (which is URL-set keyed) to avoid
     * mixing the two accumulation paths.
     */
    private static final class HashVerdictState {
        /** Per-engine votes for this content hash: engineId → VerdictType. */
        final Map<String, VerdictType> votes = new HashMap<String, VerdictType>();

        /** True once a DANGEROUS RegistryVerdict has been published. Permanent. */
        boolean dangerous;
    }

    /**
     * State for one event-listener registration.
     */
    private static final class ListenerRegistration {
        final Uuid             leaseId;
        final long             eventId;
        final AtomicLong       seqNum;
        volatile long          leaseExpiration;
        final RemoteEventListener listener;
        final MarshalledInstance  handback;
        final String           codebaseKey;

        ListenerRegistration(Uuid leaseId,
                             long eventId,
                             long leaseExpiration,
                             RemoteEventListener listener,
                             MarshalledInstance handback,
                             String codebaseKey) {
            this.leaseId         = leaseId;
            this.eventId         = eventId;
            this.seqNum          = new AtomicLong(0L);
            this.leaseExpiration = leaseExpiration;
            this.listener        = listener;
            this.handback        = handback;
            this.codebaseKey     = codebaseKey;
        }
    }

    /**
     * Runnable that delivers a single {@link VerdictEvent} to one listener.
     * Uses {@link ThrowableConstants} to classify failures: definite
     * failures cancel the registration; transient failures are logged and
     * left for the next event.
     */
    private final class SendVerdictTask implements Runnable {

        private final ListenerRegistration reg;
        private final RegistryVerdict      verdict;
        private final long                 seqNum;

        SendVerdictTask(ListenerRegistration reg,
                        RegistryVerdict verdict,
                        long seqNum) {
            this.reg    = reg;
            this.verdict = verdict;
            this.seqNum  = seqNum;
        }

        @Override
        public void run() {
            VerdictEvent event = new VerdictEvent(
                    eventSource, reg.eventId, seqNum, reg.handback, verdict);
            try {
                reg.listener.notify(event);
            } catch (Throwable t) {
                switch (ThrowableConstants.retryable(t)) {
                    case ThrowableConstants.BAD_OBJECT:
                        if (t instanceof Error) throw (Error) t;
                        // fall through - definite failure
                    case ThrowableConstants.BAD_INVOCATION:
                    case ThrowableConstants.UNCATEGORIZED:
                        listenerRegistrations.remove(reg.leaseId);
                        logger.log(Level.INFO,
                                "Cancelled listener lease after definite delivery failure",
                                t);
                        break;
                    default:
                        // ThrowableConstants.INDEFINITE - transient, leave intact
                        logger.log(Level.FINE,
                                "Transient failure delivering VerdictEvent; "
                                + "registration retained", t);
                        break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code VerdictRegistryImpl} <em>without</em> persistence.
     * All state is kept in-memory only and is lost on restart.
     *
     * @param registryPrivateKey   the registry's private key for signing
     *                             {@link RegistryVerdict} objects; must be non-null
     * @param registrySigAlgorithm JCA algorithm name for registry signatures
     *                             (e.g. {@code "SHA256withRSA"}); must be
     *                             non-null and non-empty
     * @param phoenixPublicKey     Phoenix's public key for verifying
     *                             {@link CrashReport} signatures; must be non-null
     * @param phoenixSigAlgorithm  JCA algorithm name used by Phoenix to sign
     *                             {@link CrashReport} objects; must be non-null
     *                             and non-empty
     * @param quorumMinimum        minimum number of independent SAFE verdicts
     *                             required before issuing a SAFE
     *                             {@link RegistryVerdict}; must be &ge; 1
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if any String argument is empty, or
     *                                  {@code quorumMinimum} is &lt; 1
     */
    public VerdictRegistryImpl(PrivateKey registryPrivateKey,
                               String registrySigAlgorithm,
                               PublicKey phoenixPublicKey,
                               String phoenixSigAlgorithm,
                               int quorumMinimum) {
        if (registryPrivateKey   == null) throw new NullPointerException("registryPrivateKey");
        if (registrySigAlgorithm == null) throw new NullPointerException("registrySigAlgorithm");
        if (registrySigAlgorithm.isEmpty()) throw new IllegalArgumentException("registrySigAlgorithm must not be empty");
        if (phoenixPublicKey     == null) throw new NullPointerException("phoenixPublicKey");
        if (phoenixSigAlgorithm  == null) throw new NullPointerException("phoenixSigAlgorithm");
        if (phoenixSigAlgorithm.isEmpty()) throw new IllegalArgumentException("phoenixSigAlgorithm must not be empty");
        if (quorumMinimum < 1) throw new IllegalArgumentException("quorumMinimum must be >= 1");

        this.registryPrivateKey   = registryPrivateKey;
        this.registrySigAlgorithm = registrySigAlgorithm;
        this.phoenixPublicKey     = phoenixPublicKey;
        this.phoenixSigAlgorithm  = phoenixSigAlgorithm;
        this.quorumMinimum        = quorumMinimum;
        this.executorService      = createEventExecutor();
        this.log                  = null;
    }

    /**
     * Convenience constructor with a quorum of 1.  Suitable for testing and
     * single-engine deployments.
     *
     * @param registryPrivateKey   see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param registrySigAlgorithm see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param phoenixPublicKey     see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param phoenixSigAlgorithm  see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     */
    public VerdictRegistryImpl(PrivateKey registryPrivateKey,
                               String registrySigAlgorithm,
                               PublicKey phoenixPublicKey,
                               String phoenixSigAlgorithm) {
        this(registryPrivateKey, registrySigAlgorithm,
             phoenixPublicKey,   phoenixSigAlgorithm, 1);
    }

    /**
     * Creates a new {@code VerdictRegistryImpl} <em>with</em> persistence.
     * State is recovered from {@code logDir} on startup and all subsequent
     * state-changing operations are durably logged so that the full state is
     * available after a restart.
     *
     * @param registryPrivateKey   see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param registrySigAlgorithm see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param phoenixPublicKey     see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param phoenixSigAlgorithm  see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param quorumMinimum        see {@link #VerdictRegistryImpl(PrivateKey, String, PublicKey, String, int)}
     * @param logDir               path to the directory used for
     *                             {@link ReliableLog} stable storage; the
     *                             directory is created if it does not exist;
     *                             must be non-null and non-empty
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if any String argument is empty, or
     *                                  {@code quorumMinimum} is &lt; 1
     * @throws IOException              if the persistent log cannot be
     *                                  created or recovered
     */
    public VerdictRegistryImpl(PrivateKey registryPrivateKey,
                               String registrySigAlgorithm,
                               PublicKey phoenixPublicKey,
                               String phoenixSigAlgorithm,
                               int quorumMinimum,
                               String logDir) throws IOException {
        if (registryPrivateKey   == null) throw new NullPointerException("registryPrivateKey");
        if (registrySigAlgorithm == null) throw new NullPointerException("registrySigAlgorithm");
        if (registrySigAlgorithm.isEmpty()) throw new IllegalArgumentException("registrySigAlgorithm must not be empty");
        if (phoenixPublicKey     == null) throw new NullPointerException("phoenixPublicKey");
        if (phoenixSigAlgorithm  == null) throw new NullPointerException("phoenixSigAlgorithm");
        if (phoenixSigAlgorithm.isEmpty()) throw new IllegalArgumentException("phoenixSigAlgorithm must not be empty");
        if (quorumMinimum < 1) throw new IllegalArgumentException("quorumMinimum must be >= 1");
        if (logDir == null) throw new NullPointerException("logDir");
        if (logDir.isEmpty()) throw new IllegalArgumentException("logDir must not be empty");

        this.registryPrivateKey   = registryPrivateKey;
        this.registrySigAlgorithm = registrySigAlgorithm;
        this.phoenixPublicKey     = phoenixPublicKey;
        this.phoenixSigAlgorithm  = phoenixSigAlgorithm;
        this.quorumMinimum        = quorumMinimum;
        this.executorService      = createEventExecutor();

        // Create the log and recover any previously persisted state.
        ReliableLog rl = new ReliableLog(logDir, new LocalLogHandler());
        rl.recover(VerdictRegistryImpl.class.getClassLoader());
        // Consolidate replayed log entries into a fresh snapshot.
        rl.snapshot();
        this.log = rl;
        logger.log(Level.INFO,
                "VerdictRegistryImpl: persistence initialised from log directory: {0}",
                logDir);
    }

    /**
     * Sets the exported server stub used as the source in
     * {@link VerdictEvent} objects and the server reference in
     * {@link VerdictEventLease} objects.
     *
     * <p>Must be called after the service is exported but before any
     * {@link #registerVerdictListener} call is accepted.  Typically invoked
     * from the outer {@code ActivatableVerdictRegistryImpl.start()} method.
     *
     * @param proxy the exported {@link VerdictRegistry} stub; must be
     *              non-null
     * @throws NullPointerException if {@code proxy} is {@code null}
     */
    void setEventSource(VerdictRegistry proxy) {
        if (proxy == null) throw new NullPointerException("proxy");
        this.eventSource = proxy;
    }

    /** Back-pressure semaphore for asynchronous event delivery. */
    private static final int MAX_PENDING_VERDICT_EVENTS = 200;
    private final Semaphore eventSemaphore = new Semaphore(MAX_PENDING_VERDICT_EVENTS);

    /** Creates the thread pool used for asynchronous event delivery. */
    private static ExecutorService createEventExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // -------------------------------------------------------------------------
    // VerdictRegistry interface
    // -------------------------------------------------------------------------

    @Override
    public void registerAnalysisEngine(String engineId,
                                       PublicKey engineKey,
                                       String sigAlgorithm) throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");
        if (engineKey == null) throw new NullPointerException("engineKey");
        if (sigAlgorithm == null) throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty()) throw new IllegalArgumentException("sigAlgorithm must not be empty");

        engines.put(engineId, new EngineRegistration(engineKey, sigAlgorithm));
        logger.log(Level.INFO, "Registered analysis engine: {0}", engineId);
        appendLogRecord(new RegisterEngineRecord(engineId, engineKey, sigAlgorithm));
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");

        EngineRegistration removed = engines.remove(engineId);
        if (removed == null) {
            logger.log(Level.WARNING,
                    "revokeAnalysisEngine: unknown engine ignored: {0}", engineId);
            return;
        }
        logger.log(Level.INFO, "Revoked analysis engine: {0}", engineId);
        appendLogRecord(new RevokeEngineRecord(engineId));

        // Remove the revoked engine's vote from every hash-keyed state and
        // re-evaluate the SAFE quorum on the live (push-model) store.
        // DANGEROUS verdicts are permanent (fail-safe), so only SAFE
        // re-evaluation is needed.
        reevaluateHashStatesAfterRevoke(engineId);
    }

    /**
     * Removes {@code engineId}'s vote from every {@link HashVerdictState} and
     * re-evaluates the SAFE quorum on the live hash-keyed store.  For each
     * content hash whose vote set changed and that is not permanently
     * {@code dangerous}: if the SAFE quorum is still met the hash verdict is
     * re-published (idempotent); otherwise any previously-published SAFE hash
     * verdict is retracted so clients do not rely on a stale result.  Each
     * change is persisted and any newly published verdict is notified.
     *
     * @param engineId the revoked engine whose votes are withdrawn
     */
    private void reevaluateHashStatesAfterRevoke(String engineId) {
        for (Map.Entry<String, HashVerdictState> entry : hashVerdictStates.entrySet()) {
            String contentHash = entry.getKey();
            HashVerdictState state = entry.getValue();
            RegistryVerdict publishedRv = null;
            synchronized (state) {
                if (state.votes.remove(engineId) == null || state.dangerous) {
                    continue;
                }
                logger.log(Level.INFO,
                        "Removed vote from revoked engine {0} for content hash {1}",
                        new Object[]{engineId, contentHash});
                long safeCount = 0;
                for (VerdictType v : state.votes.values()) {
                    if (v == VerdictType.SAFE) safeCount++;
                }
                if (safeCount >= quorumMinimum) {
                    RegistryVerdict rv = issueHashVerdict(contentHash, VerdictType.SAFE);
                    if (rv != null) {
                        hashPublishedVerdicts.put(contentHash, rv);
                        publishedRv = rv;
                    }
                } else {
                    // Quorum is no longer met; retract any previously-published
                    // SAFE hash verdict.
                    hashPublishedVerdicts.remove(contentHash);
                }
            }
            if (publishedRv != null) {
                appendLogRecord(new HashPublishedVerdictRecord(
                        contentHash, publishedRv, false));
                notifyListeners("hash:" + contentHash, publishedRv);
            }
        }
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        if (report == null) throw new NullPointerException("report");

        // Verify the Phoenix identity signature.
        try {
            byte[] canonical = canonicalBytesForCrashReport(report);
            if (!verify(phoenixPublicKey, phoenixSigAlgorithm, canonical, report.getSignature())) {
                logger.log(Level.WARNING,
                        "Invalid Phoenix signature on CrashReport; discarding");
                return;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IOException e) {
            logger.log(Level.WARNING,
                    "Phoenix signature verification failed; discarding", e);
            return;
        }

        Set<Uri> codebaseUrls = report.getCodebaseUrls();
        String   codebaseKey  = codebaseKey(codebaseUrls);
        VerdictState state    = verdictStates.computeIfAbsent(
                codebaseKey, k -> new VerdictState());

        RegistryVerdict publishedRv = null;

        synchronized (state) {
            if (state.dangerous) {
                return;
            }
            if (state.codebaseUrls == null) {
                state.codebaseUrls = sortedUriArray(codebaseUrls);
            }
            RegistryVerdict rv = issueVerdict(state.codebaseUrls, VerdictType.DANGEROUS);
            if (rv != null) {
                state.dangerous = true;
                publishedVerdicts.put(codebaseKey, rv);
                logger.log(Level.WARNING,
                        "Published DANGEROUS verdict (crash report) for key: {0}",
                        codebaseKey);
                publishedRv = rv;
            }
        }

        if (publishedRv != null) {
            appendLogRecord(new PublishedVerdictRecord(codebaseKey, publishedRv, true));
            notifyListeners(codebaseKey, publishedRv);
        }

        // Reactive hash condemnation: resolve each URI to its known content
        // hash(es) and condemn the hash-keyed store; record pending otherwise.
        for (Uri uri : codebaseUrls) {
            condemnHashesForUri(uri.toString());
        }
    }

    @Override
    public void reportPinning(PinningReport report) throws RemoteException {
        if (report == null) throw new NullPointerException("report");

        // Authentication is handled by JERI mutual auth (SPIFFE SVID for Host 5).
        // No application-level signature verification is needed here.

        Set<Uri> codebaseUrls = report.getCodebaseUrls();
        String   codebaseKey  = codebaseKey(codebaseUrls);
        VerdictState state    = verdictStates.computeIfAbsent(
                codebaseKey, k -> new VerdictState());

        RegistryVerdict publishedRv = null;

        synchronized (state) {
            if (state.dangerous) {
                return;
            }
            if (state.codebaseUrls == null) {
                state.codebaseUrls = sortedUriArray(codebaseUrls);
            }
            RegistryVerdict rv = issueVerdict(state.codebaseUrls, VerdictType.DANGEROUS);
            if (rv != null) {
                state.dangerous = true;
                publishedVerdicts.put(codebaseKey, rv);
                logger.log(Level.WARNING,
                        "Published DANGEROUS verdict (JFR pinning report: {0} events,"
                                + " {1} ns pinned) for key: {2}",
                        new Object[]{
                            report.getEventCount(),
                            report.getPinnedNanos(),
                            codebaseKey
                        });
                publishedRv = rv;
            }
        }

        if (publishedRv != null) {
            appendLogRecord(new PublishedVerdictRecord(codebaseKey, publishedRv, true));
            notifyListeners(codebaseKey, publishedRv);
        }

        // Reactive hash condemnation: resolve each URI to its known content
        // hash(es) and condemn the hash-keyed store; record pending otherwise.
        for (Uri uri : codebaseUrls) {
            condemnHashesForUri(uri.toString());
        }
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");
        return publishedVerdicts.get(codebaseKey(codebaseUrls));
    }

    @Override
    public void submitReport(String engineId, JarAnalysisReport report)
            throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");
        if (report == null) throw new NullPointerException("report");

        EngineRegistration reg = engines.get(engineId);
        if (reg == null) {
            logger.log(Level.WARNING,
                    "Discarding report from unregistered/revoked engine: {0}", engineId);
            return;
        }

        // Verify the engine's signature over the report's single-source-of-truth
        // canonical form.  This covers contentHash, per-class verdicts, declared
        // permissions AND codebase URLs — fixing the prior silent-discard bug for
        // reports that declared permissions or carried codebase URLs.
        try {
            byte[] canonical = report.canonicalBytes();
            if (!verify(reg.publicKey, reg.sigAlgorithm, canonical,
                        report.getEngineSignature())) {
                logger.log(Level.WARNING,
                        "Invalid signature on JarAnalysisReport from engine {0}; discarding",
                        engineId);
                return;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            logger.log(Level.WARNING,
                    "Signature verification failed for JarAnalysisReport from engine "
                    + engineId + "; discarding", e);
            return;
        }

        String   contentHash  = report.getContentHash();
        String[] reportUrls   = report.getCodebaseUrls();
        VerdictType derived   = report.deriveVerdictType();

        // Record the URI → content-hash index for reactive condemnation, and
        // determine whether any of this report's URIs is already condemned.
        boolean forcedDangerous = false;
        for (String uri : reportUrls) {
            uriToHashes.computeIfAbsent(uri, k -> ConcurrentHashMap.newKeySet())
                    .add(contentHash);
            if (pendingUrlCondemnations.remove(uri)) {
                forcedDangerous = true;
            }
        }
        if (forcedDangerous) {
            derived = VerdictType.DANGEROUS;
            logger.log(Level.WARNING,
                    "Forcing DANGEROUS for content hash {0} due to a pending URL "
                    + "condemnation", contentHash);
        }

        HashVerdictState state = hashVerdictStates.computeIfAbsent(
                contentHash, k -> new HashVerdictState());

        RegistryVerdict publishedRv  = null;
        boolean         isDangerous  = false;

        synchronized (state) {
            if (state.dangerous) {
                // Still log the accepted vote for replay completeness.
                appendLogRecord(new HashVoteRecord(
                        engineId, contentHash, derived, reportUrls));
                return;
            }
            state.votes.put(engineId, derived);

            if (derived == VerdictType.DANGEROUS) {
                RegistryVerdict rv = issueHashVerdict(contentHash, VerdictType.DANGEROUS);
                if (rv != null) {
                    state.dangerous = true;
                    hashPublishedVerdicts.put(contentHash, rv);
                    logger.log(Level.WARNING,
                            "Published DANGEROUS hash verdict for content hash: {0}",
                            contentHash);
                    publishedRv = rv;
                    isDangerous = true;
                }
            } else {
                long safeCount = 0;
                for (VerdictType v : state.votes.values()) {
                    if (v == VerdictType.SAFE) safeCount++;
                }
                if (safeCount >= quorumMinimum) {
                    RegistryVerdict rv = issueHashVerdict(contentHash, VerdictType.SAFE);
                    if (rv != null) {
                        hashPublishedVerdicts.put(contentHash, rv);
                        logger.log(Level.INFO,
                                "Published SAFE hash verdict for content hash: {0}",
                                contentHash);
                        publishedRv = rv;
                    }
                }
            }
        }

        // Persist the accepted vote and any published verdict for restart recovery.
        appendLogRecord(new HashVoteRecord(engineId, contentHash, derived, reportUrls));
        if (publishedRv != null) {
            appendLogRecord(new HashPublishedVerdictRecord(
                    contentHash, publishedRv, isDangerous));
            notifyListeners("hash:" + contentHash, publishedRv);
        }
    }

    /**
     * Condemns every content hash known to be served from {@code uri} as
     * permanently {@link VerdictType#DANGEROUS}, publishing a hash-keyed
     * {@link RegistryVerdict} and notifying {@code hash:}-scoped listeners.
     * If no content hash is yet known for {@code uri}, the URI is recorded in
     * {@link #pendingUrlCondemnations} so that a later report mapping it to a
     * hash is forced DANGEROUS.
     *
     * <p>Called from {@link #reportCrash} and {@link #reportPinning} after the
     * existing URL-keyed condemnation.
     *
     * @param uri the condemned codebase URI string
     */
    private void condemnHashesForUri(String uri) {
        Set<String> hashes = uriToHashes.get(uri);
        if (hashes == null || hashes.isEmpty()) {
            pendingUrlCondemnations.add(uri);
            return;
        }
        for (String contentHash : hashes) {
            HashVerdictState state = hashVerdictStates.computeIfAbsent(
                    contentHash, k -> new HashVerdictState());
            RegistryVerdict publishedRv = null;
            synchronized (state) {
                if (state.dangerous) {
                    continue;
                }
                RegistryVerdict rv = issueHashVerdict(contentHash, VerdictType.DANGEROUS);
                if (rv != null) {
                    state.dangerous = true;
                    hashPublishedVerdicts.put(contentHash, rv);
                    logger.log(Level.WARNING,
                            "Condemned content hash {0} (DANGEROUS) via URL {1}",
                            new Object[]{contentHash, uri});
                    publishedRv = rv;
                }
            }
            if (publishedRv != null) {
                appendLogRecord(new HashPublishedVerdictRecord(
                        contentHash, publishedRv, true));
                notifyListeners("hash:" + contentHash, publishedRv);
            }
        }
    }

    @Override
    public RegistryVerdict getVerdictByHash(String contentHash) throws RemoteException {
        if (contentHash == null) throw new NullPointerException("contentHash");
        if (contentHash.isEmpty())
            throw new IllegalArgumentException("contentHash must not be empty");
        return hashPublishedVerdicts.get(contentHash);
    }

    @Override
    public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                     Set<Uri> codebaseUrls,
                                                     MarshalledInstance handback,
                                                     long leaseDuration)
            throws RemoteException {
        if (listener    == null) throw new NullPointerException("listener");
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");

        VerdictRegistry src = eventSource;
        if (src == null) throw new IllegalStateException(
                "Service has not been exported yet; call setEventSource first");

        long now         = System.currentTimeMillis();
        long granted     = clampLeaseDuration(leaseDuration);
        long expiration  = now + granted;
        Uuid leaseId     = UuidFactory.generate();
        long eventId     = nextEventId.getAndIncrement();
        String codebaseKey = codebaseKey(codebaseUrls);

        ListenerRegistration reg = new ListenerRegistration(
                leaseId, eventId, expiration, listener, handback, codebaseKey);
        listenerRegistrations.put(leaseId, reg);

        // If a verdict is already published for this codebase, deliver it
        // immediately so the listener does not miss it.
        long initialSeqNum = 0L;
        RegistryVerdict current = publishedVerdicts.get(codebaseKey);
        if (current != null) {
            initialSeqNum = reg.seqNum.incrementAndGet();
            final long capturedSeqNum = initialSeqNum;
            final RegistryVerdict capturedVerdict = current;
            eventSemaphore.acquireUninterruptibly();
            executorService.execute(() -> {
                try { new SendVerdictTask(reg, capturedVerdict, capturedSeqNum).run(); }
                finally { eventSemaphore.release(); }
            });
        }

        Lease lease = new VerdictEventLease(src, leaseId, expiration);
        return new EventRegistration(eventId, src, lease, initialSeqNum);
    }

    @Override
    public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener,
                                                           MarshalledInstance handback,
                                                           long leaseDuration)
            throws RemoteException {
        if (listener == null) throw new NullPointerException("listener");

        VerdictRegistry src = eventSource;
        if (src == null) throw new IllegalStateException(
                "Service has not been exported yet; call setEventSource first");

        long now        = System.currentTimeMillis();
        long granted    = clampLeaseDuration(leaseDuration);
        long expiration = now + granted;
        Uuid leaseId    = UuidFactory.generate();
        long eventId    = nextEventId.getAndIncrement();

        // codebaseKey == null is the sentinel for "all verdicts".
        ListenerRegistration reg = new ListenerRegistration(
                leaseId, eventId, expiration, listener, handback, null);
        listenerRegistrations.put(leaseId, reg);

        // Burst-deliver all currently-published verdicts (URL-keyed and
        // hash-keyed) so the replica/subscriber can bootstrap its local cache.
        long initialSeqNum = 0L;
        for (RegistryVerdict v : publishedVerdicts.values()) {
            long sn = reg.seqNum.incrementAndGet();
            final long capturedSn = sn;
            final RegistryVerdict capturedV = v;
            eventSemaphore.acquireUninterruptibly();
            executorService.execute(() -> {
                try { new SendVerdictTask(reg, capturedV, capturedSn).run(); }
                finally { eventSemaphore.release(); }
            });
            initialSeqNum = capturedSn;
        }
        for (RegistryVerdict v : hashPublishedVerdicts.values()) {
            long sn = reg.seqNum.incrementAndGet();
            final long capturedSn = sn;
            final RegistryVerdict capturedV = v;
            eventSemaphore.acquireUninterruptibly();
            executorService.execute(() -> {
                try { new SendVerdictTask(reg, capturedV, capturedSn).run(); }
                finally { eventSemaphore.release(); }
            });
            initialSeqNum = capturedSn;
        }

        Lease lease = new VerdictEventLease(src, leaseId, expiration);
        return new EventRegistration(eventId, src, lease, initialSeqNum);
    }

    @Override
    public long renewEventLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException {
        if (leaseId == null) throw new NullPointerException("leaseId");

        ListenerRegistration reg = listenerRegistrations.get(leaseId);
        if (reg == null) throw new UnknownLeaseException("Unknown lease: " + leaseId);

        long granted = clampLeaseDuration(duration);
        reg.leaseExpiration = System.currentTimeMillis() + granted;
        return granted;
    }

    @Override
    public void cancelEventLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException {
        if (leaseId == null) throw new NullPointerException("leaseId");
        if (listenerRegistrations.remove(leaseId) == null) {
            throw new UnknownLeaseException("Unknown lease: " + leaseId);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Clamps a requested lease duration to the range
     * {@code [0, MAX_LISTENER_LEASE_DURATION]}.
     * {@link Lease#ANY} is treated as a request for the maximum duration.
     *
     * @param requested the requested duration in milliseconds, or
     *                  {@link Lease#ANY}
     * @return the clamped duration in milliseconds
     */
    private static long clampLeaseDuration(long requested) {
        if (requested == Lease.ANY || requested > MAX_LISTENER_LEASE_DURATION) {
            return MAX_LISTENER_LEASE_DURATION;
        }
        return Math.max(requested, 0L);
    }

    /**
     * Fans out a newly-published {@link RegistryVerdict} to every registered
     * listener whose codebase key matches, and to all <em>global</em>
     * listeners (those registered via {@link #registerGlobalVerdictListener}
     * whose {@code codebaseKey} is {@code null}).  Expired registrations are
     * pruned lazily.  Called outside of any lock.
     *
     * @param codebaseKey the canonical key for the codebase set
     * @param verdict     the verdict that was just published
     */
    private void notifyListeners(String codebaseKey, RegistryVerdict verdict) {
        long now = System.currentTimeMillis();
        for (Map.Entry<Uuid, ListenerRegistration> entry
                : listenerRegistrations.entrySet()) {
            ListenerRegistration reg = entry.getValue();
            // reg.codebaseKey == null  → global listener: receives every verdict.
            // reg.codebaseKey != null  → codebase-scoped: only matching key.
            if (reg.codebaseKey != null && !codebaseKey.equals(reg.codebaseKey)) continue;
            if (reg.leaseExpiration < now) {
                listenerRegistrations.remove(entry.getKey());
                continue;
            }
            long seqNum = reg.seqNum.incrementAndGet();
            eventSemaphore.acquireUninterruptibly();
            executorService.execute(() -> {
                try { new SendVerdictTask(reg, verdict, seqNum).run(); }
                finally { eventSemaphore.release(); }
            });
        }
    }

    /**
     * Signs and constructs a new {@link RegistryVerdict} for the given
     * (already-sorted) codebase URI array and verdict type.
     *
     * @param sortedUrls sorted array of codebase URIs
     * @param type       the verdict type to issue
     * @return a signed {@link RegistryVerdict}, or {@code null} if signing fails
     */
    private RegistryVerdict issueVerdict(Uri[] sortedUrls, VerdictType type) {
        long timestamp = System.currentTimeMillis();
        try {
            byte[] canonical = canonicalBytesForRegistryVerdict(sortedUrls, type, timestamp);
            byte[] signature = sign(registryPrivateKey, registrySigAlgorithm, canonical);
            return new RegistryVerdict(sortedUrls, type, timestamp, signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IOException e) {
            logger.log(Level.SEVERE, "Failed to sign RegistryVerdict", e);
            return null;
        }
    }

    /**
     * Issues a hash-keyed {@link RegistryVerdict} for the given content hash.
     * The verdict uses a synthetic {@code urn:sha256:<hash>} URI as the single
     * codebase URL, which is a valid RFC 3986 URN.
     */
    private RegistryVerdict issueHashVerdict(String contentHash, VerdictType type) {
        try {
            Uri urn = new Uri("urn:sha256:" + contentHash);
            return issueVerdict(new Uri[]{ urn }, type);
        } catch (java.net.URISyntaxException e) {
            logger.log(Level.SEVERE, "Failed to construct URN for content hash", e);
            return null;
        }
    }

    /**
     * Returns a deterministic string key for a set of codebase URIs.
     * The key is the null-byte-separated, lexicographically sorted list of
     * URI strings.
     */
    static String codebaseKey(Set<Uri> codebaseUrls) {
        String[] sorted = sortedUriStrings(codebaseUrls);
        StringBuilder sb = new StringBuilder();
        for (String s : sorted) {
            if (sb.length() > 0) sb.append('\0');
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Returns the codebase URLs as a sorted {@link Uri} array.
     */
    private static Uri[] sortedUriArray(Set<Uri> codebaseUrls) {
        Uri[] arr = codebaseUrls.toArray(new Uri[0]);
        Arrays.sort(arr, (a, b) -> a.toString().compareTo(b.toString()));
        return arr;
    }

    /**
     * Returns the sorted URI strings from a codebase URL set.
     */
    private static String[] sortedUriStrings(Set<Uri> urls) {
        String[] sorted = new String[urls.size()];
        int i = 0;
        for (Uri uri : urls) {
            sorted[i++] = uri.toString();
        }
        Arrays.sort(sorted);
        return sorted;
    }

    /**
     * Produces the canonical bytes for a {@link CrashReport} that are
     * verified against the Phoenix public key.
     *
     * <p>Format:
     * <ol>
     *   <li>For each URL in lexicographic order:
     *       4-byte big-endian byte-length, then UTF-8 bytes.</li>
     *   <li>4-byte big-endian exit code.</li>
     *   <li>8-byte big-endian incarnation number.</li>
     *   <li>4-byte big-endian byte-length of stderrSummary UTF-8, then those
     *       bytes.</li>
     * </ol>
     */
    static byte[] canonicalBytesForCrashReport(CrashReport report) throws IOException {
        String[] sorted = sortedUriStrings(report.getCodebaseUrls());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        for (String url : sorted) {
            byte[] b = url.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(report.getExitCode());
        dos.writeLong(report.getIncarnation());
        byte[] stderrBytes = report.getStderrSummary().getBytes(StandardCharsets.UTF_8);
        dos.writeInt(stderrBytes.length);
        dos.write(stderrBytes);
        dos.flush();
        return baos.toByteArray();
    }

    /**
     * Produces the canonical bytes that the registry signs for a
     * {@link RegistryVerdict}.
     *
     * <p>Format: sorted URLs (4-byte length-prefixed UTF-8 each), 4-byte
     * verdict ordinal, 8-byte timestamp.
     */
    private static byte[] canonicalBytesForRegistryVerdict(Uri[] sortedUrls,
                                                            VerdictType type,
                                                            long timestamp)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        for (Uri uri : sortedUrls) {
            byte[] b = uri.toString().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(type.ordinal());
        dos.writeLong(timestamp);
        dos.flush();
        return baos.toByteArray();
    }

    private static byte[] sign(PrivateKey key, String algorithm, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(algorithm);
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    private static boolean verify(PublicKey key, String algorithm,
                                  byte[] data, byte[] signature)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(algorithm);
        sig.initVerify(key);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Converts a sorted {@link Uri} array to a {@code String[]} of URI strings.
     */
    private static String[] uriArrayToStrings(Uri[] uris) {
        String[] strs = new String[uris.length];
        for (int i = 0; i < uris.length; i++) {
            strs[i] = uris[i].toString();
        }
        return strs;
    }

    /**
     * Converts a {@code String[]} of URI strings back to a sorted {@link Uri} array.
     * Strings that cannot be parsed as valid RFC 3986 URIs are silently skipped
     * (which cannot occur in practice because they were validated on first submission).
     */
    private static Uri[] stringsToUriArray(String[] strs) {
        Uri[] uris = new Uri[strs.length];
        int count = 0;
        for (String s : strs) {
            try {
                uris[count++] = new Uri(s);
            } catch (URISyntaxException e) {
                logger.log(Level.WARNING,
                        "Skipping unparseable URI during log recovery: {0}", s);
            }
        }
        if (count == strs.length) {
            return uris;
        }
        return Arrays.copyOf(uris, count);
    }

    /**
     * Writes a log record if persistence is enabled.  Failures are logged
     * as warnings but do not propagate: the service remains functional even
     * if the log write fails (the state is already updated in-memory).
     *
     * @param record the serializable log record to append
     */
    private void appendLogRecord(Serializable record) {
        if (log == null) {
            return;
        }
        synchronized (logLock) {
            try {
                log.update(record);
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "Failed to write persistence log record; state may not survive restart",
                        e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persistence — log handler and record types
    // -------------------------------------------------------------------------

    /**
     * {@link LogHandler} implementation that serialises the full registry
     * state as a snapshot and applies incremental update records during
     * recovery.
     */
    private final class LocalLogHandler extends LogHandler {

        @Override
        public void snapshot(OutputStream out) throws Exception {
            // Build serialisable snapshots of all three state maps.
            HashMap<String, SerializableEngineReg> engSnap =
                    new HashMap<String, SerializableEngineReg>(engines.size());
            for (Map.Entry<String, EngineRegistration> e : engines.entrySet()) {
                engSnap.put(e.getKey(),
                        new SerializableEngineReg(e.getValue().publicKey,
                                                  e.getValue().sigAlgorithm));
            }

            HashMap<String, SerializableVerdictState> stateSnap =
                    new HashMap<String, SerializableVerdictState>(verdictStates.size());
            for (Map.Entry<String, VerdictState> e : verdictStates.entrySet()) {
                VerdictState vs = e.getValue();
                synchronized (vs) {
                    stateSnap.put(e.getKey(),
                            new SerializableVerdictState(
                                    vs.codebaseUrls == null
                                            ? new String[0]
                                            : uriArrayToStrings(vs.codebaseUrls),
                                    vs.dangerous));
                }
            }

            HashMap<String, SerializablePublishedVerdict> pubSnap =
                    new HashMap<String, SerializablePublishedVerdict>(publishedVerdicts.size());
            for (Map.Entry<String, RegistryVerdict> e : publishedVerdicts.entrySet()) {
                RegistryVerdict rv = e.getValue();
                pubSnap.put(e.getKey(),
                        new SerializablePublishedVerdict(
                                sortedUriStrings(rv.getCodebaseUrls()),
                                rv.getVerdict(),
                                rv.getTimestamp(),
                                rv.getSignature()));
            }

            // Hash-keyed (push-model) state.
            HashMap<String, SerializableHashVerdictState> hashStateSnap =
                    new HashMap<String, SerializableHashVerdictState>(hashVerdictStates.size());
            for (Map.Entry<String, HashVerdictState> e : hashVerdictStates.entrySet()) {
                HashVerdictState hvs = e.getValue();
                synchronized (hvs) {
                    hashStateSnap.put(e.getKey(),
                            new SerializableHashVerdictState(
                                    new HashMap<String, VerdictType>(hvs.votes),
                                    hvs.dangerous));
                }
            }

            HashMap<String, SerializablePublishedVerdict> hashPubSnap =
                    new HashMap<String, SerializablePublishedVerdict>(hashPublishedVerdicts.size());
            for (Map.Entry<String, RegistryVerdict> e : hashPublishedVerdicts.entrySet()) {
                RegistryVerdict rv = e.getValue();
                hashPubSnap.put(e.getKey(),
                        new SerializablePublishedVerdict(
                                sortedUriStrings(rv.getCodebaseUrls()),
                                rv.getVerdict(),
                                rv.getTimestamp(),
                                rv.getSignature()));
            }

            HashMap<String, java.util.HashSet<String>> uriIdxSnap =
                    new HashMap<String, java.util.HashSet<String>>(uriToHashes.size());
            for (Map.Entry<String, Set<String>> e : uriToHashes.entrySet()) {
                uriIdxSnap.put(e.getKey(),
                        new java.util.HashSet<String>(e.getValue()));
            }

            java.util.HashSet<String> pendingSnap =
                    new java.util.HashSet<String>(pendingUrlCondemnations);

            PersistentSnapshot snap =
                    new PersistentSnapshot(engSnap, stateSnap, pubSnap,
                            hashStateSnap, hashPubSnap, uriIdxSnap, pendingSnap);
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(snap);
            oos.flush();
        }

        @Override
        public void recover(InputStream in) throws Exception {
            ObjectInputStream ois = new ObjectInputStream(in);
            PersistentSnapshot snap = (PersistentSnapshot) ois.readObject();

            for (Map.Entry<String, SerializableEngineReg> e : snap.engines.entrySet()) {
                engines.put(e.getKey(),
                        new EngineRegistration(e.getValue().publicKey,
                                               e.getValue().sigAlgorithm));
            }

            for (Map.Entry<String, SerializableVerdictState> e : snap.states.entrySet()) {
                VerdictState vs = new VerdictState();
                SerializableVerdictState svs = e.getValue();
                if (svs.codebaseUrls != null && svs.codebaseUrls.length > 0) {
                    vs.codebaseUrls = stringsToUriArray(svs.codebaseUrls);
                }
                vs.dangerous = svs.dangerous;
                verdictStates.put(e.getKey(), vs);
            }

            for (Map.Entry<String, SerializablePublishedVerdict> e : snap.published.entrySet()) {
                SerializablePublishedVerdict spv = e.getValue();
                Uri[] uris = stringsToUriArray(spv.codebaseUrls);
                if (uris.length > 0) {
                    publishedVerdicts.put(e.getKey(),
                            new RegistryVerdict(uris, spv.verdictType,
                                                spv.timestamp, spv.signature));
                }
            }

            // Hash-keyed (push-model) state.  Guard each field for forward
            // compatibility with older (v1) snapshots that lacked these fields.
            if (snap.hashStates != null) {
                for (Map.Entry<String, SerializableHashVerdictState> e
                        : snap.hashStates.entrySet()) {
                    SerializableHashVerdictState shvs = e.getValue();
                    HashVerdictState hvs = new HashVerdictState();
                    hvs.votes.putAll(shvs.votes);
                    hvs.dangerous = shvs.dangerous;
                    hashVerdictStates.put(e.getKey(), hvs);
                }
            }
            if (snap.hashPublished != null) {
                for (Map.Entry<String, SerializablePublishedVerdict> e
                        : snap.hashPublished.entrySet()) {
                    SerializablePublishedVerdict spv = e.getValue();
                    Uri[] uris = stringsToUriArray(spv.codebaseUrls);
                    if (uris.length > 0) {
                        hashPublishedVerdicts.put(e.getKey(),
                                new RegistryVerdict(uris, spv.verdictType,
                                                    spv.timestamp, spv.signature));
                    }
                }
            }
            if (snap.uriToHashes != null) {
                for (Map.Entry<String, java.util.HashSet<String>> e
                        : snap.uriToHashes.entrySet()) {
                    uriToHashes.computeIfAbsent(e.getKey(),
                                    k -> ConcurrentHashMap.newKeySet())
                            .addAll(e.getValue());
                }
            }
            if (snap.pendingUrlCondemnations != null) {
                pendingUrlCondemnations.addAll(snap.pendingUrlCondemnations);
            }
        }

        @Override
        public void applyUpdate(Object update) throws Exception {
            if (update instanceof RegisterEngineRecord) {
                RegisterEngineRecord rec = (RegisterEngineRecord) update;
                engines.put(rec.engineId,
                        new EngineRegistration(rec.publicKey, rec.sigAlgorithm));

            } else if (update instanceof RevokeEngineRecord) {
                RevokeEngineRecord rec = (RevokeEngineRecord) update;
                engines.remove(rec.engineId);
                // Re-evaluate the SAFE quorum on the live hash-keyed store for
                // every content hash that had a vote from the revoked engine,
                // mirroring the runtime revoke path.
                for (Map.Entry<String, HashVerdictState> entry
                        : hashVerdictStates.entrySet()) {
                    String contentHash = entry.getKey();
                    HashVerdictState state = entry.getValue();
                    synchronized (state) {
                        if (state.votes.remove(rec.engineId) == null || state.dangerous) {
                            continue;
                        }
                        long safeCount = 0;
                        for (VerdictType v : state.votes.values()) {
                            if (v == VerdictType.SAFE) safeCount++;
                        }
                        if (safeCount >= quorumMinimum) {
                            RegistryVerdict rv = issueHashVerdict(
                                    contentHash, VerdictType.SAFE);
                            if (rv != null) {
                                hashPublishedVerdicts.put(contentHash, rv);
                            }
                        } else {
                            hashPublishedVerdicts.remove(contentHash);
                        }
                    }
                }

            } else if (update instanceof PublishedVerdictRecord) {
                PublishedVerdictRecord rec = (PublishedVerdictRecord) update;
                Uri[] uris = stringsToUriArray(rec.codebaseUrls);
                if (uris.length > 0) {
                    RegistryVerdict rv = new RegistryVerdict(
                            uris, rec.verdictType, rec.timestamp, rec.signature);
                    publishedVerdicts.put(rec.codebaseKey, rv);
                    VerdictState state = verdictStates.computeIfAbsent(
                            rec.codebaseKey, k -> new VerdictState());
                    synchronized (state) {
                        if (rec.dangerous) {
                            state.dangerous = true;
                        }
                        if (state.codebaseUrls == null) {
                            state.codebaseUrls = uris;
                        }
                    }
                }

            } else if (update instanceof HashVoteRecord) {
                HashVoteRecord rec = (HashVoteRecord) update;
                // Rebuild the URI → content-hash index.
                if (rec.codebaseUrls != null) {
                    for (String uri : rec.codebaseUrls) {
                        uriToHashes.computeIfAbsent(uri,
                                        k -> ConcurrentHashMap.newKeySet())
                                .add(rec.contentHash);
                        pendingUrlCondemnations.remove(uri);
                    }
                }
                HashVerdictState state = hashVerdictStates.computeIfAbsent(
                        rec.contentHash, k -> new HashVerdictState());
                synchronized (state) {
                    state.votes.put(rec.engineId, rec.verdictType);
                    // Re-evaluate in case the corresponding
                    // HashPublishedVerdictRecord was not flushed before a crash.
                    if (rec.verdictType == VerdictType.DANGEROUS && !state.dangerous) {
                        RegistryVerdict rv = issueHashVerdict(
                                rec.contentHash, VerdictType.DANGEROUS);
                        if (rv != null) {
                            state.dangerous = true;
                            hashPublishedVerdicts.put(rec.contentHash, rv);
                        }
                    } else if (rec.verdictType != VerdictType.DANGEROUS
                            && !state.dangerous) {
                        long safeCount = 0;
                        for (VerdictType v : state.votes.values()) {
                            if (v == VerdictType.SAFE) safeCount++;
                        }
                        if (safeCount >= quorumMinimum) {
                            RegistryVerdict rv = issueHashVerdict(
                                    rec.contentHash, VerdictType.SAFE);
                            if (rv != null) {
                                hashPublishedVerdicts.put(rec.contentHash, rv);
                            }
                        }
                    }
                }

            } else if (update instanceof HashPublishedVerdictRecord) {
                HashPublishedVerdictRecord rec = (HashPublishedVerdictRecord) update;
                Uri[] uris = stringsToUriArray(rec.codebaseUrls);
                if (uris.length > 0) {
                    RegistryVerdict rv = new RegistryVerdict(
                            uris, rec.verdictType, rec.timestamp, rec.signature);
                    hashPublishedVerdicts.put(rec.contentHash, rv);
                    HashVerdictState state = hashVerdictStates.computeIfAbsent(
                            rec.contentHash, k -> new HashVerdictState());
                    synchronized (state) {
                        if (rec.dangerous) {
                            state.dangerous = true;
                        }
                    }
                }

            } else {
                logger.log(Level.WARNING,
                        "Unknown log record type during recovery: {0}",
                        update == null ? "null" : update.getClass().getName());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persistence — serialisable data carriers
    // -------------------------------------------------------------------------

    /** Full snapshot of all persistent state. */
    static final class PersistentSnapshot implements Serializable {
        private static final long serialVersionUID = 3L;
        final HashMap<String, SerializableEngineReg>        engines;
        final HashMap<String, SerializableVerdictState>     states;
        final HashMap<String, SerializablePublishedVerdict> published;
        // Hash-keyed (push-model) state.
        final HashMap<String, SerializableHashVerdictState> hashStates;
        final HashMap<String, SerializablePublishedVerdict> hashPublished;
        final HashMap<String, java.util.HashSet<String>>    uriToHashes;
        final java.util.HashSet<String>                     pendingUrlCondemnations;
        PersistentSnapshot(HashMap<String, SerializableEngineReg>        engines,
                           HashMap<String, SerializableVerdictState>     states,
                           HashMap<String, SerializablePublishedVerdict> published,
                           HashMap<String, SerializableHashVerdictState> hashStates,
                           HashMap<String, SerializablePublishedVerdict> hashPublished,
                           HashMap<String, java.util.HashSet<String>>    uriToHashes,
                           java.util.HashSet<String>                     pendingUrlCondemnations) {
            this.engines       = engines;
            this.states        = states;
            this.published     = published;
            this.hashStates    = hashStates;
            this.hashPublished = hashPublished;
            this.uriToHashes   = uriToHashes;
            this.pendingUrlCondemnations = pendingUrlCondemnations;
        }
    }

    /** Serialisable form of {@link EngineRegistration}. */
    static final class SerializableEngineReg implements Serializable {
        private static final long serialVersionUID = 1L;
        final PublicKey publicKey;
        final String    sigAlgorithm;
        SerializableEngineReg(PublicKey publicKey, String sigAlgorithm) {
            this.publicKey    = publicKey;
            this.sigAlgorithm = sigAlgorithm;
        }
    }

    /** Serialisable form of {@link VerdictState} (URL-keyed DANGEROUS state). */
    static final class SerializableVerdictState implements Serializable {
        private static final long serialVersionUID = 2L;
        final String[] codebaseUrls; // sorted URI strings
        final boolean  dangerous;
        SerializableVerdictState(String[] codebaseUrls, boolean dangerous) {
            this.codebaseUrls = codebaseUrls;
            this.dangerous    = dangerous;
        }
    }

    /** Serialisable form of {@link HashVerdictState} (push-model, hash-keyed). */
    static final class SerializableHashVerdictState implements Serializable {
        private static final long serialVersionUID = 1L;
        final HashMap<String, VerdictType> votes;
        final boolean                      dangerous;
        SerializableHashVerdictState(HashMap<String, VerdictType> votes,
                                     boolean dangerous) {
            this.votes     = votes;
            this.dangerous = dangerous;
        }
    }

    /** Serialisable form of a published {@link RegistryVerdict}. */
    static final class SerializablePublishedVerdict implements Serializable {
        private static final long serialVersionUID = 1L;
        final String[]    codebaseUrls; // sorted URI strings
        final VerdictType verdictType;
        final long        timestamp;
        final byte[]      signature;
        SerializablePublishedVerdict(String[] codebaseUrls, VerdictType verdictType,
                                     long timestamp, byte[] signature) {
            this.codebaseUrls = codebaseUrls;
            this.verdictType  = verdictType;
            this.timestamp    = timestamp;
            this.signature    = signature;
        }
    }

    /** Log record: an analysis engine was registered. */
    static final class RegisterEngineRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        final String    engineId;
        final PublicKey publicKey;
        final String    sigAlgorithm;
        RegisterEngineRecord(String engineId, PublicKey publicKey, String sigAlgorithm) {
            this.engineId     = engineId;
            this.publicKey    = publicKey;
            this.sigAlgorithm = sigAlgorithm;
        }
    }

    /** Log record: an analysis engine was revoked. */
    static final class RevokeEngineRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        final String engineId;
        RevokeEngineRecord(String engineId) {
            this.engineId = engineId;
        }
    }

    /**
     * Log record: a {@link RegistryVerdict} was published.
     * Stores the raw fields of the verdict rather than the verdict itself
     * because {@link RegistryVerdict} uses {@code @AtomicSerial} and requires
     * a specialised {@link ObjectInputStream} for safe deserialisation.
     */
    static final class PublishedVerdictRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        final String      codebaseKey;
        final String[]    codebaseUrls; // sorted URI strings
        final VerdictType verdictType;
        final long        timestamp;
        final byte[]      signature;
        final boolean     dangerous;
        PublishedVerdictRecord(String codebaseKey, RegistryVerdict verdict,
                               boolean dangerous) {
            this.codebaseKey  = codebaseKey;
            this.codebaseUrls = sortedUriStrings(verdict.getCodebaseUrls());
            this.verdictType  = verdict.getVerdict();
            this.timestamp    = verdict.getTimestamp();
            this.signature    = verdict.getSignature();
            this.dangerous    = dangerous;
        }
    }

    /**
     * Log record: a signature-verified {@link JarAnalysisReport} vote was
     * accepted (push model, hash-keyed).  Carries the report's codebase URI
     * strings so the {@link #uriToHashes} index can be rebuilt on recovery.
     */
    static final class HashVoteRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        final String      engineId;
        final String      contentHash;
        final VerdictType verdictType; // derived verdict
        final String[]    codebaseUrls;
        HashVoteRecord(String engineId, String contentHash,
                       VerdictType verdictType, String[] codebaseUrls) {
            this.engineId     = engineId;
            this.contentHash  = contentHash;
            this.verdictType  = verdictType;
            this.codebaseUrls = codebaseUrls;
        }
    }

    /**
     * Log record: a hash-keyed {@link RegistryVerdict} was published (push
     * model), including crash/pinning-driven condemnations.
     */
    static final class HashPublishedVerdictRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        final String      contentHash;
        final String[]    codebaseUrls; // sorted URI strings (synthetic urn:sha256)
        final VerdictType verdictType;
        final long        timestamp;
        final byte[]      signature;
        final boolean     dangerous;
        HashPublishedVerdictRecord(String contentHash, RegistryVerdict verdict,
                                   boolean dangerous) {
            this.contentHash  = contentHash;
            this.codebaseUrls = sortedUriStrings(verdict.getCodebaseUrls());
            this.verdictType  = verdict.getVerdict();
            this.timestamp    = verdict.getTimestamp();
            this.signature    = verdict.getSignature();
            this.dangerous    = dangerous;
        }
    }
}
