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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 *   <li>The <em>user {@link Subject}(s)</em> bound via
 *       {@link Subject#callAs Subject.callAs()} and readable via
 *       {@link Subject#current Subject.current()}.  On a DirtyChai JDK the
 *       full stack of subjects (outermost-first) is captured via
 *       {@code Subject.currentAll()}, enabling multi-principal contexts such as
 *       distributed transactions where several parties are simultaneously active.
 *       On a standard JDK only the single subject returned by
 *       {@code Subject.current()} is captured.</li>
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
 *   <li>If one or more user {@link Subject}s were captured:
 *     <ul>
 *       <li>On a <b>DirtyChai</b> JDK (where {@code Subject.callAs(Callable,
 *           Subject...)} is available), all subjects are established in a
 *           single varargs call so that {@code Subject.currentAll()} on the
 *           worker thread returns the same set that was active at
 *           submission time.</li>
 *       <li>On a <b>standard JDK</b>, only the first (outermost) subject is
 *           restored via the standard {@link Subject#callAs
 *           Subject.callAs(Subject, Callable)} API.</li>
 *     </ul>
 *   </li>
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
 * executor with {@code SubjectAwareExecutor} prevents that loss, and on a
 * DirtyChai JDK also preserves multi-principal contexts (e.g. a distributed
 * transaction where several {@code Subject}s are simultaneously active via
 * {@code Subject.callAs(Callable, Subject...)}).
 *
 * @see Security#getContext()
 * @see Subject#callAs(Subject, java.util.concurrent.Callable)
 * @since 3.1.0
 */
public final class SubjectAwareExecutor implements ExecutorService {

    /**
     * DirtyChai JDK extension: {@code Subject.currentAll()} returns all
     * user Subjects bound to the current thread via {@code Subject.callAs},
     * outermost-first.  {@code null} on a standard JDK that only provides
     * {@code Subject.current()}.  Cached once at class-load time.
     */
    private static final Method CURRENT_ALL_METHOD;

    /**
     * DirtyChai JDK extension: {@code Subject.callAs(Callable, Subject...)}
     * varargs method that establishes multiple Subjects at once.  {@code null}
     * on a standard JDK.  Cached once at class-load time.
     */
    private static final Method CALL_AS_MULTI_SUBJECT;

    static {
        Method currentAll = null;
        Method callAsMulti = null;
        try {
            currentAll = Subject.class.getMethod("currentAll");
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Standard JDK — Subject.currentAll() not available
        }
        try {
            callAsMulti = Subject.class.getMethod("callAs", Callable.class, Subject[].class);
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Standard JDK — varargs Subject.callAs(Callable, Subject...) not available
        }
        CURRENT_ALL_METHOD = currentAll;
        CALL_AS_MULTI_SUBJECT = callAsMulti;
    }

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
     * Returns all user Subjects bound to the current thread, outermost-first.
     *
     * <p>On a DirtyChai JDK, {@link #CURRENT_ALL_METHOD} ({@code Subject.currentAll()})
     * is invoked to obtain the full {@code Subject[]} array.  On a standard
     * JDK that only exposes {@code Subject.current()}, a single-element array
     * is returned.  An empty array is returned when no user Subject is present.
     */
    @SuppressWarnings("unchecked")
    private static Subject[] captureUserSubjects() {
        if (CURRENT_ALL_METHOD != null) {
            try {
                Subject[] arr = (Subject[]) CURRENT_ALL_METHOD.invoke(null);
                if (arr != null && arr.length > 0) return arr;
            } catch (Exception ignored) {
                // Reflective invocation failure — fall through to Subject.current()
            }
        }
        Subject single = Subject.current();
        return single != null ? new Subject[]{single} : new Subject[0];
    }

    /**
     * Wraps {@code task} with the current thread's Subject(s) + SecurityContext.
     */
    private Runnable wrap(Runnable task) {
        if (task == null) throw new NullPointerException("task must not be null");
        final Subject[] subjects = captureUserSubjects();
        final SecurityContext ctx = Security.getContext();
        return () -> runWithContext(subjects, ctx, task);
    }

    /**
     * Wraps {@code task} with the current thread's Subject(s) + SecurityContext.
     */
    private <V> Callable<V> wrap(Callable<V> task) {
        if (task == null) throw new NullPointerException("task must not be null");
        final Subject[] subjects = captureUserSubjects();
        final SecurityContext ctx = Security.getContext();
        return () -> callWithContext(subjects, ctx, task);
    }

    /**
     * Invokes {@code action} with all captured Subjects established.
     *
     * <p>On a DirtyChai JDK (where {@link #CALL_AS_MULTI_SUBJECT} is available)
     * and when more than one Subject was captured, a single varargs
     * {@code Subject.callAs(Callable, Subject...)} call establishes them all so
     * that {@code Subject.currentAll()} on the worker thread returns the full set.
     * For a single Subject, or on a standard JDK, the standard
     * {@link Subject#callAs(Subject, Callable)} API is used.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <V> V callAsSubjects(Subject[] subjects, Callable<V> action) throws Exception {
        if (CALL_AS_MULTI_SUBJECT != null && subjects.length > 1) {
            // DirtyChai path: single varargs call with all subjects so that
            // Subject.currentAll() on the worker thread returns the full set.
            try {
                return (V) CALL_AS_MULTI_SUBJECT.invoke(null, action, (Object) subjects);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error)     throw (Error)     cause;
                throw ite;
            } catch (IllegalAccessException iae) {
                throw new IllegalStateException(
                    "Unexpected access denial invoking Subject.callAs", iae);
            }
        }
        // Standard JDK path or single Subject: use first subject only.
        return Subject.callAs(subjects[0], action);
    }

    /**
     * Executes {@code task} inside the captured {@link SecurityContext} and,
     * if user {@link Subject}s were captured, also inside
     * {@link Subject#callAs Subject.callAs()}.
     */
    private static void runWithContext(Subject[] subjects,
                                       SecurityContext ctx,
                                       Runnable task) {
        AccessController.doPrivileged(
                ctx.wrap((PrivilegedAction<Void>) () -> {
                    if (subjects.length > 0) {
                        try {
                            callAsSubjects(subjects, () -> {
                                task.run();
                                return null;
                            });
                        } catch (RuntimeException re) {
                            throw re;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        task.run();
                    }
                    return null;
                }),
                ctx.getAccessControlContext());
    }

    /**
     * Calls {@code task} inside the captured {@link SecurityContext} and,
     * if user {@link Subject}s were captured, also inside
     * {@link Subject#callAs Subject.callAs()}.
     *
     * @throws Exception whatever {@code task.call()} throws
     */
    private static <V> V callWithContext(Subject[] subjects,
                                          SecurityContext ctx,
                                          Callable<V> task) throws Exception {
        try {
            return AccessController.doPrivileged(
                    ctx.wrap((PrivilegedExceptionAction<V>) () -> {
                        if (subjects.length > 0) {
                            return callAsSubjects(subjects, task);
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
