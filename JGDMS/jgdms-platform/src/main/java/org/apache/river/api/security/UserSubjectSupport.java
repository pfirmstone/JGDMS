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

package org.apache.river.api.security;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;

/**
 * Reflective plumbing for the DirtyChai JDK's multi-{@code Subject} API.
 *
 * <h2>Why reflection</h2>
 *
 * <p>JGDMS must compile and run on a stock OpenJDK, where
 * {@code javax.security.auth.UserSubject} does not exist.  Therefore this class
 * contains <strong>no compile-time reference</strong> to {@code UserSubject}:
 * no import, no {@code UserSubject.class} literal and — critically — no
 * {@code UserSubject[].class} array literal.  Both the class and its array type
 * are obtained by name at runtime:
 *
 * <pre>
 *   Class&lt;?&gt; us      = Class.forName("javax.security.auth.UserSubject");
 *   Class&lt;?&gt; usArray = Array.newInstance(us, 0).getClass();
 *   Subject.class.getMethod("callAs", Callable.class, usArray);
 * </pre>
 *
 * <h2>Why {@code Subject[].class} does not work</h2>
 *
 * <p>On DirtyChai {@code javax.security.auth.Subject} is
 * {@code sealed ... permits WorkerSubject, UserSubject}, and the only
 * <em>public</em> multi-argument form is
 * {@code callAs(Callable, UserSubject...)} — erased to
 * {@code (Callable, UserSubject[])}.  The {@code (Callable, Subject...)} form
 * is {@code protected} on the nested {@code Subject.NoCheck} class and is
 * therefore invisible to {@code Subject.class.getMethod}.  A lookup for
 * {@code Subject[].class} is an exact-signature miss and yields
 * {@code NoSuchMethodException}; historically that exception was swallowed and
 * the cached {@code Method} stayed {@code null}, so multi-Subject dispatch never
 * ran and every call site silently fell back to the <em>first</em> user Subject,
 * dropping all other client identities from the authorization context.
 *
 * <p>Fixing only the lookup is not enough: the reflective {@code invoke} requires
 * the argument to be assignable to {@code UserSubject[]}, and a {@code Subject[]}
 * is not.  Hence {@link #newUserSubject} (construct the right runtime type in the
 * first place) and {@link #toUserSubjectArray} (build a genuine
 * {@code UserSubject[]}, verifying every element).
 *
 * <h2>Fail-loud invariant</h2>
 *
 * <p>If {@code UserSubject} is <em>absent</em> (stock OpenJDK) the constants are
 * {@code null} and callers degrade to the single-Subject
 * {@code Subject.callAs(Subject, Callable)} path — silently, because that is the
 * correct behaviour on that platform.
 *
 * <p>If {@code UserSubject} is <em>present</em> but the expected
 * {@code callAs}/constructor signatures are not, this class fails loudly from its
 * static initializer (an {@link IllegalStateException} propagating as
 * {@link ExceptionInInitializerError}) naming the signature it expected.  A
 * DirtyChai/JGDMS version mismatch must not silently degrade to single-identity
 * authorization.
 *
 * <p>All declared types in JGDMS fields and signatures remain
 * {@code Subject}/{@code Subject[]}; only the reflective plumbing here ever sees
 * the narrower type.
 *
 * @since 3.1.0
 */
public final class UserSubjectSupport {

    /** Binary name of the DirtyChai user-identity Subject subclass. */
    public static final String USER_SUBJECT_CLASS_NAME =
            "javax.security.auth.UserSubject";

    /**
     * {@code javax.security.auth.UserSubject} on a DirtyChai JDK, or
     * {@code null} on a stock OpenJDK where the class does not exist.
     */
    public static final Class<?> USER_SUBJECT_CLASS;

    /**
     * {@code Subject.callAs(Callable, UserSubject...)} on a DirtyChai JDK, or
     * {@code null} on a stock OpenJDK.  Never {@code null} when
     * {@link #USER_SUBJECT_CLASS} is non-{@code null} — see the fail-loud
     * invariant in the class documentation.
     */
    public static final Method CALL_AS_MULTI;

