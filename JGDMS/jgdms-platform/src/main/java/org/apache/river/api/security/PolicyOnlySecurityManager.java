/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.security.Permission;
import java.security.ProtectionDomain;

/**
 * PolicyOnlySecurityManager allows the Principle of Least Privilege to be used
 * with a security Policy, while retaining scalability and performance.
 * <p>
 * ProtectionDomains created with the two argument constructor are static,
 * they never consult the Policy, however adding static Permission's to a
 * ProtectionDomain, for anything other than AllPermission can have a 
 * significant performance impact.  For this reason 
 * {@link ConcurrentPolicyFile#getPermissions(java.security.CodeSource) }
 * method returns an empty PermissionCollection for anything other than
 * AllPermission, however ClassLoader's call this method to obtain the 
 * static permissions for ProtectionDomain's using a two argument constructor. 
 * This has never been an issue when AllPermission is used for local trusted
 * code, however there are times when we want to limit privileges to the
 * principle of least privilege for local code too.
 * <p>
 * This SecurityManager implementation replaces all ProtectionDomain's with
 * a four argument constructor, so the policy provider will always be
 * consulted.
 *
 * @author peter
 */
public class PolicyOnlySecurityManager extends CombinerSecurityManager {
    
    
    @Override
    protected boolean checkPermission(ProtectionDomain pd, Permission p){
        pd = new ProtectionDomain(
                pd.getCodeSource(),
                pd.getPermissions(),
                pd.getClassLoader(),
                pd.getPrincipals()
        );
        return pd.implies(p);
    }
}
