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
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;

/**
 *
 * @author Peter Firmstone
 * @since 3.0.0
 */
class ClassLoaderGrant extends ProtectionDomainGrant {
    private static final long serialVersionUID = 1L;
    private final int hashCode;
    @SuppressWarnings("unchecked")
    ClassLoaderGrant(WeakReference<ProtectionDomain> domain, Principal[] groups, 
            Permission[] perm){
        super(domain, groups, perm);
        int hash = 7;
        hash = 19 * hash + super.hashCode();
        hashCode = hash;
    }
    
        @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
	if (o.hashCode() != this.hashCode()) return false;
        if (o instanceof ClassLoaderGrant && super.equals(o)){
            return true;
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
    public boolean impliesEquivalent(PermissionGrant grant) {
	if (!(grant instanceof ClassLoaderGrant)) return false;
	ProtectionDomain myPd = domain.get();
	ProtectionDomain yourPd = ((ClassLoaderGrant)grant).domain.get();
	if (myPd != null && yourPd != null){
	    ClassLoader myCL = myPd.getClassLoader();
	    ClassLoader yourCL = yourPd.getClassLoader();
	    if (myCL != null && yourCL != null){
		return myCL.equals(yourCL);
	    }
	}
	return false;
	// The superclass has a narrower scope than ClassLoaderGrant.
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
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