    /** {@code UserSubject(boolean, Set, Set, Set)}, or {@code null} on a stock OpenJDK. */
    private static final Constructor<?> USER_SUBJECT_CONSTRUCTOR;

    static {
        Class<?> userSubject;
        try {
            userSubject = Class.forName(USER_SUBJECT_CLASS_NAME);
        } catch (ClassNotFoundException notDirtyChai) {
            userSubject = null;
        }
        USER_SUBJECT_CLASS = userSubject;
        if (userSubject == null) {
            CALL_AS_MULTI = null;
            USER_SUBJECT_CONSTRUCTOR = null;
        } else {
            // Fail loudly, not silently, on a DirtyChai/JGDMS signature mismatch.
            CALL_AS_MULTI = lookupCallAsMulti(userSubject);
            USER_SUBJECT_CONSTRUCTOR = lookupConstructor(userSubject);
        }
    }

    private UserSubjectSupport() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns {@code true} when the DirtyChai multi-{@code Subject} API is
     * available on this JDK.
     *
     * @return true if multi-Subject {@code callAs} can be used.
     */
    public static boolean isMultiSubjectSupported() {
        return CALL_AS_MULTI != null;
    }

    /**
     * Looks up {@code Subject.callAs(Callable, UserSubject...)} for the supplied
     * {@code UserSubject} class, deriving the array parameter type reflectively.
     *
     * <p>Package-private rather than private so that the fail-loud invariant can
     * be unit tested by passing a class for which no such overload exists; this
     * is the test seam, and production always calls it with
     * {@link #USER_SUBJECT_CLASS}.
     *
     * @param userSubjectClass the resolved {@code UserSubject} class.
     * @return the multi-Subject {@code callAs} method; never {@code null}.
     * @throws IllegalStateException if no such method exists, or reflective
     *         access to it is denied.
     */
    static Method lookupCallAsMulti(Class<?> userSubjectClass) {
        Class<?> userSubjectArray = Array.newInstance(userSubjectClass, 0).getClass();
        try {
            return Subject.class.getMethod("callAs", Callable.class, userSubjectArray);
        } catch (NoSuchMethodException | SecurityException mismatch) {
            throw new IllegalStateException(
                "DirtyChai/JGDMS version mismatch: " + userSubjectClass.getName()
                + " is present, but the expected method "
                + Subject.class.getName() + ".callAs("
                + Callable.class.getName() + ", " + userSubjectClass.getName()
                + "...) was not found. Multi-Subject dispatch must not silently"
                + " degrade to a single client identity; align the JGDMS and JDK"
                + " versions.", mismatch);
        }
    }

    /**
     * Looks up the public {@code (boolean, Set, Set, Set)} constructor of the
     * supplied {@code UserSubject} class.  Package-private for the same
     * fail-loud test seam as {@link #lookupCallAsMulti}.
     *
     * @param userSubjectClass the resolved {@code UserSubject} class.
     * @return the constructor; never {@code null}.
     * @throws IllegalStateException if no such constructor exists, or reflective
     *         access to it is denied.
     */
    static Constructor<?> lookupConstructor(Class<?> userSubjectClass) {
        try {
            return userSubjectClass.getConstructor(
                    boolean.class, Set.class, Set.class, Set.class);
        } catch (NoSuchMethodException | SecurityException mismatch) {
            throw new IllegalStateException(
                "DirtyChai/JGDMS version mismatch: " + userSubjectClass.getName()
                + " is present, but the expected constructor "
                + userSubjectClass.getName()
                + "(boolean, java.util.Set, java.util.Set, java.util.Set) was not"
                + " found. Reconstructed client identities must not silently"
                + " degrade to a plain Subject; align the JGDMS and JDK versions.",
                mismatch);
        }
    }

