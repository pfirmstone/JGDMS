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
 * Permission required to allow loading a proxy codebase JAR whose verdict is
 * {@code INCONCLUSIVE} when <em>strict mode</em> is enabled.
 *
 * <p>When the system property {@value PreferredProxyCodebaseProvider#INCONCLUSIVE_STRICT_MODE_PROPERTY}
 * is set to {@code "true"}, {@link PreferredProxyCodebaseProvider} demands this
 * permission before proceeding with a codebase load whose
 * {@link VerdictRegistry} verdict is {@link VerdictType#INCONCLUSIVE}.  In
 * the default (non-strict) mode the load proceeds with a {@code WARNING} log
 * entry and no permission check, preserving backward-compatible behaviour.
 *
 * <p>The permission's <em>target name</em> is the lower-case hexadecimal
 * SHA-256 digest of the specific JAR (64 hex characters).  The wildcard name
 * {@code "*"} (inherited from {@link BasicPermission}) grants permission for
 * any INCONCLUSIVE JAR.  Only code in a highly-trusted domain (e.g. the
 * service administrator) should be granted the wildcard form; granting by
 * individual hash is preferred so that the set of tolerated INCONCLUSIVE
 * JARs is explicit and auditable.
 *
 * <h2>Policy file examples</h2>
 * Grant permission for a specific JAR digest:
 * <pre>
 * grant codeSource("file:/trusted/path/-") {
 *     permission net.jini.loader.pref.INCONCLUSIVEPermit
 *         "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
 * };
 * </pre>
 *
 * Grant permission for all INCONCLUSIVE JARs (use with care):
 * <pre>
 * grant codeSource("file:/trusted/path/-") {
 *     permission net.jini.loader.pref.INCONCLUSIVEPermit "*";
 * };
 * </pre>
 *
 * @see PreferredProxyCodebaseProvider
 * @see VerdictType#INCONCLUSIVE
 * @since 3.1.1
 * @author Peter Firmstone
 */
public final class INCONCLUSIVEPermit extends BasicPermission {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an {@code INCONCLUSIVEPermit} whose target name is the given
     * SHA-256 hex digest of the specific JAR (64 lower-case hex characters),
     * or {@code "*"} to match any JAR.
     *
     * @param name the SHA-256 hex digest string or {@code "*"}
     * @throws NullPointerException if {@code name} is null
     */
    public INCONCLUSIVEPermit(String name) {
        super(name);
    }

    /**
     * Creates an {@code INCONCLUSIVEPermit} with the given target name and
     * actions.  The {@code actions} parameter is ignored (this permission has
     * no actions) and exists only so that this class can be used by policy
     * tools that require the two-argument constructor.
     *
     * @param name    the SHA-256 hex digest string or {@code "*"}
     * @param actions ignored; must be {@code null} or empty string
     */
    public INCONCLUSIVEPermit(String name, String actions) {
        super(name, actions);
    }
}
