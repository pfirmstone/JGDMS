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

import java.security.cert.X509Certificate;

/**
 * SPI for obtaining the SPIRE trust bundle -- the set of certificate authorities
 * whose issued SVIDs this process should trust.
 *
 * <p>This interface exists to invert a dependency that cannot otherwise be
 * satisfied.  The workload credential manager that holds the trust bundle lives
 * either inside the DirtyChai JDK's {@code java.base} (package
 * {@code au.zeus.jdk.authorization.spire}, which is <em>not exported</em>) or in
 * test code.  Neither is reachable from {@code jgdms-discovery-providers} at
 * compile time, and JGDMS must continue to build and run on a stock JDK where no
 * such manager exists at all.  Rather than reach across that boundary
 * reflectively, the trust material is <em>pushed in</em> by whoever owns it.
 *
 * <p>This mirrors {@link LocalPrincipalProvider}, which breaks the same class of
 * cycle for principals.
 *
 * <h2>Rotation</h2>
 *
 * <p>SPIRE rotates issued SVIDs frequently (typically hourly) and may, more
 * rarely, rotate the CA bundle itself.  This interface therefore deliberately
 * exposes <em>no</em> listener registration: callers obtain the bundle afresh at
 * each use and implementations return whatever is current.  A cached bundle plus
 * a rotation callback would be equivalent when correct, but adds a listener
 * lifecycle that must be unregistered to avoid leaks, and a window in which a
 * stale bundle is used.  Re-reading is cheap relative to the certificate path
 * validation it feeds.
 *
 * <p><strong>Callers must not cache the returned array</strong> beyond the
 * single validation that requested it.
 *
 * <p>Implementations must be thread-safe.
 *
 * @see net.jini.security.Security#registerSpiffeTrustProvider(SpiffeTrustProvider)
 * @see net.jini.security.Security#spiffeTrustBundle()
 * @see LocalPrincipalProvider
 * @since 3.1.1
 */
public interface SpiffeTrustProvider {

    /**
     * Returns the certificate authorities currently trusted to issue SVIDs, or
     * {@code null} if no trust material is presently available.
     *
     * <p>Implementations should return a defensive copy, or an array the caller
     * may freely retain for the duration of one validation.  An empty array and
     * {@code null} are equivalent in effect: nothing can be validated.
     *
     * @return the current trust bundle, or {@code null} if unavailable
     */
    X509Certificate[] getTrustBundle();
}
