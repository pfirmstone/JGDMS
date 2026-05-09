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
import java.util.Objects;

/**
 * A {@link Principal} representing a single JWT claim value, suitable for use
 * in JAAS {@code Subject} instances and JGDMS policy grants.
 *
 * <h2>Name format</h2>
 * The name is formatted as {@code "claim:value"}, where {@code claim} is the
 * JWT claim name (e.g. {@code sub}, {@code email}, {@code group}) and
 * {@code value} is the claim value.  Examples:
 * <pre>
 *   new JwtPrincipal("sub:alice@example.org")
 *   new JwtPrincipal("email:alice@example.org")
 *   new JwtPrincipal("group:administrators")
 * </pre>
 *
 * <h2>Policy grant example</h2>
 * <pre>
 * grant codeBase "file:/opt/jgdms/order-svc/-"
 *       principal SpiffePrincipal "spiffe://.../host/selinux/order-svc"
 *       principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org" {
 *     permission OrderPermission "submit";
 * };
 * </pre>
 *
 * <h2>Multi-group handling</h2>
 * A JWT {@code groups} array produces one {@code JwtPrincipal} per group value:
 * <pre>
 *   JwtPrincipal("group:admins")
 *   JwtPrincipal("group:users")
 * </pre>
 *
 * <h2>Wire transmission</h2>
 * {@code JwtPrincipal} is transmitted over JERI by
 * {@code BasicInvocationHandler.writeUserPrincipals} as a
 * {@code (className, name)} pair and reconstructed on the receiving JVM by
 * {@code BasicInvocationDispatcher.instantiatePrincipal} via the public
 * {@code JwtPrincipal(String)} constructor.  No dispatcher changes are
 * required.  Standard Java serialization is intentionally <strong>not</strong>
 * supported; use the JERI wire protocol or {@code @AtomicSerial} if persistence
 * is ever required.
 *
 * @since 3.1.1
 */
public final class JwtPrincipal implements Principal {

    /** The {@code "claim:value"} string. */
    private final String name;

    /**
     * Constructs a {@code JwtPrincipal} with the given name.
     *
     * @param name the principal name in {@code "claim:value"} format; must not
     *             be {@code null} or blank, and must contain a {@code ':'}
     *             separator
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or does not
     *                                  contain a {@code ':'} separator
     */
    public JwtPrincipal(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank())
            throw new IllegalArgumentException("name must not be blank");
        if (!name.contains(":"))
            throw new IllegalArgumentException(
                    "JwtPrincipal name must be in 'claim:value' format, got: " + name);
        this.name = name;
    }

    /**
     * Returns the claim name portion (the part before the first {@code ':'}).
     *
     * @return the claim name, e.g. {@code "sub"} or {@code "group"}
     */
    public String getClaimName() {
        return name.substring(0, name.indexOf(':'));
    }

    /**
     * Returns the claim value portion (the part after the first {@code ':'}).
     *
     * @return the claim value, e.g. {@code "alice@example.org"} or {@code "admins"}
     */
    public String getClaimValue() {
        return name.substring(name.indexOf(':') + 1);
    }

    /** Returns the full {@code "claim:value"} name. */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JwtPrincipal && name.equals(((JwtPrincipal) obj).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "JwtPrincipal[" + name + "]";
    }
}
