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
 * side, {@code BasicInvocationDispatcher} calls the installed
 * {@code JwtVerifier} to verify the token.  A successful verification returns
 * a {@link VerifiedJwt}; the user principals of the call are the principals
 * derived from that value, not the principals the sender wrote on the wire.
 *
 * <h2>Contract change</h2>
 * Earlier versions declared {@code void verify(String)}.  A void verifier
 * cannot say whose token it accepted, so the dispatcher could not bind the
 * principals to it.  Implementations must now return the claims they
 * verified.  There is deliberately no adapter for a void verifier: one would
 * have to be treated as "accepts no identity", which is what a compile error
 * in the deployer's own code expresses more honestly.
 *
 * <h2>Caching</h2>
 * The dispatcher maintains a bounded, expiry-aware cache keyed on the SHA-256
 * of the raw JWT string and valued with the {@link VerifiedJwt}.  A verifier
 * implementation is therefore called <em>at most once</em> per unique token per
 * its validity window.  A cache hit yields the principals the verifier
 * derived, never wire principals.
 *
 * <h2>Opt-in</h2>
 * With no verifier installed a call that carries a JWT is refused.  Install a
 * verifier via {@code BasicInvocationDispatcher.setJwtVerifier(JwtVerifier)}.
 *
 * <h2>Provided implementation</h2>
 * {@link DefaultJwtVerifier} checks the JWS signature (RS256 or ES256, pinned
 * algorithm and keys) and the {@code exp}, {@code nbf}, {@code iat},
 * {@code iss}, {@code aud} and {@code typ} claims.
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
     * <p>If verification succeeds the method returns the verified claims.  If
     * verification fails the method throws {@link JwtVerificationException};
     * the dispatcher then aborts the request with an
     * {@link java.io.IOException}.  A result must not be built from a token
     * whose signature was not checked, unless it is created with
     * {@link VerifiedJwt#unauthenticated}, which carries no principals.
     *
     * @param rawJwt the JWT compact serialization ({@code header.payload.sig})
     * @return the verified claims; never {@code null}
     * @throws JwtVerificationException if the token is invalid or rejected
     */
    VerifiedJwt verify(String rawJwt) throws JwtVerificationException;
}
