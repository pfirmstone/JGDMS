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
 * <p>
 * RevokeableDynamicPolicy, is a Java Security Policy Provider that supports
 * Runtime Dynamic addition and removal of PermissionGrant's
 * </p><p>
 * Warning: Not all Permission's are truely revokeable, while any Permission can 
 * be dynamically added and later removed from this policy, the majority of JVM Permission's
 * don't prevent references from escaping.
 * </p><p>
 * To quote Tim Blackman, from river-dev:
 * </p><p><CITE>
 * I remember talking with Bob and Mike Warres about this.  The problem with
 * removing permission grants is that when code is granted a permission, 
 * it can very likely squirrel away something -- an object, or another 
 * capability available through the granted permission -- that will permit 
 * it to perform the same operation again without the JVM checking for 
 * the permission again.
 * </CITE>
 * </p><p>
 * In order for a Permission to be fully revoked, the permission must be
 * used to guard methods only, not Objects or their creation.  A Security 
 * Delegate, may be used as a wrapper with an identical interface to the object
 * it protects, a new Permission class must be implemented, for the Delegate's
 * use, in a checkPermission call, to protect access to the underlying
 * object's method. If an existing JVM Permission guards the underlying object,
 * the delegate needs to be given the standard JVM Permission.
 * </p><p>
 * The ability to revoke a Permission fully is intended for smart proxy's to
 * be given some trust temporarily, so that objects recieved from the smart proxy 
 * by a client cannot be used to continue gathering and sending information to
 * a remote server after the proxy has been discarded.
 * </p><p>
 * A list of Permission's that are revokeable will be provided here.
 * </p><p>
 * TODO: Write some Permission's that are revokeable and delegates
 * for the network and test.
 * </p><p>
 * Note: This feature is currently experimental, it should not be relied upon for any
 * application and may never make release.
 * </p>
 * @author Peter Firmstone
 * @see java.security.Policy
 * @see java.security.ProtectionDomain
 * @see java.security.AccessController
 * @see java.security.DomainCombiner
 * @see java.security.AccessControlContext
 * @see java.security.Permission
 * @see PermissionGrant
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
     * @throws java.lang.Exception if revoke unsuccessful.
     */
    public void revoke(List<PermissionGrant> grants) throws Exception;
    /**
     * Get a List copy of the current PermissionGrant's in force.
     * @return
     */
    public List<PermissionGrant> getPermissionGrants();
    /**
     * The Revocation of Permission's requires an optimised check permission
     * call.  Typically many objects that provide privileged functionality
     * are guarded in their constructor by a checkPermission(Permission) call
     * or by a GuardedObject, once a check has succeeded, the caller receives
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
     * @param p Permission the ExecutionContextManager will check.
     * @return a new ExecutionContextManager instance.
     */
    public ExecutionContextManager getExecutionContextManager();
    /**
     * 
     * @return true if Revoke supported.
     */
    public boolean revokeSupported();
}
