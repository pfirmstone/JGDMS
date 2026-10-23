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
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A dynamic grant to the ProtectionDomains of verified code.
 * <p>
 * The identities supplied (location, signers and content digest of each jar)
 * are what a verifier established before any class was loaded.  They may be
 * digest CodeSources whose digest was supplied, not computed, they are only
 * the expected identities.  A ProtectionDomain {@code pd} is implied if and
 * only if:
 * <ol>
 * <li>{@code pd.getClassLoader()} is the ClassLoader of the granted class, 
 *     by reference (checked first, nothing else is examined otherwise);
 * <li>the grant's principals are satisfied;
 * <li>{@code pd}'s CodeSource is a {@code java.security.DigestCodeSource}
 *     whose digest was computed from the code, not asserted by a caller 
 *     ({@code unverified()} is false).  The verified CodeSource is the 
 *     verifier, it is compared with the expected identity, and
 * <li>its identity (location Uri, signers and digest) is one of those
 *     supplied.
 * </ol>
 * Domains are created lazily, upon the first class defined from each jar, so
 * they are matched as they are checked.  A jar added to the ClassLoader later,
 * a jar whose digest differs, a jar whose entries have other signers, and a
 * domain with a digest asserted by a caller are not implied.
 * <p>
 * This grant is no narrower than its ClassLoader: it is only as good as the
 * isolation between the ClassLoaders of different remote objects.
 * <p>
 * The ClassLoader is held weakly, the grant is void once it is collected.  It
 * is void if serialized, and on a JVM without
 * {@code java.security.DigestCodeSource} nothing can be built or implied.
 * 
 * @since 4.0.0
 */
@SuppressWarnings("serial")
final class DigestDomainGrant extends PrincipalGrant {
    private static final long serialVersionUID = 1L;
    // The ClassLoader of the granted class, not that of its domain: a
    // java.lang.reflect.Proxy class has the shared all permission domain, 
    // whose loader is null.
    private final transient WeakReference<ProtectionDomain> domain;
    private final transient WeakReference<ClassLoader> loader;
    private final transient Set<CodeSourceKey> verified;
    private final transient List<CodeSource> sources;
    private final int hashCode;

    @SuppressWarnings("unchecked")
    DigestDomainGrant(WeakReference<ProtectionDomain> domain,
            WeakReference<ClassLoader> loader, Set<CodeSourceKey> verified,
            List<CodeSource> sources, Principal[] groups, Permission[] perm) {
        super(groups, perm);
        if (domain == null) throw new NullPointerException("domain");
        if (loader == null) throw new NullPointerException("loader");
        if (verified == null || verified.isEmpty()) {
            throw new IllegalArgumentException("no verified identities");
        }
        this.domain = domain;
        this.loader = loader;
        this.verified = Collections.unmodifiableSet(verified);
        this.sources = Collections.unmodifiableList(new ArrayList<CodeSource>(sources));
        int hash = 5;
        hash = 29 * hash + this.verified.hashCode();
        ClassLoader l = loader.get();
        hash = 29 * hash + (l != null ? System.identityHashCode(l) : 0);
        hash = 29 * hash + domainHash();
        hashCode = hash;
    }

    // What a ProtectionDomainGrant would contribute to the hash code.
    private int domainHash() {
        int hash = 7;
        hash = 13 * hash + domain.hashCode();
        hash = 13 * hash + super.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (o.hashCode() != this.hashCode) return false;
        if (o.getClass() != this.getClass()) return false;
        DigestDomainGrant c = (DigestDomainGrant) o;
        ClassLoader l = loader.get();
        // The domain references are compared, not their referents.
        return l != null && l == c.loader.get() && super.equals(o) 
                && domain.equals(c.domain) && verified.equals(c.verified);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(500);
        sb.append(super.toString()).append("ProtectionDomain: \n");
        ProtectionDomain pd = domain.get();
        if (pd != null) {
            sb.append(pd.toString());
        } else {
            sb.append("Grant is void - ProtectionDomain is null");
        }
        sb.append("\nDigest domain grant:");
        for (CodeSourceKey key : verified) {
            sb.append("\n  ").append(key);
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
        // A digest computed from the code, not asserted by a caller.
        if (!CodeSourceKey.isVerifiedDigest(cs)) return false;
        CodeSourceKey key = CodeSourceKey.of(cs); // null if unparseable
        return key != null && verified.contains(key);
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
        return super.isVoid() || domain.get() == null || loader.get() == null;
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
        ProtectionDomain dom = domain.get();
        if (dom == null || !dom.equals(other.domain.get())) return false;
        return verified.equals(other.verified);
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        if (pgb instanceof PermissionGrantBuilderImp) {
            PermissionGrantBuilderImp pgbi = (PermissionGrantBuilderImp) pgb;
            pgbi.setDomain(domain);
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
