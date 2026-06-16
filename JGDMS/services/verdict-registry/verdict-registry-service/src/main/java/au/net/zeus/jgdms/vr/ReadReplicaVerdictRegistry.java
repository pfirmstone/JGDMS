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
import java.nio.charset.StandardCharsets;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.vr.proxy.VerdictEvent;
import au.net.zeus.jgdms.vr.proxy.VerdictEventLease;

/**
 * An event-sourced read replica of a primary {@link VerdictRegistry}.
 *
 * <p>This class subscribes to the primary registry using
 * {@link VerdictRegistry#registerGlobalVerdictListener}, caches all received
 * verdicts locally after verifying their DER signatures, and serves read
 * requests lock-free from those caches.  During a primary outage the cached
 * verdicts remain fully accessible to clients — there is no TTL expiry.
 *
 * <h2>Startup sequence</h2>
 * <ol>
 *   <li>Construct a {@code ReadReplicaVerdictRegistry} with a primary proxy,
 *       the registry's public key (for signature verification), and an
 *       optional {@link Exporter}.</li>
 *   <li>Call {@link #start()} to export this instance, register with the
 *       primary, and receive the initial burst of all currently-published
 *       verdicts.  After {@code start()} returns the {@link #isReady()}
 *       flag is {@code true}.</li>
 *   <li>Register this replica with a {@link net.jini.pref.VerdictRegistryHolder}
 *       as a fallback candidate so that clients can fail over to it on
 *       {@link RemoteException} from the primary.</li>
 *   <li>Call {@link #stop()} on orderly shutdown to cancel the subscription
 *       lease and unexport this listener.</li>
 * </ol>
 *
 * <h2>Write operations</h2>
 * All write methods ({@code registerAnalysisEngine}, {@code submitReport},
 * {@code reportCrash}, etc.) throw {@link UnsupportedOperationException}.
 * Analysis engines and crash reporters must always target the primary.
 *
 * <h2>Thread safety</h2>
 * The verdict caches are {@link ConcurrentHashMap}s of immutable
 * {@link RegistryVerdict} objects; reads are always safe from any thread.
 * The {@link #start()} / {@link #stop()} lifecycle methods are synchronised.
 *
 * @see VerdictRegistry#registerGlobalVerdictListener
 * @since 3.1.1
 * @author GitHub Copilot
 */
