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
 * <p>Mirrors the self-test probes the SOW specifies:
 * <ul>
 *   <li>the real grant-application path is unreachable without the T2
 *       authentication gate ({@link #unauthenticated_neverReachesBacking}
 *       and {@link #wrongPrincipal_neverReachesBacking});</li>
 *   <li>a lease-scoped grant actually expires end-to-end, observed on the
 *       real local policy, not merely asserted ({@link
 *       #leaseScopedGrant_actuallyExpires_endToEnd});</li>
 *   <li>{@code refresh()}/{@code getGrants()} cannot leak grant state to an
 *       unauthenticated or de-authenticated caller ({@link
 *       #getGrants_reGates_capturedProxyCannotReadAfterDeauth}).</li>
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

    @Test
    public void grant_refusesNonLeasedGrant_structuralFailClosed() throws Exception {
        DynamicPolicyProvider policy = newPolicy();
        SubProcessLocalPolicyAdmin backend =
                new SubProcessLocalPolicyAdmin(policy, new Principal[]{hosted});
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        SubProcessPolicyAdmin front =
                new SubProcessPolicyAdmin(admin, backend, caller);
        PolicyAdmin pa = front.getSubProcessPolicyAdmin();

        Permission delegated = new RuntimePermission("isolation.bareProbe");
        PermissionGrant bare = principalGrant(hosted, delegated); // NOT leased

        try {
            pa.grant(bare);
            fail("a bare, non-lease-scoped grant must be refused (fail-closed)");
        } catch (SecurityException expected) { /* good */ }

        assertFalse("refused grant must not have been installed",
                policy.implies(domain(hosted), delegated));
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
