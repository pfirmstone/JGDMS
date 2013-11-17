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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ProtectionDomainGrant's become void if serialized, since ProtectionDomain's
 * cannot be serialized.
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
class ProtectionDomainGrant extends PrincipalGrant {
    private static final long serialVersionUID = 1L;
    private final WeakReference<ProtectionDomain> domain;
    private final int hashCode;
    
    @SuppressWarnings("unchecked")
    ProtectionDomainGrant(WeakReference<ProtectionDomain> domain, Principal[] groups, 
            Permission[] perm){
        super(groups, perm);
        this.domain = domain;
        int hash = 7;
        hash = 13 * hash + (this.domain != null ? this.domain.hashCode() : 0);
        hash = 13 * hash + super.hashCode();
        hashCode = hash;
    }
    
    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
	if (o.hashCode() != this.hashCode()) return false;
        if (o instanceof ProtectionDomainGrant){
            ProtectionDomainGrant c = (ProtectionDomainGrant) o;
            if ( !super.equals(o)) return false;
            if ( domain !=null && domain.equals(c.domain)) return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder(400);
        sb.append(super.toString())
          .append("ProtectionDomain: \n");
        ProtectionDomain pd = null;
        if (domain != null){ pd = domain.get();
            if (pd != null){
                sb.append(pd.toString());
            } else {
                sb.append("Grant is void - ProtectionDomain is null");
            }
        } else {
            sb.append("Grant applies to all ProtectionDomain's");
        }
        return sb.toString();
    }
            
    
    @Override
    public boolean implies(ProtectionDomain pd){
//        if ((domain == null) && (pals.isEmpty())) return true;
//        if (pd == null) return false;
        return impliesProtectionDomain(pd) && implies(getPrincipals(pd));
	
    }
    
    @Override
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return impliesClassLoader(cl) && implies(pal);
    }

    @Override
    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return impliesCodeSource(codeSource) && implies(pal);
    }
    
    /*
     * Checks if passed ProtectionDomain matches this PermissionGrant. 
     * Non-null ProtectionDomain's are
     * compared with equals() and if false are compared by ClassLoader and
     * CodeSource, in case of new PermissionDomain's created by a DomainCombiner
     */   
    // for grant
    private boolean impliesProtectionDomain(ProtectionDomain pd) {
        // ProtectionDomain comparison
        if (domain == null) return true; // Dynamic grant compatibility, opposite of CodeSource and Certificate grant's
        if (pd == null) return false;       
        if (domain.get() == null ) return false; // grant is void.
        if ( pd.equals(domain.get())) return true; // pd not null fast reference comparison
        if ( impliesClassLoader(pd.getClassLoader()) 
                && impliesCodeSource(pd.getCodeSource()))
        {
            return true;
        }
        return false;
    }

    // This is here for revoke and for new ProtectionDomain's created by the
    // DomainCombiner such as those in the SubjectDomainCombiner.
    private boolean impliesClassLoader(ClassLoader cl) {
        if (domain == null ) return true;  // Dynamic grant compatibility
        if (cl == null) return false;       
        if (domain.get() == null ) return false; // is void.
        ClassLoader thisloader = domain.get().getClassLoader(); // pd not null
        if (thisloader != null){
            return thisloader.equals(cl);
        }
        return false; // System loader if null.
    }
    // This is here for revoke and for new ProtectionDomain's created by the
    // DomainCombiner such as those in the SubjectDomainCombiner.
    private boolean impliesCodeSource(CodeSource codeSource) {
        ProtectionDomain pd = domain != null ? domain.get(): null;
        if (pd == null) return false; // is void - why did I have true?
        // The CodeSource is not normalized, if the PD was created by a domain
        // combiner it is likely that the CodeSource will have the same referent.
        CodeSource cs = pd.getCodeSource(); // Don't normalise CodeSource.
        if (cs == codeSource) return true; // same reference.
        if (cs == null && codeSource == null) return true; // if both null, when pd exists.
        if (cs == null) return false; // Null cs indicates system domain, does not imply.
        // Most won't get to here, since domain combiners shouldn't duplicate CodeSource.
        // But just in case...
        // Don't use CodeSource.equals() because that causes DNS Lookup.
        Certificate[] myCerts = cs.getCertificates();
        Certificate[] hisCerts = codeSource.getCertificates();
        if ( myCerts != null && !Arrays.equals(myCerts, hisCerts)) return false;
        try {
            URI myLocation = cs.getLocation().toURI();
            URI hisLocation = codeSource.getLocation().toURI();
            if (myLocation.equals(hisLocation)) return true;
        } catch (URISyntaxException ex) {
            // We only compare URL if we can't compare URI
            URL myLocation = cs.getLocation();
            URL hisLocation = codeSource.getLocation();
            // Only use string representation to compare, DNS cache poisioning
            // presents a security risk, so URL.equals isn't used.
            if (myLocation.toExternalForm().equals(hisLocation.toExternalForm())) return true;
        }
        return false;
    }

    @Override
    public boolean isVoid() {        
        if ( super.isVoid()) return true;
        if ( domain != null && domain.get() == null) return true;
        return false;
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        if ( pgb instanceof PermissionGrantBuilderImp ){
         PermissionGrantBuilderImp pgbi = (PermissionGrantBuilderImp) pgb;
                pgbi.setDomain(domain)
                .context(PermissionGrantBuilder.PROTECTIONDOMAIN);
        }
        return pgb;
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
