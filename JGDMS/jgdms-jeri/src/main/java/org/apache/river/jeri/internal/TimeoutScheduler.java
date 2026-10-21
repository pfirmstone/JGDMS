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

package org.apache.river.jeri.internal;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;

/**
 * The one timer that ends connections which have run out of time to start:
 * a server's TLS handshake that has not finished, a mux connection whose
 * peer has not sent its header.
 *
 * <p>One thread serves all the endpoints of the JVM, so a flood of
 * connections costs queue entries and not threads.  A task must not block: it
 * closes a socket, or hands the work of closing a connection to another
 * thread.
 *
 * <p>The thread is a platform daemon thread, so that it is not held up when
 * the carrier threads of the virtual threads are all busy.  It is created
 * when this class is initialised, in a privileged action and under {@code
 * Subject.callAs(null, ...)}: a thread that is created lazily from a request
 * thread keeps that thread's subject and context class loader for the life
 * of the JVM.
 */
public final class TimeoutScheduler {

    private static final ScheduledThreadPoolExecutor TIMER = create();

    private TimeoutScheduler() { }

    private static ScheduledThreadPoolExecutor create() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(
            1, task -> AccessController.doPrivileged(
                (PrivilegedAction<Thread>) () -> Subject.callAs(null, () -> {
                    Thread t = new Thread(task, "connection timeout scheduler");
                    t.setDaemon(true);
                    t.setContextClassLoader(
                        TimeoutScheduler.class.getClassLoader());
                    return t;
                })));
        /* A cancelled task leaves the queue at once, not at its due time. */
        timer.setRemoveOnCancelPolicy(true);
        timer.prestartAllCoreThreads();
        return timer;
    }

    /**
     * Runs the task after the delay, unless the returned future is cancelled
     * first.  The task must not block.
     */
    public static ScheduledFuture<?> schedule(Runnable task, long delay,
                                              TimeUnit unit)
    {
        return TIMER.schedule(task, delay, unit);
    }

    /** Returns the number of tasks that are waiting, for tests. */
    public static int pending() {
        return TIMER.getQueue().size();
    }
}
