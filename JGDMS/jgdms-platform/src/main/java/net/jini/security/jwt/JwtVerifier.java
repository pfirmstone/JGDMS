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
 * The dispatcher maintains a bounded cache of tokens the installed verifier
 * accepted, keyed on a digest of the token.  An entry lives until the earlier
 * of the token's {@code exp} and a fixed cap (300 seconds) and is discarded
 * when the verifier is replaced.  A token without a parseable future
 * {@code exp} is not cached and is verified on every call.
 *
 * <h2>Fail closed</h2>
 * With no verifier registered, a Subject that carries a JWT is refused.
 * Subjects that carry no JWT are unaffected.  Register a verifier via
 * {@code BasicInvocationDispatcher.setJwtVerifier(JwtVerifier)}.
 *
 * <h2>Implementation</h2>
 * {@code DefaultJwtVerifier} checks claims and, when constructed with a
 * pinned algorithm and trusted keys, the signature.  Its claims-only
 * constructors authenticate nothing.
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
}
