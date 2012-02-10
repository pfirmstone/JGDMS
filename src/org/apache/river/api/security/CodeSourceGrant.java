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
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;

/**
 *
 * @author Peter Firmstone
 */
@Deprecated
class CodeSourceGrant extends CertificateGrant {
    private static final long serialVersionUID = 1L;
    private final CodeSource cs;
    private final int hashCode;
    
    @SuppressWarnings("unchecked")
    CodeSourceGrant(CodeSource cs, Principal[] pals, Permission[] perm){
        super( cs != null? cs.getCertificates(): null, pals, perm);
        this.cs = cs != null? normalizeCodeSource(cs) : null;
        int hash = 3;
        hash = 67 * hash + (this.cs != null ? this.cs.hashCode() : 0); // This may cause network or file access.
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
	if (o.hashCode() != this.hashCode()) return false;
        Boolean result = Boolean.FALSE;
        if (o instanceof CodeSourceGrant){
            if ( !super.equals(o)) return false;
            final CodeSourceGrant c = (CodeSourceGrant) o;
            if ( cs == c.cs) return true;
            result = AccessController.doPrivileged(
                new PrivilegedAction<Boolean>(){
                    public Boolean run(){
                        if ( cs != null ) {
                            if (cs.equals(c.cs)) return Boolean.TRUE;
                        }
                        return Boolean.FALSE;
                    }
                }
            );
        }
        return result.booleanValue();
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder(500);
        return sb.append(super.toString())
                 .append("CodeSource: \n")
                 .append( cs == null ? "null" : cs.toString())
                 .toString();
    }
    
    @Override
    public boolean implies(ClassLoader cl, Principal[] p){
	if ( !implies(p)) return false;
	if ( cs == null ) return true;
	if ( cl == null ) return false;
	return false;  //Indeterminate.
    }
    
    
    /**
     * Checks if passed CodeSource matches this PermissionGrant. Null CodeSource of
     * PermissionGrant implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     */
    @Override
    public boolean implies(final CodeSource codeSource, Principal[] p) {
        if ( !implies(p)) return false;
        // sun.security.provider.PolicyFile compatibility for null CodeSource.
        // see com.sun.jini.test.spec.policyprovider.dynamicPolicyProvider.GrantPrincipal test.
        if ( codeSource == null ) return false; 
	if ( cs == null || nullCS.equals(cs)) return true;
        Boolean result = AccessController.doPrivileged(new PrivilegedAction<Boolean>(){

            @Override
            public Boolean run() {
                Boolean outcome = cs.implies(normalizeCodeSource(codeSource)) ? Boolean.TRUE : Boolean.FALSE;
                return outcome;
            }
            
        });
	return result;
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        pgb.codeSource(cs)
           .context(PermissionGrantBuilder.CODESOURCE);
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
