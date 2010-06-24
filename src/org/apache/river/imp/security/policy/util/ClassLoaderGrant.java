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

import java.lang.ref.WeakReference;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;

/**
 *
 * @author Peter Firmstone
 */
class ClassLoaderGrant extends ProtectionDomainGrant implements PermissionGrant {

    @SuppressWarnings("unchecked")
    ClassLoaderGrant(WeakReference<ProtectionDomain> domain, Principal[] groups, 
            Permission[] perm){
        super(domain, groups, perm);
    }
    
        @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
        if (o instanceof ClassLoaderGrant && super.equals(o)){
            return true;
        }
        return false;
    }
   
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + super.hashCode();
        return hash;
    }     

    @Override
    public boolean implies(ProtectionDomain pd) {
        ClassLoader cl = null;
        Principal[] pals = null;
        if (pd != null){
            cl = pd.getClassLoader();
            pals = pd.getPrincipals();
        }
        return implies(cl, pals);
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        return pgb.context(PermissionGrantBuilder.CLASSLOADER);
    }
}
