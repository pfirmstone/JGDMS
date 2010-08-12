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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.apache.river.api.security.Denied;
import org.apache.river.imp.security.policy.util.DenyImpl;

/**
 *
 * @author Peter Firmstone.
 */
class CertificateGrant extends CodeSourceGrant {
    private final Collection<Certificate> certs;
    private final int hashCode;
    private final Denied denied;
    @SuppressWarnings("unchecked")
    CertificateGrant(Certificate[] codeSourceCerts, Principal[] pals, Permission[] perms, Denied deny){
        super(null, pals, perms);
        denied = deny;
         if (codeSourceCerts == null || codeSourceCerts.length == 0) {
            certs = Collections.EMPTY_SET;
        }else{
            certs = new HashSet<Certificate>(codeSourceCerts.length);
            certs.addAll(Arrays.asList(codeSourceCerts));
        }
        int hash = 3;
        hash = 83 * hash + (this.certs != null ? this.certs.hashCode() : 0);
        hash = 83 * hash + (super.hashCode());
        hashCode = hash;
    }
    
        @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
	if (o.hashCode() != this.hashCode()) return false;
        if (o instanceof CertificateGrant){
            CertificateGrant c = (CertificateGrant) o;
            if ( !super.equals(o)) return false;
            if (certs.equals(c.certs)) return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
    
    @Override
    public boolean implies(ProtectionDomain pd) {
        if ( denied.allow(pd)){
            Certificate[] c = null;
            Principal[] pals = null;
            if (pd != null){
                c = pd.getCodeSource().getCertificates();
                pals = pd.getPrincipals();
            }
            return implies(c, pals);
        }
        return false;
    }
    
    @Override
    protected boolean impliesCertificates(Certificate[] signers){
        if ( certs.isEmpty() ) return true;
        if ( signers == null || signers.length == 0 ) return false;
        List<Certificate> certificates = Arrays.asList(signers);
        return certificates.containsAll(certs);
    }
    
    @Override
    protected boolean impliesCodeSource(CodeSource codeSource) {
        if ( certs.isEmpty()) return true;
        if (codeSource == null) return false;
        List<Certificate> certificates = Arrays.asList(codeSource.getCertificates());
        return certificates.containsAll(certs);
    }
    
    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        return pgb.certificates(certs.toArray(new Certificate[certs.size()]))
                .context(PermissionGrantBuilder.CODESOURCE_CERTS);
    }
}
