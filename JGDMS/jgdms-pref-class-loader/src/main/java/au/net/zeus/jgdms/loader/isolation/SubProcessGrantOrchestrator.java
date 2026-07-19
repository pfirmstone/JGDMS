/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.policy.VerdictPermissionMapper;
import java.rmi.RemoteException;
import java.security.Permission;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;

/**
 * The caller-side orchestrating logic connecting a known SCAP/BAE verdict to
 * the isolated subprocess it applies to (task {@code SubProcessDynamicPolicy}
 * T4 / remaining-work T3): once a verdict is known for a smart proxy about to
 * run in a given subprocess, this computes the subprocess's permission
 * ceiling via {@link VerdictPermissionMapper#computeSubProcessCeiling} and
 * pushes it, verbatim, through {@link SubProcessAdminRegistry} to that exact
 * subprocess's {@link PolicyAdmin#grant}.
 *
 * <h2>Targeting is by canonical key only</h2>
 * The only way to reach a target's {@link PolicyAdmin} through this class is
 * {@link #applyVerdictCeiling}'s {@code targetKey} parameter, an
 * {@link IsolationPoolingKey} &mdash; the same canonical, order-insensitive,
 * TLS-identity-derived key {@link SubProcessAdminRegistry} is keyed on
 * (SOW-Smart-Proxy-Isolation-Remaining-Work.md &sect;2, third bullet). There is
 * deliberately no overload accepting a raw handle, a {@link SubProcessAdministrable}
 * reference, or any other spoofable target-id/string parameter: every dispatch
 * goes {@code targetKey -> registry.lookup(targetKey) -> admin.getSubProcessPolicyAdmin()
 * -> policyAdmin.grant(...)}, so an attacker controlling only a string cannot
 * redirect a grant to a subprocess it doesn't own. Resolving a forged/foreign
 * key still yields only the {@link SubProcessAdministrable} legitimately
 * registered under it (or nothing, if unregistered) &mdash; the registry itself
 * enforces that binding (see {@link SubProcessAdminRegistry}), and the
 * accessor it returns is independently fail-closed to non-admin callers (see
 * {@link SubProcessPolicyAdmin}), so this class adds no bypass of either
 * layer.
 *
 * <h2>No independent judgment about what to grant</h2>
 * This class performs no filtering, widening, or narrowing of the ceiling
 * {@link VerdictPermissionMapper#computeSubProcessCeiling} computes; the exact
 * array it returns is what gets wrapped and pushed. The one and only
 * transformation applied is <em>representational</em>, not judgment: the raw
 * {@code Permission[]} is wrapped in a {@link PermissionGrant} so it can be
 * handed to {@link PolicyAdmin#grant(PermissionGrant)}, using the existing
 * {@link PermissionGrantBuilder#DIGEST DIGEST} grant context already used
 * elsewhere in this codebase for exactly this shape of cross-process,
 * content-hash-scoped, ClassLoader-reference-free grant (see e.g.
 * {@code PreferredProxyCodebaseProvider.tryGrantPerUriDigestGrants},
 * {@code DefaultPolicyParser}) &mdash; the same {@code contentHash} the ceiling
 * was computed for is the digest the resulting grant is scoped to, so the
 * subprocess's own policy only ever applies the ceiling to the JAR the
 * verdict was actually about.
 *
 * <p><b>Provisional, revisit at T1 integration:</b> the {@code DIGEST} context
 * choice above is this class's own representational decision, made because it
 * is the only existing {@link PermissionGrantBuilder} context that scopes a
 * grant to a JAR's content hash without needing a live cross-process
 * {@code ClassLoader}/{@code ProtectionDomain} reference &mdash; it is
 * <em>not</em> mandated by either SOW. It is only useful once the real T1
 * {@link PolicyAdmin} backing's {@code DynamicPolicyProvider} actually installs
 * hosted-proxy {@code ProtectionDomain}s under a matching
 * {@code DigestCodeSource}; if T1's real backing scopes domains a different
 * way (e.g. purely by principal, since the isolation pool is already one
 * subprocess per principal), this context choice must change to match, or the
 * pushed grant will be correctly delivered but silently imply nothing for the
 * hosted proxy's actual domain. Confirm this against T1's real shape before
 * treating T3+T1 as integrated; do not assume this choice is settled.
 *
 * <h2>Lease-scoping is not this class's job</h2>
 * {@code SOW-SubProcessDynamicPolicy.md} &sect;2 decides grants should be
 * lease-scoped by default via {@code LeasedPermissionGrant}/
 * {@code LeasedDelegation}; per that SOW's T1 (remaining-work T1), that
 * decorating happens where the grant is <em>applied</em> to the subprocess's
 * own local {@code DynamicPolicyProvider}, inside the real {@link PolicyAdmin}
 * backing &mdash; not here. This class hands the backing exactly the
 * ceiling-scoped {@link PermissionGrant}; whether/how it further wraps that
 * for lease semantics before installing it locally is the backing's concern,
 * consistent with T1's "apply verbatim, no independent judgment" contract
 * cutting both ways: this class does not pre-empt that decision either.
 *
 * <h2>Anti-replay / freshness gate (T4 adversarial-pass Finding&nbsp;2)</h2>
 * A reviewer proved that resubmitting a byte-for-byte identical {@link
 * #applyVerdictCeiling} call &mdash; same {@code targetKey}, same {@code
 * verdict}, same {@code contentHash} &mdash; silently reinstates a ceiling
 * that had already, correctly, expired: neither this class nor {@link
 * PolicyAdmin}'s real backing had any freshness/nonce/sequence check on the
 * grant-application call itself. {@link SubProcessLocalPolicyAdmin}'s own
 * Finding&nbsp;1 fix (supersede a prior <em>live</em> grant to the same
 * target) cannot close this by itself: once the earlier grant has already
 * gone void, there is nothing left for supersession to find and cancel, so a
 * replay of the expired bytes is indistinguishable, at that layer, from a
 * genuinely new first-time grant.
 *
 * <p>This class is the only layer with a usable freshness signal:
 * {@link RegistryVerdict#getTimestamp()} is part of the registry's own signed
 * content (see {@link RegistryVerdict#verifySignature}) &mdash; a replayed
 * verdict carries the identical signed timestamp it always did, which a
 * replaying party cannot advance without the registry's private key, while a
 * genuinely fresh re-verdict (a real re-analysis, issued later) is signed
 * with a strictly later timestamp. {@link #applyVerdictCeiling} therefore
 * tracks, per exact {@code (targetKey, contentHash)} pair, the timestamp of
 * the last verdict it has <em>successfully</em> applied, and refuses (fails
 * closed, {@link SecurityException}) any call whose verdict timestamp is not
 * <em>strictly greater</em> than that recorded value &mdash; before ever
 * calling down into {@link PolicyAdmin#grant}, so a replay never reaches T1
 * at all. A pair with no recorded generation yet (first-ever application, or
 * a genuinely different target/contentHash) is always accepted: this is not
 * a global monotonic clock, it is scoped exactly to the target+content pair
 * {@code impliesEquivalent} scopes supersession to at T1, so two independent,
 * concurrent first-time grants to different targets (or different JARs on the
 * same target) never contend. The recorded generation is advanced only
 * <em>after</em> {@link PolicyAdmin#grant} returns successfully &mdash; an
 * attempt refused upstream (target not registered, caller not authenticated
 * as the admin principal, {@code GrantPermission} ceiling denial, a {@code
 * DANGEROUS} verdict) never consumes the freshness slot, so a legitimate
 * retry of the very same verdict after a transient failure is not itself
 * mistaken for a replay.
 *
 * <p><strong>What this gate does not claim to solve, by design:</strong> two
 * genuinely concurrent calls carrying the identical verdict timestamp for the
 * identical target+content pair can both pass the freshness check before
 * either has recorded its generation (a narrow check-then-act race on the
 * generation map). This is judged acceptable for the current, single
 * orchestrating-controller call pattern this class is built for (verdict
 * application is not a high-frequency path, and the realistic adversarial
 * replay scenario this closes is sequential &mdash; captured bytes resent
 * later &mdash; not a true concurrent race against the legitimate caller);
 * see this class's javadoc note on {@link #lastAppliedGenerationMillis} for
 * the residual-risk detail and the follow-on hardening (atomic
 * reserve-then-commit) it would take to remove even that narrow window.
 * Separately, the per-{@code (targetKey, contentHash)} generation map is
 * unbounded for the lifetime of this instance &mdash; there is no hook here
 * for reclaiming an entry when a subprocess is torn down and unregistered
 * from {@link SubProcessAdminRegistry}; a bounded/LRU map or an
 * unregistration callback is a reasonable, currently-unbuilt follow-on if
 * this orchestrator is deployed as a long-lived singleton across many
 * subprocess lifecycles.
 *
 * @since 3.1.1
 */
