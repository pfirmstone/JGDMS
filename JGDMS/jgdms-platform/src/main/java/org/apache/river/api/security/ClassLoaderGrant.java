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
import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;

/**
 * A dynamic grant to every ProtectionDomain defined by a ClassLoader.
 * <p>
 * This is the grant for code whose identity has not been verified, it is no
 * narrower than the ClassLoader.  Where the identities of the code are known,
 * {@link DigestDomainGrant} is limited to those.
 * <p>
 * The ClassLoader is that of the granted class, not that of the class's 
 * ProtectionDomain: a {@code java.lang.reflect.Proxy} class has the shared
 * all permission domain, whose loader is null.
 * <p>
 * The grant is tied to the ClassLoader of a class and is garbage collected
 * with it: it is void, and implies nothing, once the ClassLoader is gone.
 * A grant that was given no class, or a class without a ClassLoader (a
 * bootstrap class), is in that same state from the start: it is void.  It
 * never applies to all domains.
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
@SuppressWarnings("serial")
class ClassLoaderGrant extends PrincipalGrant {
    private static final long serialVersionUID = 1L;
    // Null if no class was granted, the grant is void.
    private final transient WeakReference<ProtectionDomain> domain;
    private final transient WeakReference<ClassLoader> loader;
    private final int hashCode;

    @SuppressWarnings("unchecked")
    ClassLoaderGrant(WeakReference<ProtectionDomain> domain, 
            WeakReference<ClassLoader> loader, Principal[] groups, 
            Permission[] perm){
        super(groups, perm);
        this.domain = domain;
        this.loader = loader;
        int hash = 7;
        hash = 19 * hash + domainHash();
        ClassLoader l = loader != null ? loader.get() : null;
        hash = 19 * hash + (l != null ? System.identityHashCode(l) : 0);
        hashCode = hash;
    }
    
    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
	if (o.hashCode() != this.hashCode()) return false;
        if (o instanceof ClassLoaderGrant && super.equals(o)){
            ClassLoaderGrant c = (ClassLoaderGrant) o;
            // The references are compared, not their referents.  Two void
            // grants without a class are equal if their principals and
            // permissions are.
            if (domain == null ? c.domain != null : !domain.equals(c.domain)) return false;
            ClassLoader l = loader != null ? loader.get() : null;
            ClassLoader ol = c.loader != null ? c.loader.get() : null;
            return l == ol;
        }
        return false;
    }

    // What a ProtectionDomainGrant would contribute to the hash code.
    private int domainHash() {
        int hash = 7;
        hash = 13 * hash + (domain != null ? domain.hashCode() : 0);
        hash = 13 * hash + super.hashCode();
        return hash;
    }
   
    @Override
    public int hashCode() {
        return hashCode;
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder(500);
        sb.append(super.toString())
          .append("ProtectionDomain: \n");
        if (domain != null){
            ProtectionDomain pd = domain.get();
            if (pd != null){
                sb.append(pd.toString());
            } else {
                sb.append("Grant is void - ProtectionDomain is null");
            }
        } else {
            sb.append("Grant is void - no class was granted");
        }
        return sb.append("ClassLoader grant.").toString();
    }

    @Override
    public boolean implies(ProtectionDomain pd) {
        ClassLoader cl = null;
        Principal[] pals = null;
        if (pd != null){
            cl = pd.getClassLoader();
            pals = getPrincipals(pd);
        }
        return implies(cl, pals);
    }

    @Override
    public boolean implies(ClassLoader cl, Principal[] pal) {
        if (domain == null) return false; // void, no class was granted.
        if (cl == null) return false;
        ClassLoader l = loader != null ? loader.get() : null;
        if (l == null || l != cl) return false;
        return implies(pal);
    }

    /** A dynamic grant applies to domains, a CodeSource has no ClassLoader. */
    @Override
    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return false;
    }

    @Override
    public boolean isVoid() {
        if (super.isVoid()) return true;
        // No class, no ProtectionDomain or no ClassLoader: void.
        return domain == null || domain.get() == null 
                || loader == null || loader.get() == null;
    }

    @Override
    public boolean isDyanamic() {
        return true;
    }
    
    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
	if (!(grant instanceof ClassLoaderGrant)) return false;
	ClassLoader myCL = loader != null ? loader.get() : null;
        WeakReference<ClassLoader> other = ((ClassLoaderGrant)grant).loader;
	ClassLoader yourCL = other != null ? other.get() : null;
	return myCL != null && myCL == yourCL;
	// The superclass has a narrower scope than ClassLoaderGrant.
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        if (pgb instanceof PermissionGrantBuilderImp) {
            PermissionGrantBuilderImp pgbi = (PermissionGrantBuilderImp) pgb;
            pgbi.setDomain(domain);
            pgbi.setLoader(loader);
        }
        return pgb.context(PermissionGrantBuilder.CLASSLOADER);
    }
    
    //writeReplace method for serialization proxy pattern
    private Object writeReplace() {
        return getBuilderTemplate();
    }
    
    //readObject method for the serialization proxy pattern
    private void readObject(ObjectInputStream stream) 
            throws InvalidObjectException{
        throw new InvalidObjectException("PermissionGrantBuilder required");
    }
}
