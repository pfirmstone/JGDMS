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

import java.rmi.RemoteException;
import org.apache.river.api.security.PermissionGrant;

/**
 * The subprocess-side <em>policy management</em> surface (task&nbsp;T2,
 * requirement&nbsp;#3).  A {@code PolicyAdmin} proxy administers the dynamic
 * security policy of one isolated smart-proxy subprocess &mdash; adding
 * grants, refreshing, querying the effective policy &mdash; and <strong>never
 * dispatches to the hosted business proxy</strong>.  It is backed by a
 * separately-exported trusted management object that is genuinely distinct
 * from the hosted object.
 *
 * <p>The proxy's endpoint carries stricter {@code MethodConstraints}
 * (integrity&nbsp;required, caller must authenticate as the orchestrating
 * admin principal); every operation additionally re-checks that the caller is
 * so authenticated and fails closed otherwise.  Authentication &mdash; not the
 * mere possession of this interface &mdash; is the enforcement boundary.
 *
 * <p><strong>Reject-on-load:</strong> no legitimate business smart proxy may
 * declare this interface (or {@link SubProcessAdministrable}); a subprocess
 * refuses to host any proxy whose resolved interface closure includes it.
 *
 * @since 3.1.1
 */
public interface PolicyAdmin {

    /**
     * Adds a dynamic permission grant to the subprocess policy.
     *
     * @param grant the grant to add; must not be {@code null}
     * @throws RemoteException on communication failure
     * @throws SecurityException if the caller is not authenticated as the
     *         orchestrating admin principal (fail closed)
     */
    void grant(PermissionGrant grant) throws RemoteException;

    /**
     * Reloads / recomputes the effective subprocess policy.
     *
     * @throws RemoteException on communication failure
     * @throws SecurityException if the caller is not authenticated as the
     *         orchestrating admin principal (fail closed)
     */
    void refresh() throws RemoteException;

    /**
     * Returns the grants currently in force in the subprocess policy.
     *
     * @return a snapshot of the effective grants (never {@code null})
     * @throws RemoteException on communication failure
     * @throws SecurityException if the caller is not authenticated as the
     *         orchestrating admin principal (fail closed)
     */
    PermissionGrant[] getGrants() throws RemoteException;
}
