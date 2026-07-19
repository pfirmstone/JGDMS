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

import java.lang.reflect.Constructor;
import java.rmi.RemoteException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.security.GrantPermission;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.LeasedPermissionGrant;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Adversarial probes for the real {@link PolicyAdmin} grant-application
 * backend (task&nbsp;T1 of {@code SOW-Smart-Proxy-Isolation-Remaining-
 * Work.md} / {@code SubProcessDynamicPolicy} T3(c)): {@link
 * SubProcessLocalPolicyAdmin}, wired through the already-board-reviewed
 * authentication layer ({@link SubProcessPolicyAdmin}, {@link
 * AdminPrincipalAuthenticator}) exactly as it will be in production &mdash;
 * never testing the backend in isolation from the gate it sits behind.
 *
 * <p>Mirrors the self-test probes the SOW specifies, plus the 2026-07-20
 * adversarial board's findings against the first version of this class:
 * <ul>
 *   <li>the real grant-application path is unreachable without the T2
 *       authentication gate ({@link #unauthenticated_neverReachesBacking}
 *       and {@link #wrongPrincipal_neverReachesBacking});</li>
 *   <li>a lease-scoped grant actually expires end-to-end, observed on the
 *       real local policy, not merely asserted ({@link
 *       #leaseScopedGrant_actuallyExpires_endToEnd});</li>
 *   <li>{@code refresh()}/{@code getGrants()} cannot leak grant state to an
 *       unauthenticated or de-authenticated caller ({@link
 *       #getGrants_reGates_capturedProxyCannotReadAfterDeauth});</li>
 *   <li><strong>Finding 2 closure:</strong> a grant with an empty/absent
 *       principal array (which {@code PrincipalGrant.implies(Principal[])}
 *       treats as "implies everything") is always rebound to this backend's
 *       own {@code scopePrincipals}, never installed as a universal grant
 *       ({@link #grant_emptyPrincipalArray_reboundToScope_notUniversal},
 *       {@link #grant_nullPrincipalArray_reboundToScope_notUniversal});</li>
 *   <li><strong>Finding 3 closure:</strong> a grant built exactly the way
 *       {@code SubProcessGrantOrchestrator} (T3) actually builds one
 *       (DIGEST-context, no principals, no lease wrapper) is accepted, not
 *       refused, and once rebound actually applies to the real
 *       digest-scoped, principal-scoped hosted-proxy domain it was computed
 *       for ({@link #grant_t3StyleDigestGrant_isAcceptedAndAppliesOnceRebound});</li>
 *   <li>a bare, unleased grant is accepted and auto-wrapped in a
 *       purely-local dead-man-switch lease with a configurable default TTL,
 *       which itself actually expires ({@link
 *       #grant_bareUnleasedGrant_isAutoLeaseWrapped_thenExpires}).</li>
 * </ul>
 */
public class SubProcessLocalPolicyAdminTest {

    private static Principal name(final String n) {
        return new Principal() {
            public String getName() { return n; }
            public boolean equals(Object o) {
                return o instanceof Principal && n.equals(((Principal) o).getName());
            }
            public int hashCode() { return n.hashCode(); }
            public String toString() { return n; }
        };
    }

    private static Subject subjectWith(Principal... ps) {
        Set<Principal> set = new LinkedHashSet<Principal>(Arrays.asList(ps));
        return new Subject(true, set, Collections.<Object>emptySet(),
                Collections.<Object>emptySet());
    }

    /** Injectable caller identity holder, same shape as the T2 test suite's. */
    static final class Caller implements AdminPrincipalAuthenticator.CallerIdentity {
        volatile Subject subject;
        public Subject current() { return subject; }
    }

    private static final class DenyAllBasePolicy extends Policy {
        @Override public PermissionCollection getPermissions(CodeSource codesource) {
            return new Permissions();
        }
        @Override public PermissionCollection getPermissions(ProtectionDomain domain) {
            return new Permissions();
        }
        @Override public boolean implies(ProtectionDomain domain, Permission permission) {
            return false;
        }
        @Override public void refresh() { }
    }

    /** Real-time lease double: expiration is an absolute wall-clock millisecond. */
    private static final class TestLease implements Lease {
        private volatile long expiration;
        volatile boolean cancelled;
        TestLease(long expiration) { this.expiration = expiration; }
        @Override public long getExpiration() { return expiration; }
        @Override public void cancel() { cancelled = true; }
        @Override public void renew(long duration) { expiration = System.currentTimeMillis() + duration; }
        @Override public void setSerialFormat(int format) { }
        @Override public int getSerialFormat() { return Lease.DURATION; }
        @Override public LeaseMap<? extends Lease, Long> createLeaseMap(long duration) { return null; }
        @Override public boolean canBatch(Lease lease) { return false; }
    }

    private static DynamicPolicyProvider newPolicy() {
        return new DynamicPolicyProvider(new DenyAllBasePolicy());
    }

    private static ProtectionDomain domain(Principal... pals) {
        return new ProtectionDomain(
                new CodeSource(null, (Certificate[]) null), null, null, pals);
    }

    /**
     * {@code DynamicPolicyProvider#getPermissionGrants(ProtectionDomain)}
     * always includes one entry representing the (possibly empty) base
     * policy's own grant for the domain, in addition to any dynamic grants
     * that imply it &mdash; so tests must check for the specific permission's
     * presence/absence, never assert on a bare array length.
     */
    private static boolean anyGrantCarries(PermissionGrant[] grants, Permission perm) {
        for (PermissionGrant g : grants) {
            if (g.getPermissions().contains(perm)) return true;
        }
        return false;
    }

    private static PermissionGrant principalGrant(Principal p, Permission perm) {
        return PermissionGrantBuilder.newBuilder()
                .principals(new Principal[]{p})
                .permissions(new Permission[]{perm})
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            result[i / 2] = (byte) ((hi << 4) + lo);
        }
        return result;
    }

    private final Principal admin = name("spiffe://ctrl/admin");
    private final Principal hosted = name("spiffe://ctrl/hosted-proxy-owner");

    // ==================================================================
    // Probe 1: unreachable without the T2 authentication gate.
    // ==================================================================

    @Test
    public void unauthenticated_neverReachesBacking() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller(); // subject == null: unauthenticated
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);

        try {
            front.getSubProcessPolicyAdmin();
            fail("unauthenticated caller must not receive a usable admin proxy");
        } catch (SecurityException expected) { /* good */ }

        // Confirm the real backend was never reached: nothing installed.
        Permission probe = new RuntimePermission("isolation.probe");
        assertFalse("no grant may have reached the real local policy",
                policy.implies(domain(hosted), probe));
    }

    @Test
    public void wrongPrincipal_neverReachesBacking() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(name("spiffe://ctrl/attacker"));
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);

        try {
            front.getSubProcessPolicyAdmin();
            fail("wrong principal must not receive a usable admin proxy");
        } catch (SecurityException expected) { /* good */ }
    }

    // ==================================================================
    // Probe 2: a lease-scoped grant actually expires, end-to-end, through
    // the real authentication layer and the real local DynamicPolicyProvider.
    // ==================================================================

    @Test
    public void leaseScopedGrant_actuallyExpires_endToEnd() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin(); // authenticated as admin

        Permission delegated = new RuntimePermission("isolation.leaseProbe");
        PermissionGrant scoped = principalGrant(hosted, delegated);
        long shortLeaseMillis = 300L;
        TestLease lease = new TestLease(System.currentTimeMillis() + shortLeaseMillis);
        LeasedPermissionGrant leased = new LeasedPermissionGrant(scoped, lease);

        pa.grant(leased);

        assertTrue("grant is in force immediately after install",
                policy.implies(domain(hosted), delegated));
        PermissionGrant[] before = pa.getGrants();
        assertTrue("getGrants() reports the live grant before expiry",
                anyGrantCarries(before, delegated));

        // Wait/simulate lease expiry (real wall-clock, per the SOW's own
        // "grants, waits/simulates lease expiry" self-test wording).
        Thread.sleep(shortLeaseMillis + 400L);

        assertFalse("expired lease-scoped grant must no longer be in force",
                policy.implies(domain(hosted), delegated));
        PermissionGrant[] after = pa.getGrants();
        assertFalse("getGrants() must not report an expired grant as live",
                anyGrantCarries(after, delegated));
    }

    // ==================================================================
    // Probe 2b (2026-07-20 board finding 2 + 3 closure): grant() no longer
    // refuses on shape mismatch -- it OWNS rebinding (always to
    // scopePrincipals) and lease-wrapping (reuse caller's lease, or mint a
    // local one) unconditionally, true by construction rather than by
    // refusing whatever the caller didn't already do itself.
    // ==================================================================

    @Test
    public void grant_bareUnleasedGrant_isAutoLeaseWrapped_thenExpires() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        long shortTtlMillis = 300L;
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted}, shortTtlMillis);
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        Permission delegated = new RuntimePermission("isolation.autoLeaseProbe");
        PermissionGrant bare = principalGrant(hosted, delegated); // caller did NOT lease it

        pa.grant(bare); // must succeed: T1 mints its own local dead-man lease now

        assertTrue("auto-lease-wrapped grant is in force immediately",
                policy.implies(domain(hosted), delegated));

        Thread.sleep(shortTtlMillis + 400L);

        assertFalse("must expire per the backend's own default-TTL dead-man switch",
                policy.implies(domain(hosted), delegated));
    }

    @Test
    public void grant_emptyPrincipalArray_reboundToScope_notUniversal() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        Permission perm = new RuntimePermission("isolation.emptyPrincipalEscalationProbe");
        // Exact adversarial shape from the board's Finding 2: PRINCIPAL
        // context with an EMPTY Principal[] array -- PrincipalGrant.implies
        // (Principal[]) treats an empty required-set as always-satisfied,
        // i.e. "implies every protection domain" -- wrapped in a real
        // LeasedPermissionGrant so the (now-removed) lease-shape-only gate
        // would have let it straight through unmodified.
        PermissionGrant universalAttempt = PermissionGrantBuilder.newBuilder()
                .context(PermissionGrantBuilder.PRINCIPAL)
                .principals(new Principal[0])
                .permissions(new Permission[]{perm})
                .build();
        TestLease lease = new TestLease(System.currentTimeMillis() + 60_000L);
        pa.grant(new LeasedPermissionGrant(universalAttempt, lease));

        Principal unrelated = name("spiffe://ctrl/totally-unrelated");
        assertFalse("must be rebound to scopePrincipals, never installed as universal",
                policy.implies(domain(unrelated), perm));
        assertTrue("must apply to this backend's own configured scope",
                policy.implies(domain(hosted), perm));
    }

    @Test
    public void grant_nullPrincipalArray_reboundToScope_notUniversal() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        Permission perm = new RuntimePermission("isolation.nullPrincipalEscalationProbe");
        // .principals(...) never called at all -- the builder's internal
        // field stays null, which PrincipalGrant treats identically to an
        // empty array (Collections.emptySet()). This is T3's own actual
        // shape (see grant_t3StyleDigestGrant_isAcceptedAndAppliesOnceRebound
        // below), reduced to the PRINCIPAL context for a directly-observable
        // implies() probe.
        PermissionGrant universalAttempt = PermissionGrantBuilder.newBuilder()
                .context(PermissionGrantBuilder.PRINCIPAL)
                .permissions(new Permission[]{perm})
                .build();
        pa.grant(universalAttempt); // bare AND unscoped

        Principal unrelated = name("spiffe://ctrl/totally-unrelated-2");
        assertFalse("must be rebound to scopePrincipals, never installed as universal",
                policy.implies(domain(unrelated), perm));
        assertTrue("must apply to this backend's own configured scope",
                policy.implies(domain(hosted), perm));
    }

    /**
     * Finding 3 regression test: promoted from the board's own
     * cross-check probe ({@code T1T3IntegrationMismatchTest}, built during
     * the 2026-07-20 review to prove the original refusal-based {@code
     * grant()} rejected every grant {@code SubProcessGrantOrchestrator} (T3,
     * {@code jgdms-pref-class-loader/.../SubProcessGrantOrchestrator.java})
     * actually builds). Reproduces T3's exact construction --
     * {@code PermissionGrantBuilder.DIGEST} context, digest + permissions,
     * <em>no</em> {@code .principals(...)} call, <em>no</em> lease wrapper --
     * and now asserts the opposite: T1 accepts it, and once rebound the
     * grant actually applies to the real digest-scoped, principal-scoped
     * domain it was computed for (not merely "doesn't throw").
     */
    @Test
    public void grant_t3StyleDigestGrant_isAcceptedAndAppliesOnceRebound() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append("aa");
        byte[] digestBytes = hexToBytes(sb.toString()); // fake 32-byte SHA-256
        Permission t3Perm = new RuntimePermission("isolation.t3ceilingProbe");

        // Exactly SubProcessGrantOrchestrator.applyVerdictCeiling's grant
        // construction: DIGEST context, digest + permissions, no principals,
        // no lease.
        PermissionGrant t3Grant = PermissionGrantBuilder.newBuilder()
                .context(PermissionGrantBuilder.DIGEST)
                .digest("SHA-256", digestBytes)
                .permissions(new Permission[]{t3Perm})
                .build();

        // Must NOT throw: T1 must accept T3's exact, currently-merged grant
        // shape (Finding 3).
        pa.grant(t3Grant);

        // Stronger end-to-end proof, skipped gracefully off DirtyChai (where
        // java.security.DigestCodeSource doesn't exist): once rebound to
        // scopePrincipals and installed, the grant actually applies to a
        // real DigestCodeSource-backed domain carrying those principals --
        // directly rebutting SubProcessGrantOrchestrator's own javadoc
        // caveat that a scope mismatch could leave the pushed grant
        // "silently implying nothing" for the hosted proxy's actual domain.
        Class<?> digestCodeSourceClass;
        try {
            digestCodeSourceClass = Class.forName("java.security.DigestCodeSource");
        } catch (ClassNotFoundException notOnThisJdk) {
            Assume.assumeNoException(
                    "DigestCodeSource only available on DirtyChai", notOnThisJdk);
            return;
        }
        Constructor<?> ctor = digestCodeSourceClass.getConstructor(
                String.class, Certificate[].class, String.class, byte[].class);
        Object digestCodeSource = ctor.newInstance(
                "file:/probe.jar", null, "SHA-256", digestBytes);

        ProtectionDomain hostedProxyDomain = new ProtectionDomain(
                (CodeSource) digestCodeSource, null, null, new Principal[]{hosted});
        assertTrue("T3's digest-scoped grant, once rebound by T1, must actually"
                + " apply to the real hosted-proxy domain it was computed for",
                policy.implies(hostedProxyDomain, t3Perm));

        ProtectionDomain unrelatedDomain = new ProtectionDomain(
                (CodeSource) digestCodeSource, null, null,
                new Principal[]{name("spiffe://ctrl/someone-else")});
        assertFalse("must not apply to an unrelated principal's domain"
                + " (rebinding, not universal-implies)",
                policy.implies(unrelatedDomain, t3Perm));
    }

    // ==================================================================
    // Probe 3: getGrants()/refresh() cannot leak state to a de-authenticated
    // (captured-proxy) caller.
    // ==================================================================

    @Test
    public void getGrants_reGates_capturedProxyCannotReadAfterDeauth() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin(); // captured while admin

        Permission delegated = new RuntimePermission("isolation.leakProbe");
        PermissionGrant scoped = principalGrant(hosted, delegated);
        TestLease lease = new TestLease(System.currentTimeMillis() + 60_000L);
        pa.grant(new LeasedPermissionGrant(scoped, lease));

        // Caller identity drops to non-admin (reference leaked / replayed).
        caller.subject = subjectWith(name("spiffe://ctrl/attacker"));

        try {
            pa.getGrants();
            fail("captured proxy must re-gate getGrants() for a non-admin caller");
        } catch (SecurityException expected) { /* good: no grant data returned */ }

        try {
            pa.refresh();
            fail("captured proxy must re-gate refresh() for a non-admin caller");
        } catch (SecurityException expected) { /* good */ }
    }

    @Test
    public void adminCaller_getGrants_seesRealInstalledGrant() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        Permission delegated = new RuntimePermission("isolation.visibleProbe");
        assertFalse("not installed yet", anyGrantCarries(pa.getGrants(), delegated));

        TestLease lease = new TestLease(System.currentTimeMillis() + 60_000L);
        pa.grant(new LeasedPermissionGrant(principalGrant(hosted, delegated), lease));

        PermissionGrant[] grants = pa.getGrants();
        assertTrue("returned grants include the real installed grant, live-queried",
                anyGrantCarries(grants, delegated));

        pa.refresh(); // must not throw for an authenticated admin caller
    }

    // ==================================================================
    // Probe 4: the backend itself is a plain object, never independently
    // remotely reachable.
    // ==================================================================

    @Test
    public void backend_isNotRemote() {
        Object backend = new SubProcessLocalPolicyAdmin(
                newPolicy(), new Principal[]{hosted});
        assertFalse("the real backend must never itself be a Remote export target",
                backend instanceof java.rmi.Remote);
    }

    // ==================================================================
    // Probe 5: the existing GrantPermission install ceiling still runs
    // (this class neither bypasses nor duplicates it).
    // ==================================================================

    private static final class CeilingSecurityManager extends SecurityManager {
        private final GrantPermission ceiling;
        volatile boolean gating;
        CeilingSecurityManager(GrantPermission ceiling) { this.ceiling = ceiling; }
        @Override public void checkPermission(Permission perm) {
            if (!gating) return;
            if (perm instanceof GrantPermission && !ceiling.implies(perm)) {
                throw new SecurityException("exceeds GrantPermission ceiling: " + perm);
            }
        }
        @Override public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
        }
    }

    @Test
    public void grant_stillSurfacesInstallCeilingDenial() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        Permission inCeiling = new RuntimePermission("isolation.ceilingOk");
        Permission overCeiling = new RuntimePermission("isolation.ceilingExceeded");
        GrantPermission callerCeiling = new GrantPermission(new Permission[]{inCeiling});

        CeilingSecurityManager sm = new CeilingSecurityManager(callerCeiling);
        SecurityManager previous = System.getSecurityManager();
        try {
            System.setSecurityManager(sm);
        } catch (UnsupportedOperationException noAllowFlag) {
            Assume.assumeNoException("needs -Djava.security.manager=allow", noAllowFlag);
            return;
        }
        try {
            sm.gating = true;
            TestLease lease1 = new TestLease(System.currentTimeMillis() + 60_000L);
            pa.grant(new LeasedPermissionGrant(principalGrant(hosted, inCeiling), lease1));
            assertTrue(policy.implies(domain(hosted), inCeiling));

            TestLease lease2 = new TestLease(System.currentTimeMillis() + 60_000L);
            try {
                pa.grant(new LeasedPermissionGrant(principalGrant(hosted, overCeiling), lease2));
                fail("expected the existing GrantPermission ceiling to deny this install");
            } catch (SecurityException expected) {
                // correct: the calling context may not grant authority it
                // does not itself hold; this class does not bypass that.
            }
        } finally {
            sm.gating = false;
            if (previous != null) System.setSecurityManager(previous);
        }
    }
}
