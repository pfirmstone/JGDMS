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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 *
 * @author Peter Firmstone
 */
@Deprecated
class CodeSourceSetGrant extends CertificateGrant {
    private static final long serialVersionUID = 1L;
    private final Collection<CodeSource> cs;
    private final int hashCode;
    
    @SuppressWarnings("unchecked")
    CodeSourceSetGrant(CodeSource[] csource, Principal[] pals, Permission[] perm){
        super( null, pals, perm);
        int l = csource == null ? 0 : csource.length;
        Collection<CodeSource> list = new ArrayList<CodeSource>(l);
        int hash = 3;
        for (int i = 0 ; i < l ; i++ ){
            if ( csource[i] == null) 
                throw new NullPointerException("CodeSource array must not contain null values");
            list.add(normalizeCodeSource(csource[i]));
        }
        cs = Collections.unmodifiableCollection(list);
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
	if (o.hashCode() != this.hashCode()) return false;
        Boolean result = Boolean.FALSE;
        if (o instanceof CodeSourceSetGrant){
            final CodeSourceSetGrant c = (CodeSourceSetGrant) o;
            if ( !super.equals(o)) return false;
            result = AccessController.doPrivileged( 
                new PrivilegedAction<Boolean>(){
                    @Override
                    public Boolean run() {
                        if ( cs == c.cs) return Boolean.TRUE;
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
	if ( cs == null || cs.isEmpty() ) return true;
	if ( cl == null ) return false;
	return false;  //Indeterminate.
    }
    
    
    /**
     * Checks if passed CodeSource matches this PermissionGrant. Null CodeSource of
     * PermissionGrant implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     */
    @Override
    public boolean implies(CodeSource codeSource, Principal[] p) {
        if ( !implies(p)) return false;
        // sun.security.provider.PolicyFile compatibility for null CodeSource.
        // see com.sun.jini.test.spec.policyprovider.dynamicPolicyProvider.GrantPrincipal test.
        if ( codeSource == null ) return false; 
	if ( cs == null || cs.isEmpty()) return true;
        final CodeSource normalizedCodeSource = normalizeCodeSource(codeSource);
        Boolean result = AccessController.doPrivileged(
            new PrivilegedAction<Boolean>(){
                @Override
                public Boolean run() {
                    Iterator<CodeSource> it = cs.iterator();
                    while (it.hasNext()){
                        CodeSource c = it.next();
                        if (c == null ) return Boolean.TRUE;
                        if  (c.implies(normalizedCodeSource)) return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                }
            }
        );
        return result.booleanValue();
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        pgb.multipleCodeSources();
        Iterator<CodeSource> it = cs.iterator();
        while (it.hasNext()){
            pgb.codeSource(it.next());
        }
        pgb.context(PermissionGrantBuilder.CODESOURCE);
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
