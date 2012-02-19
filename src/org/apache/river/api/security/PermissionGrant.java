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
import java.security.ProtectionDomain;
import java.util.Collection;

/**
 * PermissionGrant's are expected to be effectively immutable,
 * thread safe and have a good hashCode implementation to perform well in
 * Collections.
 * 
 * You shouldn't pass around PermissionGrant's to just anyone as they can
 * provide an attacker with information about which Permissions may be granted.
 * 
 * Caveat Implementor: PermissionGrant's cannot perform privileged actions, 
 * whilst being used by the policy to make policy decisions, any privileged 
 * actions should performed prior to creating a PermissionGrant.  
 * Only PermissionGrant's belonging to the same ProtectionDomain as the
 * active Policy can perform PrivilegedAction's, since the Policy caches it's 
 * own domain Permissions during initialisation, it doesn't consult
 * PermissionGrant's after.
 * 
 * @author Peter Firmstone
 */
public interface PermissionGrant {
    
    /**
     * Optimisation for AllPermission.
     * 
     * @return true - if PermissionGrant contains AllPermission.
     */
    boolean isPrivileged();

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
    boolean implies(ProtectionDomain pd);  
    /**
     * Checks if this PermissionGrant applies to the passed in ClassLoader
     * and Principal's.
     * 
     * Note that if this method returns false, it doesn't necessarily mean
     * that the grant will not apply to the ClassLoader, since it will depend on 
     * the contents of the ClassLoader and that is indeterminate. It just
     * indicates that the grant definitely does apply if it returns true.
     * 
     * If this method returns false, follow up using the ProtectionDomain for a
     * more specific test, which may return true.
     */
    boolean implies(ClassLoader cl, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in CodeSource
     * and Principal's.
     * @param cs
     * @return 
     */
    boolean implies(CodeSource codeSource, Principal[] pal);

    @Override
    boolean equals(Object o);

    /**
     * Returns an unmodifiable Collection of permissions defined by this
     * PermissionGrant, which may be empty, but not null.
     * @return
     */
    Collection<Permission> getPermissions();

    @Override
    int hashCode();

    /**
     * Returns true if this PermissionGrant defines no Permissions, or if
     * a PermissionGrant was made to a ProtectionDomain that no longer exists,
     * or if the Exclusion excludes the PermissionGrant.
     * 
     */
    public boolean isVoid();
    
    /**
     * Provide a suitable PermissionGrantBuilder, the user can use to
     * produce a new PermissionGrant.
     * 
     * @return
     */
    public PermissionGrantBuilder getBuilderTemplate();

}
