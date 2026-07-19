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

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.LeasedPermissionGrant;
import org.apache.river.api.security.OneShot;
import org.apache.river.api.security.OneShotLeasedPermissionGrant;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;

/**
 * The real grant-application backend behind {@link PolicyAdmin}
 * (task&nbsp;T1 of {@code SOW-Smart-Proxy-Isolation-Remaining-Work.md}, which
 * is {@code SubProcessDynamicPolicy} task&nbsp;T3, part&nbsp;(c)).
 *
 * <p><strong>Layering.</strong> This class is the {@code backing} object
 * {@link SubProcessPolicyAdmin} is constructed with &mdash; it is never
 * itself exported, never itself a {@code Remote}, and carries none of the
 * authentication/dispatch logic that class and {@link
 * AdminPrincipalAuthenticator} already implement and have already been
 * board-reviewed for. The <em>only</em> supported way to reach an instance
 * of this class is {@code method.invoke(backing, args)} inside {@code
 * SubProcessPolicyAdmin}'s {@code GuardedPolicyAdminHandler}, which has
 * already re-checked the caller is authenticated as the orchestrating admin
 * principal before this class's methods are ever entered. This class
 * performs <strong>no authentication of its own</strong> &mdash; it trusts
 * that every call it receives already passed that gate, exactly the shape
 * the test double it replaces ({@code
 * IsolationSecurityCriticalTest.TrustedPolicyBacking}) demonstrated.
 * <strong>Package-private by construction:</strong> neither the class nor
 * either constructor is {@code public} &mdash; only code inside {@code
 * au.net.zeus.jgdms.loader.isolation} (i.e. the trusted subprocess bootstrap
 * that constructs it as {@code SubProcessPolicyAdmin}'s {@code backing}) can
 * even reference this type, so the javadoc claim above is enforced by the
 * compiler, not merely stated.
 *
 * <p><strong>Content is verbatim; scope and lease shape are NOT trusted from
 * the caller.</strong> {@link #grant(PermissionGrant)} installs the given
 * grant's <em>content</em> &mdash; the {@code Permission[]} target, and
 * whatever digest/URI/certificate/classloader selector the caller's {@link
 * PermissionGrant} carries &mdash; exactly as given; it does not compute,
 * narrow, or widen <em>what</em> is being granted. That judgment already
 * happened in {@code VerdictPermissionMapper} (SubProcessDynamicPolicy T2,
 * landed) and the caller-side wiring that built the {@link PermissionGrant}
 * (SubProcessDynamicPolicy T4 / {@code SubProcessGrantOrchestrator}).
 *
 * <p>What this class <em>does</em> unconditionally own, and never leaves to
 * the caller (board finding, 2026-07-20 — a first version of this class
 * wrongly refused-on-mismatch instead, which was both a real hole and an
 * unnecessary point of friction with the caller-side wiring's own,
 * independently-reasonable representational choices):
 * <ol>
 *   <li><strong>Principal rebinding.</strong> Every installed grant is
 *       rebuilt, via the grant's own {@link PermissionGrant#getBuilderTemplate()}
 *       (which works uniformly across every concrete grant shape — {@code
 *       PrincipalGrant}/{@code CertificateGrant}/{@code URIGrant}/{@code
 *       DigestGrant}/{@code ClassLoaderGrant}/{@code ProtectionDomainGrant} —
 *       and which {@link LeasedPermissionGrant#getBuilderTemplate()}
 *       transparently unwraps to the wrapped content grant's own template),
 *       with {@link PermissionGrantBuilder#principals(Principal[])} always
 *       overridden to this backend's own configured {@code scopePrincipals}.
 *       This is never skipped, regardless of what principal scope (if any)
 *       the caller's grant already declared. <strong>Why this must not be a
 *       refuse-on-mismatch check instead:</strong> {@code
 *       PermissionGrant}/{@code LeasedPermissionGrant} place no requirement
 *       that a wrapped grant be principal-scoped at all — a {@code
 *       PermissionGrantBuilder.DIGEST}-context grant (the caller-side
 *       wiring's own current, independently-reasonable representational
 *       choice — content-hash scoping without needing a live cross-process
 *       {@code ClassLoader}/{@code ProtectionDomain} reference) legitimately
 *       carries {@code null}/empty principals, and {@code
 *       PrincipalGrant.implies(Principal[])} treats an empty/absent required
 *       set as <em>always satisfied</em> — i.e. "implies every protection
 *       domain". A caller is never required to pre-declare a matching
 *       principal scope (the interface doesn't say so, and the sibling task
 *       that builds these grants correctly does not), so refusing on
 *       mismatch would both miss the null/empty case (nothing to "mismatch"
 *       against) and reject legitimate DIGEST-scoped ceilings outright.
 *       Unconditional rebinding closes the universal-implies escalation for
 *       every grant shape at once, and is <em>content-preserving, not
 *       content-judging</em>: the permissions, digest, URI, certificates, and
 *       classloader/domain selector the caller supplied pass through
 *       untouched; only the principal-scope <em>slot</em>, which this backend
 *       alone is positioned to know the correct value of (the identity this
 *       specific subprocess is pooled under), is ever set.</li>
 *   <li><strong>Lease wrapping.</strong> If the caller's grant is already a
 *       {@link LeasedPermissionGrant} (or {@link OneShotLeasedPermissionGrant}),
 *       its existing {@link Lease} is reused &mdash; renewal ownership stays
 *       exactly where {@code LeasedDelegation} already keeps it, with
 *       whichever delegator/renewer holds that {@code Lease} reference, not
 *       with the caller's grant object. Otherwise a purely-local {@link
 *       Lease} (no remote landlord; see {@link LocalDeadManLease}) is minted
 *       with a configurable default TTL ({@link #DEFAULT_LEASE_TTL_MILLIS})
 *       and used instead. Every install is therefore lease-scoped, per
 *       {@code SubProcessDynamicPolicy} &sect;2's "lease-scoped by default"
 *       decision, <em>true by construction</em> rather than by refusing
 *       whatever the caller didn't already do itself.</li>
 *   <li><strong>Supersession of a prior grant to the same target (T4
 *       adversarial-pass Finding&nbsp;1).</strong> Before this method
 *       returns from a <em>successful</em> {@link
 *       DynamicPolicyProvider#grant(PermissionGrant)} install, it scans this
 *       backend's own small live-install index for any earlier grant it
 *       itself installed whose unwrapped content is {@linkplain
 *       PermissionGrant#impliesEquivalent(PermissionGrant) implies-equivalent}
 *       to the one just installed &mdash; i.e. carries the identical
 *       principal/digest/URI/certificate/classloader <em>selector</em>,
 *       regardless of which {@code Permission}s either grant carries &mdash;
 *       and calls {@link LeasedPermissionGrant#cancel()} on it. Cancellation
 *       flips that grant's {@code isVoid()} synchronously, so a narrower
 *       re-grant for the same target retires the earlier, broader one on the
 *       very next {@code implies()} check: no waiting for {@code refresh()}
 *       and no waiting out the earlier grant's own lease TTL, which is
 *       exactly the gap the board's reproduction (grant a broad {@code
 *       FilePermission("&lt;&lt;ALL FILES&gt;&gt;","read,write")} ceiling,
 *       then grant a narrower {@code PropertyPermission} ceiling for the same
 *       target, and watch the broad grant keep being enforced) exploited. The
 *       new install is never touched by this scan (it cannot match itself,
 *       having not yet been added to the index at scan time), and the scan
 *       runs strictly <em>after</em> the install succeeds, so a {@code
 *       grant()} call this backend's own {@code GrantPermission} ceiling
 *       denies never has the side effect of tearing down a grant that
 *       remains validly in force.</li>
 * </ol>
 * <p><strong>What supersession deliberately does <em>not</em> do (residual
 * scope, by design).</strong> It closes the <em>revocation</em> gap for a
 * grant still live when its replacement arrives. It cannot, by itself, close
 * a <em>replay</em> of a grant that has already gone void (expired or been
 * cancelled) &mdash; once void, there is no live prior install left to find
 * and cancel, so a byte-for-byte resend of old, already-expired grant bytes
 * would satisfy this mechanism's "no matching live prior" case and be
 * accepted as if it were new. This class has no freshness/generation signal
 * available to it &mdash; {@link #grant(PermissionGrant)}'s only input is the
 * already-shaped {@link PermissionGrant}, which carries no verdict timestamp
 * or sequence number. Closing that gap (T4 Finding&nbsp;2) is {@link
 * SubProcessGrantOrchestrator}'s responsibility, at the one layer upstream of
 * this class that still holds the {@code RegistryVerdict} the grant was
 * derived from &mdash; see that class's javadoc for the anti-replay
 * generation gate it applies <em>before</em> ever calling down into this
 * class's {@link #grant(PermissionGrant)}. A caller that reaches this class
 * directly, bypassing {@code SubProcessGrantOrchestrator} entirely, gets
 * supersession (Finding&nbsp;1) but not anti-replay (Finding&nbsp;2) &mdash;
 * this is not an oversight, it is the honest limit of what a bare {@code
 * PermissionGrant} can prove about its own freshness; only the party that
 * looks at a signed verdict can.
 *
 * <p>The existing {@code DynamicPolicyProvider.grant(PermissionGrant)} still
 * runs its own {@code GrantPermission} ceiling check against the calling
 * context on the stack at the moment this method executes (see {@code
 * RevocablePolicy#grant}); this class neither strengthens nor weakens that
 * check, it simply does not bypass it.
 *
 * <p><strong>{@link #refresh()} and {@link #getGrants()}.</strong> {@code
 * refresh()} delegates to {@link DynamicPolicyProvider#refresh()} verbatim
 * (and additionally sweeps this backend's own live-install index of any now-
 * void entries, purely to bound its size &mdash; {@code refresh()}'s own
 * void-grant sweep of {@code policy} is unaffected either way).
 *
 * <p>{@code getGrants()} (T4 adversarial-pass Finding&nbsp;2's "secondary
 * compounding gap") no longer queries {@link
 * DynamicPolicyProvider#getPermissionGrants(ProtectionDomain)} against a
 * synthetic, codesource-and-loader-free {@link java.security.ProtectionDomain}.
 * That query shape is structurally blind to any grant this backend installs
 * with a non-{@code PRINCIPAL} context: {@code DigestGrant}/{@code URIGrant}/
 * {@code CertificateGrant}/{@code ClassLoaderGrant} all require their own
 * selector (a {@code DigestCodeSource}, a matching URL, certificates, a live
 * {@code ClassLoader}) to be present on the queried domain before {@code
 * implies(ProtectionDomain)} can return {@code true} &mdash; a codesource-free
 * domain can never satisfy any of them, so a live, actively-enforced {@code
 * DigestGrant} (exactly {@link SubProcessGrantOrchestrator}'s own grant
 * shape) was silently reported as absent: an operator auditing "did my grant
 * take effect" got a false all-clear for the one grant shape T3 actually
 * produces. {@code getGrants()} instead returns this backend's own
 * live-install index, filtered to {@link PermissionGrant#isVoid() !isVoid()}:
 * the exact {@link PermissionGrant} object instances {@link
 * #grant(PermissionGrant)} itself passed to {@code policy.grant(...)}, so
 * {@code isVoid()}/lease-expiry is always evaluated fresh against the real,
 * live object &mdash; this is an <em>index of identity</em>, not a stale
 * snapshot copy of state: nothing about a tracked entry's liveness is cached
 * or memoized independently of the object it indexes. The index only ever
 * contains what this backend itself verbatim-installed (see {@link
 * #grant(PermissionGrant)}), so {@code getGrants()}'s audit surface now
 * matches enforcement exactly, for every grant shape this backend accepts,
 * not only the {@code PRINCIPAL}-context one the old query shape happened to
 * see.
 *
 * @since 3.1.1
 */
