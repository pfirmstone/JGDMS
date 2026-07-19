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
 * @since 3.1.1
 */
public final class SubProcessGrantOrchestrator {

    /** {@link VerdictPermissionMapper}'s {@code contentHash} is always SHA-256. */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final SubProcessAdminRegistry registry;

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
     *         {@link PolicyAdmin#grant})
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
}