public final class SubProcessGrantOrchestrator {

    /** {@link VerdictPermissionMapper}'s {@code contentHash} is always SHA-256. */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final SubProcessAdminRegistry registry;

    /**
     * The last-applied verdict generation (signed {@link
     * RegistryVerdict#getTimestamp()}) per exact {@code (targetKey,
     * contentHash)} pair this orchestrator has successfully pushed a ceiling
     * for &mdash; the anti-replay gate's only state. See the class javadoc
     * "Anti-replay / freshness gate" section for the full rationale.
     *
     * <p><strong>Residual risk (documented for board scrutiny):</strong> the
     * read-check (in {@link #applyVerdictCeiling}) and the write-update
     * (after a successful {@link PolicyAdmin#grant}) are two separate
     * operations on this map, not one atomic reserve-then-commit. Two truly
     * concurrent calls carrying the same verdict for the same target+content
     * pair could both observe "no conflicting generation yet" before either
     * writes, and both proceed. Hardening this fully would mean reserving the
     * generation atomically via {@link ConcurrentMap#compute} before
     * resolving the target/calling {@link PolicyAdmin#grant}, and rolling the
     * reservation back if that downstream call then fails &mdash; deliberately
     * not built here, because the realistic threat this gate defends against
     * (a captured, byte-identical verdict resent later, after the original
     * had already expired) is sequential, not concurrent, and the current
     * call pattern is a single orchestrating controller, not adversarial
     * concurrent submission.
     */
    private final ConcurrentMap<VerdictGenerationKey, Long> lastAppliedGenerationMillis =
            new ConcurrentHashMap<VerdictGenerationKey, Long>();