final class SubProcessLocalPolicyAdmin implements PolicyAdmin {

    /**
     * Default local dead-man-switch TTL applied to any grant that does not
     * already arrive lease-wrapped, per {@code SubProcessDynamicPolicy}
     * &sect;2's "lease-scoped by default" decision. Matches this module's
     * existing verdict-cache TTL default ({@code
     * PreferredProxyCodebaseProvider}'s {@code jgdms.proxy.verdictCacheTtlMs}
     * default of 300,000&nbsp;ms) &mdash; a conceptually similar "how long is
     * a computed security decision valid without being refreshed" duration.
     */
    static final long DEFAULT_LEASE_TTL_MILLIS = 300_000L; // 5 minutes

    private final DynamicPolicyProvider policy;
    private final Principal[] scopePrincipals;
    private final long defaultLeaseTtlMillis;

    /**
     * This backend's own live-install index (T4 adversarial-pass Finding&nbsp;1
     * / Finding&nbsp;2's audit-blindness fix): every grant this backend has
     * itself successfully installed into {@link #policy}, paired with the
     * unwrapped, principal-rebound content template used to test
     * "same target" via {@link PermissionGrant#impliesEquivalent(PermissionGrant)}.
     * A {@link CopyOnWriteArrayList} because reads ({@link #getGrants()}) are
     * far more frequent than writes ({@link #grant(PermissionGrant)}), reads
     * must never block on a write in progress, and the list is expected to
     * stay small (one entry per distinct target this backend has ever been
     * asked to grant to, minus whatever {@link #grant(PermissionGrant)} and
     * {@link #refresh()} opportunistically sweep). Mutations (the
     * find-prior-and-cancel-then-add sequence in {@link #grant(PermissionGrant)})
     * are additionally serialized under {@link #installLock} so a same-target
     * race between two concurrent {@code grant()} calls resolves
     * deterministically rather than leaving two live "winners".
     */
    private final List<TrackedGrant> tracked = new CopyOnWriteArrayList<TrackedGrant>();

