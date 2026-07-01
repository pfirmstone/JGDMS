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
package au.net.zeus.jgdms.spiffe;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.net.Uri;

/**
 * A {@link Principal} representing a SPIFFE workload identity, identified by
 * a SPIFFE ID URI of the form {@code spiffe://<trust-domain>/<path>}.
 *
 * <p>SPIFFE IDs are carried as URI SubjectAlternativeNames (type 6) in the
 * leaf certificate of an X.509 SVID (SPIFFE Verifiable Identity Document).
 * This class provides a typed Java principal for use in JERI constraints
 * ({@link net.jini.core.constraint.ServerMinPrincipal} /
 * {@link net.jini.core.constraint.ClientMinPrincipal}).
 *
 * <h2>Downloadable, marshalled type</h2>
 * <p>{@code SpiffePrincipal} lives in a download ({@code -dl}) module because it
 * travels inside marshalled method constraints.  Placing it in a non-downloaded
 * module (on the receiver's local classpath) would raise
 * {@link ClassNotFoundException} on an earlier-version peer whose local jars
 * predate the class; carried in the sender's codebase, it is downloaded instead.
 * Accordingly the transport resolves this class <em>by name</em> and never
 * compile-depends on it.
 *
 * <h2>Serialization</h2>
 * <p>{@code SpiffePrincipal} is an {@link AtomicSerial} type, so it is marshalled
 * via the AtomicSerial wire protocol (atomic validation, no arbitrary-object
 * instantiation) rather than Java Serialization.  It is deliberately <em>not</em>
 * {@link java.io.Serializable}: the SPIFFE ID travels by value as a single
 * {@code spiffeId} string and its format is validated atomically during
 * deserialization, before the instance is constructed.
 *
 * <h2>Canonical form and matching</h2>
 * <p>The constructors normalize the SPIFFE ID through {@link Uri} (RFC 3986), so
 * {@link #getName()} always returns the canonical URI.  {@code getName()} is the
 * match key for constraint matching: because the authenticated peer identity may
 * be a different {@code SpiffePrincipal} implementation (for example the JDK's
 * read-only {@code RemoteSubject} principal), matching is by canonical
 * {@code getName()} value, not by class-exact {@code equals}.  Both
 * implementations normalize through the same RFC 3986 {@code Uri}, so their
 * canonical {@code getName()} values are identical for the same SPIFFE ID.
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
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public final class SpiffePrincipal implements Principal {

    private static final String SPIFFE_SCHEME = "spiffe://";

    /** The canonical SPIFFE ID URI, e.g. {@code spiffe://example.org/svc/name}. */
    private final String spiffeId;

    /**
     * Defines the {@link AtomicSerial} serial form: a single {@code spiffeId}
     * string field.
     *
     * @return the serial form of this class
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm("spiffeId", String.class)
        };
    }

    /**
     * Writes the serial form of the given {@code SpiffePrincipal}.
     *
     * @param arg the AtomicSerial output argument
     * @param p   the principal to serialize
     * @throws IOException if there are I/O errors while writing to the stream
     */
    public static void serialize(PutArg arg, SpiffePrincipal p) throws IOException {
        arg.put("spiffeId", p.spiffeId);
        arg.writeArgs();
    }

    /**
     * Creates a {@code SpiffePrincipal} for the given SPIFFE ID, storing its
     * canonical (RFC 3986 normalized) form.
     *
     * @param spiffeId the SPIFFE ID URI; must start with {@code spiffe://}
     * @throws NullPointerException     if {@code spiffeId} is null
     * @throws IllegalArgumentException if {@code spiffeId} does not start with
     *                                  {@code spiffe://} or is not a valid URI
     */
    public SpiffePrincipal(String spiffeId) {
        this.spiffeId = canonicalize(spiffeId);
    }

    /**
     * {@link AtomicSerial} constructor.  Validates and canonicalizes the SPIFFE
     * ID atomically, before construction, so an invalid stream cannot yield an
     * instance (and hence a reference cannot be stolen).
     *
     * @param arg the AtomicSerial deserialization argument
     * @throws IOException            if there are I/O errors while reading, or
     *                                {@link InvalidObjectException} if the SPIFFE
     *                                ID is malformed
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public SpiffePrincipal(GetArg arg) throws IOException, ClassNotFoundException {
        this.spiffeId = checkCanonical(arg.get("spiffeId", null, String.class));
    }

    /**
     * Validates and returns the canonical form of a SPIFFE ID supplied through
     * the public API (throws unchecked on failure).
     */
    private static String canonicalize(String spiffeId) {
        if (spiffeId == null)
            throw new NullPointerException("spiffeId must not be null");
        if (!spiffeId.startsWith(SPIFFE_SCHEME)
                || spiffeId.length() <= SPIFFE_SCHEME.length())
            throw new IllegalArgumentException(
                    "SPIFFE ID must start with 'spiffe://' and have a "
                    + "non-empty trust domain: " + spiffeId);
        try {
            return new Uri(spiffeId).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(
                    "SPIFFE ID is not a valid URI: " + spiffeId, ex);
        }
    }

    /**
     * Validates and returns the canonical form of a SPIFFE ID read from a
     * serialized stream, throwing {@link InvalidObjectException} (atomic
     * failure) if it is malformed.
     */
    private static String checkCanonical(String spiffeId) throws InvalidObjectException {
        if (spiffeId == null
                || !spiffeId.startsWith(SPIFFE_SCHEME)
                || spiffeId.length() <= SPIFFE_SCHEME.length()) {
            throw new InvalidObjectException(
                    "Invalid SPIFFE ID on deserialization (must start with "
                    + "'spiffe://' and have a non-empty trust domain): "
                    + spiffeId);
        }
        try {
            return new Uri(spiffeId).toString();
        } catch (URISyntaxException ex) {
            InvalidObjectException e = new InvalidObjectException(
                    "Invalid SPIFFE ID URI on deserialization: " + spiffeId);
            e.initCause(ex);
            throw e;
        }
    }

    /**
     * Returns the canonical SPIFFE ID URI.  This is the match key for SPIFFE
     * constraint matching.
     *
     * @return the SPIFFE ID; never {@code null}
     */
    @Override
    public String getName() {
        return spiffeId;
    }

    /**
     * Returns {@code true} if {@code obj} is a {@code SpiffePrincipal} with
     * the same canonical SPIFFE ID URI.
     *
     * <p>Note: class-exact equality does not bridge different
     * {@code SpiffePrincipal} implementations; cross-implementation constraint
     * matching is by canonical {@link #getName()} value (see class javadoc).
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
     * Display only -- use {@link #getName()} for matching.
     */
    @Override
    public String toString() {
        return "SpiffePrincipal(" + spiffeId + ")";
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
                    if (uri.startsWith(SPIFFE_SCHEME)) {
                        result.add(new SpiffePrincipal(uri));
                    }
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}
