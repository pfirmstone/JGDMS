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

import net.jini.security.AccessPermission;

/**
 * Permission that expresses the access control policy for the subprocess
 * policy admin surface ({@link SubProcessAdministrable} /
 * {@link PolicyAdmin}) guarded by {@link AdminPrincipalAuthenticator}.
 * Mirrors the canonical shape of
 * {@code org.apache.river.reggie.proxy.RegistrarPermission}.
 * <p>
 * An instance contains a name (also referred to as a "target name") but no
 * actions list; you either have the named permission or you don't.  The
 * convention is that the target name is the name of the guarded admin
 * operation being invoked: {@code getSubProcessPolicyAdmin}, {@code grant},
 * {@code refresh} or {@code getGrants}.  Wildcard matches are supported
 * using the syntax specified by {@link AccessPermission}.
 * <p>
 * Per-operation targets exist so a deployed policy can follow least
 * privilege: {@code grant} is the dangerous operation (it installs new
 * authority into the subprocess policy) and can be granted more narrowly
 * than the read-only {@code getGrants}.  Because {@link
 * AccessPermission#implies} requires the checked permission to be an
 * instance of this exact class, a grant of a plain {@code AccessPermission}
 * (or of any other {@code AccessPermission} subclass) with the same target
 * name never satisfies a {@code SubProcessAdminPermission} check.
 *
 * @since 3.1.1
 */
public class SubProcessAdminPermission extends AccessPermission {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an instance with the specified target name.
     *
     * @param name the target name
     * @throws NullPointerException if the target name is <code>null</code>
     * @throws IllegalArgumentException if the target name does not match
     * the syntax specified in the comments at the beginning of the {@link
     * AccessPermission} class
     */
    public SubProcessAdminPermission(String name) {
	super(name);
    }
}
