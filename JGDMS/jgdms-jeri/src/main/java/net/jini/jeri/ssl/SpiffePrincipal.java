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

import java.io.Serializable;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

/**
 * A {@link Principal} whose name is a
 * <a href="https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE-ID.md">SPIFFE ID</a>
 * URI (e.g. {@code spiffe://trust-domain/path}).
 *
 * <h2>Usage in Jini Configuration</h2>
 * <p>A deployer using {@link net.jini.jeri.ssl.SslServerEndpoint} can refer to
 * SPIFFE-authenticated services in the source for a
 * {@link net.jini.config.ConfigurationFile}:
 *
 * <pre>
 *  import net.jini.jeri.ssl.SpiffePrincipal;
 *  import net.jini.core.constraint.ServerMinPrincipal;
 *
 *  principal {
 *      static reggie = new SpiffePrincipal("spiffe://example.org/host/lookup");
 *      static tester = new SpiffePrincipal("spiffe://example.org/client/tester");
 *  }
 *
 *  org.apache.river.reggie {
 *      // LoginContext omitted: SpiffeCredentialManager is started at service boot
 *      private reggieConstraints = new StringMethodConstraints(
 *          new InvocationConstraints(
 *              new InvocationConstraint[] {
 *                  Integrity.YES,
 *                  ServerAuthentication.YES,
 *                  new ServerMinPrincipal(principal.reggie)
 *              }, null));
 *  }
 * </pre>
 *
 * <h2>Thread safety</h2>
 * <p>Instances of this class are immutable and therefore safe for concurrent
 * use by multiple threads.
 *
 * @since JGDMS 3.1
 * @see SpiffeCredentialManager
 */
public final class SpiffePrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    /** The SPIFFE URI scheme prefix. */
    private static final String SPIFFE_SCHEME = "spiffe://";

    /** The X.509 Subject Alternative Name type value for URI entries. */
    private static final int URI_SAN_TYPE = 6;

    /** The SPIFFE ID URI, e.g. {@code spiffe://trust-domain/path}. */
    private final String uri;

    /**
     * Creates a {@code SpiffePrincipal} with the given SPIFFE ID URI.
     *
     * @param uri the SPIFFE ID URI; must be non-null and start with
     *            {@code spiffe://}
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if {@code uri} does not start with
     *                                  {@code spiffe://}
     */
    public SpiffePrincipal(String uri) {
        if (uri == null) throw new NullPointerException("uri");
        if (!uri.startsWith(SPIFFE_SCHEME))
            throw new IllegalArgumentException(
                    "SPIFFE ID URI must start with \"spiffe://\": " + uri);
        this.uri = uri;
    }

    /**
     * Returns the SPIFFE ID URI, e.g. {@code spiffe://trust-domain/path}.
     *
     * @return the SPIFFE ID URI; never {@code null}
     */
    @Override
    public String getName() {
        return uri;
    }

    /**
     * Attempts to extract a {@code SpiffePrincipal} from the URI Subject
     * Alternative Name extension of an X.509 certificate.
     *
     * <p>Returns the first URI SAN whose value starts with {@code spiffe://},
     * or {@code null} if the certificate contains no such SAN.
     *
     * @param cert the X.509 certificate to inspect; must be non-null
     * @return the extracted {@code SpiffePrincipal}, or {@code null}
     */
    static SpiffePrincipal fromCertificate(X509Certificate cert) {
        Collection<List<?>> sans;
        try {
            sans = cert.getSubjectAlternativeNames();
        } catch (java.security.cert.CertificateParsingException e) {
            return null;
        }
        if (sans == null) return null;
        for (List<?> san : sans) {
            if (san.size() < 2) continue;
            Object typeObj = san.get(0);
            if (!(typeObj instanceof Integer)) continue;
            if ((Integer) typeObj != URI_SAN_TYPE) continue;
            Object value = san.get(1);
            if (value instanceof String) {
                String candidate = (String) value;
                if (candidate.startsWith(SPIFFE_SCHEME)) {
                    return new SpiffePrincipal(candidate);
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if {@code obj} is a {@code SpiffePrincipal} whose
     * URI equals this principal's URI (case-sensitive).
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SpiffePrincipal)) return false;
        return uri.equals(((SpiffePrincipal) obj).uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    /**
     * Returns a string representation of this principal, including the class
     * name and SPIFFE URI.
     */
    @Override
    public String toString() {
        return "SpiffePrincipal[" + uri + "]";
    }
}