    /**
     * @param registry the management-plane registry to resolve targets
     *        through; must not be {@code null}
     */
    public SubProcessGrantOrchestrator(SubProcessAdminRegistry registry) {
        if (registry == null) {
            throw new NullPointerException("registry");
        }
        this.registry = registry;
    }

    /**
     * Computes the permission ceiling for a verdict and pushes it, verbatim,
     * to the subprocess registered under {@code targetKey}.
     *
     * <p>Steps, in order:
     * <ol>
     *   <li>{@link VerdictPermissionMapper#computeSubProcessCeiling} computes
     *       the ceiling &mdash; the only place any judgment about <em>what</em>
     *       to grant happens;</li>
     *   <li>{@code targetKey} is resolved through {@link SubProcessAdminRegistry#lookup}
     *       &mdash; the only lookup path this class offers;</li>
     *   <li>{@link SubProcessAdministrable#getSubProcessPolicyAdmin()} is
     *       called on the resolved handle &mdash; its own fail-closed
     *       admin-principal authentication applies unchanged, this class does
     *       not (and cannot) weaken it;</li>
     *   <li>the ceiling is wrapped in a {@code DIGEST}-context
     *       {@link PermissionGrant} scoped to {@code contentHash} and handed to
     *       {@link PolicyAdmin#grant(PermissionGrant)} unmodified.</li>
     * </ol>
     *
     * @param targetKey the canonical pooling key of the subprocess to target;
     *        must not be {@code null}
     * @param verdict the (already signature-verified) authoritative verdict;
     *        see {@link VerdictPermissionMapper#computeSubProcessCeiling} for
     *        its own preconditions
     * @param contentHash lower-case SHA-256 hex digest of the JAR the ceiling
     *        is being computed and grant-scoped for; must not be {@code null}
     *        or empty
     * @param declaredNeeds the proxy's own declared needs; may be {@code null}
     *        or empty
     * @param callerGrantCeiling the {@link GrantPermission} the orchestrating
     *        party itself holds; bounds the computed ceiling
     * @param inconclusiveToleranceGranted whether the orchestrator holds an
     *        {@code INCONCLUSIVEPermit} for {@code contentHash}; see
     *        {@link VerdictPermissionMapper#computeSubProcessCeiling}'s own
     *        documentation &mdash; must be sourced from a real permit check,
     *        never hard-coded {@code true}
     * @return the computed ceiling that was pushed (never {@code null},
     *         possibly empty) &mdash; returned so a caller/test can assert on
     *         exactly what was granted without re-deriving it
     * @throws NullPointerException if {@code targetKey} is {@code null}
     * @throws IllegalStateException if no subprocess is registered under
     *         {@code targetKey} (fail closed: never silently no-ops, never
     *         guesses a different target)
     * @throws RemoteException if {@code getSubProcessPolicyAdmin()} or
     *         {@code grant()} fails at the transport layer
     * @throws SecurityException if the resolved admin surface refuses this
     *         caller (propagated unchanged from
     *         {@link SubProcessAdministrable#getSubProcessPolicyAdmin()} /
     *         {@link PolicyAdmin#grant}), or if {@code verdict}'s signed
     *         timestamp is not strictly newer than the last verdict
     *         successfully applied for this exact {@code (targetKey,
     *         contentHash)} pair (fail-closed anti-replay/staleness guard;
     *         see class javadoc "Anti-replay / freshness gate")
     */
    public Permission[] applyVerdictCeiling(
            IsolationPoolingKey targetKey,
            RegistryVerdict verdict,
            String contentHash,
            Permission[] declaredNeeds,
            GrantPermission callerGrantCeiling,
            boolean inconclusiveToleranceGranted) throws RemoteException {
        if (targetKey == null) {
            throw new NullPointerException("targetKey");
        }

        // (1) The only judgment about *what* to grant happens here, in the
        // already-reviewed, already-tested mapper -- not in this class.
        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict, contentHash, declaredNeeds, callerGrantCeiling,
                inconclusiveToleranceGranted);

