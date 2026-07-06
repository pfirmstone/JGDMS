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

package org.apache.river.api.security;

import java.rmi.RemoteException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.security.GrantPermission;
import net.jini.security.policy.DynamicPolicyProvider;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the <em>one-shot leased-grant</em> security semantics wired into
 * {@link DynamicPolicyProvider} on the {@code feature/oneshot-leased-grant} branch:
 * an {@link OneShotLeasedPermissionGrant} (a {@link LeasedPermissionGrant} that is
 * also {@link OneShot}) reports its authority <strong>only</strong> through
 * {@code impliesOnce}, never through {@code implies}, so a human-gated escalation is
 * never cached by a {@code CachingSecurityManager} nor recorded into generated
 * policy by polpAudit; it remains time-bounded by its lease (dead-man switch).
 *
 * <p>These assertions exercise {@link DynamicPolicyProvider#impliesOnce} and
 * {@code implies} <em>directly</em> &mdash; no {@code SecurityManager} is required.
 * On a vanilla JDK 21 (JGDMS's compile target, and the JDK these tests run on)
 * {@code impliesOnce} is just a public method; on the DirtyChai JDK it overrides
 * {@code Policy.impliesOnce} by name+descriptor. The runtime routing of an
 * SM's after-{@code implies} probe through {@code impliesOnce} is a DirtyChai-only
 * concern not exercisable here; what is verified here is the policy contract that
 * routing depends on.
 *
 * <p>Fixtures mirror {@link LeasedDelegationTest} (mutable {@link Clock},
 * {@link TestLease} that records cancellation and renews to now+duration,
 * {@link Id} principal, deny-all base policy, and the {@code GrantPermission}
 * ceiling SecurityManager).
 */
public class DynamicPolicyProviderOneShotTest {

    private static final Principal AGENT = new Id("spiffe://example.org/agent/claude-1");
    private static final Principal OTHER = new Id("spiffe://example.org/agent/other");

    private static final Permission DELEGATED = new RuntimePermission("agent.oneshot.read");
    private static final Permission OVER_CEILING = new RuntimePermission("agent.oneshot.admin");

    private static final long T0 = 1000L;

    // ---- Case 1: surfaced ONLY by impliesOnce (the core property) -----------

    @Test
    public void oneShotSurfacedOnlyByImpliesOnceNotByImplies() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();

        LeasedDelegation d = LeasedDelegation.grantOneShot(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);

        assertFalse("one-shot delegation is live", d.isVoid());
        assertTrue("one-shot authority is reported by impliesOnce",
                policy.impliesOnce(domain(AGENT), DELEGATED));
        assertFalse("one-shot authority NEVER contributes to implies() "
                + "(so it is never cached nor recorded)",
                policy.implies(domain(AGENT), DELEGATED));
        assertFalse("a different principal receives neither",
                policy.impliesOnce(domain(OTHER), DELEGATED));
        assertFalse(policy.implies(domain(OTHER), DELEGATED));
    }

    @Test
    public void grantOneShotDirectlyOnPolicyHasSameSurfacing() {
        // Same core property, installing the OneShotLeasedPermissionGrant straight
        // into the policy (bypassing the LeasedDelegation facade).
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();

        PermissionGrant oneShot = new OneShotLeasedPermissionGrant(
                principalGrant(new Principal[]{AGENT}, DELEGATED), lease, clock);
        assertTrue(policy.grant(oneShot));

        assertTrue(policy.impliesOnce(domain(AGENT), DELEGATED));
        assertFalse(policy.implies(domain(AGENT), DELEGATED));
    }

    // ---- Case 2: contrast with a renewable (plain) leased grant -------------

    @Test
    public void renewableLeasedGrantSurfacedByImpliesNotImpliesOnce() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();

        LeasedDelegation d = LeasedDelegation.grant(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);

        assertFalse(d.isVoid());
        assertTrue("a renewable leased grant IS reported by implies (cached/recorded normally)",
                policy.implies(domain(AGENT), DELEGATED));
        assertFalse("a renewable leased grant is NOT surfaced by impliesOnce "
                + "(impliesOnce consults one-shot grants only)",
                policy.impliesOnce(domain(AGENT), DELEGATED));
    }

    // ---- Case 3: dead-man switch — lease expiry, cancel, revoke -------------

    @Test
    public void impliesOnceFlipsFalseWhenLeaseExpires() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, 2000L);
        DynamicPolicyProvider policy = newPolicy();

        LeasedDelegation d = LeasedDelegation.grantOneShot(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);
        assertTrue("live before expiry", policy.impliesOnce(domain(AGENT), DELEGATED));

        clock.set(2000L); // dead-man switch: at expiry (>=) the grant is void

        assertTrue(d.isVoid());
        assertFalse("expired one-shot denies on the next impliesOnce check (no cache to clear)",
                policy.impliesOnce(domain(AGENT), DELEGATED));
        assertFalse("still never in implies() either", policy.implies(domain(AGENT), DELEGATED));
    }

    @Test
    public void impliesOnceFlipsFalseAfterCancel() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();

        OneShotLeasedPermissionGrant oneShot = new OneShotLeasedPermissionGrant(
                principalGrant(new Principal[]{AGENT}, DELEGATED), lease, clock);
        assertTrue(policy.grant(oneShot));
        assertTrue(policy.impliesOnce(domain(AGENT), DELEGATED));

        oneShot.cancel(); // local dead-man switch

        assertTrue(oneShot.isVoid());
        assertFalse("cancelled one-shot denies on the next impliesOnce check",
                policy.impliesOnce(domain(AGENT), DELEGATED));
    }

    @Test
    public void impliesOnceFlipsFalseAfterRevoke() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();

        LeasedDelegation d = LeasedDelegation.grantOneShot(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);
        assertTrue(policy.impliesOnce(domain(AGENT), DELEGATED));

        d.revoke();

        assertTrue("authority withdrawn immediately and locally", d.isVoid());
        assertFalse("revoked one-shot denies on the next impliesOnce check",
                policy.impliesOnce(domain(AGENT), DELEGATED));
        assertTrue("revoke notifies the landlord (best-effort lease cancel)", lease.cancelled);
    }

    // ---- Case 4: one-shot is a distinct TYPE, not a flag --------------------

    @Test
    public void oneShotIsATypeNotEqualToPlainLeasedGrantWithSameWrappedGrantAndLease() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);

        PermissionGrant wrapped = principalGrant(new Principal[]{AGENT}, DELEGATED);
        LeasedPermissionGrant plain = new LeasedPermissionGrant(wrapped, lease, clock);
        OneShotLeasedPermissionGrant oneShot = new OneShotLeasedPermissionGrant(wrapped, lease, clock);

        assertTrue("the one-shot grant carries the OneShot marker", oneShot instanceof OneShot);
        assertFalse("a plain leased grant is NOT one-shot", plain instanceof OneShot);

        // Same wrapped grant + same lease, yet the one-shot grant is NOT equal to the
        // plain leased grant: OneShotLeasedPermissionGrant.equals requires the argument
        // to be an OneShotLeasedPermissionGrant, so a one-shot grant can never compare
        // equal to a renewable leased grant (belt-and-braces on top of the type check).
        assertNotEquals("one-shot is not equal to a plain leased grant", oneShot, plain);

        // Note the equality is deliberately ASYMMETRIC and the one-shot side is the
        // load-bearing one for this guarantee: the base LeasedPermissionGrant.equals only
        // checks `instanceof LeasedPermissionGrant` + lease-identity + wrapped-grant
        // equality, so plain.equals(oneShot) is true here (the one-shot IS a leased grant
        // with the same lease and wrapped grant). The security property — a renewable
        // lease can never be mistaken FOR one-shot — is enforced by the OneShot *type*
        // (Case 1/2: only `instanceof OneShot` grants are surfaced by impliesOnce and only
        // non-OneShot grants contribute to implies), not by equals in either direction.
        assertTrue("the distinguishing property is the type, not equals",
                (oneShot instanceof OneShot) && !(plain instanceof OneShot));
    }

    // ---- Case 5: grantOneShot enforces the GrantPermission ceiling ----------
    // Mirrors AgentDelegationCeilingTest#grantEnforcesCeilingOnLeasedDelegation.
    // Needs an active SecurityManager (Permission.checkGuard is a no-op without one),
    // so it is skipped via Assume when -Djava.security.manager=allow is absent.

    @Test
    public void grantOneShotEnforcesInstallCeiling() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();
        GrantPermission userCeiling = new GrantPermission(new Permission[]{DELEGATED});

        CeilingSecurityManager sm = new CeilingSecurityManager(userCeiling);
        SecurityManager previous = System.getSecurityManager();
        try {
            System.setSecurityManager(sm);
        } catch (UnsupportedOperationException noAllowFlag) {
            Assume.assumeNoException("needs -Djava.security.manager=allow", noAllowFlag);
            return;
        }
        try {
            sm.gating = true;
            // in-ceiling one-shot delegation is permitted
            assertNotNull(LeasedDelegation.grantOneShot(
                    policy, AGENT, new Permission[]{DELEGATED}, lease, clock));
            // over-ceiling one-shot delegation is refused by the install guard,
            // exactly like the renewable grant() path
            try {
                LeasedDelegation.grantOneShot(
                        policy, AGENT, new Permission[]{OVER_CEILING}, lease, clock);
                fail("expected the GrantPermission ceiling to deny over-broad one-shot delegation");
            } catch (SecurityException expected) {
                // correct: the delegator may not delegate authority it does not hold
            }
        } finally {
            sm.gating = false;
            if (previous != null) System.setSecurityManager(previous);
        }
    }

    // ---- Harness ------------------------------------------------------------

    private static DynamicPolicyProvider newPolicy() {
        return new DynamicPolicyProvider(new DenyAllBasePolicy());
    }

    private static ProtectionDomain domain(Principal... pals) {
        return new ProtectionDomain(
                new CodeSource(null, (Certificate[]) null), null, null, pals);
    }

    private static PermissionGrant principalGrant(Principal[] pals, Permission... perms) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(perms)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
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

    private static final class Id implements Principal {
        private final String name;
        Id(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public boolean equals(Object o) {
            return o instanceof Id && name.equals(((Id) o).name);
        }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public String toString() { return name; }
    }

    private static final class MutableClock extends Clock {
        private volatile long millis;
        MutableClock(long millis) { this.millis = millis; }
        void set(long millis) { this.millis = millis; }
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    /** Lease double whose {@code renew} sets expiry to now+duration and that records cancellation. */
    private static final class TestLease implements Lease {
        private final Clock clock;
        private volatile long expiration;
        volatile boolean cancelled;
        TestLease(Clock clock, long expiration) { this.clock = clock; this.expiration = expiration; }
        @Override public long getExpiration() { return expiration; }
        @Override public void cancel() { cancelled = true; }
        @Override public void renew(long duration) { expiration = clock.millis() + duration; }
        @Override public void setSerialFormat(int format) { }
        @Override public int getSerialFormat() { return Lease.DURATION; }
        @Override public LeaseMap<? extends Lease, Long> createLeaseMap(long duration) { return null; }
        @Override public boolean canBatch(Lease lease) { return false; }
    }
}
