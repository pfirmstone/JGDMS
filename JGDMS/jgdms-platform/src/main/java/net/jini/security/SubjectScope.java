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

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import au.net.zeus.jgdms.api.policy.UserSubjectSupport;

/**
 * Exception-transparent helpers for binding {@link Subject}s with
 * {@link Subject#callAs Subject.callAs}, the replacement for the retired
 * {@code Subject.doAs} / {@code Subject.doAsPrivileged} forms.
 *
 * <p>{@code Subject.callAs} reports a failure of its action by throwing a
 * {@link java.util.concurrent.CompletionException} that wraps the exception
 * the action threw, where {@code Subject.doAs(Subject,
 * java.security.PrivilegedExceptionAction)} wrapped checked exceptions in a
 * {@link java.security.PrivilegedActionException} and let unchecked ones
 * through.  The methods here rethrow <em>exactly</em> the exception the action
 * threw, checked or unchecked, so a converted call site can catch the action's
 * own exception types directly.  An exception raised by the binding machinery
 * itself, rather than by the action, is rethrown unchanged.  Errors always
 * propagate unwrapped.
 *
 * <p>A {@code Subject} bound with {@code Subject.callAs} lives in a
 * {@code ScopedValue}; it is not inherited by a thread created inside the
 * scope.  Code that hands work to another thread and needs that work to run as
 * the same identity captures the identity with {@link #captureAll()} before
 * the hand-off and re-establishes it on the other thread with
 * {@link #callAsAll(Subject[], Callable)}.  {@link SubjectAwareExecutor} does
 * this for every task submitted to an executor.
 *
 * <p>This class lives in a module compiled for a current Java release so that
 * modules still compiled with {@code --release 8}, where
 * {@code Subject.callAs} and {@code Subject.current} are not part of the API,
 * can bind and capture subjects through it.
 *
 * @see SubjectAwareExecutor
 * @see UserSubjectSupport#callAsAll(Subject[], Callable)
 * @since 3.1.0
 */
public final class SubjectScope {

    /**
     * DirtyChai JDK extension: {@code Subject.currentAll()}, returning every
     * Subject bound to the current thread, outermost first; {@code null} on a
     * standard JDK that only provides {@code Subject.current()}.
     */
    private static final Method CURRENT_ALL_METHOD;

    static {
        Method currentAll = null;
        try {
            currentAll = Subject.class.getMethod("currentAll");
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Standard JDK: Subject.currentAll() is not available.
        }
        CURRENT_ALL_METHOD = currentAll;
    }

    private SubjectScope() {
        throw new AssertionError("no instances");
    }

    /**
     * Calls {@code action} with {@code subject} bound as the current subject,
     * via {@link Subject#callAs Subject.callAs}, and rethrows exactly the
     * exception {@code action} throws.
     *
     * @param <V> the result type of {@code action}
     * @param subject the subject to bind; may be {@code null}, in which case
     *        {@code action} runs with no subject bound
     * @param action the code to run; must not be {@code null}
     * @return the value returned by {@code action}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws Exception whatever {@code action} throws
     */
    public static <V> V callAs(Subject subject, Callable<V> action)
            throws Exception
    {
        final Tracked<V> tracked = new Tracked<V>(action);
        try {
            return Subject.callAs(subject, tracked);
        } catch (Exception e) {
            throw tracked.rethrow(e);
        }
    }

    /**
     * Calls {@code action} with all of {@code subjects} bound, via
     * {@link UserSubjectSupport#callAsAll(Subject[], Callable)}, and rethrows
     * exactly the exception {@code action} throws.  An empty array calls
     * {@code action} with the current binding unchanged.
     *
     * @param <V> the result type of {@code action}
     * @param subjects the subjects to bind, as returned by {@link #captureAll()};
     *        must not be {@code null}
     * @param action the code to run; must not be {@code null}
     * @return the value returned by {@code action}
     * @throws NullPointerException if {@code subjects} or {@code action} is
     *         {@code null}
     * @throws Exception whatever {@code action} throws
     */
    public static <V> V callAsAll(Subject[] subjects, Callable<V> action)
            throws Exception
    {
        final Tracked<V> tracked = new Tracked<V>(action);
        try {
            return UserSubjectSupport.callAsAll(subjects, tracked);
        } catch (Exception e) {
            throw tracked.rethrow(e);
        }
    }

    /**
     * Returns every {@link Subject} bound to the current thread, outermost
     * first: {@code Subject.currentAll()} on a DirtyChai JDK, otherwise the
     * single subject returned by {@link Subject#current Subject.current()}.
     * Returns an empty array when no subject is bound.  The result is a
     * snapshot that can be passed to {@link #callAsAll(Subject[], Callable)}
     * on another thread.
     *
     * @return the current subjects; never {@code null}
     */
    public static Subject[] captureAll() {
        if (CURRENT_ALL_METHOD != null) {
            try {
                Subject[] arr = (Subject[]) CURRENT_ALL_METHOD.invoke(null);
                if (arr != null && arr.length > 0) return arr.clone();
            } catch (Exception ignored) {
                // Reflective invocation failure: fall through to Subject.current().
            }
        }
        Subject single = Subject.current();
        return single != null ? new Subject[]{single} : new Subject[0];
    }

    /**
     * Wraps an action and records the exception it throws, so the original
     * exception can be told apart from any wrapper added by
     * {@code Subject.callAs}.
     */
    private static final class Tracked<V> implements Callable<V> {
        private final Callable<V> action;
        private Exception thrown;

        Tracked(Callable<V> action) {
            if (action == null) throw new NullPointerException("action must not be null");
            this.action = action;
        }

        @Override
        public V call() throws Exception {
            try {
                return action.call();
            } catch (Exception e) {
                thrown = e;
                throw e;
            }
        }

        /** The action's own exception if it threw one, otherwise {@code e}. */
        Exception rethrow(Exception e) {
            return thrown != null ? thrown : e;
        }
    }
}
