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
 * Thrown by a {@link JwtVerifier} when a raw JWT token fails verification.
 *
 * <p>Verification failures include (but are not limited to):
 * <ul>
 *   <li>expired token ({@code exp} claim in the past)</li>
 *   <li>token not yet valid ({@code iat} in the future)</li>
 *   <li>issuer mismatch ({@code iss} claim does not match configured issuer)</li>
 *   <li>audience mismatch ({@code aud} claim does not contain the expected value)</li>
 *   <li>invalid signature (if full JWKS verification is active)</li>
 * </ul>
 *
 * @since 3.1.1
 */
public class JwtVerificationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a {@code JwtVerificationException} with the given detail message.
     *
     * @param message the detail message
     */
    public JwtVerificationException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code JwtVerificationException} with the given detail message
     * and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public JwtVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
