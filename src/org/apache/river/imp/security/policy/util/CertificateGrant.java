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
 * @author Peter Firmstone.
 */
class CertificateGrant extends CodeSourceGrant {
    private final Collection<Certificate> certs;
    CertificateGrant(Certificate[] codeSourceCerts, Principal[] pals, Permission[] perms){
        super(null, pals, perms);
         if (codeSourceCerts == null || codeSourceCerts.length == 0) {
            certs = Collections.emptySet(); // Java 1.5
        }else{
            certs = new HashSet<Certificate>(codeSourceCerts.length);
            certs.addAll(Arrays.asList(codeSourceCerts));
        }
    }
    
    @Override
    public boolean implies(ProtectionDomain pd) {
        Certificate[] c = null;
        Principal[] pals = null;
        if (pd != null){
            c = pd.getCodeSource().getCertificates();
            pals = pd.getPrincipals();
        }
        return implies(c, pals);
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
}
