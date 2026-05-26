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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.security.auth.Subject;

/**
 * An {@link ExecutorService} decorator that captures the submitting thread's
 * {@link Subject} identity and {@link SecurityContext} at task-submission time,
 * and restores them on the worker thread when the task is executed.
 *
 * <h2>Identity propagation model</h2>
 *
 * <p>When a task is submitted via any of the {@code execute}, {@code submit},
 * {@code invokeAll}, or {@code invokeAny} methods, this class snapshots two
 * pieces of the calling thread's identity:
 *
 * <ol>
 *   <li>The <em>user {@link Subject}</em> bound via
 *       {@link Subject#callAs Subject.callAs()} and readable as
 *       {@link Subject#current Subject.current()}.  This is the human-user
 *       identity propagated by the application layer.</li>
 *   <li>The {@link SecurityContext} returned by {@link Security#getContext()},
 *       which encapsulates the full {@link java.security.AccessControlContext}
 *       including any SPIFFE workload-identity {@link java.security.ProtectionDomain}s
 *       injected by DirtyChai.</li>
 * </ol>
 *
 * <p>On the worker thread, the task is executed as follows:
 * <ol>
 *   <li>The captured {@link SecurityContext} is restored via
 *       {@link AccessController#doPrivileged(PrivilegedAction,
 *       java.security.AccessControlContext) AccessController.doPrivileged}.</li>
 *   <li>If a user {@link Subject} was captured, the task additionally runs
 *       inside {@link Subject#callAs Subject.callAs(capturedSubject, task)}.</li>
 * </ol>
 *
 * <h2>SPIFFE workload identity</h2>
 *
 * <p>SPIFFE/SPIRE principals are propagated through the
 * {@link java.security.AccessControlContext} (injected by DirtyChai's
 * {@code SecureClassLoader} into {@link java.security.ProtectionDomain}s)
 * rather than via {@link Subject#callAs Subject.callAs()} or
 * {@link Subject#doAs Subject.doAs()}.  The captured {@link SecurityContext}
 * carries those principals automatically.  This class never calls
 * {@code Subject.callAs()} or {@code Subject.doAs()} with a
 * {@code SpiffeSubject} or {@code WorkerSubject}; the SPIFFE propagation path
 * relies entirely on the captured {@link SecurityContext}.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 *   // at service startup:
 *   ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
 *   ExecutorService safePool = new SubjectAwareExecutor(pool);
 *
 *   // inside a service method invoked as User "alice":
 *   safePool.submit(() -> {
 *       // Subject.current() is "alice" here, even on the worker thread
 *       doSensitiveWork();
 *   });
 * </pre>
 *
 * <p>Service code that submits tasks to a bare {@link ExecutorService} silently
 * loses the user identity that was active at submission time.  Wrapping the
 * executor with {@code SubjectAwareExecutor} prevents that loss.
 *
 * @see Security#getContext()
 * @see Subject#callAs(Subject, java.util.concurrent.Callable)
 * @since 3.1.0
 */
public final class SubjectAwareExecutor implements ExecutorService {

    private final ExecutorService delegate;

    /**
     * Creates a new {@code SubjectAwareExecutor} that delegates all execution
     * to {@code delegate} while propagating the submitting thread's identity.
     *
     * @param delegate the underlying executor; must not be {@code null}
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public SubjectAwareExecutor(ExecutorService delegate) {
        if (delegate == null) throw new NullPointerException("delegate must not be null");
        this.delegate = delegate;
    }

    // -----------------------------------------------------------------------
    // Wrapping helpers
    // -----------------------------------------------------------------------

    /**
     * Wraps {@code task} with the current thread's Subject + SecurityContext.
     */
    private Runnable wrap(Runnable task) {
        if (task == null) throw new NullPointerException("task must not be null");
        final Subject subject = Subject.current();
        final SecurityContext ctx = Security.getContext();
        return () -> runWithContext(subject, ctx, task);
    }

    /**
     * Wraps {@code task} with the current thread's Subject + SecurityContext.
     */
    private <V> Callable<V> wrap(Callable<V> task) {
        if (task == null) throw new NullPointerException("task must not be null");
        final Subject subject = Subject.current();
        final SecurityContext ctx = Security.getContext();
        return () -> callWithContext(subject, ctx, task);
    }

    /**
     * Executes {@code task} inside the captured {@link SecurityContext} and,
     * if a user {@link Subject} was captured, also inside
     * {@link Subject#callAs Subject.callAs()}.
     */
    private static void runWithContext(Subject subject,
                                       SecurityContext ctx,
                                       Runnable task) {
        AccessController.doPrivileged(
                ctx.wrap((PrivilegedAction<Void>) () -> {
                    if (subject != null) {
                        Subject.callAs(subject, () -> {
                            task.run();
                            return null;
                        });
                    } else {
                        task.run();
                    }
                    return null;
                }),
                ctx.getAccessControlContext());
    }

    /**
     * Calls {@code task} inside the captured {@link SecurityContext} and,
     * if a user {@link Subject} was captured, also inside
     * {@link Subject#callAs Subject.callAs()}.
     *
     * @throws Exception whatever {@code task.call()} throws
     */
    private static <V> V callWithContext(Subject subject,
                                          SecurityContext ctx,
                                          Callable<V> task) throws Exception {
        try {
            return AccessController.doPrivileged(
                    ctx.wrap((PrivilegedExceptionAction<V>) () -> {
                        if (subject != null) {
                            return Subject.callAs(subject, task);
                        }
                        return task.call();
                    }),
                    ctx.getAccessControlContext());
        } catch (PrivilegedActionException pae) {
            Exception cause = pae.getException();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw cause;
        }
    }

    // -----------------------------------------------------------------------
    // ExecutorService delegation
    // -----------------------------------------------------------------------

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                          long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                            long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    private <T> List<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        if (tasks == null) throw new NullPointerException("tasks must not be null");
        List<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> t : tasks) {
            wrapped.add(wrap(t));
        }
        return wrapped;
    }

    // -----------------------------------------------------------------------
    // Lifecycle delegation
    // -----------------------------------------------------------------------

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
