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

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import javax.security.auth.Subject;

/**
 * The PermissionGrantBuilder creates Dynamic PermissionGrant's based on
 * information provided by the user.  The user must have access to the
 * system policy and have permission to grant permissions.
 * 
 * A PermissionGrantBuilder implementation should also be used as the serialized form
 * for PermissionGrant's, the implementation of PermissionGrant's should
 * remain package private.
 * 
 * This prevents the serialized form becoming part of the public api.
 * 
 * Single Thread use only.
 * @author Peter Firmstone.
 * @see PermissionGrant
 */
public abstract class PermissionGrantBuilder {
   
    /**
     * The PermissionGrant generated will apply to all classes loaded by
     * the ClassLoader
     */ 
    public static final int CLASSLOADER = 0;
    /**
     * The PermissionGrant generated will apply to all classes loaded from
     * the CodeSource.  This has been provided for strict compatibility
     * with the standard Java Policy, where a DNS lookup may be performed
     * to determine if CodeSource.implies(CodeSource).  In addition, to
     * resolve a File URL, it will require disk access.
     * 
     * This is very bad for Policy performance, it's use is discouraged,
     * so much so, it may removed.
     * 
     * @deprecated use URI instead.
     */
    @Deprecated
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
     * 
     */
    public static final int CODESOURCE_CERTS = 3;
    /**
     * The PermissionGrant generated will apply to the Subject that has 
     * all the principals provided.
     * 
     * @see Subject
     */
    public static final int PRINCIPAL = 4;
    
    /**
     * The PermissionGrant generated will apply to the ProtectionDomain or
     * CodeSource who's URL is implied by the given URI.  This behaves 
     * similarly to CodeSource.implies(CodeSource), except no DNS lookup is
     * performed, nor file system access to verify the file exists.
     * 
     * The DNS lookup is avoided for security and performance reasons,
     * DNS is not authenticated and therefore cannot be trusted.  Doing so,
     * could allow an attacker to use DNS Cache poisoning to escalate
     * Permission, by imitating a URL with greater privileges.
     */
    public static final int URI = 5;
    
    public static PermissionGrantBuilder newBuilder(){
        return new PermissionGrantBuilderImp();
    }
    
    /**
     * resets the state for reuse, identical to a newly created 
     * PermissionGrantBuilder.
     */
    public abstract PermissionGrantBuilder reset();
   
    /**
     * Sets the context of the PermissionGrant to on of the static final 
     * fields in this class.
     * 
     * @param context
     * @return PermissionGrantBuilder
     * @throws IllegalStateException 
     */
    public abstract PermissionGrantBuilder context(int context) throws IllegalStateException;
    /**
     * Sets the CodeSource that will receive the PermissionGrant
     * @param cs
     * @return PermissionGrantBuilder
     * @deprecated use uri instead.
     */
    @Deprecated
    public abstract PermissionGrantBuilder codeSource(CodeSource cs);
    
    public abstract PermissionGrantBuilder multipleCodeSources();
    
    public abstract PermissionGrantBuilder uri(URI uri);
    /**
     * Extracts ProtectionDomain
     * from the Class for use in the PermissionGrantBuilder.  The ClassLoader
     * and ProtectionDomain are weakly referenced, when collected any 
     * created PermissionGrant affected will be voided.
     * @param cl
     * @return PermissionGrantBuilder.
     * @throws URISyntaxException - this exception may be swallowed if a URI
     * grant is not required.
     */
    public abstract PermissionGrantBuilder clazz(Class cl);
    /**
     * Sets the Certificate[] a CodeSource must have to receive the PermissionGrant.
     * @param certs
     * @return 
     */
    public abstract PermissionGrantBuilder certificates(Certificate[] certs);
    /**
     * Sets the Principal[] that a Subject must have to be entitled to receive
     * the PermissionGrant.
     * 
     * @param pals
     * @return 
     */
    public abstract PermissionGrantBuilder principals(Principal[] pals);
    /**
     * Sets the Permission's that will be granted.
     * @param perm
     * @return 
     */
    public abstract PermissionGrantBuilder permissions(Permission[] perm);
    /**
     * An Exclusion specifically excludes some code from receiving a 
     * PermissionGrant.  This may be to avoid a known security vulnerability,
     * where code that we don't have control over allows a reference to
     * escape without performing adequate security checks.
     * 
     * EG: I trust code signed by XXX, but they have a security vulnerability
     * in xxx.jar
     * 
     * A better implementation would be to use a deny policy, where exclusions
     * are checked before grants are checked.
     * 
     * In the default implementation, this doesn't apply to Principal only 
     * grants, only Certificate and ClassLoader based grants.
     * @param e
     * @return 
     */
    //public abstract PermissionGrantBuilder exclude(Exclusion e);
    /**
     * Build the PermissionGrant using information supplied.
     * @return an appropriate PermissionGrant.
     */
    public abstract PermissionGrant build();

    /**
     * 
     * @param domain
     * @return
     */
    public abstract PermissionGrantBuilder setDomain(WeakReference<ProtectionDomain> domain);
}