    /** Serializes the mutating part of {@link #grant(PermissionGrant)}; see {@link #tracked}. */
    private final Object installLock = new Object();

    /**
     * One entry in {@link #tracked}: the exact, live {@link LeasedPermissionGrant}
     * (or {@link OneShotLeasedPermissionGrant}) instance {@link
     * #grant(PermissionGrant)} passed to {@link #policy}, paired with the
     * unwrapped content template (no lease, permissions included) used only
     * for the {@code impliesEquivalent} "same target" test &mdash; never
     * re-installed, never mutated.
     */
    private static final class TrackedGrant {
        final PermissionGrant contentTemplate;
        final LeasedPermissionGrant installed;

        TrackedGrant(PermissionGrant contentTemplate, LeasedPermissionGrant installed) {
            this.contentTemplate = contentTemplate;
            this.installed = installed;
        }
    }

    /**
     * Convenience constructor using {@link #DEFAULT_LEASE_TTL_MILLIS}.
     *
     * @param policy          the subprocess's own local dynamic policy
     *        provider; must not be null. This is the one and only authority
     *        this backend ever applies a grant to &mdash; never a remote or
     *        cross-process policy.
     * @param scopePrincipals the principal(s) this subprocess's delivered
     *        grants are scoped under (e.g. the remote SPIFFE identity {@code
     *        IsolationPoolingKey} pooled this subprocess on). Defensively
     *        copied; must not be null or empty &mdash; see {@link
     *        #SubProcessLocalPolicyAdmin(DynamicPolicyProvider, Principal[], long)}
     *        for why an empty scope is refused outright, not merely
     *        discouraged.
     */
    SubProcessLocalPolicyAdmin(DynamicPolicyProvider policy,
                               Principal[] scopePrincipals) {
        this(policy, scopePrincipals, DEFAULT_LEASE_TTL_MILLIS);
    }

