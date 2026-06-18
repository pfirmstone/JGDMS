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
import net.jini.security.policy.DynamicPolicyProvider;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Walking-skeleton integration test for the agent-authority layer
 * (`SOW-AI-Agent-Authority-Support.md`), proving that the existing JGDMS
 * authorization primitives plus {@link LeasedPermissionGrant} <em>compose</em>
 * into the four behaviours the model claims — exercised end-to-end through a
 * real {@link DynamicPolicyProvider#implies(ProtectionDomain, Permission)},
 * not asserted on paper.
 *
 * <p>This is the Phase-0 / local single-JVM (delegation "Option (i)") skeleton.
 * It deliberately uses <em>stand-in</em> principals rather than the production
 * identity carriers, because the composition it proves is identity-carrier
 * agnostic:
 * <ul>
 *   <li>the <b>agent SVID</b> is a plain {@link Principal}; in production it is a
 *       {@code SpiffePrincipal} stamped into the ProtectionDomain at class-load
 *       (ambient, via {@code SecureClassLoader} / {@code WorkerSubject});</li>
 *   <li>the <b>user</b> is a plain {@link Principal}; in production it is a
 *       {@code JwtPrincipal} injected for the dynamic scope of a call by
 *       {@code Subject.callAs} (which excludes {@code WorkerSubject}).</li>
 * </ul>
 * Whichever path puts a principal on the domain, the policy only sees
 * {@code ProtectionDomain.getPrincipals()} — which is what this test controls.
 *
 * <p><b>Scope note:</b> the four scenarios cover the <em>authority-composition</em>
 * acceptance criteria. The install-time attenuation ceiling
 * ({@code DynamicPolicyProvider.grant} runs a {@code GrantPermission} guard
 * against the delegating user's context) is a no-op without an active
 * {@code SecurityManager} and so is not enforced here; that mechanism already
 * exists at {@code DynamicPolicyProvider.grant():686-687} and the wrapper's
 * permission visibility through it is covered by
 * {@link LeasedPermissionGrantTest} (T14). A SecurityManager-backed test of the
 * ceiling is a later step (and awkward on JDK 17+).
 */
public class AgentAuthorityWalkingSkeletonTest {

    /** Stand-in for the agent's ambient SPIFFE SVID. */
    private static final Principal AGENT = new Id("spiffe://example.org/agent/claude-1");
    /** Stand-in for the user's scoped JWT principal. */
    private static final Principal USER = new Id("jwt://sub/alice");

    /** A capability inside the agent's baseline playpen. */
    private static final Permission PLAYPEN_READ = new RuntimePermission("agent.playpen.read");
    /** A capability that is only available via escalation or leased delegation. */
    private static final Permission ESCALATED_WRITE = new RuntimePermission("agent.escalated.writeProd");
    /** A capability that belongs to the user alone and must never leak to the agent. */
    private static final Permission USER_PRIVATE = new RuntimePermission("user.private.action");

    private static final long T0 = 1000L;

    // ---- Scenario 1: baseline playpen applies without the user present ------

    @Test
    public void playpenAppliesWhetherOrNotUserIsPresent() {
        DynamicPolicyProvider policy = newPolicy();
        // Baseline playpen: scoped to the AGENT identity, NO user term.
        policy.grant(principalGrant(new Principal[]{AGENT}, PLAYPEN_READ));

        assertTrue("agent acts in its playpen with no user co-present",
                policy.implies(domain(AGENT), PLAYPEN_READ));
        assertTrue("playpen still applies when the user happens to be present",
                policy.implies(domain(AGENT, USER), PLAYPEN_READ));
        assertFalse("escalated authority is NOT part of the playpen",
                policy.implies(domain(AGENT), ESCALATED_WRITE));
    }

    // ---- Scenario 2: escalation requires the live user (conjunction) --------

    @Test
    public void escalationRequiresLiveUserCoPresence() {
        DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{AGENT}, PLAYPEN_READ));
        // Escalation grant: same permission space WITH the user term (∧ user).
        policy.grant(principalGrant(new Principal[]{AGENT, USER}, ESCALATED_WRITE));

        assertFalse("no user co-present => escalated authority denied",
                policy.implies(domain(AGENT), ESCALATED_WRITE));
        assertTrue("user live in scope (callAs) => escalation granted, one-shot",
                policy.implies(domain(AGENT, USER), ESCALATED_WRITE));
        assertTrue("playpen unaffected",
                policy.implies(domain(AGENT), PLAYPEN_READ));
    }

    // ---- Scenario 3: leased delegation acts without the user, then dies -----

    @Test
    public void leasedDelegationActsWithoutUserAndDiesOnExpiry() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{AGENT}, PLAYPEN_READ));
        // The user (live at delegation time) confers a bounded, time-limited
        // subset of authority to the AGENT identity. Thereafter the user is gone.
        policy.grant(new LeasedPermissionGrant(
                principalGrant(new Principal[]{AGENT}, ESCALATED_WRITE), lease, clock));

        assertTrue("agent exercises the delegated authority with NO user present",
                policy.implies(domain(AGENT), ESCALATED_WRITE));

        clock.set(T0 + 60_000); // lease expires (>= is fail-secure)

        assertFalse("dead-man switch: lease expiry kills the delegated authority",
                policy.implies(domain(AGENT), ESCALATED_WRITE));
        assertTrue("playpen survives the lease expiry",
                policy.implies(domain(AGENT), PLAYPEN_READ));
    }

    // ---- Scenario 4: delegation is a permission-subset, NOT impersonation ---

    @Test
    public void leasedDelegationGivesPermissionSubsetNotUserIdentity() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(T0 + 60_000);
        DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{AGENT}, PLAYPEN_READ));
        // A user-only authority that exists in the policy.
        policy.grant(principalGrant(new Principal[]{USER}, USER_PRIVATE));
        // The delegation grants the AGENT a SUBSET permission — it does NOT make
        // the agent carry the user's identity.
        policy.grant(new LeasedPermissionGrant(
                principalGrant(new Principal[]{AGENT}, ESCALATED_WRITE), lease, clock));

        assertTrue("the delegated subset permission applies to the agent",
                policy.implies(domain(AGENT), ESCALATED_WRITE));
        assertFalse("agent does NOT gain the user's other authority (no impersonation/widening)",
                policy.implies(domain(AGENT), USER_PRIVATE));
        assertTrue("the user themselves still holds their own authority",
                policy.implies(domain(AGENT, USER), USER_PRIVATE));
    }

    // ---- Harness ------------------------------------------------------------

    private static DynamicPolicyProvider newPolicy() {
        return new DynamicPolicyProvider(new DenyAllBasePolicy());
    }

    private static PermissionGrant principalGrant(Principal[] pals, Permission... perms) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(perms)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
    }

    /** A ProtectionDomain carrying the given principals (as callAs/SVID-stamping would). */
    private static ProtectionDomain domain(Principal... pals) {
        return new ProtectionDomain(
                new CodeSource(null, (Certificate[]) null), null, null, pals);
    }

    /** Base policy that grants nothing, so all authority comes from dynamic grants. */
    private static final class DenyAllBasePolicy extends Policy {
        @Override
        public PermissionCollection getPermissions(CodeSource codesource) {
            return new Permissions();
        }
        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return new Permissions();
        }
        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            return false;
        }
        @Override
        public void refresh() {
        }
    }

    /** A simple value-equal Principal standing in for an SVID / JWT principal. */
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

    /** A {@link Clock} whose epoch-millis the test sets directly. */
    private static final class MutableClock extends Clock {
        private volatile long millis;
        MutableClock(long millis) { this.millis = millis; }
        void set(long millis) { this.millis = millis; }
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    /** Minimal fixed-expiry {@link Lease} double. */
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