public class ReadReplicaVerdictRegistry
        implements VerdictRegistry, RemoteEventListener, LeaseListener {

    private static final Logger logger =
            Logger.getLogger(ReadReplicaVerdictRegistry.class.getName());

    private static final String URN_SHA256_PREFIX = "urn:sha256:";

    /** URL-keyed cache: codebase key → RegistryVerdict. */
    private final ConcurrentHashMap<String, RegistryVerdict> publishedVerdicts =
            new ConcurrentHashMap<>();

    /** Hash-keyed cache: SHA-256 hex → RegistryVerdict. */
    private final ConcurrentHashMap<String, RegistryVerdict> hashPublishedVerdicts =
            new ConcurrentHashMap<>();

    /** The primary VerdictRegistry to subscribe to. */
    private final VerdictRegistry primary;

    /** The registry's public key, used to verify incoming RegistryVerdict signatures. */
    private final PublicKey primaryPublicKey;

    /** JCA algorithm used by the primary to sign RegistryVerdict objects. */
    private final String sigAlgorithm;

    /** Exporter used to make this RemoteEventListener callable by the primary. */
    private final Exporter exporter;

    /** Requested lease duration when registering with the primary. */
    private final long leaseDuration;

    /** Keeps the subscription lease alive. */
    private final LeaseRenewalManager leaseManager;

    /** The exported stub (this object as seen by remote callers). */
    private volatile Remote stub;

    /** The current event registration with the primary. */
    private volatile EventRegistration registration;

    /** True once the initial burst delivery has been fully processed. */
    private volatile boolean ready;

    /** Last observed sequence number (for gap detection). */
    private final AtomicLong lastSeqNum = new AtomicLong(-1L);

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code ReadReplicaVerdictRegistry} with a default
     * {@link BasicJeriExporter} (TCP, OS-assigned port) and a lease duration
     * of {@link Lease#FOREVER}.
     *
     * @param primary          the primary VerdictRegistry to replicate; must
     *                         not be {@code null}
     * @param primaryPublicKey the registry's public key for signature
     *                         verification; must not be {@code null}
     * @param sigAlgorithm     JCA algorithm name (e.g. {@code "SHA256withRSA"});
     *                         must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public ReadReplicaVerdictRegistry(VerdictRegistry primary,
                                      PublicKey primaryPublicKey,
                                      String sigAlgorithm) {
        this(primary, primaryPublicKey, sigAlgorithm,
                new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
                        new BasicILFactory(), false, true),
                Lease.FOREVER);
    }

    /**
     * Creates a {@code ReadReplicaVerdictRegistry} with a custom exporter and
     * lease duration.
     *
     * @param primary          the primary VerdictRegistry to replicate; must
     *                         not be {@code null}
     * @param primaryPublicKey the registry's public key for signature
     *                         verification; must not be {@code null}
     * @param sigAlgorithm     JCA algorithm name (e.g. {@code "SHA256withRSA"});
     *                         must not be {@code null}
     * @param exporter         the exporter to make this listener callable from
     *                         the primary; must not be {@code null}
     * @param leaseDuration    requested lease duration in milliseconds, or
     *                         {@link Lease#FOREVER}
     * @throws NullPointerException if {@code primary}, {@code primaryPublicKey},
     *                              {@code sigAlgorithm}, or {@code exporter} is
     *                              {@code null}
     */
    public ReadReplicaVerdictRegistry(VerdictRegistry primary,
                                      PublicKey primaryPublicKey,
                                      String sigAlgorithm,
                                      Exporter exporter,
                                      long leaseDuration) {
        if (primary == null)          throw new NullPointerException("primary");
        if (primaryPublicKey == null) throw new NullPointerException("primaryPublicKey");
        if (sigAlgorithm == null)     throw new NullPointerException("sigAlgorithm");
        if (exporter == null)         throw new NullPointerException("exporter");
        this.primary          = primary;
        this.primaryPublicKey = primaryPublicKey;
        this.sigAlgorithm     = sigAlgorithm;
        this.exporter         = exporter;
        this.leaseDuration    = leaseDuration;
        this.leaseManager     = new LeaseRenewalManager();
    }

    /**
     * Package-private constructor for unit tests that supply a custom
     * {@link LeaseRenewalManager}.
     */
    ReadReplicaVerdictRegistry(VerdictRegistry primary,
                               PublicKey primaryPublicKey,
                               String sigAlgorithm,
                               Exporter exporter,
                               long leaseDuration,
                               LeaseRenewalManager leaseManager) {
        if (primary == null)          throw new NullPointerException("primary");
        if (primaryPublicKey == null) throw new NullPointerException("primaryPublicKey");
        if (sigAlgorithm == null)     throw new NullPointerException("sigAlgorithm");
        if (exporter == null)         throw new NullPointerException("exporter");
        if (leaseManager == null)     throw new NullPointerException("leaseManager");
        this.primary          = primary;
        this.primaryPublicKey = primaryPublicKey;
        this.sigAlgorithm     = sigAlgorithm;
        this.exporter         = exporter;
        this.leaseDuration    = leaseDuration;
        this.leaseManager     = leaseManager;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Exports this listener, subscribes to the primary for all verdicts, and
     * sets the {@link #isReady()} flag once the initial burst delivery has
     * been submitted.
     *
     * <p>Calling {@code start()} more than once without an intervening
     * {@link #stop()} is a no-op.
     *
     * @throws IOException if exporting this listener or registering with the
     *                     primary fails
     */
    public synchronized void start() throws IOException {
        if (stub != null) {
            logger.fine("ReadReplicaVerdictRegistry.start() called but already started; ignoring.");
            return;
        }
        stub = exporter.export(this);
        logger.fine("ReadReplicaVerdictRegistry exported; subscribing to primary.");

        registration = primary.registerGlobalVerdictListener(
                (RemoteEventListener) stub, null, leaseDuration);

        leaseManager.renewFor(registration.getLease(), leaseDuration, this);

        ready = true;
        logger.fine("ReadReplicaVerdictRegistry subscribed; replica is ready.");
    }

    /**
     * Cancels the subscription lease and unexports this listener.
     *
     * <p>After {@code stop()} returns no further {@link #notify} callbacks
     * will be invoked.  Calling {@code stop()} when not started is a no-op.
     */
    public synchronized void stop() {
        if (stub == null) return;

        if (registration != null) {
            try {
                leaseManager.cancel(registration.getLease());
            } catch (UnknownLeaseException | RemoteException e) {
                logger.log(Level.FINE,
                        "Exception cancelling verdict replica subscription lease; ignoring.", e);
            }
            registration = null;
        }

        try {
            exporter.unexport(true);
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception unexporting ReadReplicaVerdictRegistry; ignoring.", e);
        }
        stub  = null;
        ready = false;
        logger.fine("ReadReplicaVerdictRegistry stopped.");
    }

    /**
     * Returns {@code true} if the initial burst delivery from the primary has
     * been processed and this replica is ready to serve reads.
     *
     * @return {@code true} if ready
     */
    public boolean isReady() {
        return ready;
    }

    // -------------------------------------------------------------------------
    // RemoteEventListener
    // -------------------------------------------------------------------------

    /**
     * Called by the primary registry whenever a new {@link RegistryVerdict} is
     * published.
     *
     * <p>This method:
     * <ol>
     *   <li>Casts the event to {@link VerdictEvent}; ignores unknown types.</li>
     *   <li>Verifies the DER signature on the embedded {@link RegistryVerdict}
     *       against {@link #primaryPublicKey}; silently discards on failure.</li>
     *   <li>Stores the verdict in the appropriate local cache keyed by
     *       codebase URL set or SHA-256 content hash.</li>
     * </ol>
     *
     * @param event the remote event; should be a {@link VerdictEvent}
     */
    @Override
    public void notify(RemoteEvent event) throws RemoteException {
        if (!(event instanceof VerdictEvent)) {
            logger.warning("ReadReplicaVerdictRegistry.notify() received unexpected event type: "
                    + event.getClass().getName() + "; ignoring.");
            return;
        }

        long seqNum = event.getSequenceNumber();
        long prev   = lastSeqNum.getAndSet(seqNum);
        if (prev >= 0 && seqNum > prev + 1) {
            logger.warning("ReadReplicaVerdictRegistry: sequence gap detected — expected "
                    + (prev + 1) + " but received " + seqNum
                    + "; missed " + (seqNum - prev - 1) + " verdict(s).");
        }

        RegistryVerdict rv = ((VerdictEvent) event).getVerdict();
        if (!verifyVerdictSignature(rv)) {
            logger.warning("ReadReplicaVerdictRegistry: signature verification FAILED for "
                    + rv.getCodebaseUrls() + "; discarding verdict.");
            return;
        }

        storeVerdict(rv);
    }

    // -------------------------------------------------------------------------
    // LeaseListener
    // -------------------------------------------------------------------------

    /**
     * Called by the {@link LeaseRenewalManager} when the subscription lease
     * cannot be renewed.
     *
     * <p>If the cause is an {@link UnknownLeaseException} (e.g. after a
     * primary restart) this method triggers a re-subscription attempt on a
     * daemon thread.  Other failures are logged at {@link Level#SEVERE}.
     *
     * @param e the lease renewal failure event
     */
    @Override
    public void notify(LeaseRenewalEvent e) {
        Throwable cause = e.getException();
        if (cause instanceof UnknownLeaseException) {
            logger.warning(
                    "Verdict replica subscription lease lost (UnknownLeaseException); re-subscribing.");
            Thread t = new Thread(this::resubscribe, "verdict-replica-resubscribe");
            t.setDaemon(true);
            t.start();
        } else {
            logger.log(Level.SEVERE,
                    "ReadReplicaVerdictRegistry: subscription lease renewal failed; "
                    + "no further verdicts will be received until the application is restarted.",
                    cause);
        }
    }

    // -------------------------------------------------------------------------
    // VerdictRegistry — read operations (served from local cache)
    // -------------------------------------------------------------------------

    @Override
    public RegistryVerdict getVerdictByHash(String contentHash) throws RemoteException {
        if (contentHash == null) throw new NullPointerException("contentHash");
        if (contentHash.isEmpty()) throw new IllegalArgumentException("contentHash must not be empty");
        return hashPublishedVerdicts.get(contentHash);
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");
        String key = codebaseKey(codebaseUrls);
        return publishedVerdicts.get(key);
    }

    // -------------------------------------------------------------------------
    // VerdictRegistry — write operations (not supported on a replica)
    // -------------------------------------------------------------------------

    @Override
    public void registerAnalysisEngine(String engineId, PublicKey engineKey,
                                        String sigAlgorithm) throws RemoteException {
        throw new UnsupportedOperationException(
                "ReadReplicaVerdictRegistry is read-only; direct all write operations to the primary.");
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        throw new UnsupportedOperationException(
                "ReadReplicaVerdictRegistry is read-only; direct all write operations to the primary.");
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        throw new UnsupportedOperationException(
                "ReadReplicaVerdictRegistry is read-only; direct all write operations to the primary.");
    }

    @Override
    public void reportPinning(PinningReport report) throws RemoteException {
        throw new UnsupportedOperationException(
                "ReadReplicaVerdictRegistry is read-only; direct all write operations to the primary.");
    }

    @Override
    public void submitReport(String engineId, JarAnalysisReport report) throws RemoteException {
        throw new UnsupportedOperationException(
                "ReadReplicaVerdictRegistry is read-only; direct all write operations to the primary.");
    }

    // -------------------------------------------------------------------------
    // VerdictRegistry — event subscriptions (delegated to primary)
    // -------------------------------------------------------------------------

    @Override
    public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                     Set<Uri> codebaseUrls,
                                                     MarshalledInstance handback,
                                                     long leaseDuration)
            throws RemoteException {
        return primary.registerVerdictListener(listener, codebaseUrls, handback, leaseDuration);
    }

    @Override
    public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener,
                                                           MarshalledInstance handback,
                                                           long leaseDuration)
            throws RemoteException {
        return primary.registerGlobalVerdictListener(listener, handback, leaseDuration);
    }

    @Override
    public long renewEventLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException {
        return primary.renewEventLease(leaseId, duration);
    }

    @Override
    public void cancelEventLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException {
        primary.cancelEventLease(leaseId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Stores a verified {@link RegistryVerdict} in the appropriate local cache.
     *
     * <p>Hash-keyed verdicts (where the single codebase URL starts with
     * {@value #URN_SHA256_PREFIX}) are stored in {@link #hashPublishedVerdicts};
     * all others are stored in {@link #publishedVerdicts} under a canonical key.
     *
     * @param rv the verified verdict to store
     */
    private void storeVerdict(RegistryVerdict rv) {
        Set<Uri> uris = rv.getCodebaseUrls();
        if (uris.size() == 1) {
            String url = uris.iterator().next().toString();
            if (url.startsWith(URN_SHA256_PREFIX)) {
                String hash = url.substring(URN_SHA256_PREFIX.length());
                hashPublishedVerdicts.put(hash, rv);
                logger.fine("ReadReplicaVerdictRegistry: cached hash-keyed verdict for " + hash);
                return;
            }
        }
        String key = codebaseKey(uris);
        publishedVerdicts.put(key, rv);
        logger.fine("ReadReplicaVerdictRegistry: cached URL-keyed verdict for " + key);
    }

    /**
     * Verifies the DER signature on a {@link RegistryVerdict} against
     * {@link #primaryPublicKey}.
     *
     * @param rv the verdict to verify
     * @return {@code true} if the signature is valid
     */
    private boolean verifyVerdictSignature(RegistryVerdict rv) {
        try {
            Uri[] sortedUris = sortedUriArray(rv.getCodebaseUrls());
            byte[] canonical = canonicalBytesForRegistryVerdict(
                    sortedUris, rv.getVerdict(), rv.getTimestamp());
            return verify(primaryPublicKey, sigAlgorithm, canonical, rv.getSignature());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            logger.log(Level.WARNING,
                    "ReadReplicaVerdictRegistry: exception during signature verification; "
                    + "treating as invalid.", e);
            return false;
        }
    }

    /**
     * Produces the canonical bytes for a {@link RegistryVerdict} — identical
     * to the format used by {@code VerdictRegistryImpl} when signing verdicts.
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

    private static boolean verify(PublicKey key, String algorithm,
                                  byte[] data, byte[] signature)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(algorithm);
        sig.initVerify(key);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Builds a canonical codebase key from a URL set (NUL-separated sorted URIs).
     */
    static String codebaseKey(Set<Uri> codebaseUrls) {
        String[] sorted = new String[codebaseUrls.size()];
        int i = 0;
        for (Uri uri : codebaseUrls) {
            sorted[i++] = uri.toString();
        }
        Arrays.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (String s : sorted) {
            if (sb.length() > 0) sb.append('\0');
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Returns a URI array sorted by string value (same order as the primary).
     */
    private static Uri[] sortedUriArray(Set<Uri> codebaseUrls) {
        Uri[] arr = codebaseUrls.toArray(new Uri[0]);
        Arrays.sort(arr, (a, b) -> a.toString().compareTo(b.toString()));
        return arr;
    }

    /**
     * Attempts to re-subscribe after a lease loss using exponential backoff.
     * Runs on a daemon thread to avoid blocking the {@link LeaseRenewalManager}.
     */
    private void resubscribe() {
        long delay = 1_000L;
        while (true) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                synchronized (this) {
                    if (stub == null) return; // stopped

                    registration = primary.registerGlobalVerdictListener(
                            (RemoteEventListener) stub, null, leaseDuration);
                    leaseManager.renewFor(registration.getLease(), leaseDuration, this);
                }
                ready = true;
                logger.info("ReadReplicaVerdictRegistry: re-subscribed to primary after lease loss.");
                return;
            } catch (RemoteException e) {
                ready = false;
                logger.log(Level.WARNING,
                        "ReadReplicaVerdictRegistry: re-subscription attempt failed; retrying in "
                        + delay + " ms.", e);
                delay = Math.min(delay * 2, 60_000L);
            }
        }
    }
}
