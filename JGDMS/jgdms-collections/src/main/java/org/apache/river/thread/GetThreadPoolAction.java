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

package org.apache.river.thread;

import java.security.Permission;
import java.security.PrivilegedAction;

/**
 * Provides security-checked access to internal thread pools as a
 * {@link PrivilegedAction}, to be used conveniently with
 * {@code AccessController.doPrivileged} or {@code Security.doPrivileged}.
 *
 * <h2>Two pools</h2>
 *
 * <p>There are two internal thread pools distinguished by the
 * {@link ThreadPoolPermission} name used to access them.  Both pools are
 * backed by virtual-thread-per-task executors.
 *
 * <dl>
 *   <dt><b>System pool</b> ({@code user=false},
 *       permission {@code "getSystemThreadPool"})</dt>
 *   <dd>For long-running infrastructure tasks: TLS accept loops, background
 *       maintenance threads, connection management.  Tasks run with no
 *       captured caller {@code AccessControlContext} stack and no propagated
 *       {@link javax.security.auth.Subject} — the caller's {@code doPrivileged}
 *       boundary sheds the user stack before submission, giving infrastructure
 *       code a clean system context.  This prevents user-level
 *       {@code ProtectionDomain}s from narrowing permission checks inside
 *       system tasks (confused-deputy protection) and ensures that
 *       {@code createVirtualThread} and guard checks fire against the system
 *       context rather than against the caller's user context.  In DirtyChai
 *       deployments, SPIFFE workload identity is carried via
 *       {@code ProtectionDomain} principal stamping and is always available
 *       in the system pool regardless of any ambient user scope.</dd>
 *
 *   <dt><b>User pool</b> ({@code user=true},
 *       permission {@code "getUserThreadPool"})</dt>
 *   <dd>For short-lived RPC dispatch tasks and other work .</dd>
 * </dl>
 *
 * @author Sun Microsystems, Inc.
 * @see ThreadPool
 * @see SubjectPropagatingThreadPool
 **/
public final class GetThreadPoolAction implements PrivilegedAction<Executor> {

    /** Pool for long-running infrastructure tasks — no subject propagation. */
    private static final ThreadPool systemThreadPool = new ThreadPool();

    /**
     * Pool for short-lived RPC dispatch tasks — no subject propagation.
     */
    private static final ThreadPool userThreadPool = new ThreadPool();

    private static final Permission getSystemThreadPoolPermission =
            new ThreadPoolPermission("getSystemThreadPool");
    private static final Permission getUserThreadPoolPermission =
            new ThreadPoolPermission("getUserThreadPool");

    private final boolean user;

    /**
     * Creates an action that will obtain an internal thread pool.
     * When run, this action verifies that the current access control
     * context has permission to access the indicated pool.  Neither
     * pool is suitable for Subject propagation.
     *
     * @param user if {@code true}, obtains the user-code
     *             thread pool; if {@code false}, obtains the system
     *             (infrastructure) thread pool
     */
    public GetThreadPoolAction(boolean user) {
        this.user = user;
    }

    @Override
    public Executor run() {
        if (user) {
            getUserThreadPoolPermission.checkGuard(this);
            return userThreadPool;
        } else {
            getSystemThreadPoolPermission.checkGuard(this);
            return systemThreadPool;
        }
    }
}
