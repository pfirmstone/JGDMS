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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-principal isolated-subprocess pool (task&nbsp;T2, requirement&nbsp;#1):
 * <strong>one OS subprocess per distinct remote principal</strong>, shared
 * across that principal's proxies, spawned on first need and reused thereafter.
 *
 * <p>The pool is keyed exclusively by the canonical {@link IsolationPoolingKey}
 * (requirement&nbsp;#2), so two distinct principals can never be pooled into
 * the same subprocess (distinct canonical keys &rArr; distinct map entries),
 * and the same principal always reuses its one subprocess (equal canonical
 * keys &rArr; one entry). Never keyed on object identity, hash, or order.
 *
 * <p>On spawn, the subprocess's fail-closed {@link SubProcessAdministrable}
 * surface is published in the {@link SubProcessAdminRegistry} under the same
 * key (management plane only). On DGC-driven teardown
 * ({@link SubProcessHandle#referenceRetired}) the entry is evicted and the
 * registration removed.
 *
 * @since 3.1.1
 */
public final class SubProcessPool {

    private final SubProcessLauncher launcher;
    private final SubProcessAdminRegistry registry;
    private final ConcurrentMap<IsolationPoolingKey, SubProcessHandle> live =
            new ConcurrentHashMap<IsolationPoolingKey, SubProcessHandle>();
    private final Object spawnLock = new Object();

    /**
     * @param launcher spawns the OS subprocess + admin surface (T4 supplies
     *        the real one; a fake is injected for tests); must not be null
     * @param registry management-plane registry to publish admin surfaces in;
     *        must not be null
     */
    public SubProcessPool(SubProcessLauncher launcher,
                          SubProcessAdminRegistry registry) {
        if (launcher == null) throw new NullPointerException("launcher");
        if (registry == null) throw new NullPointerException("registry");
        this.launcher = launcher;
        this.registry = registry;
    }

    /** @return the management-plane admin registry backing this pool. */
    public SubProcessAdminRegistry registry() {
        return registry;
    }

    /**
     * Returns the subprocess for {@code key}, spawning it on first need and
     * reusing an existing live one otherwise.
     *
     * <p>The returned handle is guaranteed to already hold a live handout
     * {@link SubProcessHandle#reserve() reservation} at the moment of return:
     * "select-or-spawn and pin" is performed atomically, so a caller never
     * receives a handle that a concurrent last-reference retirement can tear
     * down before the caller pins its own reference via
     * {@link SubProcessHandle#hostReference}.  If the reused handle is found to
     * be tearing down (its {@code reserve()} refuses), {@code obtain()}
     * respawns a fresh subprocess, replacing the stale mapping.
     *
     * @param key the canonical pooling key
     * @return the live, reservation-pinned subprocess handle for that principal
     * @throws IOException if a subprocess must be spawned but cannot be
     */
    public SubProcessHandle obtain(IsolationPoolingKey key) throws IOException {
        if (key == null) throw new NullPointerException("key");
        // Fast path: reuse an existing handle, but only if we can pin a
        // reservation on it atomically (rejects one racing into teardown).
        SubProcessHandle existing = live.get(key);
        if (existing != null && existing.reserve()) {
            return existing;
        }
        synchronized (spawnLock) {
            // Re-check under the lock: another thread may have spawned it.
            // Again pin atomically; a torn-down handle refuses and we respawn.
            existing = live.get(key);
            if (existing != null && existing.reserve()) {
                return existing;
            }
            SubProcessLauncher.Spawned spawned = launcher.launch(key);
            final SubProcessHandle[] holder = new SubProcessHandle[1];
            SubProcessHandle handle = new SubProcessHandle(key, spawned,
                new Runnable() {
                    @Override public void run() {
                        // Evict only if the map still points at THIS handle,
                        // so a subsequent respawn for the same key is not
                        // clobbered by a late teardown of the old one.
                        live.remove(key, holder[0]);
                        SubProcessAdministrable current = registry.lookup(key);
                        if (current == holder[0].adminSurface()) {
                            registry.unregister(key);
                        }
                    }
                });
            holder[0] = handle;
            // Pin the first reservation before publishing/returning, so the
            // handle is never handed out unpinned.
            handle.reserve();
            registry.register(key, handle.adminSurface());
            // Unconditionally replace any stale (torn-down) mapping for key.
            live.put(key, handle);
            return handle;
        }
    }

    /** @return number of live pooled subprocesses (tests / diagnostics). */
    public int size() {
        return live.size();
    }

    /**
     * @param key a canonical pooling key
     * @return the live handle for {@code key}, or {@code null} if none
     */
    public SubProcessHandle peek(IsolationPoolingKey key) {
        return live.get(key);
    }
}
