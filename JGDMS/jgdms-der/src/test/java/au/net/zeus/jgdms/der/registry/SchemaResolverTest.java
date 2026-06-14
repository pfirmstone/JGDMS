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

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.3 — SchemaResolver §12.4 decision-tree acceptance tests.
 *
 * <p>One test per §12.4 branch, in priority order:
 *
 * <ul>
 *   <li>7.3.1 — <b>LOCAL_MATCH</b>: local digest matches record digest → registry is
 *               NOT invoked. Verified via a spy {@link SchemaRegistry} that records
 *               all calls; zero calls asserted after resolution.</li>
 *   <li>7.3.2 — <b>EMBEDDED</b>: digest mismatch + embedded schema present → embedded
 *               schema used. Asserted via the resolved chain's field list and a
 *               discriminating decode (different field order in embedded vs local schema).</li>
 *   <li>7.3.3 — <b>REGISTRY</b>: embedded absent (schemaBytes = empty) + registry hit →
 *               registry.getSchema(digest) is consulted; the returned chain matches the
 *               registered schema.</li>
 *   <li>7.3.4 — <b>DEFAULT_LOCAL</b>: embedded absent + registry miss → falls back to
 *               local schema; no exception thrown.</li>
 * </ul>
 */
class SchemaResolverTest {

    // =========================================================================
    // Spy SchemaRegistry — counts getSchema() calls
    // =========================================================================

    /**
     * A spy wrapper around {@link InMemorySchemaRegistry} that counts
     * {@link #getSchema} invocations. Used to assert that the registry is NOT
     * consulted on a local-digest match (§12.4 step 1).
     */
    private static final class SpyRegistry implements SchemaRegistry {
        private final InMemorySchemaRegistry delegate = new InMemorySchemaRegistry();
        private final AtomicInteger getSchemaCalls = new AtomicInteger(0);

        @Override
        public byte[] register(byte[] schemaRecordBytes) {
            return delegate.register(schemaRecordBytes);
        }

        @Override
        public byte[] getSchema(byte[] schemaDigest) {
            getSchemaCalls.incrementAndGet();
            return delegate.getSchema(schemaDigest);
        }

        @Override
        public byte[][] getSchemaChain(byte[] leafSchemaDigest) {
            return delegate.getSchemaChain(leafSchemaDigest);
        }

        @Override
        public boolean isCompatible(byte[] schemaDigestA, byte[] schemaDigestB) {
            return delegate.isCompatible(schemaDigestA, schemaDigestB);
        }

        int getSchemaCallCount() {
            return getSchemaCalls.get();
        }
    }

    // =========================================================================
    // 7.3.1 — LOCAL_MATCH: local digest matches → registry NOT invoked
    // =========================================================================

    /**
     * 7.3.1 — When the record's schema digest matches the receiver's local
     * {@code serialForm()} digest, the resolver takes the LOCAL_MATCH branch
     * without invoking the registry.
     *
     * <p><b>Key assertion:</b> the spy's {@code getSchema()} call count is zero.
     * This proves the registry is NEVER consulted when the local digest matches
     * (§12.4 step 1 — fast path).
     */
    @Test
    void test_7_3_1_LocalMatch_RegistryNotInvoked() throws Exception {
        // Build a conforming MarshalledInstanceRecord for VersionedRecord
        VersionedRecord orig = new VersionedRecord(42, "hello", "world");
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(orig, chain);
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

        // The record's schemaDigest must equal the local serialForm() digest
        // (it was built from the same chain) — this is the LOCAL_MATCH precondition.
        assertArrayEquals(chain.leafDigest(), rec.schemaDigest(),
                "Pre-condition: record digest must equal local chain digest");

        SpyRegistry spy = new SpyRegistry();

        SchemaResolver.Result result = SchemaResolver.resolve(rec, VersionedRecord.class, spy);

        // Branch must be LOCAL_MATCH
        assertEquals(SchemaResolver.Branch.LOCAL_MATCH, result.branch(),
                "Must take LOCAL_MATCH branch when local digest equals record digest");

        // Critical: the registry must NOT have been consulted at all
        assertEquals(0, spy.getSchemaCallCount(),
                "Registry getSchema() must NOT be called on a local digest match (§12.4 step 1)");
    }

