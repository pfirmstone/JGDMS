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
package org.apache.river.norm;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.jini.io.MarshalledInstance;

/**
 * Shared reflection helpers for the norm-service AtomicMarshalledInstance-&gt;DER
 * write-site round-trip tests (commit 221fe251c: {@code ClientLeaseWrapper},
 * {@code EventType}, {@code JoinState}).
 *
 * <p>Two capabilities these tests need aren't reachable through any public API:
 * <ul>
 *   <li>Reading a {@link MarshalledInstance}'s private {@code payloadFormat} field,
 *       to assert a write site actually produced a
 *       {@code MarshallingFormat.ATOMIC_DER}-tagged instance (and not the legacy
 *       JOSS default).</li>
 *   <li>Invoking the package-private/private write and read methods at each site
 *       ({@code JoinState.writeAttributes}/{@code readAttributes},
 *       {@code EventType.getListener}), and reaching into transient fields that
 *       only hold their "just deserialized" value before a recovery method runs
 *       (e.g. {@code EventType.listener}, {@code ClientLeaseWrapper.clientLease}).</li>
 * </ul>
 *
 * <p>{@link #makeFormatUnresolvable(MarshalledInstance)} simulates, in a single
 * test JVM, the exact failure {@code MarshalledInstance.get()} hits in production
 * when {@code jgdms-der} is missing from the runtime classpath: no
 * {@code MarshalFactoryProvider} is registered for the instance's
 * {@code payloadFormat}, so {@code factoryForFormat} throws
 * {@code IllegalStateException} (see {@code net.jini.io.MarshalledInstance
 * #factoryForFormat}). Doing this by tampering with an already-legitimately-built
 * DER instance's format tag is equivalent to the two-process
 * (jgdms-der-present-writer / jgdms-der-absent-reader) probe the original
 * migration was verified with manually -- the observable failure mode
 * ({@code providers().get(format) == null}) is identical either way -- without
 * needing to spawn a second JVM with a trimmed classpath.
 */
public final class DerFormatTestSupport {

    /**
     * A {@code payloadFormat} string guaranteed to have no registered
     * {@code MarshalFactoryProvider}, so that {@code MarshalledInstance.get()}
     * fails exactly the way it would if the format's provider module (e.g.
     * {@code jgdms-der}) were absent from the runtime classpath.
     */
    public static final String UNRESOLVABLE_FORMAT =
        "TEST-ONLY/NO-PROVIDER-REGISTERED-FOR-THIS-FORMAT";

    private DerFormatTestSupport() { }

    /** Reads the private {@code payloadFormat} field of a {@link MarshalledInstance}. */
    public static String payloadFormatOf(MarshalledInstance mi) {
        return (String) getField(mi, "payloadFormat");
    }

    /**
     * Mutates {@code mi} in place so its {@code payloadFormat} names a format for
     * which no {@code MarshalFactoryProvider} is registered -- see class javadoc.
     */
    public static void makeFormatUnresolvable(MarshalledInstance mi) {
        setField(mi, "payloadFormat", UNRESOLVABLE_FORMAT);
    }

    public static Object getField(Object target, String name) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to read field " + name + " on "
                + target.getClass(), e);
        }
    }

    public static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to set field " + name + " on "
                + target.getClass(), e);
        }
    }

    /** Invokes a private (or package-private) instance method, unwrapping the
     *  target exception so callers can catch/assert on it directly. */
    public static Object invokePrivate(Object target, String methodName,
            Class<?>[] paramTypes, Object... args) {
        try {
            Method m = findMethod(target.getClass(), methodName, paramTypes);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            unwrap(e);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke " + methodName + " on "
                + target.getClass(), e);
        }
    }

    /** Invokes a private (or package-private) static method, unwrapping the
     *  target exception so callers can catch/assert on it directly. */
    public static Object invokePrivateStatic(Class<?> cls, String methodName,
            Class<?>[] paramTypes, Object... args) {
        try {
            Method m = findMethod(cls, methodName, paramTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            unwrap(e);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke static " + methodName + " on "
                + cls, e);
        }
    }

    /** Rethrows {@code e}'s cause unchecked (as a {@code RuntimeException} or
     *  {@code Error}), or wraps it in an {@code AssertionError} if it's a
     *  checked exception the caller wasn't expecting. */
    private static void unwrap(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new AssertionError(cause);
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {
                // try superclass
            }
        }
        throw new NoSuchFieldException(name + " (searched " + cls + " and superclasses)");
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>[] paramTypes)
            throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignore) {
                // try superclass
            }
        }
        throw new NoSuchMethodException(name + " (searched " + cls + " and superclasses)");
    }
}
