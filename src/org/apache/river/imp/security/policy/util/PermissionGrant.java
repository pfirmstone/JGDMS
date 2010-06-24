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

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collection;

/**
 * PermissionGrant's are expected to be effectively immutable,
 * threadsafe and have a good hashCode implementation to perform well in
 * Collections.
 * @author Peter Firmstone
 */
public interface PermissionGrant {
    /**
     * A DynamicPolicy implementation can use a PermissionGrant as a container
     * for Dynamic Grant's.  A PermissionGrant is first asked by the Policy
     * if it implies a Particular ProtectionDomain, if it does, the Policy
     * calls getPermissions.
     * @param pd ProtectionDomain
     * @return
     */
    public boolean implies(ProtectionDomain pd);
    /**
     * Checks if this PermissionGrant applies to the passed in ClassLoader
     * and Principal's.
     */
    public boolean implies(ClassLoader cl, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in CodeSource
     * and Principal's.
     * @param cs
     * @return 
     */
    public boolean implies(CodeSource codeSource, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in Certificate's
     * and Principal's.
     */
    public boolean implies(Certificate[] certs, Principal[] pal);
    /**
     * Returns an unmodifiable collection of permissions defined by this
     * PolicyEntry, may be <code>null</code>.
     * @return 
     */
    public Collection<Permission> getPermissions();
    /**
     * Returns true if this PermissionGrant defines no Permissions, or if
     * a PermissionGrant was made to a ProtectionDomain that no longer exists.
     */
    public boolean isVoid();
    @Override
    public boolean equals(Object o);
    @Override
    public int hashCode();
    @Override
    public String toString();
    
    
}