    // =========================================================================
    // 7.3.2 — EMBEDDED: digest mismatch + embedded schema present → embedded used
    // =========================================================================

    /**
     * 7.3.2 — When the record's digest does NOT match the local schema, but the
     * record has valid embedded {@code schemaBytes}, the resolver takes the EMBEDDED
     * branch and uses the embedded schema chain.
     *
     * <p>The embedded schema has the first two of VersionedRecord's fields SWAPPED
     * (label first, id second) — matching the discriminating test in Phase 5.2.5.
     * We assert that the resolved chain's field order follows the embedded schema
     * (label first), not the local serialForm() (id first).
     *
     * <p>Additionally, we perform a decode using the resolved chain and verify
     * that the values are assigned by embedded-schema position — proving the
     * embedded schema drove the positional decode.
     */
    @Test
    void test_7_3_2_Embedded_UsedWhenDigestMismatch() throws Exception {
        String className = VersionedRecord.class.getName();

        // Embedded schema: SWAPPED relative to local serialForm()
        //   Local order:    [id:int, label:String, extra:String]
        //   Embedded order: [label:String, id:int, extra:String]
        AtomicSerialSchemaRecord embeddedRecord = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("label", "java.lang.String"),
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("extra", "java.lang.String")
                ));
        SchemaChain.Result embeddedChain =
                SchemaChain.linkAndGetLeafDigest(List.of(embeddedRecord));

        // Payload in EMBEDDED order: String, int, String
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("the-label"),
                DerWriter.writeInteger(BigInteger.valueOf(77)),
                DerWriter.writeUtf8String("the-extra")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                embeddedRecord.encode(),
                embeddedChain.leafDigest(),
                Optional.empty(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Pre-condition: embedded digest != local serialForm() digest
        SchemaChain.Result localChain = SchemaGenerator.generateChain(VersionedRecord.class);
        assertFalse(java.util.Arrays.equals(localChain.leafDigest(), rec.schemaDigest()),
                "Pre-condition: embedded digest must differ from local digest");

        SpyRegistry spy = new SpyRegistry();

        SchemaResolver.Result result = SchemaResolver.resolve(rec, VersionedRecord.class, spy);

        assertEquals(SchemaResolver.Branch.EMBEDDED, result.branch(),
                "Must take EMBEDDED branch when digest mismatches but schemaBytes present");

        // The resolved chain's leaf record must have the EMBEDDED field order
        List<AtomicSerialFieldDef> resolvedFields = result.chain().chain().get(0).fields();
        assertEquals("label", resolvedFields.get(0).wireName(),
                "Resolved chain field[0] must be 'label' (embedded order, not local)");
        assertEquals("id", resolvedFields.get(1).wireName(),
                "Resolved chain field[1] must be 'id' (embedded order, not local)");

        // Decode using the resolved chain to prove the embedded schema drove positioning.
        // If the local schema had been used instead, position 0 (UTF8String) would be
        // read as int 'id' and throw — successful decode with correct values proves
        // the embedded schema was used.
        VersionedRecord decoded =
                ObjectCodec.decodeHierarchy(VersionedRecord.class, result.chain(), hierarchyPayload);
        assertEquals(77,          decoded.getId(),    "id from embedded position 1 (int)");
        assertEquals("the-label", decoded.getLabel(), "label from embedded position 0 (String)");
        assertEquals("the-extra", decoded.getExtra(), "extra from embedded position 2");
    }

    // =========================================================================
    // 7.3.3 — REGISTRY: embedded absent → registry.getSchema(digest) is queried
    // =========================================================================

    /**
     * 7.3.3 — When the embedded {@code schemaBytes} are absent (empty array) and the
     * registry has a schema for the record's digest, the resolver takes the REGISTRY
     * branch.
     *
     * <p>We build a {@link MarshalledInstanceRecord} with {@code schemaBytes = new byte[0]}
     * to simulate the "embedded absent" synthetic edge case from §12.4 step 4b. The
     * schema for the record's digest is pre-loaded into the registry. We assert that:
     * <ol>
     *   <li>The resolver takes the REGISTRY branch.</li>
     *   <li>The spy's {@code getSchema()} is called at least once (registry is queried).</li>
     *   <li>The resolved chain's class name matches the registered schema.</li>
     * </ol>
     */
    @Test
    void test_7_3_3_Registry_QueriedWhenEmbeddedAbsent() throws Exception {
        String className = VersionedRecord.class.getName();

        // Build a schema with a different field set (to distinguish from local)
        AtomicSerialSchemaRecord altSchema = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",   "int"),
                    new AtomicSerialFieldDef("label","java.lang.String")
                    // 'extra' deliberately absent
                ));
        SchemaChain.Result altChain = SchemaChain.linkAndGetLeafDigest(List.of(altSchema));
        byte[] altDigest = altChain.leafDigest();

        // Payload for the 2-field schema (id=9, label="reg-test")
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(9)),
                DerWriter.writeUtf8String("reg-test")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        // Build the record with schemaBytes = empty (synthetic "embedded absent")
        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                new byte[0],   // <-- empty schemaBytes simulates absent embedded schema
                altDigest,
                Optional.empty(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Pre-load the schema into the spy registry
        SpyRegistry spy = new SpyRegistry();
        spy.register(altSchema.encode());

        SchemaResolver.Result result = SchemaResolver.resolve(rec, VersionedRecord.class, spy);

        assertEquals(SchemaResolver.Branch.REGISTRY, result.branch(),
                "Must take REGISTRY branch when embedded absent and registry has the schema");

        // Registry must have been queried
        assertTrue(spy.getSchemaCallCount() > 0,
                "Registry getSchema() must be called when embedded schema is absent");

        // The resolved chain must represent the registered schema
        assertEquals(className, result.chain().chain().get(0).className(),
                "Resolved chain class name must match the registered schema");
        assertEquals(2, result.chain().chain().get(0).fields().size(),
                "Resolved chain must have 2 fields (from the registered schema)");
    }

    // =========================================================================
    // 7.3.4 — DEFAULT_LOCAL: embedded absent + registry miss → local + defaults
    // =========================================================================

    /**
     * 7.3.4 — When embedded schema is absent AND the registry does not have the
     * schema either, the resolver falls back to the local {@code serialForm()} schema.
     * No exception is thrown; absent fields will receive {@code GetArg} defaults.
     *
     * <p>The registry is a spy with no entries (all {@code getSchema()} calls return
     * null). We assert:
     * <ol>
     *   <li>The resolver takes the DEFAULT_LOCAL branch.</li>
     *   <li>No exception is thrown.</li>
     *   <li>The resolved chain is the local schema (class name matches, field count
     *       matches the local serialForm()).</li>
     * </ol>
     */
    @Test
    void test_7_3_4_DefaultLocal_NoExceptionOnRegistryMiss() throws Exception {
        String className = VersionedRecord.class.getName();

        // A digest for some "unknown" schema — not in the registry
        byte[] unknownDigest = new byte[32];
        java.util.Arrays.fill(unknownDigest, (byte) 0x42);

        // Payload (values irrelevant; we don't decode in this test)
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(0)),
                DerWriter.writeUtf8String("fallback"),
                DerWriter.writeUtf8String("default")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                new byte[0],        // embedded absent
                unknownDigest,
                Optional.empty(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Empty spy registry — all getSchema() calls return null
        SpyRegistry spy = new SpyRegistry();
        // (nothing registered)

        // Must not throw — this is the "fall back to local schema + defaults" branch
        SchemaResolver.Result result = assertDoesNotThrow(
                () -> SchemaResolver.resolve(rec, VersionedRecord.class, spy),
                "SchemaResolver.resolve() must not throw on a registry miss");

        assertEquals(SchemaResolver.Branch.DEFAULT_LOCAL, result.branch(),
                "Must take DEFAULT_LOCAL branch when embedded absent and registry misses");

        // The resolved chain must be the LOCAL schema
        assertEquals(className, result.chain().chain().get(0).className(),
                "DEFAULT_LOCAL chain class name must be the receiver class");

        // Local serialForm() has 3 fields
        SchemaChain.Result localChain = SchemaGenerator.generateChain(VersionedRecord.class);
        assertEquals(localChain.chain().get(0).fields().size(),
                result.chain().chain().get(0).fields().size(),
                "DEFAULT_LOCAL chain field count must match local serialForm()");
    }
}
