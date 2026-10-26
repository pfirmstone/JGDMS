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

package au.net.zeus.jgdms.api.security;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;

/**
 * Runs an action with <em>no</em> user {@link Subject} bound to the current
 * thread, so that {@link Subject#current() Subject.current()} returns
 * {@code null} for the duration of the action.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Infrastructure work (accept loops, connection reapers, lease and event
 * expiry sweeps, session shutdown) must not execute under whatever user or
 * service identity happened to be current on the thread that scheduled it.
 * Executing under an inherited identity narrows
 * {@link java.security.ProtectionDomain} permission evaluation via principal
 * scoped grants and creates confused-deputy exposure: the infrastructure task
 * becomes indistinguishable, to the authorization layer, from work the user
 * asked for.
 *
 * <p>On a DirtyChai JDK, a virtual thread is constructed per task at
 * {@code submit()} on the submitting thread and captures that thread's
 * ambient {@code Subject}; pool workers would otherwise inherit the
 * submitter's identity. This class removes the identity at the innermost
 * point: it binds an empty subject scope immediately around the action,
 * shadowing anything established further out.
 *
 * <h2>Mechanism</h2>
 *
 * <p>{@link Subject#callAs(Subject, Callable) Subject.callAs((Subject) null,
 * action)}, standard API since Java 18 and therefore present on every JDK
 * this module supports. There is deliberately no tier that runs the action
 * with the caller's identity still bound: a degradation that silently
 * restores the vulnerability this class exists to close is worse than a loud
 * failure.
 *
 * <h2>Cost</h2>
 *
 * <p>{@code Subject.callAs} performs a {@code SecurityManager} check for
 * {@link javax.security.auth.AuthPermission} {@code "callAs"} before binding,
 * on every call. Callers on a hot path (for example per-task dispatch in a
 * thread pool) therefore add one permission evaluation per action, and the code
 * source of the calling module must be granted
 * {@code javax.security.auth.AuthPermission "callAs"} or every call will fail.
 * That grant is <em>not</em> a privilege escalation: {@code callAs} with a
 * {@code null} subject can only remove identity, never add it.
 *
 * <h2>Diagnostics</h2>
 *
 * <p>Logs to the {@link Logger} named
 * {@code au.net.zeus.jgdms.api.security.SubjectIsolation}, at
 * {@link Level#WARNING WARNING}, when {@code AuthPermission "callAs"} is
 * denied -- a fault worth investigating, never silently degraded past.
 *
 * @since 4.0.0
 */
public final class SubjectIsolation {

    private static final Logger logger =
        Logger.getLogger("au.net.zeus.jgdms.api.security.SubjectIsolation");

    private SubjectIsolation() {
        throw new AssertionError("no instances");
    }

    /**
     * Calls {@code action} with no {@code Subject} bound to the current thread.
     *
     * @param <T> the result type of {@code action}
     * @param action the action to call; must not be {@code null}
     * @return whatever {@code action} returns
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws SecurityException if a {@code SecurityManager} is installed and
     *         the calling code source lacks
     *         {@code AuthPermission "callAs"}. The action is <em>not</em> run:
     *         running it with the caller's identity still bound is the exact
     *         condition this method exists to prevent.
     * @throws Exception whatever {@code action} throws, unwrapped from the
     *         {@link CompletionException} that {@code Subject.callAs} wraps it in
     */
    public static <T> T callWithoutSubject(Callable<T> action) throws Exception {
        if (action == null) throw new NullPointerException("action must not be null");
        try {
            return Subject.callAs((Subject) null, action);
        } catch (CompletionException wrapped) {
            throw rethrow(wrapped.getCause());
        } catch (SecurityException denied) {
            // Thrown by callAs itself, before the action runs, so it arrives
            // unwrapped here. Route it through the same classifier so the
            // missing grant is reported rather than read as a task failure.
            throw rethrow(denied);
        }
    }

    /**
     * Runs {@code action} with no {@code Subject} bound to the current thread.
     *
     * <p>Convenience form of {@link #callWithoutSubject(Callable)} for a
     * {@link Runnable}. Unchecked exceptions and {@link Error}s thrown by
     * {@code action} propagate unchanged; there are no others to consider.
     *
     * @param action the action to run; must not be {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws SecurityException if a {@code SecurityManager} is installed and
     *         the calling code source lacks {@code AuthPermission "callAs"}
     */
    public static void runWithoutSubject(final Runnable action) {
        if (action == null) throw new NullPointerException("action must not be null");
        try {
            callWithoutSubject(() -> {
                action.run();
                return null;
            });
        } catch (RuntimeException | Error propagate) {
            throw propagate;
        } catch (Exception impossible) {
            // A Runnable declares no checked exception, so nothing can reach
            // here; treat arrival as a defect rather than discarding it.
            throw new IllegalStateException(
                "checked exception from a Runnable", impossible);
        }
    }

    /**
     * Unwraps and rethrows a cause with its original type where possible.
     * {@code Subject.callAs} wraps whatever the action threw in a
     * {@link CompletionException}, so a cause may itself be one.
     */
    private static Exception rethrow(Throwable cause) throws Exception {
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SecurityException) {
            // Distinct from an ordinary task failure: the deployment is missing
            // a grant, and until it is added identity cannot be cleared.
            logger.log(Level.WARNING,
                "AuthPermission \"callAs\" denied to this code source; the"
                + " ambient Subject cannot be cleared, so the action was"
                + " refused rather than run under the caller's identity."
                + " Grant javax.security.auth.AuthPermission \"callAs\" to"
                + " the calling code source.", cause);
            throw (SecurityException) cause;
        }
        if (cause instanceof Error) throw (Error) cause;
        if (cause instanceof Exception) throw (Exception) cause;
        throw new IllegalStateException("unexpected throwable", cause);
    }
}
