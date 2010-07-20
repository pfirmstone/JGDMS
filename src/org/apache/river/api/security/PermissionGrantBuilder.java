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

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;

/**
 * The PermissionGrantBuilder creates Dynamic PermissionGrant's based on
 * information provided by the user.  The user must have access to the
 * system policy and have permission to grant permissions.
 * 
 * Don't Serialize the PermissionGrant, instead get a builder template
 * and send that.
 * 
 * Single Thread use only.
 * @author Peter Firmstone.
 */
public interface PermissionGrantBuilder {
   
    /**
     * The PermissionGrant generated will apply to all classes loaded by
     * the ClassLoader
     */ 
    public static final int CLASSLOADER = 0;
    /**
     * The PermissionGrant generated will apply to all classes loaded from
     * the CodeSource.
     */
    public static final int CODESOURCE = 1;
    /**
     * The PermissionGrant generated will apply to all classes belonging to
     * the ProtectionDomain.  This is actually a simplification for the 
     * programmer the PermissionGrant will apply to the CodeSource and the
     * ClassLoader combination, the reason for this is the DomainCombiner may
     * create new instances of ProtectionDomain's from those that exist on
     * the stack.
     * @see java.security.AccessControlContext
     * @see java.security.DomainCombiner
     * @see javax.security.auth.SubjectDomainCombiner
     */
    public static final int PROTECTIONDOMAIN = 2;
    /**
     * The PermissionGrant generated will apply to all classes loaded from
     * CodeSource's that have at a minimum the defined array Certificate[]
     */
    public static final int CODESOURCE_CERTS = 3;
    
    /**
     * Build the PermissionGrant using information supplied.
     * @return an appropriate PermissionGrant.
     */
    public abstract void reset();

    public abstract PermissionGrantBuilder context(int context) throws IllegalStateException;

    public abstract PermissionGrantBuilder codeSource(CodeSource cs);

    public abstract PermissionGrantBuilder clazz(Class cl);

    public abstract PermissionGrantBuilder certificates(Certificate[] certs);

    public abstract PermissionGrantBuilder principals(Principal[] pals);
    
    public abstract PermissionGrantBuilder permissions(Permission[] perm);

    public abstract PermissionGrantBuilder deny(Denied denial);
    
    public abstract PermissionGrant build();
}
