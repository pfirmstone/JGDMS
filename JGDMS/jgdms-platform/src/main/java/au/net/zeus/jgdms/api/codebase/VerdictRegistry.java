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
package au.net.zeus.jgdms.api.codebase;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Set;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.telemetry.PinningReport;

/**
 * Remote service interface for the Verdict Registry (VR).
 *
 * <p>The registry is the <em>authoritative</em> and <em>low-risk</em>
 * component in the safe-codebase architecture:
 * <ul>
 *   <li>It <strong>never</strong> downloads or parses bytecode.</li>
 *   <li>Its inputs are well-typed, signed data objects
 *       ({@link SignedVerdict} and {@link CrashReport}) received over
 *       authenticated Jini connections.</li>
 *   <li>It holds the private signing key that clients trust;
 *       {@link BytecodeAnalysisEngine} instances do <em>not</em> hold this
 *       key.</li>
 * </ul>
 *
 * <h2>BAE registration</h2>
 * Before a {@link BytecodeAnalysisEngine} can submit verdicts it must be
 * registered via {@link #registerAnalysisEngine}.  The registry stores the
 * engine's public key and uses it to verify the signatures on subsequent
 * {@link SignedVerdict} submissions.  Operators revoke a compromised engine
 * by calling {@link #revokeAnalysisEngine}.
 *
 * <h2>Verdict submission</h2>
 * Analysis engines call {@link #submitVerdict} after completing an analysis.
 * Phoenix crash reporters call {@link #reportCrash} directly, bypassing any
 * analysis engine.  Each {@link CrashReport} is treated as an implicit
 * {@link VerdictType#DANGEROUS} vote.
 *
 * <h2>Quorum policy</h2>
 * Before issuing a {@link VerdictType#SAFE} {@link RegistryVerdict} the
 * registry requires verdicts from at least a configured minimum number of
 * independent registered engines.  A single {@link VerdictType#DANGEROUS}
 * verdict from any source causes the registry to immediately publish a
 * {@link VerdictType#DANGEROUS} {@link RegistryVerdict} (fail-safe
 * semantics).
 *
 * <h2>Client interaction</h2>
 * Clients call {@link #getVerdict} to obtain the current
 * {@link RegistryVerdict} for a codebase URL set.  A client's security
 * policy only needs to grant trust to the registry's identity; no entry for
 * any individual {@link BytecodeAnalysisEngine} is required.
 *
 * @see BytecodeAnalysisEngine
 * @see SignedVerdict
 * @see CrashReport
 * @see RegistryVerdict
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public interface VerdictRegistry extends Remote {

    /**
     * Registers a {@link BytecodeAnalysisEngine} with this registry.
     *
     * <p>After registration the registry accepts {@link SignedVerdict}
     * submissions whose signatures can be verified against {@code engineKey}.
     * Calling this method a second time with the same {@code engineId} updates
     * the stored public key.
     *
     * @param engineId  a stable, registry-unique identifier for the engine
     *                  (e.g. a UUID string); must be non-null and non-empty
     * @param engineKey the engine's public key used to verify
     *                  {@link SignedVerdict} signatures; must be non-null
     * @param sigAlgorithm the JCA standard name of the signature algorithm
     *                  used by the engine (e.g. {@code "SHA256withRSA"});
     *                  must be non-null and non-empty
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void registerAnalysisEngine(String engineId,
                                 PublicKey engineKey,
                                 String sigAlgorithm) throws RemoteException;

    /**
     * Revokes a previously registered {@link BytecodeAnalysisEngine}.
     *
     * <p>After revocation the registry stops accepting {@link SignedVerdict}
     * submissions from the engine identified by {@code engineId}.  Any
     * {@link RegistryVerdict} that currently counts a verdict from the
     * revoked engine toward the quorum is invalidated and re-evaluated.
     *
     * @param engineId the identifier of the engine to revoke; must be
     *                 non-null and non-empty
     * @throws IllegalArgumentException if {@code engineId} is empty
     * @throws NullPointerException     if {@code engineId} is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void revokeAnalysisEngine(String engineId) throws RemoteException;

    /**
     * Submits a {@link SignedVerdict} from a registered
     * {@link BytecodeAnalysisEngine} to this registry.
     *
     * <p>The registry:
     * <ol>
     *   <li>Verifies the signature against the engine's registered public
     *       key.</li>
     *   <li>Applies the quorum policy to determine whether an authoritative
     *       {@link RegistryVerdict} can be issued or updated.</li>
     *   <li>If the verdict type is {@link VerdictType#DANGEROUS}, immediately
     *       publishes a {@code DANGEROUS} {@link RegistryVerdict} regardless
     *       of the quorum state.</li>
     * </ol>
     *
     * <p>Submissions from unregistered or revoked engines are silently
     * discarded.
     *
     * @param engineId the identifier of the submitting engine; must be
     *                 non-null and non-empty
     * @param verdict  the signed verdict to submit; must be non-null
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void submitVerdict(String engineId, SignedVerdict verdict) throws RemoteException;

    /**
     * Submits a {@link CrashReport} from a Phoenix crash reporter directly
     * to this registry.
     *
     * <p>The registry:
     * <ol>
     *   <li>Verifies the Phoenix identity signature against its configured
     *       Phoenix public key.</li>
     *   <li>Treats the crash as an implicit {@link VerdictType#DANGEROUS}
     *       vote for the affected codebase URLs.</li>
     *   <li>Immediately publishes a {@code DANGEROUS} {@link RegistryVerdict}
     *       for the affected codebase URLs.</li>
     * </ol>
     *
     * @param report the crash report; must be non-null
     * @throws NullPointerException if {@code report} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    void reportCrash(CrashReport report) throws RemoteException;

    /**
     * Submits a {@link PinningReport} from the JFR Telemetry Service (Host 5)
     * directly to this registry.
     *
     * <p>The registry treats confirmed carrier-thread pinning as evidence of
     * DoS-capable blocking behaviour and immediately publishes a
     * {@link VerdictType#DANGEROUS} {@link RegistryVerdict} for the affected
     * codebase URLs.
     *
     * <p>Authentication is handled entirely by the JERI mutual-authentication
     * layer (SPIFFE/SPIRE SVIDs).  The registry accepts {@code reportPinning}
     * calls only from the authorised telemetry SVID
     * ({@code spiffe://…/host/telemetry}).  No application-level signature is
     * required on the payload.
     *
     * @param report the pinning report; must be non-null
     * @throws NullPointerException if {@code report} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    void reportPinning(PinningReport report) throws RemoteException;

    /**
     * Registers a {@link RemoteEventListener} to receive a
     * {@link au.net.zeus.jgdms.vr.proxy.VerdictEvent} whenever an authoritative
     * {@link RegistryVerdict} is published for the specified codebase URLs.
     *
     * <p>If a verdict has already been published for the given URL set, the
     * listener receives it immediately (sequence number 1) before this call
     * returns.
     *
     * <p>The registration is protected by a lease.  The caller must renew the
     * lease before it expires using {@link #renewEventLease}, or cancel it
     * with {@link #cancelEventLease}.
     *
     * @param listener     the listener to notify; must be non-null
     * @param codebaseUrls the ordered set of RFC3986-normalised codebase URIs
     *                     to watch; must be non-null and non-empty
     * @param handback     opaque object returned unchanged in every
     *                     {@link au.net.zeus.jgdms.vr.proxy.VerdictEvent}
     *                     delivered to {@code listener}; may be {@code null}
     * @param leaseDuration the requested lease duration in milliseconds, or
     *                     {@link net.jini.core.lease.Lease#ANY}
     * @return an {@link EventRegistration} containing the event ID, the
     *         initial sequence number, and the granted lease
     * @throws IllegalArgumentException if {@code codebaseUrls} is empty
     * @throws NullPointerException     if {@code listener} or
     *                                  {@code codebaseUrls} is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    EventRegistration registerVerdictListener(RemoteEventListener listener,
                                              Set<Uri> codebaseUrls,
                                              MarshalledInstance handback,
                                              long leaseDuration)
            throws RemoteException;

    /**
     * Registers a listener to receive {@link au.net.zeus.jgdms.vr.proxy.VerdictEvent}
     * notifications for <em>every</em> verdict published by this registry,
     * regardless of codebase URL or content hash.
     *
     * <p>This is the primary subscription mechanism for
     * {@code ReadReplicaVerdictRegistry} instances, which must maintain a
     * complete copy of the primary registry's verdict state.  The registry
     * performs a <em>burst delivery</em> of all currently-held verdicts
     * immediately before this call returns, so the replica can bootstrap its
     * local cache from the first notification batch.
     *
     * <p>The registration is protected by a lease.  The caller must renew the
     * lease before it expires using {@link #renewEventLease}, or cancel it
     * with {@link #cancelEventLease}.
     *
     * @param listener      the listener to notify; must be non-null
     * @param handback      opaque object returned unchanged in every
     *                      {@link au.net.zeus.jgdms.vr.proxy.VerdictEvent}
     *                      delivered to {@code listener}; may be {@code null}
     * @param leaseDuration the requested lease duration in milliseconds, or
     *                      {@link net.jini.core.lease.Lease#ANY}
     * @return an {@link EventRegistration} containing the event ID, the
     *         initial sequence number, and the granted lease
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    EventRegistration registerGlobalVerdictListener(RemoteEventListener listener,
                                                    MarshalledInstance handback,
                                                    long leaseDuration)
            throws RemoteException;

    /**
     *
     * @param leaseId  the lease cookie returned by
     *                 {@link #registerVerdictListener}; must be non-null
     * @param duration the requested renewal duration in milliseconds, or
     *                 {@link net.jini.core.lease.Lease#ANY}
     * @return the duration actually granted (may be less than requested)
     * @throws UnknownLeaseException if the lease is unknown or has already
     *                               expired
     * @throws NullPointerException  if {@code leaseId} is {@code null}
     * @throws RemoteException       if a communication failure occurs
     */
    long renewEventLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException;

    /**
     * Cancels the event-listener lease identified by {@code leaseId}.
     *
     * @param leaseId the lease cookie returned by
     *                {@link #registerVerdictListener}; must be non-null
     * @throws UnknownLeaseException if the lease is unknown or has already
     *                               expired
     * @throws NullPointerException  if {@code leaseId} is {@code null}
     * @throws RemoteException       if a communication failure occurs
     */
    void cancelEventLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException;

    /**
     * Submits a {@link JarAnalysisReport} from a registered
     * {@link BytecodeAnalysisEngine} to this registry.
     *
     * <p>This is the preferred submission method for deployments that use the
     * push model ({@link BytecodeAnalysisEngine#analyzeJar}).  The registry:
     * <ol>
     *   <li>Verifies the engine signature embedded in the report against the
     *       engine's registered public key.</li>
     *   <li>Derives the aggregate {@link VerdictType} from the report using
     *       {@link JarAnalysisReport#deriveVerdictType()}.</li>
     *   <li>Applies the quorum policy and publishes a {@link RegistryVerdict}
     *       keyed by the report's content hash.</li>
     * </ol>
     *
     * @param engineId the identifier of the submitting engine; must be
     *                 non-null and non-empty
     * @param report   the signed analysis report to submit; must be non-null
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void submitReport(String engineId, JarAnalysisReport report) throws RemoteException;

    /**
     * Returns the current authoritative {@link RegistryVerdict} for the JAR
     * identified by its SHA-256 content hash.
     *
     * <p>This hash-keyed lookup is the primary client access method for
     * deployments that use the push-model pipeline.  The
     * {@code ProxyCodebaseSPI} on each client computes the hash of the JAR
     * before unmarshalling and calls this method to check for a verdict.
     *
     * <p>Returns {@code null} if the registry has not yet accumulated
     * sufficient reports from registered engines to issue an authoritative
     * result.
     *
     * @param contentHash the SHA-256 hex digest of the JAR to query;
     *                    must be non-null and non-empty
     * @return the current {@link RegistryVerdict}, or {@code null} if no
     *         authoritative verdict is available yet
     * @throws IllegalArgumentException if {@code contentHash} is empty
     * @throws NullPointerException     if {@code contentHash} is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    RegistryVerdict getVerdictByHash(String contentHash) throws RemoteException;

    /**
     * Returns the current authoritative {@link RegistryVerdict} for the
     * given set of codebase URLs.
     *
     * <p>Returns {@code null} if the registry has not yet accumulated
     * sufficient verdicts from registered engines to issue an authoritative
     * result (i.e. the quorum has not been reached and no crash has been
     * reported).
     *
     * <p>Clients should apply Jini {@code Integrity} and
     * {@code ServerAuthentication} constraints when calling this method to
     * ensure the response is genuinely from the registry and has not been
     * tampered with in transit.
     *
     * @param codebaseUrls the ordered set of RFC3986-normalised codebase URIs
     *        to query; must be non-null and non-empty
     * @return the current {@link RegistryVerdict}, or {@code null} if no
     *         authoritative verdict is available yet
     * @throws IllegalArgumentException if {@code codebaseUrls} is empty
     * @throws NullPointerException     if {@code codebaseUrls} is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException;
}
