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

import java.security.Principal;

/**
 * SPI for providing the current process's local identity principals.
 *
 * <p>In JGDMS, SPIFFE workload identities are maintained by
 * {@code net.jini.jeri.ssl.SpiffeCredentialManager} which lives in the
 * {@code jgdms-jeri} module.  Because {@code jgdms-jeri} depends on
 * {@code jgdms-platform}, a direct call from {@link net.jini.security.Security}
 * (in {@code jgdms-platform}) to {@code SpiffeCredentialManager} would create a
 * circular dependency.
 *
 * <p>This interface breaks the cycle: {@code SpiffeCredentialManager.start()}
 * registers an implementation via
 * {@link net.jini.security.Security#registerLocalPrincipalProvider(LocalPrincipalProvider)},
 * and {@link net.jini.security.Security#currentPrincipals()} falls back to this
 * provider when no {@link javax.security.auth.Subject} is active in the current
 * {@link java.security.AccessControlContext}.
 *
 * <p>Implementations must be thread-safe.  The registered instance is cleared
 * by calling {@link net.jini.security.Security#registerLocalPrincipalProvider(LocalPrincipalProvider)}
 * with {@code null} (or by the credential manager when it is closed).
 *
 * @see net.jini.security.Security#currentPrincipals()
 * @see net.jini.security.Security#registerLocalPrincipalProvider(LocalPrincipalProvider)
 */
public interface LocalPrincipalProvider {

    /**
     * Returns the current process's local identity principals, or {@code null}
     * if no local identity is presently available.
     *
     * @return an array of local principals, or {@code null}
     */
    Principal[] getLocalPrincipals();
}
