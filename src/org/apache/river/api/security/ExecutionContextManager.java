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
 * ensures that checkPermission isn't called again until the context changes, or
 * the Permission checked by this ExecutionContextManager experiences a 
 * revoke for any ProtectionDomain by the RevokeableDynamicPolicy.
 * </p><p>
 * A Runnable may be submitted to the ExecutionContextManager to be executed
 * when a Permission Revocation matching the stored Permission occurs.
 * </p><p>
 * Use of this class is not limited to Revokeable Permission's.
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
     * ExecutionContextManager is in memory usage terms.
     * </p><p>
     * This method also add's the current context to the current execution 
     * cache, it is not removed from that cache until after the addAction 
     * Runnable has been run, or accessControlExit() has been called.
     * </p>
     * 
     * @throws java.security.AccessControlException 
     */
    public void checkPermission() throws AccessControlException;
    
    /**
     * <p>
     * This method is to advise the ExecutionContextManager that the
     * current method or protected region has returned, it must
     * always follow the checkPermission() call, in response,
     * the ECM removes the current context from the execution context cache.
     * </p><p>
     * If the execution context is still in the cache at the time of 
     * revocation, the Runnable added by addAction will be run only if 
     * affected directly by the revocation.  This is determined by
     * AccessControlContext.checkPermission(Permission p) where p is the 
     * Permission affected.
     * </p><p>
     * This should be executed in the finally{} block of a try catch statement,
     * which always executes in the event of an exception or normal return.
     * </p>
     * <code>
     * try{
     *	    ecm.checkPermission();
     *	    // do something
     *	    return;
     * } catch (AccessControlException e) {
     *	    throw new SecurityException("Method blah caused an...", e);
     * } finally {
     *	    ecm.accessControlExit();
     * }
     * </code>
     * <p>
     * This should not be confused with AccessController.doPrivileged blocks
     * </p>
     */
    public void accessControlExit();

    /**
     * Get the Permission monitored by this ExecutionContextManager.
     * @return Permission monitored by the ExecutionContextManager
     */
    public Permission getPermission();

    /**
     * <p>
     * Action to be taken in event that the Permission monitored by this
     * ExecutionContextManager has experienced a revocation event.  This
     * allows Sockets to be closed, or any clean up to occur or any other
     * task that must be performed to reset state.
     * </p><p>
     * This may not be the only action performed, since the same
     * ExecutionContextManager may be used by multiple clients, the Runnable
     * is only weakly referenced, garbage collection is relied upon for its
     * removal.
     * </p><p>
     * The implementer must have the Permission's required for
     * execution, no privileges are assigned.
     * </p>
     * @param r - Clean up task to be performed.
     */
    public void addAction(Runnable r);
}
