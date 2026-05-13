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

import java.util.Objects;

/**
 * A public credential stored in a {@link javax.security.auth.Subject} to
 * carry the raw JWT compact-serialization string for wire transmission.
 *
 * <h2>Purpose</h2>
 * When a JGDMS client authenticates via {@code JwtLoginModule}, both
 * {@code JwtPrincipal} instances (extracted claims) and one {@code JwtRawToken}
 * are added to the Subject's public credentials.  When
 * {@code BasicInvocationHandler.writeUserSubjects} serialises the Subject over
 * JERI protocol version {@code 0x02}, the raw token is transmitted alongside
 * the principals so that the server-side {@link JwtVerifier} can perform
 * independent cryptographic verification.
 *
 * <h2>Why public (not private)?</h2>
 * {@code writeUserSubjects} executes outside a {@code Subject.doAs} context
 * and therefore cannot access private credentials.  The raw JWT token is not
 * a cryptographic secret: it is already transmitted in the JERI wire frame
 * and is available to the server.  Placing it in public credentials allows
 * the transport layer to read it without privilege escalation.
 *
 * <h2>Security note</h2>
 * The raw JWT compact serialization contains the signed claims of the
 * authenticated user.  Do not expose this value to untrusted code via policy
 * grants or reflective access.
 *
 * @since 3.1.1
 */
public final class JwtRawToken {

    /** The raw JWT compact-serialization string. */
    private final String token;

    /**
     * Constructs a {@code JwtRawToken} with the given raw JWT string.
     *
     * @param token the JWT compact serialization ({@code header.payload.sig});
     *              must not be {@code null} or blank
     * @throws NullPointerException     if {@code token} is {@code null}
     * @throws IllegalArgumentException if {@code token} is blank
     */
    public JwtRawToken(String token) {
        Objects.requireNonNull(token, "token");
        if (token.isBlank())
            throw new IllegalArgumentException("token must not be blank");
        this.token = token;
    }

    /**
     * Returns the raw JWT compact-serialization string.
     *
     * @return the JWT string; never {@code null}
     */
    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JwtRawToken && token.equals(((JwtRawToken) o).token);
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public String toString() {
        // Avoid logging the full token; show only the header portion.
        int dot = token.indexOf('.');
        String header = dot > 0 ? token.substring(0, dot) : token;
        return "JwtRawToken[header=" + header + "...]";
    }
}
