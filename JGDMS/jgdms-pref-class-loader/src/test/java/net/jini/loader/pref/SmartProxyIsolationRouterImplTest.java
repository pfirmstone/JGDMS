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
package net.jini.loader.pref;

import au.net.zeus.jgdms.loader.isolation.SubProcessAdminRegistry;
import au.net.zeus.jgdms.loader.isolation.SubProcessAdministrable;
import au.net.zeus.jgdms.loader.isolation.SubProcessHandle;
import au.net.zeus.jgdms.loader.isolation.SubProcessLauncher;
import au.net.zeus.jgdms.loader.isolation.SubProcessPool;
import au.net.zeus.jgdms.loader.isolation.SubProcessWireHandoff;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.loader.pref.PreferredProxyCodebaseProvider.SmartProxyIsolationRouter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies the real T2 router: install wiring through the package-private
 * {@link PreferredProxyCodebaseProvider#setIsolationRouter}, key derivation,
 * pool reuse, the T4 handoff seam, and multiplicity fail-closed at
 * {@code route()}.
 */
public class SmartProxyIsolationRouterImplTest {

    private SmartProxyIsolationRouter saved;

    @Before public void setUp() {
        saved = PreferredProxyCodebaseProvider.getIsolationRouter();
    }
    @After public void tearDown() {
        PreferredProxyCodebaseProvider.setIsolationRouter(saved);
    }

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

    static final class FakeAdmin implements SubProcessAdministrable {
        public au.net.zeus.jgdms.loader.isolation.PolicyAdmin
                getSubProcessPolicyAdmin() throws RemoteException {
            throw new SecurityException("fail-closed (test stub)");
        }
    }

    static final class FakeLauncher implements SubProcessLauncher {
        final AtomicInteger launches = new AtomicInteger();
        public Spawned launch(
                au.net.zeus.jgdms.loader.isolation.IsolationPoolingKey key) {
            launches.incrementAndGet();
            final FakeAdmin admin = new FakeAdmin();
            return new Spawned() {
                public SubProcessAdministrable adminSurface() { return admin; }
                public void shutdown() { }
            };
        }
    }

    /** Records the handoff and returns a per-call sentinel stub. */
    static final class FakeHandoff implements SubProcessWireHandoff {
        volatile SubProcessHandle lastHandle;
        final AtomicInteger calls = new AtomicInteger();
        final Object sentinel = new Object();
        public Object handoff(SubProcessHandle handle,
                              CodebaseAccessor bootstrapProxy,
                              MarshalledInstance serviceProxy,
                              URL[] codebase, String path,
                              ClassLoader parent, ClassLoader verifier,
                              Collection context) {
            calls.incrementAndGet();
            lastHandle = handle;
            // Simulate hosting a live reference in the subprocess.
            handle.hostReference(new Object());
            return sentinel;
        }
    }

    private SmartProxyIsolationRouterImpl router(FakeLauncher l, FakeHandoff h) {
        return new SmartProxyIsolationRouterImpl(
                new SubProcessPool(l, new SubProcessAdminRegistry()), h);
    }

    @Test
    public void install_swapsRouterViaPackagePrivateSeam() {
        SmartProxyIsolationRouterImpl r =
                router(new FakeLauncher(), new FakeHandoff());
        SmartProxyIsolationRouterImpl.install(r);
        assertSame("install() must set the active router via setIsolationRouter",
                r, PreferredProxyCodebaseProvider.getIsolationRouter());
    }

    @Test
    public void route_derivesKey_reusesPool_callsHandoff() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        FakeHandoff handoff = new FakeHandoff();
        SmartProxyIsolationRouterImpl r = router(launcher, handoff);

        Principal[] p = new Principal[]{ name("spiffe://example/a") };
        Object r1 = r.route(p, null, null, new URL[0], "path",
                getClass().getClassLoader(), null, java.util.Collections.emptyList());
        Object r2 = r.route(p, null, null, new URL[0], "path",
                getClass().getClassLoader(), null, java.util.Collections.emptyList());

        assertSame(handoff.sentinel, r1);
        assertSame(handoff.sentinel, r2);
        assertEquals("handoff called per route", 2, handoff.calls.get());
        assertEquals("same principal must reuse one subprocess", 1,
                launcher.launches.get());
    }

    @Test
    public void route_twoDistinctPrincipals_neverPooledTogether() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        FakeHandoff handoff = new FakeHandoff();
        SmartProxyIsolationRouterImpl r = router(launcher, handoff);

        r.route(new Principal[]{ name("spiffe://example/a") }, null, null,
                new URL[0], "p", getClass().getClassLoader(), null,
                java.util.Collections.emptyList());
        SubProcessHandle first = handoff.lastHandle;
        r.route(new Principal[]{ name("spiffe://example/b") }, null, null,
                new URL[0], "p", getClass().getClassLoader(), null,
                java.util.Collections.emptyList());
        SubProcessHandle second = handoff.lastHandle;

        assertNotSame(first, second);
        assertEquals(2, launcher.launches.get());
        assertEquals(2, r.pool().size());
    }

    @Test
    public void route_multipleSpiffe_failsClosed_neverSpawns() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        FakeHandoff handoff = new FakeHandoff();
        SmartProxyIsolationRouterImpl r = router(launcher, handoff);
        try {
            r.route(new Principal[]{ name("spiffe://example/a"),
                                     name("spiffe://example/b") },
                    null, null, new URL[0], "p",
                    getClass().getClassLoader(), null,
                    java.util.Collections.emptyList());
            fail("multiple SPIFFE principals must fail closed at route()");
        } catch (IOException expected) { /* good */ }
        assertEquals("must not spawn on a fail-closed route", 0,
                launcher.launches.get());
        assertEquals("handoff must not run on a fail-closed route", 0,
                handoff.calls.get());
    }

    @Test
    public void defaultRouter_derivesKeyButFailsClosedAtSeam() throws Exception {
        // newDefault() uses the Unsupported launcher: derivation succeeds, then
        // it fails closed at the (T4) launch seam -- never an in-process load.
        SmartProxyIsolationRouterImpl r = SmartProxyIsolationRouterImpl.newDefault();
        try {
            r.route(new Principal[]{ name("spiffe://example/a") }, null, null,
                    new URL[0], "p", getClass().getClassLoader(), null,
                    java.util.Collections.emptyList());
            fail("default router must fail closed pending T4");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("T4"));
        }
    }
}
