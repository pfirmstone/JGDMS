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

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Adversarial probes for the per-principal pool (requirement&nbsp;#1) and the
 * DGC-acknowledgment-based lifecycle (requirement&nbsp;#4): spawn-once/reuse,
 * strict per-principal isolation, and &mdash; the critical correctness
 * property &mdash; teardown only on a <em>processed clean call</em>, never on a
 * connection close.
 */
public class IsolationPoolLifecycleTest {

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

    /** A fake admin surface, one distinct instance per subprocess. */
    static final class FakeAdmin implements SubProcessAdministrable {
        final IsolationPoolingKey key;
        FakeAdmin(IsolationPoolingKey key) { this.key = key; }
        public PolicyAdmin getSubProcessPolicyAdmin() throws RemoteException {
            throw new SecurityException("fail-closed (test stub)");
        }
    }

    /** Counts launches and produces a distinct Spawned per launch. */
    static final class CountingLauncher implements SubProcessLauncher {
        final AtomicInteger launches = new AtomicInteger();
        final AtomicInteger shutdowns = new AtomicInteger();
        public Spawned launch(final IsolationPoolingKey key) {
            launches.incrementAndGet();
            final FakeAdmin admin = new FakeAdmin(key);
            return new Spawned() {
                public SubProcessAdministrable adminSurface() { return admin; }
                public void shutdown() { shutdowns.incrementAndGet(); }
            };
        }
    }

    private IsolationPoolingKey key(String spiffe) throws IOException {
        return IsolationPoolingKey.derive(new Principal[]{ name(spiffe) });
    }

    // ------------------------------------------------------------------
    // (#1) Spawn once, reuse; strict per-principal isolation.
    // ------------------------------------------------------------------

    @Test
    public void spawnOnceThenReuseForSamePrincipal() throws Exception {
        CountingLauncher launcher = new CountingLauncher();
        SubProcessPool pool =
                new SubProcessPool(launcher, new SubProcessAdminRegistry());
        IsolationPoolingKey k = key("spiffe://example/a");

        SubProcessHandle h1 = pool.obtain(k);
        SubProcessHandle h2 = pool.obtain(k);
        assertSame("same principal must reuse one subprocess", h1, h2);
        assertEquals("must spawn exactly once", 1, launcher.launches.get());
        assertEquals(1, pool.size());
    }

    @Test
    public void twoDistinctPrincipals_neverSharedSubprocess() throws Exception {
        CountingLauncher launcher = new CountingLauncher();
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        SubProcessPool pool = new SubProcessPool(launcher, registry);

        SubProcessHandle a = pool.obtain(key("spiffe://example/a"));
        SubProcessHandle b = pool.obtain(key("spiffe://example/b"));
        assertNotSame("distinct principals must never share a subprocess", a, b);
        assertEquals(2, launcher.launches.get());
        assertEquals(2, pool.size());
        assertEquals(2, registry.size());
    }

    // ------------------------------------------------------------------
    // (#3) Registry keyed by pooling key; management plane only.
    // ------------------------------------------------------------------

    @Test
    public void registryResolvesAdminSurfaceByKey() throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        SubProcessPool pool = new SubProcessPool(new CountingLauncher(), registry);
        IsolationPoolingKey k = key("spiffe://example/a");
        SubProcessHandle h = pool.obtain(k);

        SubProcessAdministrable viaRegistry = registry.lookup(k);
        assertSame("registry must resolve the same admin surface by key",
                h.adminSurface(), viaRegistry);
        // Management plane only: a SubProcessAdministrable, never a business
        // proxy; and its accessor is itself fail-closed.
        assertTrue(viaRegistry instanceof SubProcessAdministrable);
        try {
            viaRegistry.getSubProcessPolicyAdmin();
            fail("registry handle's admin accessor must be fail-closed");
        } catch (SecurityException expected) { /* good */ }
    }

    // ------------------------------------------------------------------
    // (#4) Lifecycle: connection close never tears down; a processed clean
    //      call retires a reference and, at zero, tears down.
    // ------------------------------------------------------------------

    @Test
    public void connectionClose_doesNotTearDown_whileReferenceLive()
            throws Exception {
        CountingLauncher launcher = new CountingLauncher();
        SubProcessPool pool =
                new SubProcessPool(launcher, new SubProcessAdminRegistry());
        IsolationPoolingKey k = key("spiffe://example/a");
        SubProcessHandle h = pool.obtain(k);

        Object ref = new Object();
        h.hostReference(ref);
        h.connectionClosed();          // NOT a DGC ack.
        h.connectionClosed();
        assertTrue("connection close must NOT tear down a live subprocess",
                h.isAlive());
        assertEquals("subprocess must not have been shut down", 0,
                launcher.shutdowns.get());
        assertSame("subprocess must still be pooled", h, pool.peek(k));
    }

    @Test
    public void processedCleanCall_tearsDownOnlyAfterLastReference()
            throws Exception {
        CountingLauncher launcher = new CountingLauncher();
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        SubProcessPool pool = new SubProcessPool(launcher, registry);
        IsolationPoolingKey k = key("spiffe://example/a");
        SubProcessHandle h = pool.obtain(k);

        Object r1 = new Object();
        Object r2 = new Object();
        h.hostReference(r1);
        h.hostReference(r2);

        boolean tore1 = h.referenceRetired(
                r1, SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED);
        assertFalse("must NOT tear down while another reference is live", tore1);
        assertTrue(h.isAlive());
        assertSame(h, pool.peek(k));

        boolean tore2 = h.referenceRetired(
                r2, SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED);
        assertTrue("last processed clean call must tear down", tore2);
        assertFalse(h.isAlive());
        assertEquals("subprocess must have been shut down", 1,
                launcher.shutdowns.get());
        assertNull("torn-down subprocess must be evicted from the pool",
                pool.peek(k));
        assertNull("registration must be removed on teardown", registry.lookup(k));
    }

    @Test
    public void cleanCallBeforeAnyReference_doesNotTearDown() throws Exception {
        // Guards against a spurious "zero references" teardown before the
        // subprocess has ever hosted anything.
        CountingLauncher launcher = new CountingLauncher();
        SubProcessPool pool =
                new SubProcessPool(launcher, new SubProcessAdminRegistry());
        SubProcessHandle h = pool.obtain(key("spiffe://example/a"));

        boolean tore = h.referenceRetired(
                new Object(),
                SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED);
        assertFalse("clean call before any reference must not tear down", tore);
        assertTrue(h.isAlive());
        assertEquals(0, launcher.shutdowns.get());
    }

    @Test
    public void leaseExpiry_retiresReferenceAndTearsDown() throws Exception {
        // A client that crashed / dropped without ever sending a clean call:
        // its DGC lease expires and that is a legitimate retirement trigger,
        // via the SAME event-agnostic API (no separate method / API break).
        CountingLauncher launcher = new CountingLauncher();
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        SubProcessPool pool = new SubProcessPool(launcher, registry);
        IsolationPoolingKey k = key("spiffe://example/a");
        SubProcessHandle h = pool.obtain(k);

        Object r1 = new Object();
        Object r2 = new Object();
        h.hostReference(r1);
        h.hostReference(r2);

        // Lease expiry retires a reference just like a processed clean call;
        // non-final retirement must not tear down.
        assertFalse("lease expiry must not tear down while another ref is live",
                h.referenceRetired(
                        r1, SubProcessHandle.RetirementReason.LEASE_EXPIRED));
        assertTrue(h.isAlive());

        // The final retirement (here a processed clean call) tears down; mixing
        // triggers across a subprocess's references is expected.
        assertTrue("last retirement must tear down regardless of reason",
                h.referenceRetired(
                        r2, SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED));
        assertFalse(h.isAlive());
        assertEquals(1, launcher.shutdowns.get());
        assertNull(pool.peek(k));
        assertNull(registry.lookup(k));
    }

    @Test
    public void afterTeardown_respawnCreatesFreshSubprocess() throws Exception {
        CountingLauncher launcher = new CountingLauncher();
        SubProcessPool pool =
                new SubProcessPool(launcher, new SubProcessAdminRegistry());
        IsolationPoolingKey k = key("spiffe://example/a");

        SubProcessHandle h1 = pool.obtain(k);
        Object r = new Object();
        h1.hostReference(r);
        // tears down
        h1.referenceRetired(
                r, SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED);
        assertFalse(h1.isAlive());

        SubProcessHandle h2 = pool.obtain(k);   // must respawn
        assertNotSame("a new subprocess must be spawned after teardown", h1, h2);
        assertTrue(h2.isAlive());
        assertEquals(2, launcher.launches.get());
    }

    // ------------------------------------------------------------------
    // Production default launcher fails closed (T4 not yet built).
    // ------------------------------------------------------------------

    @Test
    public void defaultLauncher_failsClosedPendingT4() throws Exception {
        SubProcessPool pool = new SubProcessPool(
                new SubProcessLauncher.UnsupportedSubProcessLauncher(),
                new SubProcessAdminRegistry());
        try {
            pool.obtain(key("spiffe://example/a"));
            fail("default launcher must fail closed, never fall back in-process");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("T4"));
        }
    }
}
