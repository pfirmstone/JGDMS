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
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.apache.river.api.net.Uri;

/**
 * A dynamic grant to each {@link ProtectionDomain} of a verified codebase.
 * <p>
 * A ProtectionDomain {@code pd} is implied if and only if:
 * <ol>
 * <li>{@code pd.getClassLoader()} is the ClassLoader of the grant's anchor
 *     ProtectionDomain (the domain of the class passed to
 *     {@link PermissionGrantBuilder#clazz(Class)});
 * <li>{@code pd}'s CodeSource location is one of the codebase locations the
 *     ClassLoader was created for, or {@code pd} is the anchor domain, or
 *     a domain with the identity of the anchor domain, such as one created by 
 *     a DomainCombiner (this also covers an anchor without a location);
 * <li>the grant's principals are satisfied.
 * </ol>
 * Code source identity (signers, digest) is not required to match for a
 * location in the codebase: a ClassLoader builds one ProtectionDomain per
 * CodeSource, and one jar may yield several (each entry's own certificates,
 * and on DirtyChai a plain CodeSource is promoted to a DigestCodeSource after
 * a permission probe), so pinning the first identity seen would depend on
 * class loading order and could be poisoned by any code holding the loader.
 * The loader is the record of what was verified.
 * <p>
 * ProtectionDomain's are created lazily, upon the first class defined from 
 * each jar, so domains are matched as they are checked, they are not listed
 * when the grant is made.
 * <p>
 * If the codebase is empty, because the ClassLoader is not a URLClassLoader, 
 * only the anchor domain is implied, in the same way as a 
 * {@link ProtectionDomainGrant}.
 * <p>
 * Like all ProtectionDomain grants, this grant becomes void if serialized.
 * 
 * @since 4.0.0
 */
class CodebaseDomainGrant extends ProtectionDomainGrant {
    private static final long serialVersionUID = 1L;
    // The ClassLoader of the granted class, null if it was loaded by the
    // bootstrap loader.  Not that of the anchor domain, a Proxy class has the
    // shared all permission domain, whose loader is null.
    private final transient WeakReference<ClassLoader> loader;
    private final transient Set<Uri> codebase;
    // Positive result for the last CodeSource checked, by reference.
    private transient volatile WeakReference<CodeSource> lastImplied;
    private final int hashCode;

    @SuppressWarnings("unchecked")
    CodebaseDomainGrant(WeakReference<ProtectionDomain> domain, 
            WeakReference<ClassLoader> loader, Set<Uri> codebase, 
            Principal[] groups, Permission[] perm) {
        super(domain, groups, perm);
        if (domain == null) throw new NullPointerException("domain");
        this.loader = loader;
        this.codebase = Collections.unmodifiableSet(codebase);
        int hash = 5;
        hash = 23 * hash + this.codebase.hashCode();
        hash = 23 * hash + super.hashCode();
        hashCode = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (o.hashCode() != this.hashCode) return false;
        if (o.getClass() != this.getClass()) return false;
        CodebaseDomainGrant c = (CodebaseDomainGrant) o;
        return super.equals(o) && codebase.equals(c.codebase);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(500);
        sb.append(super.toString()).append("\nCodebase grant: ");
        if (codebase.isEmpty()) {
            sb.append("anchor ProtectionDomain only");
        } else {
            for (Uri uri : codebase) {
                sb.append("\n  codebase \"").append(uri).append("\"");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean implies(ProtectionDomain pd) {
        if (pd == null) return false;
        return impliesDomain(pd) && implies(getPrincipals(pd));
    }

    /**
     * A ClassLoader carries no CodeSource identity, so this grant is 
     * indeterminate for a ClassLoader alone.
     */
    @Override
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return false;
    }

    private boolean impliesDomain(ProtectionDomain pd) {
        ProtectionDomain anchor = domain.get();
        if (anchor == null) return false; // void.
        if (pd == anchor) return true;
        ClassLoader cl = loader != null ? loader.get() : null;
        if (cl == null || pd.getClassLoader() != cl) return false;
        CodeSource cs = pd.getCodeSource();
        WeakReference<CodeSource> last = lastImplied;
        if (last != null && last.get() == cs && cs != null) return true;
        // The anchor's identity, including a domain created by a 
        // DomainCombiner, which shares the anchor's CodeSource.  This also
        // covers an anchor CodeSource that has no location.
        if (impliesCodeSource(cs)) return true;
        CodeSourceKey key = CodeSourceKey.of(cs);
        if (key == null) return false; // identity unknown, fail closed.
        if (!codebase.contains(key.uri)) return false;
        lastImplied = new WeakReference<CodeSource>(cs);
        return true;
    }

    /**
     * A dynamic grant applies to ProtectionDomains, a CodeSource alone has
     * no ClassLoader.
     */
    @Override
    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return false;
    }

    @Override
    public boolean isVoid() {
        if (super.isVoid()) return true;
        return loader != null && loader.get() == null;
    }

    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
        if (!(grant instanceof CodebaseDomainGrant)) return false;
        CodebaseDomainGrant other = (CodebaseDomainGrant) grant;
        if (!super.impliesEquivalent(grant)) return false;
        return codebase.equals(other.codebase);
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        if (pgb instanceof PermissionGrantBuilderImp) {
            ((PermissionGrantBuilderImp) pgb).setLoader(loader);
        }
        return pgb.context(PermissionGrantBuilder.CODEBASE_DOMAIN);
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
