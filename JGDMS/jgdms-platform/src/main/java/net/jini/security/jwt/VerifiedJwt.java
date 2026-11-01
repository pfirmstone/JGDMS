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
package net.jini.security.jwt;

import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The immutable result of a successful {@link JwtVerifier#verify(String)}:
 * the claims of a token that the verifier accepted, and the
 * {@link JwtPrincipal}s derived from them.
 *
 * <h2>Why the principals are derived here</h2>
 * A server must take the identity of a JWT-authenticated user from the token
 * it verified, never from principals the sender wrote beside the token.  The
 * mapping from claims to principals is therefore done in exactly one place,
 * {@link #derivePrincipals(String, String, List)}, which the constructor calls.
 * A verifier cannot hand back a principal that is not a function of the
 * claims it returns, and the dispatcher uses {@link #getPrincipals()} as is.
 *
 * <p>The mapping is: {@code JwtPrincipal("sub:" + sub)} when {@code sub} is
 * present, {@code JwtPrincipal("email:" + email)} when {@code email} is
 * present, and one {@code JwtPrincipal("group:" + g)} for each element of
 * {@code groups}.  It is the mapping the client side
 * ({@code JwtLoginModule}) applies when it builds the Subject it sends.
 *
 * <h2>Unauthenticated tokens</h2>
 * A token whose signature was not checked proves nothing, so a verifier that
 * did not authenticate the token must create its result with
 * {@link #unauthenticated(String, String, List, Instant)}; such a result
 * carries no principals.
 *
 * @since 3.1.1
 */
public final class VerifiedJwt {

    private final String subject;
    private final String issuer;
    private final List<String> audience;
    private final Instant expiry;
    private final String email;
    private final List<String> groups;
    private final Set<Principal> principals;

    /**
     * Creates the result for a token whose authenticity the verifier has
     * established.  The principals are derived from the arguments.
     *
     * @param subject  the {@code sub} claim, or {@code null}
     * @param issuer   the {@code iss} claim, or {@code null}
     * @param audience the {@code aud} claim as a list (a scalar is a list of
     *                 one), or {@code null} for none
     * @param expiry   the {@code exp} claim, or {@code null} if the token has
     *                 none (such a result is never cached by the dispatcher)
     * @param email    the {@code email} claim, or {@code null}
     * @param groups   the {@code groups} claim, or {@code null} for none
     * @throws IllegalArgumentException if a value cannot be a principal name
     */
    public VerifiedJwt(String subject, String issuer, List<String> audience,
                       Instant expiry, String email, List<String> groups) {
        this(subject, issuer, audience, expiry, email, groups, true);
    }

    private VerifiedJwt(String subject, String issuer, List<String> audience,
                        Instant expiry, String email, List<String> groups,
                        boolean authenticated) {
        this.subject  = subject;
        this.issuer   = issuer;
        this.audience = audience == null ? List.of() : List.copyOf(audience);
        this.expiry   = expiry;
        this.email    = email;
        this.groups   = groups == null ? List.of() : List.copyOf(groups);
        this.principals = authenticated
                ? derivePrincipals(subject, email, this.groups)
                : Collections.<Principal>emptySet();
    }

    /**
     * Creates the result for a token whose signature was NOT verified.  The
     * claims are recorded but {@link #getPrincipals()} is empty, so nothing
     * that consumes the result can be given an identity by it.
     */
    public static VerifiedJwt unauthenticated(String subject, String issuer,
                                              List<String> audience, Instant expiry) {
        return new VerifiedJwt(subject, issuer, audience, expiry, null, null, false);
    }

    /**
     * The one mapping from claims to principals.
     *
     * @return an immutable, insertion-ordered set; empty if every argument is
     *         {@code null} or empty
     */
    public static Set<Principal> derivePrincipals(String subject, String email,
                                                  List<String> groups) {
        Set<Principal> out = new LinkedHashSet<>();
        if (subject != null) out.add(new JwtPrincipal("sub:" + subject));
        if (email != null)   out.add(new JwtPrincipal("email:" + email));
        if (groups != null) {
            for (String g : groups) {
                out.add(new JwtPrincipal("group:" + Objects.requireNonNull(g, "group")));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** The {@code sub} claim, or {@code null}. */
    public String getSubject() { return subject; }

    /** The {@code iss} claim, or {@code null}. */
    public String getIssuer() { return issuer; }

    /** The {@code aud} claim as an immutable list; a scalar is a list of one. */
    public List<String> getAudience() { return audience; }

    /** The {@code exp} claim, or {@code null} if the token has none. */
    public Instant getExpiry() { return expiry; }

    /** The {@code email} claim, or {@code null}. */
    public String getEmail() { return email; }

    /** The {@code groups} claim as an immutable list; empty if absent. */
    public List<String> getGroups() { return groups; }

    /**
     * The principals derived from the verified claims; immutable.  Empty for
     * a result created by {@link #unauthenticated}.
     */
    public Set<Principal> getPrincipals() { return principals; }

    @Override
    public String toString() {
        return "VerifiedJwt[sub=" + subject + ", iss=" + issuer
                + ", principals=" + principals + "]";
    }
}
