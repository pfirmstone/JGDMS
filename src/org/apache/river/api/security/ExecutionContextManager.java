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

import java.security.AccessControlException;
import java.security.AccessControlContext;
import java.security.Permission;
import java.util.Collection;

/**
 * <p>
 * An ExecutionContextManager is designed to be repeatedly called, where calling
 * AccessController.checkPermission(Permission) is usually too great an overhead.
 * </p><p>
 * The ExecutionContextManager will only call 
 * AccessControlContext.checkPermission(Permission) once, for each context.  This
 * ensures checkPermission isn't re called, until the context changes, or
 * the Permission checked by this ExecutionContextManager experiences a 
 * revoke for any dynamic ProtectionDomain using a RevokeableDynamicPolicy.
 * </p><p>
 * A Reaper may be submitted to the ExecutionContextManager to be executed
 * when a Permission Revocation matching the stored Permission occurs.
 * </p><p>
 * Use of this class is not limited to Revokeable Permission's, although a
 * revocation event will cause #checkPermission(Permission) to block
 * until the revocation process is complete.
 * </p><p>
 * When protecting method's, the method must return from the try block.
 * </p>
 * @author Peter Firmstone
 * @see RevokeableDynamicPolicy
 * @see Permission
 * @see AccessControlContext
 */
public interface ExecutionContextManager {

    /**
     * <p>
     * This is a call made by a Security Delegate, or other Object used to
     * control access to privileged methods or constructors, similar to the
     * AccessControll.checkPermission(Permission) call.  
     * The Permission check is optimised.
     * Typically a method may only be concerned with a single Permission check,
     * but in many existing cases, the AccessController check is too expensive 
     * to be called on every method invocation.  The ExecutionContextManager 
     * should optimise this call by ensuring that checkPermission(Permission) is only
     * called once per AccessControlContext.  In other words if the caller's
     * AccessControlContext hasn't changed, then checkPermission(Permission)
     * isn't called again as it would be assumed to succeed, unless the 
     * RevokeableDynamicPolicy revokes a Permission with the same class,
     * in which case the Permission must be checked again.
     * </p><p>
     * Typically in the Java platform it isn't feasable to call 
     * AccessController.checkPermission on every invocation, as a result,
     * there are guarded objects or security sensitive objects have
     * SecurityManager checkPermission(Permission) called in their constructor.
     * </p><p>
     * ExecutionContextManager provides a more thorough form of protection.
     * </p><p>
     * ExecutionContextManager should be used for repeated
     * calls, it caches the results from the AccessControlContext.
     * </p><p>
     * Clients using the ExecutionContextManager, should be careful
     * to release references to their Permission objects,
     * since garbage collection is relied upon to clean up cached 
     * AccessControlContext's, conversely, Permission objects, shouldn't be
     * created in the checkPermission( new RuntimePermission("blah")) call,
     * since this would cause the object to be created on every invocation
     * and probably garbage collected between invocations, thrashing the cache
     * and causing an AccessControlContext.checkPermssion(Permission) call
     * as well.
     * </p><p>
     * In addition this method add's the current thread and 
     * AccessControl context to the execution 
     * cache, it is not removed from that cache until after end() 
     * has been called.
     * </p>
     * 
     * @param perms Permissions to be checked, if result not already in cache.
     * @throws java.security.AccessControlException
     * @throws java.lang.NullPointerException 
     */
    public void checkPermission(Collection<Permission> perms) 
	    throws AccessControlException, NullPointerException;
}