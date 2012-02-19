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

package net.jini.security;

import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * Interface implemented by objects representing security contexts, returned
 * from the {@link Security#getContext getContext} method of the {@link
 * Security} class, which in turn may obtain them from a security manager or
 * policy provider implementing the
 * {@link net.jini.security.policy.SecurityContextSource} interface.
 * Each <code>SecurityContext</code> contains an {@link AccessControlContext}
 * instance representing the access control context in place when the security
 * context was snapshotted; this instance can be passed to one of the
 * <code>doPrivileged</code> methods of the {@link
 * java.security.AccessController} class to restore the
 * <code>AccessControlContext</code> portion of the overall security context.
 * Additional state (if any) carried by the security context can be restored
 * for the duration of a {@link PrivilegedAction} or {@link
 * PrivilegedExceptionAction} by passing that action to the appropriate wrap
 * method of the <code>SecurityContext</code> instance, and then executing the
 * returned "wrapper" action.  These two operations--restoring the access
 * control context, and restoring any additional context encapsulated by the
 * <code>SecurityContext</code> instance--should be performed in conjunction
 * with a single <code>AccessController.doPrivileged</code> call, as
 * illustrated below:
 * <pre>
 *      // snapshot context
 *      SecurityContext ctx = Security.getContext();
 *
 *      // restore context
 *      AccessController.doPrivileged(
 *          ctx.wrap(action), ctx.getAccessControlContext());
 * </pre>
 * 
 * <BOLD>
 * Implementations must override Object equals and hashCode.
 * </BOLD>
 * 
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface SecurityContext {

    /**
     * Returns a security context-restoring <code>PrivilegedAction</code> that
     * wraps the given action, or returns the action itself if the security
     * context does not include any non-<code>AccessControlContext</code> state
     * to restore.  The <code>run</code> method of the "wrapper" action (if
     * any) restores the non-<code>AccessControlContext</code> state of the
     * security context before invoking the <code>run</code> method of the
     * wrapped action, and unrestores that state after the wrapped action's
     * <code>run</code> method has completed (normally or otherwise).  The
     * value returned or exception thrown by the wrapped action's
     * <code>run</code> method is propagated through the <code>run</code>
     * method of the wrapper action.
     *
     * @param <T> return type of PrivilegedAction
     * @param action the action to be wrapped
     * @return security context-restoring action wrapping <code>action</code>,
     * or <code>action</code> if no wrapping is necessary
     * @throws NullPointerException if <code>action</code> is <code>null</code>
     */
    <T> PrivilegedAction<T> wrap(PrivilegedAction<T> action);

    /**
     * Returns a security context-restoring
     * <code>PrivilegedExceptionAction</code> that wraps the given action, or
     * returns the action itself if the security context does not include any
     * non-<code>AccessControlContext</code> state to restore.  The
     * <code>run</code> method of the "wrapper" action (if any) restores the
     * non-<code>AccessControlContext</code> state of the security context
     * before invoking the <code>run</code> method of the wrapped action, and
     * unrestores that state after the wrapped action's <code>run</code> method
     * has completed (normally or otherwise).  The value returned or exception
     * thrown by the wrapped action's <code>run</code> method is propagated
     * through the <code>run</code> method of the wrapper action.
     *
     * @param <T> return type of PrivilegedExceptionAction
     * @param action the action to be wrapped
     * @return security context-restoring action wrapping <code>action</code>,
     * or <code>action</code> if no wrapping is necessary
     * @throws NullPointerException if <code>action</code> is <code>null</code>
     */
    <T> PrivilegedExceptionAction<T> wrap(PrivilegedExceptionAction<T> action);

    /**
     * Returns access control context portion of snapshotted security context.
     *
     * @return access control context portion of snapshotted security context
     */
    AccessControlContext getAccessControlContext();
}
