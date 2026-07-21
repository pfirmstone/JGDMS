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
 * <h2>Unforgeable provenance (2026-07-21 forgery fix)</h2>
 * Because the constructor is public and
 * {@link net.jini.export.ServerContext#doWithServerContext} performs no
 * permission check, hosted code could once fabricate a carrier naming the admin
 * principal, publish it, and defeat the gate.  To close that, a carrier now
 * carries a private <em>provenance token</em>:
 * <ul>
 *   <li>the {@linkplain #AdminAuthorizationContext(ProtectionDomain[], Subject[])
 *       public constructor} produces an <strong>unauthenticated</strong> carrier
 *       (no token); {@link #isSeamMinted()} is {@code false}; and the gate
 *       refuses it (fail closed);</li>
 *   <li>{@link #seamMint(ProtectionDomain[], Subject[])} produces an
 *       <strong>authenticated</strong> carrier &mdash; but only after a
 *       {@link SecurityManager#checkPermission(java.security.Permission)
 *       permission check} for {@link AdminAuthorizationMintPermission}, which the
 *       deployed policy grants only to the trusted {@code jgdms-jeri} seam.</li>
 * </ul>
 * The token is a private reference to a class-private singleton: hosted code
 * cannot name it, cannot subclass this {@code final} class to override
 * {@link #isSeamMinted()}, and (short of deep reflection into this module's
 * non-open package, itself permission-guarded) cannot set the private field.
 * The gate therefore honors <em>only</em> seam-minted carriers.
 *
 * @since 3.1.1
 */
public final class AdminAuthorizationContext {

    private static final ProtectionDomain[] NO_DOMAINS = new ProtectionDomain[0];
    private static final Subject[] NO_SUBJECTS = new Subject[0];

    /**
     * Class-private provenance capability.  A carrier's {@code provenance} field
     * equals this reference <em>iff</em> the carrier was produced by the
     * permission-guarded {@link #seamMint}.  Hosted code cannot reference this
     * object, so it cannot construct an authenticated carrier by any public path.
     */
    private static final Object SEAM_PROVENANCE = new Object();

    /** The permission {@link #seamMint} requires; granted only to the seam. */
    private static final AdminAuthorizationMintPermission MINT_PERMISSION =
            new AdminAuthorizationMintPermission("mint");

    private final ProtectionDomain[] domains;
    private final Subject[] userSubjects;
    /** {@link #SEAM_PROVENANCE} for a seam-minted carrier; {@code null} otherwise. */
    private final Object provenance;

    /**
     * Creates an <strong>unauthenticated</strong> value carrier over the given
     * domains and user subjects.  Both arrays are defensively copied; {@code null}
     * is treated as empty, and {@code null} elements are dropped.
     *
     * <p><strong>A carrier built through this constructor carries no provenance
     * token</strong> ({@link #isSeamMinted()} is {@code false}); the admin gate
     * refuses it (fail closed).  Only {@link #seamMint} produces a carrier the
     * gate will honor.  This constructor stays public so the type remains a
     * plain value carrier, and so tests can reproduce the hosted-code forgery
     * shape.
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
        this(domains, userSubjects, null);
    }

    private AdminAuthorizationContext(ProtectionDomain[] domains,
                                      Subject[] userSubjects,
                                      Object provenance) {
        this.domains = copyNonNull(domains, NO_DOMAINS, new ProtectionDomain[0]);
        this.userSubjects = copyNonNull(userSubjects, NO_SUBJECTS, new Subject[0]);
        this.provenance = provenance;
    }

    /**
     * Mints an <strong>authenticated</strong> carrier &mdash; the only way to
     * obtain a carrier the admin gate will honor.  Callable only by code the
     * deployed policy grants {@link AdminAuthorizationMintPermission}
     * ({@code "mint"}); in production that is exclusively the trusted
     * {@code jgdms-jeri} dispatch seam.
     *
     * <p>When a {@link SecurityManager} is installed this performs
     * {@code sm.checkPermission(new AdminAuthorizationMintPermission("mint"))}
     * and throws {@link SecurityException} if the caller lacks it, so hosted code
     * cannot mint.  When no {@code SecurityManager} is installed the check is a
     * no-op &mdash; but so is the whole admin gate (it denies every caller with
     * no {@code SecurityManager}), so an authenticated carrier is inert.
     *
     * @param domains      as {@link #AdminAuthorizationContext(ProtectionDomain[],
     *        Subject[])}
     * @param userSubjects as {@link #AdminAuthorizationContext(ProtectionDomain[],
     *        Subject[])}
     * @return an authenticated carrier ({@link #isSeamMinted()} {@code == true})
     * @throws SecurityException if a {@code SecurityManager} is installed and the
     *         caller is not granted {@link AdminAuthorizationMintPermission}
     */
    public static AdminAuthorizationContext seamMint(ProtectionDomain[] domains,
                                                     Subject[] userSubjects) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(MINT_PERMISSION);
        }
        return new AdminAuthorizationContext(domains, userSubjects,
                                             SEAM_PROVENANCE);
    }

    /**
     * @return {@code true} iff this carrier was produced by the
     *         permission-guarded {@link #seamMint} (i.e. minted by the trusted
     *         JERI seam).  A carrier built through the public constructor &mdash;
     *         the shape hosted code can forge &mdash; returns {@code false}, and
     *         the admin gate refuses it.
     */
    public boolean isSeamMinted() {
        return provenance == SEAM_PROVENANCE;
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
