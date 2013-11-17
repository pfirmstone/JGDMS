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
import java.security.Principal;
import net.jini.security.policy.DynamicPolicy;

/**
 * <p>
 * RevocablePolicy, is a Java Security Policy Provider that supports
 * Runtime Dynamic addition and removal of PermissionGrant's
 * </p><p>
 * Warning: Not all Permission's are truly revocable, while any Permission can 
 * be dynamically added and later removed from this policy, many JVM Permission
 * implementations are used in ways that allow references to escape
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
 * used to guard methods only, not Objects or their creation.  
 * </p><p>
 * See "Inside Java 2 Platform Security" 2nd Edition, ISBN:0-201-78791-1, page 176.
 * </p><p>
 * A Security Delegate, may be used as a wrapper with an identical interface to the object
 * it protects, a new Permission class must be implemented, for the Delegate's
 * use, in a checkPermission call, to protect access to the underlying
 * object's method. If an existing JVM Permission guards the underlying object,
 * the delegate needs to be given the standard JVM Permission.  DelegatePermission
 * has been created for the purpose of encapsulating an existing Permission.
 * </p><p>
 * The ability to revoke a Permission fully is intended for smart proxy's to
 * be given some trust temporarily, so that objects received from the smart proxy 
 * by a client cannot be used to continue gathering and sending information to
 * a remote server after the proxy has been discarded.
 * </p><p>
 * A list of standard Java Permission's that are confirmed safely 
 * revocable will be provided here.
 * </p>
 * @author Peter Firmstone
 * @see java.security.Policy
 * @see java.security.ProtectionDomain
 * @see java.security.AccessController
 * @see java.security.DomainCombiner
 * @see java.security.AccessControlContext
 * @see java.security.Permission
 * @see PermissionGrant
 * @see DelegatePermission
 * @see DelegateSecurityManager
 * @since 3.0.0
 */
public interface RevocablePolicy extends DynamicPolicy {
    
    /**
     * A dynamic grant.
     * 
     * Caveat: Not all Permission's once granted can be revoked.  When a Permission
     * is checked, prior to passing a reference to a caller, that reference
     * has escaped any further Permission checks, meaning that the Permission
     * cannot be revoked for the caller holding a reference.
     * 
     * @param p
     * @return true if successful 
     * @since 3.0.0
     */
    public boolean grant(PermissionGrant p);
    /**
     * Checks if policy supports revocation.
     * @return true - If Revoke supported by underlying policy.
     * @since 3.0.0
     */
    public boolean revokeSupported();
}
