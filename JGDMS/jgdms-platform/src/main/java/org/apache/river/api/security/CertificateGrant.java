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
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Peter Firmstone.
 * @since 3.0.0
 */
class CertificateGrant extends PrincipalGrant {
    private static final long serialVersionUID = 1L;
    private final Collection<Certificate> certs;
    private final List<String> alias;
    private final int hashCode;
    @SuppressWarnings("unchecked")
    CertificateGrant(Certificate[] codeSourceCerts, String[] aliases, Principal[] pals, Permission[] perms){
        super(pals, perms);
        if (codeSourceCerts == null || codeSourceCerts.length == 0) {
            certs = Collections.EMPTY_LIST;
        }else{
            certs = new ArrayList<Certificate>(codeSourceCerts.length);
            certs.addAll(Arrays.asList(codeSourceCerts));
        }
	if (aliases == null || aliases.length == 0) {
            alias = Collections.EMPTY_LIST;
        }else{
            alias = new ArrayList<String>(aliases.length);
            alias.addAll(Arrays.asList(aliases));
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
    public String toString(){
	if (!alias.isEmpty()){
	    StringBuilder sb = new StringBuilder(400);
	    sb.append("signedBy \"");
	    for (int i=0, l=alias.size(); i<l; i++){
	       if (i != 0) sb.append(",");
	       sb.append(alias.get(i));
	    }
	    sb.append("\",\n");
	    sb.append(super.toString());
	    return sb.toString();
	}
        return super.toString();
    }
    
    @Override
    public boolean implies(ProtectionDomain pd) {
        // File policy grant compatibility, different behaviour to dynamic grants.
        if (pd == null) return false; 
	CodeSource c = null;
	Principal[] pals = null;
        c = pd.getCodeSource();
        pals = getPrincipals(pd);
	return implies(c, pals);
    }
    
    // Subclasses shouldn't call this if 
    @Override
    public boolean implies(ClassLoader cl, Principal[] p){
	if ( !implies(p)) return false;
	if ( certs.isEmpty() ) return true;
	if ( cl == null ) return false;
	return false;  //Indeterminate.
    }
    
    @Override
    public boolean implies(CodeSource codeSource, Principal[] p) {
        if ( !implies(p)) return false;
        if ( codeSource == null ) return false;
	if ( certs.isEmpty() ) return true;
	Certificate[] signers = codeSource.getCertificates();
	List<Certificate> certificates = Arrays.asList(signers);
        return certificates.containsAll(certs);
    }
    
    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
	if (!(grant instanceof CertificateGrant)) return false;
	if (!super.impliesEquivalent(grant)) return false;
	return certs.equals(((CertificateGrant)grant).certs);
    }
    
    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        return pgb.certificates(certs.toArray(new Certificate[certs.size()]),
				alias.toArray(new String[alias.size()]))
                //.exclude(exclusion)
                .context(PermissionGrantBuilder.CODESOURCE_CERTS);
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
