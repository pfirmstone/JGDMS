/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.x500.X500Principal;

/**
 * Canonical pooling key for smart-proxy OS-process isolation (task&nbsp;T2,
 * requirement&nbsp;#2).
 *
 * <p>The isolation subsystem pools <em>one OS subprocess per distinct remote
 * principal</em>.  This class derives the stable, canonical string that names
 * that pool slot from the {@code Principal[]} the TLS peer <em>proved</em>
 * during the handshake.  It is a <strong>pure function of canonical name
 * strings</strong> only &mdash; never of array index, {@link Object#hashCode()},
 * identity, or iteration order.  The {@code Principal[]} handed to
 * {@code route()} originates from a {@link java.util.HashSet}, so its iteration
 * order is non-deterministic; keying on order would silently split or merge
 * pools.  This class is deliberately order-insensitive.
 *
 * <p>Derivation rule (decided, do not loosen):
 * <ul>
 *   <li>A principal is <em>SPIFFE</em> iff {@code getName().startsWith(
 *       "spiffe://")} &mdash; on the <em>name</em>, never {@code instanceof};
 *       a SPIFFE id string is already RFC&nbsp;3986 canonical.</li>
 *   <li>Exactly one SPIFFE principal &rarr; key {@code "spiffe:" + name}.</li>
 *   <li>Zero SPIFFE principals &rarr; key {@code "x500:" +
 *       x500.getName(CANONICAL)} for the sole {@link X500Principal}
 *       (logged at {@code FINE} as an X.500 fallback).</li>
 *   <li><strong>More than one SPIFFE principal &rarr; fail closed</strong>
 *       ({@link IOException}).  A conformant SVID carries exactly one
 *       {@code spiffe://} SAN; multiplicity is non-conformant/ambiguous.
 *       Falling back to X.500 here would let an attacker suppress
 *       SPIFFE-based pooling by adding a second SAN, so we refuse rather
 *       than guess.</li>
 *   <li>Zero SPIFFE and not exactly one {@link X500Principal} &rarr; fail
 *       closed: there is no unambiguous identity to pool on.</li>
 * </ul>
 *
 * <p>Consistent with JGDMS's existing {@code PrincipalGrant} /
 * {@code UnresolvedPrincipal}, which identify principals by
 * {@code (class, getName())} and match via canonical-name equality.
 *
 * @since 3.1.1
 */
public final class IsolationPoolingKey {

    private static final Logger logger =
            Logger.getLogger(IsolationPoolingKey.class.getName());

    /** RFC-3986 SPIFFE scheme prefix, matched on the principal name. */
    public static final String SPIFFE_PREFIX = "spiffe://";

    private final String value;

    private IsolationPoolingKey(String value) {
        this.value = value;
    }

    /**
     * Derives the canonical pooling key from the TLS-authenticated remote
     * principals.
     *
     * @param serverPrincipals the principals the peer proved; must be non-null
     *        and non-empty (the caller fails closed before this point)
     * @return the canonical, order-insensitive pooling key
     * @throws IOException if the identity is ambiguous or non-conformant:
     *         more than one SPIFFE principal, or zero SPIFFE principals with
     *         other than exactly one {@link X500Principal}.  Refused, never
     *         guessed at (fail closed).
     */
    public static IsolationPoolingKey derive(Principal[] serverPrincipals)
            throws IOException {
        if (serverPrincipals == null || serverPrincipals.length == 0) {
            throw new IOException(
                "Cannot derive isolation pooling key: no authenticated"
                + " server principal (fail-closed).");
        }
        List<Principal> spiffe = new ArrayList<Principal>();
        List<X500Principal> x500 = new ArrayList<X500Principal>();
        for (int i = 0; i < serverPrincipals.length; i++) {
            Principal p = serverPrincipals[i];
            if (p == null) continue;
            String name = p.getName();
            if (name != null && name.startsWith(SPIFFE_PREFIX)) {
                spiffe.add(p);
            } else if (p instanceof X500Principal) {
                x500.add((X500Principal) p);
            }
            // Any other principal type is neither a SPIFFE id nor an X.500
            // identity; it is not eligible to key a pool and is ignored here
            // (it can never, on its own, satisfy a branch below).
        }

        if (spiffe.size() > 1) {
            // Fail closed on SPIFFE multiplicity.  Do NOT fall back to X.500:
            // that would let a second SAN suppress SPIFFE-based pooling.
            throw new IOException(
                "Refusing to derive isolation pooling key: " + spiffe.size()
                + " SPIFFE principals proved by the TLS peer, but a conformant"
                + " SVID has exactly one spiffe:// SAN.  Ambiguous/non-conformant"
                + " identity; refusing rather than guessing (fail-closed).");
        }
        if (spiffe.size() == 1) {
            // spiffe:// name is already RFC-3986 canonical.
            return new IsolationPoolingKey("spiffe:" + spiffe.get(0).getName());
        }
        // Zero SPIFFE principals: fall back to a single X.500 identity.
        if (x500.size() == 1) {
            String canonical = x500.get(0).getName(X500Principal.CANONICAL);
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                    "Smart-proxy isolation fell back to X.500 identity for the"
                    + " pooling key (no SPIFFE principal proved): {0}", canonical);
            }
            return new IsolationPoolingKey("x500:" + canonical);
        }
        throw new IOException(
            "Refusing to derive isolation pooling key: no SPIFFE principal and "
            + x500.size() + " X.500 principals; need exactly one unambiguous"
            + " identity to pool on (fail-closed).");
    }

    /**
     * Canonicalises a single principal to the same namespaced string used by
     * {@link #derive}, for admin-principal matching (requirement&nbsp;#3).  Not
     * a pooling key; a per-principal canonical form used to compare a caller's
     * proven identity against the required orchestrating-admin identity.
     *
     * @param p the principal (may be null)
     * @return canonical namespaced name, or {@code null} if {@code p} is null
     */
    public static String canonicalName(Principal p) {
        if (p == null) return null;
        String name = p.getName();
        if (name != null && name.startsWith(SPIFFE_PREFIX)) {
            return "spiffe:" + name;
        }
        if (p instanceof X500Principal) {
            return "x500:" + ((X500Principal) p).getName(X500Principal.CANONICAL);
        }
        return "other:" + p.getClass().getName() + ":" + name;
    }

    /** @return the canonical pooling-key string. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IsolationPoolingKey)) return false;
        return value.equals(((IsolationPoolingKey) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
