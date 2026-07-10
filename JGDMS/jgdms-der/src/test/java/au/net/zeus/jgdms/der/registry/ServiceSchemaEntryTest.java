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

package au.net.zeus.jgdms.der.registry;

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.SimpleRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.1 -- ServiceSchemaEntry acceptance tests.
 *
 * <ul>
 *   <li>7.1.a -- Reflective field-type assertion: every declared instance field is
 *               a primitive, {@link String}, or {@code byte[]}. No service objects.</li>
 *   <li>7.1.b -- Round-trip through {@link ObjectCodec} with field equality.</li>
 *   <li>7.1.c -- {@code schemaDigest} field holds and equals a digest produced by
 *               {@link SchemaGenerator#generateChain(Class)}.leafDigest().</li>
 * </ul>
 */
class ServiceSchemaEntryTest {

    // =========================================================================
    // 7.1.a -- ALL declared instance fields are primitive, String, or byte[]
    // =========================================================================

    /**
     * 7.1.a -- Reflective assertion that every declared instance field in
     * {@link ServiceSchemaEntry} is a primitive type, {@link String}, or
     * {@code byte[]}. No service-specific objects.
     *
     * <p>This is the S12.1 "Bytes and primitives only" constraint, enforced at
     * the structural level so no code review or runtime check is needed.
     */
    @Test
    void test_7_1_a_AllDeclaredFieldsArePrimitiveStringOrByteArray() {
        for (Field f : ServiceSchemaEntry.class.getDeclaredFields()) {
            // Skip static / synthetic fields (e.g. FORMAT_JGDMS_STD006_ATOMIC_DER constant)
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            Class<?> type = f.getType();
            boolean allowed = type.isPrimitive()
                    || type == String.class
                    || type == byte[].class;
            assertTrue(allowed,
                    "Field '" + f.getName() + "' has type " + type.getName()
                    + " which is not primitive, String, or byte[]");
        }
    }

    // =========================================================================
    // 7.1.b -- Round-trip through ObjectCodec with field equality
    // =========================================================================

    /**
     * 7.1.b -- Round-trip a {@link ServiceSchemaEntry} through the DER
     * {@link ObjectCodec}: encode -> DER bytes -> decode -> equal.
     *
     * <p>Verifies that {@code serialForm()}, the {@code (GetArg)} constructor, and
     * the value constructor all cooperate with the codec correctly.
     */
    @Test
    void test_7_1_b_RoundTripThroughObjectCodec() throws Exception {
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) 0x7E);

        ServiceSchemaEntry orig = new ServiceSchemaEntry(
                digest,
                "com.example.MyService",
                "1.0.0",
                ServiceSchemaEntry.FORMAT_JGDMS_STD006_ATOMIC_DER);

        // Generate the schema for ServiceSchemaEntry
        SchemaChain.Result chain = SchemaGenerator.generateChain(ServiceSchemaEntry.class);

        // Encode
        byte[] encoded = ObjectCodec.encodeHierarchy(orig, chain);

        // Decode
        ServiceSchemaEntry decoded =
                ObjectCodec.decodeHierarchy(ServiceSchemaEntry.class, chain, encoded);

        // Field equality
        assertArrayEquals(orig.getSchemaDigest(),   decoded.getSchemaDigest(),
                "schemaDigest must round-trip");
        assertEquals(orig.getServiceInterface(),     decoded.getServiceInterface(),
                "serviceInterface must round-trip");
        assertEquals(orig.getSchemaVersion(),        decoded.getSchemaVersion(),
                "schemaVersion must round-trip");
        assertEquals(orig.getSchemaFormat(),         decoded.getSchemaFormat(),
                "schemaFormat must round-trip");
        assertEquals(orig, decoded, "Decoded entry must equal original");
    }

    /**
     * 7.1.b variant -- round-trip with an empty-string {@code schemaVersion}.
     *
     * <p>The {@code schemaVersion} field is informational only (S12.3). The DER
     * codec encodes Strings on the wire; a null String cannot be encoded (no null
     * marker in DER). Callers that want to omit a version label should use the
     * empty string {@code ""}. This test verifies that an empty schemaVersion
     * round-trips correctly.
     */
    @Test
    void test_7_1_b_RoundTripWithEmptySchemaVersion() throws Exception {
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) 0xAB);

        ServiceSchemaEntry orig = new ServiceSchemaEntry(
                digest,
                "net.jini.core.lookup.ServiceRegistrar",
                "",  // empty is the DER-codec representation of "absent version"
                ServiceSchemaEntry.FORMAT_JGDMS_STD006_ATOMIC_DER);

        SchemaChain.Result chain = SchemaGenerator.generateChain(ServiceSchemaEntry.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(orig, chain);
        ServiceSchemaEntry decoded =
                ObjectCodec.decodeHierarchy(ServiceSchemaEntry.class, chain, encoded);

        assertArrayEquals(orig.getSchemaDigest(), decoded.getSchemaDigest());
        assertEquals(orig.getServiceInterface(),  decoded.getServiceInterface());
        assertEquals("", decoded.getSchemaVersion(),
                "empty schemaVersion must survive the round-trip");
        assertEquals(orig.getSchemaFormat(),      decoded.getSchemaFormat());
        assertEquals(orig, decoded);
    }

    // =========================================================================
    // 7.1.c -- schemaDigest holds and equals a real SchemaGenerator leaf digest
    // =========================================================================

    /**
     * 7.1.c -- Verifies that the {@code schemaDigest} field can hold and equals a
     * digest produced by {@link SchemaGenerator#generateChain(Class)}.leafDigest().
     *
     * <p>Uses {@link SimpleRecord} as the fixture class whose chain is generated, then
     * stores the resulting leaf digest in a {@link ServiceSchemaEntry} and asserts
     * field equality after construction.
     */
    @Test
    void test_7_1_c_SchemaDigestFieldHoldsGeneratorLeafDigest() throws Exception {
        // Generate a real schema chain for an @AtomicSerial fixture
        SchemaChain.Result fixtureChain = SchemaGenerator.generateChain(SimpleRecord.class);
        byte[] leafDigest = fixtureChain.leafDigest();
        assertEquals(32, leafDigest.length, "leafDigest must be 32 bytes");

        // Store the digest in a ServiceSchemaEntry
        ServiceSchemaEntry entry = new ServiceSchemaEntry(
                leafDigest,
                "com.example.SomeService",
                "2.0",
                ServiceSchemaEntry.FORMAT_JGDMS_STD006_ATOMIC_DER);

        // The schemaDigest field must equal the digest from SchemaGenerator
        assertArrayEquals(leafDigest, entry.getSchemaDigest(),
                "schemaDigest field must equal the leaf digest from SchemaGenerator.generateChain");

        // Generating the chain again must produce the same digest (determinism)
        SchemaChain.Result again = SchemaGenerator.generateChain(SimpleRecord.class);
        assertArrayEquals(again.leafDigest(), entry.getSchemaDigest(),
                "schemaDigest must equal the deterministic re-generated leaf digest");
    }

    // =========================================================================
    // Guard: check() rejects bad inputs
    // =========================================================================

    @Test
    void test_7_1_check_RejectsShortDigest() {
        byte[] short31 = new byte[31];
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceSchemaEntry(short31, "com.example.X", "v1",
                        ServiceSchemaEntry.FORMAT_JGDMS_STD006_ATOMIC_DER),
                "31-byte digest must be rejected");
    }

    @Test
    void test_7_1_check_RejectsEmptyServiceInterface() {
        byte[] d = new byte[32];
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceSchemaEntry(d, "", "v1",
                        ServiceSchemaEntry.FORMAT_JGDMS_STD006_ATOMIC_DER),
                "empty serviceInterface must be rejected");
    }
}
