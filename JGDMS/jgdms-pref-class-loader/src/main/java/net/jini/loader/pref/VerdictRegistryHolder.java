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
 * proxy and its optional read-replica fallback list.
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
 * <p>Operators that deploy {@code ReadReplicaVerdictRegistry} for high
 * availability should call {@link #setInstances(VerdictRegistry[])} with an
 * ordered array of [primary, replica-1, replica-2, …].  {@link #get()}
 * returns the first non-null element; {@link #getAll()} returns the full array
 * for fallback iteration in
 * {@link PreferredProxyCodebaseProvider#getVerdictByHashWithRetry}.
 *
 * <p>Thread safety is provided by {@code volatile} fields; no additional
 * synchronization is required for reads.
 *
 * @since 3.1.1
 * @author GitHub Copilot
 */
class VerdictRegistryHolder {

    /** Single-registry fast path; backward compatible with {@link #set(VerdictRegistry)}. */
    private static volatile VerdictRegistry instance = null;

    /**
     * Ordered fallback list: [primary, replica-1, replica-2, …].  When
     * non-null this array is preferred over {@link #instance}.
     */
    private static volatile VerdictRegistry[] instances = null;

    /** Prevent instantiation. */
    private VerdictRegistryHolder() {}

    /**
     * Injects the primary {@link VerdictRegistry} proxy.  Backward-compatible
     * single-registry entry point; clears any previously-set fallback list.
     *
     * @param registry the registry proxy; may be {@code null} to disable
     *                 verdict checking
     */
    static void set(VerdictRegistry registry) {
        instances = null;
        instance  = registry;
    }

    /**
     * Injects an ordered array of {@link VerdictRegistry} proxies.  The
     * first non-null element is returned by {@link #get()}; all elements are
     * returned by {@link #getAll()} for fallback iteration.
     *
     * @param registries the ordered array [primary, replica-1, …]; a
     *                   {@code null} or empty array clears the list
     */
    static void setInstances(VerdictRegistry[] registries) {
        if (registries == null || registries.length == 0) {
            instances = null;
            instance  = null;
            return;
        }
        VerdictRegistry[] copy = registries.clone();
        instances = copy;
        instance  = copy[0];
    }

    /**
     * Returns the first non-null {@link VerdictRegistry} in the configured
     * list, or {@code null} if none has been set yet.
     *
     * @return the primary registry proxy, or {@code null}
     */
    static VerdictRegistry get() {
        VerdictRegistry[] arr = instances;
        if (arr != null) {
            for (VerdictRegistry vr : arr) {
                if (vr != null) return vr;
            }
            return null;
        }
        return instance;
    }

    /**
     * Returns all configured {@link VerdictRegistry} instances (primary +
     * replicas) in preference order, or a single-element array containing
     * {@link #instance} if no list has been set.  Returns an empty array
     * when no registry has been configured.
     *
     * @return an ordered snapshot; never {@code null}
     */
    static VerdictRegistry[] getAll() {
        VerdictRegistry[] arr = instances;
        if (arr != null) {
            return arr.clone();
        }
        VerdictRegistry single = instance;
        return single != null ? new VerdictRegistry[]{single} : new VerdictRegistry[0];
    }
}
