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

import java.security.BasicPermission;

/**
 * Permission that must be granted to a server's principal (typically a
 * {@code au.net.zeus.jgdms.spiffe.SpiffePrincipal}) to allow that server to act
 * as a trusted codebase source during the client bootstrap phase.
 *
 * <p>During the boot window — before a {@link VerdictRegistry} is available —
 * {@link PreferredProxyCodebaseProvider} checks whether the authenticated
 * server's principal holds this permission in the local security policy.  Only
 * principals of trusted lookup services and verdict registries should be
 * granted this permission.  Once the {@link VerdictRegistry} is installed, the
 * existing verdict-based gate takes over as the primary enforcement mechanism.
 *
 * <p>This permission contains a name (also referred to as a "target name") but
 * no action list; you either have the named permission or you don't.  The only
 * defined target name is {@value #TARGET_NAME}.
 *
 * <h2>Policy file example</h2>
 * <pre>
 * grant principal au.net.zeus.jgdms.spiffe.SpiffePrincipal
 *         "spiffe://trust.domain/svc/reggie" {
 *     permission net.jini.loader.pref.BootstrapPermission "loadCodebase";
 * };
 * </pre>
 *
 * @see PreferredProxyCodebaseProvider
 * @since 3.1.1
 * @author Peter Firmstone
 */
public final class BootstrapPermission extends BasicPermission {

    private static final long serialVersionUID = 1L;

    /** The only defined target name for this permission. */
    public static final String TARGET_NAME = "loadCodebase";

    /**
     * Creates a {@code BootstrapPermission} with the target name
     * {@value #TARGET_NAME}.
     */
    public BootstrapPermission() {
        super(TARGET_NAME);
    }

    /**
     * Creates a {@code BootstrapPermission} with the given target name.
     *
     * <p>The only valid target name is {@value #TARGET_NAME}.  Future
     * extensions may define additional target names using the wildcard
     * naming conventions of {@link BasicPermission}.
     *
     * @param name the target name
     * @throws NullPointerException if {@code name} is null
     */
    public BootstrapPermission(String name) {
        super(name);
    }

    /**
     * Creates a {@code BootstrapPermission} with the given target name and
     * actions.  The {@code actions} parameter is ignored (this permission
     * has no actions) and exists only so that this class can be used by
     * tools that expect this two-argument constructor.
     *
     * @param name    the target name
     * @param actions ignored; must be {@code null} or empty string
     */
    public BootstrapPermission(String name, String actions) {
        super(name, actions);
    }
}
