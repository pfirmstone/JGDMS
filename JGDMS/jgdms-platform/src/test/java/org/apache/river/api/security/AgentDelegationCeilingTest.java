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
 * Proves the <em>install-time attenuation</em> guarantee for leased user&rarr;agent
 * delegation (SOW-AI-Agent-Authority §5): a user conferring a delegation can only
 * hand the agent permissions the user is itself authorised to grant.
 * {@code DynamicPolicyProvider.grant(PermissionGrant)} runs a {@link GrantPermission}
 * guard over the grant's permissions, so a delegation whose permissions exceed the
 * delegating caller's {@code GrantPermission} ceiling is refused.
 *
 * <p>The walking skeleton ({@link AgentAuthorityWalkingSkeletonTest}) covers the
 * authority-<em>composition</em> criteria but cannot exercise this guard, because
 * {@code Permission.checkGuard(null)} is a no-op without an active
 * {@code SecurityManager}. This test installs a narrow SecurityManager that gates
 * only {@link GrantPermission} against a configurable ceiling (everything else is
 * allowed, so the JVM and test framework are unaffected), then asserts that
 * {@code grant()} permits an in-ceiling delegation and denies an over-ceiling one.
 *
 * <p>Installing a SecurityManager requires {@code -Djava.security.manager=allow}
 * at JVM start (JEP 411); the module's surefire config sets it. If it is absent the
 * SecurityManager-backed assertion is skipped (via {@link Assume}) rather than
 * failing, while {@link #ceilingLogicIsMonotone()} always runs.
 */
public class AgentDelegationCeilingTest {

    private static final Principal AGENT = new Id("spiffe://example.org/agent/claude-1");

    /** A permission the delegating user is authorised to grant. */
    private static final Permission IN_CEILING = new RuntimePermission("agent.delegated.read");
    /** A permission the delegating user is NOT authorised to grant. */
    private static final Permission OVER_CEILING = new RuntimePermission("agent.delegated.admin");

    /** The user's grant-authority: they may delegate only IN_CEILING. */
    private static final GrantPermission USER_CEILING =
            new GrantPermission(new Permission[]{IN_CEILING});

    // ---- Always-on: the ceiling relation is monotone (no SM needed) --------

    @Test
    public void ceilingLogicIsMonotone() {
        assertTrue("user may delegate what it holds",
                USER_CEILING.implies(new GrantPermission(new Permission[]{IN_CEILING})));
        assertFalse("user may NOT delegate beyond its grant-authority",
                USER_CEILING.implies(new GrantPermission(new Permission[]{OVER_CEILING})));
    }

    // ---- SM-backed: grant() enforces the ceiling at install time -----------

    @Test
    public void grantEnforcesCeilingOnLeasedDelegation() {
        MutableClock clock = new MutableClock(1000L);
        TestLease lease = new TestLease(1000L + 60_000L);
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new DenyAllBasePolicy());

        // Build both delegations BEFORE installing the SM (construction triggers
        // getProtectionDomain/getClassLoader guards we don't want to gate).
        PermissionGrant inCeiling = new LeasedPermissionGrant(
                principalGrant(new Principal[]{AGENT}, IN_CEILING), lease, clock);
        PermissionGrant overCeiling = new LeasedPermissionGrant(
                principalGrant(new Principal[]{AGENT}, OVER_CEILING), lease, clock);

        CeilingSecurityManager sm = new CeilingSecurityManager(USER_CEILING);
        SecurityManager previous = System.getSecurityManager();
        try {
            System.setSecurityManager(sm);
        } catch (UnsupportedOperationException noAllowFlag) {
            Assume.assumeNoException(
                    "needs -Djava.security.manager=allow at JVM start", noAllowFlag);
            return;
        }
        try {
            sm.gating = true;

            // In-ceiling delegation installs.
            assertTrue("in-ceiling delegation is permitted", policy.grant(inCeiling));

            // Over-ceiling delegation is refused by the install-time guard.
            try {
                policy.grant(overCeiling);
                fail("expected the GrantPermission ceiling to deny over-broad delegation");
            } catch (SecurityException expected) {
                // correct: a user cannot delegate authority it does not itself hold
            }
        } finally {
            sm.gating = false;
            // DirtyChai forbids setSecurityManager(null); under -Djava.security.manager=allow
            // no SM exists at startup, so only restore when one was actually installed.
            if (previous != null) System.setSecurityManager(previous);
        }
    }

    // ---- Harness ------------------------------------------------------------

    private static PermissionGrant principalGrant(Principal[] pals, Permission... perms) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(perms)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
    }

    /**
     * SecurityManager that denies only {@link GrantPermission}s not implied by a
     * fixed ceiling; everything else is allowed so the JVM and test framework run
     * unimpeded. {@code gating} scopes enforcement to the assertion window.
     */
    private static final class CeilingSecurityManager extends SecurityManager {
        private final GrantPermission ceiling;
        volatile boolean gating;

        CeilingSecurityManager(GrantPermission ceiling) {
            this.ceiling = ceiling;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (!gating) return;
            if (perm instanceof GrantPermission && !ceiling.implies(perm)) {
                throw new SecurityException("exceeds GrantPermission ceiling: " + perm);
            }
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
        }
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
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private static final class TestLease implements Lease {
        private final long expiration;
        TestLease(long expiration) { this.expiration = expiration; }
        @Override public long getExpiration() { return expiration; }
        @Override public void cancel() { }
        @Override public void renew(long duration) { }
        @Override public void setSerialFormat(int format) { }
        @Override public int getSerialFormat() { return Lease.DURATION; }
        @Override public LeaseMap<? extends Lease, Long> createLeaseMap(long duration) { return null; }
        @Override public boolean canBatch(Lease lease) { return false; }
    }
}
