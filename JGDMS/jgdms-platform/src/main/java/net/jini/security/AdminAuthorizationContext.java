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
package net.jini.security;

import java.security.ProtectionDomain;
import javax.security.auth.Subject;

/**
 * Value-only carrier of a remote caller's <em>already-assembled</em>
 * authorization identity, handed from the JERI dispatch seam
 * ({@code net.jini.jeri.BasicInvocationDispatcher}) to a pure permission gate
 * such as {@code au.net.zeus.jgdms.loader.isolation.AdminPrincipalAuthenticator}.
 *
 * <h2>Why this type exists (2026-07-21 board-ratified "Option B")</h2>
 * The seam is the only place that can trustworthily determine a remote
 * caller's identity: it holds the verified mTLS connection (the authenticated
 * worker SPIFFE principal) and it is the single point that validates the
 * caller's JWT USER tokens and reconstructs their principals from the
 * <em>verified claims alone</em> (dropping every attacker-controlled wire
 * principal &mdash; see {@code JwtVerifier#verifyAndExtractPrincipals} and
 * {@code BasicInvocationDispatcher.readUserSubjects}).  Rather than have every
 * downstream gate re-derive identity from ambient state (which cannot see a
 * connection-derived caller, and which a hosted domain can forge by binding a
 * {@link Subject}), the seam assembles the identity once and conveys it here.
 * A gate consuming this carrier then becomes a pure
 * <em>identity&nbsp;&rarr;&nbsp;permit/deny</em> function.
 *
 * <p>The carrier holds two things:
 * <ul>
 *   <li>{@linkplain #domains() a {@code ProtectionDomain[]}} &mdash; the
 *       reducing authorization domains assembled by the seam.  Each element
 *       carries the caller's captured {@code CodeSource} /
 *       {@code DigestCodeSource} (or the empty {@code CodeSource} when no
 *       reducing context was transmitted) stamped with the union of the
 *       authenticated worker principal(s) and the verified USER principals.
 *       On the DirtyChai JDK each element is a {@code java.security.DomainIdentity}
 *       (a value-equality {@code ProtectionDomain} subtype the seam builds so the
 *       reconstructed context interns in the {@code AccessControlContext} cache);
 *       the field is typed as the {@code ProtectionDomain} supertype because
 *       {@code jgdms-platform} must remain source-compatible with a stock JDK
 *       (which lacks {@code DomainIdentity}), exactly as
 *       {@code net.jini.jeri.RemoteContextCodec} constructs the type reflectively.
 *       Every element is built with a {@code null} class loader, so
 *       {@link ProtectionDomain#getClassLoader()} on any element is {@code null}:
 *       no live {@code ClassLoader} capability is conveyed.</li>
 *   <li>{@linkplain #userSubjects() the verified user {@code Subject}s}
 *       &mdash; so the gate can re-establish them via {@code Subject.callAs}
 *       for the duration of the granted operation (belt-and-braces; the
 *       authorization decision itself rests on the {@code domains} array).</li>
 * </ul>
 *
 * <h2>Immutability and capability-leak discipline</h2>
 * This class is {@code final} (no overridable surface) and value-only: it
 * copies both arrays on construction and returns copies from its accessors, so
 * a holder cannot mutate the seam-assembled identity, and the only
 * {@link ProtectionDomain} handles it exposes carry {@code null} class loaders.
 * It performs no authorization itself.
 *
 * @since 3.1.1
 */
public final class AdminAuthorizationContext {

    private static final ProtectionDomain[] NO_DOMAINS = new ProtectionDomain[0];
    private static final Subject[] NO_SUBJECTS = new Subject[0];

    private final ProtectionDomain[] domains;
    private final Subject[] userSubjects;

    /**
     * Creates a carrier over the given seam-assembled domains and verified
     * user subjects.  Both arrays are defensively copied; {@code null} is
     * treated as empty, and {@code null} elements are dropped.
     *
     * @param domains      the reducing authorization domains (each element a
     *        {@code java.security.DomainIdentity} on DirtyChai, carrying the
     *        caller's captured code source stamped with the worker and verified
     *        user principals); may be {@code null} or empty
     * @param userSubjects the verified user {@link Subject}s the gate may
     *        re-establish via {@code Subject.callAs}; may be {@code null} or
     *        empty
     */
    public AdminAuthorizationContext(ProtectionDomain[] domains,
                                     Subject[] userSubjects) {
        this.domains = copyNonNull(domains, NO_DOMAINS, new ProtectionDomain[0]);
        this.userSubjects = copyNonNull(userSubjects, NO_SUBJECTS, new Subject[0]);
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] copyNonNull(T[] in, T[] empty, T[] proto) {
        if (in == null || in.length == 0) {
            return empty;
        }
        int n = 0;
        for (int i = 0; i < in.length; i++) {
            if (in[i] != null) n++;
        }
        if (n == 0) return empty;
        T[] out = java.util.Arrays.copyOf(proto, n);
        int j = 0;
        for (int i = 0; i < in.length; i++) {
            if (in[i] != null) out[j++] = in[i];
        }
        return out;
    }

    /**
     * @return a copy of the seam-assembled reducing authorization domains
     *         (never {@code null}; possibly empty).  Each element carries the
     *         caller's captured code source stamped with the worker and
     *         verified user principals, and has a {@code null} class loader.
     */
    public ProtectionDomain[] domains() {
        return domains.length == 0 ? NO_DOMAINS : domains.clone();
    }

    /**
     * @return a copy of the verified user {@link Subject}s (never {@code null};
     *         possibly empty), for {@code Subject.callAs} re-establishment of
     *         the acting user identity around the granted operation.
     */
    public Subject[] userSubjects() {
        return userSubjects.length == 0 ? NO_SUBJECTS : userSubjects.clone();
    }
}
