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
package org.apache.river.api.codebase;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An immutable, serializable verdict produced by a {@link BytecodeAnalysisEngine}
 * for a set of codebase URLs.
 *
 * <p>A {@code SignedVerdict} carries:
 * <ul>
 *   <li>the ordered set of codebase URLs that were analysed,</li>
 *   <li>the {@link VerdictType} assigned by the engine,</li>
 *   <li>the UTC timestamp (milliseconds since the epoch) at which the
 *       analysis completed,</li>
 *   <li>the DER-encoded digital signature over the canonical serialized form
 *       of the above fields, produced with the engine's private key.</li>
 * </ul>
 *
 * <p><strong>Trust model.</strong> Clients do <em>not</em> trust
 * {@code SignedVerdict} objects directly.  Only a {@link VerdictRegistry} that
 * has verified the engine's signature against a registered public key, and
 * aggregated verdicts according to its quorum policy, issues the
 * authoritative {@link RegistryVerdict} that clients rely on.
 *
 * <p><strong>Serialization safety.</strong> All fields are validated during
 * construction; the class is {@code final} so that subclasses cannot weaken
 * the invariants.
 *
 * @see BytecodeAnalysisEngine
 * @see VerdictRegistry
 * @see RegistryVerdict
 * @since 3.1.1
 */
public final class SignedVerdict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The ordered set of codebase URLs that were analysed.  The order is
     * significant: the canonical byte representation that is signed includes
     * the URLs in iteration order.
     */
    private final URL[] codebaseUrls;

    /** The verdict assigned by the analysis engine. */
    private final VerdictType verdict;

    /**
     * UTC time (milliseconds since 1970-01-01T00:00:00Z) at which the
     * analysis was completed and this verdict was produced.
     */
    private final long timestamp;

    /**
     * DER-encoded signature produced by the analysis engine over the
     * canonical serialized form of {@link #codebaseUrls}, {@link #verdict},
     * and {@link #timestamp}.  The algorithm is chosen by the engine and must
     * be communicated to the {@link VerdictRegistry} during BAE registration.
     */
    private final byte[] signature;

    /**
     * Constructs a new {@code SignedVerdict}.
     *
     * @param codebaseUrls the ordered set of codebase URLs that were analysed;
     *        must be non-null and non-empty
     * @param verdict      the verdict assigned by the engine; must be non-null
     * @param timestamp    UTC milliseconds since the epoch; must be positive
     * @param signature    DER-encoded signature bytes; must be non-null and
     *        non-empty
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     */
    public SignedVerdict(URL[] codebaseUrls,
                         VerdictType verdict,
                         long timestamp,
                         byte[] signature) {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.length == 0) throw new IllegalArgumentException("codebaseUrls must not be empty");
        for (int i = 0; i < codebaseUrls.length; i++) {
            if (codebaseUrls[i] == null)
                throw new NullPointerException("codebaseUrls[" + i + "]");
        }
        if (verdict == null) throw new NullPointerException("verdict");
        if (timestamp <= 0) throw new IllegalArgumentException("timestamp must be positive");
        if (signature == null) throw new NullPointerException("signature");
        if (signature.length == 0) throw new IllegalArgumentException("signature must not be empty");

        this.codebaseUrls = codebaseUrls.clone();
        this.verdict = verdict;
        this.timestamp = timestamp;
        this.signature = signature.clone();
    }

    /**
     * Returns an unmodifiable view of the codebase URLs that were analysed.
     *
     * @return an ordered, unmodifiable set of codebase URLs
     */
    public Set<URL> getCodebaseUrls() {
        Set<URL> result = new LinkedHashSet<URL>(codebaseUrls.length * 2);
        for (URL url : codebaseUrls) {
            result.add(url);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the verdict assigned by the analysis engine.
     *
     * @return the verdict; never {@code null}
     */
    public VerdictType getVerdict() {
        return verdict;
    }

    /**
     * Returns the UTC timestamp (milliseconds since the epoch) at which the
     * analysis was completed.
     *
     * @return a positive long
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a copy of the DER-encoded signature bytes.
     *
     * @return a non-empty byte array; never {@code null}
     */
    public byte[] getSignature() {
        return signature.clone();
    }

    // -------------------------------------------------------------------------
    // Serialization support
    // -------------------------------------------------------------------------

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (codebaseUrls == null || codebaseUrls.length == 0)
            throw new InvalidObjectException("codebaseUrls must not be null or empty");
        for (int i = 0; i < codebaseUrls.length; i++) {
            if (codebaseUrls[i] == null)
                throw new InvalidObjectException("codebaseUrls[" + i + "] must not be null");
        }
        if (verdict == null)
            throw new InvalidObjectException("verdict must not be null");
        if (timestamp <= 0)
            throw new InvalidObjectException("timestamp must be positive");
        if (signature == null || signature.length == 0)
            throw new InvalidObjectException("signature must not be null or empty");
    }

    @Override
    public String toString() {
        return "SignedVerdict{verdict=" + verdict
                + ", timestamp=" + timestamp
                + ", urls=" + Arrays.toString(codebaseUrls) + '}';
    }
}
