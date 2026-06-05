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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.security.auth.Subject;

/**
 * A thread pool for short-lived tasks that propagates the submitting thread's
 * {@link Subject} identity to the worker virtual thread.
 *
 * <p>This pool extends {@link ThreadPool} and overrides {@link #wrap} to
 * capture the submitting thread's subject stack at submission time and restore
 * it on the worker thread via {@link Subject#callAs Subject.callAs()}.  This
 * ensures that {@link Subject#current Subject.current()} returns the correct
 * identity on the worker thread even though
 * {@link java.lang.ScopedValue ScopedValue} bindings are not automatically
 * inherited by executor-submitted tasks.
 *
 * <p>Unlike the base {@link ThreadPool}, which explicitly clears any ambient
 * subject via {@code Subject.callAs(null, ...)}, this pool restores the
 * captured subject stack so that service method invocations on the worker
 * thread see the same identity as was active on the submitting thread.
 * {@code SubjectPropagatingTask} bypasses {@code Task.run()} entirely to
 * prevent the base null-clearing from undoing the subject restoration.
 *
 * <p>If no subject is active on the submitting thread, this pool falls back
 * to the base {@link ThreadPool} behaviour — the task runs with an explicitly
 * cleared (null) subject, consistent with the infrastructure task contract.
 *
 * <h2>When to use this pool</h2>
 *
 * <p>Use this pool for short-lived tasks where the submitting thread's
 * identity is meaningful for the duration of the task — for example:
 * <ul>
 *   <li>RPC dispatch threads that need to propagate the caller's JWT user
 *       subject into service method invocations.</li>
 *   <li>Background tasks submitted from within a
 *       {@code Subject.callAs(userSubject, ...)} scope that must perform
 *       authenticated outbound calls on behalf of that user.</li>
 * </ul>
 *
 * <h2>When not to use this pool</h2>
 *
 * <p>Do <em>not</em> use this pool for long-running infrastructure tasks such
 * as TLS accept loops or scheduled background maintenance threads.  Such tasks
 * outlive any meaningful subject scope, and in SPIFFE deployments the workload
 * identity rotates hourly — a subject captured at submission time would become
 * stale after the first rotation, silently breaking TLS.  Use the base
 * {@link ThreadPool} for those tasks instead.
 *
 * <h2>Subject capture model</h2>
 *
 * <p>On a DirtyChai JDK, the full subject stack is captured via
 * {@code Subject.currentAll()}, preserving multi-principal contexts such as
 * distributed transactions where several {@code Subject}s are simultaneously
 * active.  On a standard JDK only the single subject returned by
 * {@code Subject.current()} is captured.
 *
 * <h2>SPIFFE workload identity</h2>
 *
 * <p>SPIFFE subjects are <em>not</em> propagated by this pool.  They are
 * managed process-wide via {@code SpiffeSubjectHolder} and are always
 * re-fetched dynamically in {@code resolveSubjectIfNeeded}.  Capturing a
 * SPIFFE subject at submission time would defeat the hourly rotation
 * mechanism.
 *
 * <p>This implementation inherits the {@link java.util.logging.Logger} named
 * {@code org.apache.river.thread.ThreadPool} from the base class.
 *
 * @author Sun Microsystems, Inc.
 * @see ThreadPool
 **/
final class SubjectPropagatingThreadPool extends ThreadPool {

    /**
     * DirtyChai JDK extension: {@code Subject.currentAll()} returns all user
     * Subjects bound to the current thread via {@code Subject.callAs},
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
            callAsMulti = Subject.class.getMethod(
                    "callAs", Callable.class, Subject[].class);
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Standard JDK — varargs Subject.callAs(Callable, Subject...)
            // not available
        }
        CURRENT_ALL_METHOD = currentAll;
        CALL_AS_MULTI_SUBJECT = callAsMulti;
    }

    SubjectPropagatingThreadPool() {
        super();
    }

    /**
     * Captures all user Subjects bound to the current thread at submission
     * time, outermost-first.
     *
     * <p>On a DirtyChai JDK, {@code Subject.currentAll()} is used to obtain
     * the full subject stack.  On a standard JDK, only the single subject
     * returned by {@code Subject.current()} is captured.  Returns an empty
     * array when no user Subject is present on the submitting thread.
     *
     * <p>SPIFFE {@code WorkerSubject} instances are not capturable via
     * {@code Subject.current()} or {@code Subject.currentAll()} — SPIFFE
     * identity is carried in {@code ProtectionDomain} principal stamping and
     * is always re-fetched process-wide via {@code SpiffeSubjectHolder}.
     */
    @SuppressWarnings("unchecked")
    private static Subject[] captureSubjects() {
        if (CURRENT_ALL_METHOD != null) {
            try {
                Subject[] arr = (Subject[]) CURRENT_ALL_METHOD.invoke(null);
                if (arr != null && arr.length > 0) return arr;
            } catch (Exception ignored) {
                // Reflective invocation failure — fall through to
                // Subject.current()
            }
        }
        Subject single = Subject.current();
        return single != null ? new Subject[]{ single } : new Subject[0];
    }

