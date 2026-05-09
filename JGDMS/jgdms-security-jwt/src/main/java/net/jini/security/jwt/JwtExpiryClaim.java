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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A public credential stored in a {@link javax.security.auth.Subject} to
 * record the expiry instant of the JWT access token that was validated during
 * login.
 *
 * <p>The {@link JwtLoginModule} background refresh thread reads this credential
 * to determine when to proactively fetch a new access token (typically at
 * 80% of the remaining lifetime before expiry).
 *
 * @since 3.1.1
 */
public final class JwtExpiryClaim implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Instant expiry;

    /**
     * Constructs an instance with the given expiry instant.
     *
     * @param expiry the instant at which the access token expires; must not be null
     * @throws NullPointerException if {@code expiry} is null
     */
    public JwtExpiryClaim(Instant expiry) {
        this.expiry = Objects.requireNonNull(expiry, "expiry");
    }

    /**
     * Returns the expiry instant of the access token.
     *
     * @return the expiry instant; never null
     */
    public Instant getExpiry() {
        return expiry;
    }

    @Override
    public String toString() {
        return "JwtExpiryClaim[" + expiry + "]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JwtExpiryClaim && expiry.equals(((JwtExpiryClaim) o).expiry);
    }

    @Override
    public int hashCode() {
        return expiry.hashCode();
    }
}
