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
package net.jini.jeri.ssl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.Principal;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A {@link Principal} representing a SPIFFE workload identity, identified by
 * a SPIFFE ID URI of the form {@code spiffe://<trust-domain>/<path>}.
 *
 * <p>SPIFFE IDs are carried as URI SubjectAlternativeNames (type 6) in the
 * leaf certificate of an X.509 SVID (SPIFFE Verifiable Identity Document).
 * This class provides a typed Java principal for use in:
 * <ul>
 *   <li>JGDMS {@link javax.security.auth.Subject} principal sets, populated
 *       by {@link SpiffeCredentialManager} when loading SVIDs.</li>
 *   <li>Java security policy {@code principal} clauses:
 *       {@code grant principal net.jini.jeri.ssl.SpiffePrincipal
 *       "spiffe://example.org/svc/name" { ... }}.</li>
 *   <li>JERI constraint {@link net.jini.core.constraint.ServerMinPrincipal}
 *       and {@link net.jini.core.constraint.ClientMinPrincipal}.</li>
 * </ul>
 *
 * <h2>SPIFFE ID format</h2>
 * <p>A SPIFFE ID is a URI in the form {@code spiffe://<trust-domain>/<path>}
 * where {@code <trust-domain>} is a DNS name identifying the trust domain and
 * {@code <path>} is a workload-specific path segment.  Example:
 * <pre>
 *   spiffe://test.jgdms.local/svc/reggie
 *   spiffe://jgdms.example.org/host/lookup
 * </pre>
 *
 * <h2>Serialization</h2>
 * <p>{@code SpiffePrincipal} is serializable for use in distributed policy
 * decisions.  Deserialization validates the SPIFFE ID format.
 *
 * @see SpiffeCredentialManager
 * @see SpiffeLoginModule
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public final class SpiffePrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    /** The SPIFFE ID URI, e.g. {@code spiffe://example.org/svc/name}. */
    private final String spiffeId;

    /**
     * Creates a {@code SpiffePrincipal} for the given SPIFFE ID.
     *
     * @param spiffeId the SPIFFE ID URI; must start with {@code spiffe://}
     * @throws NullPointerException     if {@code spiffeId} is null
     * @throws IllegalArgumentException if {@code spiffeId} does not start with
     *                                  {@code spiffe://}
     */
    public SpiffePrincipal(String spiffeId) {
        if (spiffeId == null)
            throw new NullPointerException("spiffeId must not be null");
        if (!spiffeId.startsWith("spiffe://")
                || spiffeId.length() <= "spiffe://".length())
            throw new IllegalArgumentException(
                    "SPIFFE ID must start with 'spiffe://' and have a "
                    + "non-empty trust domain: " + spiffeId);
        this.spiffeId = spiffeId;
    }

    /**
     * Returns the SPIFFE ID URI.
     *
     * @return the SPIFFE ID; never {@code null}
     */
    @Override
    public String getName() {
        return spiffeId;
    }

    /**
     * Returns {@code true} if {@code obj} is a {@code SpiffePrincipal} with
     * the same SPIFFE ID URI.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SpiffePrincipal)) return false;
        return spiffeId.equals(((SpiffePrincipal) obj).spiffeId);
    }

    @Override
    public int hashCode() {
        return spiffeId.hashCode();
    }

    /**
     * Returns a string of the form {@code SpiffePrincipal(spiffe://...)}.
     */
    @Override
    public String toString() {
        return "SpiffePrincipal(" + spiffeId + ")";
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (spiffeId == null)
            throw new IOException("spiffeId must not be null");
        if (!spiffeId.startsWith("spiffe://")
                || spiffeId.length() <= "spiffe://".length())
            throw new IOException(
                    "Invalid SPIFFE ID on deserialization (must have a "
                    + "non-empty trust domain): " + spiffeId);
    }

    /**
     * Extracts SPIFFE IDs from the URI SubjectAlternativeNames of the given
     * X.509 certificate and returns them as {@code SpiffePrincipal} instances.
     *
     * <p>Per the SPIFFE X.509-SVID specification, a valid SVID leaf contains
     * exactly one URI SAN with the {@code spiffe://} scheme.  This method
     * returns all URI SANs that start with {@code spiffe://}, which may be
     * zero or more.
     *
     * @param cert the X.509 certificate to inspect; must be non-null
     * @return unmodifiable list of {@code SpiffePrincipal} objects, one per
     *         {@code spiffe://} URI SAN; empty if none are present
     * @throws NullPointerException     if {@code cert} is null
     * @throws IllegalArgumentException if the SAN extension cannot be parsed
     */
    public static List<SpiffePrincipal> fromCertificate(X509Certificate cert) {
        if (cert == null) throw new NullPointerException("cert must not be null");
        List<SpiffePrincipal> result = new ArrayList<>();
        Collection<List<?>> sans;
        try {
            sans = cert.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            throw new IllegalArgumentException(
                    "Cannot parse SubjectAlternativeNames from certificate: "
                    + cert.getSubjectX500Principal(), e);
        }
        if (sans == null) return Collections.unmodifiableList(result);
        for (List<?> san : sans) {
            // SAN type 6 = uniformResourceIdentifier
            if (san.size() >= 2 && Integer.valueOf(6).equals(san.get(0))) {
                Object value = san.get(1);
                if (value instanceof String) {
                    String uri = (String) value;
                    if (uri.startsWith("spiffe://")) {
                        result.add(new SpiffePrincipal(uri));
                    }
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}
