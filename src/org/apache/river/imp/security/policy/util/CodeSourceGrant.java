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

package org.apache.river.imp.security.policy.util;

import org.apache.river.api.security.PermissionGrantBuilder;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import org.apache.river.imp.security.policy.util.DenyImpl;

/**
 *
 * @author Peter Firmstone
 */
class CodeSourceGrant extends PrincipalGrant {
    private final CodeSource cs;
    private final int hashCode;
    
    @SuppressWarnings("unchecked")
    CodeSourceGrant(CodeSource cs, Principal[] pals, Permission[] perm){
        super(pals, perm);
        this.cs = normalizeCodeSource(cs);
        int hash = 3;
        hash = 67 * hash + (this.cs != null ? this.cs.hashCode() : 0);
        hash = 67 * hash + (super.hashCode());
        hashCode = hash;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
    
    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
        if (o instanceof CodeSourceGrant){
            CodeSourceGrant c = (CodeSourceGrant) o;
            if ( !super.equals(o)) return false;
            if (cs.equals(c.cs)) return true;
        }
        return false;
    }
    
    /**
     * Checks if passed CodeSource matches this PermissionGrant. Null CodeSource of
     * PermissionGrant implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     */
    protected boolean impliesCodeSource(CodeSource codeSource) {
        if (cs == null) return true;
        if (codeSource == null) return false;       
        return cs.implies(normalizeCodeSource(codeSource));
    }
    
    // This is only here for user comparisons
    protected boolean impliesProtectionDomain(ProtectionDomain pd){
        CodeSource codeSource = null;
        if (pd != null){
            codeSource = pd.getCodeSource();
        }
        return impliesCodeSource(codeSource);
    }
    
    // This is only here for user comparisons
    protected boolean impliesClassLoader(ClassLoader cl) {
        if ( cs == null ) return true;      
        return false;  //Indeterminate.
    }
    
    // This is only here for user comparisons
    protected boolean impliesCertificates(Certificate[] signers){
        Certificate[] certs = cs.getCertificates();
        if ( certs.length == 0 ) return true;
        if ( signers == null || signers.length == 0 ) return false;
        List<Certificate> certificates = Arrays.asList(signers);
        return certificates.containsAll(Arrays.asList(certs));
    }
    
    // This is the real deal, the Policy utilises this.
    public boolean implies(ProtectionDomain pd) {
        CodeSource codeSource = null;
        Principal[] pals = null;
        if (pd != null){
            codeSource = pd.getCodeSource();
            pals = pd.getPrincipals();
        }
        return implies(codeSource, pals);
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        pgb.codeSource(cs)
           .context(PermissionGrantBuilder.CODESOURCE);
        return pgb;
    }
}
