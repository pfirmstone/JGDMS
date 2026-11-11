/*
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
 * Permission to tell {@link PreferredProxyCodebaseProvider} which server a
 * bootstrap proxy is already known to belong to, instead of letting the
 * provider establish that itself on the proxy's own connection.
 *
 * <p>Only a caller that has itself authenticated the server, and holds the
 * resulting pinned proxy, may assert the identity: today that is the
 * sub-process reconstruction server, acting for the parent process that did.
 * Anything that can assert an identity can choose which principal the digest
 * grants and the bootstrap check are issued for, so this must not be granted
 * to code that loads mobile code.
 *
 * <pre>
 * grant codeBase "file:/path/to/jgdms-pref-class-loader.jar" {
 *     permission net.jini.loader.pref.PinnedIdentityPermission "assertPinnedIdentity";
 * };
 * </pre>
 *
 * @see PreferredProxyCodebaseProvider#resolvePinned
 * @since 4.0
 */
public final class PinnedIdentityPermission extends BasicPermission {

    private static final long serialVersionUID = 1L;

    /** The only defined target name. */
    public static final String TARGET_NAME = "assertPinnedIdentity";

    /** Creates the permission with the target name {@value #TARGET_NAME}. */
    public PinnedIdentityPermission() {
        super(TARGET_NAME);
    }

    /**
     * Creates the permission with the given target name, for a policy file.
     *
     * @param name the target name
     */
    public PinnedIdentityPermission(String name) {
        super(name);
    }

    /**
     * Creates the permission for a policy file; the actions are ignored.
     *
     * @param name the target name
     * @param actions ignored
     */
    public PinnedIdentityPermission(String name, String actions) {
        super(name, actions);
    }
}
