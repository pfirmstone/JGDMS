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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Management-plane registry keyed by the canonical {@link IsolationPoolingKey}
 * (task&nbsp;T2, requirement&nbsp;#3).  It resolves a pooling key to that
 * subprocess's authenticated {@link SubProcessAdministrable} surface so a
 * sibling task ({@code SubProcessDynamicPolicy}, T4) can target a policy grant
 * at a subprocess by its principal identity.
 *
 * <p><strong>Management plane only.</strong> The registry exposes only the
 * {@link SubProcessAdministrable} surface &mdash; whose accessor is itself
 * fail-closed &mdash; and <em>never</em> the hosted business-proxy instances.
 * Holding a registry handle grants no business access and, absent
 * authentication as the admin principal, no usable admin access either.
 *
 * @since 3.1.1
 */
public final class SubProcessAdminRegistry {

    private final ConcurrentMap<IsolationPoolingKey, SubProcessAdministrable>
            byKey = new ConcurrentHashMap<IsolationPoolingKey,
                                          SubProcessAdministrable>();

    /**
     * Registers the admin surface for a subprocess.  Idempotent per key: the
     * first registration for a key wins (one subprocess per principal).
     *
     * @param key   the canonical pooling key; must not be null
     * @param admin the subprocess admin surface; must not be null
     * @return the surface already registered for {@code key}, or {@code null}
     *         if this registration is the first
     */
    public SubProcessAdministrable register(IsolationPoolingKey key,
                                            SubProcessAdministrable admin) {
        if (key == null) throw new NullPointerException("key");
        if (admin == null) throw new NullPointerException("admin");
        return byKey.putIfAbsent(key, admin);
    }

    /**
     * Resolves the admin surface for a pooling key.  Returns the
     * {@link SubProcessAdministrable} handle, whose {@code getSubProcessPolicyAdmin()}
     * is itself fail-closed to non-admin callers.
     *
     * @param key the canonical pooling key
     * @return the admin surface, or {@code null} if no subprocess is registered
     */
    public SubProcessAdministrable lookup(IsolationPoolingKey key) {
        if (key == null) return null;
        return byKey.get(key);
    }

    /**
     * Removes the registration for a key (subprocess teardown).
     *
     * @param key the canonical pooling key
     */
    public void unregister(IsolationPoolingKey key) {
        if (key != null) byKey.remove(key);
    }

    /** @return number of registered subprocesses (for tests / diagnostics). */
    public int size() {
        return byKey.size();
    }
}
