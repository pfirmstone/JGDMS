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

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.serial.fixtures.Holder;
import au.net.zeus.jgdms.der.serial.fixtures.Marker;
import au.net.zeus.jgdms.der.serial.fixtures.MarkerSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the DER replacement seam end-to-end: a registered {@code @Serializer}
 * lets the DER codec encode a NON-{@code @AtomicSerial} type ({@code Marker}) by
 * substituting an {@code @AtomicSerial} serializer ({@code MarkerSerializer}) on
 * encode and resolving it back via {@code readResolve} on decode.
 *
 * <p>Since WI-2 closed the production registry to a single platform resource
 * (X500Principal only), the synthetic {@code Marker} serializer is injected through
 * the package-private {@link DerReplacer#registerForTest} seam and removed again in
 * {@link #tearDown()} -- it never leaks into the closed production set and the test
 * does not rely on a classpath-merge.
 */
class DerReplacerSeamTest {

    @BeforeEach
    void registerMarker() {
        DerReplacer.registerForTest(Marker.class, MarkerSerializer.class);
    }

    @AfterEach
    void tearDown() {
        DerReplacer.resetForTest();
    }

    @Test
    void registeredTypeIsReportedRegisteredAndUnregisteredIsNot() throws Exception {
        assertTrue(DerReplacer.isRegistered(Marker.class),
                "the injected MarkerSerializer must be registered for Marker");
        assertFalse(DerReplacer.isRegistered(String.class),
                "unregistered types must not be reported as registered");
    }

    @Test
    void productionRegistryHasNoMarkerAfterReset() throws Exception {
        DerReplacer.resetForTest();
        try {
            assertFalse(DerReplacer.isRegistered(Marker.class),
                    "test serializer must NOT leak into the closed production registry");
        } finally {
            DerReplacer.registerForTest(Marker.class, MarkerSerializer.class);
        }
    }

    @Test
    void schemaGeneratorAdmitsRegisteredTypeAsNestedAtomicSerial() throws Exception {
        assertEquals("@AtomicSerial",
                SchemaGenerator.toWireType(Marker.class, Holder.class),
                "a registered non-@AtomicSerial type is admitted as a nested @AtomicSerial");
    }

    @Test
    void replaceWrapsAndResolveUnwraps() throws Exception {
        Object wrapped = DerReplacer.replace(new Marker(7));
        assertInstanceOf(MarkerSerializer.class, wrapped,
                "replace() must substitute the registered @AtomicSerial serializer");
        Object resolved = DerReplacer.resolve(wrapped);
        assertEquals(new Marker(7), resolved,
                "resolve() must rebuild the original via readResolve()");
    }

    @Test
    void replaceLeavesUnregisteredAndAtomicSerialUntouched() throws Exception {
        String s = "plain";
        assertSame(s, DerReplacer.replace(s), "unregistered value passes through unchanged");
        MarkerSerializer ms = new MarkerSerializer(new Marker(1));
        assertSame(ms, DerReplacer.replace(ms), "an @AtomicSerial value is not re-wrapped");
    }

    @Test
    void holderWithNonAtomicSerialFieldRoundTrips() throws Exception {
        Holder original = new Holder(new Marker(0xABCD));
        SchemaChain.Result chain = SchemaGenerator.generateChain(Holder.class);
        byte[] payload = ObjectCodec.encodeHierarchy(original, chain);

        Holder back = ObjectCodec.decodeHierarchy(Holder.class, chain, payload);
        assertNotNull(back.marker());
        assertEquals(original.marker(), back.marker(), "Marker value must round-trip");
        assertNotSame(original.marker(), back.marker(),
                "copy semantics: decode yields a new Marker instance");
    }

    @Test
    void encodingIsDeterministic() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(Holder.class);
        byte[] a = ObjectCodec.encodeHierarchy(new Holder(new Marker(42)), chain);
        byte[] b = ObjectCodec.encodeHierarchy(new Holder(new Marker(42)), chain);
        assertArrayEquals(a, b, "equal values must encode to byte-identical DER");
    }
}