    /**
     * Creates a {@code Subject} carrying a remote <em>user</em> identity.
     *
     * <p>On a DirtyChai JDK a {@code javax.security.auth.UserSubject} is
     * constructed reflectively and returned as its {@code Subject} supertype, so
     * that it can subsequently be passed to
     * {@code Subject.callAs(Callable, UserSubject...)}.  On a stock OpenJDK a
     * plain {@link Subject} is returned.
     *
     * <p>{@code WorkerSubject} is deliberately not used here: it is the local
     * workload identity, whereas these are remote client identities.
     *
     * @param readOnly whether the resulting Subject is read-only.  Multi-Subject
     *        {@code callAs} rejects Subjects that are not read-only, so callers
     *        that intend to pass the result to {@link #toUserSubjectArray} must
     *        pass {@code true}.
     * @param principals the Subject's principals.
     * @param pubCredentials the Subject's public credentials.
     * @param privCredentials the Subject's private credentials.
     * @return the new Subject; on DirtyChai a {@code UserSubject}.
     * @throws IllegalStateException if the reflective construction fails for a
     *         reason other than one thrown by the constructor itself.
     */
    public static Subject newUserSubject(boolean readOnly,
                                         Set<? extends Principal> principals,
                                         Set<?> pubCredentials,
                                         Set<?> privCredentials)
    {
        if (USER_SUBJECT_CONSTRUCTOR == null) {
            return new Subject(readOnly, principals, pubCredentials, privCredentials);
        }
        try {
            return (Subject) USER_SUBJECT_CONSTRUCTOR.newInstance(
                    readOnly, principals, pubCredentials, privCredentials);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(
                    "Unable to construct " + USER_SUBJECT_CLASS_NAME, cause);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Unable to construct " + USER_SUBJECT_CLASS_NAME, e);
        }
    }

