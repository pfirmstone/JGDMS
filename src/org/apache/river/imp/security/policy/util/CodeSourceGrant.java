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

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author Peter Firmstone
 */
class CodeSourceGrant extends PrincipalGrant {
    private final CodeSource cs;
    private final Collection<Permission> permissions;
    
    CodeSourceGrant(CodeSource cs, Principal[] pals, Permission[] perm){
        super(pals);
        this.cs = normalizeCodeSource(cs);
        if (perm == null || perm.length == 0) {
            this.permissions = Collections.EMPTY_LIST;
        }else{
            this.permissions = new HashSet<Permission>(perm.length);
            this.permissions.addAll(Arrays.asList(perm));
        }        
    }
    
    /**
     * Checks if passed CodeSource matches this PolicyEntry. Null CodeSource of
     * PolicyEntry implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     */
    protected boolean impliesCodeSource(CodeSource codeSource) {
        if (cs == null) return true;
        if (codeSource == null) return false;       
        return cs.implies(normalizeCodeSource(codeSource));
    }
    
    // This is only here for revoke.
    protected boolean impliesClassLoader(ClassLoader cl) {
        if ( cs == null ) return true;      
        return false;  //Indeterminate.
    }
    
    protected boolean impliesCertificates(Certificate[] signers){
        Certificate[] certs = cs.getCertificates();
        if ( certs.length == 0 ) return true;
        if ( signers == null || signers.length == 0 ) return false;
        List<Certificate> certificates = Arrays.asList(signers);
        return certificates.containsAll(Arrays.asList(certs));
    }
    
    public boolean implies(ProtectionDomain pd) {
        CodeSource codeSource = null;
        Principal[] pals = null;
        if (pd != null){
            codeSource = pd.getCodeSource();
            pals = pd.getPrincipals();
        }
        return implies(codeSource, pals);
    }

    public Collection<Permission> getPermissions() {
        return Collections.unmodifiableCollection(permissions);
    }

    public boolean isVoid() {
        if (permissions.size() == 0 ) return true;
        return false;
    }

}