    /**
     * @param policy               the subprocess's own local dynamic policy
     *        provider; must not be null.
     * @param scopePrincipals      the principal(s) every grant installed
     *        through this backend is unconditionally rebound to (see class
     *        docs). Defensively copied; must not be null or empty &mdash; an
     *        empty/absent principal array is not a "no restriction" no-op
     *        here, it is the exact shape {@code
     *        PrincipalGrant.implies(Principal[])} treats as "implies every
     *        protection domain"; accepting one would let this backend's own
     *        misconfiguration reproduce the universal-implies escalation
     *        {@link #grant(PermissionGrant)} exists to close, just via a
     *        different route than the caller's grant object. Fail closed at
     *        construction instead.
     * @param defaultLeaseTtlMillis the TTL, in milliseconds, of the purely
     *        local {@link Lease} minted for any installed grant that doesn't
     *        already arrive lease-wrapped; must be &gt; 0. This is a
     *        duration decision, not a "what to grant" decision &mdash; it
     *        does not cross into {@code VerdictPermissionMapper}'s
     *        territory.
     */
    SubProcessLocalPolicyAdmin(DynamicPolicyProvider policy,
                               Principal[] scopePrincipals,
                               long defaultLeaseTtlMillis) {
        if (policy == null) throw new NullPointerException("policy");
        if (scopePrincipals == null) {
            throw new NullPointerException("scopePrincipals");
        }
        if (scopePrincipals.length == 0) {
            throw new IllegalArgumentException(
                "scopePrincipals must not be empty: an empty/absent principal"
                + " array makes every PrincipalGrant-family grant"
                + " (PrincipalGrant/CertificateGrant/URIGrant/DigestGrant/"
                + "ClassLoaderGrant/ProtectionDomainGrant) imply EVERY"
                + " protection domain (PrincipalGrant.implies(Principal[])"
                + " treats an empty required-set as always-satisfied). This"
                + " backend must always have a concrete, non-empty identity"
                + " to rebind every installed grant to.");
        }
        if (defaultLeaseTtlMillis <= 0) {
            throw new IllegalArgumentException(
                "defaultLeaseTtlMillis must be > 0, was: " + defaultLeaseTtlMillis);
        }
        this.policy = policy;
        this.scopePrincipals = scopePrincipals.clone();
        this.defaultLeaseTtlMillis = defaultLeaseTtlMillis;
    }

