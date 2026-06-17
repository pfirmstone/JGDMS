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

import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.object.fixtures.SimpleRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.2 -- SchemaRegistry interface + InMemorySchemaRegistry acceptance tests.
 *
 * <ul>
 *   <li>7.2.1 -- Register is idempotent: same bytes -> same digest, no duplicate entry.</li>
 *   <li>7.2.2 -- Append-only: DIFFERENT bytes for the same logical class create
 *               INDEPENDENT entries -- both digests retrievable; neither overwrites the
 *               other.</li>
 *   <li>7.2.3 -- getSchemaChain returns leaf-first order (register a linked chain,
 *               retrieve it by leaf digest).</li>
 *   <li>7.2.4 -- isCompatible(A, B) is true IFF B's fields start with exactly A's
 *               fields in the same order and types.</li>
 *   <li>7.2.5 -- getSchema returns null for an unknown digest.</li>
 *   <li>7.2.6 -- getSchemaChain returns null for an unknown leaf digest.</li>
 *   <li>7.2.7 -- isCompatible returns false if either digest is unknown.</li>
 * </ul>
 */
class SchemaRegistryTest {

    private InMemorySchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemorySchemaRegistry();
    }

    // =========================================================================
    // 7.2.1 -- register is idempotent
    // =========================================================================

    /**
     * 7.2.1 -- Registering the same bytes twice returns the same digest both times
     * and results in exactly one stored entry (no duplicate).
     */
    @Test
    void test_7_2_1_Register_IsIdempotent() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(SimpleRecord.class);
        byte[] schemaBytes = chain.chain().get(0).encode();

        byte[] digest1 = registry.register(schemaBytes);
        byte[] digest2 = registry.register(schemaBytes);

        assertArrayEquals(digest1, digest2,
                "Same bytes must produce the same digest on second registration");
        assertEquals(32, digest1.length, "Digest must be 32 bytes");

        // Verify the bytes are retrievable
        byte[] retrieved = registry.getSchema(digest1);
        assertNotNull(retrieved, "Registered schema must be retrievable by digest");
        assertArrayEquals(schemaBytes, retrieved, "Retrieved bytes must equal registered bytes");

        // Register again -- still the same digest, still retrievable
        byte[] digest3 = registry.register(schemaBytes);
        assertArrayEquals(digest1, digest3, "Third registration of same bytes must still return same digest");
    }

    // =========================================================================
    // 7.2.2 -- append-only: different bytes -> different independent entries
    // =========================================================================

    /**
     * 7.2.2 -- Registering DIFFERENT bytes for the same logical class creates two
     * independent entries. Neither digest overwrites the other. Both are independently
     * retrievable.
     *
     * <p>Two schema variants are used: a two-field "v1" schema and a three-field "v2"
     * schema for the same class name. They produce different DER bytes and therefore
     * different digests.
     */
    @Test
    void test_7_2_2_Register_AppendOnly_DifferentBytes_BothRetrievable() throws Exception {
        String className = "com.example.MyService";

        // v1: two fields
        AtomicSerialSchemaRecord v1 = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("name",  "java.lang.String")
                ));

        // v2: three fields (forward evolution)
        AtomicSerialSchemaRecord v2 = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("name",  "java.lang.String"),
                    new AtomicSerialFieldDef("extra", "java.lang.String")
                ));

        byte[] bytesV1 = v1.encode();
        byte[] bytesV2 = v2.encode();

        // Pre-condition: the two schemas produce different DER bytes
        assertFalse(Arrays.equals(bytesV1, bytesV2), "v1 and v2 DER bytes must differ");

        byte[] digestV1 = registry.register(bytesV1);
        byte[] digestV2 = registry.register(bytesV2);

        // Different bytes -> different digests
        assertFalse(Arrays.equals(digestV1, digestV2),
                "Different schema bytes must produce different digests");

        // Both are independently retrievable (neither was overwritten)
        byte[] gotV1 = registry.getSchema(digestV1);
        byte[] gotV2 = registry.getSchema(digestV2);

        assertNotNull(gotV1, "v1 schema must still be retrievable after v2 was registered");
        assertNotNull(gotV2, "v2 schema must be retrievable");
        assertArrayEquals(bytesV1, gotV1, "Retrieved bytes must match the v1 schema");
        assertArrayEquals(bytesV2, gotV2, "Retrieved bytes must match the v2 schema");
    }

    // =========================================================================
    // 7.2.3 -- getSchemaChain returns leaf-first order
    // =========================================================================

    /**
     * 7.2.3 -- Register a two-record linked chain (leaf + root), then retrieve the
     * full chain by the leaf digest. The returned array must be leaf-first, matching
     * the registration order.
     */
    @Test
    void test_7_2_3_GetSchemaChain_LeafFirstOrder() throws Exception {
        // Build a two-record linked chain
        AtomicSerialSchemaRecord root = new AtomicSerialSchemaRecord(
                "com.example.Root", (byte[]) null,
                List.of(new AtomicSerialFieldDef("id", "int")));

        AtomicSerialSchemaRecord leafUnlinked = new AtomicSerialSchemaRecord(
                "com.example.Leaf", (byte[]) null,
                List.of(new AtomicSerialFieldDef("name", "java.lang.String")));

        SchemaChain.Result linked = SchemaChain.linkAndGetLeafDigest(List.of(leafUnlinked, root));
        AtomicSerialSchemaRecord linkedLeaf = linked.chain().get(0);
        AtomicSerialSchemaRecord linkedRoot = linked.chain().get(1);

        // Register both records
        byte[] leafBytes = linkedLeaf.encode();
        byte[] rootBytes = linkedRoot.encode();
        byte[] leafDigest = registry.register(leafBytes);
        registry.register(rootBytes);

        // leafDigest from register() must equal the chain's leafDigest
        assertArrayEquals(linked.leafDigest(), leafDigest,
                "Registered leaf digest must equal chain.leafDigest()");

        // Retrieve the chain by leaf digest
        byte[][] chain = registry.getSchemaChain(leafDigest);

        assertNotNull(chain, "getSchemaChain must return non-null for a registered leaf");
        assertEquals(2, chain.length, "Chain must have 2 records (leaf + root)");

        // First element must be the leaf record bytes
        assertArrayEquals(leafBytes, chain[0],
                "chain[0] must be the leaf schema bytes");
        // Second element must be the root record bytes
        assertArrayEquals(rootBytes, chain[1],
                "chain[1] must be the root schema bytes");
    }

    // =========================================================================
    // 7.2.4 -- isCompatible: prefix rule
    // =========================================================================

    /**
     * 7.2.4.a -- isCompatible(A, B) is TRUE when B's fields start with exactly A's
     * fields (B is a superset / forward-compatible extension of A).
     */
    @Test
    void test_7_2_4a_IsCompatible_TrueWhenBStartsWithA() throws Exception {
        String cls = "com.example.Svc";

        AtomicSerialSchemaRecord sA = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",   "int"),
                    new AtomicSerialFieldDef("name", "java.lang.String")
                ));

        AtomicSerialSchemaRecord sB = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("name",  "java.lang.String"),
                    new AtomicSerialFieldDef("extra", "java.lang.String") // B adds a trailing field
                ));

        byte[] digestA = registry.register(sA.encode());
        byte[] digestB = registry.register(sB.encode());

        assertTrue(registry.isCompatible(digestA, digestB),
                "isCompatible(A, B) must be true when B's fields are a superset of A's (same prefix)");
    }

    /**
     * 7.2.4.b -- isCompatible(A, B) is FALSE when B's fields do NOT start with A's.
     * (B's first field differs from A's.)
     */
    @Test
    void test_7_2_4b_IsCompatible_FalseWhenBFieldsNotPrefixOfA() throws Exception {
        String cls = "com.example.Svc2";

        AtomicSerialSchemaRecord sA = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",   "int"),
                    new AtomicSerialFieldDef("name", "java.lang.String")
                ));

        // B has a different first field (wireName differs)
        AtomicSerialSchemaRecord sB = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("uid",  "int"),     // different name
                    new AtomicSerialFieldDef("name", "java.lang.String"),
                    new AtomicSerialFieldDef("extra","java.lang.String")
                ));

        byte[] digestA = registry.register(sA.encode());
        byte[] digestB = registry.register(sB.encode());

        assertFalse(registry.isCompatible(digestA, digestB),
                "isCompatible(A, B) must be false when B's fields do not start with A's");
    }

    /**
     * 7.2.4.c -- isCompatible(A, B) is FALSE when B has FEWER fields than A (B is a
     * strict prefix of A -- the reverse direction). A cannot be decoded with B as the
     * schema because some of A's fields are missing in B.
     */
    @Test
    void test_7_2_4c_IsCompatible_FalseWhenBHasFewerFieldsThanA() throws Exception {
        String cls = "com.example.Svc3";

        AtomicSerialSchemaRecord sA = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",   "int"),
                    new AtomicSerialFieldDef("name", "java.lang.String"),
                    new AtomicSerialFieldDef("extra","java.lang.String")
                ));

        // B has only 2 fields (subset of A) -- not forward-compatible
        AtomicSerialSchemaRecord sB = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",   "int"),
                    new AtomicSerialFieldDef("name", "java.lang.String")
                ));

        byte[] digestA = registry.register(sA.encode());
        byte[] digestB = registry.register(sB.encode());

        assertFalse(registry.isCompatible(digestA, digestB),
                "isCompatible(A, B) must be false when B has fewer fields than A");
    }

    /**
     * 7.2.4.d -- isCompatible(A, A) is TRUE (a schema is trivially a superset of itself).
     */
    @Test
    void test_7_2_4d_IsCompatible_TrueForIdenticalSchemas() throws Exception {
        String cls = "com.example.Svc4";

        AtomicSerialSchemaRecord s = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(new AtomicSerialFieldDef("value", "long")));

        byte[] digest = registry.register(s.encode());

        assertTrue(registry.isCompatible(digest, digest),
                "isCompatible(A, A) must be true (schema is a superset of itself)");
    }

    /**
     * 7.2.4.e -- isCompatible(A, B) is FALSE when types differ at the same position.
     */
    @Test
    void test_7_2_4e_IsCompatible_FalseWhenFieldTypesDiffer() throws Exception {
        String cls = "com.example.Svc5";

        AtomicSerialSchemaRecord sA = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(new AtomicSerialFieldDef("id", "int")));

        // Same field name, different type
        AtomicSerialSchemaRecord sB = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(new AtomicSerialFieldDef("id", "long")));

        byte[] digestA = registry.register(sA.encode());
        byte[] digestB = registry.register(sB.encode());

        assertFalse(registry.isCompatible(digestA, digestB),
                "isCompatible(A, B) must be false when the same field position has different types");
    }

    // =========================================================================
    // 7.2.5 -- getSchema returns null for unknown digest
    // =========================================================================

    @Test
    void test_7_2_5_GetSchema_NullForUnknownDigest() {
        byte[] unknown = new byte[32];
        Arrays.fill(unknown, (byte) 0xFF);
        assertNull(registry.getSchema(unknown),
                "getSchema must return null for an unregistered digest");
    }

    // =========================================================================
    // 7.2.6 -- getSchemaChain returns null for unknown leaf digest
    // =========================================================================

    @Test
    void test_7_2_6_GetSchemaChain_NullForUnknownLeafDigest() {
        byte[] unknown = new byte[32];
        Arrays.fill(unknown, (byte) 0xAA);
        assertNull(registry.getSchemaChain(unknown),
                "getSchemaChain must return null for an unregistered leaf digest");
    }

    // =========================================================================
    // 7.2.7 -- isCompatible returns false if either digest is unknown
    // =========================================================================

    @Test
    void test_7_2_7_IsCompatible_FalseIfEitherDigestUnknown() throws Exception {
        String cls = "com.example.SvcX";
        AtomicSerialSchemaRecord s = new AtomicSerialSchemaRecord(cls, (byte[]) null,
                List.of(new AtomicSerialFieldDef("v", "int")));
        byte[] knownDigest  = registry.register(s.encode());
        byte[] unknownDigest = new byte[32];
        Arrays.fill(unknownDigest, (byte) 0x55);

        assertFalse(registry.isCompatible(unknownDigest, knownDigest),
                "isCompatible must return false when A is unknown");
        assertFalse(registry.isCompatible(knownDigest, unknownDigest),
                "isCompatible must return false when B is unknown");
    }
}
