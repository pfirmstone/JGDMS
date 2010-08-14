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
import java.security.Permission;
import java.util.Set;

/**
 * <p>
 * An ExecutionContextManager is designed to be repeatedly called, where calling
 * AccessController.checkPermission(Permission) is too great an overhead.
 * </p><p>
 * The ExecutionContextManager will only call 
 * AccessControlContext.checkPermission(Permission) once, for each context.  This
 * ensures checkPermission isn't re called, until the context changes, or
 * the Permission checked by this ExecutionContextManager experiences a 
 * revoke for any ProtectionDomain via a RevokeableDynamicPolicy.
 * </p><p>
 * A Reaper may be submitted to the ExecutionContextManager to be executed
 * when a Permission Revocation matching the stored Permission occurs.
 * </p><p>
 * Use of this class is not limited to Revokeable Permission's.
 * </p><p>
 * Typical usage:
 * </p>
 * <code>
 * ecm.begin(reaper);
 * try{
 *	    ecm.checkPermission(permissionA);
 *	    ecm.checkPermission(permissionB);
 *	    // do something
 *	    return;
 * } finally {
 *	    ecm.end();
 * }
 * </code>
 * <p>
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
     * Marks the beginning of Management of the Execution context, of the
     * AccessControlContext and submits a reaper to intercept and clean up
     * in the event of a revocation during the execution of the try finally
     * block.  This method may be omitted if a Reaper is not required.  The
     * consequence of there being no reaper, is that a call in progress during
     * revocation will return normally immediately after revocation has 
     * occurred, the permission will have been checked prior to revocation
     * however and any further permission checks, if they have been revoked
     * will throw an AccessControlException.
     * <p></p>
     * This links the current Thread to a Runnable
     * Reaper.  The checkPermission() call places the Thread and
     * AccessControlContext into the execution cache.
     * <p></p>
     * The execution cache is used to monitor methods or protected blocks that
     * must be intercepted 
     * </p>
     * @param r - Reaper provided to clean up if Revocation occurs during
     * the execution that follows this call, until the try block exits, 
     * the current thread is not interrupted, rather the reaper is expected
     * to know what resources need to be closed.
     */
    void begin(Reaper r);

    /**
     * <p>
     * This is a call made by a Security Delegate, or other Object used to
     * control access to privileged methods or constructors, similar to the
     * AccessControll.checkPermission(Permission) call, but with the Permission
     * pre defined and unchanging.  The Permission check is optimised,
     * typically a method may only be concerned with a single Permission check,
     * but in many existing cases, the AccessController check is too expensive 
     * to be called on every method invocation.  The ExecutionContextManager 
     * should optimise this call by ensuring that checkPermission(Permission) is only
     * called once per AccessControlContext.  In other words if the caller's
     * AccessControlContext hasn't changed, then checkPermission(Permission)
     * isn't called again as it would be assumed to succeed, unless the 
     * RevokeableDynamicPolicy revokes a Permission with the same class,
     * in which case the Permission must be checked again.
     * </p><p>
     * Typically where it is not feasable to call AccessController.checkPermission
     * on every invocation, those objects are usually guarded or have the
     * checkPermission method called in the constructor.
     * </p><p>
     * ExecutionContextManager provides a more thorough form of protection.
     * </p><p>
     * ExecutionContextManager should be used sparingly, the more generic
     * or widely applicable the Permission, the more efficient the 
     * ExecutionContextManager is in memory usage terms.  Clients using
     * the ECM, should be careful to release references to their permission
     * objects, used permission checks, since garbage collection is relied
     * upon to clean up cached AccessControlContext's, conversely, the
     * permission shouldn't be created in the checkPermission(permission) call,
     * since this would cause the object to be created on every invocation
     * and probably garbage collected between invocations.
     * </p><p>
     * This method also add's the current thread and context to the execution 
     * cache, it is not removed from that cache until after end() 
     * has been called.
     * </p>
     * 
     * @param p Permission to be checked, if result not already in cache.
     * @throws java.security.AccessControlException
     */
    public void checkPermission(Permission p) throws AccessControlException;
    
    /**
     * <p>
     * This method is to advise the ExecutionContextManager that the
     * current method or protected region has returned, it must
     * always follow the checkPermission() call, in response,
     * the ECM removes the current context from the execution context cache
     * and releases the reference to the Runnable reaper.
     * </p><p>
     * If the execution context is still in the cache at the time of 
     * revocation, the reaper will be run only if affected directly by the 
     * revocation, the thread may be asked to wait for a short period, to
     * allow the determination to be made. 
     * Revocation applicability is determined by
     * AccessControlContext.checkPermission(Permission p) where p is the 
     * Permission affected.
     * </p><p>
     * This should be executed in the finally{} block of a try catch statement,
     * which always executes in the event of an exception or normal return.
     * </p>
     * <code>
     * ecm.begin(reaper);
     * try{
     *	    ecm.checkPermission(permission);
     *	    // do something
     *	    return;
     * } finally {
     *	    ecm.end();
     * }
     * </code>
     * <p>
     * This should not be confused with AccessController.doPrivileged blocks
     * </p>
     */
    void end();
}