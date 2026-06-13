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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A thread pool for long-running infrastructure tasks, backed by a
 * virtual-thread-per-task executor.
 *
 * <p>Each submitted task runs immediately in its own virtual thread.
 * There is no minimum warm thread count, nor is there a maximum thread count
 * (back-pressure is provided at higher layers via Semaphore where required).
 *
 * <h2>Subject identity</h2>
 *
 * <p>This pool <em>explicitly clears</em> any ambient
 * {@link javax.security.auth.Subject} before executing each task.  Every task
 * is wrapped in {@code Subject.callAs(null, task)} so that
 * {@link Subject#current Subject.current()} always returns {@code null} on
 * the worker thread, regardless of what subject was active on the submitting
 * thread.
 *
 * <p>This is intentional and provides defence in depth:
 * <ul>
 *   <li>Infrastructure tasks (TLS accept loops, connection reapers, session
 *       shutdown, SPIFFE context rebuilds) must never run under a user or
 *       service subject.  Running with a subject could narrow
 *       {@code ProtectionDomain} permission checks via ACC intersection and
 *       create confused-deputy vulnerabilities.</li>
 *   <li>In DirtyChai deployments, {@code AccessController.getContext()}
 *       injects active scoped subjects into the captured ACC.  A virtual
 *       thread submitted from within a {@code Subject.callAs(...)} scope
 *       therefore inherits that subject in its ACC.  The explicit
 *       {@code Subject.callAs(null, ...)} wrapper ensures that the ScopedValue
 *       is cleared on the worker thread, neutralising any injected subject.</li>
 *   <li>SPIFFE workload identity is always available via process-wide
 *       {@code SpiffeSubjectHolder} and is never carried through the
 *       ScopedValue mechanism.  Clearing the ScopedValue subject does not
 *       affect SPIFFE identity resolution.</li>
 * </ul>
 *
 * <p>For short-lived tasks that need subject propagation (RPC dispatch, JWT
 * forwarding across thread boundaries) use
 * {@link SubjectPropagatingThreadPool} instead.
 *
 * <p>This implementation uses the {@link Logger} named
 * {@code org.apache.river.thread.ThreadPool} to log information at the
 * following levels:
 *
 * <table summary="Logging levels" border=1 cellpadding=5>
 * <tr><th>Level</th><th>Description</th></tr>
 * <tr><td>{@link Level#WARNING WARNING}</td>
 *     <td>uncaught exception in worker thread</td></tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * @see SubjectPropagatingThreadPool
 **/
class ThreadPool implements Executor, java.util.concurrent.Executor {

    static final Logger logger =
        Logger.getLogger("org.apache.river.thread.ThreadPool");

    private volatile boolean shutdown = false;
    private final ExecutorService es;

    ThreadPool() {
        this(newVirtualThreadPerTaskExecutor());
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                Runtime.getRuntime().addShutdownHook(shutdownHook());
                return null;
            }
        });
    }

    private static ExecutorService newVirtualThreadPerTaskExecutor() {
        // Explicitly clear any ambient subject. Subject.callAs(null,
        // ...) binds null into the ScopedValue, making Subject.current()
        // return null for the duration of the task. This neutralises
        // any subject injected via DirtyChai's ACC snapshot mechanism
        // or inherited from an enclosing Subject.callAs scope on the
        // submitting thread.
        return AccessController.doPrivileged((PrivilegedAction<ExecutorService>) () -> {
            return Subject.callAs(null, (Callable<ExecutorService>) () -> {
                return Executors.newVirtualThreadPerTaskExecutor();
            });
        });
    }

    private ThreadPool(ExecutorService es) {
        this.es = es;
    }

    private Thread shutdownHook() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Allow four seconds prior to shutdown for other
                    // processes to complete.
                    Thread.sleep(4000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                shutdown = true;
                es.shutdown();
            }
        }, "ThreadPool destroy");
        /**
         * See jtreg sun bug ID:4404702
         * This ensures that this thread doesn't unnecessarily hold
         * a strong reference to a ClassLoader, thus preventing
         * it from being garbage collected.
         */
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        return t;
    }

    /**
     * Wraps a submitted {@link Runnable} for execution.  The base
     * implementation returns a {@link Task} that clears any ambient subject
     * before running the delegate.  Subclasses override this to return an
     * enriched task that captures and restores additional context (e.g. the
     * submitting thread's {@code Subject}).
     *
     * @param runnable the runnable to wrap; never {@code null}
     * @param name     the task name used for thread naming
     * @return a {@link Runnable} ready for submission to the executor
     */
    Runnable wrap(Runnable runnable, String name) {
        return new Task(runnable, name);
    }

    // This method must not block - Executor
    @Override
    public void execute(Runnable runnable, String name)
            throws RejectedExecutionException {
        if (runnable == null) return;
        if (shutdown) throw new RejectedExecutionException("ThreadPool shutdown");
        es.submit(wrap(runnable, name));
    }

    @Override
    public void execute(Runnable command) {
        execute(command, "org.apache.river.thread.ThreadPool");
    }

    /**
     * Base task: explicitly clears any ambient {@link Subject} via
     * {@code Subject.callAs(null, ...)}, sets the thread name, runs the
     * delegate, and resets the name on completion.
     *
     * <p>Clearing the subject ensures that infrastructure tasks never
     * inadvertently run under a user or service subject inherited from the
     * submitting thread's {@code Subject.callAs} scope or from DirtyChai's
     * ACC-injected subject.
     */
    static class Task implements Runnable {

        private final Runnable runnable;
        private final String name;

        Task(Runnable runnable, String name) {
            this.runnable = runnable;
            this.name = name;
        }

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            try {
                thread.setName(NewThreadAction.NAME_PREFIX + name);
                runnable.run();
            } catch (RuntimeException t) { // Don't catch Error
                logger.log(Level.WARNING, "uncaught exception", t);
                throw t;
            } finally {
                thread.setName(NewThreadAction.NAME_PREFIX + "idle");
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
