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

import java.security.PrivilegedAction;

/**
 * A PrivilegedAction for creating a new platform thread conveniently with an
 * AccessController.doPrivileged or Security.doPrivileged.
 *
 * All constructors allow the choice of the Runnable for the new
 * thread to execute, the name of the new thread (which will be
 * prefixed with the constant NAME_PREFIX), and whether or not it will
 * be a daemon thread.
 *
 * The new thread will have the system class loader as its initial
 * context class loader (that is, its context class loader will NOT be
 * inherited from the current thread).
 *
 * <p>On DirtyChai deployments, thread creation via {@link Thread#ofPlatform()}
 * is guarded by {@code RuntimePermission("createPlatformThread")}, replacing
 * the obsolete {@code RuntimePermission("modifyThreadGroup")} that was
 * previously required for the applet-era ThreadGroup walk.
 *
 * @authorSun Microsystems, Inc.
 **/
public final class NewThreadAction implements PrivilegedAction<Thread> {

    static final String NAME_PREFIX = "(JSK) ";

    private final Runnable runnable;
    private final String name;
    private final boolean daemon;
    private final int stackSize;

    NewThreadAction(Runnable runnable, String name, boolean daemon, int stackSize) {
        this.runnable = runnable;
        this.name = name;
        this.daemon = daemon;
        this.stackSize = stackSize;
    }

    /**
     * Creates an action that will create a new platform thread.
     *
     * @paramrunnable the Runnable for the new thread to execute
     * @paramname the name of the new thread
     * @paramdaemon if true, new thread will be a daemon thread;
     *              if false, new thread will not be a daemon thread
     */
    public NewThreadAction(Runnable runnable, String name, boolean daemon) {
        this(runnable, name, daemon, 0);
    }

    /**
     * Creates an action that will create a new platform thread.
     *
     * @paramrunnable the Runnable for the new thread to execute
     * @paramname the name of the new thread
     * @paramdaemon if true, new thread will be a daemon thread;
     *              if false, new thread will not be a daemon thread
     * @paramuser no-op parameter retained for source compatibility;
     *              previously controlled which ThreadGroup the thread was
     *              created in, but ThreadGroup-based isolation was never an
     *              effective security boundary and has been removed.
     */
    public NewThreadAction(Runnable runnable, String name, boolean daemon,
                           boolean user)
    {
        this(runnable, name, daemon, 0);
    }

    public Thread run() {
        Thread t = Thread.ofPlatform()
            .name(NAME_PREFIX + name)
            .stackSize(stackSize)
            .daemon(daemon)
            .unstarted(runnable);
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        return t;
    }
}
