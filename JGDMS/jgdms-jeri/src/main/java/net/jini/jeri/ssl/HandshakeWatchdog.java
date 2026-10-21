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

package net.jini.jeri.ssl;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;

/**
 * The one timer that ends server handshakes which have run out of time.
 *
 * <p>Every accepted connection has a task here from the moment it is
 * accepted; the task closes the raw socket, which ends whatever read or write
 * the handshake is blocked in.  One thread serves all the listeners of the
 * JVM, so a flood of connections costs queue entries and not threads.
 *
 * <p>The thread is a platform daemon thread, so that it is not held up when
 * the carrier threads of the virtual threads are all busy with handshakes.
 * It is created when this class is initialised, in a privileged action and
 * under {@code Subject.callAs(null, ...)}: a thread that is created lazily
 * from a request thread keeps that thread's subject and context class loader
 * for the life of the JVM.
 */
final class HandshakeWatchdog {

    private static final ScheduledThreadPoolExecutor TIMER = create();

    private HandshakeWatchdog() { }

    private static ScheduledThreadPoolExecutor create() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(
            1, task -> AccessController.doPrivileged(
                (PrivilegedAction<Thread>) () -> Subject.callAs(null, () -> {
                    Thread t = new Thread(task, "TLS handshake watchdog");
                    t.setDaemon(true);
                    t.setContextClassLoader(
                        HandshakeWatchdog.class.getClassLoader());
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
    static ScheduledFuture<?> schedule(Runnable task, long delayNanos) {
        return TIMER.schedule(task, delayNanos, TimeUnit.NANOSECONDS);
    }

    /** Returns the number of tasks that are waiting, for tests. */
    static int pending() {
        return TIMER.getQueue().size();
    }
}