    /**
     * Installs {@code grant} into the subprocess's local policy: content
     * verbatim, principal-scope always rebound to {@code scopePrincipals},
     * always lease-wrapped (the caller's own lease if it supplied one,
     * otherwise a freshly-minted local one). See class docs for the full
     * rationale, including the T4 adversarial-pass Finding&nbsp;1 supersession
     * this method now performs and its documented residual scope
     * (replay/freshness is {@link SubProcessGrantOrchestrator}'s job, not
     * this method's).
     *
     * @throws NullPointerException if {@code grant} is null
     * @throws SecurityException if the underlying {@code
     *         DynamicPolicyProvider}'s own {@code GrantPermission} ceiling
     *         check denies the calling context (an existing, unmodified
     *         check this class does not bypass)
     */
    @Override
    public void grant(PermissionGrant grant) throws RemoteException {
        if (grant == null) throw new NullPointerException("grant");

        boolean oneShot = grant instanceof OneShot;
        Lease lease = (grant instanceof LeasedPermissionGrant)
                ? ((LeasedPermissionGrant) grant).getLease()
                : mintLocalLease();

        // Rebuild via the grant's own builder template (uniform across every
        // concrete PermissionGrant shape; LeasedPermissionGrant's own
        // getBuilderTemplate() transparently unwraps to the wrapped content
        // grant's template), overriding principal-scope unconditionally.
        // Content (permissions, digest/URI/certs/classloader selector) is
        // otherwise untouched.
        PermissionGrant rebound = grant.getBuilderTemplate()
                .principals(scopePrincipals)
                .build();

        LeasedPermissionGrant install = oneShot
                ? new OneShotLeasedPermissionGrant(rebound, lease)
                : new LeasedPermissionGrant(rebound, lease);

        synchronized (installLock) {
            // Verbatim install of the rebound, lease-wrapped grant.
            // DynamicPolicyProvider.grant(PermissionGrant) still runs its own
            // GrantPermission ceiling check against the current calling
            // context; that check is neither strengthened nor weakened here.
            // This must happen BEFORE any supersession below: if the ceiling
            // check denies this install, nothing below may run either --
            // a rejected regrant attempt must never have the side effect of
            // tearing down a DIFFERENT grant that remains validly in force.
            policy.grant(install);

            // T4 adversarial-pass Finding 1: supersede any prior LIVE grant
            // this backend itself installed for the exact same target.
            // impliesEquivalent ignores permission content and compares only
            // the imply-logic selector, so this correctly matches "same
            // target, possibly re-verdicted permissions" regardless of
            // whether the new ceiling is broader or narrower than the old
            // one. Cancellation is synchronous (LeasedPermissionGrant#cancel())
            // so the very next implies() check no longer sees the superseded
            // grant -- no refresh() call and no lease-TTL wait required.
            for (TrackedGrant t : tracked) {
                if (!t.installed.isVoid()
                        && t.contentTemplate.impliesEquivalent(rebound)) {
                    t.installed.cancel();
                }
            }
            // Bound growth of the index: drop entries already void (superseded
            // just above, naturally lease-expired, or externally cancelled).
            tracked.removeIf(SubProcessLocalPolicyAdmin::isVoidTrackedGrant);
            tracked.add(new TrackedGrant(rebound, install));
        }
    }

