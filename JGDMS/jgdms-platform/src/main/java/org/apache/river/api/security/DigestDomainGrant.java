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

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dynamic grant to the ProtectionDomains of the code that one ClassLoader
 * loaded, identified by the {@code java.security.DigestCodeSource}s it loaded.
 * <p>
 * The grant lasts only as long as the ClassLoader it is granted to, which is
 * held weakly: it is void once the ClassLoader is collected.  A
 * ProtectionDomain {@code pd} is implied if and only if:
 * <ol>
 * <li>{@code pd.getClassLoader()} is the ClassLoader of the granted class, 
 *     by reference (checked first, nothing else is examined otherwise);
 * <li>the grant's principals are satisfied; and
 * <li>{@code pd}'s CodeSource is a {@code DigestCodeSource} that the
 *     ClassLoader loaded, that is, one of those supplied to the grant, as
 *     {@code DigestCodeSource.equals} says, computed from the code
 *     ({@code unverified()} is false); or it is not, and the grant does not
 *     apply.
 * </ol>
 * The grant does not define its own identity of code: no signer order or
 * signer set, no location, and no digest is compared here, what a
 * {@code DigestCodeSource} is, is up to {@code DigestCodeSource}.  A jar
 * added to the ClassLoader later, and a CodeSource that is not a
 * {@code DigestCodeSource}, are not implied.
 * <p>
 * This grant is no narrower than its ClassLoader: it is only as good as the
 * isolation between the ClassLoaders of different remote objects.
 * <p>
 * {@code DigestCodeSource} exists only in DirtyChai, and is not visible to the
 * compiler of this module.  It is recognised by the name of its class, which
 * is final and in {@code java.*}, so it can only be the one of the boot class
 * loader, and its public accessors are called by reflection.  On a JVM
 * without it nothing can be built or implied.  The grant is void if
 * serialized.
 * 
 * @since 4.0.0
 */
@SuppressWarnings("serial")
final class DigestDomainGrant extends PrincipalGrant {
    private static final long serialVersionUID = 1L;
    /** The final class of the boot class loader, DirtyChai only. */
    private static final String DIGEST_CODE_SOURCE = "java.security.DigestCodeSource";
    /** The only algorithm accepted. */
    private static final String ALGORITHM = "SHA-256";
    // The ClassLoader of the granted class, not that of its domain: a
    // java.lang.reflect.Proxy class has the shared all permission domain, 
    // whose loader is null.
    private final transient WeakReference<ClassLoader> loader;
    // The DigestCodeSources the ClassLoader loaded.
    private final transient List<CodeSource> sources;
    private final int hashCode;

    @SuppressWarnings("unchecked")
    DigestDomainGrant(WeakReference<ClassLoader> loader, 
            List<CodeSource> sources, Principal[] groups, Permission[] perm) {
        super(groups, perm);
        if (loader == null) throw new NullPointerException("loader");
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("no digest code sources");
        }
        List<CodeSource> copy = new ArrayList<CodeSource>(sources);
        for (CodeSource cs : copy) checkSource(cs);
        this.loader = loader;
        this.sources = Collections.unmodifiableList(copy);
        int hash = 5;
        hash = 29 * hash + this.sources.hashCode();
        ClassLoader l = loader.get();
        hash = 29 * hash + (l != null ? System.identityHashCode(l) : 0);
        hash = 29 * hash + super.hashCode();
        hashCode = hash;
    }

    /**
     * Refuses what is not a DigestCodeSource with a SHA-256 digest, so a 
     * grant can never be wider than the identities given.
     */
    private static void checkSource(CodeSource cs) {
        if (!isDigestCodeSource(cs)) throw new IllegalArgumentException(
                "not a DigestCodeSource: " + cs);
        try {
            if (!ALGORITHM.equals(call(cs, "getDigestAlgorithm"))) {
                throw new IllegalArgumentException(
                    "digest algorithm must be " + ALGORITHM + ": " + cs);
            }
            if (call(cs, "getDigest") == null) {
                throw new IllegalArgumentException("code source has no digest: " + cs);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot read the digest of: " + cs, e);
        }
    }

    /**
     * @return true if cs is a {@code java.security.DigestCodeSource} of the 
     * boot class loader.
     */
    static boolean isDigestCodeSource(CodeSource cs) {
        if (cs == null) return false;
        Class<?> c = cs.getClass();
        return c.getClassLoader() == null && DIGEST_CODE_SOURCE.equals(c.getName());
    }

    // A public method of DigestCodeSource without arguments.
    private static Object call(CodeSource cs, String method) 
            throws ReflectiveOperationException {
        Method m = cs.getClass().getMethod(method);
        return m.invoke(cs);
    }

    /**
     * Whether the digest was computed from the code, not asserted by a
     * caller.  Fails closed if that cannot be determined.
     */
    private static boolean isComputed(CodeSource cs) {
        try {
            return Boolean.FALSE.equals(call(cs, "unverified"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (o.hashCode() != this.hashCode) return false;
        if (o.getClass() != this.getClass()) return false;
        DigestDomainGrant c = (DigestDomainGrant) o;
        ClassLoader l = loader.get();
        return l != null && l == c.loader.get() && super.equals(o) 
                && sources.equals(c.sources);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(500);
        sb.append(super.toString());
        ClassLoader l = loader.get();
        if (l == null) {
            sb.append("Grant is void - ClassLoader is null");
        } else {
            sb.append("ClassLoader: ").append(l);
        }
        sb.append("\nDigest domain grant:");
        for (CodeSource cs : sources) {
            sb.append("\n  ").append(cs);
        }
        return sb.toString();
    }

    @Override
    public boolean implies(ProtectionDomain pd) {
        ClassLoader l = loader.get();
        if (l == null || pd == null || pd.getClassLoader() != l) return false;
        if (!implies(getPrincipals(pd))) return false;
        CodeSource cs = pd.getCodeSource();
        // No result is cached by CodeSource reference: DigestCodeSource is
        // mutable through its public readExternal.
        if (!isDigestCodeSource(cs)) return false;
        // A digest computed from the code, not asserted by a caller.
        if (!isComputed(cs)) return false;
        for (CodeSource loaded : sources) {
            if (loaded.equals(cs)) return true;
        }
        return false;
    }

    /** A ClassLoader carries no code identity. */
    @Override
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return false;
    }

    /** A CodeSource alone has no ClassLoader. */
    @Override
    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return false;
    }

    @Override
    public boolean isVoid() {
        return super.isVoid() || loader.get() == null;
    }

    @Override
    public boolean isDyanamic() {
        return true;
    }

    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
        if (!(grant instanceof DigestDomainGrant)) return false;
        DigestDomainGrant other = (DigestDomainGrant) grant;
        ClassLoader l = loader.get();
        if (l == null || l != other.loader.get()) return false;
        if (!super.impliesEquivalent(grant)) return false;
        return sources.equals(other.sources);
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        if (pgb instanceof PermissionGrantBuilderImp) {
            PermissionGrantBuilderImp pgbi = (PermissionGrantBuilderImp) pgb;
            pgbi.setLoader(loader).setCodeSources(sources);
        }
        return pgb.context(PermissionGrantBuilder.DIGEST_DOMAIN);
    }

    //writeReplace method for serialization proxy pattern
    private Object writeReplace() {
        return getBuilderTemplate();
    }

    //readObject method for the serialization proxy pattern
    private void readObject(ObjectInputStream stream)
            throws InvalidObjectException {
        throw new InvalidObjectException("PermissionGrantBuilder required");
    }
}
