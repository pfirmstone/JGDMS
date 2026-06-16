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
 * Tests the thin user&rarr;agent delegation facade {@link LeasedDelegation}:
 * install/apply-without-user, renewal extending the lifetime, immediate
 * revocation (local void + best-effort landlord cancel), the expiry dead-man
 * switch, {@code Lease.FOREVER} rejection, and (SecurityManager-backed) that the
 * API surfaces the install-time {@code GrantPermission} ceiling denial.
 *
 * <p>Runs on a vanilla JDK 21 (JGDMS's compile target); production runs on
 * DirtyChai where an active authorization SecurityManager enforces the install
 * ceiling. The ceiling test installs a narrow SecurityManager and skips via
 * {@link Assume} if {@code -Djava.security.manager=allow} is absent.
 */
public class LeasedDelegationTest {

    private static final Principal AGENT = new Id("spiffe://example.org/agent/claude-1");
    private static final Principal OTHER = new Id("spiffe://example.org/agent/other");

    private static final Permission DELEGATED = new RuntimePermission("agent.delegated.read");
    private static final Permission OVER_CEILING = new RuntimePermission("agent.delegated.admin");

    private static final long T0 = 1000L;

    @Test
    public void delegationAppliesToAgentWithoutUserPresent() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();

        LeasedDelegation d = LeasedDelegation.grant(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);

        assertFalse(d.isVoid());
        assertSame("renewal credential is held by the delegator handle", lease, d.lease());
        assertEquals(AGENT, d.agent());
        assertTrue("agent exercises the delegated authority with no user present",
                policy.implies(domain(AGENT), DELEGATED));
        assertFalse("a different principal does not receive the delegation",
                policy.implies(domain(OTHER), DELEGATED));
    }

    @Test
    public void renewExtendsDelegationLifetime() throws Exception {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, 2000L);
        DynamicPolicyProvider policy = newPolicy();
        LeasedDelegation d = LeasedDelegation.grant(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);

        clock.set(1900L);          // still valid
        d.renew(60_000L);          // delegator renews => expiry -> 1900 + 60000
        clock.set(2500L);          // past the OLD expiry, before the new one

        assertFalse(d.isVoid());
        assertTrue(policy.implies(domain(AGENT), DELEGATED));
    }

    @Test
    public void revokeVoidsImmediatelyAndCancelsLease() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();
        LeasedDelegation d = LeasedDelegation.grant(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);
        assertTrue(policy.implies(domain(AGENT), DELEGATED));

        d.revoke();

        assertTrue("authority withdrawn immediately and locally", d.isVoid());
        assertFalse(policy.implies(domain(AGENT), DELEGATED));
        assertTrue("revoke notifies the landlord (best-effort lease cancel)", lease.cancelled);
        assertTrue("revocation is monotone", d.isVoid());
    }

    @Test
    public void expiryVoidsDelegationWithoutRenewal() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, 2000L);
        DynamicPolicyProvider policy = newPolicy();
        LeasedDelegation d = LeasedDelegation.grant(
                policy, AGENT, new Permission[]{DELEGATED}, lease, clock);
        assertTrue(policy.implies(domain(AGENT), DELEGATED));

        clock.set(2000L);          // dead-man switch: missed renewal -> void at expiry

        assertTrue(d.isVoid());
        assertFalse(policy.implies(domain(AGENT), DELEGATED));
    }

    @Test
    public void grantRejectsForeverLease() {
        MutableClock clock = new MutableClock(T0);
        TestLease forever = new TestLease(clock, Lease.FOREVER);
        try {
            LeasedDelegation.grant(newPolicy(), AGENT, new Permission[]{DELEGATED}, forever, clock);
            fail("expected IllegalArgumentException: a forever lease has no dead-man switch");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void grantSurfacesInstallCeilingDenial() {
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
            // in-ceiling delegation is permitted
            assertNotNull(LeasedDelegation.grant(
                    policy, AGENT, new Permission[]{DELEGATED}, lease, clock));
            // over-ceiling delegation is refused by the install guard
            try {
                LeasedDelegation.grant(
                        policy, AGENT, new Permission[]{OVER_CEILING}, lease, clock);
                fail("expected the GrantPermission ceiling to deny over-broad delegation");
            } catch (SecurityException expected) {
                // correct: the delegator may not delegate authority it does not hold
            }
        } finally {
            sm.gating = false;
            System.setSecurityManager(previous);
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
