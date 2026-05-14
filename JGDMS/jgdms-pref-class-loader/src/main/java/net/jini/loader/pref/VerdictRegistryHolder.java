/*
 * Copyright 2018 The Apache Software Foundation.
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

import au.net.zeus.jgdms.api.codebase.VerdictRegistry;

/**
 * Package-private holder for the lazily-initialised {@link VerdictRegistry}
 * proxy.
 *
 * <p>Keeping the holder in a separate class avoids circular-dependency
 * concerns at boot time and provides a single, thread-safe injection point.
 *
 * <p>When {@link #get()} returns {@code null} the verdict check in
 * {@link PreferredProxyCodebaseProvider} is skipped entirely (boot-time
 * permissive policy — the check is only enforced once the node is fully
 * provisioned and the registry proxy has been injected via
 * {@link #set(VerdictRegistry)}).
 *
 * <p>Thread safety is provided by the {@code volatile} field; no additional
 * synchronization is required.
 *
 * @since 3.1.1
 * @author GitHub Copilot
 */
class VerdictRegistryHolder {

    /** The lazily-initialised registry proxy; {@code null} until injected. */
    private static volatile VerdictRegistry instance = null;

    /** Prevent instantiation. */
    private VerdictRegistryHolder() {}

    /**
     * Injects the {@link VerdictRegistry} proxy to use for subsequent
     * codebase verdict checks.  May be called at any time; subsequent calls
     * replace the previous value.
     *
     * @param registry the registry proxy; may be {@code null} to disable
     *                 verdict checking
     */
    static void set(VerdictRegistry registry) {
        instance = registry;
    }

    /**
     * Returns the currently-injected {@link VerdictRegistry} proxy, or
     * {@code null} if none has been set yet.
     *
     * @return the registry proxy, or {@code null}
     */
    static VerdictRegistry get() {
        return instance;
    }
}
