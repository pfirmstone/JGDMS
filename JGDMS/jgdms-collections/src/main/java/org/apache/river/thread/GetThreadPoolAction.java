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
 * java.security.PrivilegedAction, to be used conveniently with an
 * AccessController.doPrivileged or Security.doPrivileged.
 *
 * There are two internal thread pools distinguished by the
 * {@code ThreadPoolPermission} name used to access them.  Both pools
 * are backed by virtual-thread-per-task executors.
 *
 * If there is a security manager, the run method will check the
 * ThreadPoolPermission for the requested thread pool.  If used with a
 * doPrivileged (the typical case), then only the protection domain of
 * the immediate caller of the doPrivileged needs the permission.
 *
 * The thread pools execute an action in a thread without the security
 * context in which the execute method was invoked, without any
 * subject, and with the system class loader as the context class
 * loader.  Actions are expected to complete with the same context
 * class loader and other thread-specific state (such as priority)
 * that they were started with.
 *
 * @author Sun Microsystems, Inc.
 **/
public final class GetThreadPoolAction implements PrivilegedAction<Executor> {

    /** pool of threads for executing tasks in system context */
    private static final ThreadPool systemThreadPool = new ThreadPool();

    /** pool of threads for executing tasks with user code */
    private static final ThreadPool userThreadPool = new ThreadPool();

    private static final Permission getSystemThreadPoolPermission =
new ThreadPoolPermission("getSystemThreadPool");
    private static final Permission getUserThreadPoolPermission =
new ThreadPoolPermission("getUserThreadPool");

    private final boolean user;

    /**
     * Creates an action that will obtain an internal thread pool.
     * When run, this action verifies that the current access control
     * context has permission to access the indicated pool.
     *
     * @paramuser if true, will obtain the user-code thread pool;
     *              if false, will obtain the system thread pool
     */
    public GetThreadPoolAction(boolean user) {
this.user = user;
    }

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
