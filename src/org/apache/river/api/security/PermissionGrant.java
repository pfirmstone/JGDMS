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

import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collection;

/**
 * PermissionGrant's are expected to be effectively immutable,
 * threadsafe and have a good hashCode implementation to perform well in
 * Collections.
 * 
 * You shouldn't pass around PermissionGrant's to just anyone as they can
 * provide an attacker with Permission's that may have been granted.
 * The RevokeableDynamicPolicy must be asked for the PermissionGrantBulder which creates the
 * PermissionGrant's for a less Privileged user.  The user must first have
 * access to a Policy.
 * 
 * @author Peter Firmstone
 * @see RevokeableDynamicPolicy
 */
public abstract class PermissionGrant {
    /**
     * Implied Context of Grant
     */ 
    public static final int CLASSLOADER = 0;
    public static final int CODESOURCE = 1;
    public static final int PROTECTIONDOMAIN = 2;
    public static final int CODESOURCE_CERTS = 3;
    public PermissionGrant(){
        AccessController.checkPermission(new RuntimePermission("getProtectionDomain"));
        AccessController.checkPermission(new RuntimePermission("getClassLoader"));
    }
    
    /**
     * This is here to provide functionality for less privileged code / users who
     * have Permission to get a Policy. For cases where that user / code doesn't
     * have access to ProtectionDomain's or ClassLoaders.
     * 
     * This is equalivant to the methods below, with Privileges.
     * 
     * A user will still require specific GrantPermission's before being
     * able to build a PermissionGrant.
     * 
     * @param clazz - The class file.
     * @param pal - An array of Principal's
     * @param context - PermissionGrantBuilder context constant.
     * @return
     */
    @SuppressWarnings("unchecked")
    public final boolean implies( final Class clazz, final Principal[] pal, final int context){
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run(){
                switch (context){
                    case CLASSLOADER: {
                        return implies(clazz.getClassLoader(), pal);
                    }
                    case PROTECTIONDOMAIN: {
                        return implies(clazz.getProtectionDomain(), pal);
                    }
                    case CODESOURCE: {
                        return implies(clazz.getProtectionDomain().getCodeSource(), pal);
                    }
                    case CODESOURCE_CERTS: {
                        return implies(clazz.getProtectionDomain()
                                .getCodeSource().getCertificates(),
                                pal);
                    }
                    default:
                        throw new IllegalStateException("int context unknown");
                }
            }
        });
        return false;
    }
    
    /**
     * A DynamicPolicy implementation can use a PermissionGrant as a container
     * for Dynamic Grant's.  A PermissionGrant is first asked by the Policy
     * if it applies to a Particular ProtectionDomain, if it does, the Policy
     * calls getPermissions.
     * 
     * Dynamic grants can be denied to some ProtectionDomains based on
     * CodeSource or URL's for codebases, this is to remove the possiblity of
     * executing Permissions for vulnerable codebases, once a vulnerability
     * is identified after the fact.
     * 
     * @param pd ProtectionDomain
     * @return
     * @see RevokeableDynamicPolicy
     */
    public final boolean applicable(ProtectionDomain pd){
        Deny check = getDenials();
        if (check == null){
            return implies(pd);
        } else if ( check.allow(pd)) {
            return implies(pd);
        }
        return false;
    }
    
    protected abstract Deny getDenials();
    
    /**
     * A DynamicPolicy implementation can use a PermissionGrant as a container
     * for Dynamic Grant's.  A PermissionGrant is first asked by the Policy
     * if it implies a Particular ProtectionDomain, if it does, the Policy
     * calls getPermissions.
     * 
     * Only this method is utilised by the RevokeableDynamicPolicy, 
     * for Permission checks, extending classes must implement it.
     * 
     * @param pd ProtectionDomain
     * @return
     * @see RevokeableDynamicPolicy
     */
    protected abstract boolean implies(ProtectionDomain pd);
    /**
     * Checks if this PermissionGrant applies to the ProtectionDomain with
     * the given principals, this is useful for Subject's where a Subject's
     * Principals are added to a ProtectionDomain by a Policy check by the
     * AccessController and DomainCombiner.  This is not used by the Policy.
     * This is a utility method to determine if a ProtectionDomain had a set of
     * Principals what the effect is.  
     * 
     * The ProtectionDomain's internal set of Principals are ignored for 
     * this method.
     * 
     * @param pd
     * @param pal
     * @return
     * @see AccessController
     * @see DomainCombiner
     */
    public abstract boolean implies(ProtectionDomain pd, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in ClassLoader
     * and Principal's.
     * 
     * Note that if this method returns false, it doesn't necessarily mean
     * that the grant will not apply to the ClassLoader, since it will depend on 
     * the contents of the ClassLoader and that is inderterminate. It just
     * indicates that the grant definitely does apply if it returns true.
     * 
     * If this method returns false, follow up using the ProtectionDomain for a
     * more specific test, which may return true.
     */
    public abstract boolean implies(ClassLoader cl, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in CodeSource
     * and Principal's.
     * @param cs
     * @return 
     */
    public abstract boolean implies(CodeSource codeSource, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in Certificate's
     * and Principal's.
     */
    public abstract boolean implies(Certificate[] certs, Principal[] pal);
    /**
     * Returns an unmodifiable collection of permissions defined by this
     * PolicyEntry, may be <code>null</code>.
     * @return 
     */
    public abstract Collection<Permission> getPermissions();
    /**
     * Provides a builder that will replicate the PermissionGrant.  Useful
     * when you want to change the Permission's but not the
     * context.
     */
    public abstract PermissionGrantBuilder getBuilderTemplate();
    /**
     * Returns true if this PermissionGrant defines no Permissions, or if
     * a PermissionGrant was made to a ProtectionDomain that no longer exists.
     */
    public abstract boolean isVoid();
    @Override
    public abstract boolean equals(Object o);
    @Override
    public abstract int hashCode();
}
