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

import java.security.Permission;
import java.util.List;

/**
 * RevokeableDynamicPolicy, is a Java Security Policy Provider that supports
 * Runtime Dynamically Grantable and Revokeable Permission's, in the form
 * of PermissionGrant's
 * 
 * @author Peter Firmstone
 * @see java.security.Policy
 * @see java.security.ProtectionDomain
 * @see java.security.AccessController
 * @see java.security.DomainCombiner
 * @see java.security.AccessControlContext
 * @see java.security.Permission
 */
public interface RevokeableDynamicPolicy {
    /**
     * Grant Permission's as specified in a List of PermissionGrant's
     * which can be added by concurrent threads.
     * 
     * @param grants
     */
    public void grant(List<PermissionGrant> grants);
    /**
     * Revoke, only removes any PermissionGrant's that are identical, typically
     * a List of Grant's is obtained by getPermssionGrant's which can be 
     * manipulated and investigated, any that are undesirable should be passed
     * to revoke.
     * 
     * Revokes can only be performed synchronuously with other Revokes.
     * 
     * @param grants
     */
    public void revoke(List<PermissionGrant> grants);
    /**
     * Get a List copy of the current PermissionGrant's in force.
     * @return
     */
    public List<PermissionGrant> getPermissionGrants();
    /**
     * The Revocation of Permission's requires a new construct for controlling
     * access.  Typically many objects that provide privileged functionality
     * are guarded in their constructor by a checkPermission(Permission) call
     * or by a GuardedObject, once this check has succeeded, the caller receives
     * a reference to the guarded object.  These Permission's cannot be
     * revoked completely, because the reference has escaped, the permission 
     * check will not be called again.
     * 
     * Instead what is needed is a permission check that is efficient enough
     * to allow the methods that provide the privileged functionality to be
     * called for every method invocation.  What the ExecutionContextManager
     * does is minimise the checkPermission calls by skipping checkPermission for
     * any execution AccessControlContext that has already passed, unless
     * a Permission related to the one being managed is revoked, in which case
     * the cache of AccessControlContext's previously checked are cleared.
     * 
     * The ExecutionContextManager is specific only to one permission, this 
     * is the enabler for the reduced checkPermission calls, since a
     * Permission should behave in a persistent manner, once it passes, it
     * should always pass, unless revoked.
     * 
     * 
     * @param p Permission the ExecutionContextManager will check.
     * @return a new ExecutionContextManager instance.
     */
    public ExecutionContextManager getExecutionContextManager(Permission p);
    /**
     * 
     * @return true if Revoke supported.
     */
    public boolean revokeSupported();
}
