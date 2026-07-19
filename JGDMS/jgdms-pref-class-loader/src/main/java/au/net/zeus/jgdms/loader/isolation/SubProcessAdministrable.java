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

/**
 * Administrative surface of an isolated smart-proxy subprocess (task&nbsp;T2,
 * requirement&nbsp;#3, criterion&nbsp;S1).
 *
 * <p>This is a <strong>deliberately parallel, non-colliding</strong> analogue
 * of {@link net.jini.admin.Administrable}: same accessor pattern (an accessor
 * returning a separate admin proxy) but a <em>distinct interface</em>.  The
 * hosted smart proxy / backend service may already legitimately implement
 * {@code Administrable} itself ({@code JoinAdmin} / {@code DestroyAdmin},
 * forwarded to the backend); reusing {@code getAdmin()} for
 * subprocess-policy administration would collide with and clobber that
 * legitimate surface.  The subprocess's policy-management authority therefore
 * lives on its own interface.
 *
 * <p>The accessor {@link #getSubProcessPolicyAdmin()} returns a
 * {@link PolicyAdmin} proxy to a separately-exported, subprocess-side trusted
 * management object &mdash; an object <em>genuinely distinct</em> from the
 * hosted business proxy; policy-management calls are never dispatched to or
 * through the hosted proxy instance.  The returned proxy's endpoint carries
 * stricter method constraints requiring the caller to authenticate as the
 * orchestrating admin principal ({@code Integrity.YES}).  That authentication
 * is the enforcement boundary: {@link #getSubProcessPolicyAdmin()} and every
 * {@link PolicyAdmin} operation fail closed to any caller that cannot so
 * authenticate, <em>regardless of what interfaces the caller's stub declares</em>
 * (declaration is a convention, never proof of authority).
 *
 * <p>No legitimate business smart proxy declares this interface.  A subprocess
 * refuses at reconstruction time (before export/dispatch) to host any proxy
 * whose <em>resolved</em> interface closure includes {@code SubProcessAdministrable}
 * or {@link PolicyAdmin} &mdash; checked on resolved type identity, never a
 * wire-name string match (see {@link HostedProxyGuard}).
 *
 * @since 3.1.1
 */
public interface SubProcessAdministrable {

    /**
     * Returns the policy-management proxy for this subprocess.
     *
     * <p><strong>Fails closed.</strong> The implementation must return nothing
     * usable (throw {@link SecurityException}) unless the current caller is
     * authenticated as the orchestrating admin principal.  A caller whose stub
     * merely declares this interface, without that authenticated identity, gets
     * no usable admin surface.
     *
     * @return the {@link PolicyAdmin} proxy for administering this subprocess'
     *         security policy
     * @throws RemoteException on communication failure
     * @throws SecurityException if the current caller is not authenticated as
     *         the orchestrating admin principal (fail closed)
     */
    PolicyAdmin getSubProcessPolicyAdmin() throws RemoteException;
}
