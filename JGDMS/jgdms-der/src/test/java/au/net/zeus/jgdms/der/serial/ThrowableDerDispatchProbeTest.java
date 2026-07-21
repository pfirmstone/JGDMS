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

import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.jini.core.transaction.TransactionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Throwable dispatch-precedence findings verified for
 * {@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} sec.2.2 (U1b board
 * constraint: serializer-vs-{@code @AtomicSerial} precedence must be known
 * before annotating any Throwable subclass):
 *
 * <ol>
 *   <li>On the DER path a runtime class's own {@code @AtomicSerial} annotation
 *       takes precedence over ANY registered {@code @Serializer} replacement
 *       ({@link DerReplacer#replace} checks the annotation first) -- so
 *       annotating an existing Throwable subclass {@code @AtomicSerial} is a
 *       WIRE-FORM CHANGE wherever a Throwable-keyed serializer applies, never
 *       a no-op. (The atomic JOSS-format layer has the same precedence:
 *       {@code AtomicMarshalOutputStream.defaultReplaceObject} ignores
 *       {@code @AtomicSerial} classes before its {@code instanceof Throwable}
 *       branch.)</li>
 *   <li>The closed production DER registry deliberately does NOT register
 *       {@code Throwable} ({@code META-INF/jgdms/der-serializers}: deferred,
 *       unresolved canonicality defects), so a non-{@code @AtomicSerial}
 *       Throwable cannot cross the pure DER stream at all -- it fails loudly
 *       rather than riding {@code ThrowableSerializer}.</li>
 * </ol>
 */
class ThrowableDerDispatchProbeTest {

    @AfterEach
    void reset() {
        DerReplacer.resetForTest();
    }

    /**
     * Finding 1 -- precedence. With a Throwable-keyed serializer registered
     * (the {@code ThrowableSerializer} shape), an {@code @AtomicSerial}
     * Throwable passes through {@code replace()} UNCHANGED (annotation wins),
     * while a plain Throwable IS substituted. Hence adding {@code @AtomicSerial}
     * to a Throwable subclass changes its wire dispatch.
     */
    @Test
    void atomicSerialAnnotationTakesPrecedenceOverThrowableKeyedSerializer()
            throws Exception {
        DerReplacer.registerForTest(Throwable.class,
                Class.forName("org.apache.river.api.io.ThrowableSerializer"));

        Object atomicSerialThrowable = new TransactionException("precedence");
        assertSame(atomicSerialThrowable, DerReplacer.replace(atomicSerialThrowable),
                "an @AtomicSerial Throwable must pass through unchanged --"
                + " the annotation takes precedence over the registry");

        Object plainThrowable = new IllegalStateException("precedence");
        Object substituted = DerReplacer.replace(plainThrowable);
        assertInstanceOf(org.apache.river.api.io.Resolve.class, substituted,
                "a plain Throwable must be substituted with the registered"
                + " serializer when one is keyed to Throwable");
        assertTrue(substituted.getClass().getName()
                        .endsWith("ThrowableSerializer"),
                "substitution must select the Throwable-keyed serializer,"
                + " got " + substituted.getClass().getName());
    }

    /**
     * Finding 2 -- the production registry defers Throwable: no serializer is
     * registered, and a non-{@code @AtomicSerial} Throwable fails loudly at
     * the top of the DER stream ({@code @AtomicSerial}-restricted) instead of
     * riding {@code ThrowableSerializer}. If this test ever fails because
     * Throwable WAS registered, that is a versioned wire-contract change
     * (STD-006 sec.7.6.1) -- re-verify every fenced Throwable subclass
     * (e.g. {@code net.jini.space.InternalSpaceException}) before accepting.
     */
    @Test
    void productionDerRegistryDefersThrowable() throws Exception {
        assertFalse(DerReplacer.isRegistered(Throwable.class),
                "the closed production registry must NOT register Throwable"
                + " (deferred; der-serializers)");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> out.writeObject(new IllegalStateException("boom")),
                    "a non-@AtomicSerial Throwable must fail loudly on the"
                    + " pure DER stream, not silently ride a serializer");
        } catch (IOException ignored) {
            // close() flushing an empty buffer never throws; tolerated for
            // the try-with-resources shape.
        }
    }
}
