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
 * all permission domain, whose loader is null.  Without a granted class, the
 * grant applies to all domains.
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
class ClassLoaderGrant extends ProtectionDomainGrant {
    private static final long serialVersionUID = 1L;
    private final transient WeakReference<ClassLoader> loader;
    private final int hashCode;

    @SuppressWarnings("unchecked")
    ClassLoaderGrant(WeakReference<ProtectionDomain> domain, 
            WeakReference<ClassLoader> loader, Principal[] groups, 
            Permission[] perm){
        super(domain, groups, perm);
        this.loader = loader;
        int hash = 7;
        hash = 19 * hash + super.hashCode();
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
            ClassLoader l = loader != null ? loader.get() : null;
            ClassLoader ol = ((ClassLoaderGrant) o).loader != null 
                    ? ((ClassLoaderGrant) o).loader.get() : null;
            return l == ol;
        }
        return false;
    }
   
    @Override
    public int hashCode() {
        return hashCode;
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder(500);
        return sb.append(super.toString())
                 .append("ClassLoader grant.")
                 .toString();
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
        if (domain == null) return implies(pal); // all domains.
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
        return domain != null && (loader == null || loader.get() == null);
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
            ((PermissionGrantBuilderImp) pgb).setLoader(loader);
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
