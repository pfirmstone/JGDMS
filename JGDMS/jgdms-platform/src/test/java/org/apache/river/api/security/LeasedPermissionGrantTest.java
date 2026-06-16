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

import java.lang.ref.WeakReference;
import java.rmi.RemoteException;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link LeasedPermissionGrant}, realising the design's T1-T14
 * test contract as far as is deterministic without an installed
 * {@code SecurityManager}.  A mutable {@link Clock} drives all time-sensitive
 * behaviour, so no {@code Thread.sleep} is needed (T11).
 *
 * <p>T3 (live sweeper eviction) and T6 (end-to-end {@code GrantPermission}
 * ceiling) are integration concerns owned by {@code DynamicPolicyProvider}'s
 * own tests, not re-tested here:
 * <ul>
 *   <li>T3's precondition &mdash; an expired wrapper reports {@code isVoid()
 *       == true} so the sweeper can evict it &mdash; is covered by
 *       {@link #t2_voidAtExpiryBoundary()}.</li>
 *   <li>T6's mechanism &mdash; {@code getPermissions()} delegates so the
 *       ceiling sees the real permission set &mdash; is covered by
 *       {@link #t14_getPermissionsDelegates()}; installing a {@code
 *       SecurityManager} here would be fragile on JDK 17+.</li>
 * </ul>
 */
public class LeasedPermissionGrantTest {

    private static final long T0 = 1000L;

    private PermissionGrant principalGrant(Principal[] pals, Permission... perms) {
        return PermissionGrantBuilder.newBuilder()
                .permissions(perms)
                .principals(pals)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
    }

    private PermissionGrant simpleGrant() {
        return principalGrant(new Principal[0], new RuntimePermission("leasedTest"));
    }

    // ---- T1 -----------------------------------------------------------------

    @Test
    public void t1_activeGrantImpliesBeforeExpiry() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(simpleGrant(), lease, clock);

        assertFalse(lpg.isVoid());
        assertTrue(lpg.implies((ProtectionDomain) null));
    }

    // ---- T2 -----------------------------------------------------------------

    @Test
    public void t2_voidAtExpiryBoundary() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, 2000L);
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(simpleGrant(), lease, clock);

        clock.set(1999L);
        assertFalse("just before expiry", lpg.isVoid());

        clock.set(2000L);
        assertTrue("at the exact expiry millisecond (>= convention)", lpg.isVoid());

        clock.set(2001L);
        assertTrue("after expiry", lpg.isVoid());
    }

    // ---- T4 -----------------------------------------------------------------

    @Test
    public void t4_renewalExtendsLife() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, 2000L);
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(simpleGrant(), lease, clock);
        assertFalse(lpg.isVoid());

        clock.set(1900L);                 // still valid
        lease.renew(60_000L);             // landlord pushes expiry to 1900 + 60000
        clock.set(2500L);                 // past the OLD expiry (2000), before the new one

        assertFalse(lpg.isVoid());
        assertTrue(lpg.implies((ProtectionDomain) null));
    }

    // ---- T5 -----------------------------------------------------------------

    @Test
    public void t5_cancelVoidsImmediatelyAndMonotonically() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(simpleGrant(), lease, clock);
        assertFalse(lpg.isVoid());

        long expirationBefore = lease.getExpiration();
        lpg.cancel();

        assertTrue(lpg.isVoid());
        assertFalse(lpg.implies((ProtectionDomain) null));
        assertTrue("cancellation is monotone", lpg.isVoid());
        assertEquals("local cancel must not touch the lease",
                expirationBefore, lease.getExpiration());
    }

    // ---- T7 -----------------------------------------------------------------

    @Test
    public void t7_conjunctivePrincipalMatchingPreserved() {
        Principal a = new NamedPrincipal("A");
        Principal b = new NamedPrincipal("B");
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        PermissionGrant base = principalGrant(new Principal[]{a, b},
                new RuntimePermission("leasedTest"));
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(base, lease, clock);

        assertFalse("missing B", lpg.implies((ClassLoader) null, new Principal[]{a}));
        assertTrue("both present", lpg.implies((ClassLoader) null, new Principal[]{a, b}));

        clock.set(T0 + 60_000);           // expire
        assertFalse("expiry short-circuits even when principals match",
                lpg.implies((ClassLoader) null, new Principal[]{a, b}));
    }

    // ---- T8 (deterministic): wrapped-void composition ----------------------

    @Test
    public void t8a_wrappedVoidShortCircuitsWhileLeaseLive() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        ExternallyVoidablePermissionGrant inner =
                new ExternallyVoidablePermissionGrant(simpleGrant());
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(inner, lease, clock);
        assertFalse(lpg.isVoid());

        inner.voidGrant();                // wrapped becomes void; lease still live

        assertTrue(lpg.isVoid());
        assertFalse(lpg.implies((ProtectionDomain) null));
    }

    // ---- T8 (GC): cleared ProtectionDomain voids while lease is live -------

    @Test
    public void t8b_gcOfProtectionDomainVoidsWhileLeaseLive() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);

        ProtectionDomain pd =
                new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null);
        WeakReference<ProtectionDomain> ref = new WeakReference<ProtectionDomain>(pd);
        PermissionGrant pdGrant = PermissionGrantBuilder.newBuilder()
                .setDomain(ref)
                .principals(new Principal[0])
                .permissions(new Permission[]{new RuntimePermission("leasedTest")})
                .context(PermissionGrantBuilder.PROTECTIONDOMAIN)
                .build();
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(pdGrant, lease, clock);
        assertFalse(lpg.isVoid());

        pd = null;                        // drop the only strong reference
        boolean collected = false;
        for (int i = 0; i < 50 && !collected; i++) {
            System.gc();
            collected = ref.get() == null;
        }
        // Don't make the suite hostage to GC timing; only assert if collection happened.
        Assume.assumeTrue("ProtectionDomain was not collected", collected);

        assertTrue("cleared WeakReference voids the grant even with a live lease",
                lpg.isVoid());
    }

    // ---- T9 -----------------------------------------------------------------

    @Test
    public void t9_foreverLeaseRejected() {
        MutableClock clock = new MutableClock(T0);
        TestLease forever = new TestLease(clock, Lease.FOREVER);
        try {
            new LeasedPermissionGrant(simpleGrant(), forever, clock);
            fail("expected IllegalArgumentException for Lease.FOREVER");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("FOREVER"));
        }
    }

    // ---- T10 ----------------------------------------------------------------

    @Test
    public void t10_alreadyExpiredLeaseRejected() {
        MutableClock clock = new MutableClock(T0);
        TestLease expired = new TestLease(clock, 500L); // < T0
        try {
            new LeasedPermissionGrant(simpleGrant(), expired, clock);
            fail("expected IllegalArgumentException for already-expired lease");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("expired"));
        }
    }

    @Test
    public void t10b_anyLeaseSentinelRejected() {
        MutableClock clock = new MutableClock(T0);
        TestLease any = new TestLease(clock, Lease.ANY); // -1
        try {
            new LeasedPermissionGrant(simpleGrant(), any, clock);
            fail("expected IllegalArgumentException for Lease.ANY");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- T12 ----------------------------------------------------------------

    @Test
    public void t12_equalityIncludesLeaseIdentity() {
        MutableClock clock = new MutableClock(T0);
        TestLease l1 = new TestLease(clock, T0 + 60_000);
        TestLease l2 = new TestLease(clock, T0 + 60_000); // equal expiry, distinct identity
        PermissionGrant g = simpleGrant();

        LeasedPermissionGrant a1 = new LeasedPermissionGrant(g, l1, clock);
        LeasedPermissionGrant a2 = new LeasedPermissionGrant(g, l1, clock); // same lease
        LeasedPermissionGrant b = new LeasedPermissionGrant(g, l2, clock);  // different lease

        assertEquals("same wrapped grant + same lease are equal", a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());

        assertNotEquals("different leases are distinct grants", a1, b);
        assertTrue("distinct lease identities should hash differently",
                a1.hashCode() != b.hashCode());
    }

    // ---- T13 ----------------------------------------------------------------

    @Test
    public void t13_privilegedWrappedGrantRejected() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        PermissionGrant privileged =
                principalGrant(new Principal[0], new AllPermission());
        assertTrue("precondition: wrapped grant is privileged", privileged.isPrivileged());
        try {
            new LeasedPermissionGrant(privileged, lease, clock);
            fail("expected IllegalArgumentException wrapping an AllPermission grant");
        } catch (IllegalArgumentException expected) {
            // inherited from PermissionGrant.checkInvariants
        }
    }

    // ---- T14 ----------------------------------------------------------------

    @Test
    public void t14_getPermissionsDelegates() {
        MutableClock clock = new MutableClock(T0);
        TestLease lease = new TestLease(clock, T0 + 60_000);
        Permission p1 = new RuntimePermission("p1");
        Permission p2 = new RuntimePermission("p2");
        LeasedPermissionGrant lpg = new LeasedPermissionGrant(
                principalGrant(new Principal[0], p1, p2), lease, clock);

        assertEquals("ceiling must see the real (wrapped) permission set",
                2, lpg.getPermissions().size());
        assertTrue(lpg.getPermissions().contains(p1));
        assertTrue(lpg.getPermissions().contains(p2));
    }

    // ---- Test doubles -------------------------------------------------------

    /** A {@link Clock} whose epoch-millis can be set directly by the test. */
    private static final class MutableClock extends Clock {
        private volatile long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void set(long millis) {
            this.millis = millis;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** Minimal mutable {@link Lease} double; {@code renew} sets expiry to now + duration. */
    private static final class TestLease implements Lease {
        private final Clock clock;
        private volatile long expiration;

        TestLease(Clock clock, long expiration) {
            this.clock = clock;
            this.expiration = expiration;
        }

        @Override
        public long getExpiration() {
            return expiration;
        }

        @Override
        public void cancel() {
            // The wrapper never calls this; present only to satisfy the interface.
        }

        @Override
        public void renew(long duration) {
            this.expiration = clock.millis() + duration;
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

    /** A simple value-equal {@link Principal}. */
    private static final class NamedPrincipal implements Principal {
        private final String name;

        NamedPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NamedPrincipal && name.equals(((NamedPrincipal) o).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
