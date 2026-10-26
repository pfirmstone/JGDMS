/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.river.api.net.Uri;

/**
 * The identity of a {@link CodeSource}: location {@link Uri}, signers and, 
 * when the CodeSource is a {@code java.security.DigestCodeSource}, the
 * content digest.
 * <p>
 * {@code CodeSource.equals} is deliberately not used, it can cause DNS
 * lookups, and it ignores the digest.
 * <p>
 * {@code java.security.DigestCodeSource} is accessed by reflection, it is
 * absent on a stock JDK, where a CodeSource never carries a digest.
 * 
 * @since 4.0.0
 */
final class CodeSourceKey {

    private static final Method GET_DIGEST_ALGORITHM;
    private static final Method GET_DIGEST_BYTES;
    private static final Class<?> DIGEST_CODE_SOURCE_CLASS;
    // DigestCodeSource.unverified(): true for a digest asserted by a caller
    // (public constructor, readExternal), false for one computed from the jar.
    private static final Method UNVERIFIED;

    static {
        Class<?> clazz = null;
        Method getAlg = null;
        Method getDig = null;
        try {
            clazz = Class.forName("java.security.DigestCodeSource");
            getAlg = clazz.getMethod("getDigestAlgorithm");
            getDig = clazz.getMethod("getDigest");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            clazz = null;
            getAlg = null;
            getDig = null;
        }
        Method unverified = null;
        if (clazz != null) {
            try {
                unverified = clazz.getMethod("unverified");
            } catch (NoSuchMethodException e) {
                unverified = null; // cannot tell, isVerifiedDigest fails closed.
            }
        }
        UNVERIFIED = unverified;
        DIGEST_CODE_SOURCE_CLASS = clazz;
        GET_DIGEST_ALGORITHM = getAlg;
        GET_DIGEST_BYTES = getDig;
    }

    /**
     * Creates the identity of a CodeSource.
     * 
     * @param cs the CodeSource.
     * @return the identity, or null if cs is null, has no location, or its
     * location cannot be converted to a Uri.  Callers must treat null as
     * not implied (fail closed).
     */
    static CodeSourceKey of(CodeSource cs) {
        if (cs == null) return null;
        URL url = cs.getLocation();
        if (url == null) return null;
        Uri uri;
        try {
            uri = Uri.urlToUri(url);
        } catch (URISyntaxException ex) {
            return null;
        }
        if (uri == null) return null;
        Certificate[] certs = cs.getCertificates();
        // A set: SecureClassLoader keys its domains without regard to order.
        Set<Certificate> signers = certs == null || certs.length == 0 
                ? Collections.<Certificate>emptySet()
                : Collections.unmodifiableSet(new HashSet<Certificate>(Arrays.asList(certs)));
        String alg = null;
        byte[] dig = null;
        if (DIGEST_CODE_SOURCE_CLASS != null 
                && DIGEST_CODE_SOURCE_CLASS.isInstance(cs)) {
            try {
                alg = (String) GET_DIGEST_ALGORITHM.invoke(cs);
                byte[] d = (byte[]) GET_DIGEST_BYTES.invoke(cs);
                dig = d == null ? null : d.clone();
            } catch (Exception e) {
                return null; // Identity cannot be established.
            }
        }
        return new CodeSourceKey(uri, signers, alg, dig);
    }

    /**
     * Whether cs is a {@code java.security.DigestCodeSource} whose digest was
     * computed from the jar, rather than asserted by a caller.  A digest
     * asserted by a caller is not identity: code with createClassLoader can
     * define its own bytes in a domain presenting a trusted jar's digest.
     * Fails closed: false on a stock JDK, and if verification cannot be
     * determined.
     */
    static boolean isVerifiedDigest(CodeSource cs) {
        if (cs == null || DIGEST_CODE_SOURCE_CLASS == null 
                || UNVERIFIED == null) return false;
        if (!DIGEST_CODE_SOURCE_CLASS.isInstance(cs)) return false;
        try {
            if (((Boolean) UNVERIFIED.invoke(cs)).booleanValue()) return false;
            return GET_DIGEST_BYTES.invoke(cs) != null;
        } catch (Exception e) {
            return false;
        }
    }

    final Uri uri;
    private final Set<Certificate> signers;
    private final String digestAlgorithm;
    private final byte[] digest;
    private final int hash;

    private CodeSourceKey(Uri uri, Set<Certificate> signers, 
            String digestAlgorithm, byte[] digest) {
        this.uri = uri;
        this.signers = signers;
        this.digestAlgorithm = digestAlgorithm;
        this.digest = digest;
        int h = uri.hashCode();
        h = 31 * h + signers.hashCode();
        h = 31 * h + (digestAlgorithm != null ? digestAlgorithm.hashCode() : 0);
        h = 31 * h + Arrays.hashCode(digest);
        this.hash = h;
    }

    boolean hasDigest() {
        return digest != null;
    }

    String digestAlgorithm() {
        return digestAlgorithm;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof CodeSourceKey)) return false;
        CodeSourceKey k = (CodeSourceKey) o;
        if (hash != k.hash) return false;
        if (!uri.equals(k.uri)) return false;
        if (!signers.equals(k.signers)) return false;
        if (digestAlgorithm == null ? k.digestAlgorithm != null 
                : !digestAlgorithm.equals(k.digestAlgorithm)) return false;
        return Arrays.equals(digest, k.digest);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "CodeSourceKey[" + uri + ", signers=" + signers.size()
                + (digest != null ? ", digest=" + digestAlgorithm : "") + "]";
    }
}
