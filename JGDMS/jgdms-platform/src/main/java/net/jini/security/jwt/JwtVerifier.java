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

/**
 * SPI for verifying a raw JWT token received on the JERI wire.
 *
 * <h2>Purpose</h2>
 * When a client sends user {@link javax.security.auth.Subject} data over JERI
 * protocol version {@code 0x02}, each Subject that was authenticated via JWT
 * also transmits the raw JWT compact-serialization string.  On the server
 * side, {@code BasicInvocationDispatcher} calls the configured
 * {@code JwtVerifier} (if any) to cryptographically or structurally verify
 * the token before the Subject's principals are accepted.
 *
 * <h2>Caching</h2>
 * The dispatcher maintains a bounded, expiry-aware cache keyed on the raw JWT
 * string.  A verifier implementation is therefore called <em>at most once</em>
 * per unique token per its validity window, not once per JERI call.  Expensive
 * operations such as JWKS HTTP lookups are amortised over the full lifetime of
 * the token.
 *
 * <h2>Opt-in</h2>
 * JWT verification is disabled by default (no verifier registered).  A
 * deployment that accepts JWT-bearing principals on trust of the SPIFFE SVID
 * alone can continue to do so without any change.  Register a verifier via
 * {@code BasicInvocationDispatcher.setJwtVerifier(JwtVerifier)} to enable
 * independent verification.
 *
 * <h2>Minimum viable implementation</h2>
 * {@code DefaultJwtVerifier} (in {@code jgdms-security-jwt}) checks the
 * {@code exp}, {@code iat}, {@code iss}, and {@code aud} claims without any
 * JWKS call, adding zero network dependency.  Full OIDC JWKS signature
 * verification is opt-in via a subclass or custom implementation.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent use by multiple threads.
 *
 * @since 3.1.1
 * @see net.jini.jeri.BasicInvocationDispatcher#setJwtVerifier(JwtVerifier)
 */
@FunctionalInterface
public interface JwtVerifier {

    /**
     * Verifies the supplied raw JWT compact-serialization string.
     *
     * <p>The implementation may perform any combination of:
     * <ul>
     *   <li>structural / claims validation ({@code exp}, {@code iat},
     *       {@code iss}, {@code aud})</li>
     *   <li>JWKS public-key signature verification</li>
     *   <li>custom business rules (token revocation list, etc.)</li>
     * </ul>
     *
     * <p>If verification succeeds the method returns normally.  If
     * verification fails the method throws {@link JwtVerificationException};
     * the dispatcher will then abort the request with an
     * {@link java.io.IOException} logged at {@code Level.WARNING}.
     *
     * @param rawJwt the JWT compact serialization ({@code header.payload.sig})
     * @throws JwtVerificationException if the token is invalid or rejected
     */
    void verify(String rawJwt) throws JwtVerificationException;

    /**
     * Verifies the token and returns the set of {@link Principal}s the
     * verified token's own claims authorise.
     *
     * <h2>Why the transport uses this, not the wire principals</h2>
     * A JWT-bearing user {@link javax.security.auth.Subject} transmits its
     * principals and its raw token as two independent wire fields.  The
     * principals are attacker-controlled: a malicious peer can present its
     * own genuine token while attaching an arbitrary principal set (for
     * example, the orchestrating admin's identity).  {@code
     * BasicInvocationDispatcher} therefore does not trust the transmitted
     * principals; it calls this method and <strong>reconstructs</strong> the
     * accepted principal set from the verified token alone, dropping every
     * wire principal the token does not justify.  A Subject that carries no
     * successfully-verified token contributes no principals at all.
     *
     * <p>The default implementation verifies via {@link #verify(String)} and
     * then derives principals with
     * {@link JwtPrincipalDeriver#derivePrincipals(String)} (the canonical
     * {@code sub}/{@code email}/{@code group} mapping used by {@code
     * JwtLoginModule}).  A custom verifier that maps claims to principals
     * differently &mdash; or that filters which claims may become principals
     * &mdash; should override this method; whatever it returns is the
     * authoritative accepted set for that token, so it must return
     * <em>only</em> principals the token genuinely authorises.
     *
     * @param rawJwt the JWT compact serialization ({@code header.payload.sig})
     * @return the principals authorised by the verified token; never
     *         {@code null}, possibly empty
     * @throws JwtVerificationException if the token is invalid or rejected
     *         (in which case no principals are authorised and the dispatcher
     *         aborts the request)
     */
    default java.util.Set<java.security.Principal> verifyAndExtractPrincipals(
            String rawJwt) throws JwtVerificationException
    {
        verify(rawJwt);
        return JwtPrincipalDeriver.derivePrincipals(rawJwt);
    }
}
