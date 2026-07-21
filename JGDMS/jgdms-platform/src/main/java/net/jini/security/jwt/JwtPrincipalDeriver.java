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

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Derives the set of {@link JwtPrincipal}s that a JWT's claims authorise,
 * using the same {@code claim:value} convention that {@code JwtLoginModule}
 * (in {@code jgdms-security-jwt}) applies when it populates a client
 * {@link javax.security.auth.Subject}:
 * <ul>
 *   <li>{@code sub}   &rarr; {@code JwtPrincipal("sub:"   + value)}</li>
 *   <li>{@code email} &rarr; {@code JwtPrincipal("email:" + value)}</li>
 *   <li>each element of the {@code groups} array &rarr;
 *       {@code JwtPrincipal("group:" + value)}</li>
 * </ul>
 *
 * <h2>Why this exists (2026-07-21 forgery-hardening)</h2>
 * A JWT-bearing user {@code Subject} transmitted over JERI carries both its
 * principals <em>and</em> the raw token, as two independent wire fields.
 * Verifying the token proves the token is authentic, but it does
 * <strong>not</strong> tie the transmitted principals to it: a malicious
 * peer can attach an arbitrary principal set (e.g. the orchestrating admin's
 * identity) alongside its own genuine token.  The receiving transport must
 * therefore never trust the wire principals; it must <em>reconstruct</em> the
 * authorised principal set from the verified token's own claims and drop
 * anything the token does not justify.  This class is that reconstruction,
 * shared between the {@link JwtVerifier} SPI's default
 * {@link JwtVerifier#verifyAndExtractPrincipals(String)} and any custom
 * verifier that wishes to reuse the canonical mapping.
 *
 * <p><strong>Trust boundary:</strong> this method performs <em>no</em>
 * verification.  It must be called only on a token that has already passed
 * {@link JwtVerifier#verify(String)} (cryptographic signature and claim
 * validation).  Deriving principals from an unverified token would reproduce
 * the very forgery this hardening closes; the derived principals are only as
 * trustworthy as the verifier that vouched for the token.
 *
 * @since 3.1.1
 */
public final class JwtPrincipalDeriver {

    private JwtPrincipalDeriver() { }

    /**
     * Reconstructs the {@link JwtPrincipal} set authorised by an
     * already-verified JWT's claims (see the class documentation for the
     * mapping and the trust-boundary contract).
     *
     * <p>The token's payload is Base64url-decoded and parsed with the same
     * minimal, dependency-free claims parser {@link DefaultJwtVerifier} uses.
     * Insertion order ({@code sub}, {@code email}, then {@code groups} in
     * declaration order) is preserved via a {@link LinkedHashSet}.  If the
     * payload cannot be decoded or carries none of the recognised claims, an
     * empty set is returned &mdash; which, on the transport, means the
     * Subject ends up with no authorised principals (fail closed).
     *
     * @param rawJwt the JWT compact serialization ({@code header.payload.sig})
     *               that has <em>already</em> passed
     *               {@link JwtVerifier#verify(String)}; must not be
     *               {@code null}
     * @return the authorised principals, never {@code null}, possibly empty
     */
    public static Set<Principal> derivePrincipals(String rawJwt) {
        Set<Principal> principals = new LinkedHashSet<>();
        if (rawJwt == null) {
            return principals;
        }
        String[] parts = rawJwt.split("\\.", -1);
        if (parts.length < 2) {
            return principals;
        }
        Map<String, String> claims;
        try {
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8);
            claims = DefaultJwtVerifier.parseClaimsJson(payloadJson);
        } catch (RuntimeException malformed) {
            // Payload undecodable despite verification having passed: fail
            // closed by authorising no principals.
            return principals;
        }

        String sub = claims.get("sub");
        if (sub != null && !sub.isBlank()) {
            principals.add(new JwtPrincipal("sub:" + sub));
        }
        String email = claims.get("email");
        if (email != null && !email.isBlank()) {
            principals.add(new JwtPrincipal("email:" + email));
        }
        String groups = claims.get("groups");
        if (groups != null && !groups.isBlank()) {
            // parseClaimsJson joins JSON array elements with ',' — mirror
            // JwtLoginModule.commit()'s "one JwtPrincipal per group value".
            for (String group : groups.split(",", -1)) {
                String g = group.trim();
                if (!g.isEmpty()) {
                    principals.add(new JwtPrincipal("group:" + g));
                }
            }
        }
        return principals;
    }
}
