/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.serial;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.RandomAccess;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.security.auth.x500.X500Principal;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.serial.fixtures.Marker;
import au.net.zeus.jgdms.der.serial.fixtures.MarkerSerializer;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Serializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI-1 / WI-3 / WI-6(a-d): the deterministic serializer-selection predicate
 * (exact-match &rarr; unique most-specific assignable &rarr; fail-closed), the
 * load-time {@code @AtomicSerial} admission check, and the invariant that resolution
 * never consults the thread-context loader.
 *
 * <p>Synthetic registries are driven directly through the package-private
 * {@link DerReplacer#selectSerializer} seam so no test serializer touches the closed
 * production set. The map <em>values</em> are irrelevant placeholders for the
 * selection algorithm (which reads only the keys); distinct placeholder classes are
 * used purely to make the returned selection identifiable.
 */
class DerReplacerResolutionTest {

    // Distinct placeholder serializer classes (identity only; never encoded here).
    private interface SA {}
    private interface SB {}
    private interface SC {}
    private interface SD {}

    private static Map<Class<?>, Class<?>> reg(Object... kv) {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Class<?>) kv[i], (Class<?>) kv[i + 1]);
        }
        return m;
    }

    // ---- WI-6(a): exact match resolves to its own serializer, insertion-order-free ----

    @Test
    void exactMatchResolvesToItsSerializer() throws Exception {
        Map<Class<?>, Class<?>> m = reg(Integer.class, SA.class, Long.class, SB.class);
        assertSame(SA.class, DerReplacer.selectSerializer(m, Integer.class));
        assertSame(SB.class, DerReplacer.selectSerializer(m, Long.class));
        assertNull(DerReplacer.selectSerializer(m, String.class),
                "an unregistered, unassignable type resolves to null");
    }

    @Test
    void productionRegistersX500PrincipalToItsOwnSerializer() throws Exception {
        assertTrue(DerReplacer.isRegistered(X500Principal.class),
                "the closed production registry must register X500Principal");
        Object wrapped = DerReplacer.replace(new X500Principal("CN=Self"));
        Class<?> ser = wrapped.getClass();
        assertTrue(ser.isAnnotationPresent(AtomicSerial.class),
                "the substituted serializer must itself be @AtomicSerial");
        assertEquals(X500Principal.class,
                ser.getAnnotation(Serializer.class).replaceObType(),
                "X500Principal must resolve to a serializer that replaces X500Principal");
    }

    // ---- WI-6(b): a subclass resolves to the NEAREST registered supertype ----

    @Test
    void subclassResolvesToNearestRegisteredSupertype() throws Exception {
        Map<Class<?>, Class<?>> m = reg(Throwable.class, SA.class, RuntimeException.class, SB.class);
        // IllegalStateException <: RuntimeException <: Exception <: Throwable.
        // Both Throwable and RuntimeException are assignable; RuntimeException is nearer.
        assertSame(SB.class, DerReplacer.selectSerializer(m, IllegalStateException.class),
                "must pick the most-specific (RuntimeException), not the first-inserted (Throwable)");
        // A checked exception is assignable only to Throwable here.
        assertSame(SA.class, DerReplacer.selectSerializer(m, java.io.IOException.class));
    }

    @Test
    void totallyOrderedInterfaceChainResolvesToMostSpecific() throws Exception {
        // A totally-ordered interface chain (Collection :> List) is NOT ambiguous:
        // Collection is dominated by List, so List (nearest) wins deterministically.
        Map<Class<?>, Class<?>> m = reg(Collection.class, SA.class, List.class, SB.class);
        assertSame(SB.class, DerReplacer.selectSerializer(m, ArrayList.class));
    }

    // ---- WI-6(c): class key + INCOMPARABLE interface key => FAIL CLOSED ----

    @Test
    void classAndIncomparableInterfaceKeysFailClosed() {
        // ArrayList extends AbstractList (class) AND implements RandomAccess (interface);
        // AbstractList and RandomAccess are mutually non-assignable => incomparable
        // multiplicity => must throw, never silently pick one.
        Map<Class<?>, Class<?>> m = reg(AbstractList.class, SC.class, RandomAccess.class, SD.class);
        DerException ex = assertThrows(DerException.class,
                () -> DerReplacer.selectSerializer(m, ArrayList.class),
                "incomparable class+interface keys must fail closed, not silently resolve");
        assertTrue(ex.getMessage().contains("ambiguous"),
                "fail-closed error must name the ambiguity: " + ex.getMessage());
    }

    @Test
    void twoIncomparableInterfaceKeysFailClosed() {
        // RandomAccess and Cloneable are both assignable from ArrayList and incomparable.
        Map<Class<?>, Class<?>> m = reg(RandomAccess.class, SA.class, Cloneable.class, SB.class);
        assertThrows(DerException.class,
                () -> DerReplacer.selectSerializer(m, ArrayList.class));
    }

    // ---- WI-3: load-time admission of @AtomicSerial serializers only ----

    @Test
    void registerByNameAcceptsAtomicSerialSerializer() {
        Map<Class<?>, Class<?>> m =
                DerReplacer.registerByNameForTest(MarkerSerializer.class.getName());
        assertEquals(1, m.size(), "an @AtomicSerial @Serializer must be registered");
        assertSame(MarkerSerializer.class, m.get(Marker.class));
    }

    @Test
    void registerByNameRejectsNonAtomicSerialSerializer() {
        // UIDSerializer is @Serializer(replaceObType=UID.class) but @AtomicExternal
        // (Externalizable), NOT @AtomicSerial -- jgdms-der has no Externalizable path.
        // It must be rejected loudly at load, not registered to fail deep in encode.
        Map<Class<?>, Class<?>> m =
                DerReplacer.registerByNameForTest("org.apache.river.api.io.UIDSerializer");
        assertTrue(m.isEmpty(),
                "an Externalizable (@AtomicExternal, not @AtomicSerial) serializer must be rejected");
    }

    @Test
    void registerByNameRejectsNonSerializerClass() {
        assertTrue(DerReplacer.registerByNameForTest("java.lang.String").isEmpty(),
                "a class without @Serializer must not register");
        assertTrue(DerReplacer.registerByNameForTest("does.not.Exist").isEmpty(),
                "an absent class name must not register (warn + skip)");
    }

    // ---- WI-6(d): resolution pins the codec/endpoint loader, never the TCCL ----

    @Test
    void resolutionNeverConsultsThreadContextClassLoader() throws Exception {
        // Warm up X500Principal parsing / security-provider init and the reflective
        // serializer construction BEFORE installing the hostile loader, so the assertion
        // isolates DerReplacer's own resolution (not incidental first-use classloading).
        DerReplacer.replace(new X500Principal("CN=Warmup"));
        X500Principal principal = new X500Principal("CN=NoTccl");

        ClassLoader hostile = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                throw new AssertionError("resolution must NOT use the thread-context loader: " + name);
            }
            @Override
            protected Class<?> findClass(String name) {
                throw new AssertionError("resolution must NOT use the thread-context loader: " + name);
            }
            @Override
            public java.net.URL getResource(String name) {
                throw new AssertionError("resolution must NOT use the thread-context loader: " + name);
            }
        };
        Thread t = Thread.currentThread();
        ClassLoader saved = t.getContextClassLoader();
        t.setContextClassLoader(hostile);
        try {
            // Production selection + substitution must not touch the hostile TCCL.
            assertTrue(DerReplacer.isRegistered(X500Principal.class));
            Object wrapped = DerReplacer.replace(principal);
            assertTrue(wrapped.getClass().isAnnotationPresent(AtomicSerial.class));
            // The pure predicate over a synthetic registry likewise never loads a class.
            Map<Class<?>, Class<?>> m = reg(Throwable.class, SA.class);
            assertSame(SA.class, DerReplacer.selectSerializer(m, RuntimeException.class));
        } finally {
            t.setContextClassLoader(saved);
        }
    }
}
