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
import java.util.concurrent.ThreadFactory;
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
 * <p>{@link Subject#current Subject.current()} is always {@code null} on a
 * worker thread, regardless of what subject was active on the submitting
 * thread.
 *
 * <p>This is achieved when the worker thread is <em>created</em>, not by
 * wrapping the task body.  In DirtyChai a thread captures
 * {@code Subject.current()} of the thread that constructs it, and
 * {@code Thread.runWith} then runs the whole thread body inside that
 * subject's scope.  A virtual-thread-per-task executor constructs the thread
 * inside {@code submit()}, on the <em>submitting</em> thread, so a plain
 * executor would hand every worker the submitter's subject for the lifetime
 * of the task.  Creating the executor under {@code Subject.callAs(null, ...)}
 * does not help, as that only affects threads created while the executor is
 * being constructed.  Instead the executor uses a {@link ThreadFactory} that
 * builds each thread inside {@code Subject.callAs(null, ...)}, so the thread
 * captures {@code null} and runs its body with no subject scope at all.
 *
 * <p>The task body is deliberately <em>not</em> wrapped in
 * {@code Subject.callAs(null, task)}.  Tasks submitted here include
 * accept loops and other long-running blocking loops; a {@code callAs}
 * scope must be bounded and must never enclose a blocking wait (see
 * the DirtyChai notes on {@code Thread.runWith}), and the wrapper would in
 * any case leave the thread's own captured subject scope in place around it.
 *
 * <p>This is intentional and provides defence in depth:
 * <ul>
 *   <li>Infrastructure tasks (TLS accept loops, connection reapers, session
 *       shutdown, SPIFFE context rebuilds) must never run under a user or
 *       service subject.  Running with a subject could narrow
 *       {@code ProtectionDomain} permission checks via ACC intersection and
 *       create confused-deputy vulnerabilities.</li>
 *   <li>In DirtyChai deployments, {@code AccessController.getContext()}
 *       injects active scoped subjects into the captured ACC, so a thread
 *       created from within a {@code Subject.callAs(...)} scope would
 *       otherwise inherit that subject.</li>
 *   <li>SPIFFE workload identity is always available process-wide (resolved
 *       from the ambient process subject, not the per-thread ScopedValue), so
 *       clearing the ScopedValue subject does not affect SPIFFE identity
 *       resolution.</li>
 * </ul>
 *
 * <p>A task that needs a subject must establish it itself, for example
 * from a subject captured on the submitting thread (as {@code MuxServer}
 * does for request dispatch).
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
        return Executors.newThreadPerTaskExecutor(new SubjectFreeThreadFactory());
    }

    /**
     * Creates virtual threads that capture no {@link Subject}.
     *
     * <p>{@link ThreadFactory#newThread} is invoked by the executor on the
     * submitting thread, so the thread is built inside
     * {@code Subject.callAs(null, ...)} to stop it capturing the submitter's
     * subject.  Only the (bounded) construction is inside the scope; the
     * thread body runs outside it.
     */
    private static final class SubjectFreeThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(final Runnable r) {
            return AccessController.doPrivileged((PrivilegedAction<Thread>) () ->
                Subject.callAs(null, (Callable<Thread>) () ->
                    Thread.ofVirtual().unstarted(r)));
        }
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
     * implementation returns a {@link Task} that names the thread and runs
     * the delegate; the absence of a subject is provided by the executor's
     * thread factory.  Subclasses override this to return an enriched task
     * that captures and restores additional context (e.g. the submitting
     * thread's {@code Subject}).
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
     * Base task: sets the thread name, runs the delegate, and resets the name
     * on completion.
     *
     * <p>It does not touch the {@link Subject}: the worker thread was created
     * subject-free by {@link SubjectFreeThreadFactory}, and wrapping the
     * delegate in {@code Subject.callAs} here would put a scope around
     * potentially unbounded blocking loops.
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
