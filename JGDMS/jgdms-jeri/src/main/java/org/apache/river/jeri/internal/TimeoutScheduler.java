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
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;

/**
 * The one timer that ends connections which have run out of time to start,
 * or to be acknowledged: a server's TLS handshake that has not finished, a
 * mux connection whose peer has not sent its header, a mux request whose
 * acknowledgment has not come.
 *
 * <p>One thread serves all the endpoints of the JVM, so a flood of
 * connections costs queue entries and not threads, and so one task that
 * waits delays every deadline.  A task must therefore only flip some state
 * and, if there is anything to close or to notify, call {@link #handOff} to
 * have another thread do it: closing a socket can block (a socket with a
 * linger time), and so can taking the lock of a connection.
 *
 * <p>The class is public only because the callers are in three packages.
 * Its methods run on behalf of the callers' classes only: a call from any
 * other class, loaded by any loader, fails with a {@link SecurityException},
 * whether or not a security manager is installed, so downloaded or foreign
 * code cannot use the timer to run its work on, or to delay, the threads that
 * enforce the deadlines of the JVM's connections.
 *
 * <p>The threads are created when this class is initialised, in a privileged
 * action, under {@code Subject.callAs(null, ...)} and without the inheritable
 * thread locals of the thread that happens to initialise it: a thread that is
 * created lazily from a request thread keeps that thread's subject, context
 * class loader and thread locals for the life of the JVM.
 */
public final class TimeoutScheduler {

    /**
     * The classes that may use the timer: the mux, which times the start of
     * a server connection and the acknowledgment of a request, and the TLS
     * server's watchdog.  They are named, not referred to, because two are
     * not visible from here; a class of that name is the same class only if
     * this loader defined it.
     */
    private static final Set<String> CALLERS = Set.of(
        "org.apache.river.jeri.internal.mux.Mux",
        "org.apache.river.jeri.internal.mux.Session",
        "net.jini.jeri.ssl.HandshakeWatchdog");

    private static final StackWalker CALLER = AccessController.doPrivileged(
        (PrivilegedAction<StackWalker>) () ->
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE));

    private static final ScheduledThreadPoolExecutor TIMER = create();

    private static final Executor HAND_OFF = Executors.newThreadPerTaskExecutor(
        new ThreadFactory() {
            public Thread newThread(Runnable task) {
                return AccessController.doPrivileged(
                    (PrivilegedAction<Thread>) () -> Subject.callAs(null, () -> {
                        Thread t = Thread.ofVirtual()
                            .name("connection timeout task")
                            .inheritInheritableThreadLocals(false)
                            .unstarted(task);
                        t.setContextClassLoader(
                            TimeoutScheduler.class.getClassLoader());
                        return t;
                    }));
            }
        });

    private TimeoutScheduler() { }

    private static ScheduledThreadPoolExecutor create() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(
            1, task -> AccessController.doPrivileged(
                (PrivilegedAction<Thread>) () -> Subject.callAs(null, () -> {
                    /*
                     * A platform thread, so that it is not held up when the
                     * carrier threads of the virtual threads are all busy.
                     */
                    Thread t = Thread.ofPlatform()
                        .name("connection timeout scheduler")
                        .daemon(true)
                        .inheritInheritableThreadLocals(false)
                        .unstarted(task);
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
     * Throws a SecurityException unless the class is one of the callers.
     */
    private static void check(Class<?> caller) {
        if (caller.getClassLoader() != TimeoutScheduler.class.getClassLoader()
            || !CALLERS.contains(caller.getName()))
        {
            throw new SecurityException(
                caller.getName() + " may not use the connection timeout "
                + "scheduler");
        }
    }

    /**
     * Runs the task after the delay, unless the returned future is cancelled
     * first.  The task must not block: it sets some state, and calls {@link
     * #handOff} for anything else.
     *
     * @throws SecurityException if the caller is not one of the classes that
     *         may use the scheduler
     */
    public static ScheduledFuture<?> schedule(Runnable task, long delay,
                                              TimeUnit unit)
    {
        check(CALLER.getCallerClass());
        return TIMER.schedule(task, delay, unit);
    }

    /**
     * Runs the task on a thread of its own, which is allowed to block, so
     * that the timer thread is not held up.  If no thread can be started the
     * attempt is repeated a second later, never made on the timer thread.
     *
     * @throws SecurityException if the caller is not one of the classes that
     *         may use the scheduler
     */
    public static void handOff(Runnable task) {
        check(CALLER.getCallerClass());
        run(task);
    }

    private static void run(Runnable task) {
        try {
            HAND_OFF.execute(task);
        } catch (Throwable t) {
            // out of threads or memory: try again, off this thread
            try {
                TIMER.schedule(() -> run(task), 1L, TimeUnit.SECONDS);
            } catch (Throwable ignore) {
                // shut down or out of memory: nothing more can be done
            }
        }
    }

    /** Returns the number of tasks that are waiting, for tests. */
    public static int pending() {
        return TIMER.getQueue().size();
    }
}
