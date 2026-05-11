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

package org.apache.river.api.security;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;

/**
 * A {@link PermissionGrant} that matches only {@link ProtectionDomain}s whose
 * {@link CodeSource} is a {@code java.security.DigestCodeSource} with an
 * identical content digest.  Any domain backed by a plain {@link CodeSource} —
 * even one with the same URL — is not implied, enforcing fail-secure behaviour.
 *
 * <p>Serialization uses the same proxy pattern as all other grant
 * implementations: {@link #writeReplace()} delegates to
 * {@link #getBuilderTemplate()}, and {@link #readObject} throws
 * {@link InvalidObjectException}.
 *
 * <p>{@code java.security.DigestCodeSource} is accessed by reflection so that
 * this class compiles and runs on a standard JDK 21+ JVM.  On a JVM without
 * {@code DigestCodeSource} (i.e. any JDK that is not DirtyChai or equivalent),
 * {@link #implies} always returns {@code false} for this grant type, which is
 * the fail-secure default.
 *
 * @author Peter Firmstone
 * @since 3.0.0
 */
@SuppressWarnings("serial")
class DigestGrant extends URIGrant {

    private static final long serialVersionUID = 1L;

    /** {@code java.security.DigestCodeSource} class, or {@code null} if absent. */
    private static final Class<?> DIGEST_CODE_SOURCE_CLASS;
    private static final Method GET_DIGEST_ALGORITHM;
    private static final Method GET_DIGEST_BYTES;

    static {
        Class<?> clazz = null;
        Method getAlg = null;
        Method getDig = null;
        try {
            clazz = Class.forName("java.security.DigestCodeSource");
            getAlg = clazz.getMethod("getDigestAlgorithm");
            getDig = clazz.getMethod("getDigest");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Not available on this JVM (standard JDK without DirtyChai)
        }
        DIGEST_CODE_SOURCE_CLASS = clazz;
        GET_DIGEST_ALGORITHM = getAlg;
        GET_DIGEST_BYTES = getDig;
    }

    private final String digestAlgorithm;
    private final byte[] digest;         // immutable defensive copy
    private final int hashCode;

    DigestGrant(String[] uri, String digestAlgorithm, byte[] digest,
                Certificate[] certs, String[] aliases,
                Principal[] pals, Permission[] perms) {
        super(uri, certs, aliases, pals, perms);
        this.digestAlgorithm = digestAlgorithm;
        this.digest = digest != null ? digest.clone() : null;
        int h = super.hashCode();
        h = 31 * h + (digestAlgorithm != null ? digestAlgorithm.hashCode() : 0);
        h = 31 * h + Arrays.hashCode(this.digest);
        this.hashCode = h;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DigestGrant)) return false;
        if (o.hashCode() != hashCode) return false;
        DigestGrant other = (DigestGrant) o;
        if (!super.equals(o)) return false;
        if (!stringsEqual(digestAlgorithm, other.digestAlgorithm)) return false;
        return Arrays.equals(digest, other.digest);
    }

    /**
     * Returns {@code true} only when {@code pd}'s {@link CodeSource} is a
     * {@code DigestCodeSource} with matching algorithm and digest bytes, and
     * when all inherited principal and certificate checks also pass.
     */
    @Override
    public boolean implies(ProtectionDomain pd) {
        if (pd == null) return false;
        CodeSource cs = pd.getCodeSource();
        Principal[] pals = getPrincipals(pd);
        return implies(cs, pals);
    }

    /**
     * Returns {@code false} for any plain {@link ClassLoader} argument;
     * the digest of loaded code is indeterminate without a {@link CodeSource}.
     */
    @Override
    public boolean implies(ClassLoader cl, Principal[] p) {
        return false;   // indeterminate — same as CertificateGrant
    }

    /**
     * Core implication logic.
     *
     * <ol>
     * <li>Delegates to super for principal and URI checks.
     * <li>{@code codeSource} must be a {@code DigestCodeSource} — plain
     *     {@link CodeSource} instances are never implied, even with the same URL.
     * <li>Algorithm names must match (case-sensitive).
     * <li>Digest bytes must be equal ({@link Arrays#equals}).
     * </ol>
     */
    @Override
    public boolean implies(CodeSource codeSource, Principal[] p) {
        if (!super.implies(codeSource, p)) return false;
        if (DIGEST_CODE_SOURCE_CLASS == null) return false;
        if (!DIGEST_CODE_SOURCE_CLASS.isInstance(codeSource)) return false;
        try {
            String alg = (String) GET_DIGEST_ALGORITHM.invoke(codeSource);
            byte[] dig = (byte[]) GET_DIGEST_BYTES.invoke(codeSource);
            if (!stringsEqual(digestAlgorithm, alg)) return false;
            return Arrays.equals(digest, dig);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
        if (!(grant instanceof DigestGrant)) return false;
        DigestGrant other = (DigestGrant) grant;
        if (!super.impliesEquivalent(grant)) return false;
        if (!stringsEqual(digestAlgorithm, other.digestAlgorithm)) return false;
        return Arrays.equals(digest, other.digest);
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        return pgb.digest(digestAlgorithm, digest)
                  .context(PermissionGrantBuilder.DIGEST);
    }

    private Object writeReplace() {
        return getBuilderTemplate();
    }

    private void readObject(ObjectInputStream stream)
            throws InvalidObjectException {
        throw new InvalidObjectException("PermissionGrantBuilder required");
    }

    private static boolean stringsEqual(String a, String b) {
        return a == b || (a != null && a.equals(b));
    }
}