    /**
     * Executes {@code action} with <em>all</em> of {@code subjects} bound as the
     * current scoped identities for its duration.
     *
     * <p><strong>This is the single caller site for the reflective
     * multi-{@code Subject} machinery in JGDMS.</strong>  The original defect was
     * that the lookup was duplicated in two production classes and both copies
     * were wrong (they asked for {@code Subject[]}, which does not resolve), while
     * a third, correct copy in the test sources kept passing — so the tests never
     * saw production's failure.  Centralising the whole operation here makes that
     * class of drift impossible.
     *
     * <p>Policy, in order:
     * <ul>
     *   <li>{@code subjects.length == 0} — {@code action} is called directly; no
     *       identity is bound.</li>
     *   <li>{@code subjects.length == 1}, or this JDK has no multi-{@code Subject}
     *       API — {@code Subject.callAs(subjects[0], action)}.  Note that nesting
     *       single-{@code Subject} calls would be wrong: each inner call shadows
     *       the outer one, so only the innermost identity would be visible.</li>
     *   <li>otherwise — one reflective
     *       {@code Subject.callAs(Callable, UserSubject...)} call binding every
     *       identity at once, so that {@code Subject.currentAll()} inside
     *       {@code action} observes all of them.</li>
     * </ul>
     *
     * <p>Exceptions thrown by {@code action} are propagated faithfully: an
     * {@link InvocationTargetException} from the reflective call is unwrapped and
     * its cause rethrown when it is an {@code Exception} or an {@code Error}.
     *
     * @param <V> the result type of {@code action}.
     * @param subjects the identities to bind; must not be {@code null}, and on the
     *        multi-Subject path every element must be a read-only
     *        {@code UserSubject} (see {@link #toUserSubjectArray}).
     * @param action the code to run; must not be {@code null}.
     * @return the value returned by {@code action}.
     * @throws NullPointerException if {@code subjects} or {@code action} is null.
     * @throws Exception whatever {@code action} throws.
     */
    public static <V> V callAsAll(Subject[] subjects, Callable<V> action)
            throws Exception
    {
        if (subjects == null) throw new NullPointerException("subjects must not be null");
        if (action == null) throw new NullPointerException("action must not be null");
        if (subjects.length == 0) return action.call();
        if (subjects.length == 1 || CALL_AS_MULTI == null) {
            // Single identity, or a stock OpenJDK: identical in effect, and the
            // only correct option when the multi-Subject API is absent.
            return Subject.callAs(subjects[0], action);
        }
        // The argument must be a genuine UserSubject[]: a Subject[] is not
        // assignable to the parameter type and Method.invoke would fail with
        // "argument type mismatch".
        final Object userSubjects = toUserSubjectArray(subjects);
        try {
            @SuppressWarnings("unchecked")
            V result = (V) CALL_AS_MULTI.invoke(null, action, userSubjects);
            return result;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error)     throw (Error)     cause;
            // Rare: the cause is a raw Throwable (neither Exception nor Error).
            // InvocationTargetException is itself an Exception, so rethrowing it
            // honours the caller's contract while preserving the stack trace.
            throw ite;
        } catch (IllegalAccessException iae) {
            // Should never happen: the located method is public.
            throw new IllegalStateException(
                    "Unexpected access denial invoking " + Subject.class.getName()
                    + ".callAs(Callable, " + USER_SUBJECT_CLASS_NAME + "...)", iae);
        }
    }

    /**
     * Converts {@code subjects} into a genuine {@code UserSubject[]} suitable
     * for reflective invocation of
     * {@code Subject.callAs(Callable, UserSubject...)}.
     *
     * <p>The returned value is typed {@code Object} precisely because JGDMS must
     * not name {@code UserSubject[]} at compile time; pass it straight to
     * {@link Method#invoke(Object, Object...)} as a single argument.
     *
     * <p><strong>Fails closed.</strong>  Every element must be non-{@code null},
     * an instance of {@link #USER_SUBJECT_CLASS}, and read-only (the JDK method
     * itself rejects mutable Subjects).  A violation throws rather than dropping
     * that identity: silently shrinking the set of bound identities would widen
     * authorization, because a multi-principal grant is a conjunction that is
     * evaluated against exactly the identities that are actually bound.
     *
     * @param subjects the user Subjects to bind; must not be {@code null}.
     * @return a {@code UserSubject[]} holding the same elements, as an
     *         {@code Object}.
     * @throws NullPointerException if {@code subjects} is {@code null}.
     * @throws IllegalStateException if the DirtyChai multi-Subject API is not
     *         available on this JDK.
     * @throws IllegalArgumentException if any element is {@code null}, is not a
     *         {@code UserSubject}, or is not read-only.
     */
    public static Object toUserSubjectArray(Subject[] subjects) {
        if (subjects == null) throw new NullPointerException("subjects must not be null");
        final Class<?> userSubject = USER_SUBJECT_CLASS;
        if (userSubject == null) {
            throw new IllegalStateException(
                "Cannot build a " + USER_SUBJECT_CLASS_NAME + "[]: " + USER_SUBJECT_CLASS_NAME
                + " is not present on this JDK; the caller must use the"
                + " single-Subject Subject.callAs(Subject, Callable) path instead.");
        }
        Object array = Array.newInstance(userSubject, subjects.length);
        for (int i = 0; i < subjects.length; i++) {
            Subject s = subjects[i];
            if (s == null) {
                throw new IllegalArgumentException(
                    "subjects[" + i + "] is null; refusing to bind an incomplete"
                    + " set of client identities.");
            }
            if (!userSubject.isInstance(s)) {
                throw new IllegalArgumentException(
                    "subjects[" + i + "] is a " + s.getClass().getName() + ", not a "
                    + userSubject.getName() + "; refusing to drop it from the"
                    + " authorization context. Construct remote client identities"
                    + " with UserSubjectSupport.newUserSubject(...).");
            }
            if (!s.isReadOnly()) {
                throw new IllegalArgumentException(
                    "subjects[" + i + "] (" + userSubject.getName() + ") is not"
                    + " read-only; Subject.callAs(Callable, UserSubject...) requires"
                    + " read-only Subjects.");
            }
            Array.set(array, i, s);
        }
        return array;
    }
}
