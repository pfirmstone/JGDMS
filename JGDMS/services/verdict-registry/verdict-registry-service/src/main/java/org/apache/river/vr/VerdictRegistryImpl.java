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
package org.apache.river.vr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.codebase.CrashReport;
import org.apache.river.api.codebase.RegistryVerdict;
import org.apache.river.api.codebase.SignedVerdict;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.codebase.VerdictType;
import org.apache.river.api.net.Uri;

/**
 * Server-side implementation of the {@link VerdictRegistry} service.
 *
 * <p>This is the authoritative, low-risk component in the safe-codebase
 * architecture.  It manages {@code BytecodeAnalysisEngine} registration,
 * applies quorum policy over submitted {@link SignedVerdict} objects, and
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
 * </ul>
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
     * Mutable per-codebase vote accumulator.  All fields are accessed only
     * while {@code synchronized (this)}.
     */
    private static final class VerdictState {
        /**
         * Sorted URL strings captured when the first verdict for this
         * codebase key arrives.  Used to reconstruct the {@link Uri} set when
         * re-evaluating after an engine revocation.
         */
        Uri[] codebaseUrls;

        /** Per-engine votes: engineId → VerdictType. */
        final Map<String, VerdictType> votes = new HashMap<String, VerdictType>();

        /** True once a DANGEROUS RegistryVerdict has been published. Permanent. */
        boolean dangerous;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code VerdictRegistryImpl}.
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

        // Remove the revoked engine's vote from every codebase state and
        // re-evaluate.  DANGEROUS verdicts are permanent (fail-safe), so only
        // SAFE re-evaluation is needed.
        for (Map.Entry<String, VerdictState> entry : verdictStates.entrySet()) {
            String codebaseKey = entry.getKey();
            VerdictState state = entry.getValue();
            synchronized (state) {
                if (state.votes.remove(engineId) != null && !state.dangerous) {
                    logger.log(Level.INFO,
                            "Removed vote from revoked engine {0} for key {1}",
                            new Object[]{engineId, codebaseKey});
                    RegistryVerdict rv = evaluateSafe(state);
                    if (rv != null) {
                        publishedVerdicts.put(codebaseKey, rv);
                    } else {
                        // Quorum is no longer met; retract any previously-published
                        // SAFE verdict so clients do not rely on a stale result.
                        publishedVerdicts.remove(codebaseKey);
                    }
                }
            }
        }
    }

    @Override
    public void submitVerdict(String engineId, SignedVerdict verdict) throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");
        if (verdict == null) throw new NullPointerException("verdict");

        EngineRegistration reg = engines.get(engineId);
        if (reg == null) {
            logger.log(Level.WARNING,
                    "Discarding verdict from unregistered/revoked engine: {0}", engineId);
            return;
        }

        // Verify the signature before accepting the verdict.
        try {
            byte[] canonical = canonicalBytesForVerdict(verdict);
            if (!verify(reg.publicKey, reg.sigAlgorithm, canonical, verdict.getSignature())) {
                logger.log(Level.WARNING,
                        "Invalid signature on verdict from engine {0}; discarding", engineId);
                return;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IOException e) {
            logger.log(Level.WARNING,
                    "Signature verification failed for engine " + engineId + "; discarding", e);
            return;
        }

        Set<Uri> codebaseUrls = verdict.getCodebaseUrls();
        String   codebaseKey  = codebaseKey(codebaseUrls);
        VerdictState state    = verdictStates.computeIfAbsent(
                codebaseKey, k -> new VerdictState());

        synchronized (state) {
            if (state.dangerous) {
                // Already locked in DANGEROUS; nothing more to do.
                return;
            }
            // Capture the URI set on first arrival.
            if (state.codebaseUrls == null) {
                state.codebaseUrls = sortedUriArray(codebaseUrls);
            }
            state.votes.put(engineId, verdict.getVerdict());

            if (verdict.getVerdict() == VerdictType.DANGEROUS) {
                RegistryVerdict rv = issueVerdict(state.codebaseUrls, VerdictType.DANGEROUS);
                if (rv != null) {
                    state.dangerous = true;
                    publishedVerdicts.put(codebaseKey, rv);
                    logger.log(Level.WARNING,
                            "Published DANGEROUS verdict (engine vote) for key: {0}",
                            codebaseKey);
                }
            } else {
                RegistryVerdict rv = evaluateSafe(state);
                if (rv != null) {
                    publishedVerdicts.put(codebaseKey, rv);
                    logger.log(Level.INFO,
                            "Published SAFE verdict for key: {0}", codebaseKey);
                }
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
            }
        }
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");
        return publishedVerdicts.get(codebaseKey(codebaseUrls));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Evaluates whether the current vote state has reached the SAFE quorum and,
     * if so, issues a signed {@link RegistryVerdict}.  Called with the
     * {@code VerdictState} lock held.
     *
     * @return a new {@link RegistryVerdict} if the quorum was reached, or
     *         {@code null} if it was not (or signing failed)
     */
    private RegistryVerdict evaluateSafe(VerdictState state) {
        if (state.codebaseUrls == null) {
            return null;
        }
        long safeCount = 0L;
        for (VerdictType v : state.votes.values()) {
            if (v == VerdictType.SAFE) {
                safeCount++;
            }
        }
        if (safeCount >= quorumMinimum) {
            return issueVerdict(state.codebaseUrls, VerdictType.SAFE);
        }
        return null;
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
     * Produces the canonical bytes for a {@link SignedVerdict} that are
     * verified against the submitting engine's public key.
     *
     * <p>Format:
     * <ol>
     *   <li>For each URL in lexicographic order:
     *       4-byte big-endian byte-length, then UTF-8 bytes.</li>
     *   <li>4-byte big-endian {@link VerdictType#ordinal()}.</li>
     *   <li>8-byte big-endian timestamp.</li>
     * </ol>
     */
    static byte[] canonicalBytesForVerdict(SignedVerdict verdict) throws IOException {
        String[] sorted = sortedUriStrings(verdict.getCodebaseUrls());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        for (String url : sorted) {
            byte[] b = url.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(verdict.getVerdict().ordinal());
        dos.writeLong(verdict.getTimestamp());
        dos.flush();
        return baos.toByteArray();
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
     * <p>Format mirrors {@link #canonicalBytesForVerdict}: sorted URLs,
     * verdict ordinal, timestamp.
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
}