        // (1.5) T4 adversarial-pass Finding 2: fail closed on a stale or
        // replayed verdict application before ever resolving a target or
        // calling down into PolicyAdmin.grant -- a replay never reaches T1.
        // The check is read-only here; the map is advanced only after the
        // grant actually succeeds (step 5 below), so a call refused further
        // down (unregistered target, failed admin authentication, exceeded
        // GrantPermission ceiling) never consumes the freshness slot.
        VerdictGenerationKey genKey = new VerdictGenerationKey(targetKey, contentHash);
        long candidateGeneration = verdict.getTimestamp();
        Long priorGeneration = lastAppliedGenerationMillis.get(genKey);
        if (priorGeneration != null && candidateGeneration <= priorGeneration) {
            throw new SecurityException(
                "Refusing to apply verdict ceiling: verdict timestamp "
                + candidateGeneration + " is not strictly newer than the"
                + " last-applied verdict timestamp " + priorGeneration
                + " for pooling key " + targetKey + " / contentHash "
                + contentHash + " -- refusing a stale or replayed verdict"
                + " application (fail-closed; a genuinely fresh re-verdict is"
                + " signed with a strictly later timestamp).");
        }

        // (2) Canonical-key-only resolution: no other path to a target in
        // this class.
        SubProcessAdministrable admin = registry.lookup(targetKey);
        if (admin == null) {
            throw new IllegalStateException(
                "Refusing to apply verdict ceiling: no subprocess registered"
                + " under pooling key " + targetKey + " (fail-closed; never"
                + " guessing a different target).");
        }

        // (3) Reached only via the fail-closed accessor; its own
        // admin-principal authentication is unchanged and unbypassed.
        PolicyAdmin policyAdmin = admin.getSubProcessPolicyAdmin();

        // (4) Representational wrap only -- the permissions granted are
        // exactly `ceiling`, unfiltered, digest-scoped to the same JAR the
        // ceiling was computed for.
        PermissionGrant grant = PermissionGrantBuilder.newBuilder()
                .context(PermissionGrantBuilder.DIGEST)
                .digest(DIGEST_ALGORITHM, hexToBytes(contentHash))
                .permissions(ceiling)
                .build();

        policyAdmin.grant(grant);

        // (5) Only a *successful* application advances the freshness
        // generation; merge with max() as cheap, monotone protection against
        // a lost-update race with another thread that concurrently applied a
        // still-newer verdict for the same pair while this call was in
        // flight (see the documented residual check-then-act race on
        // lastAppliedGenerationMillis).
        lastAppliedGenerationMillis.merge(
                genKey, candidateGeneration, (a, b) -> a > b ? a : b);
        return ceiling;
    }

    /**
     * Decodes a lower-case (or upper-case) hex digest string into raw bytes.
     *
     * @param hex the hex string; must have even length and consist only of
     *        hex digits
     * @return the decoded bytes
     * @throws IllegalArgumentException if {@code hex} is malformed
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) {
            throw new IllegalArgumentException(
                "contentHash hex value must have even length: " + hex);
        }
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException(
                    "contentHash is not valid hex: " + hex);
            }
            result[i / 2] = (byte) ((hi << 4) + lo);
        }
        return result;
    }

    /**
     * The key {@link #lastAppliedGenerationMillis} is scoped by: the exact
     * canonical pooling key together with the (case-insensitively compared)
     * content hash. Matches the same granularity T1's own Finding&nbsp;1
     * supersession is scoped to ({@code impliesEquivalent} over the
     * principal-rebound, digest-scoped content template), so "same target"
     * means the same thing at both layers of this one coherent mechanism.
     */
    private static final class VerdictGenerationKey {
        private final IsolationPoolingKey targetKey;
        private final String contentHashLower;

        VerdictGenerationKey(IsolationPoolingKey targetKey, String contentHash) {
            this.targetKey = targetKey;
            this.contentHashLower =
                    contentHash == null ? null : contentHash.toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VerdictGenerationKey)) return false;
            VerdictGenerationKey k = (VerdictGenerationKey) o;
            return targetKey.equals(k.targetKey)
                    && (contentHashLower == null
                            ? k.contentHashLower == null
                            : contentHashLower.equals(k.contentHashLower));
        }

        @Override
        public int hashCode() {
            int h = targetKey.hashCode();
            h = 31 * h + (contentHashLower != null ? contentHashLower.hashCode() : 0);
            return h;
        }

        @Override
        public String toString() {
            return "VerdictGenerationKey{targetKey=" + targetKey
                    + ", contentHash=" + contentHashLower + "}";
        }
    }
}
