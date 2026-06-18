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

package au.net.zeus.jgdms.der.serial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectStreamException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;

/**
 * Lets the DER codec encode a type that is <em>not</em> itself {@code @AtomicSerial}
 * by substituting an {@code @AtomicSerial} <em>serializer</em> for it on the wire
 * (the DER analogue of {@code writeReplace}), and rebuilding the original object on
 * decode via {@code readResolve}.
 *
 * <p><b>Registration honours {@link Serializer @Serializer}.</b> A serializer is an
 * existing {@code @AtomicSerial} class annotated {@code @Serializer(replaceObType = X)}
 * with a constructor that accepts {@code X} and (for decode) implementing
 * {@link Resolve}. These already exist in {@code org.apache.river.api.io}
 * (e.g. {@code ThrowableSerializer}, {@code URLSerializer}); the DER codec reuses
 * them as-is rather than reimplementing them. Because such a serializer is itself
 * {@code @AtomicSerial}, the DER value-tree encodes it natively once substituted.
 *
 * <p><b>No visibility or permission obstacle.</b> A serializer class is referenced by
 * name, so it need not be {@code public}; the {@code (X)} constructor is invoked via
 * {@code setAccessible(true)} -- the same reflective access (and the same
 * {@code ReflectPermission("suppressAccessChecks")}) the codec already uses for every
 * {@code @AtomicSerial} field and constructor.
 *
 * <p><b>Enumeration.</b> Serializer class names are listed, one per line, in classpath
 * resources named {@value #SERIALIZER_LIST_RESOURCE} (blank lines and {@code #}
 * comments ignored). Any module -- including the codec's own and test code -- can
 * contribute serializers by adding such a resource; the {@code replaceObType -> class}
 * mapping is then read from each class's {@code @Serializer} annotation.
 */
public final class DerReplacer {

    /** Classpath resource listing serializer class names (one per line). */
    public static final String SERIALIZER_LIST_RESOURCE = "META-INF/jgdms/der-serializers";

    /** replaceObType -> serializer class (insertion order preserved for stable assignable lookup). */
    private static final Map<Class<?>, Class<?>> SERIALIZERS = load();

    private DerReplacer() {
    }

    private static Map<Class<?>, Class<?>> load() {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>();
        ClassLoader cl = DerReplacer.class.getClassLoader();
        try {
            Enumeration<URL> resources = cl.getResources(SERIALIZER_LIST_RESOURCE);
            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                try (InputStream in = res.openStream();
                     BufferedReader r = new BufferedReader(
                             new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String name = line.trim();
                        if (name.isEmpty() || name.startsWith("#")) {
                            continue;
                        }
                        registerByName(m, name, cl);
                    }
                }
            }
        } catch (IOException e) {
            // A malformed/unreadable list must not break encoding of @AtomicSerial
            // types; replacement simply remains unavailable for the missing entries.
        }
        return m;
    }

    private static void registerByName(Map<Class<?>, Class<?>> m, String name, ClassLoader cl) {
        Class<?> serializer;
        try {
            serializer = Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            return; // serializer not on this classpath -- skip
        }
        Serializer ann = serializer.getAnnotation(Serializer.class);
        if (ann == null || ann.replaceObType() == null) {
            return; // not a @Serializer -- ignore
        }
        m.putIfAbsent(ann.replaceObType(), serializer);
    }

    /** The serializer registered for {@code target}, honouring assignability (e.g. a
     *  Throwable serializer handles every Throwable subtype); {@code null} if none. */
    private static Class<?> serializerFor(Class<?> target) {
        Class<?> exact = SERIALIZERS.get(target);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<Class<?>, Class<?>> e : SERIALIZERS.entrySet()) {
            if (e.getKey().isAssignableFrom(target)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * @return {@code true} if a DER replacement serializer is registered for this type
     *         (used by the schema generator to admit the field as a nested
     *         {@code @AtomicSerial}).
     */
    public static boolean isRegistered(Class<?> type) {
        return type != null && serializerFor(type) != null;
    }

    /**
     * Substitutes a registered non-{@code @AtomicSerial} value with its
     * {@code @AtomicSerial} serializer (constructed via the serializer's
     * {@code replaceObType} constructor); otherwise returns {@code value} unchanged.
     *
     * @param value the value about to be encoded (may be {@code null}).
     * @return the serializer to encode, or {@code value} itself when no substitution applies.
     * @throws IOException if a registered serializer cannot be constructed.
     */
    public static Object replace(Object value) throws IOException {
        if (value == null) {
            return null;
        }
        Class<?> c = value.getClass();
        if (c.isAnnotationPresent(AtomicSerial.class)) {
            return value;
        }
        Class<?> serializer = serializerFor(c);
        if (serializer == null) {
            return value;
        }
        Class<?> replaceType = serializer.getAnnotation(Serializer.class).replaceObType();
        try {
            Constructor<?> ctor = serializer.getDeclaredConstructor(replaceType);
            ctor.setAccessible(true);
            return ctor.newInstance(value);
        } catch (NoSuchMethodException e) {
            throw new IOException("DER serializer " + serializer.getName()
                    + " has no (" + replaceType.getName() + ") constructor", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("DER serializer " + serializer.getName()
                    + " failed to wrap " + c.getName(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IOException("DER serializer " + serializer.getName()
                    + " is not constructable", e);
        }
    }

    /**
     * Rebuilds the original object from a decoded serializer by honouring
     * {@code readResolve()} when the decoded value implements {@link Resolve};
     * otherwise returns {@code decoded} unchanged.
     *
     * @param decoded the freshly decoded value.
     * @return the resolved object.
     * @throws ObjectStreamException if {@code readResolve()} rejects the value.
     */
    public static Object resolve(Object decoded) throws ObjectStreamException {
        if (decoded instanceof Resolve r) {
            return r.readResolve();
        }
        return decoded;
    }
}