    private Lease mintLocalLease() {
        return new LocalDeadManLease(
                System.currentTimeMillis() + defaultLeaseTtlMillis);
    }

    /**
     * Reloads / recomputes the effective subprocess policy by delegating to
     * the local {@code DynamicPolicyProvider}'s own {@code refresh()}
     * verbatim (also sweeps any now-void &mdash; e.g. lease-expired &mdash;
     * dynamic grants, per {@code DynamicPolicyProvider#refresh}). Also
     * opportunistically sweeps this backend's own {@link #tracked} index of
     * any now-void entries, purely to bound the index's size over a
     * long-running subprocess's lifetime; {@link #getGrants()} already
     * filters live-vs-void on every call regardless, so this sweep changes no
     * observable behaviour, only memory footprint.
     */
    @Override
    public void refresh() throws RemoteException {
        policy.refresh();
        tracked.removeIf(SubProcessLocalPolicyAdmin::isVoidTrackedGrant);
    }

    /** Removal predicate: a tracked entry whose installed grant has gone void. */
    private static boolean isVoidTrackedGrant(TrackedGrant t) {
        return t.installed.isVoid();
    }

    /**
     * Returns the grants this backend has itself installed and which remain
     * live, read from this backend's own {@link #tracked} index (T4
     * adversarial-pass Finding&nbsp;2's audit-blindness fix &mdash; see class
     * docs for why the previous codesource-free {@code
     * DynamicPolicyProvider.getPermissionGrants(ProtectionDomain)} query shape
     * could never see a {@code DigestGrant}/{@code URIGrant}/{@code
     * CertificateGrant}/{@code ClassLoaderGrant}). The returned array elements
     * are the exact live {@link PermissionGrant} instances installed into the
     * local {@code DynamicPolicyProvider} &mdash; {@link
     * PermissionGrant#isVoid()} (transitively, lease expiry or explicit
     * {@link LeasedPermissionGrant#cancel()}) is evaluated fresh at the moment
     * of this call, not cached, so this method can never report a grant as
     * live after it has actually gone void (or vice versa).
     *
     * @return a fresh, defensive-copy snapshot; never null, possibly empty
     */
    @Override
    public PermissionGrant[] getGrants() throws RemoteException {
        List<PermissionGrant> live = new ArrayList<PermissionGrant>(tracked.size());
        for (TrackedGrant t : tracked) {
            if (!t.installed.isVoid()) {
                live.add(t.installed);
            }
        }
        return live.toArray(new PermissionGrant[live.size()]);
    }

    /**
     * A purely-local {@link Lease} with no remote landlord: its sole purpose
     * is to give a caller-supplied, not-yet-leased {@link PermissionGrant}
     * the same dead-man-switch shape as an already-leased one, per &sect;2's
     * "lease-scoped by default" decision. Minted internally by {@link
     * #mintLocalLease()} and never handed back to any caller, so it can never
     * be renewed externally &mdash; it simply expires once, at
     * minting-time-plus-TTL, exactly the intended default ceiling lifetime
     * for a grant the caller didn't already lease itself.
     */
    private static final class LocalDeadManLease implements Lease {
        private final long expiration;

        LocalDeadManLease(long expiration) {
            this.expiration = expiration;
        }

        @Override
        public long getExpiration() {
            return expiration;
        }

        @Override
        public void cancel() {
            // No remote landlord to notify; LeasedPermissionGrant's own
            // cancel()/expiry check is the actual dead-man switch.
        }

        @Override
        public void renew(long duration) {
            throw new UnsupportedOperationException(
                "LocalDeadManLease is minted internally by "
                + SubProcessLocalPolicyAdmin.class.getName()
                + " and never handed to a caller; it cannot be renewed by"
                + " design (it is meant to expire once, at its default TTL).");
        }

        @Override
        public void setSerialFormat(int format) { }

        @Override
        public int getSerialFormat() {
            return Lease.DURATION;
        }

        @Override
        public LeaseMap<? extends Lease, Long> createLeaseMap(long duration) {
            return null;
        }

        @Override
        public boolean canBatch(Lease lease) {
            return false;
        }
    }
}
