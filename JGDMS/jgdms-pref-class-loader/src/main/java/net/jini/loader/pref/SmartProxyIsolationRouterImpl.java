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

import au.net.zeus.jgdms.loader.isolation.IsolationPoolingKey;
import au.net.zeus.jgdms.loader.isolation.SubProcessAdminRegistry;
import au.net.zeus.jgdms.loader.isolation.SubProcessHandle;
import au.net.zeus.jgdms.loader.isolation.SubProcessLauncher;
import au.net.zeus.jgdms.loader.isolation.SubProcessPool;
import au.net.zeus.jgdms.loader.isolation.SubProcessWireHandoff;
import java.io.IOException;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.loader.pref.PreferredProxyCodebaseProvider.SmartProxyIsolationRouter;

/**
 * Real {@link SmartProxyIsolationRouter} for task&nbsp;T2: derives the
 * canonical per-principal pooling key, spawns-or-reuses that principal's
 * isolated subprocess (with its fail-closed admin surface and DGC-based
 * lifecycle registration), and hands the wire-level unmarshal off to the
 * task&nbsp;T4 seam.
 *
 * <p>Lives in {@code net.jini.loader.pref} because
 * {@link SmartProxyIsolationRouter} is a package-private nested interface of
 * {@link PreferredProxyCodebaseProvider}; it delegates all real work to the
 * {@code au.net.zeus.jgdms.loader.isolation} machinery. Install it through the
 * package-private {@link PreferredProxyCodebaseProvider#setIsolationRouter}
 * via {@link #install(SmartProxyIsolationRouterImpl)}.
 *
 * <p>Scope (per the T2 SOW): {@link #route} performs (a) key derivation,
 * (b) spawn-or-reuse of the subprocess including admin surface and lifecycle
 * registration, and (c) a call into the {@link SubProcessWireHandoff} seam for
 * the actual UDS byte handoff, which is task&nbsp;T4. With the default
 * (Unsupported) launcher/handoff installed, a real smart proxy still fails
 * closed with {@link UnsupportedOperationException} at the T4 seam &mdash; the
 * expected outcome until T4 lands, never a silent in-process fallback.
 *
 * @since 3.1.1
 */
public final class SmartProxyIsolationRouterImpl
        implements SmartProxyIsolationRouter {

    private final SubProcessPool pool;
    private final SubProcessWireHandoff wireHandoff;

    /**
     * @param pool        the per-principal subprocess pool; must not be null
     * @param wireHandoff the T4 wire-handoff seam; must not be null
     */
    public SmartProxyIsolationRouterImpl(SubProcessPool pool,
                                         SubProcessWireHandoff wireHandoff) {
        if (pool == null) throw new NullPointerException("pool");
        if (wireHandoff == null) throw new NullPointerException("wireHandoff");
        this.pool = pool;
        this.wireHandoff = wireHandoff;
    }

    /**
     * Builds a router with the production defaults: an
     * {@link SubProcessLauncher.UnsupportedSubProcessLauncher} and an
     * {@link SubProcessWireHandoff.UnsupportedSubProcessWireHandoff}. Until
     * task&nbsp;T4 supplies the real OS-process + UDS transport, this router
     * derives and pools keys but fails closed at the launch / handoff seam.
     *
     * @return a default-configured router
     */
    public static SmartProxyIsolationRouterImpl newDefault() {
        SubProcessPool pool = new SubProcessPool(
                new SubProcessLauncher.UnsupportedSubProcessLauncher(),
                new SubProcessAdminRegistry());
        return new SmartProxyIsolationRouterImpl(pool,
                new SubProcessWireHandoff.UnsupportedSubProcessWireHandoff());
    }

    /**
     * Installs {@code router} as the active isolation router via the
     * package-private {@link PreferredProxyCodebaseProvider#setIsolationRouter}.
     *
     * @param router the router to install; must not be null
     */
    public static void install(SmartProxyIsolationRouterImpl router) {
        PreferredProxyCodebaseProvider.setIsolationRouter(router);
    }

    /** @return the pool this router drives (management-plane access / tests). */
    public SubProcessPool pool() {
        return pool;
    }

    /** @return the management-plane registry (T4 grant-targeting seam). */
    public SubProcessAdminRegistry registry() {
        return pool.registry();
    }

    @Override
    public Object route(Principal[] serverPrincipals,
                        CodebaseAccessor bootstrapProxy,
                        MarshalledInstance serviceProxy,
                        URL[] codebase,
                        String path,
                        ClassLoader parent,
                        ClassLoader verifier,
                        Collection context)
            throws IOException, ClassNotFoundException {
        // (a) Canonical pooling key. Fails closed on ambiguous / non-conformant
        //     identity (>1 SPIFFE, or no unambiguous X.500 fallback).
        IsolationPoolingKey key = IsolationPoolingKey.derive(serverPrincipals);

        // (b) Spawn-or-reuse the subprocess for this principal, publishing its
        //     fail-closed admin surface in the registry and arming DGC-based
        //     teardown. Reuses an existing live subprocess for the same key.
        SubProcessHandle handle = pool.obtain(key);

        // (c) Hand off the wire-level unmarshal to the T4 seam and return the
        //     thin client-side stub it produces.
        return wireHandoff.handoff(handle, bootstrapProxy, serviceProxy,
                codebase, path, parent, verifier, context);
    }
}