    /**
     * Invokes {@code action} with {@code subjects} established as the current
     * subject(s) on the calling thread.
     *
     * <p>On a DirtyChai JDK with more than one subject, a single varargs
     * {@code Subject.callAs(Callable, Subject...)} call establishes the full
     * stack so that {@code Subject.currentAll()} on the worker thread returns
     * the same set that was active at submission time.  For a single subject,
     * or on a standard JDK, the standard
     * {@link Subject#callAs(Subject, Callable)} API is used.
     *
     * <p>Must only be called when {@code subjects.length > 0}.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void runAsSubjects(Subject[] subjects, Runnable action) {
        try {
            if (CALL_AS_MULTI_SUBJECT != null && subjects.length > 1) {
                // DirtyChai path: restore the full subject stack in one call
                // so that Subject.currentAll() on the worker thread returns
                // the same set that was active at submission time.
                Callable<Void> c = () -> { action.run(); return null; };
                try {
                    CALL_AS_MULTI_SUBJECT.invoke(null, c, (Object) subjects);
                } catch (InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    if (cause instanceof RuntimeException)
                        throw (RuntimeException) cause;
                    if (cause instanceof Error)
                        throw (Error) cause;
                    throw new RuntimeException(cause);
                } catch (IllegalAccessException iae) {
                    throw new IllegalStateException(
                        "Unexpected access denial invoking Subject.callAs",
                        iae);
                }
            } else {
                // Standard JDK path or single subject.
                Subject.callAs(subjects[0], () -> {
                    action.run();
                    return null;
                });
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a task that captures the current thread's subject stack at
     * call time and restores it on the worker thread at execution time.
     *
     * <p>If no subject is active on the submitting thread, falls back to the
     * base {@link ThreadPool#wrap} which produces a {@link Task} that
     * explicitly clears any ambient subject — consistent with the
     * infrastructure task contract.
     */
    @Override
    Runnable wrap(Runnable runnable, String name) {
        Subject[] subjects = captureSubjects();
        if (subjects.length == 0) {
            // No subject active on submitting thread — use the base Task
            // which explicitly clears any ambient subject on the worker thread.
            return super.wrap(runnable, name);
        }
        return new SubjectPropagatingTask(runnable, name, subjects);
    }

    /**
     * Task that restores a captured subject stack and runs the delegate
     * directly — bypassing {@link Task#run()} to prevent the base class's
     * {@code Subject.callAs(null, ...)} null-clearing from undoing the
     * subject restoration.
     *
     * <p>Thread naming and exception handling are reproduced here rather
     * than delegating to the base, for the same reason.
     */
    private static final class SubjectPropagatingTask implements Runnable {

        private final Runnable runnable;
        private final String name;
        private final Subject[] subjects; // captured at submission time

        SubjectPropagatingTask(Runnable runnable, String name,
                               Subject[] subjects) {
            this.runnable = runnable;
            this.name = name;
            this.subjects = subjects;
        }

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            // Restore the captured subject stack and run the delegate.
            // Thread naming and exception handling are inline rather than
            // delegated to Task.run() because Task.run() wraps in
            // Subject.callAs(null, ...) which would clear the subject we
            // are about to restore.
            runAsSubjects(subjects, () -> {
                try {
                    thread.setName(NewThreadAction.NAME_PREFIX + name);
                    runnable.run();
                } catch (RuntimeException t) { // Don't catch Error
                    logger.log(Level.WARNING, "uncaught exception", t);
                    if (t instanceof SecurityException) {
                        // ignore — already logged
                    } else {
                        // Ignorance of RuntimeException is generally bad,
                        // bail out.
                        throw t;
                    }
                } finally {
                    thread.setName(NewThreadAction.NAME_PREFIX + "idle");
                }
            });
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
